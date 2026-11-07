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
package net.arcanic.visuals.helper;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Social;
import net.arcanic.visuals.core.Sync;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.fx.Projection;
import net.arcanic.visuals.gui.InteractMenu;
import net.arcanic.visuals.gui.PingMenu;
import net.arcanic.visuals.render.Px;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.lwjgl.glfw.GLFW;

/**
 * Pings for friends, as in Apex: tap the mouse wheel for the obvious one (an enemy on a player, loot on an item, "go
 * here" on a block), hold it for the menu of all of them, tap a friend's ping to answer "got it". A ping is what the
 * player sees himself, shared with friends on the same server; on an enemy it follows him only while he is in view.
 */
public final class Pings {
    private static final double RANGE = 192.0;
    private static final long HOLD = 230L;
    private static final int PER_PLAYER = 2;
    private static final List<Beacon> BEACONS = new ArrayList<>();
    private static boolean holding;
    private static long pressAt;
    private static Aim pressAim;
    private static Player tapPlayer;
    private static Beacon aimed;

    private Pings() {}

    public enum Kind {
        ENEMY("enemy", "crosshair", 0xFFFF5C72, "Враг здесь", "Враг", 10000L, "ping_enemy"),
        GO("go", "navigate", 0xFF60A5FA, "Иду сюда", "Сюда", 15000L, "ping_location"),
        ATTACK("attack", "sword", 0xFFFB923C, "Атакуем", "Атакуем", 15000L, "ping_enemy"),
        DEFEND("defend", "shield", 0xFF4ADE80, "Обороняем", "Обороняем", 20000L, "ping_location"),
        LOOT("loot", "gem", 0xFFFBBF24, "Лут здесь", "Лут", 20000L, "ping_loot"),
        WATCH("watch", "eye", 0xFF22D3EE, "Смотрю сюда", "Смотрю", 12000L, "ping_location"),
        RETREAT("retreat", "running", 0xFFA78BFA, "Отступаем", "Отступаем", 12000L, "ping_danger"),
        HELP("help", "alert-circle", 0xFFF472B6, "Нужна помощь", "Помогите", 25000L, "ping_danger");

        public final String id;
        public final String icon;
        public final int colour;
        public final String menuLabel;
        final String shortLabel;
        final long life;
        final String sound;

        Kind(String id, String icon, int colour, String menuLabel, String shortLabel, long life, String sound) {
            this.id = id;
            this.icon = icon;
            this.colour = colour;
            this.menuLabel = menuLabel;
            this.shortLabel = shortLabel;
            this.life = life;
            this.sound = sound;
        }

        static Kind of(String id) {
            for (Kind k : values()) if (k.id.equals(id)) return k;
            return GO;
        }
    }

    /** What the crosshair was on when the wheel went down: a point, maybe a player or an item, and how to say it. */
    public record Aim(Vec3 pos, Entity target, String what) {}

    private static final class Beacon {
        String owner; // the account, or "" for the player's own pings
        String name;
        long id;
        Kind kind;
        Vec3 pos;
        String dim;
        String server;
        UUID target;
        String label;
        long born;
        long until;
        final List<String> acks = new ArrayList<>();
        // where it was drawn this frame, for the "got it" tap
        int sx = Integer.MIN_VALUE;
        int sy;
    }

    // ------------------------------------------------------------------ the wheel

    /** True when a friend plays on this server: only then do pings mean anything. */
    public static boolean pingable() {
        String here = Social.serverHere();
        if (!Sync.on() || here == null) return false;
        for (Social.Friend f : Social.friends()) if (f.inWorld() && here.equals(f.server())) return true;
        return false;
    }

    /** The wheel went down in the world; true when pings take it (the game must not pick a block then). */
    public static boolean press() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return false;
        Player p = InteractMenu.aimed(mc);
        Player arcanic = p != null && InteractMenu.knows(p) ? p : null;
        if (arcanic == null && !pingable()) return false;
        holding = true;
        pressAt = System.currentTimeMillis();
        pressAim = aim(mc);
        tapPlayer = arcanic;
        return true;
    }

    public static boolean holding() {
        return holding;
    }

    /** Held long enough: the menu of all pings. A release missed while the game was busy counts as one. */
    public static void tick(Minecraft mc) {
        if (!holding) return;
        if (mc.player == null || mc.level == null) {
            holding = false;
            return;
        }
        if (GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) != GLFW.GLFW_PRESS) {
            release();
            return;
        }
        if (System.currentTimeMillis() - pressAt >= HOLD && mc.screen == null && pingable()) {
            holding = false;
            mc.setScreen(new PingMenu(pressAim));
            UiSound.play("interact_open", 1.15F, 0.8F);
        }
    }

    /** The wheel came up before the menu opened: a tap. True when it was ours. */
    public static boolean release() {
        if (!holding) return false;
        holding = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return true;
        if (tapPlayer != null) {
            InteractMenu.open(tapPlayer);
            return true;
        }
        if (aimed != null) {
            answer(aimed);
            return true;
        }
        Aim a = pressAim;
        if (a.target instanceof Player) send(Kind.ENEMY, a);
        else if (a.target instanceof ItemEntity) send(Kind.LOOT, a);
        else send(Kind.GO, a);
        return true;
    }

    /** What the player looks at, up to 192 blocks: a player or an item before the block behind them. */
    private static Aim aim(Minecraft mc) {
        Player me = mc.player;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 eye = me.getEyePosition(pt);
        Vec3 look = me.getViewVector(pt);
        HitResult block = me.pick(RANGE, pt, false);
        double reach = block.getType() == HitResult.Type.MISS ? RANGE : eye.distanceTo(block.getLocation());
        AABB box = me.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                me,
                eye,
                eye.add(look.scale(reach)),
                box,
                e -> e != me && !e.isSpectator() && (e instanceof Player || e instanceof ItemEntity),
                reach * reach);
        if (hit != null) {
            Entity e = hit.getEntity();
            int far = (int) eye.distanceTo(e.position());
            String what = e instanceof ItemEntity item
                    ? Lang.tr("Предмет") + ": " + item.getItem().getHoverName().getString() + " · " + far + " "
                            + Lang.tr("м")
                    : Lang.tr("Игрок") + " " + e.getName().getString() + " · " + far + " " + Lang.tr("м");
            return new Aim(e.position(), e, what);
        }
        if (block.getType() != HitResult.Type.MISS) {
            return new Aim(block.getLocation(), null, Lang.tr("Точка") + " · " + (int) reach + " " + Lang.tr("м"));
        }
        return new Aim(eye.add(look.scale(RANGE)), null, Lang.tr("Вдаль, по направлению взгляда"));
    }

    // ------------------------------------------------------------------ sending and hearing

    /** A ping from this player: shown here at once and sent to the friends. */
    public static void send(Kind kind, Aim a) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Beacon b = new Beacon();
        b.owner = "";
        b.name = mc.player.getGameProfile().name();
        b.id = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        b.kind = kind;
        if (kind == Kind.HELP) {
            b.pos = mc.player.position(); // help is wanted where the player stands
            b.target = mc.player.getUUID();
        } else {
            b.pos = a.pos;
            b.target = a.target instanceof Player p ? p.getUUID() : null;
            if (a.target instanceof ItemEntity item)
                b.label = item.getItem().getHoverName().getString();
            else if (a.target instanceof Player p) b.label = p.getName().getString();
        }
        b.dim = mc.level.dimension().identifier().toString();
        b.server = Social.serverHere();
        b.born = System.currentTimeMillis();
        b.until = b.born + kind.life;
        add(b);
        UiSound.play(kind.sound, 1.0F, 0.75F);

        JsonObject o = new JsonObject();
        o.addProperty("t", "beacon");
        o.addProperty("id", b.id);
        o.addProperty("kind", kind.id);
        o.addProperty("x", b.pos.x);
        o.addProperty("y", b.pos.y);
        o.addProperty("z", b.pos.z);
        o.addProperty("dim", b.dim);
        o.addProperty("server", b.server);
        if (b.target != null) o.addProperty("target", b.target.toString());
        if (b.label != null) o.addProperty("label", b.label);
        Sync.post(o);
    }

    /** A tap on a ping: "got it" on a friend's, away with one's own. */
    private static void answer(Beacon b) {
        JsonObject o = new JsonObject();
        if (b.owner.isEmpty()) {
            BEACONS.remove(b);
            o.addProperty("t", "beacon_off");
            o.addProperty("id", b.id);
            UiSound.play("interact_close");
        } else {
            String me = Minecraft.getInstance().player.getGameProfile().name();
            if (b.acks.contains(me)) return;
            b.acks.add(me);
            o.addProperty("t", "beacon_ack");
            o.addProperty("owner", b.owner);
            o.addProperty("id", b.id);
            UiSound.play("ping_ack");
        }
        Sync.post(o);
    }

    private static void add(Beacon b) {
        BEACONS.removeIf(o -> o.owner.equals(b.owner) && o.id == b.id);
        BEACONS.add(b);
        // a few pings per player at once, as in Apex: a new one pushes the oldest out
        int mine = 0;
        for (int i = BEACONS.size() - 1; i >= 0; --i) {
            if (BEACONS.get(i).owner.equals(b.owner) && ++mine > PER_PLAYER) BEACONS.remove(i);
        }
    }

    /** A ping message from the service, on the game thread. */
    public static void heard(String t, JsonObject p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        switch (t) {
            case "beacon" -> {
                Beacon b = new Beacon();
                b.owner = str(p, "from");
                b.name = Objects.requireNonNullElse(str(p, "name"), b.owner);
                b.id = p.get("id").getAsLong();
                b.kind = Kind.of(str(p, "kind"));
                b.pos = new Vec3(num(p, "x"), num(p, "y"), num(p, "z"));
                b.dim = str(p, "dim");
                b.server = str(p, "server");
                b.label = str(p, "label");
                String target = str(p, "target");
                try {
                    b.target = target == null ? null : UUID.fromString(target);
                } catch (IllegalArgumentException e) {
                    b.target = null;
                }
                if (b.owner == null || !Objects.equals(b.server, Social.serverHere())) return; // a ping elsewhere
                b.born = System.currentTimeMillis();
                b.until = b.born + b.kind.life;
                add(b);
                UiSound.play(b.kind.sound);
            }
            case "beacon_ack" -> {
                String owner = str(p, "owner");
                long id = p.get("id").getAsLong();
                String who = Objects.requireNonNullElse(str(p, "name"), "?");
                for (Beacon b : BEACONS) {
                    boolean same = b.id == id && (b.owner.equals(owner) || b.owner.isEmpty());
                    if (same && !b.acks.contains(who)) {
                        b.acks.add(who);
                        if (b.owner.isEmpty()) UiSound.play("ping_ack"); // a friend answered my ping
                    }
                }
            }
            case "beacon_off" -> {
                String owner = str(p, "from");
                long id = p.get("id").getAsLong();
                BEACONS.removeIf(b -> b.id == id && b.owner.equals(owner));
            }
            default -> {}
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    private static double num(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : 0.0;
    }

    // ------------------------------------------------------------------ on screen

    /** Where a ping stands now: over an enemy's head while he is in view, else where it was put. */
    private static Vec3 spot(Minecraft mc, Beacon b) {
        if (b.target != null) {
            Player p = mc.level.getPlayerByUUID(b.target);
            if (p == mc.player) return p.position().add(0.0, p.getBbHeight() + 0.6, 0.0);
            if (p != null && !p.isInvisibleTo(mc.player) && mc.player.hasLineOfSight(p)) {
                return p.position().add(0.0, p.getBbHeight() + 0.6, 0.0);
            }
            return b.pos.add(0.0, 2.4, 0.0);
        }
        return b.kind == Kind.LOOT ? b.pos.add(0.0, 0.9, 0.0) : b.pos.add(0.0, 0.6, 0.0);
    }

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        aimed = null;
        if (mc.player == null || mc.level == null || BEACONS.isEmpty()) return;
        long now = System.currentTimeMillis();
        String dim = mc.level.dimension().identifier().toString();
        String here = Social.serverHere();
        int w = Px.width();
        int h = Px.height();
        float u = Math.max(0.6F, Math.min(1.6F, (float) h / 1080.0F));
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 eye = mc.player.getEyePosition(pt);
        double best = 40.0 * u;
        float base = Px.fade;
        Iterator<Beacon> it = BEACONS.iterator();

        while (it.hasNext()) {
            Beacon b = it.next();
            if (now > b.until) {
                it.remove();
                continue;
            }
            b.sx = Integer.MIN_VALUE;
            if (!Objects.equals(b.dim, dim) || !Objects.equals(b.server, here)) continue;
            Vec3 at = spot(mc, b);
            double[] s = Projection.toScreen(at, w, h);
            int margin = Math.round(40.0F * u);
            boolean on = s != null && s[0] > margin && s[0] < w - margin && s[1] > margin && s[1] < h - margin;
            if (on) {
                int x = (int) s[0];
                int y = (int) s[1];
                b.sx = x;
                b.sy = y;
                double off = Math.hypot(x - w / 2.0, y - h / 2.0);
                if (off < best) {
                    best = off;
                    aimed = b;
                }
            }
        }

        for (Beacon b : BEACONS) {
            if (!Objects.equals(b.dim, dim) || !Objects.equals(b.server, here)) continue;
            float life =
                    Math.min(1.0F, (float) (b.until - now) / 800.0F) * Math.min(1.0F, (float) (now - b.born) / 150.0F);
            Px.fade = base * life;
            Vec3 at = spot(mc, b);
            int dist = (int) eye.distanceTo(at);
            if (b.sx != Integer.MIN_VALUE) {
                marker(g, b, b.sx, b.sy, dist, u, now, b == aimed);
            } else {
                edge(g, mc, b, at, w, h, u, now);
            }
        }

        Px.fade = base;
    }

    private static void marker(GuiGraphics g, Beacon b, int x, int y, int dist, float u, long now, boolean hot) {
        int c = b.kind.colour;
        int r = Math.round(15.0F * u);
        float age = (float) (now - b.born) / 700.0F;
        if (age < 1.0F) {
            // the arrival: a ring running out and fading
            int rr = Math.round(r + age * r * 2.2F);
            Px.ring(g, x, y, rr, Px.a(c & 0xFFFFFF, Math.round(200.0F * (1.0F - age))));
        }
        if (b.kind == Kind.HELP) {
            float beat = (float) ((now - b.born) % 900L) / 900.0F;
            Px.ring(g, x, y, Math.round(r + beat * r * 1.6F), Px.a(c & 0xFFFFFF, Math.round(160.0F * (1.0F - beat))));
        }
        int gs = r * 6;
        Px.glow(g, x - gs / 2, y - gs / 2, gs, gs, Px.a(c & 0xFFFFFF, hot ? 150 : 95));
        Px.circle(g, x, y, r, 0xF0161719);
        Px.ring(g, x, y, r, hot ? Px.mix(c, -1, 0.35F) : c);
        int is = Math.round(r * 1.15F);
        Px.icon(g, "w." + b.kind.icon, x - is / 2, y - is / 2, is, c);

        int fs = Math.round(12.0F * u);
        String head = b.label != null && (b.kind == Kind.LOOT || b.kind == Kind.ENEMY)
                ? Lang.tr(b.kind.shortLabel) + ": " + b.label
                : Lang.tr(b.kind.shortLabel);
        String text = head + "  " + dist + " " + Lang.tr("м");
        int tw = Px.textWidth(text, Px.Weight.SEMIBOLD, fs) + Math.round(18.0F * u);
        int th = Math.round(22.0F * u);
        int ty = y + r + Math.round(7.0F * u);
        Px.round(g, x - tw / 2, ty, tw, th, th / 2, 0xE6161719);
        Px.textCenter(g, text, x, ty + (th - fs) / 2, Px.Weight.SEMIBOLD, fs, 0xFFF4F4F6);
        int ny = ty + th + Math.round(4.0F * u);
        int fs2 = Math.round(10.5F * u);
        Px.textCenter(g, b.owner.isEmpty() ? Lang.tr("твой пинг") : b.name, x, ny, Px.Weight.MEDIUM, fs2, 0xB3FFFFFF);
        if (!b.acks.isEmpty()) {
            ny += fs2 + Math.round(4.0F * u);
            Px.textCenter(g, "✓ " + String.join(", ", b.acks), x, ny, Px.Weight.SEMIBOLD, fs2, 0xFF4ADE80);
        }
        if (hot) {
            // the hint for the tap: answer a friend's ping, take one's own away
            String hint = Lang.tr(b.owner.isEmpty() ? "СКМ — убрать" : "СКМ — понял");
            int hw = Px.textWidth(hint, Px.Weight.SEMIBOLD, fs2) + Math.round(14.0F * u);
            int hh = Math.round(18.0F * u);
            int hy = y - r - Math.round(8.0F * u) - hh;
            Px.round(g, x - hw / 2, hy, hw, hh, hh / 2, 0xFFF4F4F6);
            Px.textCenter(g, hint, x, hy + (hh - fs2) / 2, Px.Weight.SEMIBOLD, fs2, 0xFF141518);
        }
    }

    /** Off screen: a small marker on the screen's rim with a chevron pointing where it is. */
    private static void edge(GuiGraphics g, Minecraft mc, Beacon b, Vec3 at, int w, int h, float u, long now) {
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 rel = at.subtract(cam.position());
        Vector3fc up = cam.upVector();
        Vector3fc left = cam.leftVector();
        double x = -(rel.x * left.x() + rel.y * left.y() + rel.z * left.z());
        double y = rel.x * up.x() + rel.y * up.y() + rel.z * up.z();
        double ang = Math.atan2(x, y);
        int margin = Math.round(54.0F * u);
        float cx = (float) (w / 2.0 + Math.sin(ang) * (w / 2.0 - margin));
        float cy = (float) (h / 2.0 - Math.cos(ang) * (h / 2.0 - margin));
        int c = b.kind.colour;
        int r = Math.round(12.0F * u);
        Px.glow(g, Math.round(cx) - r * 3, Math.round(cy) - r * 3, r * 6, r * 6, Px.a(c & 0xFFFFFF, 80));
        Px.circle(g, Math.round(cx), Math.round(cy), r, 0xF0161719);
        Px.ring(g, Math.round(cx), Math.round(cy), r, c);
        int is = Math.round(r * 1.1F);
        Px.icon(g, "w." + b.kind.icon, Math.round(cx) - is / 2, Math.round(cy) - is / 2, is, c);
        int s = Math.round(12.0F * u);
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().rotate((float) ang);
        Px.icon(g, "w.chev-up", -s / 2, -r - s, s, c);
        g.pose().popMatrix();
        if (!b.owner.isEmpty() && (b.kind == Kind.HELP || b.kind == Kind.ENEMY)) {
            int fs = Math.round(10.0F * u);
            Px.textCenter(
                    g,
                    b.name,
                    Math.round(cx),
                    Math.round(cy) + r + Math.round(4.0F * u),
                    Px.Weight.MEDIUM,
                    fs,
                    0xB3FFFFFF);
        }
    }
}
