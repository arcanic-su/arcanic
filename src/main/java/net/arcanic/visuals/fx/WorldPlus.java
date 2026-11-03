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

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.hud.DynamicIsland;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.core.Holder;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;

public final class WorldPlus {
    private WorldPlus() {}

    static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static void world(WorldRenderContext ctx) {
        if (mc().level != null && mc().player != null) {
            Vec3 cam = mc().gameRenderer.getMainCamera().position();
            float pt = mc().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            if (Modules.HIT_RANGE.enabled()) {
                Modules.HIT_RANGE.render(ctx, cam, pt);
            }

            if (Modules.SLIPSTREAM.enabled()) {
                Modules.SLIPSTREAM.render(ctx, cam, pt);
            }
        }
    }

    public static void hud(GuiGraphics g) {
        if (mc().level != null && mc().player != null) {
            if (Modules.CRYSTALS.enabled()) {
                Modules.CRYSTALS.hud(g);
            }
        }
    }

    public static final class ItemPhysics extends Module {
        final Setting.Num spin = this.add(new Setting.Num("spin", "Вращение в полёте", 100.0, 0.0, 200.0, 10.0, "%"));
        private final Map<Integer, float[]> tilt = new HashMap<>();

        public ItemPhysics() {
            super(
                    "item_physics",
                    "Физика предметов",
                    "Выброшенные предметы лежат на земле и кувыркаются в полёте",
                    Category.WORLD,
                    false);
        }

        public void place(PoseStack ps, ItemEntityRenderState s) {
            ItemState st = (ItemState) s;
            AABB box = s.item.getModelBoundingBox();
            boolean flat = box.maxZ - box.minZ < 0.13;
            long now = System.nanoTime();
            float[] t = (float[]) this.tilt.computeIfAbsent(
                    st.arcanicvisuals$id(), (k) -> new float[] {flat ? 90.0F : 0.0F, (float) now});
            float dt = Math.min(0.1F, ((float) now - t[1]) / 1.0E9F);
            t[1] = (float) now;
            float yaw = s.bobOffset * 57.3F % 360.0F;
            if (st.arcanicvisuals$ground()) {
                float rest = flat
                        ? (float) (Math.abs(Math.floorMod(Math.round(t[0]), 360) - 270) < 90 ? 270 : 90)
                        : (float) (Math.round(t[0] / 90.0F) * 90);
                if (flat && Math.abs(t[0] - rest) > 180.0F) {
                    t[0] += t[0] < rest ? 360.0F : -360.0F;
                }

                t[0] += (rest - t[0]) * (1.0F - (float) Math.exp((double) (-dt * 14.0F)));
                float lie = (float) Math.abs(Math.sin(Math.toRadians((double) t[0])));
                float lift = flat
                        ? (float) (box.maxZ + 0.005) * lie + (float) (-box.minY) * (1.0F - lie)
                        : (float) (-box.minY);
                ps.translate(0.0F, lift, 0.0F);
                ps.mulPose((Quaternionfc) Axis.YP.rotationDegrees(yaw));
                ps.mulPose((Quaternionfc) Axis.XP.rotationDegrees(t[0]));
            } else {
                t[0] = (t[0] + dt * 540.0F * this.spin.f() / 100.0F) % 360.0F;
                ps.translate(0.0F, (float) (-(box.minY + box.maxY) / 2.0) + 0.06F, 0.0F);
                ps.mulPose((Quaternionfc) Axis.YP.rotationDegrees(yaw + s.ageInTicks * 4.0F * this.spin.f() / 100.0F));
                ps.mulPose((Quaternionfc) Axis.XP.rotationDegrees(t[0]));
            }

            if (this.tilt.size() > 512) {
                this.tilt.clear();
            }
        }
    }

    public static final class ChunkAnimator extends Module {
        final Setting.Mode from =
                this.add(new Setting.Mode("from", "Откуда", "Снизу", new String[] {"Снизу", "Сверху"}));
        final Setting.Num time = this.add(new Setting.Num("time", "Длительность", 0.6, 0.2, 1.5, 0.05, " с"));
        final Setting.Num depth = this.add(new Setting.Num("depth", "Глубина", 14.0, 4.0, 40.0, 1.0, " бл"));

        public ChunkAnimator() {
            super(
                    "chunk_animator",
                    "Появление чанков",
                    "Новые чанки плавно поднимаются на место",
                    Category.WORLD,
                    false);
        }

        public Matrix4fc move(Matrix4fc mv, long uploaded, int ox, int oy, int oz) {
            if (uploaded <= 0L) {
                return mv;
            } else {
                float t = (float) (Util.getMillis() - uploaded) / (float) ((Double) this.time.get() * 1000.0);
                if (!(t >= 1.0F) && !(t < 0.0F)) {
                    Vec3 c = WorldPlus.mc().gameRenderer.getMainCamera().position();
                    if (Math.abs((double) (ox + 8) - c.x) < 24.0 && Math.abs((double) (oz + 8) - c.z) < 24.0) {
                        return mv;
                    } else {
                        float e = 1.0F - (float) Math.pow((double) (1.0F - t), 3.0);
                        float d = this.depth.f() * (1.0F - e) * (float) (this.from.is("Сверху") ? 1 : -1);
                        return (new Matrix4f(mv)).translate(0.0F, d, 0.0F);
                    }
                } else {
                    return mv;
                }
            }
        }
    }

    public static final class HitRange extends Module {
        final Setting.Bool self = this.add(new Setting.Bool("self", "Вокруг себя", true));
        final Setting.Bool others = this.add(new Setting.Bool("others", "Под игроками", true));
        private final Map<Integer, float[]> shade = new HashMap<>();

        public HitRange() {
            super(
                    "hit_range",
                    "Дистанция удара",
                    "Круг досягаемости удара: зелёный — цель достаёт, красный — тебя достают",
                    Category.COMBAT,
                    false);
        }

        private static float reachTo(Player from, Entity to) {
            Vec3 eye = from.getEyePosition();
            AABB b = to.getBoundingBox();
            double x = Math.max(b.minX, Math.min(eye.x, b.maxX));
            double y = Math.max(b.minY, Math.min(eye.y, b.maxY));
            double z = Math.max(b.minZ, Math.min(eye.z, b.maxZ));
            return (float) eye.distanceTo(new Vec3(x, y, z));
        }

        private float ease(int id, float to) {
            float[] s = (float[]) this.shade.computeIfAbsent(id, (k) -> new float[] {to});
            s[0] += (to - s[0]) * 0.18F;
            return s[0];
        }

        void render(WorldRenderContext ctx, Vec3 cam, float pt) {
            Player me = WorldPlus.mc().player;
            PoseStack.Pose pose = ctx.matrices().last();
            VertexConsumer vc = ctx.consumers().getBuffer(Fx.type(Fx.Tex.RING_SOFT, true));
            double reach = me.entityInteractionRange();
            Set<Integer> live = new HashSet<>();
            boolean anyIn = false;

            for (Player p : WorldPlus.mc().level.players()) {
                if (p != me && !p.isSpectator() && p.isAlive() && !(p.distanceToSqr(me) > 256.0)) {
                    if ((double) reachTo(me, p) <= reach) {
                        anyIn = true;
                    }

                    if (this.others.on() && !p.isInvisible()) {
                        live.add(p.getId());
                        boolean meIn = (double) reachTo(p, me) <= p.entityInteractionRange();
                        float k = this.ease(p.getId(), meIn ? 1.0F : 0.0F);
                        ring(
                                vc,
                                pose,
                                cam,
                                p,
                                pt,
                                (float) p.entityInteractionRange(),
                                Fx.lerp(1728053247, 0xCCFF5A64, k),
                                0.55F);
                    }
                }
            }

            if (this.self.on()) {
                float k = this.ease(me.getId(), anyIn ? 1.0F : 0.0F);
                ring(vc, pose, cam, me, pt, (float) reach, Fx.lerp(1442840575, 0xDD4ADE80, k), 0.7F);
                live.add(me.getId());
            }

            this.shade.keySet().retainAll(live);
        }

        private static void ring(
                VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, Entity e, float pt, float r, int col, float a) {
            double x = e.xo + (e.getX() - e.xo) * (double) pt;
            double y = e.yo + (e.getY() - e.yo) * (double) pt;
            double z = e.zo + (e.getZ() - e.zo) * (double) pt;
            float cx = (float) (x - cam.x);
            float cy = (float) (y - cam.y) + 0.03F;
            float cz = (float) (z - cam.z);
            Fx.quad(vc, pose, cx, cy, cz, r, 0.0F, 0.0F, 0.0F, 0.0F, r, WorldFx.alpha(col, a));
        }
    }

    public static final class Fullbright extends Module {
        final Setting.Num level = this.add(new Setting.Num("level", "Яркость", 100.0, 20.0, 100.0, 5.0, "%"));
        private float shown;

        public Fullbright() {
            super(
                    "fullbright",
                    "Полная яркость",
                    "Всё видно и в темноте, без зелья ночного зрения",
                    Category.WORLD,
                    false);
        }

        public static float nightVision(float original) {
            Fullbright f = Modules.FULLBRIGHT;
            if (f == null) {
                return original;
            } else {
                f.shown += ((f.enabled() ? f.level.f() / 100.0F : 0.0F) - f.shown) * 0.2F;
                if (f.shown < 0.002F) {
                    f.shown = 0.0F;
                }

                return Math.max(original, f.shown);
            }
        }
    }

    public static final class Slipstream extends ArcVisuals.Colored {
        final Setting.Num amount = this.add(new Setting.Num("amount", "Плотность", 100.0, 20.0, 200.0, 10.0, "%"));
        private final List<Streak> streaks = new ArrayList<>();
        private final Random rnd = new Random();
        private long last;

        public Slipstream() {
            super("slipstream", "Поток в полёте", "Струи воздуха вокруг при полёте на элитрах", Category.EFFECTS);
        }

        void render(WorldRenderContext ctx, Vec3 cam, float pt) {
            Player p = WorldPlus.mc().player;
            long now = System.nanoTime();
            float dt = this.last == 0L ? 0.0F : Math.min(0.05F, (float) (now - this.last) / 1.0E9F);
            this.last = now;
            Vec3 v = p.getDeltaMovement();
            double speed = v.length();
            boolean flying = p.isFallFlying() && speed > 0.45;
            if (flying) {
                float rate = (float) ((speed - 0.45) * 120.0 * (double) this.amount.f() / 100.0);
                int n = (int) (rate * dt * 20.0F + this.rnd.nextFloat());
                Vec3 dir = v.normalize();
                Vec3 side = dir.cross(new Vec3(0.0, 1.0, 0.0));
                if (side.lengthSqr() < 1.0E-4) {
                    side = new Vec3(1.0, 0.0, 0.0);
                }

                side = side.normalize();
                Vec3 up = side.cross(dir).normalize();

                for (int i = 0; i < n && this.streaks.size() < 160; ++i) {
                    double ang = this.rnd.nextDouble() * Math.PI * 2.0;
                    double rad = 1.2 + this.rnd.nextDouble() * 3.2;
                    Streak s = new Streak();
                    s.at = cam.add(dir.scale(4.0 + this.rnd.nextDouble() * 9.0))
                            .add(side.scale(Math.cos(ang) * rad))
                            .add(up.scale(Math.sin(ang) * rad));
                    s.len = (float) (speed * (1.2 + this.rnd.nextDouble() * 1.6));
                    s.life = 0.35F + this.rnd.nextFloat() * 0.3F;
                    this.streaks.add(s);
                }
            }

            if (!this.streaks.isEmpty()) {
                PoseStack.Pose pose = ctx.matrices().last();
                VertexConsumer vc = ctx.consumers().getBuffer(Fx.type(Fx.Tex.GLOW, true));
                Vec3 dir = speed > 0.001 ? v.normalize() : new Vec3(0.0, 0.0, 1.0);

                for (int i = this.streaks.size() - 1; i >= 0; --i) {
                    Streak s = (Streak) this.streaks.get(i);
                    s.age += dt;
                    if (s.age >= s.life) {
                        this.streaks.remove(i);
                    } else {
                        float k = s.age / s.life;
                        float a = (float) Math.sin((double) k * Math.PI) * (flying ? 1.0F : 0.6F);
                        Vec3 c = s.at.subtract(cam);
                        Vec3 toCam = c.normalize();
                        Vec3 wide = dir.cross(toCam);
                        if (!(wide.lengthSqr() < 1.0E-5)) {
                            wide = wide.normalize().scale(0.035);
                            Vec3 along = dir.scale((double) (s.len / 2.0F));
                            int col = this.color((float) i / (float) Math.max(1, this.streaks.size()));
                            Fx.quad(
                                    vc,
                                    pose,
                                    (float) c.x,
                                    (float) c.y,
                                    (float) c.z,
                                    (float) along.x,
                                    (float) along.y,
                                    (float) along.z,
                                    (float) wide.x,
                                    (float) wide.y,
                                    (float) wide.z,
                                    WorldFx.alpha(Fx.lerp(-1, col, 0.45F), 0.5F * a));
                        }
                    }
                }
            }
        }

        private static final class Streak {
            Vec3 at;
            float len;
            float life;
            float age;
        }
    }

    public static final class Crystals extends Module {
        final Setting.Colour colour = this.add(new Setting.Colour("color", "Цвет", false, -4616961));
        final Setting.Bool tint = this.add(new Setting.Bool("tint", "Перекрашивать", true));
        final Setting.Num size = this.add(new Setting.Num("size", "Размер", 100.0, 50.0, 150.0, 5.0, "%"));
        final Setting.Bool damage = this.add(new Setting.Bool("damage", "Урон по мне", true));
        private final Map<Integer, Float> hurt = new HashMap<>();
        public static EntityRenderState rendering;
        private final List<double[]> danger = new ArrayList<>();

        public Crystals() {
            super(
                    "crystal_look",
                    "Кристаллы",
                    "Цвет и размер кристаллов, урон от взрыва по тебе",
                    Category.WORLD,
                    false);
        }

        public int tintFor(int original) {
            int base = this.enabled() && this.tint.on() ? this.colour.argb() | 0xFF000000 : original;
            EntityRenderState st = rendering;
            Player me = WorldPlus.mc().player;
            if (this.enabled() && this.damage.on() && st != null && me != null) {
                float dmg = 0.0F;

                for (double[] d : this.danger) {
                    if (Math.abs(d[0] - st.x) < 1.0 && Math.abs(d[1] - st.y) < 1.0 && Math.abs(d[2] - st.z) < 1.0) {
                        dmg = (float) Math.max((double) dmg, d[3]);
                    }
                }

                if (dmg < 0.5F) {
                    return base;
                } else {
                    float life = me.getHealth() + me.getAbsorptionAmount();
                    float k = Math.min(1.0F, dmg / Math.max(1.0F, life));
                    if (dmg >= life) {
                        float beat = 0.5F
                                + 0.5F
                                        * (float) Math.sin(
                                                (double) System.nanoTime() / (double) 1.0E9F * Math.PI * 2.0 * 1.6);
                        return Fx.lerp(-53698, -28776, beat);
                    } else {
                        return Fx.lerp(base, Fx.lerp(-14249, -45730, k), 0.35F + 0.65F * k);
                    }
                }
            } else {
                return base;
            }
        }

        public float scale() {
            return this.enabled() ? this.size.f() / 100.0F : 1.0F;
        }

        public void tick() {
            this.hurt.clear();
            this.danger.clear();
            Player me = WorldPlus.mc().player;
            if (me != null && WorldPlus.mc().level != null && this.damage.on()) {
                for (Entity e : WorldPlus.mc().level.entitiesForRendering()) {
                    if (e instanceof EndCrystal) {
                        EndCrystal c = (EndCrystal) e;
                        if (c.distanceToSqr(me) < 196.0) {
                            float d = blast(c.position(), me);
                            this.hurt.put(c.getId(), d);
                            this.danger.add(new double[] {c.getX(), c.getY(), c.getZ(), (double) d});
                        }
                    }
                }
            }
        }

        static float blast(Vec3 at, Player me) {
            double f = 12.0;
            double d = Math.sqrt(me.distanceToSqr(at)) / f;
            if (d > 1.0) {
                return 0.0F;
            } else {
                double seen = (double) ServerExplosion.getSeenPercent(at, me);
                double impact = (1.0 - d) * seen;
                float dmg = (float) ((impact * impact + impact) / 2.0 * 7.0 * f + 1.0);
                Difficulty diff = WorldPlus.mc().level.getDifficulty();
                if (diff == Difficulty.PEACEFUL) {
                    dmg = 0.0F;
                } else if (diff == Difficulty.EASY) {
                    dmg = Math.min(dmg / 2.0F + 1.0F, dmg);
                } else if (diff == Difficulty.HARD) {
                    dmg *= 1.5F;
                }

                dmg = CombatRules.getDamageAfterAbsorb(
                        me,
                        dmg,
                        WorldPlus.mc().level.damageSources().explosion((Entity) null, (Entity) null),
                        (float) me.getArmorValue(),
                        (float) me.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
                int prot = 0;

                for (EquipmentSlot s : new EquipmentSlot[] {
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
                }) {
                    ItemStack st = me.getItemBySlot(s);

                    for (Object2IntMap.Entry<Holder<Enchantment>> en :
                            st.getEnchantments().entrySet()) {
                        if (((Holder) en.getKey()).is(Enchantments.PROTECTION)) {
                            prot += en.getIntValue();
                        } else if (((Holder) en.getKey()).is(Enchantments.BLAST_PROTECTION)) {
                            prot += en.getIntValue() * 2;
                        }
                    }
                }

                dmg = CombatRules.getDamageAfterMagicAbsorb(dmg, (float) Math.min(20, prot));
                MobEffectInstance res = me.getEffect(MobEffects.RESISTANCE);
                if (res != null) {
                    dmg *= Math.max(0.0F, 1.0F - (float) (res.getAmplifier() + 1) * 0.2F);
                }

                return Math.max(0.0F, dmg);
            }
        }

        void hud(GuiGraphics g) {
            if (this.damage.on() && !this.hurt.isEmpty()) {
                Player me = WorldPlus.mc().player;
                float life = me.getHealth() + me.getAbsorptionAmount();
                float worst = 0.0F;

                for (Float d : this.hurt.values()) {
                    if (d != null) {
                        worst = Math.max(worst, d);
                    }
                }

                if (worst >= 0.5F) {
                    DynamicIsland.crystalHearts(worst, worst >= life);
                }
            }
        }
    }

    public interface ItemState {
        boolean arcanicvisuals$ground();

        int arcanicvisuals$id();

        void arcanicvisuals$set(boolean var1, int var2);
    }
}
