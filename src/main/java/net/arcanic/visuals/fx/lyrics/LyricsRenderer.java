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
package net.arcanic.visuals.fx.lyrics;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
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
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.OptionalInt;
import net.arcanic.visuals.render.EmbeddedShaders;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.slf4j.LoggerFactory;

/**
 * Draws lyric words as real geometry in the world pass, with the frame's own projection (view bobbing included) and
 * the interpolated camera, so they sit still in the world while you walk and turn and the glyphs keep correct
 * perspective. Each word is shaded with its own reveal, leave, fill and glow values (EvoVisuals' lyrics font); its
 * dissolve and shimmer patterns are worked out on the word's own sheet, so they stay on the letters instead of
 * swimming across the screen. Words queued as glow go to a half size buffer first, get a dual Kawase blur and are
 * added over the frame before the letters. Nothing is allocated per frame once the buffers have grown.
 */
public final class LyricsRenderer implements LyricsFont.Sink {
    private static final float THICKNESS = 0.1F;
    private static final float HALO_THICKNESS = 0.02F;
    private static final float SMOOTHNESS = 0.34F;
    private static final float PEN_OFFSET = 0.75F;
    private static final float BASELINE_RATIO = 0.7F;
    /** Per-word uniform block: eight vec4. */
    private static final int PARAMS = 32;

    private static final int WORD_BYTES = PARAMS * 4;
    private static final int QUAD_FLOATS = 20;
    private static final int BLOOM_STEPS = 5;
    private static final float BLOOM_SPREAD = 2.5F;

    private static final RenderPipeline WORD = RenderPipeline.builder()
            .withLocation(id("pipeline/lyrics_word"))
            .withVertexShader(id("core/lyrics_word"))
            .withFragmentShader(id("core/lyrics_word"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("LyricsWord", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();
    /** Motes of light: soft camera-facing dots, added over the frame (and into the glow). */
    private static final RenderPipeline MOTE = RenderPipeline.builder()
            .withLocation(id("pipeline/lyrics_mote"))
            .withVertexShader(id("core/lyrics_mote"))
            .withFragmentShader(id("core/lyrics_mote"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, Mode.QUADS)
            .withBlend(BlendFunction.ADDITIVE)
            .withCull(false)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();

    private static final RenderPipeline DOWN = bloom("down", null);
    private static final RenderPipeline UP = bloom("up", null);
    private static final RenderPipeline ADD = bloom("add", BlendFunction.ADDITIVE);
    private static final Vector4f WHITE = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
    private static final Vector3f NO_OFFSET = new Vector3f();
    private static final Matrix4f IDENTITY = new Matrix4f();

    // the sheet of the chunk being queued, camera-relative
    private float ox, oy, oz, rx, ry, rz, dx, dy, dz;
    private int colour;

    // queued glyph quads: four corners of (x, y, z, u, v)
    private float[] quads = new float[QUAD_FLOATS * 256];
    private int[] colours = new int[256];
    private int quadCount;

    // queued draws, in the order they were queued
    private LyricsFont[] drawFont = new LyricsFont[64];
    private boolean[] drawGlow = new boolean[64];
    private int[] drawFirst = new int[64];
    private int[] drawQuads = new int[64];
    private float[] params = new float[PARAMS * 64];
    private int drawCount;
    private int glowCount;

    // queued motes: camera-relative centre, radius and colour
    private float[] motes = new float[4 * 128];
    private int[] moteColours = new int[128];
    private int moteCount;

    private final Matrix4f view = new Matrix4f();
    private final Quaternionf turn = new Quaternionf();
    private ByteBufferBuilder bytes;
    private GpuBuffer wordBuffer;
    private ByteBuffer wordData;
    private GpuBuffer bloomParams;
    private ByteBuffer bloomData;
    private TextureTarget source, quarter, eighth, quarterUp, result;
    private boolean broken;

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("arcanicvisuals", path);
    }

    private static RenderPipeline bloom(String step, BlendFunction blend) {
        RenderPipeline.Builder b = RenderPipeline.builder()
                .withLocation(id("pipeline/lyrics_bloom_" + step))
                .withVertexShader(id("core/lyrics_bloom"))
                .withFragmentShader(id("core/lyrics_bloom_" + step))
                .withUniform("LyricsBloom", UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withVertexFormat(DefaultVertexFormat.EMPTY, Mode.TRIANGLES)
                .withCull(false)
                .withDepthWrite(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
        if (blend != null) b.withBlend(blend);
        else b.withoutBlend();
        return b.build();
    }

    /** Starts a frame. */
    public void begin() {
        quadCount = 0;
        drawCount = 0;
        glowCount = 0;
        moteCount = 0;
    }

    /** Queues a mote of light at a camera-relative point, {@code radius} world units across its glow. */
    public void mote(float x, float y, float z, float radius, int colour) {
        if (moteCount == moteColours.length) {
            motes = Arrays.copyOf(motes, motes.length * 2);
            moteColours = Arrays.copyOf(moteColours, moteColours.length * 2);
        }
        int o = moteCount * 4;
        motes[o] = x;
        motes[o + 1] = y;
        motes[o + 2] = z;
        motes[o + 3] = radius;
        moteColours[moteCount++] = colour;
    }

    /** The sheet the next words are written on: its origin relative to the camera, and its right and down units. */
    public void sheet(double x, double y, double z, Vector3f right, Vector3f down) {
        ox = (float) x;
        oy = (float) y;
        oz = (float) z;
        rx = right.x;
        ry = right.y;
        rz = right.z;
        dx = down.x;
        dy = down.y;
        dz = down.z;
    }

    /**
     * Queues one word on the current sheet with its top left at sheet point (x, y), {@code width} wide. Glow words
     * must all be queued before the others (they are drawn first, into the bloom).
     */
    public void word(
            boolean toGlow,
            LyricsFont font,
            String text,
            float size,
            float width,
            int colour,
            int accent,
            float glowIntensity,
            float glowRadius,
            float pass,
            float sung,
            float gradient,
            float reveal,
            float leave,
            float seed,
            float time,
            float x,
            float y) {
        if (text.isEmpty()) return;
        int first = quadCount;
        this.colour = colour;
        font.layout(text, size, x - PEN_OFFSET, y + size * BASELINE_RATIO, this);
        if (quadCount == first) return;

        if (drawCount == drawFont.length) {
            int n = drawCount * 2;
            drawFont = Arrays.copyOf(drawFont, n);
            drawGlow = Arrays.copyOf(drawGlow, n);
            drawFirst = Arrays.copyOf(drawFirst, n);
            drawQuads = Arrays.copyOf(drawQuads, n);
            params = Arrays.copyOf(params, n * PARAMS);
        }
        int d = drawCount++;
        if (toGlow) glowCount++;
        drawFont[d] = font;
        drawGlow[d] = toGlow;
        drawFirst[d] = first;
        drawQuads[d] = quadCount - first;
        int o = d * PARAMS;
        float[] p = params;
        p[o] = ox;
        p[o + 1] = oy;
        p[o + 2] = oz;
        p[o + 3] = pass;
        p[o + 4] = rx;
        p[o + 5] = ry;
        p[o + 6] = rz;
        p[o + 7] = font.range();
        p[o + 8] = dx;
        p[o + 9] = dy;
        p[o + 10] = dz;
        p[o + 11] = size;
        p[o + 12] = pass != 0 ? HALO_THICKNESS : THICKNESS;
        p[o + 13] = SMOOTHNESS;
        p[o + 14] = reveal;
        p[o + 15] = leave;
        p[o + 16] = time;
        p[o + 17] = x;
        p[o + 18] = Math.max(width, 1.0F);
        p[o + 19] = y;
        p[o + 20] = sung;
        p[o + 21] = gradient;
        p[o + 22] = glowRadius;
        p[o + 23] = glowIntensity;
        p[o + 24] = channel(accent, 16);
        p[o + 25] = channel(accent, 8);
        p[o + 26] = channel(accent, 0);
        p[o + 27] = seed;
        p[o + 28] = channel(accent, 16);
        p[o + 29] = channel(accent, 8);
        p[o + 30] = channel(accent, 0);
        p[o + 31] = channel(accent, 24);
    }

    /** One glyph quad from the font, in sheet units; stored as camera-relative corners. */
    @Override
    public void quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
        if (quadCount == colours.length) {
            quads = Arrays.copyOf(quads, quads.length * 2);
            colours = Arrays.copyOf(colours, colours.length * 2);
        }
        int o = quadCount * QUAD_FLOATS;
        corner(o, x0, y0, u0, v0);
        corner(o + 5, x0, y1, u0, v1);
        corner(o + 10, x1, y1, u1, v1);
        corner(o + 15, x1, y0, u1, v0);
        colours[quadCount++] = colour;
    }

    private void corner(int o, float u, float v, float tu, float tv) {
        quads[o] = ox + rx * u + dx * v;
        quads[o + 1] = oy + ry * u + dy * v;
        quads[o + 2] = oz + rz * u + dz * v;
        quads[o + 3] = tu;
        quads[o + 4] = tv;
    }

    private static float channel(int argb, int shift) {
        return (argb >>> shift & 255) / 255.0F;
    }

    /** Draws everything queued this frame: the glow first (added at {@code glowStrength}), then the words. */
    public void flush(Camera cam, float glowStrength) {
        if (broken || (quadCount == 0 || drawCount == 0) && moteCount == 0) return;
        try {
            if (!EmbeddedShaders.compile(WORD).isValid()
                    || !EmbeddedShaders.compile(DOWN).isValid()
                    || !EmbeddedShaders.compile(UP).isValid()
                    || !EmbeddedShaders.compile(ADD).isValid()
                    || !EmbeddedShaders.compile(MOTE).isValid()) {
                broken = true;
                LoggerFactory.getLogger("ArcanicLyrics").error("Lyrics shaders failed to compile");
                return;
            }
            // a texture loaded for the first time uploads through its own command: never inside an open pass
            Minecraft mc = Minecraft.getInstance();
            mc.getTextureManager().getTexture(LyricsFont.MEDIUM.atlas);
            mc.getTextureManager().getTexture(LyricsFont.BOLD.atlas);
            draw(cam, glowStrength);
        } catch (RuntimeException e) {
            broken = true;
            LoggerFactory.getLogger("ArcanicLyrics").error("Lyrics renderer off", e);
        }
    }

    private void draw(Camera cam, float glowStrength) {
        Minecraft mc = Minecraft.getInstance();
        GpuDevice device = RenderSystem.getDevice();
        RenderTarget main = mc.getMainRenderTarget();

        if (bytes == null) bytes = new ByteBufferBuilder(1 << 16);
        BufferBuilder bb = new BufferBuilder(bytes, Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int q = 0; q < quadCount; q++) {
            int o = q * QUAD_FLOATS, col = colours[q];
            for (int v = 0; v < 4; v++) {
                int i = o + v * 5;
                bb.addVertex(quads[i], quads[i + 1], quads[i + 2])
                        .setUv(quads[i + 3], quads[i + 4])
                        .setColor(col);
            }
        }
        // the motes after the glyph quads, each a square facing the camera
        Vector3fc left = cam.leftVector(), up = cam.upVector();
        for (int m = 0; m < moteCount; m++) {
            int o = m * 4, col = moteColours[m];
            float r = motes[o + 3];
            float rx = -left.x() * r, ry = -left.y() * r, rz = -left.z() * r;
            float ux = up.x() * r, uy = up.y() * r, uz = up.z() * r;
            float cx = motes[o], cy = motes[o + 1], cz = motes[o + 2];
            bb.addVertex(cx - rx + ux, cy - ry + uy, cz - rz + uz).setUv(0, 0).setColor(col);
            bb.addVertex(cx - rx - ux, cy - ry - uy, cz - rz - uz).setUv(0, 1).setColor(col);
            bb.addVertex(cx + rx - ux, cy + ry - uy, cz + rz - uz).setUv(1, 1).setColor(col);
            bb.addVertex(cx + rx + ux, cy + ry + uy, cz + rz + uz).setUv(1, 0).setColor(col);
        }
        try (MeshData mesh = bb.build()) {
            if (mesh == null) return;

            int stride = align(WORD_BYTES, device.getUniformOffsetAlignment());
            long need = (long) stride * Math.max(1, drawCount);
            if (wordBuffer == null || wordBuffer.size() < need) {
                if (wordBuffer != null) wordBuffer.close();
                long size = Math.max(need * 2, (long) stride * 64);
                wordBuffer = device.createBuffer(() -> "Arcanic lyrics words", 136, size);
                wordData = ByteBuffer.allocateDirect((int) size).order(ByteOrder.nativeOrder());
            }
            wordData.clear();
            for (int d = 0; d < drawCount; d++) {
                wordData.position(d * stride);
                int o = d * PARAMS;
                for (int i = 0; i < PARAMS; i++) wordData.putFloat(params[o + i]);
            }
            wordData.position(0).limit((int) need);
            CommandEncoder encoder = device.createCommandEncoder();
            if (drawCount > 0) encoder.writeToBuffer(wordBuffer.slice(0, need), wordData);

            // the camera's view rotation, exactly as the level is drawn with; the projection (with view bobbing)
            // comes from the frame's default uniforms
            view.rotation(cam.rotation().conjugate(turn));
            GpuBufferSlice transforms =
                    RenderSystem.getDynamicUniforms().writeTransform(view, WHITE, NO_OFFSET, IDENTITY);
            GpuBuffer vertices =
                    DefaultVertexFormat.POSITION_TEX_COLOR.uploadImmediateVertexBuffer(mesh.vertexBuffer());
            RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(Mode.QUADS);
            GpuBuffer indexBuffer = indices.getBuffer((quadCount + moteCount) * 6);
            GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

            if ((glowCount > 0 || moteCount > 0) && glowStrength > 0) {
                targets(main.width, main.height);
                encoder.clearColorTexture(source.getColorTexture(), 0);
                try (RenderPass pass = encoder.createRenderPass(
                        () -> "Arcanic lyrics glow", source.getColorTextureView(), OptionalInt.empty())) {
                    if (glowCount > 0) words(pass, true, stride, transforms, vertices, indexBuffer, indices, sampler);
                    motes(pass, transforms, vertices, indexBuffer, indices);
                }
                bloom(encoder, device, main, sampler, glowStrength);
            }
            if (drawCount > glowCount || moteCount > 0) {
                try (RenderPass pass = encoder.createRenderPass(
                        () -> "Arcanic lyrics", main.getColorTextureView(), OptionalInt.empty())) {
                    if (drawCount > glowCount) {
                        words(pass, false, stride, transforms, vertices, indexBuffer, indices, sampler);
                    }
                    motes(pass, transforms, vertices, indexBuffer, indices);
                }
            }
        }
    }

    private void words(
            RenderPass pass,
            boolean glowPass,
            int stride,
            GpuBufferSlice transforms,
            GpuBuffer vertices,
            GpuBuffer indexBuffer,
            RenderSystem.AutoStorageIndexBuffer indices,
            GpuSampler sampler) {
        pass.setPipeline(WORD);
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", transforms);
        pass.setVertexBuffer(0, vertices);
        pass.setIndexBuffer(indexBuffer, indices.type());
        Minecraft mc = Minecraft.getInstance();
        LyricsFont bound = null;
        for (int d = 0; d < drawCount; d++) {
            if (drawGlow[d] != glowPass) continue;
            if (drawFont[d] != bound) {
                bound = drawFont[d];
                pass.bindTexture(
                        "Sampler0",
                        mc.getTextureManager().getTexture(bound.atlas).getTextureView(),
                        sampler);
            }
            pass.setUniform("LyricsWord", wordBuffer.slice((long) d * stride, WORD_BYTES));
            pass.drawIndexed(0, drawFirst[d] * 6, drawQuads[d] * 6, 1);
        }
    }

    private void motes(
            RenderPass pass,
            GpuBufferSlice transforms,
            GpuBuffer vertices,
            GpuBuffer indexBuffer,
            RenderSystem.AutoStorageIndexBuffer indices) {
        if (moteCount == 0) return;
        pass.setPipeline(MOTE);
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", transforms);
        pass.setVertexBuffer(0, vertices);
        pass.setIndexBuffer(indexBuffer, indices.type());
        pass.drawIndexed(0, quadCount * 6, moteCount * 6, 1);
    }

    /** Down twice and up twice from the half size capture, then added over the frame. */
    private void bloom(CommandEncoder encoder, GpuDevice device, RenderTarget main, GpuSampler sampler, float k) {
        int stride = align(16, device.getUniformOffsetAlignment());
        if (bloomParams == null) {
            bloomParams = device.createBuffer(() -> "Arcanic lyrics bloom", 136, (long) stride * BLOOM_STEPS);
            bloomData = ByteBuffer.allocateDirect(stride * BLOOM_STEPS).order(ByteOrder.nativeOrder());
        }
        bloomData.clear();
        putStep(0, stride, source, k);
        putStep(1, stride, quarter, k);
        putStep(2, stride, eighth, k);
        putStep(3, stride, quarterUp, k);
        putStep(4, stride, result, k);
        bloomData.position(0).limit(stride * BLOOM_STEPS);
        encoder.writeToBuffer(bloomParams.slice(), bloomData);

        step(encoder, DOWN, source, quarter, 0, stride, sampler);
        step(encoder, DOWN, quarter, eighth, 1, stride, sampler);
        step(encoder, UP, eighth, quarterUp, 2, stride, sampler);
        step(encoder, UP, quarterUp, result, 3, stride, sampler);
        try (RenderPass pass = encoder.createRenderPass(
                () -> "Arcanic lyrics bloom", main.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(ADD);
            pass.setUniform("LyricsBloom", bloomParams.slice((long) 4 * stride, 16));
            pass.bindTexture("Sampler0", result.getColorTextureView(), sampler);
            pass.draw(0, 3);
        }
    }

    private void putStep(int index, int stride, TextureTarget from, float k) {
        bloomData.position(index * stride);
        bloomData
                .putFloat(1.0F / from.width)
                .putFloat(1.0F / from.height)
                .putFloat(BLOOM_SPREAD)
                .putFloat(k);
    }

    private void step(
            CommandEncoder encoder,
            RenderPipeline pipeline,
            TextureTarget from,
            TextureTarget to,
            int index,
            int stride,
            GpuSampler sampler) {
        try (RenderPass pass =
                encoder.createRenderPass(() -> "Arcanic lyrics bloom", to.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(pipeline);
            pass.setUniform("LyricsBloom", bloomParams.slice((long) index * stride, 16));
            pass.bindTexture("Sampler0", from.getColorTextureView(), sampler);
            pass.draw(0, 3);
        }
    }

    private void targets(int w, int h) {
        int hw = Math.max(1, w / 2), hh = Math.max(1, h / 2);
        source = ensure(source, "source", hw, hh);
        quarter = ensure(quarter, "quarter", Math.max(1, w / 4), Math.max(1, h / 4));
        eighth = ensure(eighth, "eighth", Math.max(1, w / 8), Math.max(1, h / 8));
        quarterUp = ensure(quarterUp, "quarter up", quarter.width, quarter.height);
        result = ensure(result, "result", hw, hh);
    }

    private static TextureTarget ensure(TextureTarget t, String name, int w, int h) {
        if (t != null && t.width == w && t.height == h) return t;
        if (t != null) t.destroyBuffers();
        return new TextureTarget("Arcanic lyrics bloom " + name, w, h, false);
    }

    private static int align(int size, int alignment) {
        int a = Math.max(1, alignment);
        return (size + a - 1) / a * a;
    }
}
