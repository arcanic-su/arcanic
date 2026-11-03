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
package net.arcanic.visuals.helper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.arcanic.visuals.core.Config;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.hud.Toasts;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class HolyFeatures {
    private static final String CLIENT = "arcanicvisuals";
    private static final long LIMIT = 10500L;
    private static final long TIMEOUT = 6000L;
    private static final int TRIES = 3;
    private static final Set<String> RULES = Set.of("trajectories", "hitboxes", "outline", "click_pearl");
    private static final Set<String> blocked = new HashSet<>();
    private static final Set<String> paused = new LinkedHashSet<>();
    private static String pending;
    private static long sentAt;
    private static long joinedAt;
    private static int tries;
    private static boolean answered;

    private HolyFeatures() {}

    public static void register() {
        PayloadTypeRegistry.playC2S().register(HolyFeatures.Packet.TYPE, HolyFeatures.Packet.CODEC);
        PayloadTypeRegistry.playS2C().register(HolyFeatures.Packet.TYPE, HolyFeatures.Packet.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(HolyFeatures.Packet.TYPE, (packet, ctx) -> ctx.client()
                .execute(() -> answer(new String(packet.data(), StandardCharsets.UTF_8))));
        ClientPlayConnectionEvents.JOIN.register((ClientPlayConnectionEvents.Join) (handler, sender, mc) -> {
            reset();
            joinedAt = System.currentTimeMillis();
        });
        ClientPlayConnectionEvents.DISCONNECT.register(
                (ClientPlayConnectionEvents.Disconnect) (handler, mc) -> mc.execute(() -> {
                    reset();
                    joinedAt = 0L;
                    restore();
                }));
        ClientTickEvents.END_CLIENT_TICK.register((ClientTickEvents.EndTick) (mc) -> tick());
    }

    private static void reset() {
        blocked.clear();
        pending = null;
        tries = 0;
        answered = false;
    }

    public static boolean hidden(Module m) {
        return m != null && !blocked.isEmpty() && blocked.contains(m.id) && HolyEvents.onHolyWorld();
    }

    public static boolean locked(Module m) {
        return m != null && RULES.contains(m.id) && HolyEvents.onHolyWorld();
    }

    public static boolean forbidden(Module m) {
        return hidden(m) || locked(m);
    }

    public static void deny(Module m) {
        if (!hidden(m)) {
            String text = "Эта функция запрещена на HolyWorld";
            Toasts.setting(text, false);
            UiSound.play("reset", 0.8F, 0.8F);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen == null) {
                mc.player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.RED), true);
            }
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getConnection() != null) {
            boolean holy = HolyEvents.onHolyWorld();
            if (!holy) {
                restore();
            } else {
                long now = System.currentTimeMillis();
                if (!answered && joinedAt > 0L && now - joinedAt > 1000L && tries < 3) {
                    label38:
                    {
                        if (pending == null) {
                            if (sentAt != 0L && now - sentAt <= 10500L) {
                                break label38;
                            }
                        } else if (now - sentAt <= Math.max(6000L, 10500L)) {
                            break label38;
                        }

                        send();
                    }
                }

                for (Module m : Modules.ALL) {
                    if (m.enabled() && forbidden(m)) {
                        m.setEnabled(false);
                        paused.add(m.id);
                    }
                }
            }
        }
    }

    private static void restore() {
        if (!paused.isEmpty()) {
            boolean any = false;

            for (Module m : Modules.ALL) {
                if (paused.contains(m.id) && !forbidden(m)) {
                    m.setEnabled(true);
                    paused.remove(m.id);
                    any = true;
                }
            }

            if (any) {
                Config.dirty();
            }
        }
    }

    private static void send() {
        JsonArray features = new JsonArray();

        for (Module m : Modules.ALL) {
            features.add(m.id);
        }

        for (Module m : Modules.ITEM_PARTS) {
            features.add(m.id);
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("client", "arcanicvisuals");
        payload.add("features", features);
        JsonObject req = new JsonObject();
        pending = UUID.randomUUID().toString();
        req.addProperty("id", pending);
        req.addProperty("method", "checkFeatures");
        req.add("payload", payload);
        sentAt = System.currentTimeMillis();
        ++tries;

        try {
            ClientPlayNetworking.send(new Packet(req.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (RuntimeException e) {
            tries = 3;
        }
    }

    private static void answer(String json) {
        JsonObject o;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return;
            }

            o = root.getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }

        if (!o.has("event")) {
            String id = o.has("id") && !o.get("id").isJsonNull() ? o.get("id").getAsString() : null;
            if (pending != null && pending.equals(id)) {
                pending = null;
                boolean ok = o.has("ok") && o.get("ok").getAsBoolean();
                if (!ok) {
                    String err = o.has("error") ? o.get("error").getAsString() : "";
                    if (!err.equals("RATE_LIMITED")) {
                        tries = 3;
                    }

                } else {
                    answered = true;
                    blocked.clear();

                    try {
                        for (JsonElement e : o.getAsJsonObject("payload").getAsJsonArray("blocklist")) {
                            blocked.add(e.getAsString());
                        }
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        }
    }

    public static record Packet(byte[] data) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Packet> TYPE =
                new CustomPacketPayload.Type<Packet>(Identifier.fromNamespaceAndPath("liteapi", "feature-control"));
        public static final StreamCodec<FriendlyByteBuf, Packet> CODEC =
                CustomPacketPayload.codec(((p, buf) -> buf.writeBytes(p.data())), ((buf) -> {
                    byte[] b = new byte[buf.readableBytes()];
                    buf.readBytes(b);
                    return new Packet(b);
                }));

        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
