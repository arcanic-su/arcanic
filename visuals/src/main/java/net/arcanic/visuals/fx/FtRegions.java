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
package net.arcanic.visuals.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.helper.FunTime;
import net.arcanic.visuals.render.Px;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** FunTime private regions: the block in hand shows the region it would claim, and placed ones keep a frame. */
public final class FtRegions extends Module {
    private final Setting.Bool preview = this.add(new Setting.Bool("preview", "Предпросмотр установки", true));
    private final Setting.Bool placed = this.add(new Setting.Bool("placed", "Поставленные регионы", true));
    private final Setting.Bool labels = this.add(new Setting.Bool("labels", "Подписи размера", true));
    private final Setting.Colour colour = this.add(new Setting.Colour("color", "Цвет", true));
    private static final int MAX_PLACED = 32;
    private final List<Region> regions = new ArrayList<>();

    /** The region blocks and the radius each claims around itself. */
    private enum Size {
        IRON(Blocks.IRON_BLOCK, 3),
        GOLD(Blocks.GOLD_BLOCK, 5),
        DIAMOND(Blocks.DIAMOND_BLOCK, 7),
        DIAMOND_ORE(Blocks.DIAMOND_ORE, 10),
        EMERALD_ORE(Blocks.EMERALD_ORE, 15),
        CHAIN(Blocks.CHAIN_COMMAND_BLOCK, 25);

        final Block block;
        final int radius;

        Size(Block block, int radius) {
            this.block = block;
            this.radius = radius;
        }

        static Size of(Item item) {
            if (!(item instanceof BlockItem b)) return null;
            for (Size s : values()) if (s.block == b.getBlock()) return s;
            return null;
        }
    }

    private record Region(String server, BlockPos pos, int radius) {
        AABB box() {
            return new AABB(
                    this.pos.getX() - this.radius + 0.01,
                    this.pos.getY() - this.radius + 0.01,
                    this.pos.getZ() - this.radius + 0.01,
                    this.pos.getX() + this.radius + 0.99,
                    this.pos.getY() + this.radius + 0.99,
                    this.pos.getZ() + this.radius + 0.99);
        }
    }

    public FtRegions() {
        super(
                "ft_regions",
                "Регионы FunTime",
                "Зона региона перед установкой блока привата и рамки уже поставленных регионов",
                Category.FUNTIME,
                true);
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            Modules.FT_REGIONS.used(hand, hit);
            return InteractionResult.PASS;
        });
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static String server() {
        ServerData d = mc().getCurrentServer();
        return d == null || d.ip == null ? "" : d.ip;
    }

    private void used(net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!this.enabled() || !FunTime.here() || mc().player == null || mc().level == null) return;
        Size size = Size.of(mc().player.getItemInHand(hand).getItem());
        if (size == null) return;
        BlockPos at = target(hit);
        this.regions.removeIf(r -> r.pos().equals(at));
        this.regions.add(new Region(server(), at, size.radius));
        while (this.regions.size() > MAX_PLACED) this.regions.removeFirst();
    }

    private static BlockPos target(BlockHitResult hit) {
        BlockPos at = hit.getBlockPos();
        return mc().level.getBlockState(at).canBeReplaced() ? at : at.relative(hit.getDirection());
    }

    private Region aimed() {
        if (!this.preview.on() || mc().player == null) return null;
        Size size = Size.of(mc().player.getMainHandItem().getItem());
        if (size == null) size = Size.of(mc().player.getOffhandItem().getItem());
        if (size == null) return null;
        HitResult hit = mc().hitResult;
        if (!(hit instanceof BlockHitResult b) || hit.getType() != HitResult.Type.BLOCK) return null;
        return new Region(server(), target(b), size.radius);
    }

    private List<Region> shown() {
        List<Region> out = new ArrayList<>();
        if (this.placed.on()) {
            String here = server();
            for (Region r : this.regions) {
                if (r.server().equals(here)) out.add(r);
            }
        }
        Region aim = this.aimed();
        if (aim != null) out.add(aim);
        return out;
    }

    void render(WorldRenderContext ctx, Vec3 cam, float pt) {
        Minecraft mc = mc();
        if (!this.enabled() || !FunTime.here() || mc.player == null || mc.level == null) return;
        List<Region> list = this.shown();
        if (list.isEmpty()) return;
        double fov = Math.toRadians(Projection.fov());
        TrapZone.Frame f = new TrapZone.Frame(
                ctx.matrices().last(),
                cam,
                (float) (2.0 * Math.tan(fov / 2.0) / Math.max(1, mc.getWindow().getHeight())),
                pt,
                (float) (System.nanoTime() % 1000000000000L) / 1.0E9F);
        VertexConsumer glow = ctx.consumers().getBuffer(Fx.ribbon(Fx.Tex.LINE_GLOW, false));
        VertexConsumer edge = ctx.consumers().getBuffer(Fx.ribbon(Fx.Tex.LINE_AA, false));
        int c = this.colour.argb() | 0xFF000000;
        for (Region r : list) {
            FtHelper.cuboid(glow, edge, f, r.box(), c);
            FtHelper.cuboid(glow, edge, f, new AABB(r.pos()), Fx.lerp(c, -1, 0.4F));
        }
    }

    void hud(GuiGraphics g) {
        if (!this.enabled() || !this.labels.on() || !FunTime.here() || mc().player == null || mc().screen != null) {
            return;
        }
        int fs = Math.max(11, Math.round(Px.height() / 1080.0F * 13.0F));
        int c = this.colour.argb() | 0xFF000000;
        for (Region r : this.shown()) {
            double[] s = Projection.toScreen(Vec3.atCenterOf(r.pos()), Px.width(), Px.height());
            if (s == null) continue;
            String size = r.radius() + "x" + r.radius(); // FunTime names a region by its radius
            Px.textCenter(g, size, (int) s[0], (int) s[1] - fs / 2, Px.Weight.BOLD, fs, Px.am(c, Px.fade));
        }
    }
}
