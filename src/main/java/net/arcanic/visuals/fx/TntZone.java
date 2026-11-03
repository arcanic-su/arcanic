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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.helper.HolyItems;
import net.arcanic.visuals.helper.HolyRules;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public final class TntZone extends Module {
    private final ItemZones owner;
    private static final int RED = -50354;
    private static final long BURN = 10000L;
    private final List<Charge> charges = new ArrayList<>();
    private HolyItems.Tnt held;
    private BlockPos aim;
    private float show;
    private float hurt;
    private long lastNanos = System.nanoTime();
    private float selfDamage;
    private final float[] hist = new float[40];
    private int histN;
    private Shot shot;
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("arcanicvisuals/holyworld-tnt.json");
    private static final Path LOG =
            FabricLoader.getInstance().getConfigDir().resolve("arcanicvisuals/holyworld-tnt-log.csv");
    private final Map<String, Float> blastPower = new HashMap<>();
    private final Map<String, double[]> shift = new HashMap<>();
    private final Map<String, List<Float>> scale = new HashMap<>();
    private boolean loaded;
    private static final ItemStack TNT_ICON;
    private static final Identifier DARK;
    private static final Identifier DARK_HALF;

    public TntZone(ItemZones owner) {
        super("holy_tnt", "Динамит HolyWorld", "", Category.HOLYWORLD, true);
        this.owner = owner;
    }

    public boolean enabled() {
        return this.owner.on("tnt", this) || this.owner.on("plain_tnt", this);
    }

    private boolean plainOn() {
        return this.owner.on("plain_tnt", this);
    }

    private ItemZones.Item cfg(Charge c) {
        return this.owner.item(c.tnt == HolyItems.PLAIN ? "plain_tnt" : "tnt");
    }

    private ItemZones.Item dyn() {
        return this.owner.item("tnt");
    }

    private boolean holyOn() {
        return this.owner.on("tnt", this);
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
                if (o.has("blast_power")) {
                    for (Map.Entry<String, JsonElement> e :
                            o.getAsJsonObject("blast_power").entrySet()) {
                        this.blastPower.put((String) e.getKey(), ((JsonElement) e.getValue()).getAsFloat());
                    }
                }

                if (o.has("damage_scale")) {
                    for (Map.Entry<String, JsonElement> e :
                            o.getAsJsonObject("damage_scale").entrySet()) {
                        List<Float> l = new ArrayList<>();

                        for (JsonElement v : ((JsonElement) e.getValue()).getAsJsonArray()) {
                            l.add(v.getAsFloat());
                        }

                        this.scale.put((String) e.getKey(), l);
                    }
                }

                if (o.has("centre_shift")) {
                    for (Map.Entry<String, JsonElement> e :
                            o.getAsJsonObject("centre_shift").entrySet()) {
                        JsonArray a = ((JsonElement) e.getValue()).getAsJsonArray();
                        this.shift.put((String) e.getKey(), new double[] {
                            a.get(0).getAsDouble(),
                            a.get(1).getAsDouble(),
                            a.get(2).getAsDouble(),
                            a.get(3).getAsDouble()
                        });
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void save() {
        JsonObject o = new JsonObject();
        JsonObject b = new JsonObject();
        this.blastPower.forEach(b::addProperty);
        o.add("blast_power", b);
        JsonObject sh = new JsonObject();
        this.shift.forEach((k, v) -> {
            JsonArray a = new JsonArray();

            for (double d : v) {
                a.add(d);
            }

            sh.add(k, a);
        });
        o.add("centre_shift", sh);
        JsonObject sc = new JsonObject();
        this.scale.forEach((k, v) -> {
            JsonArray a = new JsonArray();
            v.forEach(a::add);
            sc.add(k, a);
        });
        o.add("damage_scale", sc);

        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(
                    FILE, (new GsonBuilder()).setPrettyPrinting().create().toJson(o), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private Vec3 shiftOf(HolyItems.Tnt t) {
        this.load();
        double[] v = (double[]) this.shift.get(t.id());
        return v == null ? Vec3.ZERO : new Vec3(v[0], v[1], v[2]);
    }

    private float power(HolyItems.Tnt t) {
        this.load();
        return (Float) this.blastPower.getOrDefault(t.id(), t.power());
    }

    public static void register() {
        UseBlockCallback.EVENT.register((UseBlockCallback) (player, level, hand, hit) -> {
            Modules.HOLY_TNT.placed(player, hand, hit);
            return InteractionResult.PASS;
        });
        ClientPlayerBlockBreakEvents.AFTER.register(
                (ClientPlayerBlockBreakEvents.After) (level, player, pos, state) -> Modules.HOLY_TNT.broken(pos));
    }

    private void broken(BlockPos pos) {
        this.charges.removeIf((c) -> c.pos.equals(pos) && c.entity == -1 && c.lastPos == null);
    }

    public static void destroyed(BlockPos pos) {
        TntZone m = Modules.HOLY_TNT;
        if (m != null) {
            m.broken(pos);
        }
    }

    private boolean shown(Charge c) {
        return c.tnt == HolyItems.PLAIN ? this.plainOn() : this.holyOn();
    }

    private static boolean real(Charge c) {
        return c.block || c.entity != -1 || c.lit > 0L || c.tnt == HolyItems.PLAIN;
    }

    private void placed(Player player, InteractionHand hand, BlockHitResult hit) {
        Minecraft mc = Minecraft.getInstance();
        if (this.enabled() && player == mc.player && mc.level != null) {
            if (!HolyRules.safeHere()) {
                HolyItems.Tnt t = HolyItems.tnt(player.getItemInHand(hand));
                if (t != null && this.holyOn()) {
                    BlockPos pos = target(hit);
                    this.charges.removeIf((c) -> c.pos.equals(pos) && c.entity == -1 && c.lit == 0L);
                    this.charges.add(new Charge(t, pos, System.currentTimeMillis()));
                }
            }
        }
    }

    private static BlockPos target(BlockHitResult hit) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos p = hit.getBlockPos();
        return mc.level.getBlockState(p).canBeReplaced() ? p : p.relative(hit.getDirection());
    }

    private static Vec3 origin(BlockPos p) {
        return new Vec3((double) p.getX() + 0.5, (double) p.getY() + 0.06125, (double) p.getZ() + 0.5);
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            if (this.histN == this.hist.length) {
                System.arraycopy(this.hist, 1, this.hist, 0, --this.histN);
            }

            this.hist[this.histN++] =
                    mc.player.isAlive() ? mc.player.getHealth() + mc.player.getAbsorptionAmount() : 0.0F;
            long now = System.currentTimeMillis();
            Iterator<Charge> it = this.charges.iterator();

            while (it.hasNext()) {
                Charge c = (Charge) it.next();
                if (c.gone > 0L) {
                    if (now - c.gone > 500L) {
                        it.remove();
                    }
                } else {
                    if (c.entity >= 0) {
                        Entity e = mc.level.getEntity(c.entity);
                        if (e != null && !e.isRemoved()) {
                            c.lastPos = e.position().add(0.0, 0.06125, 0.0);
                        }

                        if (e == null || e.isRemoved()) {
                            c.entity = -2;
                            c.lit = now;
                        }
                    } else if (c.entity == -1 && c.lit == 0L) {
                        if (mc.level.getBlockState(c.pos).is(Blocks.TNT)) {
                            c.block = true;
                            c.vanished = 0L;
                            continue;
                        }

                        if (c.block
                                && !mc.level
                                        .getEntitiesOfClass(ItemEntity.class, (new AABB(c.pos)).inflate(1.2))
                                        .isEmpty()) {
                            it.remove();
                            continue;
                        }

                        PrimedTnt lit = null;

                        for (PrimedTnt p :
                                mc.level.getEntitiesOfClass(PrimedTnt.class, (new AABB(c.pos)).inflate(1.5))) {
                            if (lit == null || p.distanceToSqr(origin(c.pos)) < lit.distanceToSqr(origin(c.pos))) {
                                lit = p;
                            }
                        }

                        if (lit != null) {
                            c.entity = lit.getId();
                            c.fuseMax = Math.max(1, lit.getFuse());
                        } else if (c.block) {
                            if (c.vanished == 0L) {
                                c.vanished = now;
                            } else if (now - c.vanished > 600L) {
                                c.lit = c.vanished;
                            }
                        } else if (now - c.at > 1500L) {
                            it.remove();
                        }
                        continue;
                    }

                    if (c.lit > 0L && c.entity < 0 && now - c.lit > 10000L) {
                        c.gone = now;
                    }
                }
            }

            if (this.plainOn()) {
                Set<Integer> known = new HashSet<>();

                for (Charge c : this.charges) {
                    known.add(c.entity);
                }

                for (PrimedTnt p : mc.level.getEntitiesOfClass(
                        PrimedTnt.class, mc.player.getBoundingBox().inflate(64.0))) {
                    if (!known.contains(p.getId())) {
                        Charge c = new Charge(HolyItems.PLAIN, p.blockPosition(), now);
                        c.entity = p.getId();
                        c.fuseMax = Math.max(80, p.getFuse());
                        this.charges.add(c);
                    }
                }
            }

            if (this.shot != null && --this.shot.ticks()[0] <= 0) {
                Shot s = this.shot;
                this.shot = null;
                this.log(s);
            }

        } else {
            this.charges.clear();
        }
    }

    public static void exploded(Vec3 centre, float radius, int blocks) {
        TntZone m = Modules.HOLY_TNT;
        Minecraft mc = Minecraft.getInstance();
        if (m != null && m.enabled() && mc.player != null) {
            Charge best = null;
            double bestD = 6.25;

            for (Charge c : m.charges) {
                if (c.gone <= 0L) {
                    double d = m.where(c, 1.0F).distanceToSqr(centre);
                    if (d < bestD) {
                        bestD = d;
                        best = c;
                    }
                }
            }

            if (best != null) {
                best.gone = System.currentTimeMillis();
                if (best.tnt != HolyItems.PLAIN) {
                    m.load();
                    Vec3 delta = centre.subtract(m.raw(best, 1.0F));
                    if (delta.length() < 1.5) {
                        double[] v = (double[]) m.shift.computeIfAbsent(best.tnt.id(), (k) -> new double[4]);
                        v[3] = Math.min(v[3] + 1.0, 10.0);
                        v[0] += (delta.x - v[0]) / v[3];
                        v[1] += (delta.y - v[1]) / v[3];
                        v[2] += (delta.z - v[2]) / v[3];
                        m.save();
                    }

                    if (radius > 0.0F && !Float.valueOf(radius).equals(m.blastPower.get(best.tnt.id()))) {
                        m.blastPower.put(best.tnt.id(), radius);
                        m.save();
                    }

                    Player me = mc.player;
                    double dist = me.position().distanceTo(centre);
                    if (!(dist > (double) (m.power(best.tnt) * 2.0F + 2.0F))) {
                        float before = -1.0F;

                        for (int i = Math.max(0, m.histN - 6); i < m.histN; ++i) {
                            before = Math.max(before, m.hist[i]);
                        }

                        float seen = 0.0F;

                        try {
                            seen = HolyItems.seenFrom(me, centre, (BlockPos) null);
                        } catch (RuntimeException ignored) {
                        }

                        m.shot = new Shot(
                                best.tnt,
                                radius,
                                dist,
                                seen,
                                best.predicted,
                                before,
                                delta,
                                best.shownSeen,
                                best.shownOpen,
                                best.predictedHere,
                                ping(me),
                                me.getDeltaMovement().horizontalDistance() * 20.0,
                                new int[] {6});
                    }
                }
            }
        }
    }

    private void log(Shot s) {
        Player me = Minecraft.getInstance().player;
        if (me != null) {
            boolean died = !me.isAlive() || me.getHealth() <= 0.0F;
            float after = died ? 0.0F : me.getHealth() + me.getAbsorptionAmount();
            float took = s.before() - after;
            if (!died && s.speed() < 1.0 && s.predictedHere() >= 0.9F && took >= 0.25F) {
                List<Float> l = (List) this.scale.computeIfAbsent(s.tnt().id(), (k) -> new ArrayList<>());
                l.add(Math.max(0.05F, Math.min(4.0F, took / s.predictedHere())));

                while (l.size() > 8) {
                    l.remove(0);
                }

                this.save();
            }

            try {
                Files.createDirectories(LOG.getParent());
                if (!Files.exists(LOG, new LinkOption[0])) {
                    Files.writeString(
                            LOG,
                            "time,kind,power,distance,seen,armor,hp_before,hp_after,took,died,shown,shown_seen,seen_without_block,dx,dy,dz,ping,speed,vanilla_here,scale\n",
                            StandardCharsets.UTF_8);
                }

                Files.writeString(
                        LOG,
                        String.format(
                                Locale.ROOT,
                                "%s,%s,%.1f,%.2f,%.3f,%d,%.1f,%.1f,%.1f,%b,%.1f,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%.1f,%.1f,%.3f%n",
                                LocalDateTime.now().withNano(0),
                                s.tnt().id(),
                                s.power(),
                                s.dist(),
                                s.seen(),
                                me.getArmorValue(),
                                s.before(),
                                after,
                                s.before() - after,
                                died,
                                s.shown(),
                                s.shownSeen(),
                                s.shownOpen(),
                                s.delta().x,
                                s.delta().y,
                                s.delta().z,
                                s.ping(),
                                s.speed(),
                                s.predictedHere(),
                                this.scaleOf(s.tnt())),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.APPEND);
            } catch (Exception ignored) {
            }
        }
    }

    void render(WorldRenderContext ctx, Vec3 cam, float pt) {
        Minecraft mc = Minecraft.getInstance();
        Player me = mc.player;
        long nanos = System.nanoTime();
        float dt = Math.min(0.1F, (float) (nanos - this.lastNanos) / 1.0E9F);
        this.lastNanos = nanos;
        HolyItems.Tnt t = this.holyOn() ? HolyItems.heldTnt(me) : null;
        BlockPos at = null;
        if (t != null) {
            HitResult var12 = mc.hitResult;
            if (var12 instanceof BlockHitResult) {
                BlockHitResult bh = (BlockHitResult) var12;
                if (bh.getType() == Type.BLOCK) {
                    at = target(bh);
                }
            }
        }

        boolean holding = at != null;
        if (holding) {
            this.held = t;
            this.aim = at;
        }

        this.show += ((float) (holding ? 1 : 0) - this.show)
                * (1.0F - (float) Math.exp((double) (-dt * (float) (holding ? 12 : 9))));
        if (this.show < 0.01F) {
            this.aim = null;
        }

        float worst = 0.0F;
        boolean safe = HolyRules.safeHere();
        if (this.show > 0.3F
                && this.held != null
                && this.aim != null
                && !safe
                && this.dyn().hearts.on()) {
            worst = this.damage(me, origin(this.aim).add(this.shiftOf(this.held)), this.held, this.aim);
        }

        for (Charge c : this.charges) {
            if (c.gone <= 0L && real(c) && this.shown(c)) {
                BlockPos solid = c.entity < 0 && c.lastPos == null && c.tnt != HolyItems.PLAIN ? c.pos : null;
                Vec3 w = this.where(c, pt);
                c.predicted = this.damage(me, w, c.tnt, solid);

                try {
                    c.predictedHere = me.position().distanceTo(w) <= (double) (this.power(c.tnt) * 2.0F)
                            ? hit(me, w, this.power(c.tnt), solid, Vec3.ZERO)
                            : 0.0F;
                } catch (RuntimeException ignored) {
                }

                try {
                    if (me.position().distanceTo(w) <= (double) (this.power(c.tnt) * 2.0F)) {
                        c.shownSeen = solid != null
                                ? HolyItems.seenBehind(me, w, solid)
                                : HolyItems.seenFrom(me, w, (BlockPos) null);
                        c.shownOpen = HolyItems.seenFrom(me, w, c.pos);
                    } else {
                        c.shownSeen = c.shownOpen = 0.0F;
                    }
                } catch (RuntimeException ignored) {
                }

                if (this.cfg(c).hearts.on()) {
                    worst = Math.max(worst, c.predicted);
                }
            }
        }

        this.hurt += (worst - this.hurt) * (1.0F - (float) Math.exp((double) (-dt * 14.0F)));
        this.selfDamage = this.hurt < 0.1F ? 0.0F : this.hurt;
    }

    private float burnt(Charge c, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (c.entity >= 0) {
            Entity var5 = mc.level.getEntity(c.entity);
            if (var5 instanceof PrimedTnt) {
                PrimedTnt p = (PrimedTnt) var5;
                return 1.0F - TrapZone.clamp(((float) p.getFuse() - pt) / (float) c.fuseMax);
            }
        }

        return c.lit > 0L ? TrapZone.clamp((float) (System.currentTimeMillis() - c.lit) / 4000.0F) : 0.0F;
    }

    private Vec3 where(Charge c, float pt) {
        return this.raw(c, pt).add(this.shiftOf(c.tnt));
    }

    private Vec3 raw(Charge c, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (c.entity >= 0) {
            Entity var5 = mc.level.getEntity(c.entity);
            if (var5 instanceof PrimedTnt) {
                PrimedTnt p = (PrimedTnt) var5;
                return p.getPosition(pt).add(0.0, 0.06125, 0.0);
            }
        }

        return c.lastPos != null ? c.lastPos : origin(c.pos);
    }

    private float scaleOf(HolyItems.Tnt t) {
        this.load();
        List<Float> l = (List) this.scale.get(t.id());
        if (l != null && !l.isEmpty()) {
            List<Float> s = new ArrayList(l);
            s.sort((Comparator) null);
            int n = s.size();
            return n % 2 == 1 ? (Float) s.get(n / 2) : ((Float) s.get(n / 2 - 1) + (Float) s.get(n / 2)) / 2.0F;
        } else {
            return 1.0F;
        }
    }

    private float damage(Player me, Vec3 at, HolyItems.Tnt t, BlockPos solid) {
        float pw = this.power(t);
        if (me.position().distanceTo(at) > (double) (pw * 2.0F + 2.0F)) {
            return 0.0F;
        } else {
            try {
                Vec3 back = lag(me);
                float here = hit(me, at, pw, solid, Vec3.ZERO);
                float worst = back.lengthSqr() < 0.01 ? here : Math.max(here, hit(me, at, pw, solid, back));
                return worst * this.scaleOf(t);
            } catch (RuntimeException var9) {
                return 0.0F;
            }
        }
    }

    private static float hit(Player me, Vec3 at, float pw, BlockPos solid, Vec3 shift) {
        return solid != null
                ? HolyItems.explosionDamageBehind(me, at, pw, solid, shift)
                : HolyItems.explosionDamage(me, at, pw, (BlockPos) null, shift);
    }

    private static int ping(Player me) {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        PlayerInfo info = conn == null ? null : conn.getPlayerInfo(me.getUUID());
        return info == null ? 0 : Math.max(0, info.getLatency());
    }

    private static Vec3 lag(Player me) {
        double ticks = Math.min(8.0, (double) ping(me) / 2.0 / 50.0 + 1.0);
        Vec3 v = me.getDeltaMovement();
        if (v.horizontalDistance() < 0.05) {
            return Vec3.ZERO;
        } else {
            Vec3 back = (new Vec3(v.x, 0.0, v.z)).scale(-ticks);
            double len = back.length();
            return len > 3.0 ? back.scale(3.0 / len) : back;
        }
    }

    void hud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && mc.level != null && mc.player != null) {
            float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            long now = System.currentTimeMillis();

            for (Charge c : this.charges) {
                if (this.shown(c) && this.cfg(c).timer.on()) {
                    int zc = this.cfg(c).zone.argb() | 0xFF000000;
                    if (real(c)) {
                        float a = TrapZone.clamp((float) (now - c.at) / 250.0F)
                                * (c.gone > 0L ? 1.0F - TrapZone.clamp((float) (now - c.gone) / 300.0F) : 1.0F);
                        boolean lit = c.entity >= 0 || c.lit > 0L;
                        Curtain.badge(
                                g,
                                this.where(c, pt).add(0.0, 0.9, 0.0),
                                TNT_ICON,
                                1.0F - this.burnt(c, pt),
                                lit,
                                c.predicted,
                                health,
                                zc,
                                a,
                                false);
                    }
                }
            }
        }
    }

    public static void hearts(
            GuiGraphics g, int x, int y, int lineH, int regen, float maxHealth, int health, int absorb) {
        TntZone m = Modules.HOLY_TNT;
        if (m != null) {
            float dmg = m.enabled() ? m.selfDamage : 0.0F;
            ExplosiveThing thing = Modules.HOLY_THING;
            if (thing != null && thing.enabled()) {
                dmg = Math.max(dmg, thing.selfDamage());
            }

            if (!(dmg <= 0.0F)) {
                boolean kills = dmg >= (float) (health + absorb);
                float time = (float) (System.nanoTime() % 1000000000000L) / 1.0E9F;
                float beat =
                        0.5F - 0.5F * (float) Math.cos((double) time * Math.PI * 2.0 * (double) (kills ? 2.6F : 1.6F));
                int hearts = (int) Math.ceil((double) maxHealth / 2.0);
                int extra = (int) Math.ceil((double) absorb / 2.0);
                float fromAbsorb = Math.min(dmg, (float) absorb);
                float fromHealth = dmg - fromAbsorb;
                float absorbLeft = (float) absorb - fromAbsorb;
                float healthLeft = (float) health - fromHealth;

                for (int l = hearts + extra - 1; l >= 0; --l) {
                    float lo;
                    float hi;
                    float keep;
                    if (l >= hearts) {
                        lo = (float) ((l - hearts) * 2);
                        hi = Math.min(lo + 2.0F, (float) absorb);
                        keep = absorbLeft;
                    } else {
                        lo = (float) (l * 2);
                        hi = Math.min(lo + 2.0F, (float) health);
                        keep = healthLeft;
                    }

                    if (!(hi <= lo) && !(keep >= hi)) {
                        int hx = x + l % 10 * 8;
                        int hy = y - l / 10 * lineH;
                        if (l < hearts && l == regen) {
                            hy -= 2;
                        }

                        boolean whole = keep <= lo;
                        boolean half = hi - lo <= 1.0F;
                        float k = beat * (whole ? 0.9F : 0.5F);
                        g.blitSprite(
                                RenderPipelines.GUI_TEXTURED,
                                (Identifier) (half ? DARK_HALF : DARK),
                                hx,
                                hy,
                                9,
                                9,
                                TrapZone.alpha(-1, k));
                    }
                }
            }
        }
    }

    static {
        TNT_ICON = new ItemStack(Items.TNT);
        DARK = Identifier.withDefaultNamespace("hud/heart/withered_full");
        DARK_HALF = Identifier.withDefaultNamespace("hud/heart/withered_half");
    }

    private static final class Charge {
        final HolyItems.Tnt tnt;
        final BlockPos pos;
        final long at;
        boolean block;
        int entity = -1;
        int fuseMax = 80;
        long lit;
        long gone;
        long vanished;
        float predicted;
        float shownSeen;
        float shownOpen;
        float predictedHere;
        Vec3 lastPos;

        Charge(HolyItems.Tnt tnt, BlockPos pos, long at) {
            this.tnt = tnt;
            this.pos = pos;
            this.at = at;
        }
    }

    private static record Shot(
            HolyItems.Tnt tnt,
            float power,
            double dist,
            float seen,
            float shown,
            float before,
            Vec3 delta,
            float shownSeen,
            float shownOpen,
            float predictedHere,
            int ping,
            double speed,
            int[] ticks) {}
}
