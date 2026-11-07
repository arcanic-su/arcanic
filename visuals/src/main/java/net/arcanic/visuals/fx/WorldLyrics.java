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

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.fx.lyrics.LyricsFont;
import net.arcanic.visuals.fx.lyrics.LyricsRenderer;
import net.arcanic.visuals.fx.lyrics.SongClock;
import net.arcanic.visuals.fx.lyrics.WordPlane;
import net.arcanic.visuals.gui.PromptScreen;
import net.arcanic.visuals.hud.Toasts;
import net.arcanic.visuals.media.Lyrics;
import net.arcanic.visuals.media.Music;
import net.arcanic.visuals.media.MusicAccounts;
import net.arcanic.visuals.media.whisper.SystemAudio;
import net.arcanic.visuals.media.whisper.Whisper;
import net.arcanic.visuals.media.whisper.WhisperModel;
import net.arcanic.visuals.render.Px;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lyrics of the song playing on the PC, written into the world a word or two at a time, after EvoVisuals'
 * WorldLyrics. Each chunk lands on its own slightly turned sheet in a free spot of your view (or ahead on the left
 * and right of your path while you run), burns in word by word, fills with the accent while it is sung, bobs gently
 * and dissolves when the line is over or it leaves the screen.
 *
 * <p>Everything runs in the world pass ({@link WorldRenderEvents#END_MAIN}): the words are real geometry drawn with the
 * frame's camera and projection, so they stay put in the world with view bobbing and do not lag behind when you turn.
 * The HUD only shows the Whisper status line ({@link #render}).
 */
public final class WorldLyrics extends Module {
    private static final Logger LOG = LoggerFactory.getLogger("ArcanicLyrics");
    private static final int MAX_PHRASES = 10;
    private static final int MIN_EMPHASIS_LENGTH = 4;
    private static final float BASE_FONT_SIZE = 22.0F;
    private static final float EMPHASIS_SCALE = 1.4F;
    private static final float WORD_GAP_RATIO = 0.28F;
    private static final float WORD_GAP = BASE_FONT_SIZE * WORD_GAP_RATIO;
    private static final float EMPHASIS_LIFT_RATIO = 0.18F;
    private static final float WORLD_LINE_HEIGHT = 0.6F;
    private static final float REFERENCE_DISTANCE = 7.0F;
    private static final float MIN_DISTANCE = 1.8F;
    private static final float WALL_MARGIN = 0.6F;
    private static final double RUN_SPEED_PER_TICK = 0.24;
    private static final double MIN_HEADING_SPEED = 0.05;
    private static final double ROADSIDE_LEAD_SECONDS = 2.4;
    private static final double ROADSIDE_BASE_AHEAD = 4.0;
    private static final double ROADSIDE_MIN_AHEAD = 5.0;
    private static final double ROADSIDE_MAX_AHEAD = 28.0;
    private static final double ROADSIDE_LATERAL = 2.6;
    private static final float ROADSIDE_LATERAL_JITTER = 0.25F;
    private static final double ROADSIDE_MIN_LIFT = -0.4;
    private static final double ROADSIDE_MAX_LIFT = 1.1;
    private static final float ROADSIDE_TWIST_SCALE = 0.35F;
    private static final int MAX_CHUNK_WORDS = 2;
    private static final int MAX_CHUNK_CHARS = 12;
    private static final int VISIBLE_CHUNKS = 2;
    private static final long CHUNK_LEAD_MS = 220;
    private static final int MAX_FIT_ATTEMPTS = 8;
    private static final int FIT_PULL_ATTEMPTS = 4;
    private static final double FIT_PULL = 0.3;
    private static final float FIT_SHRINK = 0.82F;
    private static final float BOX_TOP = -BASE_FONT_SIZE * EMPHASIS_SCALE * 0.95F;
    private static final float BOX_BOTTOM = BASE_FONT_SIZE * 0.7F;
    private static final float SCREEN_MARGIN_X = 0.08F;
    private static final float SCREEN_MARGIN_Y = 0.1F;
    private static final float SCREEN_SLOT_JITTER = 0.04F;
    /** Spots of the screen (0..1) that chunks are spread over, pulled to the centre by the scatter. */
    private static final float[][] SCREEN_SLOTS = {
        {0.3F, 0.34F}, {0.7F, 0.3F}, {0.4F, 0.62F}, {0.66F, 0.56F}, {0.26F, 0.5F}, {0.54F, 0.4F}
    };

    private static final float MAX_PLANE_YAW = 28.0F;
    private static final float MAX_PLANE_PITCH = 12.0F;
    private static final float MAX_PLANE_ROLL = 7.0F;
    private static final Vector3fc WORLD_UP = new Vector3f(0.0F, 1.0F, 0.0F);
    private static final float DEPTH_JITTER = 0.25F;
    private static final float BOB_AMPLITUDE = 0.06F;
    private static final double BOB_PERIOD_MS = 900.0;
    private static final float REVEAL_WINDOW_RATIO = 0.7F;
    private static final long MAX_WORD_STEP_MS = 420;
    private static final long WORD_REVEAL_MS = 340;
    private static final long WORD_REVEAL_LEAD_MS = 160;
    private static final long LINE_LEAD_MS = 220;
    /** Without word stamps: one sung syllable takes about this long, and a line is sung over this share of it. */
    private static final long SYLLABLE_MS = 230;

    private static final float SUNG_SHARE = 0.86F;
    private static final String VOWELS = "aeiouyаеёиоуыэюяáéíóúàèìòùâêîôûäëïöüæøåœ";
    /** Motes: how many leave a dissolving word, how many a second rise from the edge of the fill, and the cap. */
    private static final int MOTES_PER_WORD = 6;

    private static final float SUNG_MOTES_PER_S = 14.0F;
    private static final int MAX_MOTES = 420;
    /** Below this size the window is minimised; a frame gap longer than this means the game was away. */
    private static final int MIN_TARGET_PX = 64;

    private static final long AWAY_NS = 750_000_000L;
    private static final long LEAVE_MS = 900;
    private static final long HOLD_AFTER_LINE_MS = 350;
    private static final long MAX_LAST_WORD_MS = 900;
    private static final long MIN_WORD_MS = 120;
    private static final long EMPTY_TRACK_GRACE_NS = 3_000_000_000L;
    private static final float GLOW_RADIUS = 1.6F;
    private static final float GLOW_STRENGTH = 0.55F;
    private static final float SHADOW_STRENGTH = 0.8F;
    private static final int QUIET_BLOOM_ALPHA = 140;
    private static final int SUNG_BLOOM_ALPHA = 215;
    /** How long a word's glow takes to swell when it starts to be sung, and to settle after. */
    private static final float SING_GLOW_IN_MS = 160.0F;

    private static final float SING_GLOW_OUT_MS = 320.0F;
    /** Placement: spots tried for a new chunk before settling for the one that overlaps least. */
    private static final int PLACEMENT_TRIES = 12;

    private static final int ROADSIDE_TRIES = 4;
    /** Gap kept around a chunk on screen when placing the next one, as a share of the screen width. */
    private static final float BOX_PADDING = 0.02F;
    /** A chunk mostly off screen leaves (Evo: 250 ms the moment a corner crosses the edge; softer here). */
    private static final float OFFSCREEN_VISIBLE_SHARE = 0.5F;

    private static final long OFFSCREEN_LEAVE_MS = 600;
    /** Two standing chunks drifting over each other by more than this share: the older one leaves. */
    private static final float OVERLAP_SHARE = 0.2F;
    /** A chunk in the way of a newly placed one leaves this fast. */
    private static final long MAKE_ROOM_LEAVE_MS = 600;
    /** Leaving chunks past this share of their dissolve no longer block new ones. */
    private static final float LEAVING_BLOCKS_UNTIL = 0.5F;
    /** The shader clock wraps once an hour, where all its waves line up again. */
    private static final double SHADER_TIME_PERIOD_S = 3600.0;

    private static final long MIN_LIVE_LINE_MS = 2200;
    private static final int MIN_TRANSCRIPT_LINES = 4;
    private static final float STATUS_FONT_SIZE = 8.0F;
    private static final float STATUS_BOTTOM_OFFSET = 62.0F;
    private static final int STATUS_COLOUR = 0xD0FFFFFF;
    private static final String SOURCE_AUTO = "Авто";
    private static final String SOURCE_YANDEX = "Яндекс Музыка";
    private static final String SOURCE_SPOTIFY = "Spotify";
    private static final String LANGUAGE_AUTO = "Авто";
    private static final String LANGUAGE_RUSSIAN = "Русский";
    private static final String LANGUAGE_ENGLISH = "Английский";

    public final Setting.Num distance = add(new Setting.Num("distance", "Расстояние", 7.0, 3.0, 14.0, 0.5, " бл"));
    public final Setting.Num size = add(new Setting.Num("size", "Размер", 1.0, 0.5, 2.0, 0.05, "×"));
    public final Setting.Num scatter = add(new Setting.Num("scatter", "Разброс", 70.0, 0.0, 100.0, 5.0, "%"));
    public final Setting.Num offset = add(new Setting.Num("offset", "Сдвиг текста", 0.0, -1500.0, 1500.0, 50.0, " мс"));
    /** Where the lyrics come from first: the free sources, or the player's own Yandex Music or Spotify. */
    public final Setting.Mode source =
            add(new Setting.Mode("source", "Откуда текст", SOURCE_AUTO, SOURCE_AUTO, SOURCE_YANDEX, SOURCE_SPOTIFY));

    public final Setting.Action yandexAccount = add(new Setting.Action(
            "yandex_account",
            "Яндекс Музыка",
            () -> MusicAccounts.yandex()
                    ? "Выйти" + (MusicAccounts.yandexName().isEmpty() ? "" : " · " + MusicAccounts.yandexName())
                    : (MusicAccounts.waitingYandex() ? "Жду адрес из буфера…" : "Войти"),
            MusicAccounts::yandexButton));
    public final Setting.Action spotifyAccount = add(new Setting.Action(
            "spotify_account",
            "Spotify",
            () -> MusicAccounts.spotify()
                    ? "Выйти" + (MusicAccounts.spotifyName().isEmpty() ? "" : " · " + MusicAccounts.spotifyName())
                    : (MusicAccounts.waitingSpotify() ? "Жду sp_dc из буфера…" : "Войти"),
            MusicAccounts::spotifyButton));
    public final Setting.Action pasteAccount = add(new Setting.Action(
                    "paste_account", "Вставить токен вручную", "Вставить", WorldLyrics::pasteByHand))
            .showIf(() -> !MusicAccounts.yandex() || !MusicAccounts.spotify());
    public final Setting.Bool roadside = add(new Setting.Bool("roadside", "Вдоль дороги", true));
    public final Setting.Bool glow = add(new Setting.Bool("glow", "Свечение", true));
    public final Setting.Num glowStrength = add(new Setting.Num(
                    "glow_strength", "Сила свечения", 85.0, 10.0, 100.0, 5.0, "%"))
            .showIf(glow::on);
    public final Setting.Bool shadow = add(new Setting.Bool("shadow", "Тень", true));
    public final Setting.Bool sparks = add(new Setting.Bool("motes", "Искры", true));
    public final Setting.Bool recognize = add(new Setting.Bool("recognize", "Распознавать (Whisper)", false));
    public final Setting.Mode model = add(new Setting.Mode(
                    "whisper_model",
                    "Модель Whisper",
                    WhisperModel.BASE.label,
                    WhisperModel.BASE.label,
                    WhisperModel.SMALL.label))
            .showIf(recognize::on);
    public final Setting.Mode language = add(new Setting.Mode(
                    "language", "Язык", LANGUAGE_AUTO, LANGUAGE_AUTO, LANGUAGE_RUSSIAN, LANGUAGE_ENGLISH))
            .showIf(recognize::on);
    public final Setting.Colour textColour = add(new Setting.Colour("text_color", "Цвет текста", false, 0xFFFFFFFF));
    public final Setting.Colour accent = add(new Setting.Colour("accent", "Акцент", false, 0xFF9660FF));

    /** One word of a line: when it shows, how long it is sung, whether it is the line's big word, its set width. */
    private static final class Word {
        final String text;
        final long revealAt;
        final long sungMs;
        final boolean emphasized;
        final float seed;
        /** Whether it has shown at all, and the sparks owed by its fill, for the motes. */
        boolean seen;

        float owed;
        final LyricsFont font;
        final float size;
        final float width;

        Word(String text, long revealAt, long sungMs, boolean emphasized, float seed) {
            this.text = text;
            this.revealAt = revealAt;
            this.sungMs = sungMs;
            this.emphasized = emphasized;
            this.seed = seed;
            this.font = emphasized ? LyricsFont.BOLD : LyricsFont.MEDIUM;
            this.size = emphasized ? BASE_FONT_SIZE * EMPHASIS_SCALE : BASE_FONT_SIZE;
            this.width = font.width(text, size);
        }
    }

    /** A mote of light drifting up from a word: where it is, how it moves, how long it lives. */
    private static final class Mote {
        double x, y, z;
        float vx, vy, vz;
        final float life, radius, seed;
        final int colour;
        float age;

        Mote(
                double x,
                double y,
                double z,
                float vx,
                float vy,
                float vz,
                float life,
                float radius,
                int colour,
                float seed) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.life = life;
            this.radius = radius;
            this.colour = colour;
            this.seed = seed;
        }
    }

    /** A run of up to two words waiting for its time to appear. */
    private record Chunk(Word[] words, long lineEnd) {}

    /** A chunk placed in the world on its own sheet. */
    private static final class Phrase {
        final Vec3 anchor;
        final Vector3f right;
        final Vector3f down;
        final Word[] words;
        final long endMs;
        final double bobPhase;
        final float half;
        final float[] box = new float[4];
        long leaveStartNs = -1;
        long leaveMs = LEAVE_MS;

        Phrase(Vec3 anchor, Vector3f right, Vector3f down, Word[] words, long endMs, double bobPhase) {
            this.anchor = anchor;
            this.right = right;
            this.down = down;
            this.words = words;
            this.endMs = endMs;
            this.bobPhase = bobPhase;
            this.half = groupWidth(words) * 0.5F;
        }

        boolean leaving() {
            return leaveStartNs >= 0;
        }
    }

    private final List<Phrase> phrases = new ArrayList<>(MAX_PHRASES + 1);
    private final List<Chunk> pending = new ArrayList<>();
    private final List<Mote> motes = new ArrayList<>();
    private long lastFrameNs;
    private boolean returning;
    private float frameDt;
    /** The sheet the lines are counted in: a better one (word timing) may replace it mid-song. */
    private Lyrics.Sheet shownSheet;

    private final Random random = new Random();
    private final LyricsRenderer renderer = new LyricsRenderer();
    private final WordPlane view = new WordPlane();
    private final SongClock clock = new SongClock();
    private final long epochNs = System.nanoTime();
    private final Vector3f fitRight = new Vector3f();
    private final Vector3f fitDown = new Vector3f();
    private final float[] fitScreen = new float[2];
    private final float[] boxA = new float[4];
    private final float[] boxB = new float[4];
    private int lastScreenSlot = -1;
    private String trackKey = "";
    private int lineIndex = -1;
    private int roadsideCounter;
    private long emptySinceNs = -1;
    /** What Whisper heard of the current song, saved as its lyrics when the song changes. */
    private final List<Lyrics.Line> transcript = new ArrayList<>();

    private long lastSegment;
    private boolean listening;
    /** Whether recognition is running this frame, for the status line on the HUD. */
    private volatile boolean hearing;

    private boolean failed;

    public WorldLyrics() {
        super(
                "world_lyrics",
                "Текст песни в мире",
                "Строки песни, которая играет у тебя на ПК, появляются в мире по словам: проявляются, заливаются цветом в такт и рассыпаются. Текст ищется на lrclib.net, YouTube Music и NetEase или берётся прямо из твоей Яндекс Музыки или Spotify, а если его нигде нет, слова может распознать Whisper.",
                Category.WORLD,
                false);
        // after the level (translucent blocks, particles, weather) and before the hand
        WorldRenderEvents.END_MAIN.register(ctx -> renderWorld());
    }

    @Override
    public void tick() {
        Music.request();
        MusicAccounts.prefer(
                source.is(SOURCE_YANDEX)
                        ? MusicAccounts.Source.YANDEX
                        : (source.is(SOURCE_SPOTIFY) ? MusicAccounts.Source.SPOTIFY : MusicAccounts.Source.AUTO));
    }

    /** A window to paste the address after the Yandex sign-in, a Yandex token or the Spotify sp_dc cookie. */
    private static void pasteByHand() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(PromptScreen.field(
                        mc.screen,
                        "Токен музыки",
                        "Адрес после входа в Яндекс или sp_dc из Spotify",
                        "Подключить",
                        text -> {
                            if (!MusicAccounts.paste(text)) {
                                UiSound.play("reset");
                                Toasts.setting(Lang.tr("Это не токен Яндекса и не sp_dc Spotify"), false);
                            }
                        })
                .limit(2000));
    }

    @Override
    protected void onDisable() {
        flushTranscript();
        stopListening();
        hearing = false;
        phrases.clear();
        pending.clear();
        motes.clear();
        shownSheet = null;
        trackKey = "";
        lineIndex = -1;
    }

    /** HUD part: only the Whisper status line, while recognition is running. */
    public void render(GuiGraphics g) {
        if (hearing) renderStatus(g); // HudRenderer already works in frame pixels (Px.begin)
    }

    private void renderWorld() {
        if (!enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Camera cam = mc.gameRenderer.getMainCamera();
        if (cam == null || !cam.isInitialized()) return;
        try {
            frame(mc, cam);
        } catch (RuntimeException e) {
            if (!failed) LOG.error("World lyrics frame failed", e);
            failed = true;
        }
    }

    private void frame(Minecraft mc, Camera cam) {
        Music.request();
        Music.Track track = Music.track();
        long now = System.nanoTime();
        // A minimised window renders into a target a few pixels wide: placing words or judging what is on screen
        // against it would throw them all out or squash them. Nothing is done until the window is back.
        RenderTarget target = mc.getMainRenderTarget();
        if (mc.getWindow().isMinimized() || target.width < MIN_TARGET_PX || target.height < MIN_TARGET_PX) {
            lastFrameNs = 0;
            return;
        }
        // back from a minimised or frozen window: the motes of then are long gone, and what ran out meanwhile leaves
        // quietly instead of bursting all at once
        returning = lastFrameNs == 0 || now - lastFrameNs > AWAY_NS;
        if (returning) motes.clear();
        frameDt = lastFrameNs == 0 ? 0.0F : Math.min(0.1F, (now - lastFrameNs) / 1.0E9F);
        lastFrameNs = now;
        long song = clock.update(track.present() ? track.key() : "", track.playing(), Music.position(), now);
        long pos = song + Math.round(offset.get());
        view.frame(cam, mc.getMainRenderTarget().width, mc.getMainRenderTarget().height);

        Lyrics.Result found = Lyrics.of(track);
        Lyrics.Sheet sheet = found != null && found.found() ? found.sheet() : null;
        if (sheet != null && sheet != shownSheet) {
            // the same song's lines again, now with word timing: carry on from where we are, without a restart
            if (shownSheet != null && !sheet.empty()) {
                int at = sheet.at(pos + LINE_LEAD_MS);
                lineIndex = at >= 0 ? at : sheet.before(pos + LINE_LEAD_MS);
            }
            shownSheet = sheet;
        }
        if (clock.jumped()) rewind(sheet, pos, now);
        advance(track, sheet, pos, now);
        boolean hear = found != null && !found.found() && recognize.on() && track.present() && track.playing();
        hearing = hear;
        if (hear) recognize(track, pos, now);
        else if (!recognize.on()) stopListening();
        releaseChunks(pos, now);
        if (!phrases.isEmpty() || !motes.isEmpty()) draw(cam, pos, now);
    }

    // ---- timing ----

    private void advance(Music.Track track, Lyrics.Sheet sheet, long pos, long now) {
        String key = track.present() ? track.key() : "";
        if (key.isEmpty() && !trackKey.isEmpty()) {
            // The media session blinks empty between some tracks: hold the old one for a moment.
            if (emptySinceNs < 0) emptySinceNs = now;
            if (now - emptySinceNs < EMPTY_TRACK_GRACE_NS) key = trackKey;
        } else {
            emptySinceNs = -1;
        }
        if (!key.equals(trackKey)) {
            flushTranscript();
            trackKey = key;
            shownSheet = null;
            lineIndex = -1;
            pending.clear();
            leaveAll(now);
        }

        if (sheet != null && !sheet.empty() && track.present()) {
            int index = sheet.at(pos + LINE_LEAD_MS);
            // Only forward: a position that wobbles back a little must not start the same line twice.
            if (index > lineIndex) {
                startLine(sheet.lines().get(index));
                lineIndex = index;
            }
        }

        for (int i = phrases.size() - 1; i >= 0; i--) {
            Phrase p = phrases.get(i);
            if (!p.leaving() && pos > p.endMs + HOLD_AFTER_LINE_MS) leave(p, now, LEAVE_MS, true);
            if (p.leaving() && leaveProgress(p, now) >= 1.0F) phrases.remove(i);
        }
    }

    /**
     * The clock moved at once (a seek, a pause or resume, a correction). A jump that stays on the line being shown
     * keeps it as it is: restarting it would dissolve the words on screen and burn the same ones in again. A jump to
     * another line drops what was queued for the old one, and a jump back lets the earlier line start over.
     */
    private void rewind(Lyrics.Sheet sheet, long pos, long now) {
        if (sheet == null || sheet.empty()) return;
        int at = sheet.before(pos + LINE_LEAD_MS);
        if (at == lineIndex) return;
        pending.clear();
        if (at < lineIndex) {
            lineIndex = sheet.at(pos + LINE_LEAD_MS) >= 0 ? at - 1 : at;
            leaveAll(now);
        }
    }

    /** Sends a chunk away over {@code ms}; a visible one breaks into motes that float up from its letters. */
    private void leave(Phrase p, long now, long ms, boolean burst) {
        p.leaveStartNs = now;
        p.leaveMs = ms;
        if (!burst || returning || !sparks.on()) return;
        float x = -p.half;
        int accentColour = accent.argb(), text = textColour.argb();
        for (Word w : p.words) {
            if (w.seen) {
                int n = MOTES_PER_WORD + Math.min(8, w.text.length() / 2);
                for (int i = 0; i < n; i++) {
                    float u = x + random.nextFloat() * w.width;
                    float v = w.size * (-0.5F + random.nextFloat() * 0.65F);
                    float speed = 0.35F + random.nextFloat() * 0.45F;
                    spawn(
                            p,
                            u,
                            v,
                            w.size,
                            speed,
                            1.1F + random.nextFloat() * 0.9F,
                            1.0F,
                            random.nextFloat() < 0.72F ? accentColour : text);
                }
            }
            x += w.width + WORD_GAP;
        }
    }

    /** A mote at sheet point (u, v) of a chunk, rising at {@code speed} blocks a second with a little sideways drift. */
    private void spawn(Phrase p, float u, float v, float glyph, float speed, float life, float scale, int colour) {
        if (motes.size() >= MAX_MOTES) motes.remove(0);
        Vector3f r = p.right, d = p.down;
        double x = p.anchor.x + r.x * u + d.x * v,
                y = p.anchor.y + r.y * u + d.y * v,
                z = p.anchor.z + r.z * u + d.z * v;
        float unit = r.length();
        float side = signedRandom() * 0.18F;
        float radius = glyph * unit * (0.07F + random.nextFloat() * 0.06F) * scale;
        motes.add(new Mote(
                x, y, z, r.x / unit * side, speed, r.z / unit * side, life, radius, colour, random.nextFloat()));
    }

    private void leaveAll(long now) {
        for (Phrase p : phrases) if (!p.leaving()) leave(p, now, LEAVE_MS, true);
    }

    /** Splits a line into timed words, picks its longest word to stress and queues it in chunks of up to two. */
    private void startLine(Lyrics.Line line) {
        String[] parts = line.text().strip().split("\\s+");
        if (parts.length == 0) return;
        int chars = 0, emphasis = -1, longest = MIN_EMPHASIS_LENGTH - 1;
        for (int i = 0; i < parts.length; i++) {
            chars += parts[i].length();
            if (parts[i].length() > longest) {
                longest = parts[i].length();
                emphasis = i;
            }
        }

        Word[] words = new Word[parts.length];
        long[] stamps = line.worded() && line.words().length == parts.length ? line.words() : null;
        if (stamps != null) {
            // the source times every word: each one is sung from its stamp to the next
            for (int i = 0; i < parts.length; i++) {
                long at = stamps[i];
                long end = i + 1 < parts.length ? stamps[i + 1] : Math.min(line.end(), at + MAX_LAST_WORD_MS);
                words[i] = new Word(parts[i], at, Math.max(end - at, MIN_WORD_MS), i == emphasis, random.nextFloat());
            }
        } else {
            // only the line's start is known: spread the words over it by their syllables, at a singing pace
            int[] syllables = new int[parts.length];
            int total = 0;
            for (int i = 0; i < parts.length; i++) total += syllables[i] = syllables(parts[i]);
            long window = Math.min((long) (line.duration() * SUNG_SHARE), SYLLABLE_MS * total + 120);
            int before = 0;
            for (int i = 0; i < parts.length; i++) {
                long at = line.start() + window * before / Math.max(total, 1);
                before += syllables[i];
                long end = i + 1 < parts.length
                        ? line.start() + window * before / Math.max(total, 1)
                        : Math.min(line.end(), at + MAX_LAST_WORD_MS);
                words[i] = new Word(parts[i], at, Math.max(end - at, MIN_WORD_MS), i == emphasis, random.nextFloat());
            }
        }

        pending.clear();
        int start = 0, run = 0;
        for (int i = 0; i < words.length; i++) {
            run += words[i].text.length();
            boolean fits = i - start + 1 < MAX_CHUNK_WORDS
                    && run < MAX_CHUNK_CHARS
                    && (i + 1 >= words.length || run + words[i + 1].text.length() <= MAX_CHUNK_CHARS);
            if (!fits || i == words.length - 1) {
                pending.add(new Chunk(Arrays.copyOfRange(words, start, i + 1), line.end()));
                start = i + 1;
                run = 0;
            }
        }
    }

    /** Syllables in a word: its vowels (a run of Latin vowels counts once), at least one. */
    private static int syllables(String word) {
        int n = 0;
        boolean run = false;
        for (int i = 0; i < word.length(); i++) {
            char c = Character.toLowerCase(word.charAt(i));
            boolean vowel = VOWELS.indexOf(c) >= 0;
            boolean cyrillic = c >= 0x0400 && c <= 0x04FF;
            if (vowel && (!run || cyrillic)) n++;
            run = vowel;
        }
        return Math.max(1, n);
    }

    /** Places every chunk whose first word is due, sending all but the newest standing chunk away. */
    private static boolean due(Chunk chunk, long pos) {
        return chunk.words()[0].revealAt - CHUNK_LEAD_MS <= pos;
    }

    private void releaseChunks(long pos, long now) {
        while (!pending.isEmpty() && due(pending.get(0), pos)) {
            Chunk chunk = pending.remove(0);
            // several due at once (the game was away or stalled): only the last is still worth showing, and one
            // already sung through is not shown at all, rather than all of them flashing up together
            if (!pending.isEmpty() && due(pending.get(0), pos)) continue;
            Word last = chunk.words()[chunk.words().length - 1];
            if (pos > last.revealAt + last.sungMs + HOLD_AFTER_LINE_MS) continue;
            int standing = 0;
            for (int i = phrases.size() - 1; i >= 0; i--) {
                Phrase p = phrases.get(i);
                if (!p.leaving() && ++standing >= VISIBLE_CHUNKS) leave(p, now, LEAVE_MS, true);
            }
            spawnChunk(chunk, now);
        }
    }

    // ---- recognition ----

    /** Feeds what Whisper heard into the world as lines, and keeps them for the transcript. */
    private void recognize(Music.Track track, long pos, long now) {
        if (!SystemAudio.supported()) return;
        listening = true;
        Whisper.request(track.key(), WhisperModel.byLabel(model.get()), languageCode());
        Whisper.Segment seg;
        while ((seg = Whisper.poll(lastSegment)) != null) {
            lastSegment = seg.sequence();
            if (!seg.trackKey().equals(trackKey)) continue;
            // the song time the phrase was heard at, from how long ago it was heard
            long start = pos - (long) ((now - seg.startNanos()) / 1_000_000.0);
            long end = pos - (long) ((now - seg.endNanos()) / 1_000_000.0);
            if (start >= 0) transcript.add(new Lyrics.Line(start, Math.max(end, start + 1), seg.text()));
            long duration = Math.max(end - start, MIN_LIVE_LINE_MS);
            startLine(new Lyrics.Line(pos, pos + duration, seg.text()));
        }
    }

    private void stopListening() {
        if (!listening) return;
        listening = false;
        Whisper.stop();
    }

    private String languageCode() {
        if (language.is(LANGUAGE_RUSSIAN)) return "ru";
        if (language.is(LANGUAGE_ENGLISH)) return "en";
        return "auto";
    }

    /** Saves a long enough transcript as the song's lyrics, each line ending where the next begins. */
    private void flushTranscript() {
        if (transcript.size() >= MIN_TRANSCRIPT_LINES && !trackKey.isEmpty()) {
            List<Lyrics.Line> sorted = new ArrayList<>(transcript);
            sorted.sort((a, b) -> Long.compare(a.start(), b.start()));
            List<Lyrics.Line> lines = new ArrayList<>(sorted.size());
            for (int i = 0; i < sorted.size(); i++) {
                Lyrics.Line l = sorted.get(i);
                long end = i + 1 < sorted.size()
                        ? Math.min(l.end(), sorted.get(i + 1).start())
                        : l.end();
                if (end > l.start()) lines.add(new Lyrics.Line(l.start(), end, l.text()));
            }
            Lyrics.storeTranscript(trackKey, new Lyrics.Sheet(List.copyOf(lines)));
        }
        transcript.clear();
    }

    /** One line over the hotbar while Whisper downloads, loads or cannot run. */
    private void renderStatus(GuiGraphics g) {
        String status;
        if (!SystemAudio.supported()) {
            status = Lang.tr("Распознавание работает только на Windows");
        } else {
            status = switch (Whisper.state()) {
                case DOWNLOADING ->
                    String.format(
                            Lang.tr("Whisper: скачивание модели %d%%"),
                            Math.round(Whisper.downloadProgress() * 100.0F));
                case LOADING -> Lang.tr("Whisper: загрузка модели");
                case FAILED -> Lang.tr("Whisper не запустился, подробности в логе");
                case UNSUPPORTED_CPU -> Lang.tr("Для распознавания нужен процессор с AVX2");
                default -> null;
            };
        }
        if (status == null) return;
        int scale = Px.guiScale();
        int px = Math.round(STATUS_FONT_SIZE * scale);
        int x = (Px.width() - Px.textWidth(status, Px.Weight.MEDIUM, px)) / 2;
        int y = Px.height() - Math.round(STATUS_BOTTOM_OFFSET * scale);
        Px.text(g, status, x, y, Px.Weight.MEDIUM, px, STATUS_COLOUR);
    }

    // ---- placement ----

    /** A candidate spot for a chunk: its sheet (in sheet units) and its padded box on screen. */
    private record Placement(Vec3 anchor, Vector3f right, Vector3f down, float[] box) {}

    /**
     * Places a chunk where it does not cover anything still standing: Evo's spot (a screen slot, or the roadside
     * while running) first, then the other slots and a few free spots, keeping the one that overlaps least. Anything
     * still in the way of the chosen spot makes room by leaving.
     */
    private void spawnChunk(Chunk chunk, long now) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        float spread = scatter.f() / 100.0F;
        Vec3 eye = cam.position();
        Vec3 motion = roadside.on() ? horizontalMotion(mc.player) : null;
        float half = groupWidth(chunk.words()) * 0.5F;
        List<float[]> taken = standingBoxes(now);
        int side = roadsideCounter++;
        boolean[] tried = new boolean[SCREEN_SLOTS.length];

        Placement best = null;
        float bestOverlap = Float.MAX_VALUE;
        for (int attempt = 0; attempt < PLACEMENT_TRIES; attempt++) {
            Placement p;
            if (motion != null && attempt < ROADSIDE_TRIES) {
                p = roadside(mc, cam, eye, motion, (side + attempt) % 2 == 0 ? -1.0F : 1.0F, attempt, spread, half);
            } else {
                float[] at = nextScreenSlot(spread, tried);
                if (at == null) at = freeSpot();
                float dist = distance.f() * (1.0F + signedRandom() * DEPTH_JITTER * spread);
                p = place(mc, cam, eye, view.direction(at[0], at[1]), dist, spread, half);
            }
            if (p == null) continue;
            float o = overlap(p.box(), taken);
            if (o < bestOverlap) {
                best = p;
                bestOverlap = o;
            }
            if (o <= 0) break;
        }
        if (best == null) return;

        if (bestOverlap > 0) {
            for (Phrase s : phrases) {
                if (s.leaving() || !boxOf(s, BOX_PADDING, boxA)) continue;
                if (intersection(boxA, best.box()) > 0) {
                    leave(s, now, MAKE_ROOM_LEAVE_MS, true);
                }
            }
        }
        phrases.add(new Phrase(
                best.anchor(),
                best.right(),
                best.down(),
                chunk.words(),
                chunk.lineEnd(),
                random.nextFloat() * Math.PI * 2));
        while (phrases.size() > MAX_PHRASES) phrases.remove(0);
    }

    /** Running: ahead of where you will be in a couple of seconds, left or right of the path; retries vary it. */
    private Placement roadside(
            Minecraft mc, Camera cam, Vec3 eye, Vec3 motion, float side, int attempt, float spread, float half) {
        double speed = motion.length();
        Vec3 heading = motion.normalize();
        Vec3 lateral = new Vec3(-heading.z, 0.0, heading.x).scale(side);
        double ahead = Mth.clamp(
                speed * 20.0 * ROADSIDE_LEAD_SECONDS + ROADSIDE_BASE_AHEAD, ROADSIDE_MIN_AHEAD, ROADSIDE_MAX_AHEAD);
        if (attempt >= 2) {
            ahead = Mth.clamp(ahead * (attempt == 2 ? 1.35 : 0.75), ROADSIDE_MIN_AHEAD, ROADSIDE_MAX_AHEAD);
        }
        double across = ROADSIDE_LATERAL * (1.0 + signedRandom() * ROADSIDE_LATERAL_JITTER);
        double lift = Mth.lerp(random.nextFloat(), ROADSIDE_MIN_LIFT, ROADSIDE_MAX_LIFT);
        Vec3 at = heading.scale(ahead).add(lateral.scale(across)).add(0.0, lift, 0.0);
        return place(mc, cam, eye, at.normalize(), (float) at.length(), spread * ROADSIDE_TWIST_SCALE, half);
    }

    /** Evo's sheet for a direction: kept clear of walls, turned a little, pulled in or shrunk until it fits. */
    private Placement place(Minecraft mc, Camera cam, Vec3 eye, Vec3 dir, float dist, float twist, float half) {
        float clear = clearDistance(mc, eye, dir, dist);
        float unit = WORLD_LINE_HEIGHT * size.f() * (clear / REFERENCE_DISTANCE) / BASE_FONT_SIZE;
        Vector3f forward = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
        Vector3f right = new Vector3f(forward).cross(WORLD_UP);
        if (right.lengthSquared() < 1.0E-4F) right.set(1.0F, 0.0F, 0.0F);
        right.normalize();
        Vector3f down = new Vector3f(forward).cross(right).normalize();
        Quaternionf turn = new Quaternionf()
                .rotateAxis(signedRandom() * MAX_PLANE_YAW * twist * Mth.DEG_TO_RAD, WORLD_UP)
                .rotateAxis(signedRandom() * MAX_PLANE_PITCH * twist * Mth.DEG_TO_RAD, right)
                .rotateAxis(signedRandom() * MAX_PLANE_ROLL * twist * Mth.DEG_TO_RAD, forward);
        Vector3f r = turn.transform(new Vector3f(right));
        Vector3f d = turn.transform(new Vector3f(down));
        Vec3 anchor = eye.add(dir.scale(clear));
        Vec3 centre = eye.add(Vec3.directionFromRotation(cam.xRot(), cam.yRot()).scale(clear));

        // Pull the sheet towards the middle of the view until it fits, then shrink it.
        for (int i = 0; i < MAX_FIT_ATTEMPTS && !fitsOnScreen(anchor, r, d, unit, half); i++) {
            if (i < FIT_PULL_ATTEMPTS) anchor = anchor.lerp(centre, FIT_PULL);
            else unit *= FIT_SHRINK;
        }
        r.mul(unit);
        d.mul(unit);
        view.set(anchor, r, d);
        float[] box = new float[4];
        return box(half, BOX_PADDING, box) ? new Placement(anchor, r, d, box) : null;
    }

    /** A spot anywhere in the middle of the view, for when every slot is taken. */
    private float[] freeSpot() {
        return new float[] {0.5F + signedRandom() * 0.3F, 0.47F + signedRandom() * 0.22F};
    }

    /** Padded screen boxes of every chunk that still counts as standing. */
    private List<float[]> standingBoxes(long now) {
        List<float[]> out = new ArrayList<>();
        for (Phrase p : phrases) {
            if (p.leaving() && leaveProgress(p, now) > LEAVING_BLOCKS_UNTIL) continue;
            float[] b = new float[4];
            if (boxOf(p, BOX_PADDING, b)) out.add(b);
        }
        return out;
    }

    /** A chunk's screen box (without its bob), padded by a share of the width; false when it is behind you. */
    private boolean boxOf(Phrase p, float pad, float[] out) {
        view.set(p.anchor, p.right, p.down);
        return box(p.half, pad, out);
    }

    /** The screen box of a chunk of half width {@code half} on the current sheet; false if behind you. */
    private boolean box(float half, float pad, float[] out) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int c = 0; c < 4; c++) {
            if (!view.project((c & 1) == 0 ? -half : half, c < 2 ? BOX_TOP : BOX_BOTTOM, fitScreen)) return false;
            minX = Math.min(minX, fitScreen[0]);
            maxX = Math.max(maxX, fitScreen[0]);
            minY = Math.min(minY, fitScreen[1]);
            maxY = Math.max(maxY, fitScreen[1]);
        }
        float px = view.width() * pad;
        out[0] = minX - px;
        out[1] = minY - px;
        out[2] = maxX + px;
        out[3] = maxY + px;
        return true;
    }

    private static float area(float[] b) {
        return Math.max(0, b[2] - b[0]) * Math.max(0, b[3] - b[1]);
    }

    private static float intersection(float[] a, float[] b) {
        float w = Math.min(a[2], b[2]) - Math.max(a[0], b[0]);
        float h = Math.min(a[3], b[3]) - Math.max(a[1], b[1]);
        return w > 0 && h > 0 ? w * h : 0;
    }

    private static float overlap(float[] box, List<float[]> taken) {
        float sum = 0;
        for (float[] t : taken) sum += intersection(box, t);
        return sum;
    }

    /** A slot not tried yet for this chunk (the last used one only when nothing else is left); null when none. */
    private float[] nextScreenSlot(float spread, boolean[] tried) {
        int free = 0;
        for (boolean t : tried) if (!t) free++;
        if (free == 0) return null;
        int slot;
        do {
            slot = random.nextInt(SCREEN_SLOTS.length);
        } while (tried[slot] || slot == lastScreenSlot && free > 1);
        tried[slot] = true;
        lastScreenSlot = slot;
        return new float[] {
            0.5F + (SCREEN_SLOTS[slot][0] - 0.5F) * spread + signedRandom() * SCREEN_SLOT_JITTER,
            0.5F + (SCREEN_SLOTS[slot][1] - 0.5F) * spread + signedRandom() * SCREEN_SLOT_JITTER
        };
    }

    /** Whether the four corners of a chunk's text box sit inside the screen margins (Evo's fit test). */
    private boolean fitsOnScreen(Vec3 anchor, Vector3f right, Vector3f down, float unit, float half) {
        view.set(anchor, fitRight.set(right).mul(unit), fitDown.set(down).mul(unit));
        float w = view.width(), h = view.height();
        for (int c = 0; c < 4; c++) {
            if (!view.project((c & 1) == 0 ? -half : half, c < 2 ? BOX_TOP : BOX_BOTTOM, fitScreen)) return false;
            if (fitScreen[0] < w * SCREEN_MARGIN_X
                    || fitScreen[0] > w * (1 - SCREEN_MARGIN_X)
                    || fitScreen[1] < h * SCREEN_MARGIN_Y
                    || fitScreen[1] > h * (1 - SCREEN_MARGIN_Y)) return false;
        }
        return true;
    }

    private static float groupWidth(Word[] words) {
        float w = 0;
        for (Word word : words) w += word.width + WORD_GAP;
        return w - WORD_GAP;
    }

    /** Your horizontal motion when you run (or sprint), else null. */
    private static Vec3 horizontalMotion(LocalPlayer me) {
        Vec3 v = me.getDeltaMovement();
        Vec3 flat = new Vec3(v.x, 0.0, v.z);
        if (flat.lengthSqr() < RUN_SPEED_PER_TICK * RUN_SPEED_PER_TICK && !me.isSprinting()) return null;
        return flat.lengthSqr() < MIN_HEADING_SPEED * MIN_HEADING_SPEED ? null : flat;
    }

    /** How far the words can go along {@code dir} before a wall, keeping a little gap. */
    private static float clearDistance(Minecraft mc, Vec3 eye, Vec3 dir, float dist) {
        Vec3 end = eye.add(dir.scale(dist + WALL_MARGIN));
        BlockHitResult hit =
                mc.level.clip(new ClipContext(eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.MISS) return dist;
        return (float) Math.max(MIN_DISTANCE, hit.getLocation().distanceTo(eye) - WALL_MARGIN);
    }

    // ---- drawing ----

    private enum Pass {
        GLOW,
        SHADOW,
        BODY
    }

    private void draw(Camera cam, long pos, long now) {
        double ms = now / 1_000_000.0;
        float time = (float) (((now - epochNs) / 1.0E9) % SHADER_TIME_PERIOD_S);
        int text = textColour.argb();
        int tint = accent.argb();
        float glowIntensity = glow.on() ? GLOW_STRENGTH : 0.0F;
        settle(now);
        Vec3 eye = cam.position();
        renderer.begin();
        if (glow.on()) drawPhrases(eye, pos, now, ms, time, text, tint, glowIntensity, Pass.GLOW);
        // every backdrop first, so no shadow lands on letters drawn before it
        if (shadow.on()) drawPhrases(eye, pos, now, ms, time, text, tint, glowIntensity, Pass.SHADOW);
        drawPhrases(eye, pos, now, ms, time, text, tint, glowIntensity, Pass.BODY);
        drawMotes(eye);
        renderer.flush(cam, glow.on() ? glowStrength.f() / 100.0F : 0.0F);
    }

    /**
     * Before drawing: a chunk mostly gone off screen leaves, and when walking or turning brings two standing chunks
     * well over each other, the older one makes room. Both with a gentle dissolve.
     */
    private void settle(long now) {
        float w = view.width(), h = view.height();
        for (Phrase p : phrases) {
            if (p.leaving()) continue;
            if (!boxOf(p, 0, p.box)) {
                leave(p, now, OFFSCREEN_LEAVE_MS, false);
                continue;
            }
            float all = area(p.box);
            float onScreen = Math.max(0, Math.min(p.box[2], w) - Math.max(p.box[0], 0))
                    * Math.max(0, Math.min(p.box[3], h) - Math.max(p.box[1], 0));
            if (all > 0 && onScreen < all * OFFSCREEN_VISIBLE_SHARE) {
                leave(p, now, OFFSCREEN_LEAVE_MS, false);
            }
        }
        for (int i = 0; i < phrases.size(); i++) {
            Phrase older = phrases.get(i);
            if (older.leaving()) continue;
            for (int j = i + 1; j < phrases.size(); j++) {
                Phrase newer = phrases.get(j);
                if (newer.leaving()) continue;
                float shared = intersection(older.box, newer.box);
                if (shared > OVERLAP_SHARE * Math.min(area(older.box), area(newer.box))) {
                    leave(older, now, LEAVE_MS, true);
                    break;
                }
            }
        }
    }

    private void drawPhrases(
            Vec3 eye, long pos, long now, double ms, float time, int text, int tint, float glowIntensity, Pass pass) {
        for (Phrase p : phrases) {
            double bob = Math.sin(ms / BOB_PERIOD_MS + p.bobPhase) * BOB_AMPLITUDE;
            renderer.sheet(p.anchor.x - eye.x, p.anchor.y + bob - eye.y, p.anchor.z - eye.z, p.right, p.down);
            float leave = p.leaving() ? leaveProgress(p, now) : 0.0F;
            float x = -p.half;
            for (Word w : p.words) {
                // A word that had not shown yet when its chunk left stays hidden, rather than popping in to dissolve.
                float reveal = Mth.clamp((pos + WORD_REVEAL_LEAD_MS - w.revealAt) / (float) WORD_REVEAL_MS, 0.0F, 1.0F);
                if (reveal > 0.0F) {
                    w.seen = true;
                    float lift = w.emphasized ? w.size * EMPHASIS_LIFT_RATIO : 0.0F;
                    float y = -w.size * 0.5F - lift;
                    switch (pass) {
                        case GLOW -> {
                            // Evo lifts the glow of the word being sung; it swells and settles here instead of
                            // switching, so it does not flash.
                            float k = w.emphasized ? 1.0F : singing(w, pos);
                            int alpha = Math.round(Mth.lerp(k, QUIET_BLOOM_ALPHA, SUNG_BLOOM_ALPHA));
                            renderer.word(
                                    true,
                                    w.font,
                                    w.text,
                                    w.size,
                                    w.width,
                                    withAlpha(tint, alpha),
                                    tint,
                                    0,
                                    GLOW_RADIUS,
                                    0,
                                    sungShare(w, pos),
                                    0,
                                    reveal,
                                    leave,
                                    w.seed,
                                    time,
                                    x,
                                    y);
                        }
                        case SHADOW ->
                            renderer.word(
                                    false,
                                    w.font,
                                    w.text,
                                    w.size,
                                    w.width,
                                    withAlpha(0, 255),
                                    tint,
                                    SHADOW_STRENGTH,
                                    GLOW_RADIUS,
                                    1,
                                    sungShare(w, pos),
                                    0,
                                    reveal,
                                    leave,
                                    w.seed,
                                    time,
                                    x,
                                    y);
                        case BODY -> {
                            float sung = Mth.clamp((pos - w.revealAt) / (float) w.sungMs, 0.0F, 1.0F);
                            if (sparks.on() && !p.leaving() && sung > 0.0F && sung < 1.0F) {
                                w.owed += frameDt * SUNG_MOTES_PER_S * (w.emphasized ? 1.5F : 1.0F);
                                while (w.owed >= 1.0F) {
                                    w.owed -= 1.0F;
                                    spawn(
                                            p,
                                            x + w.width * sung,
                                            y + w.size * (0.2F + random.nextFloat() * 0.6F),
                                            w.size,
                                            0.12F + random.nextFloat() * 0.2F,
                                            0.6F + random.nextFloat() * 0.5F,
                                            0.7F,
                                            tint);
                                }
                            }
                            renderer.word(
                                    false,
                                    w.font,
                                    w.text,
                                    w.size,
                                    w.width,
                                    withAlpha(text, 255),
                                    tint,
                                    glowIntensity,
                                    GLOW_RADIUS,
                                    0,
                                    sung,
                                    w.emphasized ? 1.0F : 0.0F,
                                    reveal,
                                    leave,
                                    w.seed,
                                    time,
                                    x,
                                    y);
                        }
                    }
                }
                x += w.width + WORD_GAP;
            }
        }
    }

    /** Moves the motes on, fading each in and out over its life with a soft twinkle, and queues them. */
    private void drawMotes(Vec3 eye) {
        float dt = frameDt;
        float drag = (float) Math.exp(-dt * 0.9F);
        for (int i = motes.size() - 1; i >= 0; i--) {
            Mote m = motes.get(i);
            m.age += dt;
            if (m.age >= m.life) {
                motes.remove(i);
                continue;
            }
            m.vx *= drag;
            m.vz *= drag;
            m.vy = m.vy * drag + 0.05F * dt;
            m.x += m.vx * dt;
            m.y += m.vy * dt;
            m.z += m.vz * dt;
            float k = m.age / m.life;
            float alpha = Math.min(1.0F, m.age / 0.12F) * (1.0F - k) * (1.0F - k);
            alpha *= 0.78F + 0.22F * Mth.sin(m.age * 9.0F + m.seed * 6.28F);
            int a = Math.round(255.0F * Math.max(0.0F, Math.min(1.0F, alpha)));
            if (a <= 2) continue;
            renderer.mote(
                    (float) (m.x - eye.x),
                    (float) (m.y - eye.y),
                    (float) (m.z - eye.z),
                    m.radius * (0.65F + 0.35F * (1.0F - k)),
                    withAlpha(m.colour, a));
        }
    }

    /** 0..1: how much of a word has been sung; the shadow and the glow follow the word's hop with it. */
    private static float sungShare(Word w, long pos) {
        return Mth.clamp((pos - w.revealAt) / (float) w.sungMs, 0.0F, 1.0F);
    }

    /** 0..1: how much a word is being sung right now, eased in and out. */
    private static float singing(Word w, long pos) {
        float t = pos - w.revealAt;
        float in = Mth.clamp(t / SING_GLOW_IN_MS, 0.0F, 1.0F);
        float out = Mth.clamp(1.0F - (t - w.sungMs) / SING_GLOW_OUT_MS, 0.0F, 1.0F);
        float k = in * out;
        return k * k * (3 - 2 * k);
    }

    private static int withAlpha(int rgb, int alpha) {
        return alpha << 24 | rgb & 0xFFFFFF;
    }

    private static float leaveProgress(Phrase p, long now) {
        return Mth.clamp((float) ((now - p.leaveStartNs) / 1_000_000.0 / p.leaveMs), 0.0F, 1.0F);
    }

    private float signedRandom() {
        return random.nextFloat() * 2.0F - 1.0F;
    }
}
