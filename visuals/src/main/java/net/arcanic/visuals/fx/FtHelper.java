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

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.helper.FunTime;
import net.arcanic.visuals.render.Px;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * FunTime's paid items: where a trap, a plast, a disorientation, a dust or a freezing snowball will land, the
 * drill's dig, and how long a placed trap or plast still stands.
 */
public final class FtHelper extends Module {
    private static final double CARD_RANGE = 32.0;

    private static ItemZones.Item item(String key) {
        return Modules.FT_ITEMS.item(key);
    }

    private static boolean on(String key) {
        return item(key).on.on();
    }

    private static int zone(String key) {
        return item(key).zone.argb() | 0xFF000000;
    }

    private static int caught(String key) {
        return item(key).caught.argb() | 0xFF000000;
    }

    private static boolean timer(String key) {
        return on(key) && item(key).timer.on();
    }

    private static final long MATCH_MS = 1200L;
    private static final double MATCH_RANGE = 8.0;
    private static final Identifier DRAGON_GROWL = Identifier.withDefaultNamespace("entity.ender_dragon.growl");
    private final CooldownUse trapUse = new CooldownUse(Items.NETHERITE_SCRAP);
    private final CooldownUse plastUse = new CooldownUse(Items.DRIED_KELP);
    private final Structures structures = new Structures();
    private final List<Timer> timers = new CopyOnWriteArrayList<>();
    private long growlAt;
    private Object level;

    public FtHelper() {
        super(
                "ft_helper",
                "Радиус предметов FunTime",
                "FunTime: зоны трапки, пласта, дезориентации, пыли и снежка, бур и таймеры поставленных структур",
                Category.FUNTIME,
                true);
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** Drawn and ticked through the FunTime item list (Радиус предметов), not listed on its own. */
    public boolean enabled() {
        return true;
    }

    private boolean live() {
        return FunTime.here();
    }

    // ---------------------------------------------------------------- ticks and packets

    public void tick() {
        Minecraft mc = mc();
        if (mc.player == null || mc.level == null) return;
        if (this.level != mc.level) {
            this.level = mc.level;
            this.structures.clear();
            this.timers.clear();
        }
        if (!FunTime.here()) return;
        if (timer("trap")) this.trapUse.tick(this::trapTimer);
        if (timer("plast")) this.plastUse.tick(FtHelper::plastTimer);
        this.structures.tick(timer("trap"), timer("plast"));
        long now = System.currentTimeMillis();
        for (Timer t : this.timers) {
            if (t.at == null && now - t.start <= MATCH_MS) {
                Structures.Placement p = this.structures.take(t.type, t.from, MATCH_RANGE, now, MATCH_MS);
                if (p != null) {
                    t.at = cardAt(p.pos(), t);
                    t.shownAt = now;
                }
            }
        }
        this.timers.removeIf(Timer::over);
    }

    /** Sounds from the server's packet handler: structures are told apart by what they play when placed. */
    public void sound(ClientboundSoundPacket packet) {
        if (!this.live()) return;
        Identifier id = packet.getSound()
                .unwrapKey()
                .map(ResourceKey::identifier)
                .orElseGet(() -> packet.getSound().value().location());
        if (DRAGON_GROWL.equals(id)) this.growlAt = System.currentTimeMillis();
        if (timer("trap") || timer("plast")) this.structures.heard(packet, id);
    }

    private Timer trapTimer() {
        boolean dragon = System.currentTimeMillis() - this.growlAt < 500L;
        LocalPlayer p = mc().player;
        return new Timer(
                FunTime.Item.TRAP,
                dragon ? "Драконья трапка" : "Трапка",
                dragon ? 30000 : 15000,
                p.getYRot(),
                p.getXRot(),
                p.position());
    }

    private static Timer plastTimer() {
        LocalPlayer p = mc().player;
        boolean flat = Math.abs(p.getXRot()) > 45.0F;
        return new Timer(
                FunTime.Item.PLAST,
                flat ? "Горизонтальный пласт" : "Вертикальный пласт",
                flat ? 60000 : 20000,
                p.getYRot(),
                p.getXRot(),
                p.position());
    }

    private static Vec3 cardAt(BlockPos pos, Timer t) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.4;
        double z = pos.getZ() + 0.5;
        if (t.type == FunTime.Item.PLAST) {
            if (t.pitch < -45.0F) y += 4.0;
            else if (t.pitch > 45.0F) y -= 2.0;
            else {
                double yaw = Math.toRadians(t.yaw);
                x += -Math.sin(yaw) * 2.0;
                z += Math.cos(yaw) * 2.0;
            }
        }
        return new Vec3(x, y, z);
    }

    // ---------------------------------------------------------------- world

    /** A zone shown for an item in someone's hand: it eases in, follows smoothly and draws itself out. */
    private static final class Held {
        String kind;
        float show;
        long since;
        double[] box;
        double[] disc;
        Plast plast;
        List<Vec3> path;
    }

    private final Held mine = new Held();
    private final java.util.Map<Integer, Held> theirs = new java.util.HashMap<>();
    private long lastNanos = System.nanoTime();
    private static final double ENEMY_RANGE = 48.0;

    /** Which zone the item in {@code p}'s hands makes, or null. */
    private static String heldKind(Player p) {
        for (ItemStack s : new ItemStack[] {p.getMainHandItem(), p.getOffhandItem()}) {
            Item i = s.getItem();
            if (i == Items.NETHERITE_SCRAP && on("trap")) return "trap";
            if (i == Items.DRIED_KELP && on("plast")) return "plast";
            if (i == Items.ENDER_EYE && on("disorient")) return "disorient";
            if (i == Items.SUGAR && on("dust")) return "dust";
            if (i == Items.SNOWBALL && on("snowball")) return "snowball";
        }
        return null;
    }

    private static double[] bounds(AABB b) {
        return new double[] {b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ};
    }

    /** Moves {@code at} toward {@code want}; a jump of more than a few blocks snaps there instead. */
    private static double[] follow(double[] at, double[] want, float k) {
        if (at == null || at.length != want.length || Math.abs(at[0] - want[0]) + Math.abs(at[2] - want[2]) > 6.0) {
            return want.clone();
        }
        for (int i = 0; i < want.length; i++) at[i] += (want[i] - at[i]) * k;
        return at;
    }

    private void update(Held h, Player p, String kind, float pt, float dt, long now) {
        if (kind != null && (h.show < 0.02F || !kind.equals(h.kind))) {
            h.since = now;
            h.box = null;
            h.disc = null;
        }
        if (kind != null) h.kind = kind;
        h.show += ((kind != null ? 1.0F : 0.0F) - h.show)
                * (1.0F - (float) Math.exp(-dt * (kind != null ? 12.0F : 9.0F)));
        if (kind == null) return;
        float k = 1.0F - (float) Math.exp(-dt * 20.0F);
        Vec3 feet = p.getPosition(pt);
        switch (kind) {
            case "trap" ->
                h.box = follow(
                        h.box,
                        bounds(trapBox(p.blockPosition(), item("trap").dragon.on())),
                        k);
            case "plast" -> {
                h.plast = plastShape(p, item("plast").dragon.on());
                if (h.plast.box() != null) h.box = follow(h.box, bounds(h.plast.box()), k);
                else h.box = null;
            }
            case "disorient", "dust" -> h.disc = follow(h.disc, new double[] {feet.x, feet.y, feet.z, 10.0}, k);
            case "snowball" -> {
                List<Vec3> path = new ArrayList<>(202);
                Vec3 land = snowballPath(p, pt, path);
                h.path = path;
                if (land != null) h.disc = follow(h.disc, new double[] {land.x, land.y, land.z, 5.0}, k);
            }
            default -> {}
        }
    }

    void render(WorldRenderContext ctx, Vec3 cam, float pt) {
        Minecraft mc = mc();
        LocalPlayer me = mc.player;
        if (!this.live() || me == null || mc.level == null) return;
        long nanos = System.nanoTime();
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1F, (nanos - this.lastNanos) / 1.0E9F);
        this.lastNanos = nanos;
        this.update(this.mine, me, heldKind(me), pt, dt, now);
        java.util.Set<Integer> near = new java.util.HashSet<>();
        for (Player p : mc.level.players()) {
            if (!enemy(p) || p.distanceToSqr(me) > ENEMY_RANGE * ENEMY_RANGE) continue;
            Held h = this.theirs.computeIfAbsent(p.getId(), id -> new Held());
            this.update(h, p, heldKind(p), pt, dt, now);
            near.add(p.getId());
        }
        this.theirs.keySet().retainAll(near);
        double fov = Math.toRadians(Projection.fov());
        TrapZone.Frame f = new TrapZone.Frame(
                ctx.matrices().last(),
                cam,
                (float) (2.0 * Math.tan(fov / 2.0) / Math.max(1, mc.getWindow().getHeight())),
                pt,
                (float) (nanos % 1000000000000L) / 1.0E9F);
        Vec3 feet = me.getPosition(pt);
        for (boolean under : new boolean[] {true, false}) {
            VertexConsumer vc =
                    ctx.consumers().getBuffer(Fx.ribbon(under ? Fx.Tex.LINE_GLOW : Fx.Tex.LINE_EDGE, false));
            this.draw(vc, f, this.mine, feet, under, false, now, me);
            for (java.util.Map.Entry<Integer, Held> e : this.theirs.entrySet()) {
                if (mc.level.getEntity(e.getKey()) instanceof Player p) {
                    this.draw(vc, f, e.getValue(), p.getPosition(pt), under, true, now, me);
                }
            }
            for (Timer t : this.timers) this.placed(vc, f, t, under, now);
            if (on("drill") && !under) {
                AABB dig = drillBox(me);
                if (dig != null)
                    TrapZone.zone(vc, f, bounds(dig), null, false, 0.9F, 9.0F, false, zone("drill"), false);
            }
            if (on("drill") && under) {
                AABB dig = drillBox(me);
                if (dig != null) TrapZone.zone(vc, f, bounds(dig), null, true, 0.9F, 9.0F, false, zone("drill"), true);
            }
        }
    }

    /** One held zone: your own in the item's colour (the "caught" colour once an enemy is inside), an enemy's always
     * in the "caught" colour and brighter when you stand in it. */
    private void draw(
            VertexConsumer vc, TrapZone.Frame f, Held h, Vec3 feet, boolean under, boolean enemy, long now, Player me) {
        if (h.show < 0.01F || h.kind == null) return;
        float appear = (now - h.since) / 700.0F;
        float a = h.show * (enemy ? 0.75F : 1.0F);
        String key = h.kind;
        switch (key) {
            case "trap", "plast" -> {
                if (h.box != null) {
                    AABB box = new AABB(h.box[0], h.box[1], h.box[2], h.box[3], h.box[4], h.box[5]);
                    boolean hit = enemy ? box.contains(me.position()) : enemyIn(box);
                    int c = enemy || hit ? caught(key) : zone(key);
                    TrapZone.zone(
                            vc, f, h.box, enemy ? null : feet, under, hit && enemy ? 1.0F : a, appear, !enemy, c, true);
                } else if (h.plast != null && h.plast.blocks() != null) {
                    boolean hit = enemy ? h.plast.blocks().contains(me.blockPosition()) : enemyOn(h.plast.blocks());
                    int c = enemy || hit ? caught(key) : zone(key);
                    for (BlockPos b : h.plast.blocks()) {
                        TrapZone.zone(vc, f, bounds(new AABB(b)), null, under, a * 0.6F, appear, false, c, false);
                    }
                }
            }
            case "disorient", "dust", "snowball" -> {
                if ("snowball".equals(key) && h.path != null && !under) {
                    int pc = enemy ? caught(key) : zone(key);
                    for (int i = 0; i + 1 < h.path.size(); i++) {
                        float a0 = a * i / h.path.size();
                        float a1 = a * (i + 1) / h.path.size();
                        TrapZone.line(vc, f, h.path.get(i), h.path.get(i + 1), 3.0F, alpha(pc, a0), alpha(pc, a1));
                    }
                }
                if (h.disc != null) {
                    Vec3 o = new Vec3(h.disc[0], h.disc[1] + 0.03, h.disc[2]);
                    double r = h.disc[3] * TrapZone.expo(appear / 0.6F);
                    boolean hit =
                            enemy ? me.position().distanceToSqr(o) <= h.disc[3] * h.disc[3] : enemyNear(o, h.disc[3]);
                    int c = enemy || hit ? caught(key) : zone(key);
                    float breathe = 0.78F + 0.22F * (float) Math.sin(f.time() * Math.PI * 2.0 / 1.8);
                    if (under) {
                        TrapZone.ring(vc, f, o, r, 18.0F, TrapZone.alpha(c, 0.4F * a * breathe));
                    } else {
                        TrapZone.ring(vc, f, o, r, 5.0F, TrapZone.alpha(Fx.lerp(c, -1, 0.35F), 0.95F * a));
                        TrapZone.ring(vc, f, o, r * 0.97, 2.0F, TrapZone.alpha(c, 0.45F * a));
                    }
                }
            }
            default -> {}
        }
    }

    /** A placed trap or plast stays drawn where it stands until its time runs out, fading with it. */
    private void placed(VertexConsumer vc, TrapZone.Frame f, Timer t, boolean under, long now) {
        AABB box = t.box();
        if (box == null) return;
        float life = TrapZone.clamp((now - t.start) / 250.0F) * (1.0F - TrapZone.clamp((now - t.end()) / 500.0F));
        float left = TrapZone.clamp((float) t.left(now) / t.duration);
        String key = t.type == FunTime.Item.PLAST ? "plast" : "trap";
        TrapZone.zone(vc, f, bounds(box), null, under, life * (0.45F + 0.55F * left), 9.0F, false, zone(key), true);
    }

    static int alpha(int c, float a) {
        return TrapZone.alpha(c, a * ((c >>> 24) / 255.0F));
    }

    /** A soft glowing outline with a faint fill on the floor and a fading wall. */
    static void cuboid(VertexConsumer glow, VertexConsumer edge, TrapZone.Frame f, AABB b, int c) {
        double x0 = b.minX, y0 = b.minY + 0.02, z0 = b.minZ, x1 = b.maxX, y1 = b.maxY, z1 = b.maxZ;
        float breathe = 0.78F + 0.22F * (float) Math.sin(f.time() * Math.PI * 2.0 / 1.8);
        int wallLow = TrapZone.alpha(c, 0.16F * breathe);
        int wallHigh = TrapZone.alpha(c, 0.0F);
        TrapZone.face(glow, f, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, wallLow, wallLow, wallHigh, wallHigh);
        TrapZone.face(glow, f, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, wallLow, wallLow, wallHigh, wallHigh);
        TrapZone.face(glow, f, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, wallLow, wallLow, wallHigh, wallHigh);
        TrapZone.face(glow, f, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, wallLow, wallLow, wallHigh, wallHigh);
        int floor = TrapZone.alpha(c, 0.1F);
        TrapZone.face(glow, f, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, floor, floor, floor, floor);
        int bright = TrapZone.alpha(Fx.lerp(c, -1, 0.35F), 0.95F);
        int dim = TrapZone.alpha(c, 0.5F);
        double[][] corners = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
        for (int i = 0; i < 4; i++) {
            double[] p = corners[i];
            double[] q = corners[(i + 1) % 4];
            TrapZone.line(edge, f, new Vec3(p[0], y0, p[1]), new Vec3(q[0], y0, q[1]), 5.0F, bright, bright);
            TrapZone.line(edge, f, new Vec3(p[0], y1, p[1]), new Vec3(q[0], y1, q[1]), 5.0F, dim, dim);
            TrapZone.line(edge, f, new Vec3(p[0], y0, p[1]), new Vec3(p[0], y1, p[1]), 5.0F, bright, dim);
        }
    }

    private static void disc(VertexConsumer glow, VertexConsumer edge, TrapZone.Frame f, Vec3 at, double r, int c) {
        Vec3 o = new Vec3(at.x, at.y + 0.03, at.z);
        TrapZone.ring(edge, f, o, r, 5.0F, TrapZone.alpha(Fx.lerp(c, -1, 0.3F), 0.9F));
        TrapZone.ring(glow, f, o, r, 16.0F, TrapZone.alpha(c, 0.35F));
    }

    // ---------------------------------------------------------------- shapes

    static AABB trapBox(BlockPos p, boolean dragon) {
        int r = dragon ? 3 : 2;
        int h = dragon ? 6 : 4;
        return new AABB(p.getX() - r, p.getY(), p.getZ() - r, p.getX() + r + 1, p.getY() + h, p.getZ() + r + 1);
    }

    private record Plast(AABB box, List<BlockPos> blocks) {}

    /** Pulse's plast rules: above or below when looking up or down, a wall ahead otherwise, slanted on diagonals. */
    static Plast plastShape(Player me, boolean dragon) {
        return plastShape(me.blockPosition(), me.getYRot(), me.getXRot(), dragon);
    }

    static Plast plastShape(BlockPos p, float yaw, float pitch, boolean dragon) {
        int r = dragon ? 3 : 2;
        if (Math.abs(pitch) > 45.0F) {
            int lo = pitch < -45.0F ? 3 : -3;
            int hi = pitch < -45.0F ? (dragon ? 7 : 5) : -1;
            return new Plast(
                    new AABB(
                            p.getX() - r,
                            p.getY() + lo,
                            p.getZ() - r,
                            p.getX() + r + 1,
                            p.getY() + hi,
                            p.getZ() + r + 1),
                    null);
        }
        float y = (yaw % 360.0F + 360.0F) % 360.0F;
        int[] diagonals = {45, 135, 225, 315};
        int nearest = diagonals[0];
        for (int d : diagonals) if (Math.abs(y - d) < Math.abs(y - nearest)) nearest = d;
        if (Math.abs(y - nearest) < 22.0F) {
            int dx = nearest == 45 || nearest == 135 ? -3 : 2;
            int dz = nearest == 45 || nearest == 315 ? 2 : -2;
            BlockPos start = p.offset(dx, -1, dz);
            int height = dragon ? 7 : 5;
            List<BlockPos> blocks = new ArrayList<>();
            for (int i = -r; i <= r; i++) {
                for (int up = 0; up < height; up++) {
                    if (nearest == 45 || nearest == 225) {
                        blocks.add(start.offset(i, up, i));
                        blocks.add(start.offset(i + 1, up, i));
                    } else {
                        blocks.add(start.offset(i, up, -i));
                        blocks.add(start.offset(i + 1, up, -i));
                    }
                }
            }
            return new Plast(null, blocks);
        }
        BlockPos c;
        boolean alongX;
        if (y >= 315.0F || y < 45.0F) {
            c = p.offset(0, -1, 3);
            alongX = true;
        } else if (y < 135.0F) {
            c = p.offset(-3, -1, 0);
            alongX = false;
        } else if (y < 225.0F) {
            c = p.offset(0, -1, -3);
            alongX = true;
        } else {
            c = p.offset(3, -1, 0);
            alongX = false;
        }
        int h = dragon ? 7 : 5;
        AABB box = alongX
                ? new AABB(c.getX() - r, c.getY(), c.getZ() - 1, c.getX() + r + 1, c.getY() + h, c.getZ() + 1)
                : new AABB(c.getX() - 1, c.getY(), c.getZ() - r, c.getX() + 1, c.getY() + h, c.getZ() + r + 1);
        return new Plast(box, null);
    }

    /** The bulldozer pickaxe's dig around the block looked at, from its custom enchantment or lore. */
    private static AABB drillBox(LocalPlayer me) {
        ItemStack held = me.getMainHandItem();
        if (!isPickaxe(held.getItem())) return null;
        int[] size = digSize(held);
        if (size == null) return null;
        HitResult hit = mc().hitResult;
        if (!(hit instanceof BlockHitResult b) || hit.getType() != HitResult.Type.BLOCK) return null;
        int r = size[0];
        int depth = size[1];
        BlockPos at = b.getBlockPos();
        Direction side = b.getDirection();
        Direction.Axis axis = side.getAxis();
        BlockPos a = across(at, axis, -r, -r);
        BlockPos z = across(at.relative(side.getOpposite(), depth - 1), axis, r, r);
        return new AABB(
                Math.min(a.getX(), z.getX()),
                Math.min(a.getY(), z.getY()),
                Math.min(a.getZ(), z.getZ()),
                Math.max(a.getX(), z.getX()) + 1,
                Math.max(a.getY(), z.getY()) + 1,
                Math.max(a.getZ(), z.getZ()) + 1);
    }

    private static BlockPos across(BlockPos p, Direction.Axis axis, int u, int v) {
        return switch (axis) {
            case X -> p.offset(0, u, v);
            case Y -> p.offset(u, 0, v);
            default -> p.offset(u, v, 0);
        };
    }

    private static boolean isPickaxe(Item i) {
        return i == Items.WOODEN_PICKAXE
                || i == Items.STONE_PICKAXE
                || i == Items.IRON_PICKAXE
                || i == Items.GOLDEN_PICKAXE
                || i == Items.DIAMOND_PICKAXE
                || i == Items.NETHERITE_PICKAXE;
    }

    /** {radius across, depth}: the mega bulldozer digs 9x9x5, the bulldozer 3x3x1 or 3x3x3 at level II. */
    private static int[] digSize(ItemStack stack) {
        CompoundTag values = FunTime.bukkit(stack);
        if (values != null) {
            ListTag list = values.getListOrEmpty("minecraft:custom-enchantments");
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompoundOrEmpty(i);
                String type = e.getStringOr("minecraft:type", "").toLowerCase(Locale.ROOT);
                if (type.contains("mega")) return new int[] {4, 5};
                if (type.contains("bulldozer")) return new int[] {1, e.getIntOr("minecraft:level", 0) >= 2 ? 3 : 1};
            }
        }
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) {
                String s = line.getString();
                if (s.contains("9x9x5") || s.toLowerCase(Locale.ROOT).contains("мега-бульдозер"))
                    return new int[] {4, 5};
                if (s.contains("Бульдозер II")) return new int[] {1, 3};
                if (s.contains("Бульдозер I")) return new int[] {1, 1};
            }
        }
        return null;
    }

    /** Where a thrown snowball lands, stepping the way the game moves it. */
    private static Vec3 snowballPath(Player me, float pt, List<Vec3> path) {
        Vec3 pos = me.getEyePosition(pt);
        float yaw = me.getYRot() * (float) (Math.PI / 180.0);
        float pitch = me.getXRot() * (float) (Math.PI / 180.0);
        Vec3 look = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 v = look.scale(1.5).add(0.0, me.getDeltaMovement().y * 0.5, 0.0);
        path.add(pos);
        for (int i = 0; i < 200; i++) {
            Vec3 next = pos.add(v);
            BlockHitResult hit =
                    mc().level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, me));
            if (hit.getType() != HitResult.Type.MISS) {
                path.add(hit.getLocation());
                return hit.getLocation();
            }
            pos = next;
            path.add(pos);
            if (pos.y < mc().level.getMinY()) return pos;
            v = v.scale(0.99).subtract(0.0, 0.03, 0.0);
        }
        return null;
    }

    // ---------------------------------------------------------------- who is inside

    private static boolean enemy(Player p) {
        LocalPlayer me = mc().player;
        return p != me && p.isAlive() && !p.isSpectator() && !Friends.is(p) && !naked(p);
    }

    /** Invisible and bare: a staff member or a bot, not someone to warn about. */
    private static boolean naked(Player p) {
        if (!p.isInvisible()) return false;
        for (EquipmentSlot s :
                new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!p.getItemBySlot(s).isEmpty()) return false;
        }
        return true;
    }

    private static boolean enemyIn(AABB box) {
        for (Player p : mc().level.players()) if (enemy(p) && box.contains(p.position())) return true;
        return false;
    }

    private static boolean enemyOn(List<BlockPos> blocks) {
        Set<BlockPos> set = new HashSet<>(blocks);
        for (Player p : mc().level.players()) if (enemy(p) && set.contains(p.blockPosition())) return true;
        return false;
    }

    private static boolean enemyNear(Vec3 at, double r) {
        for (Player p : mc().level.players()) if (enemy(p) && p.distanceToSqr(at) <= r * r) return true;
        return false;
    }

    // ---------------------------------------------------------------- hud

    void hud(GuiGraphics g) {
        Minecraft mc = mc();
        if (!this.live() || mc.player == null || mc.screen != null || this.timers.isEmpty()) return;
        long now = System.currentTimeMillis();
        int fs = Math.max(11, Math.round(Px.height() / 1080.0F * 13.0F));
        Vec3 me = mc.player.position();
        for (Timer t : this.timers) {
            String key = t.type == FunTime.Item.PLAST ? "plast" : "trap";
            AABB box = t.box();
            Vec3 at = t.at != null ? t.at : (box != null ? box.getCenter() : null);
            if (at == null || at.distanceToSqr(me) > CARD_RANGE * CARD_RANGE) continue;
            float life = TrapZone.clamp((now - t.start) / 250.0F) * (1.0F - TrapZone.clamp((now - t.end()) / 500.0F));
            TrapZone.countdown(
                    g,
                    at,
                    t.left(now) / 1000.0F,
                    t.duration / 1000.0F,
                    life,
                    false,
                    fs,
                    new ItemStack(t.type == FunTime.Item.PLAST ? Items.DRIED_KELP : Items.NETHERITE_SCRAP),
                    zone(key),
                    caught(key));
        }
    }

    protected void onDisable() {
        this.structures.clear();
        this.timers.clear();
    }

    // ---------------------------------------------------------------- timers

    static final class Timer {
        final FunTime.Item type;
        final String name;
        final long start = System.currentTimeMillis();
        final int duration;
        final float yaw;
        final float pitch;
        final Vec3 from;
        Vec3 at;
        long shownAt;

        Timer(FunTime.Item type, String name, int duration, float yaw, float pitch, Vec3 from) {
            this.type = type;
            this.name = name;
            this.duration = duration;
            this.yaw = yaw;
            this.pitch = pitch;
            this.from = from;
        }

        boolean over() {
            return System.currentTimeMillis() - this.start >= this.duration + 600L;
        }

        long end() {
            return this.start + this.duration;
        }

        /** Where the structure stands: from where it was used and the look at that moment. */
        AABB box() {
            BlockPos at = BlockPos.containing(this.from);
            if (this.type == FunTime.Item.TRAP) return trapBox(at, this.name.startsWith("Драконья"));
            Plast p = plastShape(at, this.yaw, this.pitch, item("plast").dragon.on());
            return p.box();
        }

        long left(long now) {
            return Math.max(0L, this.duration - (now - this.start));
        }

        float share(long now) {
            return this.duration <= 0 ? 0.0F : (float) this.left(now) / this.duration;
        }
    }

    /**
     * A use of a paid item, told by its cooldown starting while one of them leaves the inventory: the cooldown
     * alone would also start when the server refuses the item.
     */
    private final class CooldownUse {
        private final Item item;
        private boolean cooling;
        private int lastCount = -1;
        private int sinceSpent = 1000;
        private Timer waiting;
        private int wait;

        CooldownUse(Item item) {
            this.item = item;
        }

        void tick(Supplier<Timer> make) {
            LocalPlayer p = mc().player;
            int count = 0;
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                ItemStack s = p.getInventory().getItem(i);
                if (s.getItem() == this.item) count += s.getCount();
            }
            this.sinceSpent =
                    this.lastCount != -1 && count == this.lastCount - 1 ? 0 : Math.min(this.sinceSpent + 1, 1000);
            this.lastCount = count;
            boolean now = p.getCooldowns().isOnCooldown(this.item.getDefaultInstance());
            if (now && !this.cooling) {
                if (this.sinceSpent <= 4) {
                    FtHelper.this.timers.add(make.get());
                    this.waiting = null;
                    this.wait = 0;
                } else {
                    this.waiting = make.get();
                    this.wait = 4;
                }
            } else if (this.wait > 0) {
                if (this.sinceSpent == 0) {
                    FtHelper.this.timers.add(this.waiting);
                    this.waiting = null;
                    this.wait = 0;
                } else if (--this.wait == 0) {
                    this.waiting = null;
                }
            }
            this.cooling = now;
        }
    }

    /** Placed traps and plasts found by the set of sounds each kind plays where it stands. */
    static final class Structures {
        private static final long HEARD_MS = 1000L;
        private static final long KEEP_MS = 1500L;
        private static final double HEAR_RANGE = 24.0;
        private static final List<Signature> SIGNATURES = signatures();
        private final Queue<Heard> incoming = new ConcurrentLinkedQueue<>();
        private final List<Heard> recent = new ArrayList<>();
        private final List<Placement> placed = new ArrayList<>();

        record Heard(Identifier id, float volume, float pitch, Vec3 pos, BlockPos block, long at) {}

        record Cue(Identifier id, float volume, float pitch) {
            boolean matches(Heard h) {
                return this.id.equals(h.id())
                        && Math.abs(h.volume() - this.volume) <= 0.06F
                        && Math.abs(h.pitch() - this.pitch) <= 0.06F;
            }
        }

        record Signature(FunTime.Item type, List<Cue> cues) {}

        record Placement(BlockPos pos, FunTime.Item type, long at) {}

        void heard(ClientboundSoundPacket packet, Identifier id) {
            if (packet.getSource() != SoundSource.RECORDS) return;
            Vec3 pos = new Vec3(packet.getX(), packet.getY(), packet.getZ());
            this.incoming.add(new Heard(
                    id,
                    packet.getVolume(),
                    packet.getPitch(),
                    pos,
                    BlockPos.containing(pos),
                    System.currentTimeMillis()));
        }

        void tick(boolean traps, boolean plasts) {
            long now = System.currentTimeMillis();
            Vec3 me = mc().player.position();
            Set<BlockPos> fresh = new HashSet<>();
            Heard h;
            while ((h = this.incoming.poll()) != null) {
                if (now - h.at() <= HEARD_MS && me.distanceToSqr(h.pos()) <= HEAR_RANGE * HEAR_RANGE) {
                    this.recent.add(h);
                    fresh.add(h.block());
                }
            }
            this.recent.removeIf(x -> x.at() < now - HEARD_MS);
            for (BlockPos b : fresh) this.match(b, now, traps, plasts);
            this.placed.removeIf(p -> p.at() < now - KEEP_MS);
        }

        Placement take(FunTime.Item type, Vec3 near, double range, long now, long within) {
            Placement best = null;
            double bestD = 0.0;
            boolean bestSame = false;
            for (Placement p : this.placed) {
                if (now - p.at() > within) continue;
                double d = Vec3.atCenterOf(p.pos()).distanceToSqr(near);
                if (d > range * range) continue;
                boolean same = p.type() == type;
                if (best == null || same && !bestSame || same == bestSame && d < bestD) {
                    best = p;
                    bestD = d;
                    bestSame = same;
                }
            }
            if (best != null) this.placed.remove(best);
            return best;
        }

        void clear() {
            this.incoming.clear();
            this.recent.clear();
            this.placed.clear();
        }

        private void match(BlockPos at, long now, boolean traps, boolean plasts) {
            List<Heard> here = new ArrayList<>(4);
            for (Heard h : this.recent) if (h.block().equals(at)) here.add(h);
            if (here.isEmpty()) return;
            for (Signature s : SIGNATURES) {
                List<Heard> used = matched(here, s);
                if (used != null) {
                    FunTime.Item type = s.type();
                    FunTime.Item held = heldNear(Vec3.atCenterOf(at));
                    if (held != FunTime.Item.NONE) type = held;
                    if ((type != FunTime.Item.TRAP || traps) && (type != FunTime.Item.PLAST || plasts)) {
                        this.placed.removeIf(p -> p.pos().equals(at));
                        this.placed.add(new Placement(at, type, now));
                    }
                    this.recent.removeAll(used);
                    return;
                }
            }
        }

        private static List<Heard> matched(List<Heard> here, Signature s) {
            List<Heard> left = new ArrayList<>(here);
            List<Heard> used = new ArrayList<>(s.cues().size());
            for (Cue c : s.cues()) {
                Heard found = null;
                for (Heard h : left) {
                    if (c.matches(h)) {
                        found = h;
                        break;
                    }
                }
                if (found == null) return null;
                left.remove(found);
                used.add(found);
            }
            return used;
        }

        /** What the player standing at a placement holds tells a trap from a plast where the sounds can't. */
        private static FunTime.Item heldNear(Vec3 at) {
            Player best = null;
            double bestD = 9.0;
            for (Player p : mc().level.players()) {
                double d = p.position().distanceToSqr(at);
                if (d <= bestD && held(p) != FunTime.Item.NONE) {
                    bestD = d;
                    best = p;
                }
            }
            return best == null ? FunTime.Item.NONE : held(best);
        }

        private static FunTime.Item held(Player p) {
            FunTime.Item main = FunTime.item(p.getMainHandItem());
            return main != FunTime.Item.NONE ? main : FunTime.item(p.getOffhandItem());
        }

        private static List<Signature> signatures() {
            FunTime.Item t = FunTime.Item.TRAP;
            FunTime.Item p = FunTime.Item.PLAST;
            List<Signature> out = new ArrayList<>();
            out.add(sig(
                    t,
                    c("entity.wither.break_block", .5F, 1F),
                    c("entity.evoker_fangs.attack", .5F, .85F),
                    c("ui.toast.challenge_complete", .35F, 1.5F),
                    c("entity.ender_dragon.growl", .2F, 1.5F)));
            out.add(sig(
                    p,
                    c("entity.wither.break_block", .7F, 1F),
                    c("entity.evoker_fangs.attack", .5F, .85F),
                    c("ui.toast.challenge_complete", .35F, 1.5F),
                    c("entity.ender_dragon.growl", .2F, 1.5F)));
            out.add(sig(
                    t,
                    c("entity.wither.ambient", .7F, .5F),
                    c("entity.zombie_villager.converted", .55F, .85F),
                    c("entity.ender_dragon.hurt", .6F, .5F)));
            out.add(sig(t, c("entity.wither.break_block", .7F, 1F), c("entity.ender_dragon.hurt", .6F, .5F)));
            out.add(sig(t, c("entity.ender_dragon.hurt", .6F, .5F), c("block.piston.extend", .6F, .5F)));
            out.add(sig(t, c("block.piston.extend", .7F, .5F)));
            out.add(sig(
                    t,
                    c("entity.blaze.shoot", .7F, 1F),
                    c("entity.ghast.hurt", .6F, .5F),
                    c("block.piston.contract", .45F, .5F)));
            out.add(sig(
                    t,
                    c("entity.blaze.hurt", .7F, .5F),
                    c("entity.ender_dragon.flap", .7F, .85F),
                    c("block.shulker_box.close", .6F, 1.5F)));
            out.add(sig(
                    t,
                    c("block.beacon.power_select", .7F, 1F),
                    c("entity.phantom.ambient", .6F, .5F),
                    c("entity.ender_dragon.hurt", .55F, .5F)));
            out.add(sig(
                    t,
                    c("item.armor.equip_netherite", .7F, .5F),
                    c("block.piston.contract", .45F, 1.5F),
                    c("entity.skeleton.death", .45F, .5F)));
            out.add(sig(
                    t,
                    c("block.piston.extend", .7F, .5F),
                    c("entity.evoker.cast_spell", .7F, .85F),
                    c("entity.ender_dragon.hurt", .6F, .5F)));
            out.add(sig(
                    t,
                    c("entity.lightning_bolt.impact", .25F, .75F),
                    c("entity.blaze.shoot", .6F, .75F),
                    c("block.lava.pop", .7F, .5F)));
            out.add(sig(
                    t,
                    c("entity.enderman.ambient", .7F, .5F),
                    c("entity.ender_dragon.flap", .7F, .85F),
                    c("block.shulker_box.close", .6F, .5F)));
            out.add(sig(t, c("item.armor.equip_netherite", .35F, .65F), c("block.piston.extend", .45F, .75F)));
            out.add(sig(t, c("block.glass.break", .7F, 2F), c("block.snow.break", .2F, 1.5F)));
            out.add(sig(t, c("entity.lightning_bolt.thunder", .7F, 1F), c("entity.phantom.death", .6F, .5F)));
            out.add(sig(p, c("block.anvil.place", .7F, 1.1F)));
            out.sort(Comparator.comparingInt((Signature s) -> s.cues().size()).reversed());
            return List.copyOf(out);
        }

        private static Signature sig(FunTime.Item type, Cue... cues) {
            return new Signature(type, List.of(cues));
        }

        private static Cue c(String id, float volume, float pitch) {
            return new Cue(Identifier.withDefaultNamespace(id), volume, pitch);
        }
    }
}
