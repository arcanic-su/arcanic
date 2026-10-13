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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.arcanic.visuals.mixin.MinecraftSessionAccess;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.server.Services;

/** Persists nicknames only. */
public final class LocalAccounts {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("arcanicvisuals/accounts.json");
    private static final List<String> profiles = new ArrayList<>();
    private static boolean loaded;
    private static Snapshot launch;
    private static boolean local, changed;
    private static String onlineUuid;

    private LocalAccounts() {}

    public static List<String> list() {
        if (!loaded) {
            loaded = true;
            if (Files.exists(FILE))
                try {
                    List<String> names =
                            new Gson().fromJson(Files.readString(FILE), new TypeToken<List<String>>() {}.getType());
                    if (names != null)
                        for (String n : names)
                            if (valid(n) && profiles.stream().noneMatch(p -> p.equalsIgnoreCase(n))) profiles.add(n);
                } catch (Exception ex) {
                    System.err.println("[ArcanicVisuals] accounts: " + ex);
                }
        }
        return List.copyOf(profiles);
    }

    public static boolean valid(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{3,16}");
    }

    public static String add(String raw) {
        String name = raw.trim();
        list();
        if (!valid(name)) return "Ник: 3–16 символов, A–Z, 0–9 и _";
        if (profiles.stream().anyMatch(n -> n.equalsIgnoreCase(name))) return "Этот профиль уже добавлен";
        profiles.add(name);
        try {
            save();
        } catch (Exception ex) {
            profiles.remove(name);
            return "Не удалось сохранить профили";
        }
        return null;
    }

    public static String remove(String name) {
        list();
        int at = profiles.indexOf(name);
        if (at < 0) return null;
        profiles.remove(at);
        try {
            save();
        } catch (Exception ex) {
            profiles.add(at, name);
            return "Не удалось сохранить профили";
        }
        return null;
    }

    private static void save() throws IOException {
        Files.createDirectories(FILE.getParent());
        Path temp = FILE.resolveSibling("accounts.json.tmp");
        Files.writeString(temp, new Gson().toJson(profiles), StandardCharsets.UTF_8);
        try {
            Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static boolean isLocal() {
        return local;
    }

    public static boolean isLauncher() {
        return !changed;
    }

    public static String onlineUuid() {
        return onlineUuid;
    }

    public record OnlineSession(
            User user,
            Services services,
            ProfileResult profile,
            UserApiService api,
            UserApiService.UserProperties properties) {}
    /** Network work is completed before changing any of Minecraft's active services. */
    public static OnlineSession prepareMicrosoft(MicrosoftAccounts.Session a) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        User user = new User(
                a.name(),
                a.uuid(),
                a.access(),
                a.xuid().equals("0") ? Optional.empty() : Optional.of(a.xuid()),
                Optional.of("dacf11f1-b22d-4b0d-a837-5504fd8af781"));
        YggdrasilAuthenticationService auth = new YggdrasilAuthenticationService(mc.getProxy());
        Services services = Services.create(auth, mc.gameDirectory);
        UserApiService api = auth.createUserApiService(a.access());
        ProfileResult profile = services.sessionService().fetchProfile(a.uuid(), true);
        if (profile == null) throw new IOException("Не удалось получить лицензионный профиль Minecraft");
        return new OnlineSession(user, services, profile, api, api.fetchProperties());
    }

    public static String applyMicrosoft(OnlineSession a) {
        Minecraft mc = Minecraft.getInstance();
        if (inWorld(mc)) return "Выйдите из мира, чтобы сменить профиль";
        MinecraftSessionAccess access = (MinecraftSessionAccess) mc;
        try {
            Snapshot next = new Snapshot(
                    a.user(),
                    a.services(),
                    CompletableFuture.completedFuture(a.profile()),
                    a.api(),
                    CompletableFuture.completedFuture(a.properties()),
                    new PlayerSocialManager(mc, a.api()),
                    new ClientTelemetryManager(mc, a.api(), a.user()),
                    ProfileKeyPairManager.create(a.api(), a.user(), mc.gameDirectory.toPath()),
                    ReportingContext.create(ReportEnvironment.local(), a.api()));
            if (launch == null) launch = Snapshot.capture(access);
            ClientTelemetryManager previous = access.av$gettelemetryManager();
            next.apply(access);
            if (changed) previous.close();
            changed = true;
            local = false;
            onlineUuid = a.user().getProfileId().toString().replace("-", "");
            mc.updateTitle();
            return null;
        } catch (Exception ex) {
            return "Не удалось переключить профиль";
        }
    }

    public static String launcherName() {
        return launch == null
                ? Minecraft.getInstance().getUser().getName()
                : launch.user().getName();
    }

    public static String select(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (inWorld(mc)) return "Выйдите из мира, чтобы сменить профиль";
        if (!valid(name)) return "Некорректный ник";
        try {
            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            User user = new User(name, uuid, "", Optional.empty(), Optional.empty());
            UserApiService api = UserApiService.OFFLINE;
            Snapshot next = new Snapshot(
                    user,
                    Services.create(YggdrasilAuthenticationService.createOffline(mc.getProxy()), mc.gameDirectory),
                    CompletableFuture.completedFuture(null),
                    api,
                    CompletableFuture.completedFuture(UserApiService.OFFLINE_PROPERTIES),
                    new PlayerSocialManager(mc, api),
                    new ClientTelemetryManager(mc, api, user),
                    ProfileKeyPairManager.EMPTY_KEY_MANAGER,
                    ReportingContext.create(ReportEnvironment.local(), api));
            if (launch == null) launch = Snapshot.capture((MinecraftSessionAccess) mc);
            ClientTelemetryManager previous = ((MinecraftSessionAccess) mc).av$gettelemetryManager();
            next.apply((MinecraftSessionAccess) mc);
            if (changed) previous.close();
            local = true;
            changed = true;
            onlineUuid = null;
            mc.updateTitle();
            return null;
        } catch (Exception ex) {
            System.err.println("[ArcanicVisuals] profile switch: " + ex);
            return "Не удалось переключить профиль";
        }
    }

    public static String restore() {
        Minecraft mc = Minecraft.getInstance();
        if (inWorld(mc)) return "Выйдите из мира, чтобы сменить профиль";
        if (launch != null && changed) {
            ClientTelemetryManager previous = ((MinecraftSessionAccess) mc).av$gettelemetryManager();
            launch.apply((MinecraftSessionAccess) mc);
            local = false;
            changed = false;
            onlineUuid = null;
            previous.close();
            mc.updateTitle();
        }
        return null;
    }

    private static boolean inWorld(Minecraft mc) {
        return mc.level != null || mc.getSingleplayerServer() != null || mc.getConnection() != null;
    }

    private record Snapshot(
            User user,
            Services services,
            CompletableFuture<ProfileResult> profileFuture,
            UserApiService userApiService,
            CompletableFuture<UserApiService.UserProperties> userPropertiesFuture,
            PlayerSocialManager playerSocialManager,
            ClientTelemetryManager telemetryManager,
            ProfileKeyPairManager profileKeyPairManager,
            ReportingContext reportingContext) {
        static Snapshot capture(MinecraftSessionAccess a) {
            return new Snapshot(
                    a.av$getuser(),
                    a.av$getservices(),
                    a.av$getprofileFuture(),
                    a.av$getuserApiService(),
                    a.av$getuserPropertiesFuture(),
                    a.av$getplayerSocialManager(),
                    a.av$gettelemetryManager(),
                    a.av$getprofileKeyPairManager(),
                    a.av$getreportingContext());
        }

        void apply(MinecraftSessionAccess a) {
            a.av$setuser(user);
            a.av$setservices(services);
            a.av$setprofileFuture(profileFuture);
            a.av$setuserApiService(userApiService);
            a.av$setuserPropertiesFuture(userPropertiesFuture);
            a.av$setplayerSocialManager(playerSocialManager);
            a.av$settelemetryManager(telemetryManager);
            a.av$setprofileKeyPairManager(profileKeyPairManager);
            a.av$setreportingContext(reportingContext);
        }
    }
}
