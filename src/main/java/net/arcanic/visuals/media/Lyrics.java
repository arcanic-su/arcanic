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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Timed lyrics for the track playing on the PC, after EvoVisuals' resolver: lrclib.net first, then YouTube Music,
 * then NetEase. Timed lyrics win over plain ones; plain lyrics are spread over the song by line length. What Whisper
 * heard of a song without lyrics is kept as a transcript and used before asking the providers again.
 */
public final class Lyrics {
    private static final Logger LOG = LoggerFactory.getLogger("ArcanicLyrics");
    private static final int CACHE_SIZE = 48;
    private static final long RETRY_MS = 60_000;
    private static final long EXACT_SECONDS = 2;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "arcanic-lyrics");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static final Map<String, Result> CACHE = new LinkedHashMap<>(CACHE_SIZE, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Result> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    /** Track key to the time a lookup started; negative when every provider failed (retry after a minute). */
    private static final Map<String, Long> PENDING = new ConcurrentHashMap<>();

    private Lyrics() {}

    /** One line: when it starts and ends in the song, in ms. */
    /**
     * One sung line. When the source times every word (NetEase's word-by-word lyrics, enhanced LRC), {@code words}
     * holds the start of each whitespace-separated word of the text; otherwise it is null.
     */
    public record Line(long start, long end, String text, long[] words) {
        public Line(long start, long end, String text) {
            this(start, end, text, null);
        }

        public long duration() {
            return Math.max(0, end - start);
        }

        /** Whether every word of the text has its own start. */
        public boolean worded() {
            return words != null
                    && words.length > 0
                    && words.length == text.strip().split("\\s+").length;
        }
    }

    public record Sheet(List<Line> lines) {
        public static final Sheet NONE = new Sheet(List.of());

        public boolean empty() {
            return lines.isEmpty();
        }

        /** Whether most lines carry their own word timing. */
        public boolean worded() {
            int n = 0;
            for (Line l : lines) if (l.worded()) n++;
            return n > 0 && n * 2 >= lines.size();
        }

        /** Index of the line sung at {@code ms}, or -1 between lines. */
        public int at(long ms) {
            int lo = 0, hi = lines.size() - 1, found = -1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (lines.get(mid).start() <= ms) {
                    found = mid;
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            return found >= 0 && ms < lines.get(found).end() ? found : -1;
        }

        /** Index of the last line that starts at or before {@code ms}, or -1 before the first one. */
        public int before(long ms) {
            int lo = 0, hi = lines.size() - 1, found = -1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (lines.get(mid).start() <= ms) {
                    found = mid;
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            return found;
        }
    }

    /** Where the lyrics came from; timed sources carry real line stamps. */
    public enum Origin {
        LRCLIB(true),
        YOUTUBE_MUSIC(true),
        NETEASE(true),
        LRCLIB_ESTIMATED(false),
        YOUTUBE_MUSIC_ESTIMATED(false),
        WHISPER(true),
        NONE(false);

        public final boolean timed;

        Origin(boolean timed) {
            this.timed = timed;
        }
    }

    public record Result(Sheet sheet, Origin origin, long matchedSeconds) {
        static final Result NOT_FOUND = new Result(Sheet.NONE, Origin.NONE, 0);

        public boolean found() {
            return !sheet.empty();
        }

        long durationError(long seconds) {
            return seconds > 0 && matchedSeconds > 0 ? Math.abs(seconds - matchedSeconds) : Long.MAX_VALUE / 2;
        }
    }

    /** The lookup for the track, or null while it is still running. Never blocks. */
    public static Result of(Music.Track track) {
        if (!track.present()) return null;
        String key = track.key();
        synchronized (CACHE) {
            Result r = CACHE.get(key);
            if (r != null) return r;
        }
        long now = System.currentTimeMillis();
        Long started = PENDING.get(key);
        if (started == null || started < 0 && now + started > RETRY_MS) {
            PENDING.put(key, now);
            Query q = Query.of(track.artist(), track.title(), track.duration() / 1000);
            WORKER.execute(() -> resolve(key, q));
        }
        return null;
    }

    private static void resolve(String key, Query q) {
        Result saved = readTranscript(key);
        if (saved != null) {
            synchronized (CACHE) {
                Result had = CACHE.get(key);
                if (had == null || had.origin() == Origin.NONE) CACHE.put(key, saved);
            }
            PENDING.remove(key);
            return;
        }
        Result best = Result.NOT_FOUND;
        int failed = 0;
        boolean shown = false;
        for (Provider p : Provider.values()) {
            // with exact line timing in hand only NetEase is still worth asking: it alone times every word
            if (exact(best, q.seconds()) && p != Provider.NETEASE) continue;
            Result r;
            try {
                r = p.find(q);
            } catch (Exception ex) {
                LOG.debug("Lyrics provider {} failed", p, ex);
                failed++;
                continue;
            }
            if (better(r, best, q.seconds())) best = r;
            if (exact(best, q.seconds())) {
                if (best.sheet().worded()) break;
                if (!shown) {
                    // show the lines at once while the word timing is looked for
                    shown = true;
                    synchronized (CACHE) {
                        Result had = CACHE.get(key);
                        if (had == null || had.origin() == Origin.NONE) CACHE.put(key, best);
                    }
                }
            }
        }
        LOG.debug("Lyrics for {} resolved via {}", q.searchText(), best.origin());
        if (failed == Provider.values().length) {
            PENDING.put(key, -System.currentTimeMillis());
            return;
        }
        synchronized (CACHE) {
            Result had = CACHE.get(key);
            if (had == null
                    || had.origin() == Origin.NONE
                    || shown && best.sheet().worded()) CACHE.put(key, best);
        }
        PENDING.remove(key);
    }

    /**
     * Keeps what Whisper heard of a song that has no lyrics anywhere, in memory and as an LRC file, so the next time
     * it plays the words are there from the start. Never replaces real timed lyrics.
     */
    public static void storeTranscript(String key, Sheet sheet) {
        if (sheet.empty()) return;
        Result heard = new Result(sheet, Origin.WHISPER, 0);
        synchronized (CACHE) {
            Result had = CACHE.get(key);
            if (had == null || !had.origin().timed || had.origin() == Origin.WHISPER) CACHE.put(key, heard);
        }
        WORKER.execute(() -> writeTranscript(key, sheet));
    }

    private static Result readTranscript(String key) {
        Path file = transcriptFile(key);
        if (file == null || !Files.isRegularFile(file)) return null;
        try {
            Sheet s = parseLrc(Files.readString(file, StandardCharsets.UTF_8));
            return s.empty() ? null : new Result(s, Origin.WHISPER, 0);
        } catch (Exception e) {
            LOG.warn("Could not read transcript for {}", file.getFileName(), e);
            return null;
        }
    }

    private static void writeTranscript(String key, Sheet sheet) {
        Path file = transcriptFile(key);
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            StringBuilder out = new StringBuilder();
            for (Line l : sheet.lines()) {
                long ms = l.start();
                out.append(String.format(
                        Locale.ROOT, "[%02d:%02d.%02d]%s%n", ms / 60_000, ms / 1000 % 60, ms % 1000 / 10, l.text()));
            }
            Files.writeString(temp, out.toString(), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            LOG.warn("Could not store transcript for {}", file.getFileName(), e);
        }
    }

    /** config/arcanicvisuals/lyrics/<first 12 bytes of the key's SHA-256>.lrc */
    private static Path transcriptFile(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return FabricLoader.getInstance()
                    .getConfigDir()
                    .resolve("arcanicvisuals")
                    .resolve("lyrics")
                    .resolve(HexFormat.of().formatHex(digest, 0, 12) + ".lrc");
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean better(Result a, Result b, long seconds) {
        if (!a.found()) return false;
        if (!b.found()) return true;
        if (a.origin().timed != b.origin().timed) return a.origin().timed;
        boolean aw = a.sheet().worded(), bw = b.sheet().worded();
        if (aw != bw) {
            // word timing wins, but only for the same recording: its length must agree
            Result worded = aw ? a : b;
            if (worded.durationError(seconds) <= EXACT_SECONDS) return aw;
        }
        return a.durationError(seconds) < b.durationError(seconds);
    }

    private static boolean exact(Result r, long seconds) {
        return r.origin().timed && r.durationError(seconds) <= EXACT_SECONDS;
    }

    private enum Provider {
        LRCLIB {
            Result find(Query q) throws Exception {
                return Lrclib.find(q);
            }
        },
        YOUTUBE_MUSIC {
            Result find(Query q) throws Exception {
                return YouTubeMusic.find(q);
            }
        },
        NETEASE {
            Result find(Query q) throws Exception {
                return NetEase.find(q);
            }
        };

        abstract Result find(Query q) throws Exception;
    }

    /** Artist and title cleaned of channel noise, brackets and featuring credits. */
    record Query(String artist, String title, long seconds) {
        private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        private static final Pattern NOISE_BRACKETS = Pattern.compile(
                "\\s*[(\\[【][^)\\]】]*(?:feat\\.?|ft\\.?|featuring|official|video|audio|lyric|clip|visuali[sz]er|remaster"
                        + "|live|hd|4k|mv|prod|slowed|reverb|sped|speed|nightcore|bass boost|8d|премьера|клип|текст"
                        + "|премьера клипа|lyrics|explicit|clean)[^)\\]】]*[)\\]】]",
                FLAGS);
        private static final Pattern FEATURING =
                Pattern.compile("\\s+(?:feat\\.?|ft\\.?|featuring|prod\\.?)\\s+.*$", FLAGS);
        private static final Pattern TRAILING_TAGS =
                Pattern.compile("\\s*[|｜/]\\s*.*$|\\s+-\\s+(?:slowed|sped up|speed up|reverb|remastered.*)$", FLAGS);
        private static final Pattern ARTIST_NOISE = Pattern.compile("\\s*(?:-\\s*topic|vevo|official|music)$", FLAGS);
        private static final Pattern ARTIST_SEPARATORS =
                Pattern.compile("\\s*(?:,|&|\\bx\\b|×|\\band\\b|\\bи\\b)\\s*", FLAGS);
        private static final Pattern DECORATION = Pattern.compile("[\"«»“”„'`]");
        private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
        private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
        private static final String DASH = " - ";

        static Query of(String artist, String title, long seconds) {
            String a = artist == null ? "" : artist.strip();
            String t = title == null ? "" : title.strip();
            int dash = t.indexOf(DASH);
            if (dash > 0 && (a.isEmpty() || looselyEquals(t.substring(0, dash), a))) {
                a = t.substring(0, dash).strip();
                t = t.substring(dash + DASH.length()).strip();
            }
            t = DECORATION.matcher(t).replaceAll("");
            t = NOISE_BRACKETS.matcher(t).replaceAll("");
            t = TRAILING_TAGS.matcher(t).replaceAll("");
            t = FEATURING.matcher(t).replaceAll("").strip();
            a = ARTIST_NOISE.matcher(a).replaceAll("").strip();
            return new Query(a, t, Math.max(0, seconds));
        }

        String primaryArtist() {
            String[] parts = ARTIST_SEPARATORS.split(artist, 2);
            return parts.length == 0 ? artist : parts[0].strip();
        }

        String searchText() {
            return artist.isEmpty() ? title : artist + " " + title;
        }

        static String fold(String s) {
            if (s == null) return "";
            String bare = DIACRITICS
                    .matcher(Normalizer.normalize(s, Normalizer.Form.NFD))
                    .replaceAll("");
            return NON_WORD.matcher(bare.toLowerCase(Locale.ROOT))
                    .replaceAll(" ")
                    .strip();
        }

        static boolean looselyEquals(String a, String b) {
            return fold(a).equals(fold(b));
        }

        /** 1 for the same words, 0.85 when one contains the other, else the share of shared words. */
        static double similarity(String a, String b) {
            String x = fold(a), y = fold(b);
            if (x.isEmpty() || y.isEmpty()) return 0;
            if (x.equals(y)) return 1;
            if (x.contains(y) || y.contains(x)) return 0.85;
            Set<String> left = new HashSet<>(Arrays.asList(x.split(" ")));
            Set<String> all = new HashSet<>(Arrays.asList(y.split(" ")));
            int shared = 0;
            for (String w : left) if (all.contains(w)) shared++;
            all.addAll(left);
            return (double) shared / all.size();
        }
    }

    /** How well a found song matches the query: title and artist likeness, nudged by the duration. */
    private static double score(String title, String artist, long seconds, Query q) {
        double t = Query.similarity(Query.of(artist, title, 0).title(), q.title());
        if (t < 0.5) return 0;
        double a;
        if (q.artist().isEmpty()) {
            a = 0.4;
        } else {
            a = Math.max(Query.similarity(artist, q.artist()), Query.similarity(artist, q.primaryArtist()));
            if (a < 0.5) return 0;
        }
        double s = t * 0.6 + a * 0.4;
        if (q.seconds() > 0 && seconds > 0) {
            long off = Math.abs(seconds - q.seconds());
            if (off <= 3) s += 0.15;
            else if (off > 12) s -= 0.35;
        }
        return s;
    }

    private static final double MIN_SCORE = 0.6;

    // ---- LRC parsing and plain-text estimation ----

    private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final long MAX_LINE_MS = 8000;
    private static final long TAIL_LINE_MS = 5000;
    private static final int MAX_LINE_LENGTH = 160;

    /** Enhanced LRC's word stamp, {@code <mm:ss.xx>} before a word. */
    private static final Pattern WORD_STAMP = Pattern.compile("<(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?>");

    /** A line's text without its word stamps, and each word's start relative to the line's own stamp (or null). */
    private record Worded(String text, long[] words) {}

    /**
     * Parses LRC text; a line may carry several stamps, and each ends at the next stamp (8 s at most). Enhanced LRC's
     * word stamps are taken as the words' own timing.
     */
    static Sheet parseLrc(String lrc) {
        if (lrc == null || lrc.isBlank()) return Sheet.NONE;
        List<long[]> stamps = new ArrayList<>();
        List<Worded> texts = new ArrayList<>();
        for (String raw : lrc.split("\\R")) {
            Matcher m = STAMP.matcher(raw);
            int end = 0;
            List<Long> at = new ArrayList<>(1);
            while (m.find() && m.start() == end) {
                at.add(millis(m));
                end = m.end();
            }
            if (at.isEmpty()) continue;
            Worded text = worded(raw.substring(end), at.get(0));
            for (long ms : at) {
                stamps.add(new long[] {ms, texts.size()});
                texts.add(text);
            }
        }
        stamps.sort(Comparator.comparingLong(s -> s[0]));
        List<Line> lines = new ArrayList<>(stamps.size());
        for (int i = 0; i < stamps.size(); i++) {
            Worded text = texts.get((int) stamps.get(i)[1]);
            if (text.text().isEmpty()) continue;
            long start = stamps.get(i)[0];
            long end =
                    i + 1 < stamps.size() ? Math.min(stamps.get(i + 1)[0], start + MAX_LINE_MS) : start + TAIL_LINE_MS;
            long[] words = null;
            if (text.words() != null) {
                words = new long[text.words().length];
                for (int w = 0; w < words.length; w++) words[w] = start + text.words()[w];
            }
            if (end > start) lines.add(new Line(start, end, text.text(), words));
        }
        return lines.isEmpty() ? Sheet.NONE : new Sheet(List.copyOf(lines));
    }

    /** Splits enhanced LRC's word stamps off a line's text; plain text comes back with no word timing. */
    private static Worded worded(String raw, long lineStart) {
        Matcher m = WORD_STAMP.matcher(raw);
        if (!m.find()) return new Worded(clip(raw), null);
        StringBuilder clean = new StringBuilder();
        List<Long> charStart = new ArrayList<>();
        long stamp = lineStart;
        int from = 0;
        m.reset();
        while (true) {
            boolean found = m.find();
            String piece = raw.substring(from, found ? m.start() : raw.length());
            for (int i = 0; i < piece.length(); i++) {
                clean.append(piece.charAt(i));
                charStart.add(stamp);
            }
            if (!found) break;
            stamp = millis(m);
            from = m.end();
        }
        return words(clean.toString(), charStart, lineStart);
    }

    /** The text, stripped and clipped, with the start of each word: the stamp of its first letter. */
    private static Worded words(String text, List<Long> charStart, long lineStart) {
        List<Long> starts = new ArrayList<>();
        boolean inWord = false;
        int lead = 0;
        while (lead < text.length() && Character.isWhitespace(text.charAt(lead))) lead++;
        String clean = clip(text);
        for (int i = lead; i < text.length() && i - lead < clean.length(); i++) {
            boolean space = Character.isWhitespace(text.charAt(i));
            if (!space && !inWord) starts.add(charStart.get(i) - lineStart);
            inWord = !space;
        }
        long[] words = new long[starts.size()];
        for (int i = 0; i < words.length; i++) words[i] = starts.get(i);
        return new Worded(clean, words.length == 0 ? null : words);
    }

    private static long millis(Matcher m) {
        long min = Long.parseLong(m.group(1)), sec = Long.parseLong(m.group(2)), frac = 0;
        String f = m.group(3);
        if (f != null) {
            long v = Long.parseLong(f);
            frac = switch (f.length()) {
                case 1 -> v * 100;
                case 2 -> v * 10;
                default -> v;
            };
        }
        return (min * 60 + sec) * 1000 + frac;
    }

    private static String clip(String s) {
        String t = s.strip();
        return t.length() > MAX_LINE_LENGTH ? t.substring(0, MAX_LINE_LENGTH) : t;
    }

    /** Spreads untimed lyrics over the song, each line getting time by its length, past a short intro. */
    static Sheet estimate(String plain, long seconds) {
        if (plain == null || plain.isBlank()) return Sheet.NONE;
        List<String> rows = new ArrayList<>();
        for (String raw : plain.split("\\R")) {
            String t = raw.strip();
            if (!t.isEmpty() && !t.startsWith("["))
                rows.add(t.length() > MAX_LINE_LENGTH ? t.substring(0, MAX_LINE_LENGTH) : t);
        }
        if (rows.isEmpty()) return Sheet.NONE;
        long total = seconds > 0 ? seconds * 1000 : 180_000;
        long intro = (long) (total * 0.06);
        long body = Math.max(1, (long) (total * (1 - 0.06 - 0.05)));
        long weight = 0;
        for (String r : rows) weight += Math.max(6, r.length());
        List<Line> lines = new ArrayList<>(rows.size());
        long at = intro;
        for (String r : rows) {
            long len = body * Math.max(6, r.length()) / weight;
            lines.add(new Line(at, at + len, r));
            at += len;
        }
        return new Sheet(List.copyOf(lines));
    }

    // ---- HTTP ----

    private static String get(String url, Map<String, String> headers) throws Exception {
        return exchange(url, headers, null);
    }

    private static String postJson(String url, Map<String, String> headers, String body) throws Exception {
        return exchange(url, headers, body);
    }

    private static String exchange(String url, Map<String, String> headers, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            c.setConnectTimeout(5000);
            c.setReadTimeout(8000);
            headers.forEach(c::setRequestProperty);
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(bytes);
                }
            }
            int code = c.getResponseCode();
            if (code == 404) return null;
            if (code != 200) throw new IllegalStateException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                return new String(in.readNBytes(4 << 20), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Counts requests so a provider only reports failure when every one of them failed. */
    private static final class Requests {
        private int made, failed;
        private Exception last;

        String get(String url, Map<String, String> headers) {
            made++;
            try {
                return Lyrics.get(url, headers);
            } catch (Exception e) {
                failed++;
                last = e;
                return null;
            }
        }

        void throwIfAllFailed() throws Exception {
            if (made > 0 && failed == made && last != null) throw last;
        }
    }

    private static String text(JsonObject o, String field) {
        JsonElement e = o.get(field);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) return null;
        String s = e.getAsString();
        return s.isBlank() ? null : s;
    }

    private static String string(JsonObject o, String field) {
        JsonElement e = o.get(field);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static double number(JsonObject o, String field) {
        JsonElement e = o.get(field);
        return e != null && !e.isJsonNull() && e.isJsonPrimitive() ? e.getAsDouble() : 0;
    }

    // ---- lrclib.net ----

    private static final class Lrclib {
        private static final String GET = "https://lrclib.net/api/get?artist_name=%s&track_name=%s&duration=%d";
        private static final String FIELDS = "https://lrclib.net/api/search?track_name=%s&artist_name=%s";
        private static final String TRACK = "https://lrclib.net/api/search?track_name=%s";
        private static final String QUERY = "https://lrclib.net/api/search?q=%s";
        private static final Map<String, String> HEADERS = Map.of("User-Agent", "ArcanicVisuals (https://arcanic.su)");
        private static final double SYNCED_BONUS = 0.08;

        static Result find(Query q) throws Exception {
            if (q.title().isEmpty()) return Result.NOT_FOUND;
            Requests http = new Requests();
            if (!q.artist().isEmpty() && q.seconds() > 0) {
                String body = http.get(String.format(GET, enc(q.artist()), enc(q.title()), q.seconds()), HEADERS);
                if (body != null) {
                    JsonElement e = JsonParser.parseString(body);
                    if (e.isJsonObject()) {
                        Result r = result(e.getAsJsonObject(), q.seconds());
                        if (r.origin().timed) return r;
                    }
                }
            }
            Set<String> urls = new LinkedHashSet<>();
            if (!q.artist().isEmpty()) {
                urls.add(String.format(FIELDS, enc(q.title()), enc(q.artist())));
                urls.add(String.format(FIELDS, enc(q.title()), enc(q.primaryArtist())));
            }
            urls.add(String.format(QUERY, enc(q.searchText())));
            urls.add(String.format(TRACK, enc(q.title())));
            JsonObject best = null;
            double bestScore = MIN_SCORE;
            for (String url : urls) {
                String body = http.get(url, HEADERS);
                if (body == null) continue;
                JsonElement e = JsonParser.parseString(body);
                if (!e.isJsonArray()) continue;
                for (JsonElement el : e.getAsJsonArray()) {
                    if (!el.isJsonObject()) continue;
                    JsonObject o = el.getAsJsonObject();
                    double s = score(o, q);
                    if (s > bestScore) {
                        bestScore = s;
                        best = o;
                    }
                }
                if (best != null && text(best, "syncedLyrics") != null) break;
            }
            if (best == null) {
                http.throwIfAllFailed();
                return Result.NOT_FOUND;
            }
            return result(best, q.seconds());
        }

        private static double score(JsonObject o, Query q) {
            JsonElement inst = o.get("instrumental");
            if (inst != null && inst.isJsonPrimitive() && inst.getAsBoolean()) return 0;
            boolean synced = text(o, "syncedLyrics") != null;
            if (!synced && text(o, "plainLyrics") == null) return 0;
            double s = Lyrics.score(text(o, "trackName"), text(o, "artistName"), Math.round(number(o, "duration")), q);
            return s > 0 && synced ? s + SYNCED_BONUS : s;
        }

        private static Result result(JsonObject o, long seconds) {
            long matched = Math.round(number(o, "duration"));
            String synced = text(o, "syncedLyrics");
            if (synced != null) {
                Sheet s = parseLrc(synced);
                if (!s.empty()) return new Result(s, Origin.LRCLIB, matched);
            }
            Sheet s = estimate(text(o, "plainLyrics"), seconds);
            return s.empty() ? Result.NOT_FOUND : new Result(s, Origin.LRCLIB_ESTIMATED, matched);
        }
    }

    // ---- YouTube Music ----

    private static final class YouTubeMusic {
        private static final String API = "https://music.youtube.com/youtubei/v1/";
        private static final String SUFFIX = "?prettyPrint=false";
        private static final String SONGS_FILTER = "EgWKAQIIAWoKEAoQCRADEAQQBQ==";
        private static final Map<String, String> WEB_HEADERS = Map.of(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36",
                "Origin",
                "https://music.youtube.com");
        private static final Map<String, String> ANDROID_HEADERS =
                Map.of("User-Agent", "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 14) gzip");
        private static final Pattern DURATION = Pattern.compile("(?:(\\d+):)?(\\d{1,2}):(\\d{2})");
        private static final String DOT = " • ";
        private static final int MAX_CANDIDATES = 6;

        private record Match(String videoId, long seconds) {}

        static Result find(Query q) throws Exception {
            if (q.title().isEmpty()) return Result.NOT_FOUND;
            Match m = bestMatch(q);
            if (m == null) return Result.NOT_FOUND;
            Result timed = timed(m.videoId(), m.seconds());
            return timed.found() ? timed : plain(m.videoId(), m.seconds() > 0 ? m.seconds() : q.seconds());
        }

        private static Match bestMatch(Query q) throws Exception {
            JsonObject req = request(webClient());
            req.addProperty("query", q.searchText());
            req.addProperty("params", SONGS_FILTER);
            String body = postJson(API + "search" + SUFFIX, WEB_HEADERS, req.toString());
            if (body == null) return null;
            List<JsonObject> items = new ArrayList<>();
            collect(JsonParser.parseString(body), "musicResponsiveListItemRenderer", items);
            Match best = null;
            double bestScore = MIN_SCORE;
            for (int i = 0; i < Math.min(items.size(), MAX_CANDIDATES); i++) {
                JsonObject item = items.get(i);
                String id = videoId(item);
                List<String> cols = columns(item);
                if (id == null || cols.isEmpty()) continue;
                String sub = cols.size() > 1 ? cols.get(1) : "";
                String artist = sub.contains(DOT) ? sub.substring(0, sub.indexOf(DOT)) : sub;
                long seconds = duration(sub);
                double s = score(cols.get(0), artist, seconds, q);
                if (s > bestScore) {
                    bestScore = s;
                    best = new Match(id, seconds);
                }
            }
            return best;
        }

        private static Result timed(String videoId, long seconds) throws Exception {
            String browseId = lyricsBrowseId(androidClient(), ANDROID_HEADERS, videoId);
            if (browseId == null) return Result.NOT_FOUND;
            JsonObject req = request(androidClient());
            req.addProperty("browseId", browseId);
            String body = postJson(API + "browse" + SUFFIX, ANDROID_HEADERS, req.toString());
            if (body == null) return Result.NOT_FOUND;
            List<JsonObject> owners = new ArrayList<>();
            collectOwners(JsonParser.parseString(body), "timedLyricsData", owners);
            if (owners.isEmpty()) return Result.NOT_FOUND;
            JsonArray data = owners.get(0).getAsJsonArray("timedLyricsData");
            List<Line> lines = new ArrayList<>(data.size());
            for (JsonElement el : data) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String line = string(o, "lyricLine");
                JsonObject cue = o.getAsJsonObject("cueRange");
                if (line == null || cue == null) continue;
                String text = line.strip();
                long start = parseLong(string(cue, "startTimeMilliseconds"));
                long end = parseLong(string(cue, "endTimeMilliseconds"));
                if (!text.isEmpty() && end > start) lines.add(new Line(start, end, text));
            }
            return lines.isEmpty()
                    ? Result.NOT_FOUND
                    : new Result(new Sheet(List.copyOf(lines)), Origin.YOUTUBE_MUSIC, seconds);
        }

        private static Result plain(String videoId, long seconds) throws Exception {
            String browseId = lyricsBrowseId(webClient(), WEB_HEADERS, videoId);
            if (browseId == null) return Result.NOT_FOUND;
            JsonObject req = request(webClient());
            req.addProperty("browseId", browseId);
            String body = postJson(API + "browse" + SUFFIX, WEB_HEADERS, req.toString());
            if (body == null) return Result.NOT_FOUND;
            List<JsonObject> shelves = new ArrayList<>();
            collect(JsonParser.parseString(body), "musicDescriptionShelfRenderer", shelves);
            if (shelves.isEmpty()) return Result.NOT_FOUND;
            Sheet s = estimate(runs(shelves.get(0).getAsJsonObject("description")), seconds);
            return s.empty() ? Result.NOT_FOUND : new Result(s, Origin.YOUTUBE_MUSIC_ESTIMATED, seconds);
        }

        private static String lyricsBrowseId(JsonObject client, Map<String, String> headers, String videoId)
                throws Exception {
            JsonObject req = request(client);
            req.addProperty("videoId", videoId);
            String body = postJson(API + "next" + SUFFIX, headers, req.toString());
            return body == null ? null : findLyricsBrowseId(JsonParser.parseString(body));
        }

        private static String findLyricsBrowseId(JsonElement e) {
            if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                String id = string(o, "browseId");
                if (id != null && id.startsWith("MPLY")) return id;
                for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                    String found = findLyricsBrowseId(en.getValue());
                    if (found != null) return found;
                }
            } else if (e.isJsonArray()) {
                for (JsonElement el : e.getAsJsonArray()) {
                    String found = findLyricsBrowseId(el);
                    if (found != null) return found;
                }
            }
            return null;
        }

        private static void collect(JsonElement e, String key, List<JsonObject> out) {
            if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                JsonElement hit = o.get(key);
                if (hit != null && hit.isJsonObject()) out.add(hit.getAsJsonObject());
                for (Map.Entry<String, JsonElement> en : o.entrySet()) collect(en.getValue(), key, out);
            } else if (e.isJsonArray()) {
                for (JsonElement el : e.getAsJsonArray()) collect(el, key, out);
            }
        }

        private static void collectOwners(JsonElement e, String key, List<JsonObject> out) {
            if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                JsonElement hit = o.get(key);
                if (hit != null && hit.isJsonArray()) {
                    out.add(o);
                    return;
                }
                for (Map.Entry<String, JsonElement> en : o.entrySet()) collectOwners(en.getValue(), key, out);
            } else if (e.isJsonArray()) {
                for (JsonElement el : e.getAsJsonArray()) collectOwners(el, key, out);
            }
        }

        private static String videoId(JsonObject item) {
            JsonObject data = item.getAsJsonObject("playlistItemData");
            return data == null ? null : string(data, "videoId");
        }

        private static List<String> columns(JsonObject item) {
            List<String> out = new ArrayList<>();
            JsonArray cols = item.getAsJsonArray("flexColumns");
            if (cols == null) return out;
            for (JsonElement el : cols) {
                if (!el.isJsonObject()) continue;
                JsonObject col = el.getAsJsonObject().getAsJsonObject("musicResponsiveListItemFlexColumnRenderer");
                out.add(col == null ? "" : runs(col.getAsJsonObject("text")));
            }
            return out;
        }

        private static String runs(JsonObject o) {
            if (o == null) return "";
            JsonArray runs = o.getAsJsonArray("runs");
            if (runs == null) return "";
            StringBuilder sb = new StringBuilder();
            for (JsonElement el : runs) {
                if (!el.isJsonObject()) continue;
                String t = string(el.getAsJsonObject(), "text");
                if (t != null) sb.append(t);
            }
            return sb.toString();
        }

        /** The last m:ss or h:mm:ss in a subtitle, in seconds. */
        private static long duration(String s) {
            Matcher m = DURATION.matcher(s);
            long out = 0;
            while (m.find()) {
                long h = m.group(1) == null ? 0 : Long.parseLong(m.group(1));
                out = h * 3600 + Long.parseLong(m.group(2)) * 60 + Long.parseLong(m.group(3));
            }
            return out;
        }

        private static long parseLong(String s) {
            if (s == null) return 0;
            try {
                return Long.parseLong(s.strip());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        private static JsonObject request(JsonObject client) {
            JsonObject ctx = new JsonObject();
            ctx.add("client", client);
            JsonObject req = new JsonObject();
            req.add("context", ctx);
            return req;
        }

        private static JsonObject webClient() {
            JsonObject c = new JsonObject();
            c.addProperty("clientName", "WEB_REMIX");
            c.addProperty("clientVersion", "1.20250101.01.00");
            c.addProperty("hl", "en");
            return c;
        }

        private static JsonObject androidClient() {
            JsonObject c = new JsonObject();
            c.addProperty("clientName", "ANDROID_MUSIC");
            c.addProperty("clientVersion", "7.27.52");
            c.addProperty("androidSdkVersion", 34);
            c.addProperty("osName", "Android");
            c.addProperty("osVersion", "14");
            c.addProperty("hl", "en");
            return c;
        }
    }

    // ---- NetEase Cloud Music ----

    private static final class NetEase {
        private static final String SEARCH = "https://music.163.com/api/cloudsearch/pc?type=1&limit=10&offset=0&s=%s";
        /** Both the line lyrics and, when the song has them, the word-by-word ones ("yrc"). */
        private static final String LYRIC =
                "https://music.163.com/api/song/lyric/v1?id=%d&cp=false&lv=0&kv=0&tv=0&rv=0&yv=0&ytv=0&yrv=0";
        /** A word-by-word line: {@code [start,duration]} and then {@code (start,duration,0)} before every piece. */
        private static final Pattern YRC_LINE = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");

        private static final Pattern YRC_PIECE = Pattern.compile("\\((\\d+),(\\d+),-?\\d+\\)");
        private static final Map<String, String> HEADERS = Map.of(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36",
                "Referer",
                "https://music.163.com/");
        private static final Pattern CREDIT_LINE = Pattern.compile(
                "^\\s*(?:作词|作曲|编曲|制作人|混音|母带|录音|和声|吉他|贝斯|鼓|监制|出品|发行|OP|SP|Lyrics|Composer|Producer|Arranger)\\s*[:：].*$",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

        static Result find(Query q) throws Exception {
            if (q.title().isEmpty()) return Result.NOT_FOUND;
            String body = get(String.format(SEARCH, enc(q.searchText())), HEADERS);
            if (body == null) return Result.NOT_FOUND;
            JsonObject result = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("result");
            JsonArray songs = result == null ? null : result.getAsJsonArray("songs");
            if (songs == null) return Result.NOT_FOUND;
            long bestId = -1, bestSeconds = 0;
            double bestScore = MIN_SCORE;
            for (JsonElement el : songs) {
                if (!el.isJsonObject()) continue;
                JsonObject song = el.getAsJsonObject();
                long seconds = song.has("dt") ? song.get("dt").getAsLong() / 1000 : 0;
                double s = score(string(song, "name"), artists(song), seconds, q);
                if (s > bestScore) {
                    bestScore = s;
                    bestId = song.get("id").getAsLong();
                    bestSeconds = seconds;
                }
            }
            if (bestId < 0) return Result.NOT_FOUND;
            String lyric = get(String.format(LYRIC, bestId), HEADERS);
            if (lyric == null) return Result.NOT_FOUND;
            JsonObject root = JsonParser.parseString(lyric).getAsJsonObject();
            JsonObject yrc = root.getAsJsonObject("yrc");
            Sheet s = withoutCredits(parseYrc(yrc == null ? null : string(yrc, "lyric")));
            if (s.empty()) {
                JsonObject lrc = root.getAsJsonObject("lrc");
                s = withoutCredits(parseLrc(lrc == null ? null : string(lrc, "lyric")));
            }
            return s.empty() ? Result.NOT_FOUND : new Result(s, Origin.NETEASE, bestSeconds);
        }

        /** NetEase's word-by-word lyrics: every piece of a line has its own start; credit lines (JSON) are skipped. */
        static Sheet parseYrc(String yrc) {
            if (yrc == null || yrc.isBlank()) return Sheet.NONE;
            List<Line> lines = new ArrayList<>();
            for (String raw : yrc.split("\\R")) {
                Matcher line = YRC_LINE.matcher(raw.strip());
                if (!line.matches()) continue;
                long start = Long.parseLong(line.group(1));
                long length = Long.parseLong(line.group(2));
                String rest = line.group(3);
                Matcher piece = YRC_PIECE.matcher(rest);
                StringBuilder text = new StringBuilder();
                List<Long> charStart = new ArrayList<>();
                long at = start;
                int from = -1;
                while (piece.find()) {
                    if (from >= 0) {
                        for (int i = from; i < piece.start(); i++) {
                            text.append(rest.charAt(i));
                            charStart.add(at);
                        }
                    }
                    at = Long.parseLong(piece.group(1));
                    from = piece.end();
                }
                if (from >= 0) {
                    for (int i = from; i < rest.length(); i++) {
                        text.append(rest.charAt(i));
                        charStart.add(at);
                    }
                }
                Worded w = words(text.toString(), charStart, start);
                if (w.text().isEmpty() || length <= 0) continue;
                long[] words = null;
                if (w.words() != null) {
                    words = new long[w.words().length];
                    for (int i = 0; i < words.length; i++) words[i] = start + w.words()[i];
                }
                lines.add(new Line(start, start + Math.min(length, MAX_LINE_MS), w.text(), words));
            }
            lines.sort(Comparator.comparingLong(Line::start));
            return lines.isEmpty() ? Sheet.NONE : new Sheet(List.copyOf(lines));
        }

        private static Sheet withoutCredits(Sheet s) {
            List<Line> kept = new ArrayList<>(s.lines().size());
            for (Line l : s.lines()) if (!CREDIT_LINE.matcher(l.text()).matches()) kept.add(l);
            return kept.isEmpty() ? Sheet.NONE : new Sheet(List.copyOf(kept));
        }

        private static String artists(JsonObject song) {
            JsonArray ar = song.getAsJsonArray("ar");
            if (ar == null) return "";
            StringBuilder sb = new StringBuilder();
            for (JsonElement el : ar) {
                if (!el.isJsonObject()) continue;
                String name = string(el.getAsJsonObject(), "name");
                if (name == null) continue;
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(name);
            }
            return sb.toString();
        }
    }
}
