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
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
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
import net.arcanic.visuals.core.Theme;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;

public final class Ambience extends Module {
    private static final int NIGHT_BLUE = 0xFF5078DC;
    public final Setting.Bool sky = this.add(new Setting.Bool("sky", "Цвет неба", true));
    public final Setting.Bool clouds = this.add(new Setting.Bool("clouds", "Цвет облаков", true));
    public final Setting.Bool stars = this.add(new Setting.Bool("stars", "Цвет звёзд", true));
    public final Setting.Bool themeSync = this.add(new Setting.Bool("theme_sync", "Цвет темы", true));
    public final Setting.Colour skyColour = (this.add(new Setting.Colour("sky_colour", "Цвет неба", false, 0xFF5078DC)))
            .showIf(() -> !this.themeSync.on() && this.sky.on());
    public final Setting.Colour cloudColour = (this.add(
                    new Setting.Colour("cloud_colour", "Цвет облаков", false, 0xFF5078DC)))
            .showIf(() -> !this.themeSync.on() && this.clouds.on());
    public final Setting.Colour starColour = (this.add(
                    new Setting.Colour("star_colour", "Цвет звёзд", false, 0xFF5078DC)))
            .showIf(() -> !this.themeSync.on() && this.stars.on());
    public final Setting.Bool night = this.add(new Setting.Bool("night", "Ночной режим", true));
    public final Setting.Bool nightSync;
    public final Setting.Colour nightColour;
    public final Setting.Num nightStrength;
    public final Setting.Bool wet;
    public final Setting.Num wetReflection;
    public final Setting.Num wetAmount;
    public final Setting.Mode wetQuality;
    public final Setting.Bool rain;
    public final Setting.Num rainDensity;
    public final Setting.Num rainOpacity;
    public final Setting.Num rainDrops;
    public final Setting.Num rainSplashes;
    public final Setting.Bool crystals;
    public final Setting.Mode crystalShape;
    public final Setting.Colour crystalColour;
    public static int drawnSky = 0xFF78A7FF;
    private static final RenderPipeline NIGHT;
    private static final ByteBuffer DATA;
    private static GpuBuffer uniforms;
    private static boolean failed;

    public void tick() {
        if (this.crystals.on()) {
            Modules.FLOATING_CRYSTALS.tick();
        } else {
            Modules.FLOATING_CRYSTALS.clear();
        }
    }

    public Ambience() {
        super(
                "ambience",
                "Ambience",
                "Небо, облака и звёзды в цвет темы, синяя ночь и парящие кристаллы",
                Category.WORLD,
                false);
        this.nightSync = this.add(new Setting.Bool("night_sync", "Ночь цветом темы", true))
                .showIf(this.night::on);
        this.nightColour = (this.add(new Setting.Colour("night_colour", "Цвет ночи", false, 0xFF5078DC)))
                .showIf(() -> this.night.on() && !this.nightSync.on());
        this.nightStrength = this.add(new Setting.Num("night_strength", "Сила ночи", 70.0, 0.0, 100.0, 1.0, "%"))
                .showIf(this.night::on);
        this.wet = this.add(new Setting.Bool("wet", "Мокрый мир", false));
        this.wetReflection = this.add(new Setting.Num("wet_reflection", "Отражения", 70.0, 0.0, 100.0, 5.0, "%"))
                .showIf(this.wet::on);
        this.wetAmount = this.add(new Setting.Num("wet_amount", "Влажность", 60.0, 0.0, 100.0, 5.0, "%"))
                .showIf(this.wet::on);
        Setting.Mode mode = this.add(
                new Setting.Mode("wet_quality", "Качество отражений", "Среднее", "Низкое", "Среднее", "Высокое"));
        this.wetQuality = (Setting.Mode) mode.showIf(this.wet::on);
        this.rain = this.add(new Setting.Bool("rain", "Дождь", false));
        this.rainDensity = this.add(new Setting.Num("rain_density", "Плотность дождя", 100.0, 10.0, 200.0, 5.0, "%"))
                .showIf(this.rain::on);
        this.rainOpacity = this.add(new Setting.Num("rain_opacity", "Прозрачность дождя", 45.0, 5.0, 100.0, 5.0, "%"))
                .showIf(this.rain::on);
        this.rainDrops = this.add(new Setting.Num("rain_drops", "Капли на экране", 60.0, 0.0, 100.0, 5.0, "%"))
                .showIf(this.rain::on);
        this.rainSplashes = this.add(new Setting.Num("rain_splashes", "Всплески", 60.0, 0.0, 100.0, 5.0, "%"))
                .showIf(this.rain::on);
        this.crystals = this.add(new Setting.Bool("crystals", "Парящие кристаллы", false));
        Setting.Mode mode2 = this.add(new Setting.Mode(
                "crystal_shape", "Фигура кристаллов", "Кристалл", "Кристалл", "Пирамида", "Звезда", "Икосаэдр"));
        this.crystalShape = (Setting.Mode) mode2.showIf(this.crystals::on);
        this.crystalColour = this.add(new Setting.Colour("crystal_colour", "Цвет кристаллов", true))
                .showIf(this.crystals::on);
    }

    private int resolved(Setting.Colour own) {
        return 0xFF000000 | (this.themeSync.on() ? Theme.accent() : own.argb());
    }

    public int sky(int vanilla) {
        drawnSky = this.enabled() && this.sky.on() ? this.resolved(this.skyColour) : vanilla;
        return drawnSky;
    }

    public int clouds(int vanilla) {
        return this.enabled() && this.clouds.on()
                ? vanilla & 0xFF000000 | this.resolved(this.cloudColour) & 0xFFFFFF
                : vanilla;
    }

    public Vector4f stars(float r, float g, float b, float a) {
        if (this.enabled() && this.stars.on()) {
            int c = this.resolved(this.starColour);
            return new Vector4f(
                    (float) (c >> 16 & 255) / 255.0F * r,
                    (float) (c >> 8 & 255) / 255.0F * g,
                    (float) (c & 255) / 255.0F * b,
                    a);
        } else {
            return new Vector4f(r, g, b, a);
        }
    }

    private boolean nightActive() {
        return this.enabled() && this.night.on() && this.nightStrength.get() > 0.0;
    }

    public static void tintLightmap(GpuTextureView lightmap) {
        Ambience m = Modules.AMBIENCE;
        if (!failed && m.nightActive() && lightmap != null) {
            try {
                GpuDevice device = RenderSystem.getDevice();
                if (!device.precompilePipeline(NIGHT).isValid()) {
                    failed = true;
                    System.err.println("[ArcanicVisuals] Ambience night shader failed");
                    return;
                }

                if (uniforms == null) {
                    uniforms = device.createBuffer(() -> "Ambience night", 136, 16L);
                }

                int c = m.nightSync.on() ? Theme.accent() : m.nightColour.argb();
                DATA.clear();
                DATA.putFloat((float) (c >> 16 & 255) / 255.0F)
                        .putFloat((float) (c >> 8 & 255) / 255.0F)
                        .putFloat((float) (c & 255) / 255.0F)
                        .putFloat(m.nightStrength.f() / 100.0F);
                DATA.flip();
                CommandEncoder encoder = device.createCommandEncoder();
                encoder.writeToBuffer(uniforms.slice(), DATA);

                try (RenderPass pass =
                        encoder.createRenderPass(() -> "Ambience night", lightmap, OptionalInt.empty())) {
                    pass.setPipeline(NIGHT);
                    pass.setUniform("NightPass", uniforms);
                    pass.draw(0, 3);
                }
            } catch (RuntimeException e) {
                failed = true;
                System.err.println("[ArcanicVisuals] Ambience night off: " + e);
            }
        }
    }

    static {
        NIGHT = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("arcanicvisuals", "pipeline/ambience_night"))
                .withVertexShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "core/ambience_night"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("arcanicvisuals", "core/ambience_night"))
                .withUniform("NightPass", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.EMPTY, Mode.TRIANGLES)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withCull(false)
                .withDepthWrite(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .build();
        DATA = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
    }
}
