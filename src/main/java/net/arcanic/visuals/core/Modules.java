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
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.arcanic.visuals.arc.Capes;
import net.arcanic.visuals.arc.HandShaders;
import net.arcanic.visuals.arc.MeleeSkins;
import net.arcanic.visuals.cosmetic.Wearables;
import net.arcanic.visuals.emote.Emotes;
import net.arcanic.visuals.fx.Ambience;
import net.arcanic.visuals.fx.ArcVisuals;
import net.arcanic.visuals.fx.AspectRatio;
import net.arcanic.visuals.fx.AutoSprint;
import net.arcanic.visuals.fx.BlockHighlight;
import net.arcanic.visuals.fx.ClickPearl;
import net.arcanic.visuals.fx.CombatFx;
import net.arcanic.visuals.fx.ComboHelper;
import net.arcanic.visuals.fx.Crosshair;
import net.arcanic.visuals.fx.CustomFog;
import net.arcanic.visuals.fx.CustomTime;
import net.arcanic.visuals.fx.DrillHelper;
import net.arcanic.visuals.fx.ExplosiveThing;
import net.arcanic.visuals.fx.FastSwap;
import net.arcanic.visuals.fx.FloatingCrystals;
import net.arcanic.visuals.fx.FreeLook;
import net.arcanic.visuals.fx.Friends;
import net.arcanic.visuals.fx.HandGlow;
import net.arcanic.visuals.fx.HitSound;
import net.arcanic.visuals.fx.Hitboxes;
import net.arcanic.visuals.fx.ItemScroller;
import net.arcanic.visuals.fx.ItemZones;
import net.arcanic.visuals.fx.KillSound;
import net.arcanic.visuals.fx.LowFire;
import net.arcanic.visuals.fx.MaceFx;
import net.arcanic.visuals.fx.MotionBlur;
import net.arcanic.visuals.fx.NoRender;
import net.arcanic.visuals.fx.Optimizer;
import net.arcanic.visuals.fx.Outline;
import net.arcanic.visuals.fx.Paint;
import net.arcanic.visuals.fx.PlayerTrail;
import net.arcanic.visuals.fx.PotionFx;
import net.arcanic.visuals.fx.SkyShader;
import net.arcanic.visuals.fx.SwingAnimation;
import net.arcanic.visuals.fx.TntZone;
import net.arcanic.visuals.fx.TotemFx;
import net.arcanic.visuals.fx.Trajectories;
import net.arcanic.visuals.fx.TrapZone;
import net.arcanic.visuals.fx.VisualFx;
import net.arcanic.visuals.fx.WorldLyrics;
import net.arcanic.visuals.fx.WorldPlus;
import net.arcanic.visuals.fx.impact.ProjectileHit;
import net.arcanic.visuals.helper.ChatCoords;
import net.arcanic.visuals.helper.ChatTools;
import net.arcanic.visuals.helper.CommandBinds;
import net.arcanic.visuals.helper.EnchantSearch;
import net.arcanic.visuals.helper.FakePlayer;
import net.arcanic.visuals.helper.HolyEvents;
import net.arcanic.visuals.helper.HolyEventsHud;
import net.arcanic.visuals.helper.HolyFeatures;
import net.arcanic.visuals.helper.Kits;
import net.arcanic.visuals.helper.ShulkerPreview;
import net.arcanic.visuals.helper.SoundMixer;
import net.arcanic.visuals.helper.Waypoints;
import net.arcanic.visuals.hud.DynamicIsland;
import net.arcanic.visuals.hud.HudElements;
import net.arcanic.visuals.hud.HudModule;
import net.arcanic.visuals.hud.Notify;
import net.arcanic.visuals.hud.Overlays;
import net.arcanic.visuals.hud.Survival;
import net.arcanic.visuals.hud.TargetHud;

public final class Modules {
    public static final CombatFx.HitEffects HIT = new CombatFx.HitEffects();
    public static final CombatFx.DamageNumbers DAMAGE = new CombatFx.DamageNumbers();
    public static final CombatFx.KillEffects KILL = new CombatFx.KillEffects();
    public static final MaceFx MACE = new MaceFx();
    public static final ProjectileHit PROJECTILE_HIT = new ProjectileHit();
    public static final TotemFx TOTEM = new TotemFx();
    public static final PotionFx POTION_FX = new PotionFx();
    public static final SwingAnimation SWING_ANIMATION = new SwingAnimation();
    public static final CombatFx.TargetMarker MARKER = new CombatFx.TargetMarker();
    public static final CombatFx.HitMarker HITMARKER = new CombatFx.HitMarker();
    public static final ComboHelper COMBO = new ComboHelper();
    public static final CombatFx.SwingTrail SWING = new CombatFx.SwingTrail();
    public static final TargetHud TARGET = new TargetHud();
    public static final VisualFx.JumpCircles JUMP = new VisualFx.JumpCircles();
    public static final VisualFx.Footprints FEET = new VisualFx.Footprints();
    public static final Crosshair CROSSHAIR = new Crosshair();
    public static final VisualFx.HandView HANDS = new VisualFx.HandView();
    public static final MeleeSkins KNIVES = new MeleeSkins();
    public static final MeleeSkins GUNS = MeleeSkins.guns();
    public static final ItemZones FT_ITEMS = ItemZones.funTime();
    public static final net.arcanic.visuals.fx.FtHelper FT_HELPER = new net.arcanic.visuals.fx.FtHelper();
    public static final net.arcanic.visuals.helper.FtEvents FT_EVENTS = new net.arcanic.visuals.helper.FtEvents();
    public static final net.arcanic.visuals.fx.FtRegions FT_REGIONS = new net.arcanic.visuals.fx.FtRegions();
    public static final net.arcanic.visuals.fx.SeeInvisible SEE_INVISIBLE = new net.arcanic.visuals.fx.SeeInvisible();
    public static final net.arcanic.visuals.hud.Totems.Tracker TOTEM_TRACKER =
            new net.arcanic.visuals.hud.Totems.Tracker();
    public static final net.arcanic.visuals.hud.Totems.Bar TOTEM_BAR = new net.arcanic.visuals.hud.Totems.Bar();
    public static final net.arcanic.visuals.fx.Extras.HitColor HIT_COLOR = new net.arcanic.visuals.fx.Extras.HitColor();
    public static final net.arcanic.visuals.fx.Extras.HitBubble HIT_BUBBLE =
            new net.arcanic.visuals.fx.Extras.HitBubble();
    public static final net.arcanic.visuals.fx.Extras.BodyGlow BODY_GLOW = new net.arcanic.visuals.fx.Extras.BodyGlow();
    public static final net.arcanic.visuals.fx.Extras.Halo HALO = new net.arcanic.visuals.fx.Extras.Halo();
    public static final net.arcanic.visuals.fx.Extras.ChinaHat CHINA_HAT = new net.arcanic.visuals.fx.Extras.ChinaHat();
    public static final net.arcanic.visuals.fx.Extras.SelfName SELF_NAME = new net.arcanic.visuals.fx.Extras.SelfName();
    public static final net.arcanic.visuals.fx.Extras.WorldParticles WORLD_PARTICLES =
            new net.arcanic.visuals.fx.Extras.WorldParticles();
    public static final net.arcanic.visuals.hud.UiAnimations UI_ANIMATIONS = new net.arcanic.visuals.hud.UiAnimations();
    public static final net.arcanic.visuals.helper.ServerTools.AutoReconnect AUTO_RECONNECT =
            new net.arcanic.visuals.helper.ServerTools.AutoReconnect();
    public static final net.arcanic.visuals.helper.ServerTools.PickupLog PICKUP_LOG =
            new net.arcanic.visuals.helper.ServerTools.PickupLog();
    public static final net.arcanic.visuals.helper.ServerTools.LockSlot LOCK_SLOT =
            new net.arcanic.visuals.helper.ServerTools.LockSlot();
    public static final net.arcanic.visuals.helper.ServerTools.ElytraSwap ELYTRA_SWAP =
            new net.arcanic.visuals.helper.ServerTools.ElytraSwap();
    public static final net.arcanic.visuals.helper.ServerTools.RwHelper RW_HELPER =
            new net.arcanic.visuals.helper.ServerTools.RwHelper();
    public static final net.arcanic.visuals.helper.ChestSorter CHEST_SORTER =
            new net.arcanic.visuals.helper.ChestSorter();
    public static final Capes CAPES = new Capes();
    public static final HandShaders HAND_SHADER = new HandShaders();
    public static final Paint.Armor ARMOR_PAINT = new Paint.Armor();
    public static final Outline OUTLINE = new Outline();
    public static final SkyShader SKY_SHADER = new SkyShader();
    public static final CustomFog CUSTOM_FOG = new CustomFog();
    public static final CustomTime CUSTOM_TIME = new CustomTime();
    public static final Ambience AMBIENCE = new Ambience();
    public static final FloatingCrystals FLOATING_CRYSTALS = new FloatingCrystals();
    public static final HandGlow HAND_GLOW = new HandGlow();
    public static final BlockHighlight BLOCK = new BlockHighlight();
    public static final ArcVisuals.ProjectileTrails PROJECTILES = new ArcVisuals.ProjectileTrails();
    public static final Hitboxes HITBOXES = new Hitboxes();
    public static final ArcVisuals.NameTags NAMETAGS = new ArcVisuals.NameTags();
    public static final ArcVisuals.TNTTimer TNT = new ArcVisuals.TNTTimer();
    public static final ArcVisuals.ConsumeESP CONSUME = new ArcVisuals.ConsumeESP();
    public static final PlayerTrail TRAILS = new PlayerTrail();
    public static final Trajectories TRAJECTORIES = new Trajectories();
    public static final NoRender NO_RENDER = new NoRender();
    public static final LowFire LOW_FIRE = new LowFire();
    public static final WorldLyrics LYRICS = new WorldLyrics();
    public static final FastSwap FAST_SWAP = new FastSwap();
    public static final Friends FRIENDS = new Friends();
    public static final ItemZones HOLY_ITEMS = new ItemZones();
    public static final TrapZone HOLY_TRAP;
    public static final TrapZone HOLY_BLAST_TRAP;
    public static final TrapZone HOLY_STUN;
    public static final List<TrapZone> ZONES;
    public static final ExplosiveThing HOLY_THING;
    public static final TntZone HOLY_TNT;
    public static final List<Module> ITEM_PARTS;
    public static final Waypoints MARKS;
    public static final ChatCoords CHAT_COORDS;
    public static final HolyEvents HOLY_EVENTS;
    public static final EnchantSearch ENCHANT_SEARCH;
    public static final EnchantSearch FT_ENCHANT_SEARCH = EnchantSearch.funTime();
    public static final DrillHelper DRILL;
    public static final HolyEventsHud HOLY_EVENTS_HUD;
    public static final AspectRatio ASPECT;
    public static final MotionBlur MOTION_BLUR;
    public static final AutoSprint AUTO_SPRINT;
    public static final FreeLook FREE_LOOK;
    public static final ItemScroller ITEM_SCROLLER;
    public static final ClickPearl CLICK_PEARL;
    public static final ArcVisuals.Zoom ZOOM;
    public static final Emotes EMOTES;
    public static final Wearables WINGS;
    public static final Wearables BACKPACKS;
    public static final Wearables HEADWEAR;
    public static final KillSound KILLSOUND;
    public static final HitSound HITSOUND;
    public static final Optimizer OPTIMIZER;
    public static final Notify.Prefs NOTIFY;
    public static final ShulkerPreview SHULKER;
    public static final ChatTools.Helper CHAT_HELPER;
    public static final ChatTools.Safe COMMAND_SAFE;
    public static final ChatTools.Auction AUCTION;
    public static final CommandBinds COMMAND_BINDS;
    public static final SoundMixer SOUND_MIXER;
    public static final FakePlayer FAKE_PLAYER;
    public static final Kits KITS;
    public static final Overlays.Streamer STREAMER;
    public static final WorldPlus.ItemPhysics ITEM_PHYSICS;
    public static final WorldPlus.ChunkAnimator CHUNKS;
    public static final WorldPlus.HitRange HIT_RANGE;
    public static final WorldPlus.Fullbright FULLBRIGHT;
    public static final WorldPlus.Slipstream SLIPSTREAM;
    public static final WorldPlus.Crystals CRYSTALS;
    public static final ArcVisuals.ArrowTags ARROW_TAGS;
    public static final Survival.Saturation SATURATION;
    public static final Survival.ArmorAlert ARMOR_ALERT;
    public static final Overlays.TabList TAB;
    public static final Overlays.BossBars BOSS_BARS;
    public static final MenuPrefs PREFS;
    public static final ThemeStudio THEMES;
    public static final List<Module> ALL;
    public static final List<HudModule> HUD;
    private static final Set<String> UTILITIES;

    private Modules() {}

    public static Category section(Module m) {
        return UTILITIES.contains(m.id) ? Category.UTILITY : m.category.section();
    }

    /** Modules registered by Lua scripts while they run. */
    public static final List<Module> SCRIPTED = new ArrayList<>();

    public static List<Module> everything() {
        if (SCRIPTED.isEmpty()) return ALL;
        List<Module> out = new ArrayList<>(ALL);
        out.addAll(SCRIPTED);
        return out;
    }

    public static List<Module> of(Category c) {
        List<Module> out = new ArrayList<>();

        for (Module m : everything()) {
            if (section(m) == c && !HolyFeatures.hidden(m)) {
                out.add(m);
            }
        }

        if (c == Category.VISUALS) {
            out.sort(Comparator.comparingInt((mx) -> mx.category.ordinal()));
        }

        return out;
    }

    static {
        HOLY_TRAP = new TrapZone(HOLY_ITEMS, TrapZone.Kind.TRAP);
        HOLY_BLAST_TRAP = new TrapZone(HOLY_ITEMS, TrapZone.Kind.BLAST);
        HOLY_STUN = new TrapZone(HOLY_ITEMS, TrapZone.Kind.STUN);
        ZONES = List.of(HOLY_TRAP, HOLY_BLAST_TRAP, HOLY_STUN);
        HOLY_THING = new ExplosiveThing(HOLY_ITEMS);
        HOLY_TNT = new TntZone(HOLY_ITEMS);
        ITEM_PARTS = List.of(HOLY_TRAP, HOLY_BLAST_TRAP, HOLY_STUN, HOLY_THING, HOLY_TNT);
        MARKS = new Waypoints();
        CHAT_COORDS = new ChatCoords();
        HOLY_EVENTS = new HolyEvents();
        ENCHANT_SEARCH = new EnchantSearch();
        DRILL = new DrillHelper();
        HOLY_EVENTS_HUD = new HolyEventsHud();
        ASPECT = new AspectRatio();
        MOTION_BLUR = new MotionBlur();
        AUTO_SPRINT = new AutoSprint();
        FREE_LOOK = new FreeLook();
        ITEM_SCROLLER = new ItemScroller();
        CLICK_PEARL = new ClickPearl();
        ZOOM = new ArcVisuals.Zoom();
        EMOTES = new Emotes();
        WINGS = new Wearables(
                "wings",
                "Крылья",
                "Крылья за спиной в 3-м лице: дышат стоя, машут на ходу, бьют в прыжке и полёте, складываются, когда крадёшься. Видят и другие игроки с Arcanic.",
                "wings");
        BACKPACKS = new Wearables(
                "backpacks",
                "Рюкзаки",
                "Брендовый рюкзак на спине в 3-м лице: подпрыгивает на шагу, бегунки и брелоки качаются. Видят и другие игроки с Arcanic.",
                "bodywear");
        HEADWEAR = new Wearables(
                "headwear",
                "Головные уборы",
                "Головной убор в 3-м лице: зверёк на голове, шапка-моб, НЛО, туча с дождём, аквариум, медуза и другие — у каждого своя анимация. Видят и другие игроки с Arcanic.",
                "hat");
        KILLSOUND = new KillSound();
        HITSOUND = new HitSound();
        OPTIMIZER = new Optimizer();
        NOTIFY = new Notify.Prefs();
        SHULKER = new ShulkerPreview();
        CHAT_HELPER = new ChatTools.Helper();
        COMMAND_SAFE = new ChatTools.Safe();
        AUCTION = new ChatTools.Auction();
        COMMAND_BINDS = new CommandBinds();
        SOUND_MIXER = new SoundMixer();
        FAKE_PLAYER = new FakePlayer();
        KITS = new Kits();
        STREAMER = new Overlays.Streamer();
        ITEM_PHYSICS = new WorldPlus.ItemPhysics();
        CHUNKS = new WorldPlus.ChunkAnimator();
        HIT_RANGE = new WorldPlus.HitRange();
        FULLBRIGHT = new WorldPlus.Fullbright();
        SLIPSTREAM = new WorldPlus.Slipstream();
        CRYSTALS = new WorldPlus.Crystals();
        ARROW_TAGS = new ArcVisuals.ArrowTags();
        SATURATION = new Survival.Saturation();
        ARMOR_ALERT = new Survival.ArmorAlert();
        TAB = new Overlays.TabList();
        BOSS_BARS = new Overlays.BossBars();
        PREFS = new MenuPrefs();
        THEMES = new ThemeStudio();
        ALL = new ArrayList<>();
        HUD = new ArrayList<>();
        ALL.addAll(List.of(
                HIT,
                SWING,
                SWING_ANIMATION,
                DAMAGE,
                KILL,
                MACE,
                PROJECTILE_HIT,
                TOTEM,
                POTION_FX,
                MARKER,
                OUTLINE,
                FRIENDS,
                TRAILS,
                JUMP,
                FEET,
                ARMOR_PAINT,
                CROSSHAIR,
                COMBO,
                HANDS,
                HAND_SHADER,
                HAND_GLOW,
                KNIVES,
                GUNS,
                CAPES,
                WINGS,
                BACKPACKS,
                HEADWEAR,
                EMOTES));
        ALL.addAll(List.of(KILLSOUND, HITSOUND, OPTIMIZER, HIT_RANGE, SLIPSTREAM, ARROW_TAGS));
        ALL.addAll(
                List.of(FT_ITEMS, FT_EVENTS, FT_ENCHANT_SEARCH, FT_REGIONS, SEE_INVISIBLE, TOTEM_TRACKER, TOTEM_BAR));
        ALL.addAll(List.of(HIT_COLOR, HIT_BUBBLE, BODY_GLOW, HALO, CHINA_HAT, SELF_NAME, WORLD_PARTICLES));
        ALL.add(UI_ANIMATIONS);
        ALL.addAll(List.of(AUTO_RECONNECT, PICKUP_LOG, LOCK_SLOT, ELYTRA_SWAP, RW_HELPER, CHEST_SORTER));
        ALL.addAll(List.of(
                SKY_SHADER,
                CUSTOM_FOG,
                CUSTOM_TIME,
                AMBIENCE,
                NO_RENDER,
                LOW_FIRE,
                LYRICS,
                BLOCK,
                PROJECTILES,
                HITBOXES,
                NAMETAGS,
                TNT,
                CONSUME,
                TRAJECTORIES,
                HOLY_ITEMS,
                MARKS,
                CHAT_COORDS,
                HOLY_EVENTS,
                ENCHANT_SEARCH,
                DRILL,
                ZOOM,
                ASPECT,
                MOTION_BLUR,
                AUTO_SPRINT,
                FREE_LOOK,
                ITEM_SCROLLER,
                CLICK_PEARL,
                FAST_SWAP,
                ITEM_PHYSICS,
                CHUNKS,
                FULLBRIGHT,
                CRYSTALS,
                SHULKER,
                CHAT_HELPER,
                COMMAND_SAFE,
                AUCTION,
                COMMAND_BINDS,
                SOUND_MIXER,
                STREAMER,
                KITS,
                FAKE_PLAYER,
                SATURATION,
                ARMOR_ALERT,
                TAB));
        HUD.addAll(List.of(
                new DynamicIsland(),
                TARGET,
                new HudElements.Potions(),
                new HudElements.HotKeys(),
                new HudElements.Cooldowns(),
                new HudElements.Armor(),
                new HudElements.Coordinates(),
                new HudElements.Keystrokes(),
                HOLY_EVENTS_HUD,
                NOTIFY,
                new Survival.LowHealth(),
                new Survival.Inventory(),
                BOSS_BARS));
        ALL.addAll(HUD);

        for (Module m : ALL) {
            for (Setting<?> st : m.settings) {
                st.markDefault();
            }
        }

        for (Setting<?> st : PREFS.settings) {
            st.markDefault();
        }

        for (Setting<?> st : THEMES.settings) {
            st.markDefault();
        }

        UTILITIES = Set.of(
                "optimizer",
                "zoom",
                "free_look",
                "auto_sprint",
                "item_scroller",
                "click_pearl",
                "combohelper",
                "fast_swap");
    }
}
