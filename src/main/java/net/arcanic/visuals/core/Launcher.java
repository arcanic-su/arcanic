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

import com.sun.jna.platform.win32.Crypt32Util;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

/** The installed Arcanic launcher. */
public final class Launcher {
    private Launcher() {}

    public static Path exe() {
        String started = System.getProperty("arcanic.launcher", "");
        if (!started.isEmpty() && Files.isRegularFile(Path.of(started))) return Path.of(started);
        String local = System.getenv("LOCALAPPDATA");
        if (local == null) return null;
        Path installed = Path.of(local, "Programs", "Arcanic", "Arcanic.exe");
        return Files.isRegularFile(installed) ? installed : null;
    }

    /** Can the launcher that started the game read the media session for us (--media)? Older ones cannot. */
    public static boolean media() {
        return System.getProperty("arcanic.helpers", "").contains("media") && exe() != null;
    }

    // tokens on disk are encrypted for this Windows user (DPAPI), the same way the launcher keeps its own

    static String seal(String plain) {
        if (plain.isEmpty()) return "";
        try {
            return "dpapi:"
                    + HexFormat.of().formatHex(Crypt32Util.cryptProtectData(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (Throwable e) {
            return "";
        }
    }

    static String unseal(String text) {
        if (!text.startsWith("dpapi:")) return text;
        try {
            return new String(
                    Crypt32Util.cryptUnprotectData(HexFormat.of().parseHex(text.substring(6))), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return "";
        }
    }
}
