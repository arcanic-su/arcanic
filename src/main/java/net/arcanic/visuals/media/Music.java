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
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import net.arcanic.visuals.core.Launcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;

/** Windows media session reader: supports Yandex's app and browser media sessions. */
public final class Music {
    public record Track(String artist, String title, boolean playing, long position, long duration, String artwork) {
        public static final Track EMPTY = new Track("", "", false, 0, 0, "");

        public boolean present() {
            return !title.isBlank();
        }

        public String key() {
            return artist + '\u001f' + title;
        }
    }

    private static final Identifier COVER = Identifier.fromNamespaceAndPath("arcanicvisuals", "media/cover");
    private static final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "arcanic-media");
        t.setDaemon(true);
        return t;
    });
    private static volatile Track track = Track.EMPTY;
    private static volatile String coverKey = "";
    private static volatile long anchorTime, anchorPosition, lastUpdate;
    private static long reportedPosition = -1;
    private static volatile long lastRequest;
    private static boolean started;
    private static volatile boolean closed;
    private static Process helper;
    private static BufferedReader reader;
    private static Path temp, artwork, commands;
    private static String artworkDigest = "";
    private static int starts;
    private static volatile boolean preview;

    private Music() {}

    public static synchronized void request() {
        lastRequest = System.nanoTime();
        if (started
                || closed
                || preview
                || !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return;
        started = true;
        worker.scheduleWithFixedDelay(Music::poll, 0, 350, TimeUnit.MILLISECONDS);
    }

    public static Track track() {
        return track;
    }

    public static Identifier cover() {
        return coverKey.equals(track.key()) ? COVER : null;
    }

    public static long position() {
        Track current = track;
        if (!current.present() || current.duration() <= 0) return 0;
        long elapsed = current.playing()
                ? Math.max(0, (Math.min(System.nanoTime(), lastUpdate + 8_000_000_000L) - anchorTime) / 1_000_000L)
                : 0;
        return Math.max(0, Math.min(current.duration(), anchorPosition + elapsed));
    }

    public static String time(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    public static void control(String action) {
        if (!Set.of("toggle", "prev", "next").contains(action) || preview || closed) return;
        request();
        worker.execute(() -> {
            try {
                if (helper != null && helper.isAlive())
                    Files.writeString(
                            commands,
                            action + "\n",
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND);
            } catch (IOException e) {
                stopHelper();
            }
        });
    }

    private static void poll() {
        if (closed || preview) return;
        if (System.nanoTime() - lastRequest > 6_000_000_000L) {
            stopHelper();
            starts = 0;
            publish(Track.EMPTY);
            return;
        }
        try {
            if (helper == null || !helper.isAlive()) {
                stopHelper();
                if (starts++ >= 3) return;
                if (temp == null) {
                    temp = Files.createTempDirectory("arcanic-media-");
                    artwork = temp.resolve("cover.png");
                    commands = temp.resolve("commands.txt");
                }
                Files.writeString(commands, "", StandardCharsets.UTF_8);
                if (!Launcher.media()) return;
                Path exe = Launcher.exe();
                helper = new ProcessBuilder(
                                exe.toString(),
                                "--media",
                                artwork.toString(),
                                commands.toString(),
                                String.valueOf(ProcessHandle.current().pid()))
                        .redirectError(temp.resolve("helper.err").toFile())
                        .start();
                reader = new BufferedReader(new InputStreamReader(helper.getInputStream(), StandardCharsets.UTF_8));
            }
            for (int i = 0; i < 16 && reader.ready(); i++) {
                String line = reader.readLine();
                if (line == null) {
                    stopHelper();
                    publish(Track.EMPTY);
                    break;
                }
                if (line.startsWith("arcanic-media-fail")) {
                    LoggerFactory.getLogger("ArcanicMusic").warn("Media helper: {}", line);
                    stopHelper();
                    publish(Track.EMPTY);
                    break;
                }
                publish(parse(line));
            }
            try {
                updateCover();
            } catch (Exception e) {
                LoggerFactory.getLogger("ArcanicMusic").debug("Artwork is not ready", e);
            }
        } catch (Exception e) {
            LoggerFactory.getLogger("ArcanicMusic").warn("Cannot read Windows media session", e);
            stopHelper();
            publish(Track.EMPTY);
        }
    }

    public static Track parse(String line) {
        if (line == null || line.isBlank()) return Track.EMPTY;
        String[] fields = line.split("\t", -1);
        if (fields.length < 5 || fields[2].isBlank()) return Track.EMPTY;
        if (!fields[0].equalsIgnoreCase("playing") && !fields[0].equalsIgnoreCase("paused")) return Track.EMPTY;
        long duration = number(fields[4]), position = Math.min(number(fields[3]), duration);
        return new Track(
                clean(fields[1]),
                clean(fields[2]),
                fields[0].equalsIgnoreCase("playing"),
                position,
                duration,
                fields.length > 5 ? fields[5] : "");
    }

    private static String clean(String s) {
        String text = s.replaceAll("[\\p{Cntrl}]", " ").strip();
        return text.substring(0, Math.min(96, text.length()));
    }

    private static long number(String s) {
        try {
            return Math.max(0, Math.min(86_400_000, Long.parseLong(s)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static synchronized void publish(Track next) {
        long now = System.nanoTime();
        if (!next.key().equals(track.key()) || next.position() != reportedPosition) {
            anchorPosition = next.position();
            anchorTime = now;
        } else if (next.playing() != track.playing()) {
            anchorPosition = position();
            anchorTime = now;
        }
        reportedPosition = next.position();
        lastUpdate = now;
        if (!next.key().equals(track.key())) artworkDigest = "";
        track = next;
    }

    private static void updateCover() throws Exception {
        Track current = track;
        if (!current.present() || current.artwork().isBlank() || artwork == null || !Files.isRegularFile(artwork))
            return;
        long size = Files.size(artwork);
        if (size <= 0 || size > 4_194_304) return;
        byte[] bytes = Files.readAllBytes(artwork);
        String digest =
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        if (digest.equals(artworkDigest) && coverKey.equals(current.key())) return;
        NativeImage image;
        try (var in = new ByteArrayInputStream(bytes)) {
            image = NativeImage.read(in);
        }
        if (image.getWidth() > 2048 || image.getHeight() > 2048) {
            image.close();
            return;
        }
        NativeImage rounded = new NativeImage(128, 128, false);
        int side = Math.min(image.getWidth(), image.getHeight());
        int offsetX = (image.getWidth() - side) / 2, offsetY = (image.getHeight() - side) / 2;
        for (int y = 0; y < 128; y++)
            for (int x = 0; x < 128; x++) {
                int color = image.getPixel(offsetX + x * side / 128, offsetY + y * side / 128);
                double dx = Math.max(0, Math.abs(x + .5 - 64) - 40), dy = Math.max(0, Math.abs(y + .5 - 64) - 40);
                double alpha = Math.max(0, Math.min(1, 24.5 - Math.hypot(dx, dy)));
                rounded.setPixel(x, y, (color & 0xFFFFFF) | ((int) ((color >>> 24) * alpha) << 24));
            }
        image.close();
        artworkDigest = digest;
        Minecraft.getInstance().execute(() -> {
            if (closed || !track.key().equals(current.key())) {
                rounded.close();
                return;
            }
            Minecraft.getInstance()
                    .getTextureManager()
                    .register(COVER, new DynamicTexture(() -> "music cover", rounded));
            coverKey = current.key();
        });
    }

    private static void stopHelper() {
        if (helper != null) helper.destroyForcibly();
        helper = null;
        try {
            if (reader != null) reader.close();
        } catch (IOException ignored) {
        }
        reader = null;
    }

    public static synchronized void close() {
        if (closed) return;
        closed = true;
        worker.execute(() -> {
            stopHelper();
            if (temp != null)
                try {
                    Files.deleteIfExists(temp.resolve("cover.png"));
                    Files.deleteIfExists(temp.resolve("helper.err"));
                    Files.deleteIfExists(temp.resolve("commands.txt"));
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
        });
        worker.shutdown();
    }
}
