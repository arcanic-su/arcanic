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
package net.arcanic.visuals.arc;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalInt;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** The hand fragment shader on the 1.21.11 GPU API. */
public final class HandShaders extends Module {
    private static final String[] MODES = {
        "Solid",
        "Rainbow",
        "Wave",
        "Pulse",
        "Cosmos",
        "Nebula",
        "Cobweb",
        "Plasma",
        "LiquidGlass",
        "Matrix",
        "CosmicBlast",
        "AlienNebula",
        "Aqua",
        "Purple",
        "Golden",
        "Flow",
        "Galaxy",
        "Smoky",
        "Homie",
        "Yippie",
        "HiDef",
        "Gang",
        "Holy",
        "Teal",
        "Gamer",
        "Techno",
        "Sheldon",
        "Network"
    };
    public final Setting.Mode mode = add(new Setting.Mode("shader", "Шейдер Arcanic", "Nebula", MODES));
    public final Setting.Colour colour = add(new Setting.Colour("colour", "Цвет", true));
    public final Setting.Num speed = add(new Setting.Num("speed", "Скорость", 1, 0, 5, .1, ""));
    public final Setting.Num opacity = add(new Setting.Num("opacity", "Плотность", 1, .1, 1, .05, ""));
    public final Setting.Bool only = add(new Setting.Bool("only", "Только рисунок шейдера", false));
    private static RenderTarget hand, scene;
    private static GpuBuffer uniforms;
    private static GpuTextureView oldColour, oldDepth;
    private static boolean captured;
    private static final ByteBuffer DATA = ByteBuffer.allocateDirect(48).order(ByteOrder.nativeOrder());
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("arcanicvisuals", "pipeline/arc_hand"))
            .withVertexShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "core/arc_hand"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "core/arc_hand"))
            .withUniform("ArcHand", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();

    public HandShaders() {
        super(
                "arc_hand_shader",
                "Подсветка рук и оружия",
                "Оригинальные шейдеры Arcanic: космос, плазма, стекло и ещё 25 эффектов.",
                Category.HANDS,
                false);
    }

    @Override
    protected void onDisable() {
        close();
    }

    public static void begin() {
        var mc = Minecraft.getInstance();
        if (!Modules.HAND_SHADER.enabled()
                || mc.player == null
                || !mc.options.getCameraType().isFirstPerson()) return;
        var device = RenderSystem.getDevice();
        // Validate before redirecting the hands so shader failure preserves vanilla rendering.
        if (!device.precompilePipeline(PIPELINE).isValid()) return;
        var main = mc.getMainRenderTarget();
        if (hand == null || hand.width != main.width || hand.height != main.height) {
            close();
            hand = new TextureTarget("Arcanic hand", main.width, main.height, true);
            scene = new TextureTarget("Arcanic scene", main.width, main.height, false);
        }
        if (uniforms == null)
            uniforms = device.createBuffer(
                    () -> "Arcanic hand uniforms", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 48);
        device.createCommandEncoder().clearColorAndDepthTextures(hand.getColorTexture(), 0, hand.getDepthTexture(), 1);
        oldColour = RenderSystem.outputColorTextureOverride;
        oldDepth = RenderSystem.outputDepthTextureOverride;
        RenderSystem.outputColorTextureOverride = hand.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = hand.getDepthTextureView();
        captured = true;
    }

    public static void finish() {
        if (!captured) return;
        captured = false;
        RenderSystem.outputColorTextureOverride = oldColour;
        RenderSystem.outputDepthTextureOverride = oldDepth;
        var mc = Minecraft.getInstance();
        var module = Modules.HAND_SHADER;
        var main = mc.getMainRenderTarget();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(
                main.getColorTexture(), scene.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
        int c = module.colour.argb();
        int mode = module.mode.index();
        if (mode >= 12) mode += 2;
        DATA.clear();
        DATA.putFloat(((c >> 16) & 255) / 255f)
                .putFloat(((c >> 8) & 255) / 255f)
                .putFloat((c & 255) / 255f)
                .putFloat(module.opacity.f());
        DATA.putFloat(main.width)
                .putFloat(main.height)
                .putFloat((System.nanoTime() % 100_000_000_000L) / 1_000_000_000f * module.speed.f())
                .putFloat(mode);
        DATA.putFloat(module.only.on() ? 1 : 0);
        DATA.position(48);
        DATA.flip();
        encoder.writeToBuffer(uniforms.slice(), DATA);
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        try (var pass = encoder.createRenderPass(
                () -> "Arcanic hands",
                oldColour != null ? oldColour : main.getColorTextureView(),
                OptionalInt.empty())) {
            pass.setPipeline(PIPELINE);
            pass.setUniform("ArcHand", uniforms);
            pass.bindTexture("Sampler0", hand.getColorTextureView(), sampler);
            pass.bindTexture("Sampler1", scene.getColorTextureView(), sampler);
            pass.draw(0, 3);
        }
    }

    public static void close() {
        if (captured) {
            RenderSystem.outputColorTextureOverride = oldColour;
            RenderSystem.outputDepthTextureOverride = oldDepth;
            captured = false;
        }
        if (hand != null) {
            hand.destroyBuffers();
            hand = null;
        }
        if (scene != null) {
            scene.destroyBuffers();
            scene = null;
        }
        if (uniforms != null) {
            uniforms.close();
            uniforms = null;
        }
    }
}
