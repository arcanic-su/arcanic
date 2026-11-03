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

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Lang;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.core.Theme;
import net.arcanic.visuals.core.UiSound;
import net.arcanic.visuals.gui.PromptScreen;
import net.arcanic.visuals.hud.HudElements;
import net.arcanic.visuals.hud.Notify;
import net.arcanic.visuals.mixin.ContainerScreenAccess;
import net.arcanic.visuals.render.Px;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class Kits extends Module {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("arcanicvisuals/kits.json");
    private static final int SLOTS = 41;
    private final Map<String, String[]> kits = new LinkedHashMap<>();
    private String active = "";
    private boolean loaded;
    final Setting.Bool marks = this.add(new Setting.Bool("marks", "Подсвечивать слоты", true));
    final Setting.Bool arrange = this.add(new Setting.Bool("arrange", "Кнопка «Разложить»", true));
    final Setting.Action save = this.add(new Setting.Action("save", "Сохранить текущую", "Сохранить", this::askSave));
    final Setting.Action choose = this.add(new Setting.Action("choose", "Раскладка", "Выбрать", this::askChoose));
    final Setting.Action delete =
            this.add(new Setting.Action("delete", "Удалить раскладку", "Удалить", this::askDelete));
    private final ArrayDeque<int[]> clicks = new ArrayDeque<>();
    private int[] button = new int[4];

    public Kits() {
        super(
                "kits",
                "Раскладки",
                "Сохрани раскладку инвентаря: подсветка не на месте и недостающего, «Разложить» в один клик",
                Category.UTILITY,
                false);
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((ScreenEvents.AfterInit) (client, screen, w, h) -> {
            if (screen instanceof AbstractContainerScreen<?> s) {
                if (!(screen instanceof CreativeModeInventoryScreen)) {
                    ScreenEvents.afterRender(screen).register((ScreenEvents.AfterRender)
                            (sc, g, mx, my, dt) -> Modules.KITS.overlay(s, g, mx, my));
                    ScreenMouseEvents.allowMouseClick(screen).register((ScreenMouseEvents.AllowMouseClick)
                            (sc, e) -> !Modules.KITS.click(s, e.x(), e.y()));
                    return;
                }
            }
        });
    }

    private void load() {
        if (!this.loaded) {
            this.loaded = true;

            try {
                if (!Files.exists(FILE)) {
                    return;
                }

                JsonObject o = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                this.active = o.has("active") ? o.get("active").getAsString() : "";

                for (Map.Entry<String, JsonElement> e :
                        o.getAsJsonObject("kits").entrySet()) {
                    String[] k = new String[41];
                    JsonArray a = ((JsonElement) e.getValue()).getAsJsonArray();

                    for (int i = 0; i < 41; i++) {
                        k[i] = i < a.size() ? a.get(i).getAsString() : "";
                    }

                    this.kits.put(e.getKey(), k);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void write() {
        JsonObject o = new JsonObject();
        JsonObject ks = new JsonObject();
        o.addProperty("active", this.active);
        this.kits.forEach((n, k) -> {
            JsonArray a = new JsonArray();

            for (String s : k) {
                a.add(s == null ? "" : s);
            }

            ks.add(n, a);
        });
        o.add("kits", ks);

        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(
                    FILE, (new GsonBuilder()).setPrettyPrinting().create().toJson(o), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private String[] kit() {
        this.load();
        return this.active.isEmpty() ? null : (String[]) this.kits.get(this.active);
    }

    private static String id(ItemStack st) {
        return st.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
    }

    private void askSave() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            Notify.text("Раскладки: зайди в мир, чтобы сохранить", false);
        } else {
            this.load();
            mc.setScreen(PromptScreen.field(
                    mc.screen, "Новая раскладка", "Название (например, «Ивент»)", "Сохранить", (name) -> {
                        Inventory inv = mc.player.getInventory();
                        String[] k = new String[41];

                        for (int i = 0; i < 41; i++) {
                            k[i] = id(inv.getItem(i));
                        }

                        this.kits.put(name, k);
                        this.active = name;
                        this.write();
                        UiSound.play("select");
                        Notify.text("Раскладка сохранена: " + name, true);
                    }));
        }
    }

    private void askChoose() {
        Minecraft mc = Minecraft.getInstance();
        this.load();
        List<String> names = new ArrayList(this.kits.keySet());
        names.add(Lang.tr("Без раскладки"));
        int at = Math.max(0, names.indexOf(this.active));
        if (this.active.isEmpty()) {
            at = names.size() - 1;
        }

        mc.setScreen(PromptScreen.pick(mc.screen, "Раскладка", names, at, "Выбрать", (i) -> {
            this.active = i < this.kits.size() ? names.get(i) : "";
            this.write();
        }));
    }

    private void askDelete() {
        Minecraft mc = Minecraft.getInstance();
        this.load();
        if (this.kits.isEmpty()) {
            Notify.text("Раскладки: пока нет сохранённых", false);
        } else {
            List<String> names = new ArrayList(this.kits.keySet());
            mc.setScreen(PromptScreen.pick(mc.screen, "Удалить раскладку", names, 0, "Удалить", (i) -> {
                String n = names.get(i);
                this.kits.remove(n);
                if (n.equals(this.active)) {
                    this.active = "";
                }

                this.write();
            }));
        }
    }

    private List<Missing> missing(Inventory inv, String[] k) {
        Map<String, Integer> need = new LinkedHashMap<>();
        Map<String, Integer> have = new LinkedHashMap<>();

        for (String s : k) {
            if (s != null && !s.isEmpty()) {
                need.merge(s, 1, Integer::sum);
            }
        }

        for (int i = 0; i < 41; i++) {
            String s = id(inv.getItem(i));
            if (!s.isEmpty()) {
                have.merge(s, 1, Integer::sum);
            }
        }

        List<Missing> out = new ArrayList<>();
        need.forEach((sx, n) -> {
            int lack = n - (Integer) have.getOrDefault(sx, 0);
            if (lack > 0) {
                BuiltInRegistries.ITEM
                        .getOptional(Identifier.parse(sx))
                        .ifPresent((it) -> out.add(new Missing(it, lack)));
            }
        });
        return out;
    }

    void overlay(AbstractContainerScreen<?> s, GuiGraphics g, int mx, int my) {
        if (this.enabled()) {
            String[] k = this.kit();
            Minecraft mc = Minecraft.getInstance();
            if (k != null && mc.player != null) {
                Inventory inv = mc.player.getInventory();
                ContainerScreenAccess acc = (ContainerScreenAccess) s;
                int left = acc.arcanicvisuals$left();
                int top = acc.arcanicvisuals$top();
                Map<String, Integer> spare = new LinkedHashMap<>();

                for (int i = 0; i < 41; i++) {
                    String now = id(inv.getItem(i));
                    if (!now.isEmpty() && !now.equals(k[i])) {
                        spare.merge(now, 1, Integer::sum);
                    }
                }

                boolean wrong = false;
                Px.begin(g);

                try {
                    float gs = (float) Px.guiScale();
                    if (this.marks.on()) {
                        for (Slot slot : s.getMenu().slots) {
                            if (slot.container instanceof Inventory) {
                                int i = slot.getContainerSlot();
                                if (i >= 0
                                        && i < 41
                                        && k[i] != null
                                        && !k[i].isEmpty()
                                        && !k[i].equals(id(slot.getItem()))) {
                                    wrong = true;
                                    boolean somewhere = (Integer) spare.getOrDefault(k[i], 0) > 0;
                                    int col = somewhere ? -14003 : -42396;
                                    int x = Math.round((float) (left + slot.x) * gs);
                                    int y = Math.round((float) (top + slot.y) * gs);
                                    int sz = Math.round(16.0F * gs);
                                    Px.round(g, x, y, sz, sz, Math.round(2.5F * gs), Px.a(col, 46));
                                    Px.outline(g, x, y, sz, sz, Math.round(2.5F * gs), Px.a(col, 200));
                                }
                            }
                        }
                    }

                    List<Missing> lack = this.missing(inv, k);
                    int w = Math.round(110.0F * gs);
                    int pad = Math.round(6.0F * gs);
                    int row = Math.round(14.0F * gs);
                    int px = Math.round((float) (left + acc.arcanicvisuals$width() + 4) * gs);
                    int py = Math.round((float) top * gs);
                    int bh = !this.arrange.on() || !(s instanceof InventoryScreen) || !wrong && this.clicks.size() <= 0
                            ? 0
                            : Math.round(18.0F * gs);
                    int h = pad
                            + Math.round(10.0F * gs)
                            + pad
                            + (lack.isEmpty() ? row : lack.size() * row)
                            + (bh > 0 ? pad + bh : 0)
                            + pad;
                    Px.round(g, px, py, w, h, Math.round(6.0F * gs), 0xE6111116);
                    Px.outline(g, px, py, w, h, Math.round(6.0F * gs), 587202559);
                    int fs = Math.max(8, Math.round(6.5F * gs));
                    Px.text(
                            g,
                            Px.ellipsize(this.active, Px.Weight.SEMIBOLD, fs, w - pad * 2),
                            px + pad,
                            py + pad,
                            Px.Weight.SEMIBOLD,
                            fs,
                            0xFFF4F4F8);
                    int y = py + pad + Math.round(10.0F * gs) + pad;
                    if (lack.isEmpty()) {
                        Px.text(
                                g,
                                Lang.tr(wrong ? "Всё есть, не на местах" : "Всё на месте"),
                                px + pad,
                                y + (row - fs) / 2,
                                Px.Weight.MEDIUM,
                                fs,
                                wrong ? -14003 : 0xFF4ADE80);
                    }

                    for (Missing m : lack) {
                        HudElements.item(
                                g, new ItemStack(m.item()), px + pad, y + Math.round(gs), row - Math.round(2.0F * gs));
                        String t = "×" + m.need();
                        Px.text(
                                g,
                                Px.ellipsize(
                                        (new ItemStack(m.item())).getHoverName().getString(),
                                        Px.Weight.MEDIUM,
                                        fs,
                                        w - pad * 3 - row - Px.textWidth(t, Px.Weight.BOLD, fs)),
                                px + pad + row + Math.round(2.0F * gs),
                                y + (row - fs) / 2,
                                Px.Weight.MEDIUM,
                                fs,
                                0xFFE6E6EC);
                        Px.textRight(g, t, px + w - pad, y + (row - fs) / 2, Px.Weight.BOLD, fs, -42396);
                        y += row;
                    }

                    if (bh > 0) {
                        int bx = px + pad;
                        int by = y + pad;
                        int bw = w - pad * 2;
                        double pmx = (double) ((float) mx * gs);
                        double pmy = (double) ((float) my * gs);
                        boolean hv = pmx >= (double) bx
                                && pmx < (double) (bx + bw)
                                && pmy >= (double) by
                                && pmy < (double) (by + bh);
                        boolean busy = !this.clicks.isEmpty();
                        Px.round(
                                g,
                                bx,
                                by,
                                bw,
                                bh,
                                Math.round(4.0F * gs),
                                busy
                                        ? 822083583
                                        : (hv
                                                ? Px.mix(Theme.accent() | 0xFF000000, -1, 0.1F)
                                                : Theme.accent() | 0xFF000000));
                        Px.textCenter(
                                g,
                                Lang.tr(busy ? "Раскладываю…" : "Разложить"),
                                bx + bw / 2,
                                by + (bh - fs) / 2,
                                Px.Weight.SEMIBOLD,
                                fs,
                                -1);
                        this.button = new int[] {bx, by, bw, bh};
                    } else {
                        this.button = new int[4];
                    }
                } finally {
                    Px.end(g);
                }
            }
        }
    }

    boolean click(AbstractContainerScreen<?> s, double mx, double my) {
        if (this.enabled() && this.button[2] != 0 && s instanceof InventoryScreen) {
            double px = Px.px(mx);
            double py = Px.px(my);
            if (!(px < (double) this.button[0])
                    && !(px >= (double) (this.button[0] + this.button[2]))
                    && !(py < (double) this.button[1])
                    && !(py >= (double) (this.button[1] + this.button[3]))) {
                if (this.clicks.isEmpty()) {
                    this.plan(s);
                }

                UiSound.play("click");
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private void plan(AbstractContainerScreen<?> s) {
        String[] k = this.kit();
        Minecraft mc = Minecraft.getInstance();
        if (k != null && mc.player != null && s.getMenu().getCarried().isEmpty()) {
            int menu = s.getMenu().containerId;
            Map<Integer, Integer> menuSlot = new LinkedHashMap<>();

            for (Slot slot : s.getMenu().slots) {
                if (slot.container instanceof Inventory) {
                    menuSlot.put(slot.getContainerSlot(), slot.index);
                }
            }

            String[] now = new String[41];

            for (int i = 0; i < 41; i++) {
                now[i] = id(mc.player.getInventory().getItem(i));
            }

            for (int t = 0; t < 41; t++) {
                if (k[t] != null && !k[t].isEmpty() && !k[t].equals(now[t]) && menuSlot.containsKey(t)) {
                    int from = -1;

                    for (int i = 0; i < 41; i++) {
                        if (i != t && k[t].equals(now[i]) && !k[t].equals(k[i]) && menuSlot.containsKey(i)) {
                            from = i;
                            break;
                        }
                    }

                    if (from >= 0) {
                        this.clicks.add(new int[] {menu, menuSlot.get(from)});
                        this.clicks.add(new int[] {menu, menuSlot.get(t)});
                        this.clicks.add(new int[] {menu, menuSlot.get(from)});
                        String moved = now[t];
                        now[t] = now[from];
                        now[from] = moved;
                    }
                }
            }
        }
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!this.clicks.isEmpty()) {
            Screen screen2 = mc.screen;
            if (screen2 instanceof InventoryScreen) {
                InventoryScreen s = (InventoryScreen) screen2;
                if (mc.player != null && mc.gameMode != null) {
                    int[] c = (int[]) this.clicks.poll();
                    if (c[0] != ((InventoryMenu) s.getMenu()).containerId) {
                        this.clicks.clear();
                        return;
                    }

                    mc.gameMode.handleInventoryMouseClick(c[0], c[1], 0, ClickType.PICKUP, mc.player);
                    return;
                }
            }

            this.clicks.clear();
        }
    }

    private static record Missing(Item item, int need) {}
}
