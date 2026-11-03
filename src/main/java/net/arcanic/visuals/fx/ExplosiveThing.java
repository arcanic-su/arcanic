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

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.helper.HolyItems;
import net.arcanic.visuals.helper.HolyRules;
import net.arcanic.visuals.render.Px;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class ExplosiveThing extends Module {
    private final ItemZones owner;
    private final Setting.Colour zone;
    private final Setting.Bool hearts;
    private final Setting.Bool players;
    private final Setting.Bool scan;
    private static final float DEFAULT_POWER = 3.0F;
    private Use pending;
    private HolyRules.Use pendingUse;
    private boolean pendingOk;
    private Shot shot;
    private float show;
    private float hurt;
    private float selfDamage;
    private long heldSince;
    private long lastNanos = System.nanoTime();
    private final float[] hist = new float[40];
    private int histN;
    private final Map<Integer, float[]> marks = new HashMap<>();
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("arcanicvisuals/holyworld-thing.json");
    private static final Path LOG =
            FabricLoader.getInstance().getConfigDir().resolve("arcanicvisuals/holyworld-thing-log.csv");
    private boolean loaded;
    private float power = -1.0F;
    private final double[] offset = new double[4];
    private final double[] delay = new double[2];
    private final List<Float> scale = new ArrayList<>();
    private final List<String> ids = new ArrayList<>();

    public ExplosiveThing(ItemZones owner) {
        super("holy_thing", "Взрывная штучка", "", Category.HOLYWORLD, true);
        this.owner = owner;
        ItemZones.Item it = owner.item("thing");
        this.zone = it.zone;
        this.hearts = it.hearts;
        this.players = it.players;
        this.scan = it.glow;
    }

    public boolean enabled() {
        return this.owner.on("thing", this);
    }

    private void load() {
        if (!this.loaded) {
            this.loaded = true;

            try {
                if (!Files.exists(FILE, new LinkOption[0])) {
                    return;
                }

                JsonObject o = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                if (o.has("power")) {
                    this.power = o.get("power").getAsFloat();
                }

                if (o.has("offset")) {
                    for (int i = 0; i < 4; ++i) {
                        this.offset[i] = o.getAsJsonArray("offset").get(i).getAsDouble();
                    }
                }

                if (o.has("delay")) {
                    for (int i = 0; i < 2; ++i) {
                        this.delay[i] = o.getAsJsonArray("delay").get(i).getAsDouble();
                    }
                }

                if (o.has("scale")) {
                    for (JsonElement v : o.getAsJsonArray("scale")) {
                        this.scale.add(v.getAsFloat());
                    }
                }

                if (o.has("ids")) {
                    for (JsonElement v : o.getAsJsonArray("ids")) {
                        this.ids.add(v.getAsString());
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void save() {
        JsonObject o = new JsonObject();
        o.addProperty("power", this.power);
        JsonArray off = new JsonArray();
        JsonArray del = new JsonArray();
        JsonArray sc = new JsonArray();
        JsonArray id = new JsonArray();

        for (double d : this.offset) {
            off.add(d);
        }

        for (double d : this.delay) {
            del.add(d);
        }

        this.scale.forEach(sc::add);
        this.ids.forEach(id::add);
        o.add("offset", off);
        o.add("delay", del);
        o.add("scale", sc);
        o.add("ids", id);

        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(
                    FILE, (new GsonBuilder()).setPrettyPrinting().create().toJson(o), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private float power() {
        this.load();
        return this.power > 0.0F ? this.power : 3.0F;
    }

    private float scaleOf() {
        this.load();
        if (this.scale.isEmpty()) {
            return 1.0F;
        } else {
            List<Float> s = new ArrayList(this.scale);
            s.sort((Comparator) null);
            int n = s.size();
            return n % 2 == 1 ? (Float) s.get(n / 2) : ((Float) s.get(n / 2 - 1) + (Float) s.get(n / 2)) / 2.0F;
        }
    }

    private Vec3 centre(Vec3 pos, float yaw) {
        this.load();
        double r = Math.toRadians((double) yaw);
        Vec3 ahead = new Vec3(-Math.sin(r), 0.0, Math.cos(r));
        Vec3 right = new Vec3(-Math.cos(r), 0.0, -Math.sin(r));
        return pos.add(ahead.scale(this.offset[0]))
                .add(0.0, this.offset[1], 0.0)
                .add(right.scale(this.offset[2]));
    }

    boolean is(ItemStack s) {
        if (s != null && !s.isEmpty()) {
            this.load();
            String own = HolyItems.id(s);
            if (own != null && this.ids.contains(own)) {
                return true;
            } else {
                String name = s.getHoverName()
                        .getString()
                        .replaceAll("[&§][0-9a-fk-orA-FK-OR]", "")
                        .toUpperCase(Locale.ROOT);
                boolean named = name.contains("ВЗРЫВНАЯ ШТУЧК") || name.contains("ВЗРЫНАЯ ШТУЧК");
                if (named && own != null && !this.ids.contains(own)) {
                    this.ids.add(own);
                    this.save();
                }

                return named;
            }
        } else {
            return false;
        }
    }

    private boolean holding(Player p) {
        return p != null && !HolyRules.safeHere() && (this.is(p.getMainHandItem()) || this.is(p.getOffhandItem()));
    }

    public static void register() {
        UseItemCallback.EVENT.register((UseItemCallback) (player, level, hand) -> {
            Modules.HOLY_THING.used(player, hand);
            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((UseBlockCallback) (player, level, hand, hit) -> {
            Modules.HOLY_THING.used(player, hand);
            return InteractionResult.PASS;
        });
    }

    private void used(Player player, InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (this.enabled() && player == mc.player && this.is(player.getItemInHand(hand))) {
            if (!HolyRules.safeHere()) {
                long now = System.currentTimeMillis();
                if (this.pending == null || now - this.pending.at() >= 400L) {
                    this.pending = new Use(
                            player.position(),
                            player.getYRot(),
                            now,
                            player.getItemInHand(hand).copy());
                    this.pendingUse = HolyRules.Use.of(hand);
                    this.pendingOk = false;
                }
            }
        }
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            this.pending = null;
        } else {
            if (this.histN == this.hist.length) {
                System.arraycopy(this.hist, 1, this.hist, 0, --this.histN);
            }

            this.hist[this.histN++] =
                    mc.player.isAlive() ? mc.player.getHealth() + mc.player.getAbsorptionAmount() : 0.0F;
            if (this.pending != null && System.currentTimeMillis() - this.pending.at() > 10000L) {
                this.pending = null;
            }

            if (this.pending != null && !this.pendingOk && this.pendingUse != null) {
                if (this.pendingUse.taken()) {
                    this.pendingOk = true;
                } else if (this.pendingUse.late()) {
                    this.pending = null;
                }
            }

            if (this.shot != null && --this.shot.ticks()[0] <= 0) {
                Shot s = this.shot;
                this.shot = null;
                this.learn(s);
            }
        }
    }

    public static void exploded(Vec3 centre, float radius) {
        ExplosiveThing m = Modules.HOLY_THING;
        Minecraft mc = Minecraft.getInstance();
        if (m != null && m.enabled() && mc.player != null && m.pending != null) {
            Use u = m.pending;
            if (!(centre.distanceTo(u.pos()) > 10.0)) {
                m.pending = null;
                m.load();
                long after = System.currentTimeMillis() - u.at();
                boolean first = m.power <= 0.0F;
                if (radius > 0.0F) {
                    m.power = radius;
                }

                Vec3 d = centre.subtract(u.pos());
                double r = Math.toRadians((double) u.yaw());
                Vec3 ahead = new Vec3(-Math.sin(r), 0.0, Math.cos(r));
                Vec3 right = new Vec3(-Math.cos(r), 0.0, -Math.sin(r));
                double[] o = m.offset;
                o[3] = Math.min(o[3] + 1.0, 10.0);
                o[0] += (d.dot(ahead) - o[0]) / o[3];
                o[1] += (d.y - o[1]) / o[3];
                o[2] += (d.dot(right) - o[2]) / o[3];
                m.delay[1] = Math.min(m.delay[1] + 1.0, 10.0);
                double[] var10000 = m.delay;
                var10000[0] += ((double) after - m.delay[0]) / m.delay[1];
                m.save();
                Player me = mc.player;
                float vanilla = 0.0F;

                try {
                    vanilla = HolyItems.explosionDamage(me, centre, radius, (BlockPos) null, Vec3.ZERO);
                } catch (RuntimeException ignored) {
                }

                float before = -1.0F;

                for (int i = Math.max(0, m.histN - 6); i < m.histN; ++i) {
                    before = Math.max(before, m.hist[i]);
                }

                m.shot = new Shot(radius, me.position().distanceTo(centre), vanilla, before, new int[] {6});
            }
        }
    }

    private void learn(Shot s) {
        Player me = Minecraft.getInstance().player;
        if (me != null) {
            boolean died = !me.isAlive() || me.getHealth() <= 0.0F;
            float after = died ? 0.0F : me.getHealth() + me.getAbsorptionAmount();
            float took = s.before() - after;
            if (!died && s.vanilla() >= 0.9F && took >= 0.25F) {
                this.scale.add(Math.max(0.05F, Math.min(4.0F, took / s.vanilla())));

                while (this.scale.size() > 8) {
                    this.scale.remove(0);
                }

                this.save();
            }

            try {
                Files.createDirectories(LOG.getParent());
                if (!Files.exists(LOG, new LinkOption[0])) {
                    Files.writeString(
                            LOG,
                            "time,power,distance,armor,hp_before,hp_after,took,died,vanilla,scale\n",
                            StandardCharsets.UTF_8);
                }

                Files.writeString(
                        LOG,
                        String.format(
                                Locale.ROOT,
                                "%s,%.1f,%.2f,%d,%.1f,%.1f,%.1f,%b,%.1f,%.3f%n",
                                LocalDateTime.now().withNano(0),
                                s.power(),
                                s.dist(),
                                me.getArmorValue(),
                                s.before(),
                                after,
                                took,
                                died,
                                s.vanilla(),
                                this.scaleOf()),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.APPEND);
            } catch (Exception ignored) {
            }
        }
    }

    float selfDamage() {
        return this.hearts.on() ? this.selfDamage : 0.0F;
    }

    private float damageTo(Entity e, Vec3 c) {
        if (e instanceof Player p) {
            try {
                return HolyItems.explosionDamage(p, c, this.power(), (BlockPos) null, Vec3.ZERO) * this.scaleOf();
            } catch (RuntimeException var5) {
                return 0.0F;
            }
        } else {
            return 0.0F;
        }
    }

    void render(WorldRenderContext ctx, Vec3 cam, float pt) {
        Minecraft mc = Minecraft.getInstance();
        Player me = mc.player;
        long nanos = System.nanoTime();
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1F, (float) (nanos - this.lastNanos) / 1.0E9F);
        this.lastNanos = nanos;
        boolean hold = this.holding(me);
        if (hold && this.show < 0.02F) {
            this.heldSince = now;
        }

        this.show += ((float) (hold ? 1 : 0) - this.show)
                * (1.0F - (float) Math.exp((double) (-dt * (float) (hold ? 12 : 9))));
        Vec3 heldAt = this.centre(me.getPosition(pt), me.getViewYRot(pt));
        Vec3 usedAt =
                this.pending != null && this.pendingOk ? this.centre(this.pending.pos(), this.pending.yaw()) : null;
        float wait = (float) Math.max(300.0, this.delay[0]);
        float burnt = this.pending != null && this.pendingOk
                ? TrapZone.clamp((float) (now - this.pending.at()) / wait)
                : 0.0F;
        float worst = 0.0F;
        if (this.show > 0.3F) {
            worst = this.damageTo(me, heldAt);
        }

        if (usedAt != null) {
            worst = Math.max(worst, this.damageTo(me, usedAt));
        }

        this.hurt += (worst - this.hurt) * (1.0F - (float) Math.exp((double) (-dt * 14.0F)));
        this.selfDamage = this.hurt < 0.1F ? 0.0F : this.hurt;
        Map<Integer, float[]> next = new HashMap<>();
        float k = 1.0F - (float) Math.exp((double) (-dt * 10.0F));
        if (this.show > 0.3F || usedAt != null) {
            for (AbstractClientPlayer p : mc.level.players()) {
                if (p != me && !p.isSpectator() && !(p.distanceToSqr(me) > 4096.0)) {
                    float d = Math.max(
                            this.show > 0.3F ? this.damageTo(p, heldAt) : 0.0F,
                            usedAt != null ? this.damageTo(p, usedAt) : 0.0F);
                    float[] old = (float[]) this.marks.get(p.getId());
                    float c = old == null ? 0.0F : old[0];
                    c += ((float) (d > 0.5F ? 1 : 0) - c) * k;
                    if (c > 0.01F || d > 0.5F) {
                        next.put(p.getId(), new float[] {c, d});
                    }
                }
            }
        }

        this.marks.clear();
        this.marks.putAll(next);
        if (!(this.show < 0.01F) || usedAt != null) {
            double fov = Math.toRadians((double) Projection.fov());
            TrapZone.Frame f = new TrapZone.Frame(
                    ctx.matrices().last(),
                    cam,
                    (float) (2.0
                            * Math.tan(fov / 2.0)
                            / (double) Math.max(1, mc.getWindow().getHeight())),
                    pt,
                    (float) (nanos % 1000000000000L) / 1.0E9F);
            float health = me.getHealth() + me.getAbsorptionAmount();
            int zc = this.zone.argb() | 0xFF000000;
            double R = (double) (this.power() * 2.0F);

            for (boolean under : new boolean[] {true, false}) {
                VertexConsumer vc =
                        ctx.consumers().getBuffer(Fx.ribbon(under ? Fx.Tex.LINE_GLOW : Fx.Tex.LINE_AA, false));
                if (this.show > 0.01F && usedAt == null) {
                    Curtain.draw(
                            vc,
                            f,
                            heldAt,
                            R,
                            under,
                            this.show * 0.9F,
                            (float) (now - this.heldSince) / 1000.0F,
                            0.0F,
                            zc,
                            this.scan.on());
                }

                if (usedAt != null) {
                    Curtain.draw(vc, f, usedAt, R, under, 1.0F, 9.0F, burnt, zc, this.scan.on());
                }
            }
        }
    }

    public int tint(Entity e, int colour) {
        if (this.enabled() && this.players.on() && e != null) {
            float[] m = (float[]) this.marks.get(e.getId());
            if (m != null && !(m[0] < 0.01F)) {
                float time = (float) (System.nanoTime() % 1000000000000L) / 1.0E9F;
                float pulse = 0.5F + 0.5F * (float) Math.sin((double) time * Math.PI * 2.0 * 1.3);
                int red = Fx.lerp(-1, -54726, m[0] * (0.35F + 0.5F * pulse));
                int r = (colour >> 16 & 255) * (red >> 16 & 255) / 255;
                int g = (colour >> 8 & 255) * (red >> 8 & 255) / 255;
                int b = (colour & 255) * (red & 255) / 255;
                return colour & 0xFF000000 | r << 16 | g << 8 | b;
            } else {
                return colour;
            }
        } else {
            return colour;
        }
    }

    void hud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && mc.player != null) {
            if (this.pending != null && this.pendingOk) {
                long now = System.currentTimeMillis();
                float wait = (float) Math.max(300.0, this.delay[0]);
                Vec3 at = this.centre(this.pending.pos(), this.pending.yaw()).add(0.0, 0.9, 0.0);
                Curtain.badge(
                        g,
                        at,
                        this.pending.icon(),
                        1.0F - TrapZone.clamp((float) (now - this.pending.at()) / wait),
                        true,
                        this.selfDamage,
                        mc.player.getHealth() + mc.player.getAbsorptionAmount(),
                        this.zone.argb() | 0xFF000000,
                        1.0F,
                        true);
            }

            if (!(this.show < 0.05F)) {
                this.load();
                if (!(this.power > 0.0F)) {
                    int fs = Math.max(10, Math.round((float) Px.height() / 1080.0F * 12.0F));
                    String t = "Взрывная штучка: зона примерная — взорви одну, и мод узнает её силу";
                    float k = Px.fade;
                    Px.fade = k * this.show;
                    Px.textCenter(g, t, Px.width() / 2, Px.height() / 2 + fs * 3, Px.Weight.MEDIUM, fs, -2565928);
                    Px.fade = k;
                }
            }
        }
    }

    private static record Use(Vec3 pos, float yaw, long at, ItemStack icon) {}

    private static record Shot(float power, double dist, float vanilla, float before, int[] ticks) {}
}
