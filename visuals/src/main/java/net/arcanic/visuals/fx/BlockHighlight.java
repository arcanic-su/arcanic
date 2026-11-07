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
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class BlockHighlight extends Module {
    private static final String[] SHADERS = {
        "Nebula", "Cosmos", "Web", "Plasma", "LiquidGlass", "Matrix", "LivingArmor", "AlienNebula", "Holy"
    };
    private static final String[] FILES = {
        "nebula", "cosmos", "web", "plasma", "glass", "matrix", "armor", "alien", "holy"
    };
    public final Setting.Bool outline = this.add(new Setting.Bool("outline", "Обводка", true));
    public final Setting.Num width;
    public final Setting.Mode animation;
    public final Setting.Bool glide;
    public final Setting.Num glideSpeed;
    public final Setting.Bool clientColour;
    public final Setting.Colour colour;
    public final Setting.Bool fill;
    public final Setting.Mode fillType;
    public final Setting.Num fillAlpha;
    public final Setting.Mode shader;
    public final Setting.Num speed;
    public final Setting.Num shaderAlpha;
    public final Setting.Bool shaderClient;
    public final Setting.Colour shaderColour;
    private AABB shown;
    private float appear;
    private long last;
    private static final int[][] FACES =
            new int[][] {{0, 1, 2, 3}, {7, 6, 5, 4}, {0, 4, 5, 1}, {1, 5, 6, 2}, {2, 6, 7, 3}, {3, 7, 4, 0}};
    private static final Map<String, RenderPipeline> PIPELINES = new HashMap<>();
    private static final Map<String, Boolean> BROKEN = new HashMap<>();
    private static GpuBuffer params;
    private static final ByteBuffer DATA = ByteBuffer.allocateDirect(48).order(ByteOrder.nativeOrder());

    public BlockHighlight() {
        super(
                "block_overlay",
                "Выделение блока",
                "Обводка и анимированная заливка блока, на который смотришь",
                Category.WORLD,
                false);
        this.width = this.add(new Setting.Num("line_width", "Толщина линий", 2.0, 1.0, 5.0, 0.5, " px"))
                .showIf(this.outline::on);
        Setting.Mode mode = this.add(new Setting.Mode("animation2", "Анимация", "Нет", "Нет", "Пульс", "Волна"));
        this.animation = (Setting.Mode) mode.showIf(this.outline::on);
        this.glide = this.add(new Setting.Bool("glide", "Плавный переезд", true));
        Setting.Num num = this.add(new Setting.Num("glide_speed", "Скорость переезда", 0.35, 0.05, 1.0, 0.05, ""));
        this.glideSpeed = (Setting.Num) num.showIf(this.glide::on);
        this.clientColour = this.add(new Setting.Bool("client_colour", "Цвет клиента", true));
        this.colour =
                (this.add(new Setting.Colour("colour2", "Цвет", false, -1))).showIf(() -> !this.clientColour.on());
        this.fill = this.add(new Setting.Bool("fill2", "Заливка", true));
        Setting.Mode mode2 = this.add(new Setting.Mode("fill_type", "Тип заливки", "Обычная", "Обычная", "Шейдер"));
        this.fillType = (Setting.Mode) mode2.showIf(this.fill::on);
        this.fillAlpha = (this.add(new Setting.Num("fill_alpha", "Прозрачность заливки", 0.3, 0.1, 1.0, 0.05, "")))
                .showIf(() -> this.fill.on() && this.fillType.is("Обычная"));
        this.shader = (this.add(new Setting.Mode("shader", "Шейдер", "Nebula", SHADERS)))
                .showIf(() -> this.fill.on() && this.fillType.is("Шейдер"));
        this.speed = (this.add(new Setting.Num("shader_speed", "Скорость шейдера", 1.0, 0.1, 3.0, 0.1, "×")))
                .showIf(() -> this.fill.on() && this.fillType.is("Шейдер"));
        this.shaderAlpha = (this.add(new Setting.Num("shader_alpha", "Прозрачность шейдера", 1.0, 0.1, 1.0, 0.05, "")))
                .showIf(() -> this.fill.on() && this.fillType.is("Шейдер"));
        this.shaderClient = (this.add(new Setting.Bool("shader_client", "Цвет клиента для шейдера", true)))
                .showIf(() -> this.fill.on() && this.fillType.is("Шейдер"));
        this.shaderColour = (this.add(new Setting.Colour("shader_colour", "Цвет шейдера", false, 0xFF00FF78)))
                .showIf(() -> this.fill.on() && this.fillType.is("Шейдер") && !this.shaderClient.on());
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private int base() {
        return (this.clientColour.on() ? Theme.accent() : this.colour.argb()) | 0xFF000000;
    }

    protected void onDisable() {
        this.shown = null;
        this.appear = 0.0F;
    }

    private static AABB lerp(AABB a, AABB b, double t) {
        return new AABB(
                a.minX + (b.minX - a.minX) * t,
                a.minY + (b.minY - a.minY) * t,
                a.minZ + (b.minZ - a.minZ) * t,
                a.maxX + (b.maxX - a.maxX) * t,
                a.maxY + (b.maxY - a.maxY) * t,
                a.maxZ + (b.maxZ - a.maxZ) * t);
    }

    public void render(WorldRenderContext ctx, Vec3 cam) {
        if (mc().player != null) {
            long now = System.currentTimeMillis();
            float frames = (float) Math.max(1L, Math.min(now - this.last, 100L)) / 16.666666F;
            this.last = now;
            AABB hit = this.targetBox();
            if (hit != null) {
                this.shown = this.shown != null && this.appear >= 0.003921569F && this.glide.on()
                        ? lerp(this.shown, hit, (double) Math.min(1.0F, this.glideSpeed.f() * frames))
                        : hit;
            }

            this.appear = Math.max(0.0F, Math.min(1.0F, this.appear + (hit == null ? -0.22F : 0.22F) * frames));
            if (this.shown != null && !(this.appear < 0.003921569F)) {
                AABB target = this.shown;
                float time = (float) (now % 100000L) / 1000.0F;
                PoseStack.Pose pose = ctx.matrices().last();
                AABB box = this.animation.is("Пульс")
                        ? scaleBox(target, 1.0F + (float) ((Math.sin((double) now / 260.0) + 1.0) * 0.5) * 0.04F)
                        : target;
                AABB filled = box.inflate(0.01);
                if (this.fill.on() && this.fillType.is("Шейдер")) {
                    this.shaderFill(pose, cam, filled, time);
                }

                if (this.fill.on() && this.fillType.is("Обычная")) {
                    this.solidFill(
                            ctx.consumers().getBuffer(RenderTypes.debugFilledBox()),
                            pose,
                            cam,
                            filled,
                            this.fillAlpha.f() * this.appear);
                }

                if (this.outline.on()) {
                    VertexConsumer lines = ctx.consumers().getBuffer(RenderTypes.lines());
                    ShapeRenderer.renderShape(
                            ctx.matrices(),
                            lines,
                            Shapes.create(box),
                            -cam.x,
                            -cam.y,
                            -cam.z,
                            a(this.base(), this.appear),
                            this.width.f());
                    if (this.animation.is("Волна")) {
                        float wave = (float) (now % 1000L) / 1000.0F;
                        ShapeRenderer.renderShape(
                                ctx.matrices(),
                                lines,
                                Shapes.create(scaleBox(target, 1.0F + wave * 0.18F)),
                                -cam.x,
                                -cam.y,
                                -cam.z,
                                a(this.base(), (1.0F - wave) * this.appear),
                                Math.max(1.0F, this.width.f() - 0.5F));
                    }
                }
            }
        }
    }

    private static AABB scaleBox(AABB box, float scale) {
        Vec3 c = box.getCenter();
        double x = (box.maxX - box.minX) * 0.5 * (double) scale;
        double y = (box.maxY - box.minY) * 0.5 * (double) scale;
        double z = (box.maxZ - box.minZ) * 0.5 * (double) scale;
        return new AABB(c.x - x, c.y - y, c.z - z, c.x + x, c.y + y, c.z + z);
    }

    private AABB targetBox() {
        if (mc().level != null) {
            HitResult hitResult2 = mc().hitResult;
            if (hitResult2 instanceof BlockHitResult) {
                BlockHitResult hit = (BlockHitResult) hitResult2;
                if (hit.getType() == Type.BLOCK) {
                    BlockPos pos = hit.getBlockPos();
                    VoxelShape shape = mc().level.getBlockState(pos).getShape(mc().level, pos);
                    return shape.isEmpty() ? null : shape.bounds().move(pos);
                }
            }
        }

        return null;
    }

    private static int a(int c, float alpha) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F) << 24 | c & 0xFFFFFF;
    }

    private static Vec3[] corners(AABB b, Vec3 cam) {
        return new Vec3[] {
            (new Vec3(b.minX, b.minY, b.minZ)).subtract(cam),
            (new Vec3(b.maxX, b.minY, b.minZ)).subtract(cam),
            (new Vec3(b.maxX, b.minY, b.maxZ)).subtract(cam),
            (new Vec3(b.minX, b.minY, b.maxZ)).subtract(cam),
            (new Vec3(b.minX, b.maxY, b.minZ)).subtract(cam),
            (new Vec3(b.maxX, b.maxY, b.minZ)).subtract(cam),
            (new Vec3(b.maxX, b.maxY, b.maxZ)).subtract(cam),
            (new Vec3(b.minX, b.maxY, b.maxZ)).subtract(cam)
        };
    }

    private void solidFill(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, AABB b, float alpha) {
        Vec3[] p = corners(b, cam);
        int c = a(this.base(), alpha);

        for (int[] f : FACES) {
            for (int i = 0; i < 4; i++) {
                Vec3 q = p[f[i]];
                vc.addVertex(pose, (float) q.x, (float) q.y, (float) q.z).setColor(c);
            }
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("arcanicvisuals", path);
    }

    private static RenderPipeline pipeline(String file) {
        return (RenderPipeline) PIPELINES.computeIfAbsent(file, (f) -> RenderPipeline.builder()
                .withLocation(id("pipeline/bo_" + f))
                .withVertexShader(id("core/bo_world"))
                .withFragmentShader(id("core/bo_" + f))
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("BlockOverlay", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, Mode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withCull(false)
                .withDepthWrite(false)
                .build());
    }

    private void shaderFill(PoseStack.Pose pose, Vec3 cam, AABB b, float time) {
        String file = FILES[Math.max(0, this.shader.index())];
        if ((Boolean) BROKEN.getOrDefault(file, false)) {
            this.solidFillFallback(pose, cam, b);
        } else {
            GpuDevice device = RenderSystem.getDevice();
            RenderPipeline p = pipeline(file);

            try {
                if (!device.precompilePipeline(p).isValid()) {
                    BROKEN.put(file, true);
                    System.err.println("[ArcanicVisuals] block overlay shader failed: " + file);
                    return;
                }

                BufferBuilder bb = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                Vec3[] q = corners(b, cam);
                int c = -1;
                Matrix4f m = pose.pose();

                for (int[] f : FACES) {
                    for (int i = 0; i < 4; i++) {
                        Vec3 v = q[f[i]];
                        bb.addVertex(m, (float) v.x, (float) v.y, (float) v.z).setColor(c);
                    }
                }

                try (MeshData mesh = bb.buildOrThrow()) {
                    RenderTarget target = mc().getMainRenderTarget();
                    if (params == null) {
                        params = device.createBuffer(() -> "Arcanic block overlay", 136, 48L);
                    }

                    int tint = this.shaderClient.on() ? Theme.accent() : this.shaderColour.argb();
                    DATA.clear();
                    DATA.putFloat((float) (tint >> 16 & 255) / 255.0F)
                            .putFloat((float) (tint >> 8 & 255) / 255.0F)
                            .putFloat((float) (tint & 255) / 255.0F)
                            .putFloat(this.shaderAlpha.f() * this.appear);
                    DATA.putFloat(time * this.speed.f())
                            .putFloat((float) target.width)
                            .putFloat((float) target.height)
                            .putFloat(0.0F);
                    DATA.putFloat((float) cam.x)
                            .putFloat((float) cam.y)
                            .putFloat((float) cam.z)
                            .putFloat(0.0F);
                    DATA.flip();
                    CommandEncoder encoder = device.createCommandEncoder();
                    encoder.writeToBuffer(params.slice(), DATA);
                    GpuBuffer vertices =
                            DefaultVertexFormat.POSITION_COLOR.uploadImmediateVertexBuffer(mesh.vertexBuffer());
                    RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(Mode.QUADS);
                    int count = mesh.drawState().indexCount();
                    GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                            .writeTransform(
                                    RenderSystem.getModelViewMatrix(),
                                    new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
                                    new Vector3f(),
                                    new Matrix4f());

                    try (RenderPass pass = encoder.createRenderPass(
                            () -> "Arcanic block overlay",
                            target.getColorTextureView(),
                            OptionalInt.empty(),
                            target.getDepthTextureView(),
                            OptionalDouble.empty())) {
                        pass.setPipeline(p);
                        RenderSystem.bindDefaultUniforms(pass);
                        pass.setUniform("DynamicTransforms", transforms);
                        pass.setUniform("BlockOverlay", params);
                        pass.setVertexBuffer(0, vertices);
                        pass.setIndexBuffer(indices.getBuffer(count), indices.type());
                        pass.drawIndexed(0, 0, count, 1);
                    }
                }
            } catch (RuntimeException e) {
                BROKEN.put(file, true);
                System.err.println("[ArcanicVisuals] block overlay shader " + file + ": " + e);
            }
        }
    }

    private void solidFillFallback(PoseStack.Pose pose, Vec3 cam, AABB b) {
        VertexConsumer vc = mc().renderBuffers().bufferSource().getBuffer(RenderTypes.debugFilledBox());
        this.solidFill(vc, pose, cam, b, 0.3F);
    }
}
