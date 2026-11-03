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

import java.util.Optional;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.render.Px;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

public final class ShulkerPreview extends Module {
    final Setting.Bool tint = this.add(new Setting.Bool("tint", "В цвет шалкера", true));
    final Setting.Bool compact = this.add(new Setting.Bool("compact", "Только занятые ряды", false));
    private static final int CELL = 18;
    private static final int PAD = 4;

    public ShulkerPreview() {
        super(
                "shulker_preview",
                "Просмотр шалкеров",
                "Содержимое шалкера в подсказке: все слоты, без открытия",
                Category.UTILITY,
                true);
    }

    public static void register() {
        TooltipComponentCallback.EVENT.register((TooltipComponentCallback) (data) -> {
            Client var10000;
            if (data instanceof Data d) {
                var10000 = new Client(d);
            } else {
                var10000 = null;
            }

            return var10000;
        });
    }

    public static Optional<TooltipComponent> of(ItemStack stack) {
        ShulkerPreview m = Modules.SHULKER;
        if (m != null && m.enabled()) {
            ItemContainerContents c = (ItemContainerContents) stack.get(DataComponents.CONTAINER);
            if (c != null && c.nonEmptyItems().iterator().hasNext()) {
                NonNullList<ItemStack> items = NonNullList.<ItemStack>withSize(27, ItemStack.EMPTY);
                c.copyInto(items);
                int colour = 0;
                if (m.tint.on()) {
                    Item var7 = stack.getItem();
                    if (var7 instanceof BlockItem) {
                        BlockItem bi = (BlockItem) var7;
                        Block var10 = bi.getBlock();
                        if (var10 instanceof ShulkerBoxBlock) {
                            ShulkerBoxBlock sb = (ShulkerBoxBlock) var10;
                            DyeColor d = sb.getColor();
                            colour = d == null ? -7574616 : 0xFF000000 | d.getTextureDiffuseColor();
                        }
                    }
                }

                int rows = 3;
                if (m.compact.on()) {
                    rows = 0;

                    for (int i = 0; i < 27; ++i) {
                        if (!((ItemStack) items.get(i)).isEmpty()) {
                            rows = i / 9 + 1;
                        }
                    }
                }

                return Optional.of(new Data(items, colour, Math.max(1, rows)));
            } else {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    public static boolean replacesLines() {
        ShulkerPreview m = Modules.SHULKER;
        return m != null && m.enabled();
    }

    public static record Data(NonNullList<ItemStack> items, int colour, int rows) implements TooltipComponent {}

    static record Client(Data d) implements ClientTooltipComponent {
        public int getHeight(Font font) {
            return this.d.rows() * 18 + 8 + 2;
        }

        public int getWidth(Font font) {
            return 170;
        }

        public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics g) {
            int rows = this.d.rows();
            int w = 170;
            int h = rows * 18 + 8;
            int tint = this.d.colour();
            Px.begin(g);

            try {
                float s = (float) Px.px(1.0);
                int px = Math.round((float) x * s);
                int py = Math.round((float) y * s);
                int pw = Math.round((float) w * s);
                int ph = Math.round((float) h * s);
                int r = Math.round(4.0F * s);
                Px.round(g, px, py, pw, ph, r, tint == 0 ? 822083583 : Px.a(tint, 46));
                Px.outline(g, px, py, pw, ph, r, tint == 0 ? 587202559 : Px.a(tint, 120));

                for (int i = 0; i < rows * 9; ++i) {
                    int cx = Math.round((float) (x + 4 + i % 9 * 18 + 1) * s);
                    int cy = Math.round((float) (y + 4 + i / 9 * 18 + 1) * s);
                    int cs = Math.round(16.0F * s);
                    boolean full = !((ItemStack) this.d.items().get(i)).isEmpty();
                    Px.round(g, cx, cy, cs, cs, Math.round(2.5F * s), full ? 754974719 : 318767103);
                }
            } finally {
                Px.end(g);
            }

            for (int i = 0; i < rows * 9; ++i) {
                ItemStack st = (ItemStack) this.d.items().get(i);
                if (!st.isEmpty()) {
                    int ix = x + 4 + i % 9 * 18 + 1;
                    int iy = y + 4 + i / 9 * 18 + 1;
                    g.renderItem(st, ix, iy);
                    g.renderItemDecorations(font, st, ix, iy);
                }
            }
        }
    }
}
