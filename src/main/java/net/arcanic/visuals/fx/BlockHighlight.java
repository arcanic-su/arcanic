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
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Block highlight module
 *
 * Outlines and fills the block you are looking at.
 */
public final class BlockHighlight extends Module {
    private static final String[] SHADERS = {
        "Nebula", "Cosmos", "Web", "Plasma", "LiquidGlass", "Matrix", "LivingArmor", "AlienNebula", "Holy"
    };
    private static final String[] FILES = {
        "nebula", "cosmos", "web", "plasma", "glass", "matrix", "armor", "alien", "holy"
    };

    public final Setting.Bool outline = add(new Setting.Bool("outline", "Обводка", true));
    public final Setting.Num width = add(new Setting.Num("line_width", "Толщина линий", 2, 1, 5, .5, " px"))
            .showIf(outline::on);
    public final Setting.Mode animation = add(new Setting.Mode(
                    "animation2", "Анимация", "Нет", "Нет", "Пульс", "Волна"))
            .showIf(outline::on);
    public final Setting.Bool clientColour = add(new Setting.Bool("client_colour", "Цвет клиента", true));
    public final Setting.Colour colour =
            add(new Setting.Colour("colour2", "Цвет", false, 0xFFFFFFFF)).showIf(() -> !clientColour.on());
    public final Setting.Bool fill = add(new Setting.Bool("fill2", "Заливка", true));
    public final Setting.Mode fillType = add(new Setting.Mode(
                    "fill_type", "Тип заливки", "Обычная", "Обычная", "Шейдер"))
            .showIf(fill::on);
    public final Setting.Num fillAlpha = add(new Setting.Num("fill_alpha", "Прозрачность заливки", .3, .1, 1, .05, ""))
            .showIf(() -> fill.on() && fillType.is("Обычная"));
    public final Setting.Mode shader = add(new Setting.Mode("shader", "Шейдер", "Nebula", SHADERS))
            .showIf(() -> fill.on() && fillType.is("Шейдер"));
    public final Setting.Num speed = add(new Setting.Num("shader_speed", "Скорость шейдера", 1, .1, 3, .1, "×"))
            .showIf(() -> fill.on() && fillType.is("Шейдер"));
    public final Setting.Num shaderAlpha = add(new Setting.Num(
                    "shader_alpha", "Прозрачность шейдера", 1, .1, 1, .05, ""))
            .showIf(() -> fill.on() && fillType.is("Шейдер"));
    public final Setting.Bool shaderClient = add(new Setting.Bool("shader_client", "Цвет клиента для шейдера", true))
            .showIf(() -> fill.on() && fillType.is("Шейдер"));
    public final Setting.Colour shaderColour = add(new Setting.Colour(
                    "shader_colour", "Цвет шейдера", false, 0xFF00FF78))
            .showIf(() -> fill.on() && fillType.is("Шейдер") && !shaderClient.on());

    public BlockHighlight() {
        super(
                "block_overlay",
                "Выделение блока",
                "Обводка и анимированная заливка блока, на который смотришь",
                Category.WORLD,
                false);
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private int base() {
        return (clientColour.on() ? Theme.accent() : colour.argb()) | 0xFF000000;
    }

    public void render(WorldRenderContext ctx, Vec3 cam) {
        AABB target = targetBox();
        if (target == null || mc().player == null) return;
        long now = System.currentTimeMillis();
        float time = (now % 100_000L) / 1000f;
        PoseStack.Pose pose = ctx.matrices().last();
        // The current hit is read every frame. Only the selected box's size animates.
        AABB box = animation.is("Пульс")
                ? scaleBox(target, 1f + (float) ((Math.sin(now / 260.0) + 1) * .5) * .04f)
                : target;
        AABB filled = box.inflate(.01);
        if (fill.on() && fillType.is("Шейдер")) shaderFill(pose, cam, filled, time);
        if (fill.on() && fillType.is("Обычная"))
            solidFill(ctx.consumers().getBuffer(RenderTypes.debugFilledBox()), pose, cam, filled, fillAlpha.f());
        if (outline.on()) {
            VertexConsumer lines = ctx.consumers().getBuffer(RenderTypes.lines());
            ShapeRenderer.renderShape(
                    ctx.matrices(), lines, Shapes.create(box), -cam.x, -cam.y, -cam.z, base(), width.f());
            if (animation.is("Волна")) {
                float wave = (now % 1000L) / 1000f;
                ShapeRenderer.renderShape(
                        ctx.matrices(),
                        lines,
                        Shapes.create(scaleBox(target, 1f + wave * .18f)),
                        -cam.x,
                        -cam.y,
                        -cam.z,
                        a(base(), 1f - wave),
                        Math.max(1f, width.f() - .5f));
            }
        }
    }

    private static AABB scaleBox(AABB box, float scale) {
        Vec3 c = box.getCenter();
        double x = (box.maxX - box.minX) * .5 * scale;
        double y = (box.maxY - box.minY) * .5 * scale;
        double z = (box.maxZ - box.minZ) * .5 * scale;
        return new AABB(c.x - x, c.y - y, c.z - z, c.x + x, c.y + y, c.z + z);
    }

    private AABB targetBox() {
        if (mc().level == null
                || !(mc().hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = hit.getBlockPos();
        VoxelShape shape = mc().level.getBlockState(pos).getShape(mc().level, pos);
        return shape.isEmpty() ? null : shape.bounds().move(pos);
    }

    private static int a(int c, float alpha) {
        return (Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24) | (c & 0xFFFFFF);
    }

    private static final int[][] FACES = {
        {0, 1, 2, 3}, {7, 6, 5, 4}, {0, 4, 5, 1}, {1, 5, 6, 2}, {2, 6, 7, 3}, {3, 7, 4, 0}
    };

    private static Vec3[] corners(AABB b, Vec3 cam) {
        return new Vec3[] {
            new Vec3(b.minX, b.minY, b.minZ).subtract(cam),
            new Vec3(b.maxX, b.minY, b.minZ).subtract(cam),
            new Vec3(b.maxX, b.minY, b.maxZ).subtract(cam),
            new Vec3(b.minX, b.minY, b.maxZ).subtract(cam),
            new Vec3(b.minX, b.maxY, b.minZ).subtract(cam),
            new Vec3(b.maxX, b.maxY, b.minZ).subtract(cam),
            new Vec3(b.maxX, b.maxY, b.maxZ).subtract(cam),
            new Vec3(b.minX, b.maxY, b.maxZ).subtract(cam)
        };
    }

    private void solidFill(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, AABB b, float alpha) {
        Vec3[] p = corners(b, cam);
        int c = a(base(), alpha);
        for (int[] f : FACES)
            for (int i = 0; i < 4; i++) {
                Vec3 q = p[f[i]];
                vc.addVertex(pose, (float) q.x, (float) q.y, (float) q.z).setColor(c);
            }
    }

    private static final Map<String, RenderPipeline> PIPELINES = new HashMap<>();
    private static final Map<String, Boolean> BROKEN = new HashMap<>();
    private static GpuBuffer params;
    private static final ByteBuffer DATA = ByteBuffer.allocateDirect(48).order(ByteOrder.nativeOrder());

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("arcanicvisuals", path);
    }

    private static RenderPipeline pipeline(String file) {
        return PIPELINES.computeIfAbsent(file, f -> RenderPipeline.builder()
                .withLocation(id("pipeline/bo_" + f))
                .withVertexShader(id("core/bo_world"))
                .withFragmentShader(id("core/bo_" + f))
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("BlockOverlay", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withCull(false)
                .withDepthWrite(false)
                .build());
    }

    private void shaderFill(PoseStack.Pose pose, Vec3 cam, AABB b, float time) {
        String file = FILES[Math.max(0, shader.index())];
        if (BROKEN.getOrDefault(file, false)) {
            solidFillFallback(pose, cam, b);
            return;
        }
        var device = RenderSystem.getDevice();
        RenderPipeline p = pipeline(file);
        try {
            if (!device.precompilePipeline(p).isValid()) {
                BROKEN.put(file, true);
                System.err.println("[ArcanicVisuals] block overlay shader failed: " + file);
                return;
            }
            BufferBuilder bb =
                    Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            Vec3[] q = corners(b, cam);
            int c = 0xFFFFFFFF;
            Matrix4f m = pose.pose();
            for (int[] f : FACES)
                for (int i = 0; i < 4; i++) {
                    Vec3 v = q[f[i]];
                    bb.addVertex(m, (float) v.x, (float) v.y, (float) v.z).setColor(c);
                }
            try (MeshData mesh = bb.buildOrThrow()) {
                var target = mc().getMainRenderTarget();
                if (params == null)
                    params = device.createBuffer(
                            () -> "Arcanic block overlay", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 48);
                int tint = (shaderClient.on() ? Theme.accent() : shaderColour.argb());
                DATA.clear();
                DATA.putFloat(((tint >> 16) & 255) / 255f)
                        .putFloat(((tint >> 8) & 255) / 255f)
                        .putFloat((tint & 255) / 255f)
                        .putFloat(shaderAlpha.f());
                DATA.putFloat(time * speed.f())
                        .putFloat(target.width)
                        .putFloat(target.height)
                        .putFloat(0);
                DATA.putFloat((float) cam.x)
                        .putFloat((float) cam.y)
                        .putFloat((float) cam.z)
                        .putFloat(0);
                DATA.flip();
                var encoder = device.createCommandEncoder();
                encoder.writeToBuffer(params.slice(), DATA);
                GpuBuffer vertices =
                        DefaultVertexFormat.POSITION_COLOR.uploadImmediateVertexBuffer(mesh.vertexBuffer());
                var indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                int count = mesh.drawState().indexCount();
                var transforms = RenderSystem.getDynamicUniforms()
                        .writeTransform(
                                RenderSystem.getModelViewMatrix(),
                                new Vector4f(1, 1, 1, 1),
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

    private void solidFillFallback(PoseStack.Pose pose, Vec3 cam, AABB b) {
        var vc = mc().renderBuffers().bufferSource().getBuffer(RenderTypes.debugFilledBox());
        solidFill(vc, pose, cam, b, .3f);
    }
}
