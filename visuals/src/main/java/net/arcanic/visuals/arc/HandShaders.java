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
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalInt;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

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
    public final Setting.Mode mode;
    public final Setting.Colour colour;
    public final Setting.Num speed;
    public final Setting.Num opacity;
    public final Setting.Bool only;
    private static RenderTarget hand;
    private static RenderTarget scene;
    private static GpuBuffer uniforms;
    private static GpuTextureView oldColour;
    private static GpuTextureView oldDepth;
    private static boolean captured;
    private static final ByteBuffer DATA = ByteBuffer.allocateDirect(48).order(ByteOrder.nativeOrder());
    private static final RenderPipeline PIPELINE;

    public HandShaders() {
        super(
                "arc_hand_shader",
                "Подсветка рук и оружия",
                "Оригинальные шейдеры Arcanic: космос, плазма, стекло и ещё 25 эффектов.",
                Category.HANDS,
                false);
        this.mode = this.add(new Setting.Mode("shader", "Шейдер Arcanic", "Nebula", MODES));
        this.colour = this.add(new Setting.Colour("colour", "Цвет", true));
        this.speed = this.add(new Setting.Num("speed", "Скорость", 1.0, 0.0, 5.0, 0.1, ""));
        this.opacity = this.add(new Setting.Num("opacity", "Плотность", 1.0, 0.1, 1.0, 0.05, ""));
        this.only = this.add(new Setting.Bool("only", "Только рисунок шейдера", false));
    }

    protected void onDisable() {
        close();
    }

    public static void begin() {
        Minecraft mc = Minecraft.getInstance();
        if (Modules.HAND_SHADER.enabled()
                && mc.player != null
                && mc.options.getCameraType().isFirstPerson()) {
            GpuDevice device = RenderSystem.getDevice();
            if (device.precompilePipeline(PIPELINE).isValid()) {
                RenderTarget main = mc.getMainRenderTarget();
                if (hand == null || hand.width != main.width || hand.height != main.height) {
                    close();
                    hand = new TextureTarget("Arcanic hand", main.width, main.height, true);
                    scene = new TextureTarget("Arcanic scene", main.width, main.height, false);
                }

                if (uniforms == null) {
                    uniforms = device.createBuffer(() -> "Arcanic hand uniforms", 136, 48L);
                }

                device.createCommandEncoder()
                        .clearColorAndDepthTextures(hand.getColorTexture(), 0, hand.getDepthTexture(), 1.0);
                oldColour = RenderSystem.outputColorTextureOverride;
                oldDepth = RenderSystem.outputDepthTextureOverride;
                RenderSystem.outputColorTextureOverride = hand.getColorTextureView();
                RenderSystem.outputDepthTextureOverride = hand.getDepthTextureView();
                captured = true;
            }
        }
    }

    public static void finish() {
        if (captured) {
            captured = false;
            RenderSystem.outputColorTextureOverride = oldColour;
            RenderSystem.outputDepthTextureOverride = oldDepth;
            Minecraft mc = Minecraft.getInstance();
            HandShaders module = Modules.HAND_SHADER;
            RenderTarget main = mc.getMainRenderTarget();
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(
                    main.getColorTexture(), scene.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
            int c = module.colour.argb();
            int mode = module.mode.index();
            if (mode >= 12) {
                mode += 2;
            }

            DATA.clear();
            DATA.putFloat((float) (c >> 16 & 255) / 255.0F)
                    .putFloat((float) (c >> 8 & 255) / 255.0F)
                    .putFloat((float) (c & 255) / 255.0F)
                    .putFloat(module.opacity.f());
            DATA.putFloat((float) main.width)
                    .putFloat((float) main.height)
                    .putFloat((float) (System.nanoTime() % 100000000000L) / 1.0E9F * module.speed.f())
                    .putFloat((float) mode);
            DATA.putFloat(module.only.on() ? 1.0F : 0.0F);
            DATA.position(48);
            DATA.flip();
            encoder.writeToBuffer(uniforms.slice(), DATA);
            GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

            try (RenderPass pass = encoder.createRenderPass(
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

    static {
        PIPELINE = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("arcanicvisuals", "pipeline/arc_hand"))
                .withVertexShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "core/arc_hand"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "core/arc_hand"))
                .withUniform("ArcHand", UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withSampler("Sampler1")
                .withVertexFormat(DefaultVertexFormat.EMPTY, Mode.TRIANGLES)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withCull(false)
                .withDepthWrite(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .build();
    }
}
