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
package net.arcanic.visuals.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.server.Services;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftSessionAccess {
    @Accessor("user")
    User av$getuser();

    @Mutable
    @Accessor("user")
    void av$setuser(User value);

    @Accessor("services")
    Services av$getservices();

    @Mutable
    @Accessor("services")
    void av$setservices(Services value);

    @Accessor("profileFuture")
    CompletableFuture<ProfileResult> av$getprofileFuture();

    @Mutable
    @Accessor("profileFuture")
    void av$setprofileFuture(CompletableFuture<ProfileResult> value);

    @Accessor("userApiService")
    UserApiService av$getuserApiService();

    @Mutable
    @Accessor("userApiService")
    void av$setuserApiService(UserApiService value);

    @Accessor("userPropertiesFuture")
    CompletableFuture<UserApiService.UserProperties> av$getuserPropertiesFuture();

    @Mutable
    @Accessor("userPropertiesFuture")
    void av$setuserPropertiesFuture(CompletableFuture<UserApiService.UserProperties> value);

    @Accessor("playerSocialManager")
    PlayerSocialManager av$getplayerSocialManager();

    @Mutable
    @Accessor("playerSocialManager")
    void av$setplayerSocialManager(PlayerSocialManager value);

    @Accessor("telemetryManager")
    ClientTelemetryManager av$gettelemetryManager();

    @Mutable
    @Accessor("telemetryManager")
    void av$settelemetryManager(ClientTelemetryManager value);

    @Accessor("profileKeyPairManager")
    ProfileKeyPairManager av$getprofileKeyPairManager();

    @Mutable
    @Accessor("profileKeyPairManager")
    void av$setprofileKeyPairManager(ProfileKeyPairManager value);

    @Accessor("reportingContext")
    ReportingContext av$getreportingContext();

    @Mutable
    @Accessor("reportingContext")
    void av$setreportingContext(ReportingContext value);
}
