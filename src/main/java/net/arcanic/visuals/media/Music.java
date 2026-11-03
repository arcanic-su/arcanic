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
package net.arcanic.visuals.media;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Color;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.arcanic.visuals.core.Launcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;

public final class Music {
    private static final Identifier COVER = Identifier.fromNamespaceAndPath("arcanicvisuals", "media/cover");
    private static final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor((r) -> {
        Thread t = new Thread(r, "arcanic-media");
        t.setDaemon(true);
        return t;
    });
    private static volatile Track track;
    private static volatile String coverKey;
    private static volatile int coverColour;
    private static volatile long anchorTime;
    private static volatile long anchorPosition;
    private static volatile long lastUpdate;
    private static long reportedPosition;
    private static volatile long lastRequest;
    private static boolean started;
    private static volatile boolean closed;
    private static Process helper;
    private static BufferedReader reader;
    private static Path temp;
    private static Path artwork;
    private static Path commands;
    private static String artworkDigest;
    private static int starts;
    private static volatile boolean preview;

    private Music() {}

    public static synchronized void request() {
        lastRequest = System.nanoTime();
        if (!started
                && !closed
                && !preview
                && System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            started = true;
            // often, so a reported position is never more than a tenth of a second old when it is anchored
            worker.scheduleWithFixedDelay(Music::poll, 0L, 100L, TimeUnit.MILLISECONDS);
        }
    }

    public static Track track() {
        return track;
    }

    public static Identifier cover() {
        return coverKey.equals(track.key()) ? COVER : null;
    }

    public static int coverColour() {
        return coverKey.equals(track.key()) ? coverColour : 0;
    }

    private static int vivid(NativeImage image) {
        double r = 0.0;
        double g = 0.0;
        double b = 0.0;
        double sum = 0.0;

        for (int y = 0; y < image.getHeight(); y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                int c = image.getPixel(x, y);
                if (c >>> 24 >= 128) {
                    double cr = (double) (c >> 16 & 255) / 255.0;
                    double cg = (double) (c >> 8 & 255) / 255.0;
                    double cb = (double) (c & 255) / 255.0;
                    double max = Math.max(cr, Math.max(cg, cb));
                    double min = Math.min(cr, Math.min(cg, cb));
                    double sat = max <= 0.0 ? 0.0 : (max - min) / max;
                    double wgt = sat * sat * max + 0.015;
                    r += cr * wgt;
                    g += cg * wgt;
                    b += cb * wgt;
                    sum += wgt;
                }
            }
        }

        if (sum <= 0.0) {
            return 0;
        } else {
            float[] hsv = Color.RGBtoHSB(
                    (int) Math.round(r / sum * 255.0),
                    (int) Math.round(g / sum * 255.0),
                    (int) Math.round(b / sum * 255.0),
                    null);
            int rgb = Color.HSBtoRGB(hsv[0], Math.max(0.45F, Math.min(0.85F, hsv[1])), Math.max(0.92F, hsv[2]));
            return rgb | 0xFF000000;
        }
    }

    public static long position() {
        Track current = track;
        if (current.present() && current.duration() > 0L) {
            long elapsed = current.playing()
                    ? Math.max(0L, (Math.min(System.nanoTime(), lastUpdate + 8000000000L) - anchorTime) / 1000000L)
                    : 0L;
            return Math.max(0L, Math.min(current.duration(), anchorPosition + elapsed));
        } else {
            return 0L;
        }
    }

    public static String time(long ms) {
        long s = Math.max(0L, ms / 1000L);
        return String.format(Locale.ROOT, "%d:%02d", s / 60L, s % 60L);
    }

    public static void control(String action) {
        if (Set.of("toggle", "prev", "next").contains(action) && !preview && !closed) {
            request();
            worker.execute(() -> {
                try {
                    if (helper != null && helper.isAlive()) {
                        Files.writeString(
                                commands,
                                action + "\n",
                                StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.APPEND);
                    }
                } catch (IOException e) {
                    stopHelper();
                }
            });
        }
    }

    private static void poll() {
        if (!closed && !preview) {
            if (System.nanoTime() - lastRequest > 6000000000L) {
                stopHelper();
                starts = 0;
                publish(Music.Track.EMPTY);
            } else {
                try {
                    if (helper == null || !helper.isAlive()) {
                        stopHelper();
                        if (starts++ >= 3) {
                            return;
                        }

                        if (temp == null) {
                            temp = Files.createTempDirectory("arcanic-media-");
                            artwork = temp.resolve("cover.png");
                            commands = temp.resolve("commands.txt");
                        }

                        Files.writeString(commands, "", StandardCharsets.UTF_8);
                        String pid = String.valueOf(ProcessHandle.current().pid());
                        ProcessBuilder pb;
                        Path compiled;
                        if ((compiled = compiled()) != null) {
                            // our own reader of the Windows media session, built once on this PC by the .NET
                            // Framework's csc: music played anywhere (Spotify, Yandex Music, a browser) and its cover
                            // reach the island with or without the launcher
                            pb = new ProcessBuilder(compiled.toString(), artwork.toString(), commands.toString(), pid);
                        } else if (Launcher.media()) {
                            pb = new ProcessBuilder(
                                    Launcher.exe().toString(), "--media", artwork.toString(), commands.toString(), pid);
                        } else {
                            // neither: the PowerShell reader (no covers)
                            pb = new ProcessBuilder(
                                    "powershell.exe",
                                    "-NoProfile",
                                    "-NonInteractive",
                                    "-ExecutionPolicy",
                                    "Bypass",
                                    "-WindowStyle",
                                    "Hidden",
                                    "-File",
                                    script().toString(),
                                    artwork.toString(),
                                    commands.toString(),
                                    pid);
                        }
                        helper = pb.redirectError(temp.resolve("helper.err").toFile())
                                .start();
                        reader = new BufferedReader(
                                new InputStreamReader(helper.getInputStream(), StandardCharsets.UTF_8));
                    }

                    for (int i = 0; i < 16 && reader.ready(); i++) {
                        String line = reader.readLine();
                        if (line == null) {
                            stopHelper();
                            publish(Music.Track.EMPTY);
                            break;
                        }

                        if (line.startsWith("arcanic-media-fail")) {
                            LoggerFactory.getLogger("ArcanicMusic").warn("Media helper: {}", line);
                            stopHelper();
                            publish(Music.Track.EMPTY);
                            break;
                        }

                        publish(parse(line));
                    }

                    try {
                        // the cover file is hashed only now and then, or at once when a track brings a new one
                        if (++coverPolls >= 8 || !track.artwork().equals(coverChecked)) {
                            coverPolls = 0;
                            coverChecked = track.artwork();
                            updateCover();
                        }
                    } catch (Exception e) {
                        LoggerFactory.getLogger("ArcanicMusic").debug("Artwork is not ready", e);
                    }
                } catch (Exception e) {
                    LoggerFactory.getLogger("ArcanicMusic").warn("Cannot read Windows media session", e);
                    stopHelper();
                    publish(Music.Track.EMPTY);
                }
            }
        }
    }

    private static int coverPolls;
    private static String coverChecked = "";
    private static Path built;
    private static boolean buildFailed;

    /**
     * The C# media reader, compiled from the jar's MediaHelper.cs with the csc.exe every Windows ships in its .NET
     * Framework, against the WinRT metadata in System32. Kept in %LOCALAPPDATA%\Arcanic\media under a hash of the
     * source, so it is built once per version; null when it cannot be built.
     */
    private static Path compiled() {
        if (built != null && Files.isRegularFile(built)) return built;
        if (buildFailed) return null;
        try {
            String windir = Objects.requireNonNullElse(System.getenv("WINDIR"), "C:\\Windows");
            Path framework = Path.of(windir, "Microsoft.NET", "Framework64", "v4.0.30319");
            if (!Files.isRegularFile(framework.resolve("csc.exe")))
                framework = Path.of(windir, "Microsoft.NET", "Framework", "v4.0.30319");
            Path csc = framework.resolve("csc.exe");
            Path meta = Path.of(windir, "System32", "WinMetadata");
            String local = System.getenv("LOCALAPPDATA");
            if (!Files.isRegularFile(csc) || !Files.isDirectory(meta) || local == null) {
                buildFailed = true;
                return null;
            }
            byte[] source;
            try (InputStream in = Music.class.getResourceAsStream("/assets/arcanicvisuals/media/MediaHelper.cs")) {
                if (in == null) throw new IOException("MediaHelper.cs is missing from the jar");
                source = in.readAllBytes();
            }
            String hash = HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(source))
                    .substring(0, 12);
            Path dir = Path.of(local, "Arcanic", "media");
            Path exe = dir.resolve("arcanic-media-" + hash + ".exe");
            if (!Files.isRegularFile(exe)) {
                Files.createDirectories(dir);
                Path cs = dir.resolve("MediaHelper-" + hash + ".cs");
                Files.write(cs, source);
                Process p = new ProcessBuilder(
                                csc.toString(),
                                "-nologo",
                                "-optimize",
                                "-target:winexe",
                                "-out:" + exe,
                                "-r:" + framework.resolve("System.Runtime.dll"),
                                "-r:" + framework.resolve("System.Drawing.dll"),
                                "-r:" + meta.resolve("Windows.Media.winmd"),
                                "-r:" + meta.resolve("Windows.Foundation.winmd"),
                                "-r:" + meta.resolve("Windows.Storage.winmd"),
                                cs.toString())
                        .redirectErrorStream(true)
                        .redirectOutput(dir.resolve("build-" + hash + ".log").toFile())
                        .start();
                if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0 || !Files.isRegularFile(exe)) {
                    p.destroyForcibly();
                    LoggerFactory.getLogger("ArcanicMusic").warn("Cannot build the media reader, see {}", dir);
                    buildFailed = true;
                    return null;
                }
            }
            built = exe;
            return exe;
        } catch (Exception e) {
            LoggerFactory.getLogger("ArcanicMusic").warn("Cannot build the media reader", e);
            buildFailed = true;
            return null;
        }
    }

    /** The bundled PowerShell media reader, copied next to the cover once per game. */
    private static Path script() throws IOException {
        Path ps = temp.resolve("smtc.ps1");
        if (!Files.isRegularFile(ps)) {
            try (InputStream in = Music.class.getResourceAsStream("/assets/arcanicvisuals/media/smtc.ps1")) {
                if (in == null) throw new IOException("smtc.ps1 is missing from the jar");
                Files.copy(in, ps);
            }
        }
        return ps;
    }

    public static Track parse(String line) {
        if (line != null && !line.isBlank()) {
            String[] fields = line.split("\t", -1);
            if (fields.length >= 5 && !fields[2].isBlank()) {
                if (!fields[0].equalsIgnoreCase("playing") && !fields[0].equalsIgnoreCase("paused")) {
                    return Music.Track.EMPTY;
                } else {
                    long duration = number(fields[4]);
                    boolean playing = fields[0].equalsIgnoreCase("playing");
                    long position = number(fields[3]);
                    if (playing && fields.length > 6) {
                        // the reader says when it took the position: the time the line spent in the pipe is added
                        try {
                            long age = System.currentTimeMillis() - Long.parseLong(fields[6].strip());
                            position += Math.max(0L, Math.min(1500L, age));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    position = Math.min(position, duration);
                    return new Track(
                            clean(fields[1]),
                            clean(fields[2]),
                            playing,
                            position,
                            duration,
                            fields.length > 5 ? fields[5] : "");
                }
            } else {
                return Music.Track.EMPTY;
            }
        } else {
            return Music.Track.EMPTY;
        }
    }

    private static String clean(String s) {
        String text = s.replaceAll("[\\p{Cntrl}]", " ").strip();
        return text.substring(0, Math.min(96, text.length()));
    }

    private static long number(String s) {
        try {
            return Math.max(0L, Math.min(86400000L, Long.parseLong(s)));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static long emptySince;

    private static synchronized void publish(Track next) {
        long now = System.nanoTime();
        // a session that drops out for a moment (a slow read, a switch between apps) must not blink the island:
        // only an absence that lasts 1.5 s clears the track
        if (!next.present() && track.present()) {
            if (emptySince == 0L) emptySince = now;
            if (now - emptySince < 1_500_000_000L) return;
        } else {
            emptySince = 0L;
        }
        if (next.key().equals(track.key()) && next.position() == reportedPosition) {
            if (next.playing() != track.playing()) {
                anchorPosition = position();
                anchorTime = now;
            }
        } else {
            anchorPosition = next.position();
            anchorTime = now;
        }

        reportedPosition = next.position();
        lastUpdate = now;
        if (!next.key().equals(track.key())) {
            artworkDigest = "";
        }

        track = next;
    }

    private static void updateCover() throws Exception {
        Track current = track;
        if (current.present() && !current.artwork().isBlank() && artwork != null && Files.isRegularFile(artwork)) {
            long size = Files.size(artwork);
            if (size > 0L && size <= 4194304L) {
                byte[] bytes = Files.readAllBytes(artwork);
                String digest = HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                if (!digest.equals(artworkDigest) || !coverKey.equals(current.key())) {
                    ByteArrayInputStream in = new ByteArrayInputStream(bytes);

                    NativeImage image;
                    try {
                        image = NativeImage.read((InputStream) in);
                    } catch (Throwable e) {
                        try {
                            in.close();
                        } catch (Throwable e2) {
                            e.addSuppressed(e2);
                        }

                        throw e;
                    }

                    in.close();
                    if (image.getWidth() <= 2048 && image.getHeight() <= 2048) {
                        NativeImage rounded = new NativeImage(128, 128, false);
                        int side = Math.min(image.getWidth(), image.getHeight());
                        int offsetX = (image.getWidth() - side) / 2;
                        int offsetY = (image.getHeight() - side) / 2;

                        for (int y = 0; y < 128; y++) {
                            for (int x = 0; x < 128; x++) {
                                int color = image.getPixel(offsetX + x * side / 128, offsetY + y * side / 128);
                                double dx = Math.max(0.0, Math.abs((double) x + 0.5 - 64.0) - 40.0);
                                double dy = Math.max(0.0, Math.abs((double) y + 0.5 - 64.0) - 40.0);
                                double alpha = Math.max(0.0, Math.min(1.0, 24.5 - Math.hypot(dx, dy)));
                                rounded.setPixel(
                                        x, y, color & 0xFFFFFF | (int) ((double) (color >>> 24) * alpha) << 24);
                            }
                        }

                        image.close();
                        artworkDigest = digest;
                        int colour = vivid(rounded);
                        Minecraft.getInstance().execute(() -> {
                            if (!closed && track.key().equals(current.key())) {
                                Minecraft.getInstance()
                                        .getTextureManager()
                                        .register(COVER, new DynamicTexture(() -> "music cover", rounded));
                                coverColour = colour;
                                coverKey = current.key();
                            } else {
                                rounded.close();
                            }
                        });
                    } else {
                        image.close();
                    }
                }
            }
        }
    }

    private static void stopHelper() {
        if (helper != null) {
            helper.destroyForcibly();
        }

        helper = null;

        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException ignored) {
        }

        reader = null;
    }

    public static synchronized void close() {
        if (!closed) {
            closed = true;
            worker.execute(() -> {
                stopHelper();
                if (temp != null) {
                    try {
                        Files.deleteIfExists(temp.resolve("cover.png"));
                        Files.deleteIfExists(temp.resolve("helper.err"));
                        Files.deleteIfExists(temp.resolve("commands.txt"));
                        Files.deleteIfExists(temp.resolve("smtc.ps1"));
                        Files.deleteIfExists(temp.resolve("cover.png.tmp"));
                        Files.deleteIfExists(temp);
                    } catch (IOException ignored) {
                    }
                }
            });
            worker.shutdown();
        }
    }

    static {
        track = Music.Track.EMPTY;
        coverKey = "";
        reportedPosition = -1L;
        artworkDigest = "";
    }

    public static record Track(
            String artist, String title, boolean playing, long position, long duration, String artwork) {
        public static final Track EMPTY = new Track("", "", false, 0L, 0L, "");

        public boolean present() {
            return !this.title.isBlank();
        }

        public String key() {
            return this.artist + "\u001f" + this.title;
        }
    }
}
