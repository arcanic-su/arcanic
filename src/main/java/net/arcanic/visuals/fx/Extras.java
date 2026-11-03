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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Small looks taken over from Pulse: hurt colour, hit bubbles, body glow, a halo, a hat, your own name, motes. */
public final class Extras {
    private static final Identifier WHITE =
            Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/effect/white.png");

    private Extras() {}

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static void register() {
        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (level.isClientSide() && target instanceof LivingEntity) Modules.HIT_BUBBLE.hit();
            return InteractionResult.PASS;
        });
    }

    /** Called from the world pass. */
    static void world(WorldRenderContext ctx, Vec3 cam, float pt) {
        Modules.HIT_BUBBLE.render(ctx, cam);
    }

    /** Called right after a living entity's body is drawn, with the model posed and the pose at its root. */
    public static void afterBody(
            EntityModel<?> model, LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector c) {
        if (state.isInvisible) return;
        Entity e = Ducks.entityOf(state);
        if (Modules.BODY_GLOW.enabled() && e instanceof Player) Modules.BODY_GLOW.glow(model, state, pose, c);
        if (e != mc().player || !(model instanceof HumanoidModel<?> humanoid)) return;
        if (Modules.HALO.enabled()) Modules.HALO.draw(humanoid, pose, c);
        if (Modules.CHINA_HAT.enabled()) Modules.CHINA_HAT.draw(humanoid, pose, c);
    }

    private static void v(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float w, int c) {
        vc.addVertex(p, x, y, z)
                .setColor(c)
                .setUv(u, w)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(p, 0.0F, -1.0F, 0.0F);
    }

    private static RenderType additive(Identifier tex) {
        RenderType t = Fx.glowWith(tex);
        return t != null ? t : RenderTypes.eyes(tex);
    }

    /** Moves the pose to the head: the model's own space there has y pointing down, the top of the head at -0.5. */
    private static void toHead(HumanoidModel<?> m, PoseStack pose) {
        m.root().translateAndRotate(pose);
        m.head.translateAndRotate(pose);
    }

    // ---------------------------------------------------------------- hit colour

    /** The flash of a hurt entity in your own colour: the whole body, or tinting the skin. */
    public static final class HitColor extends Module {
        private final Setting.Mode mode = this.add(new Setting.Mode("mode", "Режим", "Полностью", "Полностью", "Скин"));
        private final Setting.Colour colour = this.add(new Setting.Colour("color", "Цвет", true));

        public HitColor() {
            super("hit_color", "Hit Color", "Цвет сущности, когда она получает урон", Category.COMBAT, false);
        }

        private boolean hurt(Object state) {
            return this.enabled() && state instanceof LivingEntityRenderState l && l.hasRedOverlay;
        }

        public RenderType type(Object state, RenderType type) {
            return this.hurt(state) && this.mode.is("Полностью") ? RenderTypes.entityTranslucent(WHITE) : type;
        }

        public int overlay(Object state, int overlay) {
            return this.hurt(state) ? OverlayTexture.NO_OVERLAY : overlay;
        }

        public int colour(Object state, int colour) {
            if (!this.hurt(state)) return colour;
            int c = this.colour.argb() | 0xFF000000;
            if (this.mode.is("Полностью")) return c;
            int r = (colour >> 16 & 255) * (c >> 16 & 255) / 255;
            int g = (colour >> 8 & 255) * (c >> 8 & 255) / 255;
            int b = (colour & 255) * (c & 255) / 255;
            return colour & 0xFF000000 | r << 16 | g << 8 | b;
        }
    }

    // ---------------------------------------------------------------- hit bubbles

    /** A ring of light that opens where you strike and fades as it spins out. */
    public static final class HitBubble extends Module {
        private static final Identifier[] TEXTURES = {
            Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/effect/bubble_1.png"),
            Identifier.fromNamespaceAndPath("arcanicvisuals", "textures/effect/bubble_2.png")
        };
        private final Setting.Num life = this.add(new Setting.Num("life", "Время жизни", 40.0, 10.0, 150.0, 5.0, " т"));
        private final Setting.Mode texture =
                this.add(new Setting.Mode("texture", "Текстура", "Пузырь 1", "Пузырь 1", "Пузырь 2"));
        private final Setting.Colour colour = this.add(new Setting.Colour("color", "Цвет", true));
        private final List<Bubble> bubbles = new ArrayList<>();

        private record Bubble(Vec3 at, float yaw, float pitch, long born) {}

        public HitBubble() {
            super("hit_bubble", "Hit Bubble", "Пузырь света там, куда ты ударил", Category.COMBAT, false);
        }

        void hit() {
            LocalPlayer me = mc().player;
            if (!this.enabled() || me == null || mc().level == null) return;
            float pt = mc().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            Vec3 eye = me.getEyePosition(pt);
            Vec3 reach = eye.add(me.getViewVector(pt));
            BlockHitResult block =
                    mc().level.clip(new ClipContext(eye, reach, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, me));
            Vec3 at = block.getType() != HitResult.Type.MISS ? block.getLocation() : reach;
            this.bubbles.add(new Bubble(at, -me.getViewYRot(pt), me.getViewXRot(pt), System.currentTimeMillis()));
        }

        void render(WorldRenderContext ctx, Vec3 cam) {
            if (this.bubbles.isEmpty()) return;
            long now = System.currentTimeMillis();
            long life = Math.round((Double) this.life.get() * 50.0);
            this.bubbles.removeIf(b -> now - b.born() >= life);
            if (!this.enabled() || this.bubbles.isEmpty()) return;
            Identifier tex = TEXTURES[this.texture.is("Пузырь 2") ? 1 : 0];
            VertexConsumer vc = ctx.consumers().getBuffer(additive(tex));
            int c = this.colour.argb() | 0xFF000000;
            PoseStack ps = ctx.matrices();
            for (Bubble b : this.bubbles) {
                long age = now - b.born();
                float grow = age / 1500.0F;
                float fade = (1.0F - grow) * 0.8F;
                if (fade <= 0.0F) continue;
                float half = grow;
                ps.pushPose();
                ps.translate(b.at().x - cam.x, b.at().y - cam.y, b.at().z - cam.z);
                ps.mulPose(new Quaternionf().rotationY((float) Math.toRadians(b.yaw())));
                ps.mulPose(new Quaternionf().rotationX((float) Math.toRadians(b.pitch())));
                ps.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(-age / 4.0F)));
                PoseStack.Pose p = ps.last();
                int col = WorldFx.alpha(c, fade);
                v(vc, p, -half, -half, 0.0F, 0.0F, 0.0F, col);
                v(vc, p, half, -half, 0.0F, 1.0F, 0.0F, col);
                v(vc, p, half, half, 0.0F, 1.0F, 1.0F, col);
                v(vc, p, -half, half, 0.0F, 0.0F, 1.0F, col);
                ps.popPose();
            }
        }

        protected void onDisable() {
            this.bubbles.clear();
        }
    }

    // ---------------------------------------------------------------- body glow

    /** A soft glow sprite on every part of a player's body, so the figure shines from within. */
    public static final class BodyGlow extends Module {
        private final Setting.Num size = this.add(new Setting.Num("size", "Размер", 2.3, 1.5, 3.0, 0.1, "×"));
        private final Setting.Num opacity =
                this.add(new Setting.Num("opacity", "Прозрачность", 100.0, 0.0, 100.0, 1.0, "%"));
        private final Setting.Colour colour = this.add(new Setting.Colour("color", "Цвет", true));

        public BodyGlow() {
            super("body_glow", "Body Glow", "Мягкое свечение по телу игроков", Category.PLAYERS, false);
        }

        void glow(EntityModel<?> model, LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector c) {
            float a = ((Double) this.opacity.get()).floatValue() / 100.0F;
            if (a <= 0.0F) return;
            List<float[]> sprites = new ArrayList<>();
            Vector3f at = new Vector3f();
            model.root().visit(pose, (p, path, index, cube) -> {
                p.pose()
                        .transformPosition(
                                (cube.minX + cube.maxX) * 0.5F / 16.0F,
                                (cube.minY + cube.maxY) * 0.5F / 16.0F,
                                (cube.minZ + cube.maxZ) * 0.5F / 16.0F,
                                at);
                float w = (cube.maxX - cube.minX) / 16.0F;
                float h = (cube.maxY - cube.minY) / 16.0F;
                float d = (cube.maxZ - cube.minZ) / 16.0F;
                sprites.add(new float[] {at.x, at.y, at.z, (float) Math.sqrt(w * w + h * h + d * d) * state.scale});
            });
            if (sprites.isEmpty()) return;
            Quaternionf facing = mc().gameRenderer.getMainCamera().rotation();
            int col = WorldFx.alpha(this.colour.argb() | 0xFF000000, a);
            float scale = ((Double) this.size.get()).floatValue() * 0.5F;
            c.submitCustomGeometry(new PoseStack(), Fx.type(Fx.Tex.GLOW, true), (last, vc) -> {
                Vector3f r = new Vector3f();
                Vector3f u = new Vector3f();
                for (float[] s : sprites) {
                    float half = s[3] * scale;
                    facing.transform(r.set(half, 0.0F, 0.0F));
                    facing.transform(u.set(0.0F, half, 0.0F));
                    Matrix4f m = last.pose();
                    corner(vc, last, m, s, -r.x - u.x, -r.y - u.y, -r.z - u.z, 0.0F, 1.0F, col);
                    corner(vc, last, m, s, r.x - u.x, r.y - u.y, r.z - u.z, 1.0F, 1.0F, col);
                    corner(vc, last, m, s, r.x + u.x, r.y + u.y, r.z + u.z, 1.0F, 0.0F, col);
                    corner(vc, last, m, s, -r.x + u.x, -r.y + u.y, -r.z + u.z, 0.0F, 0.0F, col);
                }
            });
        }

        private static void corner(
                VertexConsumer vc,
                PoseStack.Pose p,
                Matrix4f m,
                float[] s,
                float dx,
                float dy,
                float dz,
                float u,
                float w,
                int c) {
            v(vc, p, s[0] + dx, s[1] + dy, s[2] + dz, u, w, c);
        }
    }

    // ---------------------------------------------------------------- halo

    /** A soft ring of light floating over your head (third person). */
    public static final class Halo extends Module {
        private final Setting.Num radius = this.add(new Setting.Num("radius", "Радиус", 0.45, 0.3, 1.5, 0.05, ""));
        private final Setting.Num height = this.add(new Setting.Num("height", "Высота", 0.0, -0.4, 0.6, 0.05, ""));
        private final Setting.Colour colour = this.add(new Setting.Colour("color", "Цвет", true));

        public Halo() {
            super("halo", "Нимб", "Светящийся нимб над твоей головой в 3-м лице", Category.PLAYERS, false);
        }

        void draw(HumanoidModel<?> m, PoseStack pose, SubmitNodeCollector c) {
            pose.pushPose();
            toHead(m, pose);
            float y = -0.72F - ((Double) this.height.get()).floatValue();
            float r = ((Double) this.radius.get()).floatValue();
            float t = (System.nanoTime() % 100000000000L) / 1.0E9F;
            float breathe = 0.85F + 0.15F * (float) Math.sin(t * 2.4);
            int col = this.colour.argb() | 0xFF000000;
            c.submitCustomGeometry(pose, Fx.type(Fx.Tex.RING_SOFT, true), (last, vc) -> {
                flat(vc, last, y, r * 1.08F, WorldFx.alpha(col, 0.35F * breathe));
                flat(vc, last, y, r, WorldFx.alpha(Fx.lerp(col, -1, 0.35F), 0.95F));
            });
            pose.popPose();
        }

        private static void flat(VertexConsumer vc, PoseStack.Pose p, float y, float r, int c) {
            v(vc, p, -r, y, -r, 0.0F, 0.0F, c);
            v(vc, p, r, y, -r, 1.0F, 0.0F, c);
            v(vc, p, r, y, r, 1.0F, 1.0F, c);
            v(vc, p, -r, y, r, 0.0F, 1.0F, c);
        }
    }

    // ---------------------------------------------------------------- china hat

    /** A wide shallow cone hat of soft colour over your head (third person). */
    public static final class ChinaHat extends Module {
        private static final int SEGMENTS = 48;
        private final Setting.Num width = this.add(new Setting.Num("width", "Ширина", 0.65, 0.4, 1.2, 0.05, ""));
        private final Setting.Num rise = this.add(new Setting.Num("rise", "Высота конуса", 0.35, 0.1, 0.8, 0.05, ""));
        private final Setting.Colour colour = this.add(new Setting.Colour("color", "Цвет", true));

        public ChinaHat() {
            super(
                    "china_hat",
                    "China Hat",
                    "Китайская шляпа-конус на твоей голове в 3-м лице",
                    Category.PLAYERS,
                    false);
        }

        void draw(HumanoidModel<?> m, PoseStack pose, SubmitNodeCollector c) {
            pose.pushPose();
            toHead(m, pose);
            float base = -0.52F;
            float apex = base - ((Double) this.rise.get()).floatValue();
            float r = ((Double) this.width.get()).floatValue();
            int col = this.colour.argb() | 0xFF000000;
            int top = WorldFx.alpha(Fx.lerp(col, -1, 0.25F), 0.55F);
            int rim = WorldFx.alpha(col, 0.3F);
            c.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(WHITE), (last, vc) -> {
                for (int i = 0; i < SEGMENTS; i++) {
                    double a0 = i * Math.PI * 2.0 / SEGMENTS;
                    double a1 = (i + 1) * Math.PI * 2.0 / SEGMENTS;
                    float x0 = (float) Math.cos(a0) * r;
                    float z0 = (float) Math.sin(a0) * r;
                    float x1 = (float) Math.cos(a1) * r;
                    float z1 = (float) Math.sin(a1) * r;
                    v(vc, last, 0.0F, apex, 0.0F, 0.5F, 0.5F, top);
                    v(vc, last, x0, base, z0, 0.0F, 0.0F, rim);
                    v(vc, last, x1, base, z1, 1.0F, 0.0F, rim);
                    v(vc, last, 0.0F, apex, 0.0F, 0.5F, 0.5F, top);
                    v(vc, last, 0.0F, apex, 0.0F, 0.5F, 0.5F, top);
                    v(vc, last, x1, base, z1, 1.0F, 0.0F, rim);
                    v(vc, last, x0, base, z0, 0.0F, 0.0F, rim);
                    v(vc, last, 0.0F, apex, 0.0F, 0.5F, 0.5F, top);
                }
            });
            c.submitCustomGeometry(pose, Fx.type(Fx.Tex.RING_SOFT, true), (last, vc) -> {
                int glow = WorldFx.alpha(Fx.lerp(col, -1, 0.3F), 0.7F);
                float rr = r * 1.04F;
                v(vc, last, -rr, base, -rr, 0.0F, 0.0F, glow);
                v(vc, last, rr, base, -rr, 1.0F, 0.0F, glow);
                v(vc, last, rr, base, rr, 1.0F, 1.0F, glow);
                v(vc, last, -rr, base, rr, 0.0F, 1.0F, glow);
            });
            pose.popPose();
        }
    }

    // ---------------------------------------------------------------- self nametag

    /** Your own name over your head when you look at yourself from behind or the front. */
    public static final class SelfName extends Module {
        public SelfName() {
            super("self_nametag", "Self Nametag", "Свой ник над головой в 3-м лице", Category.PLAYERS, false);
        }

        public boolean shows(Entity e) {
            Minecraft mc = mc();
            return this.enabled()
                    && e == mc.player
                    && !mc.options.getCameraType().isFirstPerson();
        }
    }

    // ---------------------------------------------------------------- world particles

    /** Slow motes or twinkling stars drifting in the air around you. */
    public static final class WorldParticles extends Module {
        private final Setting.Mode kind = this.add(new Setting.Mode("kind", "Вид", "Огоньки", "Огоньки", "Звёзды"));
        private final Setting.Num rate =
                this.add(new Setting.Num("rate", "Сколько в секунду", 12.0, 2.0, 60.0, 1.0, ""));
        private final Setting.Num range = this.add(new Setting.Num("range", "Радиус", 8.0, 3.0, 20.0, 1.0, " бл"));
        private final Setting.Colour colour = this.add(new Setting.Colour("color", "Цвет", true));
        private float owed;

        public WorldParticles() {
            super(
                    "world_particles",
                    "World Particles",
                    "Огоньки или звёзды, парящие вокруг тебя",
                    Category.WORLD,
                    false);
        }

        public void tick() {
            LocalPlayer me = mc().player;
            if (me == null || mc().level == null || mc().isPaused()) return;
            this.owed += ((Double) this.rate.get()).floatValue() / 20.0F;
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            double r = (Double) this.range.get();
            boolean stars = this.kind.is("Звёзды");
            int c = this.colour.argb() | 0xFF000000;
            while (this.owed >= 1.0F) {
                this.owed -= 1.0F;
                double a = rnd.nextDouble() * Math.PI * 2.0;
                double d = Math.sqrt(rnd.nextDouble()) * r;
                Vec3 at = me.position().add(Math.cos(a) * d, rnd.nextDouble() * 3.0 - 0.5, Math.sin(a) * d);
                Fx.spawn()
                        .at(at)
                        .vel(rnd.nextGaussian() * 0.05, 0.03 + rnd.nextDouble() * 0.06, rnd.nextGaussian() * 0.05)
                        .tex(stars ? Fx.Tex.STAR : Fx.Tex.GLOW)
                        .life(3.0F + rnd.nextFloat() * 2.5F)
                        .size(stars ? 0.16F : 0.1F, 0.0F)
                        .colour(Fx.lerp(c, -1, 0.45F), c)
                        .drag(0.4F)
                        .fadeIn(0.5F)
                        .twinkle(stars ? 1.0F : 0.0F)
                        .spin(rnd.nextFloat() * 6.28F, stars ? 0.6F : 0.0F);
            }
        }
    }
}
