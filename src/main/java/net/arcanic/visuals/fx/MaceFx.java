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

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.fx.impact.ImpactDraw;
import net.arcanic.visuals.fx.impact.ScreenFx;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class MaceFx extends Module {
    public final Setting.Mode style =
            this.add(new Setting.Mode("style", "Эффект", "Кратер", "Кратер", "Гром", "Ударная волна"));
    final Setting.Bool players = this.add(new Setting.Bool("players", "По игрокам", true));
    final Setting.Bool monsters = this.add(new Setting.Bool("monsters", "По монстрам", true));
    final Setting.Bool animals = this.add(new Setting.Bool("animals", "По мирным", true));
    final Setting.Num size = this.add(new Setting.Num("size", "Размер", 1.0, 0.6, 1.6, 0.05, "×"));
    final Setting.Bool byHeight = this.add(new Setting.Bool("by_height", "Сила по высоте", true));
    final Setting.Mode palette = this.add(new Setting.Mode("palette", "Цвета", "Свои", "Свои", "Тема"));
    final Setting.Bool screen = this.add(new Setting.Bool("screen", "Эффекты на экране", true));
    final Setting.Bool shake = this.add(new Setting.Bool("shake", "Тряска камеры", true));
    final Setting.Bool sound = this.add(new Setting.Bool("sound", "Звук", true));
    final Setting.Num volume =
            (this.add(new Setting.Num("volume", "Громкость", 0.8, 0.1, 1.0, 0.05, ""))).showIf(() -> this.sound.on());
    private static final Identifier RING = Identifier.fromNamespaceAndPath("arcanic", "textures/totem/ring.png");
    private static final Identifier DISC = Identifier.fromNamespaceAndPath("arcanic", "textures/totem/disc.png");
    private static final Identifier ORB = Identifier.fromNamespaceAndPath("arcanic", "textures/targetesp/orb.png");
    private static final float RING_FRONT = 0.86F;
    private static final float[] LIFE = {2.6F, 1.8F, 2.0F};
    private final List<Smash> smashes = new ArrayList<>();
    private final List<Smash> studioSmashes = new ArrayList<>();
    private ClientLevel level;
    private static MultiBufferSource.BufferSource blocks;

    public MaceFx() {
        super("mace_fx", "Mace Smash", "Эффект удара булавой с высоты", Category.COMBAT, false);
    }

    private Pal pal(int style) {
        if (this.palette.is("Тема")) {
            int a = Theme.accent() | 0xFF000000;
            int b = Theme.accent2() | 0xFF000000;
            return new Pal(Fx.lerp(a, -1, 0.55F), a, b);
        } else {
            return switch (style) {
                case 1 -> new Pal(0xFFF4F8FF, 0xFF8FC6FF, 0xFF4A62FF);
                case 2 -> new Pal(0xFFF6FCFF, 0xFFA6E6FF, 0xFF6577FF);
                default -> new Pal(-5208, -30178, 0xFFC4231A);
            };
        }
    }

    private boolean targets(LivingEntity e) {
        if (e instanceof Player) {
            return this.players.on();
        } else {
            return e instanceof Enemy ? this.monsters.on() : this.animals.on();
        }
    }

    public void attack(Entity target) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (this.enabled() && me != null && mc.level != null && target instanceof LivingEntity l) {
            if (target != me) {
                if (me.getMainHandItem().getItem() instanceof MaceItem
                        && MaceItem.canSmashAttack(me)
                        && this.targets(l)) {
                    float fall = (float) me.fallDistance;
                    float power = this.byHeight.on() ? Mth.clamp((fall - 1.5F) / 8.0F, 0.0F, 1.0F) : 0.6F;
                    double x = l.getX();
                    double y = l.getY();
                    double z = l.getZ();
                    double ground = y;
                    BlockState block = null;
                    int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(x, y + 0.1, z));
                    int dust = 0xFF8C8478;
                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                    int top = Mth.floor(y + 0.05);

                    for (int i = 0; i < 4; i++) {
                        pos.set(Mth.floor(x), top - i, Mth.floor(z));
                        BlockState st = mc.level.getBlockState(pos);
                        if (!st.isAir()) {
                            VoxelShape shape = st.getCollisionShape(mc.level, pos);
                            if (!shape.isEmpty()) {
                                double surface = (double) pos.getY() + shape.max(Axis.Y);
                                if (!(surface > y + 0.05)) {
                                    ground = surface;
                                    int col = st.getMapColor(mc.level, pos).col;
                                    if (col != 0) {
                                        dust = 0xFF000000 | col;
                                    }

                                    if (st.getRenderShape() == RenderShape.MODEL
                                            && st.getFluidState().isEmpty()) {
                                        block = st;
                                        light = LevelRenderer.getLightColor(mc.level, pos.above());
                                    }
                                    break;
                                }
                            }
                        }
                    }

                    if (block == null) {
                        block = Blocks.STONE.defaultBlockState();
                    }

                    Smash s = new Smash(this.style.index(), x, ground, z, power, this.size.f(), block, light, dust);
                    prepare(s);
                    this.smashes.add(s);

                    while (this.smashes.size() > 4) {
                        this.smashes.remove(0);
                    }

                    return;
                }

                return;
            }
        }
    }

    protected void onDisable() {
        this.smashes.clear();
    }

    public void studioSmash(Vec3 at, float power, BlockState block, int dust) {
        Smash s = new Smash(this.style.index(), at.x, at.y, at.z, power, this.size.f(), block, 15728880, dust);
        s.start = Studio.clockMs();
        s.studio = true;
        prepare(s);
        this.studioSmashes.add(s);

        while (this.studioSmashes.size() > 2) {
            this.studioSmashes.remove(0);
        }
    }

    public void studio(Show.View sv) {
        if (!this.studioSmashes.isEmpty()) {
            View v = new View(sv.ps(), sv.pose(), sv.cam(), sv.left(), sv.up(), sv.forward());
            long now = Studio.clockMs();
            Iterator<Smash> it = this.studioSmashes.iterator();

            while (it.hasNext()) {
                Smash s = (Smash) it.next();
                float t = (float) (now - s.start) / 1000.0F;
                if (!(t > LIFE[s.style]) && !(t < -0.05F)) {
                    t = Math.max(0.0F, t);
                    float dt = Math.max(0.0F, Math.min(0.1F, t - s.last));
                    s.last = t;
                    Pal c = this.pal(s.style);
                    switch (s.style) {
                        case 1:
                            this.thunder(s, t, dt, v, c);
                            break;
                        case 2:
                            this.dome(s, t, dt, v, c);
                            break;
                        default:
                            this.crater(s, t, dt, v, c);
                    }
                } else {
                    it.remove();
                }
            }
        }
    }

    public void clearStudio() {
        this.studioSmashes.clear();
    }

    private static void prepare(Smash s) {
        float g = 0.46F;
        int n = (int) Math.ceil((double) (s.reach / g));

        for (int i = -n; i <= n; i++) {
            for (int j = -n; j <= n; j++) {
                float ox = (float) i * g + (s.rnd.nextFloat() - 0.5F) * 0.05F;
                float oz = (float) j * g + (s.rnd.nextFloat() - 0.5F) * 0.05F;
                float d = (float) Math.hypot((double) ox, (double) oz);
                if (!(d > s.reach) && !(d < 0.3F)) {
                    s.tiles.add(new float[] {ox, oz, d, (s.rnd.nextFloat() - 0.5F) * 0.3F});
                }
            }
        }

        if (s.style == 0) {
            int count = 8 + Math.round(10.0F * s.power);

            for (int i = 0; i < count; i++) {
                double a = s.rnd.nextDouble() * Math.PI * 2.0;
                Chunk c = new Chunk(
                        (new Vector3f(s.rnd.nextFloat() - 0.5F, s.rnd.nextFloat() - 0.5F, s.rnd.nextFloat() - 0.5F))
                                .normalize());
                c.x = s.x + Math.cos(a) * 0.3;
                c.y = s.y + 0.15;
                c.z = s.z + Math.sin(a) * 0.3;
                double out = (2.0 + s.rnd.nextDouble() * 2.5) * (0.7 + 0.5 * (double) s.power);
                c.vx = Math.cos(a) * out;
                c.vz = Math.sin(a) * out;
                c.vy = (4.5 + s.rnd.nextDouble() * 3.0) * (0.7 + 0.4 * (double) s.power);
                c.spin = 5.0F + s.rnd.nextFloat() * 9.0F;
                c.size = (0.18F + s.rnd.nextFloat() * 0.16F) * s.scale;
                s.chunks.add(c);
            }

            int cracks = 8 + Math.round(4.0F * s.power);

            for (int i = 0; i < cracks; i++) {
                double a = (double) i * Math.PI * 2.0 / (double) cracks + (s.rnd.nextDouble() - 0.5) * 0.4;
                float length = s.reach * (0.45F + s.rnd.nextFloat() * 0.45F);
                addCrack(s, 0.22F, a, length, 9);
                if (s.rnd.nextFloat() < 0.6F) {
                    float[][] main = (float[][]) s.cracks.get(s.cracks.size() - 1);
                    int k = 3 + s.rnd.nextInt(4);
                    double ba = a + (double) (s.rnd.nextBoolean() ? 1 : -1) * (0.5 + s.rnd.nextDouble() * 0.4);
                    addCrackFrom(s, main[0][k], main[1][k], ba, length * 0.35F, 5);
                }
            }
        }

        if (s.style == 1) {
            for (int i = 0; i < 7; i++) {
                s.arcs.add(new float[] {
                    (float) ((double) i * Math.PI * 2.0 / 7.0 + (s.rnd.nextDouble() - 0.5) * 0.5),
                    s.reach * (0.75F + s.rnd.nextFloat() * 0.45F)
                });
            }
        }
    }

    private static void addCrack(Smash s, float from, double a, float length, int n) {
        addCrackFrom(s, (float) Math.cos(a) * from, (float) Math.sin(a) * from, a, length, n);
    }

    private static void addCrackFrom(Smash s, float x0, float z0, double a, float length, int n) {
        float[] xs = new float[n + 1];
        float[] zs = new float[n + 1];
        xs[0] = x0;
        zs[0] = z0;
        float step = length / (float) n;

        for (int i = 1; i <= n; i++) {
            a += (s.rnd.nextDouble() - 0.5) * 0.7;
            xs[i] = xs[i - 1] + (float) Math.cos(a) * step;
            zs[i] = zs[i - 1] + (float) Math.sin(a) * step;
        }

        s.cracks.add(new float[][] {xs, zs});
    }

    public void world(PoseStack ps, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != this.level) {
            this.level = mc.level;
            this.smashes.clear();
        }

        if (!this.smashes.isEmpty() && mc.level != null) {
            Camera camera = mc.gameRenderer.getMainCamera();
            View v = new View(
                    ps,
                    ps.last().pose(),
                    camera.position(),
                    new Vector3f(camera.leftVector()),
                    new Vector3f(camera.upVector()),
                    (Vector3f) null);
            long now = System.currentTimeMillis();
            Iterator<Smash> it = this.smashes.iterator();

            while (it.hasNext()) {
                Smash s = (Smash) it.next();
                float t = (float) (now - s.start) / 1000.0F;
                if (t > LIFE[s.style]) {
                    it.remove();
                } else {
                    float dt = Math.max(0.0F, Math.min(0.1F, t - s.last));
                    s.last = t;
                    Pal c = this.pal(s.style);
                    switch (s.style) {
                        case 1:
                            this.thunder(s, t, dt, v, c);
                            break;
                        case 2:
                            this.dome(s, t, dt, v, c);
                            break;
                        default:
                            this.crater(s, t, dt, v, c);
                    }
                }
            }
        }
    }

    private void crater(Smash s, float t, float dt, View v, Pal c) {
        float k = s.scale;
        float x = v.x(s.x);
        float y = v.y(s.y);
        float z = v.z(s.z);
        if (s.once(0)) {
            this.play(
                    s,
                    s.power > 0.45F ? SoundEvents.MACE_SMASH_GROUND_HEAVY : SoundEvents.MACE_SMASH_GROUND,
                    1.0F,
                    1.0F);
            this.play(s, SoundEvents.GENERIC_EXPLODE.value(), 0.35F, 0.75F);
            this.play(s, s.block.getSoundType().getBreakSound(), 1.0F, 0.7F);
            this.jolt(s, (1.6F + 2.6F * s.power) * k);

            for (int i = 0; i < 36 + Math.round(30.0F * s.power); i++) {
                double a = s.rnd.nextDouble() * Math.PI * 2.0;
                double sp = 2.0 + s.rnd.nextDouble() * 4.0;
                Fx.spawn()
                        .at(new Vec3(s.x, s.y + 0.1, s.z))
                        .vel(Math.cos(a) * sp, 3.0 + s.rnd.nextDouble() * 5.0, Math.sin(a) * sp)
                        .tex(Fx.Tex.SPARK)
                        .mode(Fx.Mode.STRETCH)
                        .size(0.06F * k, 0.0F)
                        .life(0.5F + s.rnd.nextFloat() * 0.5F)
                        .drag(1.4F)
                        .gravity(9.0F)
                        .colour(c.light, c.deep & 0xFFFFFF);
            }
        }

        float burn = 1.0F - smooth((t - 0.6F) / 1.9F);
        flat(v.pose, DISC, x, y + 0.006F, z, s.reach * 0.42F / 0.9F, 0.0F, 0xFF1A0D06, 0.5F * burn, 0.0F);
        float grow = easeOut(t / 0.22F);
        float glow = (1.0F - smooth((t - 0.4F) / 2.0F)) * (0.82F + 0.18F * Mth.sin((double) (t * 9.0F)));

        for (float[][] crack : s.cracks) {
            flatLine(v, crack[0], crack[1], x, y + 0.012F, z, grow, k, c, glow);
        }

        this.slabs(s, t, v, 0.42F, 0.55F, 0.0F, 1.0F);
        this.chunks(s, t, dt, v);
        groundRing(v, x, y, z, t, 0.0F, 0.55F, s.reach * 1.15F, c.light, 0.8F);
        dust(s, t, v, 0.9F);
    }

    private void thunder(Smash s, float t, float dt, View v, Pal c) {
        float k = s.scale;
        float x = v.x(s.x);
        float y = v.y(s.y);
        float z = v.z(s.z);
        if (s.once(0)) {
            this.play(s, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.55F, 1.15F);
            this.play(s, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0F, 1.0F);
            this.play(s, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0F, 1.0F);
            this.jolt(s, (2.2F + 2.4F * s.power) * k);

            for (int i = 0; i < 70; i++) {
                double a = s.rnd.nextDouble() * Math.PI * 2.0;
                double sp = 3.0 + s.rnd.nextDouble() * 6.0;
                Fx.spawn()
                        .at(new Vec3(s.x, s.y + 0.15, s.z))
                        .vel(Math.cos(a) * sp, 2.0 + s.rnd.nextDouble() * 5.0, Math.sin(a) * sp)
                        .tex(Fx.Tex.SPARK)
                        .mode(Fx.Mode.STRETCH)
                        .size(0.05F * k, 0.0F)
                        .life(0.4F + s.rnd.nextFloat() * 0.4F)
                        .drag(2.0F)
                        .gravity(10.0F)
                        .colour(c.light, c.main & 0xFFFFFF);
            }
        }

        flat(
                v.pose,
                DISC,
                x,
                y + 0.006F,
                z,
                1.4F * k / 0.9F,
                0.0F,
                0xFF0E0E14,
                0.55F * (1.0F - smooth((t - 0.3F) / 1.4F)),
                0.0F);
        float[] window = {0.0F, 0.1F, 0.22F};
        float[] lasts = {0.075F, 0.07F, 0.13F};

        for (int w = 0; w < 3; w++) {
            float u = (t - window[w]) / lasts[w];
            if (!(u < 0.0F) && !(u > 1.0F)) {
                float a = (1.0F - u) * (1.0F - u);
                this.bolt(s, v, x, y, z, w, a, c);
                sprite(v, ORB, x, y + 0.4F, z, 2.6F * k, c.light, 0.0F, a * 0.9F);
            }
        }

        if (t < 0.55F) {
            Random r = new Random(s.seed);
            List<Vector3f> path = channel(r, x, y, z, k);
            float[][] p = arrays(path);
            ribbon(v, Fx.Tex.LINE_GLOW.id, p[0], p[1], p[2], 0.5F * k, c.main, 0.18F * (1.0F - t / 0.55F));
        }

        if (t < 0.7F) {
            long frame = (long) (t / 0.04F);
            Random jag = new Random(s.seed * 13L + frame);
            float fade = (1.0F - smooth((t - 0.3F) / 0.4F)) * (0.6F + 0.4F * jag.nextFloat());

            for (float[] arc : s.arcs) {
                int n = 12;
                float[] xs = new float[n + 1];
                float[] ys = new float[n + 1];
                float[] zs = new float[n + 1];
                float reachNow = arc[1] * easeOut(t / 0.18F);

                for (int i = 0; i <= n; i++) {
                    float f = (float) i / (float) n;
                    float d = reachNow * f;
                    float side = (jag.nextFloat() - 0.5F) * 0.35F * k * f;
                    float lift = jag.nextFloat() * 0.12F * k * f;
                    float ca = Mth.cos((double) arc[0]);
                    float sa = Mth.sin((double) arc[0]);
                    xs[i] = x + ca * d - sa * side;
                    ys[i] = y + 0.05F + lift;
                    zs[i] = z + sa * d + ca * side;
                }

                ribbon(v, Fx.Tex.LINE_GLOW.id, xs, ys, zs, 0.22F * k, c.main, 0.6F * fade);
                ribbon(v, Fx.Tex.LINE_GLOW.id, xs, ys, zs, 0.035F * k, c.light, fade);
            }
        }

        this.slabs(s, t, v, 0.18F, 0.4F, 0.0F, 0.7F);
        groundRing(v, x, y, z, t, 0.0F, 0.45F, s.reach * 1.2F, c.light, 0.9F);
        float reach = reach(s);
        if (this.screen.on() && reach > 0.0F && t < 0.4F) {
            centre(v, x, y + 1.0F, z);

            for (int w = 0; w < 3; w++) {
                float u = (t - window[w]) / lasts[w];
                if (u >= 0.0F && u <= 1.0F) {
                    ScreenFx.flash(0.32F * (1.0F - u) * reach, r(c.light), g(c.light), b(c.light));
                }
            }
        }
    }

    private static List<Vector3f> channel(Random r, float x, float y, float z, float k) {
        List<Vector3f> pts = new ArrayList<>();
        pts.add(new Vector3f(x + (r.nextFloat() - 0.5F) * 6.0F, y + 26.0F, z + (r.nextFloat() - 0.5F) * 6.0F));
        pts.add(new Vector3f(x, y, z));
        float rough = 0.28F;

        for (int depth = 0; depth < 6; depth++) {
            List<Vector3f> next = new ArrayList<>();

            for (int i = 0; i + 1 < pts.size(); i++) {
                Vector3f a = (Vector3f) pts.get(i);
                Vector3f b = (Vector3f) pts.get(i + 1);
                float len = a.distance(b);
                Vector3f mid = (new Vector3f(a))
                        .add(b)
                        .mul(0.5F)
                        .add(
                                (r.nextFloat() - 0.5F) * len * rough,
                                (r.nextFloat() - 0.5F) * len * rough * 0.3F,
                                (r.nextFloat() - 0.5F) * len * rough);
                next.add(a);
                next.add(mid);
            }

            next.add((Vector3f) pts.get(pts.size() - 1));
            pts = next;
        }

        return pts;
    }

    private void bolt(Smash s, View v, float x, float y, float z, int strike, float a, Pal c) {
        Random r = new Random(s.seed);
        List<Vector3f> path = channel(r, x, y, z, s.scale);
        float[][] p = arrays(path);
        ribbon(v, Fx.Tex.LINE_GLOW.id, p[0], p[1], p[2], 0.9F * s.scale, c.main, 0.75F * a);
        ribbon(v, Fx.Tex.LINE_GLOW.id, p[0], p[1], p[2], 0.14F * s.scale, c.light, a);
        Random fr = new Random(s.seed + (long) strike * 7919L);

        for (int f = 0; f < 4; f++) {
            int from = 8 + fr.nextInt(path.size() / 2);
            Vector3f start = (Vector3f) path.get(from);
            Vector3f end = (new Vector3f(start))
                    .add((fr.nextFloat() - 0.5F) * 7.0F, -2.0F - fr.nextFloat() * 4.0F, (fr.nextFloat() - 0.5F) * 7.0F);
            List<Vector3f> fork = new ArrayList(List.of(start, end));

            for (int depth = 0; depth < 4; depth++) {
                List<Vector3f> next = new ArrayList<>();

                for (int i = 0; i + 1 < fork.size(); i++) {
                    Vector3f q = (Vector3f) fork.get(i);
                    Vector3f w = (Vector3f) fork.get(i + 1);
                    float len = q.distance(w);
                    next.add(q);
                    next.add((new Vector3f(q))
                            .add(w)
                            .mul(0.5F)
                            .add(
                                    (fr.nextFloat() - 0.5F) * len * 0.35F,
                                    (fr.nextFloat() - 0.5F) * len * 0.15F,
                                    (fr.nextFloat() - 0.5F) * len * 0.35F));
                }

                next.add((Vector3f) fork.get(fork.size() - 1));
                fork = next;
            }

            float[][] q = arrays(fork);
            ribbon(v, Fx.Tex.LINE_GLOW.id, q[0], q[1], q[2], 0.45F * s.scale, c.main, 0.55F * a);
            ribbon(v, Fx.Tex.LINE_GLOW.id, q[0], q[1], q[2], 0.07F * s.scale, c.light, 0.8F * a);
        }
    }

    private static float[][] arrays(List<Vector3f> pts) {
        float[] xs = new float[pts.size()];
        float[] ys = new float[pts.size()];
        float[] zs = new float[pts.size()];

        for (int i = 0; i < pts.size(); i++) {
            xs[i] = ((Vector3f) pts.get(i)).x;
            ys[i] = ((Vector3f) pts.get(i)).y;
            zs[i] = ((Vector3f) pts.get(i)).z;
        }

        return new float[][] {xs, ys, zs};
    }

    private void dome(Smash s, float t, float dt, View v, Pal c) {
        float k = s.scale;
        float x = v.x(s.x);
        float y = v.y(s.y);
        float z = v.z(s.z);
        if (s.once(0)) {
            this.play(s, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0F, 1.0F);
            this.play(s, SoundEvents.WIND_CHARGE_BURST.value(), 1.0F, 0.7F);
            this.play(s, SoundEvents.WARDEN_SONIC_BOOM, 0.3F, 1.7F);
            this.jolt(s, (1.8F + 2.4F * s.power) * k);
        }

        float u = t / 0.62F;
        if (u < 1.0F) {
            float radius = s.reach * easeOut(u);
            float fade = (float) Math.pow((double) (1.0F - u), 1.4);
            shell(v, x, y, z, radius, c, fade);
            shell(v, x, y, z, radius * 0.72F, c, fade * 0.45F);

            for (int i = 1; i <= 3; i++) {
                float h = radius * (0.18F + 0.2F * (float) i);
                if (!(h >= radius)) {
                    float rr = (float) Math.sqrt((double) (radius * radius - h * h));
                    flat(v.pose, RING, x, y + h, z, rr / 0.86F, 0.0F, c.light, 0.25F * fade, 0.7F * fade);
                }
            }
        }

        if (t < 0.2F) {
            sprite(v, ORB, x, y + 0.3F, z, 1.8F * k, c.light, 0.0F, 1.0F - t / 0.2F);
        }

        this.slabs(s, t, v, 0.36F, 0.62F, 0.55F, 1.0F);
        groundRing(v, x, y, z, t, 0.0F, 0.62F, s.reach * 1.05F, c.light, 1.0F);
        groundRing(v, x, y, z, t, 0.1F, 0.9F, s.reach * 0.8F, c.main, 0.6F);
        dust(s, t, v, 1.2F);
        float reach = reach(s);
        if (this.screen.on() && reach > 0.0F && t < 0.75F) {
            centre(v, x, y + 0.5F, z);
            float w = 1.0F - t / 0.75F;
            ScreenFx.wave(t / 0.75F * 1.4F, 0.07F, 0.05F * w * reach, 0.7F * w * reach);
            ScreenFx.flash(0.18F * (1.0F - clamp(t / 0.15F)) * reach, 1.0F, 1.0F, 1.0F);
        }
    }

    private static void shell(View v, float x, float y, float z, float radius, Pal c, float a) {
        if (!(a <= 0.01F) && !(radius <= 0.05F)) {
            int lat = 9;
            int lon = 40;
            BufferBuilder b = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            for (int i = 0; i < lat; i++) {
                for (int j = 0; j < lon; j++) {
                    for (int q = 0; q < 4; q++) {
                        int ii = i + (q != 1 && q != 2 ? 0 : 1);
                        int jj = j + (q >= 2 ? 1 : 0);
                        float phi = (float) ii / (float) lat * (float) Math.PI / 2.0F;
                        float th = (float) jj / (float) lon * (float) Math.PI * 2.0F;
                        float nx = Mth.cos((double) phi) * Mth.cos((double) th);
                        float ny = Mth.sin((double) phi);
                        float nz = Mth.cos((double) phi) * Mth.sin((double) th);
                        float px = x + nx * radius;
                        float py = y + ny * radius;
                        float pz = z + nz * radius;
                        Vector3f eye = v.toward(px, py, pz);
                        float facing = Math.abs((nx * eye.x + ny * eye.y + nz * eye.z) / (eye.length() + 1.0E-4F));
                        float rim = (float) Math.pow((double) (1.0F - facing), 2.4);
                        float low = 1.0F - (float) ii / (float) lat;
                        int col = Fx.lerp(c.main, c.light, low);
                        b.addVertex(v.pose, px, py, pz).setColor(alpha(col, a * rim * (0.35F + 0.65F * low)));
                    }
                }
            }

            ImpactDraw.draw(ImpactDraw.type(Mode.QUADS, true, false, true), b);
        }
    }

    private void slabs(Smash s, float t, View v, float height, float width, float lag, float life) {
        float g = 0.46F;
        float front = s.reach * easeOut(t / 0.62F) - lag;
        float fade = 1.0F - smooth((t - 0.25F) / (LIFE[s.style] * life - 0.25F));
        float lift = height * (0.6F + 0.6F * s.power) * s.scale * fade;
        if (!(lift <= 0.01F)) {
            PoseStack ps = v.ps;
            BlockRenderDispatcher renderer = Minecraft.getInstance().getBlockRenderer();
            MultiBufferSource.BufferSource out = buffers();
            boolean any = false;

            for (float[] tile : s.tiles) {
                float d = tile[2];
                float off = (d - front) / width;
                float h = lift * (float) Math.exp((double) (-off * off)) * (1.0F - 0.5F * d / s.reach);
                if (!(h < 0.02F)) {
                    float tilt = Mth.clamp(off * h * 1.6F / width, -0.55F, 0.55F);
                    ps.pushPose();
                    ps.translate(v.x(s.x) + tile[0], v.y(s.y) + h - g * 0.3F, v.z(s.z) + tile[1]);
                    ps.mulPose((Quaternionfc) (new Quaternionf())
                            .rotateAxis(tilt, -tile[1] / d, 0.0F, tile[0] / d)
                            .rotateY(tile[3]));
                    ps.scale(g * 0.98F, g * 0.6F, g * 0.98F);
                    ps.translate(-0.5F, -0.5F, -0.5F);
                    renderer.renderSingleBlock(s.block, ps, out, s.light, OverlayTexture.NO_OVERLAY);
                    ps.popPose();
                    any = true;
                }
            }

            if (any) {
                out.endBatch();
            }
        }
    }

    private void chunks(Smash s, float t, float dt, View v) {
        if (!s.chunks.isEmpty()) {
            PoseStack ps = v.ps;
            BlockRenderDispatcher renderer = Minecraft.getInstance().getBlockRenderer();
            MultiBufferSource.BufferSource out = buffers();
            float shrink = 1.0F - smooth((t - 1.8F) / 0.7F);

            for (Chunk c : s.chunks) {
                if (c.bounces < 3) {
                    c.vy -= (double) (20.0F * dt);
                    c.x += c.vx * (double) dt;
                    c.y += c.vy * (double) dt;
                    c.z += c.vz * (double) dt;
                    c.angle += c.spin * dt;
                    double floor = s.y + (double) c.size * 0.5;
                    if (c.y < floor && c.vy < 0.0) {
                        c.y = floor;
                        c.vy *= -0.3;
                        c.vx *= 0.55;
                        c.vz *= 0.55;
                        c.spin *= 0.5F;
                        if (++c.bounces >= 3) {
                            c.vx = c.vy = c.vz = 0.0;
                        }
                    }
                }

                float sz = c.size * shrink;
                if (!(sz <= 0.01F)) {
                    ps.pushPose();
                    ps.translate(v.x(c.x), v.y(c.y), v.z(c.z));
                    ps.mulPose((Quaternionfc) (new Quaternionf()).rotateAxis(c.angle, c.axis));
                    ps.scale(sz, sz, sz);
                    ps.translate(-0.5F, -0.5F, -0.5F);
                    renderer.renderSingleBlock(s.block, ps, out, s.light, OverlayTexture.NO_OVERLAY);
                    ps.popPose();
                }
            }

            out.endBatch();
        }
    }

    private static void dust(Smash s, float t, View v, float spread) {
        float a = smooth(t / 0.08F) * (1.0F - smooth((t - 0.35F) / 1.5F));
        if (!(a <= 0.01F)) {
            int col = Fx.lerp(s.dust, 0xFFB9B2A8, 0.35F);
            Random r = new Random(s.seed * 31L);
            float x0 = v.x(s.x);
            float y0 = v.y(s.y);
            float z0 = v.z(s.z);
            Emit e = new Emit(v.pose, 1.0F);

            for (int i = 0; i < 26; i++) {
                float ang = r.nextFloat() * (float) Math.PI * 2.0F;
                float far = 0.85F + r.nextFloat() * 0.35F;
                float up = r.nextFloat();
                float out = s.reach * spread * far * easeOut(t / 1.1F);
                float px = x0 + Mth.cos((double) ang) * out;
                float pz = z0 + Mth.sin((double) ang) * out;
                float py = y0 + 0.2F + 0.45F * up * easeOut(t / 1.3F);
                float size = (0.35F + 0.9F * easeOut(t / 1.3F)) * s.scale * (0.75F + 0.5F * r.nextFloat());
                quad(e, v, px, py, pz, size, r.nextFloat() * 6.0F + t * 0.4F, alpha(col, 0.55F * a));
            }

            ImpactDraw.draw(ImpactDraw.type(Fx.Tex.SMOKE.id, Mode.QUADS, true, false), e.b);
        }
    }

    private static MultiBufferSource.BufferSource buffers() {
        if (blocks == null) {
            blocks = MultiBufferSource.immediate(new ByteBufferBuilder(262144));
        }

        return blocks;
    }

    private void play(Smash s, SoundEvent sound, float vol, float pitch) {
        if (s.studio) {
            if (this.sound.on()) {
                ImpactDraw.studioSound(sound, vol * this.volume.f() * 0.6F, pitch);
            }

        } else {
            Minecraft mc = Minecraft.getInstance();
            if (this.sound.on() && mc.level != null && sound != null) {
                mc.level.playLocalSound(
                        s.x, s.y + 0.5, s.z, sound, SoundSource.PLAYERS, vol * this.volume.f(), pitch, false);
            }
        }
    }

    private static float reach(Smash s) {
        if (s.studio) {
            return 1.0F;
        } else {
            LocalPlayer me = Minecraft.getInstance().player;
            if (me == null) {
                return 0.0F;
            } else {
                double d = me.position().distanceTo(new Vec3(s.x, s.y, s.z));
                return d > 30.0 ? 0.0F : (float) (1.0 - d / 30.0);
            }
        }
    }

    private void jolt(Smash s, float strength) {
        if (this.shake.on()) {
            ImpactDraw.shake(strength * reach(s));
        }
    }

    private static void centre(View v, float x, float y, float z) {
        Vector4f q = new Vector4f(x, y, z, 1.0F);
        v.pose.transform(q);
        ImpactDraw.PROJECTION.transform(q);
        ScreenFx.project(q);
    }

    private static void twice(Matrix4f m, Identifier tex, float shape, float glow, Consumer<Emit> quads) {
        if (shape > 0.003F) {
            Emit e = new Emit(m, shape);
            quads.accept(e);
            ImpactDraw.draw(ImpactDraw.type(tex, Mode.QUADS, true, false), e.b);
        }

        if (glow > 0.003F) {
            Emit e = new Emit(m, glow);
            quads.accept(e);
            ImpactDraw.draw(ImpactDraw.type(tex, Mode.QUADS, true, true), e.b);
        }
    }

    private static void quad(Emit e, View v, float x, float y, float z, float half, float rot, int c) {
        float cr = Mth.cos((double) rot) * half;
        float sr = Mth.sin((double) rot) * half;
        Vector3f l = v.left;
        Vector3f u = v.up;
        float[][] k =
                new float[][] {{cr - sr, sr + cr}, {cr + sr, sr - cr}, {-cr + sr, -sr - cr}, {-cr - sr, -sr + cr}};
        float[] tu = {0.0F, 0.0F, 1.0F, 1.0F};
        float[] tv = {0.0F, 1.0F, 1.0F, 0.0F};

        for (int i = 0; i < 4; i++) {
            e.v(
                    x + l.x * k[i][0] + u.x * k[i][1],
                    y + l.y * k[i][0] + u.y * k[i][1],
                    z + l.z * k[i][0] + u.z * k[i][1],
                    tu[i],
                    tv[i],
                    c);
        }
    }

    private static void sprite(
            View v, Identifier tex, float x, float y, float z, float half, int c, float shape, float glow) {
        twice(v.pose, tex, shape, glow, (e) -> quad(e, v, x, y, z, half, 0.0F, c));
    }

    private static void flat(
            Matrix4f pose,
            Identifier tex,
            float x,
            float y,
            float z,
            float half,
            float rot,
            int c,
            float shape,
            float glow) {
        float cr = Mth.cos((double) rot) * half;
        float sr = Mth.sin((double) rot) * half;
        twice(pose, tex, shape, glow, (e) -> {
            e.v(x - cr + sr, y, z - sr - cr, 0.0F, 0.0F, c);
            e.v(x - cr - sr, y, z - sr + cr, 0.0F, 1.0F, c);
            e.v(x + cr - sr, y, z + sr + cr, 1.0F, 1.0F, c);
            e.v(x + cr + sr, y, z + sr - cr, 1.0F, 0.0F, c);
        });
    }

    private static void groundRing(
            View v, float x, float y, float z, float t, float from, float to, float radius, int c, float a) {
        float u = (t - from) / (to - from);
        if (!(u <= 0.0F) && !(u >= 1.0F)) {
            float k = (1.0F - u) * (1.0F - u);
            flat(v.pose, RING, x, y + 0.03F, z, radius * easeOut(u) / 0.86F, 0.0F, c, 0.3F * k * a, k * a);
        }
    }

    private static void flatLine(
            View v, float[] xs, float[] zs, float x, float y, float z, float grow, float k, Pal c, float a) {
        if (!(a <= 0.01F)) {
            int n = Math.max(2, Math.round((float) xs.length * grow));
            float[] gx = new float[n];
            float[] gz = new float[n];

            for (int i = 0; i < n; i++) {
                gx[i] = x + xs[i];
                gz[i] = z + zs[i];
            }

            for (int pass = 0; pass < 2; pass++) {
                float width = pass == 0 ? 0.3F * k : 0.06F * k;
                int col = pass == 0 ? c.main : c.light;
                float alpha = pass == 0 ? 0.6F * a : a;
                twice(v.pose, Fx.Tex.LINE_GLOW.id, 0.0F, alpha, (e) -> {
                    for (int i = 0; i + 1 < n; i++) {
                        float dx = gx[i + 1] - gx[i];
                        float dz = gz[i + 1] - gz[i];
                        float l = (float) Math.hypot((double) dx, (double) dz) + 1.0E-5F;
                        float w0 = width * 0.5F * taper(i, n);
                        float w1 = width * 0.5F * taper(i + 1, n);
                        float sx = -dz / l;
                        float sz = dx / l;
                        int c0 = alpha(col, taper(i, n));
                        int c1 = alpha(col, taper(i + 1, n));
                        e.v(gx[i] - sx * w0, y, gz[i] - sz * w0, 0.0F, 0.0F, c0);
                        e.v(gx[i] + sx * w0, y, gz[i] + sz * w0, 0.0F, 1.0F, c0);
                        e.v(gx[i + 1] + sx * w1, y, gz[i + 1] + sz * w1, 1.0F, 1.0F, c1);
                        e.v(gx[i + 1] - sx * w1, y, gz[i + 1] - sz * w1, 1.0F, 0.0F, c1);
                    }
                });
            }
        }
    }

    private static void ribbon(
            View v, Identifier tex, float[] xs, float[] ys, float[] zs, float width, int c, float a) {
        int n = xs.length;
        float[] ox = new float[n];
        float[] oy = new float[n];
        float[] oz = new float[n];

        for (int i = 0; i < n; i++) {
            int p = Math.max(0, i - 1);
            int q = Math.min(n - 1, i + 1);
            float tx = xs[q] - xs[p];
            float ty = ys[q] - ys[p];
            float tz = zs[q] - zs[p];
            Vector3f eye = v.toward(xs[i], ys[i], zs[i]);
            float cx = ty * eye.z - tz * eye.y;
            float cy = tz * eye.x - tx * eye.z;
            float cz = tx * eye.y - ty * eye.x;
            float l = (float) Math.sqrt((double) (cx * cx + cy * cy + cz * cz));
            float w = width * 0.5F * taper(i, n) / Math.max(l, 1.0E-6F);
            ox[i] = cx * w;
            oy[i] = cy * w;
            oz[i] = cz * w;
        }

        twice(v.pose, tex, 0.0F, a, (e) -> {
            for (int i = 0; i + 1 < n; i++) {
                int c0 = alpha(c, taper(i, n));
                int c1 = alpha(c, taper(i + 1, n));
                e.v(xs[i] - ox[i], ys[i] - oy[i], zs[i] - oz[i], 0.0F, 0.0F, c0);
                e.v(xs[i] + ox[i], ys[i] + oy[i], zs[i] + oz[i], 0.0F, 1.0F, c0);
                e.v(xs[i + 1] + ox[i + 1], ys[i + 1] + oy[i + 1], zs[i + 1] + oz[i + 1], 1.0F, 1.0F, c1);
                e.v(xs[i + 1] - ox[i + 1], ys[i + 1] - oy[i + 1], zs[i + 1] - oz[i + 1], 1.0F, 0.0F, c1);
            }
        });
    }

    private static float taper(int i, int n) {
        if (n < 3) {
            return 1.0F;
        } else {
            float f = (float) i / (float) (n - 1);
            return smooth(Math.min(f, 1.0F - f) / 0.2F) * 0.85F + 0.15F * (float) (i != 0 && i != n - 1 ? 1 : 0);
        }
    }

    private static float clamp(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    private static float smooth(float t) {
        t = clamp(t);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float easeOut(float t) {
        t = 1.0F - clamp(t);
        return 1.0F - t * t * t;
    }

    static int alpha(int c, float a) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, a)) * (float) (c >>> 24)) << 24 | c & 0xFFFFFF;
    }

    private static float r(int c) {
        return (float) (c >> 16 & 255) / 255.0F;
    }

    private static float g(int c) {
        return (float) (c >> 8 & 255) / 255.0F;
    }

    private static float b(int c) {
        return (float) (c & 255) / 255.0F;
    }

    private static final class Smash {
        final int style;
        long start = System.currentTimeMillis();
        boolean studio;
        final Random rnd = new Random();
        final double x;
        final double y;
        final double z;
        final float power;
        final float scale;
        final float reach;
        final BlockState block;
        final int light;
        final int dust;
        int done;
        float last;
        float owed;
        final List<float[]> tiles = new ArrayList<>();
        final List<Chunk> chunks = new ArrayList<>();
        final List<float[][]> cracks = new ArrayList<>();
        final List<float[]> arcs = new ArrayList<>();
        final long seed;

        Smash(
                int style,
                double x,
                double y,
                double z,
                float power,
                float scale,
                BlockState block,
                int light,
                int dust) {
            this.style = style;
            this.x = x;
            this.y = y;
            this.z = z;
            this.power = power;
            this.scale = scale;
            this.block = block;
            this.light = light;
            this.dust = dust;
            this.seed = this.rnd.nextLong();
            float f2;
            switch (style) {
                case 1 -> f2 = 1.5F + 1.1F * power;
                case 2 -> f2 = 2.6F + 2.1F * power;
                default -> f2 = 2.2F + 1.9F * power;
            }

            this.reach = scale * f2;
        }

        boolean once(int moment) {
            if ((this.done & 1 << moment) != 0) {
                return false;
            } else {
                this.done |= 1 << moment;
                return true;
            }
        }
    }

    private static final class Chunk {
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        final Vector3f axis;
        float angle;
        float spin;
        float size;
        int bounces;

        Chunk(Vector3f axis) {
            this.axis = axis;
        }
    }

    private static record Pal(int light, int main, int deep) {}

    private static record View(PoseStack ps, Matrix4f pose, Vec3 cam, Vector3f left, Vector3f up, Vector3f forward) {
        Vector3f toward(float x, float y, float z) {
            return this.forward != null ? this.forward : new Vector3f(x, y, z);
        }

        float x(double w) {
            return (float) (w - this.cam.x);
        }

        float y(double w) {
            return (float) (w - this.cam.y);
        }

        float z(double w) {
            return (float) (w - this.cam.z);
        }
    }

    private static final class Emit {
        final BufferBuilder b;
        final Matrix4f m;
        final float k;

        Emit(Matrix4f m, float k) {
            this.b = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            this.m = m;
            this.k = k;
        }

        void v(float x, float y, float z, float u, float v, int c) {
            this.b.addVertex(this.m, x, y, z).setUv(u, v).setColor(MaceFx.alpha(c, this.k));
        }
    }
}
