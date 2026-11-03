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
package net.arcanic.visuals.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Sync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

public final class Wearables extends Module {
    private static final List<Item> CATALOGUE = loadCatalogue();
    private static final int FULL_BRIGHT = 15728880;
    public final Setting.Mode design;
    private final List<Item> items = new ArrayList<>();
    private static final String[] SLOTS = new String[] {"wings", "backpack", "companion", "headwear"};
    private static final Map<Integer, Motion> MOTION = new HashMap<>();
    private static long sweptAt;

    public static Identifier icon(Item it) {
        return Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/wear/icon_" + it.n() + ".png");
    }

    public Wearables(String id, String name, String description, String type) {
        super(id, name, description, Category.COSMETICS, false);

        for (Item it : CATALOGUE) {
            if (it.type().equals(type)) {
                this.items.add(it);
            }
        }

        String[] names = (String[]) this.items.stream().map(Item::name).toArray((x$0) -> new String[x$0]);
        this.design = this.add(new Setting.Mode(
                "design", "Модель", names.length > 0 ? names[0] : "—", names.length > 0 ? names : new String[] {"—"}));
    }

    public List<Item> items() {
        return this.items;
    }

    public static Item byNumber(String n) {
        if (n == null) {
            return null;
        } else {
            for (Item it : CATALOGUE) {
                if (String.valueOf(it.n()).equals(n)) {
                    return it;
                }
            }

            return null;
        }
    }

    public Item current() {
        for (Item it : this.items) {
            if (this.design.is(it.name())) {
                return it;
            }
        }

        return this.items.isEmpty() ? null : (Item) this.items.get(0);
    }

    private static List<Item> loadCatalogue() {
        List<Item> out = new ArrayList<>();

        try {
            InputStream in = Wearables.class.getResourceAsStream("/assets/arcanicvisuals/wear/index.json");

            Object var8;
            label71:
            {
                try {
                    if (in == null) {
                        var8 = out;
                        break label71;
                    }

                    for (JsonElement e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                            .getAsJsonArray()) {
                        JsonObject o = e.getAsJsonObject();
                        out.add(new Item(
                                o.get("n").getAsInt(),
                                o.get("name").getAsString(),
                                o.get("type").getAsString(),
                                num(o, "pos", 1.0F) < 0.0F ? 1 : (int) num(o, "pos", 1.0F),
                                (int) num(o, "cat", 1.0F),
                                num(o, "scale", 1.0F),
                                num(o, "x", 0.0F),
                                num(o, "y", 0.0F),
                                num(o, "z", 0.0F),
                                num(o, "yaw", 0.0F),
                                num(o, "pitch", 0.0F),
                                num(o, "roll", 0.0F)));
                    }
                } catch (Throwable var6) {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (Throwable var5) {
                            var6.addSuppressed(var5);
                        }
                    }

                    throw var6;
                }

                if (in != null) {
                    in.close();
                }

                return out;
            }

            if (in != null) {
                in.close();
            }

            return (List<Item>) var8;
        } catch (Exception e) {
            System.err.println("[ArcanicVisuals] wearables: " + String.valueOf(e));
            return out;
        }
    }

    private static float num(JsonObject o, String k, float def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsFloat() : def;
    }

    public static void afterBody(
            EntityModel<?> model, LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector) {
        if (state instanceof AvatarRenderState a) {
            if (model instanceof PlayerModel pm) {
                if (!a.isInvisible && !a.isSpectator) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null) {
                        return;
                    }

                    Item[] worn = new Item[SLOTS.length];
                    boolean any = false;
                    Entity entity;
                    if (a.id == mc.player.getId()) {
                        entity = mc.player;
                        Wearables[] mods = new Wearables[] {Modules.WINGS, Modules.BACKPACKS, null, Modules.HEADWEAR};

                        for (int i = 0; i < mods.length; ++i) {
                            if (mods[i] != null && mods[i].enabled()) {
                                any |= (worn[i] = mods[i].current()) != null;
                            }
                        }
                    } else {
                        entity = mc.level == null ? null : mc.level.getEntity(a.id);
                        Sync.Remote remote = entity == null ? null : Sync.of(entity);
                        if (remote == null) {
                            return;
                        }

                        for (int i = 0; i < SLOTS.length; ++i) {
                            if (!SLOTS[i].equals("companion")) {
                                any |= (worn[i] = byNumber(remote.worn(SLOTS[i]))) != null;
                            }
                        }
                    }

                    if (!any) {
                        return;
                    }

                    Motion mo = motion(a, entity);
                    long ms = System.currentTimeMillis();
                    Drive d = new Drive(
                            (double) (ms % 3600000L) / 1000.0 + (double) mo.phase,
                            a.walkAnimationPos,
                            mo.walk,
                            mo.air,
                            mo.sneak,
                            Mth.clamp(a.capeLean / 2.0F + a.capeFlap, -10.0F, 90.0F),
                            a.capeLean2 / 2.0F,
                            ms);

                    for (Item it : worn) {
                        if (it != null) {
                            render(it, pm, pose, collector, a.lightCoords, d, mo.posed, a);
                        }
                    }

                    return;
                }
            }
        }
    }

    private static Motion motion(AvatarRenderState a, Entity e) {
        long now = System.nanoTime();
        if (now - sweptAt > 5000000000L) {
            sweptAt = now;
            MOTION.values().removeIf((m) -> now - m.seen > 10000000000L);
        }

        Motion mo;
        float dt;
        boolean var10000;
        label54:
        {
            mo = (Motion) MOTION.computeIfAbsent(a.id, Motion::new);
            dt = mo.at == 0L ? 1.0F : Math.min(0.25F, (float) (now - mo.at) / 1.0E9F);
            mo.at = mo.seen = now;
            if (!a.isFallFlying) {
                label49:
                {
                    if (e != null && !e.onGround() && !e.isInWater() && !e.isPassenger()) {
                        if (!(e instanceof LivingEntity)) {
                            break label49;
                        }

                        LivingEntity le = (LivingEntity) e;
                        if (!le.onClimbable()) {
                            break label49;
                        }
                    }

                    var10000 = false;
                    break label54;
                }
            }

            var10000 = true;
        }

        boolean airborne = var10000;
        mo.walk += (Mth.clamp(a.walkAnimationSpeed * 1.6F, 0.0F, 1.0F) - mo.walk)
                * (1.0F - (float) Math.exp((double) (-dt * 6.0F)));
        mo.air += ((float) (airborne ? 1 : 0) - mo.air) * (1.0F - (float) Math.exp((double) (-dt * 5.0F)));
        mo.sneak += ((float) (a.isCrouching ? 1 : 0) - mo.sneak) * (1.0F - (float) Math.exp((double) (-dt * 8.0F)));
        return mo;
    }

    static void render(
            Item it,
            PlayerModel pm,
            PoseStack pose,
            SubmitNodeCollector collector,
            int light,
            Drive d,
            Map<Integer, float[]> posed,
            AvatarRenderState a) {
        Geo geo = Wearables.Geo.get(it.n());
        if (geo != null && geo.quadCount != 0) {
            pose.pushPose();
            float lift = 0.0F;
            float push = 0.0F;
            switch (it.pos()) {
                case 1:
                    pm.body.translateAndRotate(pose);
                    lift = -0.3F;
                    if (a != null && !a.chestEquipment.isEmpty()) {
                        push = 0.0625F;
                    }
                    break;
                case 2:
                    pm.head.translateAndRotate(pose);
                    lift = it.category() == 3 ? 0.5F : 0.0F;
                    if (a != null && !a.headEquipment.isEmpty()) {
                        lift += 0.0625F;
                    }
                    break;
                case 3:
                    lift = 0.75F;
                    break;
                case 4:
                    pm.rightArm.translateAndRotate(pose);
                    lift = -0.25F;
                    break;
                case 5:
                    pm.leftArm.translateAndRotate(pose);
                    lift = -0.25F;
                    break;
                case 6:
                    pm.rightLeg.translateAndRotate(pose);
                    lift = -0.35F;
                    break;
                case 7:
                    pm.leftLeg.translateAndRotate(pose);
                    lift = -0.35F;
            }

            pose.mulPose((Quaternionfc) Axis.ZP.rotationDegrees(180.0F));
            pose.translate(it.x(), it.y() + lift, it.z() + push);
            if (it.yaw() != 0.0F) {
                pose.mulPose((Quaternionfc) Axis.YP.rotationDegrees(it.yaw()));
            }

            if (it.pitch() != 0.0F) {
                pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees(it.pitch()));
            }

            if (it.roll() != 0.0F) {
                pose.mulPose((Quaternionfc) Axis.ZP.rotationDegrees(it.roll()));
            }

            pose.scale(it.scale(), it.scale(), it.scale());
            float[] quads = (float[]) posed.computeIfAbsent(it.n(), (k) -> new float[geo.quadCount * 32]);
            geo.pose(d, quads);
            long ms = d.millis();

            for (int mode = 0; mode < 4; ++mode) {
                if (geo.hasMode[mode]) {
                    int l = mode != 1 && mode != 3 ? light : 15728880;
                    int m = mode;
                    collector.submitCustomGeometry(
                            pose, geo.renderType(mode), (pp, vc) -> geo.emit(quads, m, ms, l, pp, vc));
                }
            }

            pose.popPose();
        }
    }

    public static record Item(
            int n,
            String name,
            String type,
            int pos,
            int category,
            float scale,
            float x,
            float y,
            float z,
            float yaw,
            float pitch,
            float roll) {}

    static final class Motion {
        float walk;
        float air;
        float sneak;
        long at;
        long seen;
        final float phase;
        final Map<Integer, float[]> posed = new HashMap<>();

        Motion(int id) {
            this.phase = (float) id * 0.618034F % 1.0F * 10.0F;
        }
    }

    static record Drive(
            double seconds, float walkPos, float walk, float air, float sneak, float lean, float side, long millis) {}

    static final class Geo {
        private static final Map<Integer, Geo> CACHE = new HashMap<>();
        static final int SOLID = 0;
        static final int GLOW = 1;
        static final int CLEAR = 2;
        static final int SHINE = 3;
        static final int MODES = 4;
        static final int IDLE = 0;
        static final int WALK = 1;
        static final int AIR = 2;
        static final int SNEAK = 3;
        static final int LAYERS = 4;
        static final float STRIDE = 9.43138F;
        static final int Q = 32;
        Identifier texture;
        final List<Bone> roots = new ArrayList<>();
        int quadCount;
        float textureHeight = 64.0F;
        final float[] length = new float[4];
        final boolean[] has = new boolean[4];
        final boolean[] stride = new boolean[4];
        final boolean[] hasMode = new boolean[4];
        int frames = 1;
        float frameMs = 150.0F;
        int[] quadMode;
        int[] quadFrames;
        float[] quadTicks;
        float[] quadBand;
        private float[] bounds;
        private final Matrix4f[] stack = new Matrix4f[48];
        private final Vector3f tmp = new Vector3f();
        private final float[] key = new float[3];
        private final float[] times = new float[4];
        private final float[] weights = new float[4];
        private final float[] r = new float[3];
        private final float[] p = new float[3];
        private final float[] s = new float[3];
        private static final String[] FACES = new String[] {"west", "east", "down", "up", "north", "south"};
        private static final float[][] NORMALS = new float[][] {
            {-1.0F, 0.0F, 0.0F},
            {1.0F, 0.0F, 0.0F},
            {0.0F, -1.0F, 0.0F},
            {0.0F, 1.0F, 0.0F},
            {0.0F, 0.0F, -1.0F},
            {0.0F, 0.0F, 1.0F}
        };

        static Geo get(int n) {
            if (!CACHE.containsKey(n)) {
                CACHE.put(n, load(n));
            }

            return (Geo) CACHE.get(n);
        }

        float frameShift(long ms) {
            return this.frames <= 1
                    ? 0.0F
                    : (float) (ms / (long) Math.max(1.0F, this.frameMs) % (long) this.frames) / (float) this.frames;
        }

        float shift(int k, long ms) {
            int f = this.quadFrames[k];
            return f <= 1
                    ? 0.0F
                    : (float) ((int) (ms / (long) Math.max(1.0F, this.quadTicks[k] * 50.0F) % (long) f))
                            * this.quadBand[k];
        }

        RenderType renderType(int mode) {
            RenderType var10000;
            switch (mode) {
                case 2 -> var10000 = RenderTypes.entityTranslucent(this.texture);
                case 3 -> var10000 = RenderTypes.entityTranslucentEmissive(this.texture);
                default -> var10000 = RenderTypes.entityCutoutNoCull(this.texture);
            }

            return var10000;
        }

        void emit(float[] q, int mode, long ms, int light, PoseStack.Pose pp, VertexConsumer vc) {
            float all = this.frameShift(ms);
            int k = 0;

            for (int i = 0; k < this.quadCount; i += 32) {
                if (this.quadMode[k] == mode) {
                    float dv = all + this.shift(k, ms);

                    for (int c = 0; c < 4; ++c) {
                        int o = i + c * 8;
                        vc.addVertex(pp, q[o], q[o + 1], q[o + 2])
                                .setColor(-1)
                                .setUv(q[o + 3], q[o + 4] + dv)
                                .setOverlay(OverlayTexture.NO_OVERLAY)
                                .setLight(light)
                                .setNormal(pp, q[o + 5], q[o + 6], q[o + 7]);
                    }
                }

                ++k;
            }
        }

        float[] bounds() {
            if (this.bounds != null) {
                return this.bounds;
            } else {
                float[] q = new float[this.quadCount * 32];
                this.pose(new Drive(0.0, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0L), q);
                float[] b = new float[] {
                    Float.MAX_VALUE,
                    Float.MAX_VALUE,
                    Float.MAX_VALUE,
                    -Float.MAX_VALUE,
                    -Float.MAX_VALUE,
                    -Float.MAX_VALUE
                };

                for (int i = 0; i < q.length; i += 32) {
                    for (int c = 0; c < 4; ++c) {
                        int o = i + c * 8;
                        float x = -q[o];
                        float y = q[o + 1];
                        float z = q[o + 2];
                        b[0] = Math.min(b[0], x);
                        b[1] = Math.min(b[1], y);
                        b[2] = Math.min(b[2], z);
                        b[3] = Math.max(b[3], x);
                        b[4] = Math.max(b[4], y);
                        b[5] = Math.max(b[5], z);
                    }
                }

                return this.bounds = b;
            }
        }

        void pose(Drive d, float[] out) {
            this.weights[0] = 1.0F;
            this.weights[1] = d.walk();
            this.weights[2] = d.air();
            this.weights[3] = d.sneak();

            for (int l = 0; l < 4; ++l) {
                if (this.has[l]) {
                    double t = this.stride[l] ? (double) (d.walkPos() / 9.43138F * this.length[l]) : d.seconds();
                    this.times[l] =
                            (float) ((t % (double) this.length[l] + (double) this.length[l]) % (double) this.length[l]);
                }
            }

            int[] at = new int[] {0};
            Matrix4f root = this.matrix(0).identity();

            for (Bone b : this.roots) {
                this.bone(b, root, 1, d, at, out);
            }
        }

        private Matrix4f matrix(int depth) {
            if (this.stack[depth] == null) {
                this.stack[depth] = new Matrix4f();
            }

            return this.stack[depth];
        }

        private void channel(Track t, float time, float w, float[] v) {
            if (t != null) {
                t.at(time, this.key);
                if (this.key[0] == this.key[0]) {
                    for (int c = 0; c < 3; ++c) {
                        v[c] += (this.key[c] - v[c]) * w;
                    }
                }
            }
        }

        private void bone(Bone b, Matrix4f parent, int depth, Drive d, int[] at, float[] out) {
            if (depth < this.stack.length - 2) {
                Matrix4f m = this.matrix(depth).set(parent);
                this.r[0] = this.r[1] = this.r[2] = this.p[0] = this.p[1] = this.p[2] = 0.0F;
                this.s[0] = this.s[1] = this.s[2] = 1.0F;

                for (int l = 0; l < 4; ++l) {
                    Track[] tr = b.tracks[l];
                    float w = this.weights[l];
                    if (tr != null && !(w <= 0.001F)) {
                        this.channel(tr[0], this.times[l], w, this.r);
                        this.channel(tr[1], this.times[l], w, this.p);
                        this.channel(tr[2], this.times[l], w, this.s);
                    }
                }

                float[] var10000 = this.r;
                var10000[0] += b.swingX * d.lean();
                var10000 = this.r;
                var10000[2] += b.swingZ * d.side();
                if (this.p[0] != 0.0F || this.p[1] != 0.0F || this.p[2] != 0.0F) {
                    m.translate(-this.p[0] / 16.0F, this.p[1] / 16.0F, this.p[2] / 16.0F);
                }

                float rx = b.rx + (float) Math.toRadians((double) (-this.r[0]));
                float ry = b.ry + (float) Math.toRadians((double) (-this.r[1]));
                float rz = b.rz + (float) Math.toRadians((double) this.r[2]);
                float sx = this.s[0];
                float sy = this.s[1];
                float sz = this.s[2];
                m.translate(b.px, b.py, b.pz);
                if (rz != 0.0F) {
                    m.rotateZ(rz);
                }

                if (ry != 0.0F) {
                    m.rotateY(ry);
                }

                if (rx != 0.0F) {
                    m.rotateX(rx);
                }

                if (sx != 1.0F || sy != 1.0F || sz != 1.0F) {
                    m.scale(sx, sy, sz);
                }

                m.translate(-b.px, -b.py, -b.pz);

                for (Cube c : b.cubes) {
                    Matrix4f cm = m;
                    if (c.rx != 0.0F || c.ry != 0.0F || c.rz != 0.0F) {
                        cm = this.matrix(depth + 1).set(m).translate(c.px, c.py, c.pz);
                        if (c.rz != 0.0F) {
                            cm.rotateZ(c.rz);
                        }

                        if (c.ry != 0.0F) {
                            cm.rotateY(c.ry);
                        }

                        if (c.rx != 0.0F) {
                            cm.rotateX(c.rx);
                        }

                        cm.translate(-c.px, -c.py, -c.pz);
                    }

                    for (int f = 0; f < 6; ++f) {
                        if (c.has[f]) {
                            int o = at[0];
                            at[0] += 32;
                            cm.transformDirection(NORMALS[f][0], NORMALS[f][1], NORMALS[f][2], this.tmp);
                            if (this.tmp.lengthSquared() > 1.0E-10F) {
                                this.tmp.normalize();
                            }

                            float nx = this.tmp.x;
                            float ny = this.tmp.y;
                            float nz = this.tmp.z;

                            for (int k = 0; k < 4; ++k) {
                                int s0 = (f * 4 + k) * 5;
                                int w = o + k * 8;
                                cm.transformPosition(c.v[s0], c.v[s0 + 1], c.v[s0 + 2], this.tmp);
                                out[w] = this.tmp.x;
                                out[w + 1] = this.tmp.y;
                                out[w + 2] = this.tmp.z;
                                out[w + 3] = c.v[s0 + 3];
                                out[w + 4] = c.v[s0 + 4];
                                out[w + 5] = nx;
                                out[w + 6] = ny;
                                out[w + 7] = nz;
                            }
                        }
                    }
                }

                for (Mesh me : b.meshes) {
                    for (int q = 0; q < me.quads; ++q) {
                        int o = at[0];
                        at[0] += 32;

                        for (int k = 0; k < 4; ++k) {
                            int s0 = (q * 4 + k) * 8;
                            int w = o + k * 8;
                            m.transformPosition(me.v[s0], me.v[s0 + 1], me.v[s0 + 2], this.tmp);
                            out[w] = this.tmp.x;
                            out[w + 1] = this.tmp.y;
                            out[w + 2] = this.tmp.z;
                            out[w + 3] = me.v[s0 + 3];
                            out[w + 4] = me.v[s0 + 4];
                            m.transformDirection(me.v[s0 + 5], me.v[s0 + 6], me.v[s0 + 7], this.tmp);
                            if (this.tmp.lengthSquared() > 1.0E-10F) {
                                this.tmp.normalize();
                            }

                            out[w + 5] = this.tmp.x;
                            out[w + 6] = this.tmp.y;
                            out[w + 7] = this.tmp.z;
                        }
                    }
                }

                for (Bone k : b.kids) {
                    this.bone(k, m, depth + 1, d, at, out);
                }
            }
        }

        private static Geo load(int n) {
            try {
                InputStream in =
                        Wearables.class.getResourceAsStream("/assets/arcanicvisuals/wear/cosmetic_" + n + ".json");

                Object var27;
                label139:
                {
                    Geo var33;
                    try {
                        if (in == null) {
                            var27 = null;
                            break label139;
                        }

                        JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                                .getAsJsonObject();
                        JsonObject g = root.getAsJsonObject("model")
                                .getAsJsonArray("minecraft:geometry")
                                .get(0)
                                .getAsJsonObject();
                        JsonObject d = g.getAsJsonObject("description");
                        float tw = Wearables.num(d, "texture_width", 64.0F);
                        float th = Wearables.num(d, "texture_height", 64.0F);
                        Geo geo = new Geo();
                        geo.textureHeight = th;
                        geo.texture = Identifier.fromNamespaceAndPath(
                                "arcanicvisuals", "textures/wear/cosmetic_" + n + ".png");
                        Map<String, Bone> byName = new HashMap<>();
                        List<Bone> all = new ArrayList<>();
                        if (g.has("bones")) {
                            for (JsonElement be : g.getAsJsonArray("bones")) {
                                JsonObject bo = be.getAsJsonObject();
                                Bone b = new Bone();
                                b.name = bo.get("name").getAsString();
                                b.parentName =
                                        bo.has("parent") ? bo.get("parent").getAsString() : null;
                                float[] p = vec(bo, "pivot", 0.0F, 0.0F, 0.0F);
                                float[] r = vec(bo, "rotation", 0.0F, 0.0F, 0.0F);
                                b.px = -p[0] / 16.0F;
                                b.py = p[1] / 16.0F;
                                b.pz = p[2] / 16.0F;
                                b.rx = (float) Math.toRadians((double) (-r[0]));
                                b.ry = (float) Math.toRadians((double) (-r[1]));
                                b.rz = (float) Math.toRadians((double) r[2]);
                                float[] sw = vec(bo, "swing", 0.0F, 0.0F);
                                b.swingX = sw[0];
                                b.swingZ = sw[1];
                                if (bo.has("cubes")) {
                                    for (JsonElement ce : bo.getAsJsonArray("cubes")) {
                                        Cube c = cube(ce.getAsJsonObject(), tw, th);
                                        b.cubes.add(c);

                                        for (boolean h : c.has) {
                                            if (h) {
                                                ++geo.quadCount;
                                            }
                                        }
                                    }
                                }

                                if (bo.has("meshes")) {
                                    for (JsonElement me : bo.getAsJsonArray("meshes")) {
                                        Mesh mesh = mesh(me.getAsJsonObject(), tw, th);
                                        b.meshes.add(mesh);
                                        geo.quadCount += mesh.quads;
                                    }
                                }

                                byName.put(b.name, b);
                                all.add(b);
                            }
                        }

                        for (Bone b : all) {
                            Bone parent = b.parentName == null ? null : (Bone) byName.get(b.parentName);
                            if (parent != null && parent != b) {
                                parent.kids.add(b);
                            } else {
                                geo.roots.add(b);
                            }
                        }

                        geo.quadMode = new int[geo.quadCount];
                        geo.quadFrames = new int[geo.quadCount];
                        geo.quadTicks = new float[geo.quadCount];
                        geo.quadBand = new float[geo.quadCount];
                        int[] k = new int[] {0};

                        for (Bone b : geo.roots) {
                            geo.index(b, k);
                        }

                        if (root.has("textureAnimation")) {
                            JsonObject ta = root.getAsJsonObject("textureAnimation");
                            geo.frames = Math.max(1, (int) Wearables.num(ta, "frameCount", 1.0F));
                            geo.frameMs = Math.max(1.0F, Wearables.num(ta, "frameTime", 3.0F)) * 50.0F;
                        }

                        if (root.has("animation")) {
                            animation(geo, root.getAsJsonObject("animation"), byName);
                        }

                        var33 = geo;
                    } catch (Throwable var25) {
                        if (in != null) {
                            try {
                                in.close();
                            } catch (Throwable var24) {
                                var25.addSuppressed(var24);
                            }
                        }

                        throw var25;
                    }

                    if (in != null) {
                        in.close();
                    }

                    return var33;
                }

                if (in != null) {
                    in.close();
                }

                return (Geo) var27;
            } catch (Exception e) {
                System.err.println("[ArcanicVisuals] wearable " + n + ": " + String.valueOf(e));
                return null;
            }
        }

        private void index(Bone b, int[] k) {
            for (Cube c : b.cubes) {
                for (int f = 0; f < 6; ++f) {
                    if (c.has[f]) {
                        int var10004 = k[0];
                        int var10001 = k[0];
                        k[0] = var10004 + 1;
                        this.part(var10001, c.mode, c.frames, c.ticks, c.band);
                    }
                }
            }

            for (Mesh me : b.meshes) {
                for (int f = 0; f < me.quads; ++f) {
                    int var12 = k[0];
                    int var11 = k[0];
                    k[0] = var12 + 1;
                    this.part(var11, me.mode, me.frames, me.ticks, me.band);
                }
            }

            for (Bone kid : b.kids) {
                this.index(kid, k);
            }
        }

        private void part(int q, int mode, int frames, float ticks, float band) {
            this.quadMode[q] = mode;
            this.quadFrames[q] = frames;
            this.quadTicks[q] = ticks;
            this.quadBand[q] = band / this.textureHeight;
            this.hasMode[mode] = true;
        }

        private static int mode(JsonObject o) {
            if (o.has("mode") && o.get("mode").isJsonPrimitive()) {
                byte var10000;
                switch (o.get("mode").getAsString()) {
                    case "glow" -> var10000 = 1;
                    case "clear" -> var10000 = 2;
                    case "shine" -> var10000 = 3;
                    default -> var10000 = 0;
                }

                return var10000;
            } else {
                return 0;
            }
        }

        private static Mesh mesh(JsonObject o, float tw, float th) {
            Mesh me = new Mesh();
            me.mode = mode(o);
            float[] fr = vec(o, "frames", 1.0F, 1.0F, 0.0F);
            me.frames = Math.max(1, (int) fr[0]);
            me.ticks = Math.max(1.0F, fr[1]);
            me.band = fr[2];
            JsonArray a = o.getAsJsonArray("v");
            me.quads = a.size() / 32;
            me.v = new float[me.quads * 32];

            for (int i = 0; i < me.quads * 4; ++i) {
                int s = i * 8;
                me.v[s] = -a.get(s).getAsFloat() / 16.0F;
                me.v[s + 1] = a.get(s + 1).getAsFloat() / 16.0F;
                me.v[s + 2] = a.get(s + 2).getAsFloat() / 16.0F;
                me.v[s + 3] = a.get(s + 3).getAsFloat() / tw;
                me.v[s + 4] = a.get(s + 4).getAsFloat() / th;
                me.v[s + 5] = -a.get(s + 5).getAsFloat();
                me.v[s + 6] = a.get(s + 6).getAsFloat();
                me.v[s + 7] = a.get(s + 7).getAsFloat();
            }

            return me;
        }

        private static Cube cube(JsonObject o, float tw, float th) {
            float[] origin = vec(o, "origin", 0.0F, 0.0F, 0.0F);
            float[] size = vec(o, "size", 1.0F, 1.0F, 1.0F);
            float[] pivot = vec(o, "pivot", origin[0], origin[1], origin[2]);
            float[] rot = vec(o, "rotation", 0.0F, 0.0F, 0.0F);
            float inflate = Wearables.num(o, "inflate", 0.0F);
            Cube c = new Cube();
            c.px = -pivot[0] / 16.0F;
            c.py = pivot[1] / 16.0F;
            c.pz = pivot[2] / 16.0F;
            c.rx = (float) Math.toRadians((double) (-rot[0]));
            c.ry = (float) Math.toRadians((double) (-rot[1]));
            c.rz = (float) Math.toRadians((double) rot[2]);
            c.mode = mode(o);
            float[] fr = vec(o, "frames", 1.0F, 1.0F, 0.0F);
            c.frames = Math.max(1, (int) fr[0]);
            c.ticks = Math.max(1.0F, fr[1]);
            c.band = fr[2];
            float x = origin[0] - inflate;
            float y = origin[1] - inflate;
            float z = origin[2] - inflate;
            float w = size[0] + inflate * 2.0F;
            float h = size[1] + inflate * 2.0F;
            float dp = size[2] + inflate * 2.0F;
            float x0 = -(x + w) / 16.0F;
            float y0 = y / 16.0F;
            float z0 = z / 16.0F;
            float x1 = -x / 16.0F;
            float y1 = (y + h) / 16.0F;
            float z1 = (z + dp) / 16.0F;
            JsonElement uv = o.get("uv");

            for (int f = 0; f < 6; ++f) {
                float u;
                float v;
                float du;
                float dv;
                if (uv != null && uv.isJsonObject()) {
                    JsonObject face = uv.getAsJsonObject().has(FACES[f])
                            ? uv.getAsJsonObject().getAsJsonObject(FACES[f])
                            : null;
                    if (face == null || !face.has("uv")) {
                        continue;
                    }

                    float[] a = vec(face, "uv", 0.0F, 0.0F);
                    float[] s = vec(face, "uv_size", 0.0F, 0.0F);
                    u = a[0] / tw;
                    v = a[1] / th;
                    du = s[0] / tw;
                    dv = s[1] / th;
                } else {
                    float bu = 0.0F;
                    float bv = 0.0F;
                    if (uv != null && uv.isJsonArray()) {
                        bu = uv.getAsJsonArray().get(0).getAsFloat();
                        bv = uv.getAsJsonArray().get(1).getAsFloat();
                    }

                    float sw = size[0];
                    float sh = size[1];
                    float sd = size[2];
                    switch (f) {
                        case 0:
                            u = bu + sd + sw;
                            v = bv + sd;
                            du = sd;
                            dv = sh;
                            break;
                        case 1:
                            u = bu;
                            v = bv + sd;
                            du = sd;
                            dv = sh;
                            break;
                        case 2:
                            u = bu + sd + sw;
                            v = bv;
                            du = sw;
                            dv = sd;
                            break;
                        case 3:
                            u = bu + sd;
                            v = bv;
                            du = sw;
                            dv = sd;
                            break;
                        case 4:
                            u = bu + sd;
                            v = bv + sd;
                            du = sw;
                            dv = sh;
                            break;
                        default:
                            u = bu + sd + sw + sd;
                            v = bv + sd;
                            du = sw;
                            dv = sh;
                    }

                    u /= tw;
                    v /= th;
                    du /= tw;
                    dv /= th;
                }

                float u1 = u + du;
                float v1 = v + dv;
                float[][] var10000;
                switch (f) {
                    case 0 ->
                        var10000 = new float[][] {
                            {x0, y1, z0, u, v}, {x0, y1, z1, u1, v}, {x0, y0, z1, u1, v1}, {x0, y0, z0, u, v1}
                        };
                    case 1 ->
                        var10000 = new float[][] {
                            {x1, y1, z1, u, v}, {x1, y1, z0, u1, v}, {x1, y0, z0, u1, v1}, {x1, y0, z1, u, v1}
                        };
                    case 2 ->
                        var10000 = new float[][] {
                            {x1, y0, z0, u, v}, {x1, y0, z1, u, v1}, {x0, y0, z1, u1, v1}, {x0, y0, z0, u1, v}
                        };
                    case 3 ->
                        var10000 = new float[][] {
                            {x0, y1, z0, u, v}, {x0, y1, z1, u, v1}, {x1, y1, z1, u1, v1}, {x1, y1, z0, u1, v}
                        };
                    case 4 ->
                        var10000 = new float[][] {
                            {x1, y1, z0, u, v}, {x0, y1, z0, u1, v}, {x0, y0, z0, u1, v1}, {x1, y0, z0, u, v1}
                        };
                    default ->
                        var10000 = new float[][] {
                            {x0, y1, z1, u, v}, {x1, y1, z1, u1, v}, {x1, y0, z1, u1, v1}, {x0, y0, z1, u, v1}
                        };
                }

                float[][] p = var10000;

                for (int k = 0; k < 4; ++k) {
                    System.arraycopy(p[k], 0, c.v, (f * 4 + k) * 5, 5);
                }

                c.has[f] = true;
            }

            return c;
        }

        private static int layer(String name) {
            int var10000;
            switch (name) {
                case "walk" -> var10000 = 1;
                case "air" -> var10000 = 2;
                case "sneak" -> var10000 = 3;
                default -> var10000 = name.contains("idle") ? 0 : -1;
            }

            return var10000;
        }

        private static void animation(Geo geo, JsonObject anim, Map<String, Bone> byName) {
            if (anim.has("animations") && anim.get("animations").isJsonObject()) {
                JsonObject[] picked = new JsonObject[4];
                JsonObject first = null;

                for (Map.Entry<String, JsonElement> e :
                        anim.getAsJsonObject("animations").entrySet()) {
                    if (((JsonElement) e.getValue()).isJsonObject()) {
                        String name = ((String) e.getKey()).toLowerCase(Locale.ROOT);
                        if (!name.contains("gui")) {
                            if (first == null) {
                                first = ((JsonElement) e.getValue()).getAsJsonObject();
                            }

                            int l = layer(name);
                            if (l >= 0 && picked[l] == null) {
                                picked[l] = ((JsonElement) e.getValue()).getAsJsonObject();
                            }
                        }
                    }
                }

                if (picked[0] == null) {
                    picked[0] = first;
                }

                for (int l = 0; l < 4; ++l) {
                    JsonObject a = picked[l];
                    if (a != null && a.has("bones") && a.get("bones").isJsonObject()) {
                        float longest = 0.0F;
                        boolean any = false;

                        for (Map.Entry<String, JsonElement> e :
                                a.getAsJsonObject("bones").entrySet()) {
                            Bone b = (Bone) byName.get(e.getKey());
                            if (b != null && ((JsonElement) e.getValue()).isJsonObject()) {
                                JsonObject ch = ((JsonElement) e.getValue()).getAsJsonObject();
                                Track[] tr = new Track[] {
                                    track(ch.get("rotation")), track(ch.get("position")), track(ch.get("scale"))
                                };

                                for (Track t : tr) {
                                    if (t != null) {
                                        any = true;
                                        longest = Math.max(longest, t.t()[t.t().length - 1]);
                                    }
                                }

                                if (tr[0] != null || tr[1] != null || tr[2] != null) {
                                    b.tracks[l] = tr;
                                }
                            }
                        }

                        float length = Wearables.num(a, "animation_length", 0.0F);
                        if (length <= 0.0F) {
                            length = longest;
                        }

                        if (any && !(length <= 0.0F)) {
                            geo.has[l] = true;
                            geo.length[l] = length;
                            geo.stride[l] = a.has("stride")
                                    && a.get("stride").isJsonPrimitive()
                                    && a.get("stride").getAsBoolean();
                        } else {
                            for (Bone b : byName.values()) {
                                b.tracks[l] = null;
                            }
                        }
                    }
                }
            }
        }

        private static Track track(JsonElement e) {
            if (e != null && !e.isJsonNull()) {
                List<float[]> keys = new ArrayList<>();
                if (e.isJsonObject()
                        && !e.getAsJsonObject().has("vector")
                        && !e.getAsJsonObject().has("post")) {
                    for (Map.Entry<String, JsonElement> k : e.getAsJsonObject().entrySet()) {
                        float[] v = value((JsonElement) k.getValue());
                        if (v != null) {
                            try {
                                keys.add(new float[] {Float.parseFloat((String) k.getKey()), v[0], v[1], v[2]});
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                } else {
                    float[] v = value(e);
                    if (v != null) {
                        keys.add(new float[] {0.0F, v[0], v[1], v[2]});
                    }
                }

                if (keys.isEmpty()) {
                    return null;
                } else {
                    keys.sort((p, q) -> Float.compare(p[0], q[0]));
                    float[] t = new float[keys.size()];
                    float[][] v = new float[keys.size()][];

                    for (int i = 0; i < t.length; ++i) {
                        t[i] = ((float[]) keys.get(i))[0];
                        v[i] = new float[] {
                            ((float[]) keys.get(i))[1], ((float[]) keys.get(i))[2], ((float[]) keys.get(i))[3]
                        };
                    }

                    return new Track(t, v);
                }
            } else {
                return null;
            }
        }

        private static float[] value(JsonElement e) {
            try {
                if (e.isJsonObject()) {
                    JsonObject o = e.getAsJsonObject();
                    e = o.has("vector") ? o.get("vector") : (o.has("post") ? o.get("post") : null);
                    if (e == null) {
                        return null;
                    }
                }

                if (e.isJsonArray()) {
                    JsonArray a = e.getAsJsonArray();
                    if (a.size() < 3) {
                        return null;
                    }

                    return new float[] {
                        a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()
                    };
                }

                if (e.isJsonPrimitive()) {
                    float f = e.getAsFloat();
                    return new float[] {f, f, f};
                }
            } catch (RuntimeException ignored) {
            }

            return null;
        }

        private static float[] vec(JsonObject o, String k, float... def) {
            if (o.has(k) && o.get(k).isJsonArray()) {
                JsonArray a = o.getAsJsonArray(k);
                float[] v = def.clone();

                for (int i = 0; i < v.length && i < a.size(); ++i) {
                    try {
                        v[i] = a.get(i).getAsFloat();
                    } catch (RuntimeException ignored) {
                    }
                }

                return v;
            } else {
                return def;
            }
        }

        static final class Bone {
            String name;
            String parentName;
            final List<Bone> kids = new ArrayList<>();
            final List<Cube> cubes = new ArrayList<>();
            final List<Mesh> meshes = new ArrayList<>();
            float px;
            float py;
            float pz;
            float rx;
            float ry;
            float rz;
            float swingX;
            float swingZ;
            final Track[][] tracks = new Track[4][];
        }

        static final class Cube {
            float px;
            float py;
            float pz;
            float rx;
            float ry;
            float rz;
            final float[] v = new float[120];
            final boolean[] has = new boolean[6];
            int mode;
            int frames = 1;
            float ticks = 1.0F;
            float band;
        }

        static final class Mesh {
            float[] v;
            int quads;
            int mode;
            int frames = 1;
            float ticks = 1.0F;
            float band;
        }

        static record Track(float[] t, float[][] v) {
            void at(float time, float[] out) {
                int n = this.t.length;
                if (n == 0) {
                    out[0] = out[1] = out[2] = Float.NaN;
                } else if (time <= this.t[0]) {
                    System.arraycopy(this.v[0], 0, out, 0, 3);
                } else if (time >= this.t[n - 1]) {
                    System.arraycopy(this.v[n - 1], 0, out, 0, 3);
                } else {
                    int i;
                    for (i = 1; this.t[i] < time; ++i) {}

                    float k = (time - this.t[i - 1]) / Math.max(1.0E-6F, this.t[i] - this.t[i - 1]);

                    for (int c = 0; c < 3; ++c) {
                        out[c] = this.v[i - 1][c] + (this.v[i][c] - this.v[i - 1][c]) * k;
                    }
                }
            }
        }
    }
}
