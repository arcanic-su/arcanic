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
package net.arcanic.visuals.core;

import java.util.ArrayList;
import java.util.List;
import net.arcanic.visuals.arc.Capes;
import net.arcanic.visuals.arc.HandShaders;
import net.arcanic.visuals.arc.MeleeSkins;
import net.arcanic.visuals.cosmetic.Wearables;
import net.arcanic.visuals.emote.Emotes;
import net.arcanic.visuals.fx.ArcVisuals;
import net.arcanic.visuals.fx.BlockHighlight;
import net.arcanic.visuals.fx.CombatFx;
import net.arcanic.visuals.fx.ComboHelper;
import net.arcanic.visuals.fx.HandAnim;
import net.arcanic.visuals.fx.HitSound;
import net.arcanic.visuals.fx.Hitboxes;
import net.arcanic.visuals.fx.KillSound;
import net.arcanic.visuals.fx.Optimizer;
import net.arcanic.visuals.fx.Outline;
import net.arcanic.visuals.fx.Paint;
import net.arcanic.visuals.fx.PlayerTrail;
import net.arcanic.visuals.fx.Trajectories;
import net.arcanic.visuals.fx.VisualFx;
import net.arcanic.visuals.fx.WorldTweaks;
import net.arcanic.visuals.hud.DynamicIsland;
import net.arcanic.visuals.hud.HudElements;
import net.arcanic.visuals.hud.HudModule;
import net.arcanic.visuals.hud.Notify;
import net.arcanic.visuals.hud.TargetHud;

/** Every module of the client, in menu order. */
public final class Modules {
    private Modules() {}

    public static final CombatFx.HitEffects HIT = new CombatFx.HitEffects();
    public static final CombatFx.DamageNumbers DAMAGE = new CombatFx.DamageNumbers();
    public static final CombatFx.KillEffects KILL = new CombatFx.KillEffects();
    public static final CombatFx.TargetMarker MARKER = new CombatFx.TargetMarker();
    public static final CombatFx.HitMarker HITMARKER = new CombatFx.HitMarker();
    public static final ComboHelper COMBO = new ComboHelper();
    public static final CombatFx.SwingTrail SWING = new CombatFx.SwingTrail();
    public static final TargetHud TARGET = new TargetHud();
    public static final VisualFx.JumpCircles JUMP = new VisualFx.JumpCircles();
    public static final VisualFx.Halo HALO = new VisualFx.Halo();
    public static final VisualFx.Footprints FEET = new VisualFx.Footprints();
    public static final VisualFx.Hat HAT = new VisualFx.Hat();
    public static final VisualFx.Atmosphere ATMOS = new VisualFx.Atmosphere();
    public static final VisualFx.Crosshair CROSSHAIR = new VisualFx.Crosshair();
    public static final VisualFx.HandView HANDS = new VisualFx.HandView();
    public static final HandAnim ANIM = new HandAnim();
    public static final MeleeSkins KNIVES = new MeleeSkins();
    public static final Capes CAPES = new Capes();
    public static final HandShaders HAND_SHADER = new HandShaders();
    public static final Paint.Weapon WEAPON = new Paint.Weapon();
    public static final Paint.Armor ARMOR_PAINT = new Paint.Armor();
    public static final Outline OUTLINE = new Outline();
    public static final WorldTweaks WORLD = new WorldTweaks();
    public static final BlockHighlight BLOCK = new BlockHighlight();
    public static final ArcVisuals.ProjectileTrails PROJECTILES = new ArcVisuals.ProjectileTrails();
    public static final ArcVisuals.ElytraTrails ELYTRA = new ArcVisuals.ElytraTrails();
    public static final Hitboxes HITBOXES = new Hitboxes();
    public static final ArcVisuals.NameTags NAMETAGS = new ArcVisuals.NameTags();
    public static final ArcVisuals.TNTTimer TNT = new ArcVisuals.TNTTimer();
    public static final ArcVisuals.ConsumeESP CONSUME = new ArcVisuals.ConsumeESP();
    public static final PlayerTrail TRAILS = new PlayerTrail();
    public static final Trajectories TRAJECTORIES = new Trajectories();
    public static final ArcVisuals.Zoom ZOOM = new ArcVisuals.Zoom();
    public static final ArcVisuals.NoRender NO_RENDER = new ArcVisuals.NoRender();
    public static final Emotes EMOTES = new Emotes();
    public static final Wearables WINGS = new Wearables(
            "wings", "Крылья", "Крылья за спиной в 3-м лице, машут сами. Видят и другие игроки с Arcanic.", "wings");
    public static final Wearables BACKPACKS = new Wearables(
            "backpacks",
            "Рюкзаки",
            "Рюкзак или катана на спине в 3-м лице. Видят и другие игроки с Arcanic.",
            "bodywear");
    public static final Wearables COMPANIONS = new Wearables(
            "companions",
            "Питомцы",
            "Анимированный питомец рядом с персонажем в 3-м лице. Видят и другие игроки с Arcanic.",
            "pet");
    public static final Wearables HEADWEAR = new Wearables(
            "headwear",
            "Головные уборы",
            "Шляпа, нимб или маска на голове в 3-м лице. Видят и другие игроки с Arcanic.",
            "hat");
    public static final KillSound KILLSOUND = new KillSound();
    public static final HitSound HITSOUND = new HitSound();
    public static final Optimizer OPTIMIZER = new Optimizer();
    public static final Notify.Prefs NOTIFY = new Notify.Prefs();

    public static final MenuPrefs PREFS = new MenuPrefs();

    public static final List<Module> ALL = new ArrayList<>();
    public static final List<HudModule> HUD = new ArrayList<>();

    static {
        ALL.addAll(List.of(
                HIT,
                SWING,
                DAMAGE,
                KILL,
                MARKER,
                OUTLINE,
                TRAILS,
                JUMP,
                HALO,
                HAT,
                FEET,
                ARMOR_PAINT,
                ATMOS,
                CROSSHAIR,
                COMBO,
                ANIM,
                HANDS,
                HAND_SHADER,
                KNIVES,
                CAPES,
                WINGS,
                BACKPACKS,
                COMPANIONS,
                HEADWEAR,
                EMOTES));
        ALL.addAll(List.of(KILLSOUND, HITSOUND, OPTIMIZER));
        ALL.addAll(List.of(
                WORLD, BLOCK, PROJECTILES, ELYTRA, HITBOXES, NAMETAGS, TNT, CONSUME, TRAJECTORIES, ZOOM, NO_RENDER));
        HUD.addAll(List.of(
                new DynamicIsland(),
                TARGET,
                new HudElements.Potions(),
                new HudElements.HotKeys(),
                new HudElements.Cooldowns(),
                new HudElements.Performance(),
                new HudElements.Armor(),
                new HudElements.Coordinates(),
                new HudElements.Keystrokes(),
                NOTIFY));
        ALL.addAll(HUD);
        for (Module m : ALL) for (Setting<?> st : m.settings) st.markDefault();
        for (Setting<?> st : PREFS.settings) st.markDefault();
    }

    public static List<Module> of(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : ALL) if (m.category == c) out.add(m);
        // the target HUD is both a combat and a HUD thing: show it under Combat too
        if (c == Category.COMBAT) out.add(TARGET);
        return out;
    }
}
