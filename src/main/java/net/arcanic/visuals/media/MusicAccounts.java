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
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Launcher;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.hud.Toasts;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Yandex Music / Spotify accounts as a lyrics source for World Lyrics.
 *
 * <p>No proper OAuth for a game client, so sign-in goes through the browser: Yandex hands the token in the redirect
 * URL, Spotify needs the sp_dc cookie. We watch the clipboard for either. Stored DPAPI-encrypted, see {@link Launcher}.
 */
public final class MusicAccounts {
    private MusicAccounts() {}

    private static final Logger LOG = LoggerFactory.getLogger("ArcanicLyrics");
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("arcanicvisuals-music.txt");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public enum Source {
        AUTO,
        YANDEX,
        SPOTIFY
    }

    private static volatile Source preferred = Source.AUTO;
    private static volatile String yandexToken = "";
    private static volatile String yandexName = "";
    private static volatile String spotifyCookie = "";
    private static volatile String spotifyName = "";

    private enum Waiting {
        NONE,
        YANDEX,
        SPOTIFY
    }

    private static Waiting waiting = Waiting.NONE;
    private static long waitUntil;
    private static int ticks;
    private static String lastClip = "";

    public static Source preferred() {
        return preferred;
    }

    public static void prefer(Source s) {
        if (s != preferred) {
            preferred = s;
            Lyrics.forget();
        }
    }

    public static boolean yandex() {
        return !yandexToken.isEmpty();
    }

    public static boolean spotify() {
        return !spotifyCookie.isEmpty();
    }

    public static String yandexName() {
        return yandexName;
    }

    public static String spotifyName() {
        return spotifyName;
    }

    public static boolean waitingYandex() {
        return waiting == Waiting.YANDEX;
    }

    public static boolean waitingSpotify() {
        return waiting == Waiting.SPOTIFY;
    }

    // ---- storage

    public static void load() {
        try {
            if (!Files.isRegularFile(FILE)) return;
            String plain = Launcher.unseal(
                    Files.readString(FILE, StandardCharsets.UTF_8).strip());
            if (plain.isEmpty()) return;
            JsonObject o = JsonParser.parseString(plain).getAsJsonObject();
            yandexToken = str(o, "yandex");
            yandexName = str(o, "yandexName");
            spotifyCookie = str(o, "spotify");
            spotifyName = str(o, "spotifyName");
        } catch (IOException | RuntimeException e) {
            LOG.warn("Cannot read the music accounts", e);
        }
    }

    private static synchronized void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("yandex", yandexToken);
            o.addProperty("yandexName", yandexName);
            o.addProperty("spotify", spotifyCookie);
            o.addProperty("spotifyName", spotifyName);
            if (yandexToken.isEmpty() && spotifyCookie.isEmpty()) {
                Files.deleteIfExists(FILE);
            } else {
                String sealed = Launcher.seal(o.toString());
                if (sealed.isEmpty()) throw new IOException("cannot encrypt");
                Files.writeString(FILE, sealed, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOG.warn("Cannot save the music accounts", e);
        }
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    // ---- sign-in

    private static final String YANDEX_CLIENT = "23cabbbdc6cd418abb4b39c32c41195d";
    private static final String YANDEX_AUTH =
            "https://oauth.yandex.ru/authorize?response_type=token&client_id=" + YANDEX_CLIENT;
    private static final Pattern YANDEX_TOKEN = Pattern.compile("access_token=([A-Za-z0-9_\\-.]{20,})");
    private static final Pattern YANDEX_BARE = Pattern.compile("^(y0_[A-Za-z0-9_\\-]{20,}|AQAA[A-Za-z0-9_\\-]{20,})$");
    private static final Pattern SP_DC = Pattern.compile("(?:sp_dc\\s*[=:]\\s*)?\"?([A-Za-z0-9_\\-]{100,})\"?");

    public static void yandexButton() {
        if (yandex()) {
            yandexToken = "";
            yandexName = "";
            save();
            Lyrics.forget();
            UiSound.play("drain");
            Toasts.setting(Lang.tr("Яндекс Музыка отключена"), false);
            return;
        }
        lastClip = clipboard();
        waiting = Waiting.YANDEX;
        waitUntil = System.currentTimeMillis() + 5 * 60_000L;
        Util.getPlatform().openUri(YANDEX_AUTH);
        UiSound.play("pop");
        Toasts.setting(Lang.tr("Войди в Яндекс и скопируй адрес страницы, которая откроется"), true);
    }

    public static void spotifyButton() {
        if (spotify()) {
            spotifyCookie = "";
            spotifyName = "";
            spotifyToken = null;
            save();
            Lyrics.forget();
            UiSound.play("drain");
            Toasts.setting(Lang.tr("Spotify отключён"), false);
            return;
        }
        lastClip = clipboard();
        waiting = Waiting.SPOTIFY;
        waitUntil = System.currentTimeMillis() + 5 * 60_000L;
        Util.getPlatform().openUri("https://open.spotify.com");
        UiSound.play("pop");
        Toasts.setting(Lang.tr("Войди в Spotify и скопируй значение куки sp_dc"), true);
    }

    /** Yandex redirect URL / token or Spotify sp_dc. False if it's neither. */
    public static boolean paste(String text) {
        if (text == null) return false;
        String s = text.strip();
        Matcher m = YANDEX_TOKEN.matcher(s);
        if (m.find()) {
            checkYandex(m.group(1));
            return true;
        }
        if (YANDEX_BARE.matcher(s).matches()) {
            checkYandex(s);
            return true;
        }
        Matcher c = SP_DC.matcher(s);
        if ((s.contains("sp_dc") || waiting == Waiting.SPOTIFY) && c.matches()) {
            checkSpotify(c.group(1));
            return true;
        }
        return false;
    }

    /** Polls the clipboard once a second while a sign-in is pending. */
    public static void tick() {
        if (waiting == Waiting.NONE) return;
        if (System.currentTimeMillis() > waitUntil) {
            waiting = Waiting.NONE;
            return;
        }
        if (++ticks % 20 != 0) return;
        String clip = clipboard();
        if (clip.equals(lastClip) || clip.isBlank()) return;
        lastClip = clip;
        if (clip.length() < 4000) paste(clip);
    }

    private static String clipboard() {
        try {
            String s = Minecraft.getInstance().keyboardHandler.getClipboard();
            return s == null ? "" : s;
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static void checkYandex(String token) {
        waiting = Waiting.NONE;
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject o = json(get("https://api.music.yandex.net/account/status", yandexHeaders(token)));
                JsonObject account = o.getAsJsonObject("result").getAsJsonObject("account");
                String login = account.has("login") ? account.get("login").getAsString() : "";
                if (login.isEmpty() && account.has("displayName"))
                    login = account.get("displayName").getAsString();
                if (!account.has("uid")) throw new IOException("not signed in");
                String shown = login;
                Minecraft.getInstance().execute(() -> {
                    yandexToken = token;
                    yandexName = shown;
                    save();
                    Lyrics.forget();
                    UiSound.play("select");
                    Toasts.setting(Lang.tr("Яндекс Музыка подключена") + (shown.isEmpty() ? "" : ": " + shown), true);
                });
            } catch (Exception e) {
                LOG.warn("Yandex Music sign-in failed", e);
                Minecraft.getInstance().execute(() -> {
                    UiSound.play("reset");
                    Toasts.setting(Lang.tr("Яндекс не принял токен, попробуй войти ещё раз"), false);
                });
            }
        });
    }

    private static void checkSpotify(String cookie) {
        waiting = Waiting.NONE;
        CompletableFuture.runAsync(() -> {
            try {
                String token = spotifyToken(cookie, true);
                JsonObject me = json(get("https://api.spotify.com/v1/me", Map.of("Authorization", "Bearer " + token)));
                String name = me.has("display_name") && !me.get("display_name").isJsonNull()
                        ? me.get("display_name").getAsString()
                        : str(me, "id");
                Minecraft.getInstance().execute(() -> {
                    spotifyCookie = cookie;
                    spotifyName = name;
                    save();
                    Lyrics.forget();
                    UiSound.play("select");
                    Toasts.setting(Lang.tr("Spotify подключён") + (name.isEmpty() ? "" : ": " + name), true);
                });
            } catch (Exception e) {
                LOG.warn("Spotify sign-in failed", e);
                spotifyToken = null;
                Minecraft.getInstance().execute(() -> {
                    UiSound.play("reset");
                    Toasts.setting(Lang.tr("Spotify не принял sp_dc, скопируй куку ещё раз"), false);
                });
            }
        });
    }

    // ---- HTTP

    private static String get(String url, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET();
        headers.forEach(b::header);
        HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (r.statusCode() == 404) return null;
        if (r.statusCode() != 200)
            throw new IOException(
                    "HTTP " + r.statusCode() + " from " + URI.create(url).getHost());
        return r.body();
    }

    private static JsonObject json(String body) throws IOException {
        if (body == null) throw new IOException("not found");
        return JsonParser.parseString(body).getAsJsonObject();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // ---- Yandex Music

    private static final String YANDEX_SIGN_KEY = "p93jhgh689SBReK6ghtw62";

    private static Map<String, String> yandexHeaders(String token) {
        return Map.of("Authorization", "OAuth " + token, "X-Yandex-Music-Client", "YandexMusicAndroid/24023621");
    }

    static Lyrics.Result yandexLyrics(Lyrics.Query q) throws Exception {
        String token = yandexToken;
        if (token.isEmpty() || q.title().isEmpty()) return Lyrics.Result.NOT_FOUND;
        Map<String, String> h = yandexHeaders(token);
        JsonObject found = json(get(
                "https://api.music.yandex.net/search?type=track&page=0&nocorrect=false&text=" + enc(q.searchText()),
                h));
        JsonObject tracks = found.getAsJsonObject("result").getAsJsonObject("tracks");
        if (tracks == null) return Lyrics.Result.NOT_FOUND;
        JsonObject best = null;
        double bestScore = Lyrics.MIN_SCORE;
        for (JsonElement e : tracks.getAsJsonArray("results")) {
            JsonObject t = e.getAsJsonObject();
            JsonObject info = t.getAsJsonObject("lyricsInfo");
            boolean any =
                    info != null && (bool(info, "hasAvailableSyncLyrics") || bool(info, "hasAvailableTextLyrics"));
            if (!any) continue;
            double s = Lyrics.score(str(t, "title"), artists(t.getAsJsonArray("artists")), millis(t, "durationMs"), q);
            if (s > 0 && bool(info, "hasAvailableSyncLyrics")) s += 0.08;
            if (s > bestScore) {
                bestScore = s;
                best = t;
            }
        }
        if (best == null) return Lyrics.Result.NOT_FOUND;
        String id = best.get("id").getAsString();
        long seconds = millis(best, "durationMs");
        boolean synced = bool(best.getAsJsonObject("lyricsInfo"), "hasAvailableSyncLyrics");
        long ts = System.currentTimeMillis() / 1000;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(YANDEX_SIGN_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sign = Base64.getEncoder().encodeToString(mac.doFinal((id + ts).getBytes(StandardCharsets.UTF_8)));
        JsonObject lyrics = json(get(
                "https://api.music.yandex.net/tracks/" + id + "/lyrics?format=" + (synced ? "LRC" : "TEXT")
                        + "&timeStamp=" + ts + "&sign=" + enc(sign),
                h));
        String url = str(lyrics.getAsJsonObject("result"), "downloadUrl");
        if (url.isEmpty()) return Lyrics.Result.NOT_FOUND;
        String text = get(url, Map.of("Authorization", "OAuth " + token));
        if (text == null || text.isBlank()) return Lyrics.Result.NOT_FOUND;
        if (synced) {
            Lyrics.Sheet sheet = Lyrics.parseLrc(text);
            if (!sheet.empty()) return new Lyrics.Result(sheet, Lyrics.Origin.YANDEX, seconds);
        }
        Lyrics.Sheet plain = Lyrics.estimate(text, q.seconds() > 0 ? q.seconds() : seconds);
        return plain.empty()
                ? Lyrics.Result.NOT_FOUND
                : new Lyrics.Result(plain, Lyrics.Origin.YANDEX_ESTIMATED, seconds);
    }

    private static boolean bool(JsonObject o, String k) {
        JsonElement e = o == null ? null : o.get(k);
        return e != null && e.isJsonPrimitive() && e.getAsBoolean();
    }

    private static long millis(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? Math.round(e.getAsDouble() / 1000.0) : 0;
    }

    private static String artists(JsonArray a) {
        if (a == null) return "";
        List<String> names = new ArrayList<>();
        for (JsonElement e : a) {
            if (e.isJsonObject() && e.getAsJsonObject().has("name"))
                names.add(e.getAsJsonObject().get("name").getAsString());
        }
        return String.join(", ", names);
    }

    // ---- Spotify

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    /** Web player TOTP secrets, community-maintained, Spotify rotates them. */
    private static final String SECRETS =
            "https://raw.githubusercontent.com/xyloflake/spot-secrets-go/refs/heads/main/secrets/secretDict.json";

    private static volatile String spotifyToken;
    private static volatile long spotifyTokenUntil;
    private static volatile String secret;
    private static volatile String secretVersion;
    private static volatile long secretAt;

    private static synchronized String spotifyToken(String cookie, boolean fresh) throws Exception {
        long now = System.currentTimeMillis();
        if (!fresh && spotifyToken != null && now < spotifyTokenUntil - 60_000L) return spotifyToken;
        if (secret == null || now - secretAt > 6 * 3_600_000L) loadSecret();
        JsonObject time = json(get("https://open.spotify.com/api/server-time", Map.of("User-Agent", UA)));
        long serverTime = time.get("serverTime").getAsLong();
        String totp = totp(secret, serverTime);
        String url = "https://open.spotify.com/api/token?reason=transport&productType=web-player&totp=" + totp
                + "&totpVer=" + secretVersion + "&ts=" + now / 1000;
        JsonObject o = json(get(url, Map.of("User-Agent", UA, "Cookie", "sp_dc=" + cookie)));
        if (bool(o, "isAnonymous") || !o.has("accessToken")) throw new IOException("sp_dc was not accepted");
        spotifyToken = o.get("accessToken").getAsString();
        spotifyTokenUntil = o.has("accessTokenExpirationTimestampMs")
                ? o.get("accessTokenExpirationTimestampMs").getAsLong()
                : now + 30 * 60_000L;
        return spotifyToken;
    }

    private static void loadSecret() throws Exception {
        JsonObject all = json(get(SECRETS, Map.of("User-Agent", UA)));
        String version = null;
        for (String k : all.keySet()) version = k; // newest is last
        if (version == null) throw new IOException("no Spotify secret");
        StringBuilder b = new StringBuilder();
        JsonArray bytes = all.getAsJsonArray(version);
        for (int i = 0; i < bytes.size(); i++) b.append(bytes.get(i).getAsInt() ^ (i % 33 + 9));
        secret = b.toString();
        secretVersion = version;
        secretAt = System.currentTimeMillis();
    }

    /** RFC 6238: HMAC-SHA1 over the 30-second step, six digits. */
    private static String totp(String key, long seconds) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(seconds / 30).array());
        int off = h[h.length - 1] & 0x0F;
        int bin = (h[off] & 0x7F) << 24 | (h[off + 1] & 0xFF) << 16 | (h[off + 2] & 0xFF) << 8 | h[off + 3] & 0xFF;
        return String.format("%06d", bin % 1_000_000);
    }

    static Lyrics.Result spotifyLyrics(Lyrics.Query q) throws Exception {
        String cookie = spotifyCookie;
        if (cookie.isEmpty() || q.title().isEmpty()) return Lyrics.Result.NOT_FOUND;
        String token = spotifyToken(cookie, false);
        Map<String, String> auth = Map.of("Authorization", "Bearer " + token, "User-Agent", UA);
        String query = q.artist().isEmpty() ? q.title() : "track:" + q.title() + " artist:" + q.primaryArtist();
        JsonObject found = json(get("https://api.spotify.com/v1/search?type=track&limit=8&q=" + enc(query), auth));
        JsonObject best = null;
        double bestScore = Lyrics.MIN_SCORE;
        for (JsonElement e : found.getAsJsonObject("tracks").getAsJsonArray("items")) {
            JsonObject t = e.getAsJsonObject();
            double s = Lyrics.score(str(t, "name"), artists(t.getAsJsonArray("artists")), millis(t, "duration_ms"), q);
            if (s > bestScore) {
                bestScore = s;
                best = t;
            }
        }
        if (best == null) return Lyrics.Result.NOT_FOUND;
        long seconds = millis(best, "duration_ms");
        String body = get(
                "https://spclient.wg.spotify.com/color-lyrics/v2/track/" + str(best, "id")
                        + "?format=json&market=from_token",
                Map.of("Authorization", "Bearer " + token, "App-Platform", "WebPlayer", "User-Agent", UA));
        if (body == null) return Lyrics.Result.NOT_FOUND;
        JsonObject lyrics = json(body).getAsJsonObject("lyrics");
        if (lyrics == null) return Lyrics.Result.NOT_FOUND;
        boolean synced = !"UNSYNCED".equals(str(lyrics, "syncType"));
        StringBuilder lrc = new StringBuilder();
        StringBuilder plain = new StringBuilder();
        for (JsonElement e : lyrics.getAsJsonArray("lines")) {
            JsonObject l = e.getAsJsonObject();
            String words = str(l, "words").strip();
            if (words.isEmpty() || words.equals("♪")) continue;
            long ms = Long.parseLong(str(l, "startTimeMs").isEmpty() ? "0" : str(l, "startTimeMs"));
            lrc.append(String.format("[%02d:%02d.%02d]%s%n", ms / 60_000, ms / 1000 % 60, ms % 1000 / 10, words));
            plain.append(words).append('\n');
        }
        if (synced) {
            Lyrics.Sheet sheet = Lyrics.parseLrc(lrc.toString());
            if (!sheet.empty()) return new Lyrics.Result(sheet, Lyrics.Origin.SPOTIFY, seconds);
        }
        Lyrics.Sheet est = Lyrics.estimate(plain.toString(), q.seconds() > 0 ? q.seconds() : seconds);
        return est.empty() ? Lyrics.Result.NOT_FOUND : new Lyrics.Result(est, Lyrics.Origin.SPOTIFY_ESTIMATED, seconds);
    }
}
