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
package net.arcanic.visuals.gui;

import java.util.List;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Social;
import net.arcanic.visuals.core.Sync;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.emote.Emotes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** The player menu: look at an Arcanic player and click the wheel. */
public final class InteractMenu extends PillMenu {
    private static final double REACH = 12.0;

    private final Player target;
    // looked up once: a missing emote would reload the emote files on every frame
    private final Emotes.Emote hello = Modules.EMOTES.enabled() ? Modules.EMOTES.byName("Gentleman") : null;
    private long confirmAt;

    private InteractMenu(Player target) {
        this.target = target;
    }

    /** The player in the crosshair, or the one closest to it a few blocks away. */
    public static Player aimed(Minecraft mc) {
        if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player p && p != mc.player) {
            return p;
        }
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1.0F);
        Player best = null;
        double bestAngle = Math.toRadians(7.0);

        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == mc.player || p.isSpectator() || p.isInvisibleTo(mc.player)) continue;
            Vec3 to = p.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > REACH || dist < 0.01) continue;
            double angle =
                    Math.acos(Math.max(-1.0, Math.min(1.0, to.scale(1.0 / dist).dot(look))));
            if (angle < bestAngle && mc.player.hasLineOfSight(p)) {
                best = p;
                bestAngle = angle;
            }
        }

        return best;
    }

    /** True when the menu has something to offer for this player: they play with Arcanic or are a friend. */
    public static boolean knows(Player p) {
        return Social.friendOf(p) != null
                || Social.askedBy(p) != null
                || Social.askedTo(p) != null
                || Sync.of(p) != null;
    }

    /** Opens the menu for the aimed player when they play with Arcanic; true if it opened. */
    public static boolean open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return false;
        Player p = aimed(mc);
        return p != null && open(p);
    }

    public static boolean open(Player p) {
        Minecraft mc = Minecraft.getInstance();
        if (!knows(p) || mc.screen != null) return false;
        if (!Sync.signedIn()) {
            Social.note(Lang.tr("Нужен вход: войди в Arcanic Social"), false);
            return true;
        }
        mc.setScreen(new InteractMenu(p));
        UiSound.play("interact_open");
        return true;
    }

    private String nick() {
        return this.target.getGameProfile().name();
    }

    @Override
    String title() {
        return this.nick();
    }

    @Override
    String subtitle() {
        return Lang.tr(Social.friendOf(this.target) != null ? "Друг в Arcanic Social" : "Играет с Arcanic Visuals");
    }

    @Override
    void build(List<Pill> into) {
        Social.Friend f = Social.friendOf(this.target);
        if (f != null) {
            into.add(new Pill("flag", AMBER, "Поделиться меткой", () -> Social.shareMark(f), false, false));
            into.add(new Pill(
                    "bag",
                    VIOLET,
                    "Посмотреть инвентарь",
                    () -> {
                        Social.askInventory(f);
                        Minecraft.getInstance().setScreen(new FriendInventoryScreen(null, f));
                    },
                    true,
                    false));
            boolean lit = Social.lit(f.account());
            into.add(new Pill(
                    lit ? "eye" : "eye-off",
                    GREEN,
                    lit ? "Выключить подсветку" : "Подсветить друга",
                    () -> Social.light(f.account(), !lit),
                    true,
                    false));
            into.add(new Pill("navigate", BLUE, "Отправить координаты", () -> Social.sharePosition(f), false, false));
        } else {
            Social.Request asked = Social.askedBy(this.target);
            Social.Request sent = Social.askedTo(this.target);
            if (asked != null) {
                into.add(new Pill(
                        "check", GREEN, "Принять заявку в друзья", () -> Social.accept(asked.account()), false, false));
                into.add(new Pill("x", GREY, "Отклонить заявку", () -> Social.decline(asked.account()), false, false));
            } else if (sent != null) {
                into.add(
                        new Pill("user-x", GREY, "Отменить заявку", () -> Social.cancel(sent.account()), false, false));
            } else {
                into.add(new Pill(
                        "user-plus",
                        GREEN,
                        "Добавить в друзья",
                        () -> {
                            Social.add(this.target);
                            UiSound.play("friend_sent");
                        },
                        false,
                        false));
            }
        }

        Emotes.Emote hello = this.hello;
        if (hello != null) {
            into.add(new Pill("hand-heart", AMBER, "Поздороваться", () -> Modules.EMOTES.play(hello), false, false));
        }

        into.add(new Pill(
                "copy",
                GREY,
                "Скопировать ник",
                () -> {
                    Minecraft.getInstance().keyboardHandler.setClipboard(this.nick());
                    Social.note(Lang.tr("Ник скопирован") + ": " + this.nick(), true);
                },
                false,
                false));
        if (f != null) {
            boolean sure = System.currentTimeMillis() - this.confirmAt < 3000L;
            into.add(new Pill(
                    "user-x",
                    RED,
                    sure ? "Точно удалить?" : "Удалить из друзей",
                    () -> {
                        if (System.currentTimeMillis() - this.confirmAt < 3000L) {
                            Social.remove(f.account());
                            this.close();
                        } else {
                            this.confirmAt = System.currentTimeMillis();
                            UiSound.play("pop", 0.9F, 0.8F);
                        }
                    },
                    true,
                    true));
        }
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null
                || !this.target.isAlive()
                || this.target.isRemoved()
                || this.target.distanceTo(mc.player) > REACH + 4.0) {
            this.onClose();
        }
    }
}
