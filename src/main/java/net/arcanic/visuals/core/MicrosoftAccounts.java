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

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/** Uses the same Windows DPAPI vault and OAuth implementation as Arcanic Launcher. */
public final class MicrosoftAccounts {
    public record Profile(String name, String uuid) {}

    public record Session(String name, UUID uuid, String access, String xuid) {
        @Override
        public String toString() {
            return "Microsoft session (redacted)";
        }
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Arcanic Microsoft");
        t.setDaemon(true);
        return t;
    });
    private static volatile List<Profile> profiles = List.of();
    private static volatile boolean busy;
    private static volatile Process process;
    private static volatile boolean cancelled;

    private MicrosoftAccounts() {}

    public static List<Profile> list() {
        return profiles;
    }

    public static boolean busy() {
        return busy;
    }

    public static void cancel() {
        cancelled = true;
        Process p = process;
        if (p != null) p.destroyForcibly();
    }

    private static Path helper() throws IOException {
        Path exe = Launcher.exe();
        if (exe == null) throw new IOException("Для входа Microsoft нужен лаунчер Arcanic");
        return exe;
    }

    private static JsonObject call(String... arguments) throws Exception {
        if (cancelled) throw new IOException("Вход отменён");
        List<String> command = new ArrayList<>();
        command.add(helper().toString());
        command.addAll(List.of(arguments));
        Process p = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        process = p;
        // stdout is a private parent-child pipe, never a log file. It contains a short-lived game token only.
        CompletableFuture<byte[]> output = CompletableFuture.supplyAsync(() -> {
            try {
                return p.getInputStream().readNBytes(128 * 1024);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });
        try {
            if (cancelled) p.destroyForcibly();
            if (!p.waitFor(11, TimeUnit.MINUTES)) {
                p.destroyForcibly();
                throw new IOException("Время ожидания входа истекло");
            }
            if (cancelled) throw new IOException("Вход отменён");
            byte[] bytes = output.get(5, TimeUnit.SECONDS);
            JsonObject result;
            try {
                result = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
                        .getAsJsonObject();
            } catch (Exception e) {
                throw new IOException("Модуль Microsoft не вернул ответ");
            } finally {
                Arrays.fill(bytes, (byte) 0);
            }
            if (result.has("error")) throw new IOException(result.get("error").getAsString());
            if (p.exitValue() != 0) throw new IOException("Не удалось выполнить вход Microsoft");
            return result;
        } finally {
            process = null;
            if (p.isAlive()) p.destroyForcibly();
            p.getInputStream().close();
        }
    }

    private static void reload() throws Exception {
        List<Profile> next = new ArrayList<>();
        for (JsonElement e : call("--account-list").getAsJsonArray("accounts")) {
            JsonObject j = e.getAsJsonObject();
            next.add(new Profile(j.get("name").getAsString(), j.get("uuid").getAsString()));
        }
        profiles = List.copyOf(next);
    }

    private static Session session(JsonObject reply) {
        JsonObject a = reply.getAsJsonObject("account");
        String raw = a.get("uuid").getAsString();
        UUID uuid = UUID.fromString(raw.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
        return new Session(
                a.get("name").getAsString(),
                uuid,
                a.get("access").getAsString(),
                a.has("xuid") ? a.get("xuid").getAsString() : "0");
    }

    @FunctionalInterface
    private interface Work {
        LocalAccounts.OnlineSession run() throws Exception;
    }

    private static synchronized void run(Work work, Consumer<String> done) {
        if (busy) return;
        busy = true;
        cancelled = false;
        EXECUTOR.execute(() -> {
            LocalAccounts.OnlineSession next = null;
            String error = null;
            try {
                next = work.run();
            } catch (Exception e) {
                error = e.getMessage() == null ? "Не удалось выполнить вход Microsoft" : e.getMessage();
            }
            LocalAccounts.OnlineSession apply = next;
            String failure = error;
            Minecraft.getInstance().execute(() -> {
                String result = failure;
                try {
                    if (result == null && apply != null)
                        result = cancelled ? "Вход отменён" : LocalAccounts.applyMicrosoft(apply);
                } catch (Exception e) {
                    result = "Не удалось переключить профиль";
                } finally {
                    busy = false;
                }
                done.accept(result);
            });
        });
    }

    public static void refreshList(Consumer<String> done) {
        run(
                () -> {
                    reload();
                    return null;
                },
                done);
    }

    public static void login(Consumer<String> done) {
        run(
                () -> {
                    Session a = session(call("--account-login"));
                    reload();
                    return LocalAccounts.prepareMicrosoft(a);
                },
                done);
    }

    public static void select(String uuid, Consumer<String> done) {
        run(
                () -> {
                    Session a = session(call("--account-refresh", uuid));
                    reload();
                    return LocalAccounts.prepareMicrosoft(a);
                },
                done);
    }

    public static void remove(String uuid, Consumer<String> done) {
        run(
                () -> {
                    call("--account-remove", uuid);
                    reload();
                    return null;
                },
                done);
    }
}
