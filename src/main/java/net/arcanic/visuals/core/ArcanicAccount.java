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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

/** The Arcanic account inside the game. */
public final class ArcanicAccount {
    private ArcanicAccount() {}

    public enum State {
        SIGNED_OUT,
        WAITING,
        LOADING,
        SIGNED_IN
    }

    private static final Path SAVED = FabricLoader.getInstance().getConfigDir().resolve("arcanicvisuals-account.txt");
    // read by the launcher: "in", the token and the login after a sign-in here; "out" and the token after a sign-out
    private static final Path FOR_LAUNCHER = Path.of(System.getProperty("user.home"), ".arcanic", "game-session.txt");
    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static volatile State state = State.SIGNED_OUT;
    private static volatile String name = "", plan = "", note = "";
    private static volatile boolean noteBad, redeeming, cancel, asked;

    public static State state() {
        return state;
    }

    public static String name() {
        return name;
    }

    public static boolean premium() {
        return plan.equals("premium") || plan.equals("beta");
    }

    public static String planName() {
        return premium() ? "Premium" : "Free";
    }

    public static String note() {
        return note;
    }

    public static boolean noteBad() {
        return noteBad;
    }

    public static boolean redeeming() {
        return redeeming;
    }

    static String savedToken() {
        try {
            return Files.exists(SAVED) ? Launcher.unseal(Files.readString(SAVED).trim()) : "";
        } catch (IOException e) {
            return "";
        }
    }

    private static void say(String text, boolean bad) {
        note = text;
        noteBad = bad;
    }

    /** Asks the server whose the token is and on which plan, once per start. */
    public static void refresh() {
        if (asked) return;
        asked = true;
        if (!Sync.signedIn()) {
            state = State.SIGNED_OUT;
            return;
        }
        state = State.LOADING;
        call("GET", "/api/v1/me", null, Sync.token()).whenComplete((r, e) -> {
            if (e == null && r.statusCode() == 200)
                profile(JsonParser.parseString(r.body()).getAsJsonObject());
            else if (e == null && r.statusCode() == 401) {
                Sync.setToken("");
                forget();
                state = State.SIGNED_OUT;
            } else {
                state = State.SIGNED_IN; // offline: still signed in, the plan is not known for now
                say("Сервер недоступен", true);
                asked = false;
            }
        });
    }

    private static void profile(JsonObject o) {
        if (o.has("name")) name = o.get("name").getAsString();
        plan = o.has("plan") ? o.get("plan").getAsString() : "free";
        state = State.SIGNED_IN;
    }

    private static CompletableFuture<HttpResponse<String>> call(
            String method, String path, JsonObject body, String token) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(Sync.api() + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json");
        if (token != null && !token.isEmpty()) b.header("Authorization", "Bearer " + token);
        b.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body.toString()));
        return HTTP.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Opens the site's sign-in page and waits for it to hand a one-time code back; a second call cancels. */
    public static void signIn() {
        if (state == State.WAITING) {
            cancel = true;
            return;
        }
        if (state != State.SIGNED_OUT) return;
        cancel = false;
        state = State.WAITING;
        say("Заверши вход на сайте", false);
        Thread t = new Thread(ArcanicAccount::waitForCode, "Arcanic sign-in");
        t.setDaemon(true);
        t.start();
    }

    private static void waitForCode() {
        String code = null;
        try (ServerSocket server = new ServerSocket(0, 4, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(250);
            byte[] random = new byte[16];
            new SecureRandom().nextBytes(random);
            String expected = HexFormat.of().formatHex(random);
            Util.getPlatform().openUri(Sync.api() + "/login/?port=" + server.getLocalPort() + "&state=" + expected);
            long deadline = System.currentTimeMillis() + 10 * 60 * 1000;
            while (code == null && !cancel && System.currentTimeMillis() < deadline) {
                try (Socket client = server.accept()) {
                    client.setSoTimeout(3000);
                    code = answer(client, expected);
                } catch (SocketTimeoutException ignored) {
                    // nothing yet: check the cancel flag and wait again
                }
            }
        } catch (IOException e) {
            failed("Не удалось начать вход");
            return;
        }
        if (cancel) failed("Вход отменён");
        else if (code == null) failed("Время входа истекло");
        else exchange(code);
    }

    private static String answer(Socket client, String expected) throws IOException {
        InputStream in = client.getInputStream();
        StringBuilder head = new StringBuilder();
        int c;
        while ((c = in.read()) != -1 && head.length() < 8192) {
            head.append((char) c);
            if (head.length() >= 4 && head.substring(head.length() - 4).equals("\r\n\r\n")) break;
        }
        String line = head.toString().split("\r\n", 2)[0];
        int end = line.startsWith("GET ") ? line.indexOf(' ', 4) : -1;
        String target = end > 0 ? line.substring(4, end) : "";
        String code = null;
        if (target.startsWith("/?")) {
            Map<String, String> q = new HashMap<>();
            for (String pair : target.substring(2).split("&")) {
                int at = pair.indexOf('=');
                if (at > 0)
                    q.put(
                            URLDecoder.decode(pair.substring(0, at), StandardCharsets.UTF_8),
                            URLDecoder.decode(pair.substring(at + 1), StandardCharsets.UTF_8));
            }
            String got = q.get("code");
            if (expected.equals(q.get("state")) && got != null && !got.isEmpty()) code = got;
        }
        String reply = target.startsWith("/?")
                ? "HTTP/1.1 302 Found\r\nLocation: " + Sync.api() + "/login/?" + (code == null ? "failed=1" : "done=1")
                        + "\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\n"
                        + "Content-Length: 0\r\n\r\n"
                : "HTTP/1.1 404 Not Found\r\nConnection: close\r\nContent-Length: 0\r\n\r\n";
        OutputStream out = client.getOutputStream();
        out.write(reply.getBytes(StandardCharsets.US_ASCII));
        out.flush();
        return code;
    }

    private static void exchange(String code) {
        JsonObject body = new JsonObject();
        body.addProperty("code", code);
        call("POST", "/api/v1/exchange", body, null).whenComplete((r, e) -> {
            if (e != null) failed("Нет связи с сервером");
            else if (r.statusCode() != 200) failed("Сайт вернул неверный ответ");
            else signedIn(JsonParser.parseString(r.body()).getAsJsonObject());
        });
    }

    private static void failed(String why) {
        state = State.SIGNED_OUT;
        say(why, !why.equals("Вход отменён"));
    }

    private static void signedIn(JsonObject session) {
        String token = session.get("token").getAsString();
        Sync.setToken(token);
        try {
            Files.writeString(SAVED, Launcher.seal(token));
        } catch (IOException ignored) {
            // kept for this start only
        }
        profile(session);
        tellLauncher("in", token, name);
        say("Вход в Arcanic выполнен", false);
    }

    public static void signOut() {
        if (state != State.SIGNED_IN) return;
        String token = Sync.token();
        call("POST", "/api/v1/logout", new JsonObject(), token);
        Sync.setToken("");
        forget();
        tellLauncher("out", token, "");
        name = "";
        plan = "";
        state = State.SIGNED_OUT;
        say("Ты вышел из аккаунта Arcanic", false);
    }

    private static void tellLauncher(String what, String token, String login) {
        try {
            Files.createDirectories(FOR_LAUNCHER.getParent());
            Files.writeString(FOR_LAUNCHER, String.join("\n", what, Launcher.seal(token), login) + "\n");
        } catch (IOException ignored) {
            // the launcher keeps what it had
        }
    }

    private static void forget() {
        try {
            Files.deleteIfExists(SAVED);
        } catch (IOException ignored) {
            // nothing was kept
        }
    }

    /** Redeems a Premium key for this account. */
    public static void redeem(String key) {
        if (redeeming || state != State.SIGNED_IN) return;
        if (key.replaceAll("[^A-Za-z0-9]", "").length() != 16) {
            say("Ключ: 16 латинских букв и цифр", true);
            return;
        }
        redeeming = true;
        say("", false);
        JsonObject body = new JsonObject();
        body.addProperty("key", key);
        call("POST", "/api/v1/redeem", body, Sync.token()).whenComplete((r, e) -> {
            redeeming = false;
            if (e != null) say("Нет связи с сервером", true);
            else if (r.statusCode() == 200) {
                profile(JsonParser.parseString(r.body()).getAsJsonObject());
                say("Premium активирован", false);
            } else
                say(
                        switch (r.statusCode()) {
                            case 404 -> "Такого ключа нет";
                            case 409 -> "Этот ключ уже использован";
                            case 429 -> "Слишком много попыток. Подожди немного";
                            case 401 -> "Войди в аккаунт заново";
                            default -> "Сервер ответил " + r.statusCode();
                        },
                        true);
        });
    }
}
