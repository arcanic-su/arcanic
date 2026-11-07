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
package net.arcanic.visuals.lua;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.arcanic.visuals.core.Keys;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Key names for scripts: "r", "f6", "lshift", "mouse4"... and back. Mouse buttons are {@link Keys#MOUSE} + n. */
final class LuaKeys {
    private LuaKeys() {}

    private static final Map<String, Integer> CODES = new HashMap<>();
    private static final Map<Integer, String> NAMES = new HashMap<>();

    private static void put(String name, int code) {
        CODES.put(name, code);
        NAMES.putIfAbsent(code, name);
    }

    static {
        for (char c = 'a'; c <= 'z'; c++) put(String.valueOf(c), GLFW.GLFW_KEY_A + (c - 'a'));
        for (int i = 0; i <= 9; i++) put(String.valueOf(i), GLFW.GLFW_KEY_0 + i);
        for (int i = 1; i <= 25; i++) put("f" + i, GLFW.GLFW_KEY_F1 + i - 1);
        for (int i = 0; i <= 9; i++) put("num" + i, GLFW.GLFW_KEY_KP_0 + i);
        put("space", GLFW.GLFW_KEY_SPACE);
        put("enter", GLFW.GLFW_KEY_ENTER);
        put("tab", GLFW.GLFW_KEY_TAB);
        put("backspace", GLFW.GLFW_KEY_BACKSPACE);
        put("escape", GLFW.GLFW_KEY_ESCAPE);
        put("insert", GLFW.GLFW_KEY_INSERT);
        put("delete", GLFW.GLFW_KEY_DELETE);
        put("right", GLFW.GLFW_KEY_RIGHT);
        put("left", GLFW.GLFW_KEY_LEFT);
        put("down", GLFW.GLFW_KEY_DOWN);
        put("up", GLFW.GLFW_KEY_UP);
        put("pageup", GLFW.GLFW_KEY_PAGE_UP);
        put("pagedown", GLFW.GLFW_KEY_PAGE_DOWN);
        put("home", GLFW.GLFW_KEY_HOME);
        put("end", GLFW.GLFW_KEY_END);
        put("capslock", GLFW.GLFW_KEY_CAPS_LOCK);
        put("lshift", GLFW.GLFW_KEY_LEFT_SHIFT);
        put("rshift", GLFW.GLFW_KEY_RIGHT_SHIFT);
        put("lctrl", GLFW.GLFW_KEY_LEFT_CONTROL);
        put("rctrl", GLFW.GLFW_KEY_RIGHT_CONTROL);
        put("lalt", GLFW.GLFW_KEY_LEFT_ALT);
        put("ralt", GLFW.GLFW_KEY_RIGHT_ALT);
        put("minus", GLFW.GLFW_KEY_MINUS);
        put("equal", GLFW.GLFW_KEY_EQUAL);
        put("grave", GLFW.GLFW_KEY_GRAVE_ACCENT);
        for (int i = 1; i <= 8; i++) put("mouse" + i, Keys.MOUSE + i - 1);
    }

    /** -1 for an unknown name. */
    static int code(String name) {
        Integer c = CODES.get(name.toLowerCase(Locale.ROOT).replace(" ", ""));
        return c == null ? -1 : c;
    }

    static String name(int code) {
        String n = NAMES.get(code);
        return n != null ? n : "key" + code;
    }

    static boolean down(int code) {
        if (code < 0) return false;
        long window = Minecraft.getInstance().getWindow().handle();
        if (code >= Keys.MOUSE) return GLFW.glfwGetMouseButton(window, code - Keys.MOUSE) == GLFW.GLFW_PRESS;
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), code);
    }
}
