/*
 * This file is part of Arcanic Visuals (https://github.com/arcanic-su/arcanic)
 *
 * Copyright (c) 2026 Arcanic
 *
 * Arcanic Visuals is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Arcanic Visuals is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Arcanic Visuals. If not, see <https://www.gnu.org/licenses/>.
 */
package net.arcanic.visuals.fx;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalInt;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * "Motion Blur": each frame of the world is laid over what came before it, the older ones fading - the smear of a
 * camera in motion, as in a film. The same at any frame rate: how much of the past stays is worked out per second, not
 * per frame. The interface over the world stays sharp (it is drawn after).
 */
public final class MotionBlur extends Module {
    private final Setting.Num strength = add(new Setting.Num("strength", "Сила", 50, 10, 90, 5, "%"));

    public MotionBlur() {
        super(
                "motion_blur",
                "Motion Blur",
                "Плавное размытие при движении камеры, как в видео; интерфейс остаётся чётким",
                Category.WORLD,
                false);
    }

    private static RenderTarget now, past;
    private static RenderPipeline pipeline;
    private static com.mojang.blaze3d.buffers.GpuBuffer params;
    private static long last;
    private static boolean fresh = true;

    @Override
    protected void onDisable() {
        fresh = true;
    }

    /** Called once the world is drawn: the past laid under the new frame, and the result kept as the next past. */
    public static void apply() {
        MotionBlur m = Modules.MOTION_BLUR;
        Minecraft mc = Minecraft.getInstance();
        if (m == null || !m.enabled() || mc.level == null) {
            fresh = true;
            return;
        }
        RenderTarget main = mc.getMainRenderTarget();
        if (main == null) return;
        GpuTexture tex = main.getColorTexture();
        GpuTextureView view = main.getColorTextureView();
        if (tex == null || view == null) return;
        int w = Math.max(1, main.width), h = Math.max(1, main.height);
        if (now == null) now = new TextureTarget("arcanicvisuals_motion_now", w, h, false);
        if (past == null) past = new TextureTarget("arcanicvisuals_motion_past", w, h, false);
        if (now.width != w || now.height != h) {
            now.resize(w, h);
            past.resize(w, h);
            fresh = true;
        }
        long t = System.nanoTime();
        float dt = last == 0 ? 0 : (t - last) / 1e9f;
        last = t;
        if (dt > .25f) fresh = true; // a pause (a menu, a stall): no old picture smeared over the new one
        CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
        if (fresh) {
            enc.copyTextureToTexture(tex, past.getColorTexture(), 0, 0, 0, 0, 0, w, h);
            fresh = false;
            return;
        }
        enc.copyTextureToTexture(tex, now.getColorTexture(), 0, 0, 0, 0, 0, w, h);
        if (pipeline == null) {
            pipeline = RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath("arcanicvisuals", "pipeline/motion_blur"))
                    .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "post/motion_blur"))
                    .withDepthWrite(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withColorWrite(true, true)
                    .withCull(false)
                    .withoutBlend()
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                    .withSampler("InSampler")
                    .withSampler("PrevSampler")
                    .withUniform("Params", UniformType.UNIFORM_BUFFER)
                    .build();
            Std140SizeCalculator size = new Std140SizeCalculator();
            size.putVec4();
            params = RenderSystem.getDevice()
                    .createBuffer(
                            () -> "arcanicvisuals motion blur",
                            GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE,
                            size.get());
        }
        // how much of the past stays after this frame: the setting is per 1/60 s, so the smear is the same at any fps
        float keep = (float) Math.pow(m.strength.get() / 100.0, Math.max(dt, 1e-3) * 60.0);
        keep = Math.max(0, Math.min(.96f, keep));
        try (GpuBuffer.MappedView mapped = enc.mapBuffer(params, false, true)) {
            Std140Builder.intoBuffer(mapped.data()).putVec4(keep, 0, 0, 0);
        }
        try (RenderPass pass = enc.createRenderPass(() -> "arcanicvisuals motion blur", view, OptionalInt.empty())) {
            pass.setPipeline(pipeline);
            pass.setUniform("Params", params);
            var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            pass.bindTexture("InSampler", now.getColorTextureView(), sampler);
            pass.bindTexture("PrevSampler", past.getColorTextureView(), sampler);
            pass.draw(0, 3);
        }
        enc.copyTextureToTexture(tex, past.getColorTexture(), 0, 0, 0, 0, 0, w, h);
    }
}
