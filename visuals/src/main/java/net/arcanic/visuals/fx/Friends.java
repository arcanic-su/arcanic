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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Config;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Social;
import net.arcanic.visuals.hud.Toasts;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

/** Friend list and their highlight. Middle click a player to add or remove them. */
public final class Friends extends Module {
    public final Setting.Text names = add(new Setting.Text("names", "Список друзей", "", "Ники через запятую", 2000));
    public final Setting.Bool middleClick =
            add(new Setting.Bool("middle_click", "Колёсико мыши — добавить/убрать", true));
    public final Setting.Bool highlight = add(new Setting.Bool("highlight", "Подсветка друзей", true));
    public final Setting.Colour colour = add(new Setting.Colour("color", "Цвет подсветки", false, 0xFF4ADE80))
            .showIf(highlight::on);
    public final Setting.Bool noAttack = add(new Setting.Bool("no_target", "Не подсвечивать как врагов", true));

    private String parsedFrom;
    private Set<String> parsed = Set.of();

    public Friends() {
        super(
                "friends",
                "Друзья",
                "Подсвечивает друзей цветной обводкой, чтобы не спутать их с врагами. Добавляй ник в список или жми колёсиком мыши по игроку.",
                Category.PLAYERS,
                false);
    }

    public static boolean is(Entity e) {
        Friends f = Modules.FRIENDS;
        return f != null
                && f.enabled()
                && e instanceof Player p
                && f.list().contains(key(p.getGameProfile().name()));
    }

    /** True when the friend should get the friend outline: a friend from the list here, or an Arcanic Social one. */
    public static boolean glows(Entity e) {
        return is(e) && Modules.FRIENDS.highlight.on() && e != Minecraft.getInstance().player || Social.glows(e);
    }

    /** True when some friend may be outlined this frame. */
    public static boolean anyGlow() {
        return Modules.FRIENDS.enabled() && Modules.FRIENDS.highlight.on() || Social.anyGlow();
    }

    public static boolean hideFromEnemies(Entity e) {
        return is(e) && Modules.FRIENDS.noAttack.on() || e instanceof Player p && Social.friendOf(p) != null;
    }

    public static int glowColour() {
        return Modules.FRIENDS.colour.argb() | 0xFF000000;
    }

    private Set<String> list() {
        String raw = names.get();
        if (!raw.equals(parsedFrom)) {
            parsedFrom = raw;
            parsed = Arrays.stream(raw.split("[,;\\s]+"))
                    .filter(s -> !s.isBlank())
                    .map(Friends::key)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        return parsed;
    }

    public void middleClicked() {
        if (!enabled() || !middleClick.on()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null
                || !(mc.hitResult instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof Player p)) {
            return;
        }
        String name = p.getGameProfile().name();
        Set<String> next = new LinkedHashSet<>(Arrays.asList(names.get().split("\\s*,\\s*")));
        next.removeIf(String::isBlank);
        boolean removed = next.removeIf(n -> key(n).equals(key(name)));
        if (!removed) next.add(name);
        names.set(String.join(", ", next));
        Config.dirty();
        Toasts.setting((removed ? "Убран из друзей: " : "Добавлен в друзья: ") + name, !removed);
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
