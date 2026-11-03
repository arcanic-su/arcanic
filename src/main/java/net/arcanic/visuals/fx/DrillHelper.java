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
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.helper.EnchantSearch;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public final class DrillHelper extends Module {
    private final Setting.Colour colour = this.add(new Setting.Colour("color", "Цвет", true));
    private final Setting.Bool fill = this.add(new Setting.Bool("fill", "Заливка граней", true));
    private static final EnchantSearch.Custom DRILL = custom("Бур");
    private static final EnchantSearch.Custom MEGA = custom("Мега-бур");
    private double[] at;
    private float show;
    private long lastNanos = System.nanoTime();

    private static EnchantSearch.Custom custom(String name) {
        for (EnchantSearch.Custom c : EnchantSearch.CUSTOM) {
            if (c.name().equals(name)) {
                return c;
            }
        }

        throw new IllegalStateException(name);
    }

    public DrillHelper() {
        super("drill_helper", "Бур", "Область, которую сломает кирка с Буром или Мега-буром", Category.HOLYWORLD, true);
    }

    private static int[] reach(ItemStack st) {
        if (!st.isEmpty() && st.getItem().getDescriptionId().contains("pickaxe")) {
            if (EnchantSearch.customLevel(st, MEGA) > 0) {
                return new int[] {2, 2};
            } else {
                int lv = EnchantSearch.customLevel(st, DRILL);
                return lv <= 0 ? null : new int[] {1, Math.min(2, lv)};
            }
        } else {
            return null;
        }
    }

    private static double[] box(BlockPos pos, Direction face, int half, int depth) {
        BlockPos far = pos.relative(face.getOpposite(), depth - 1);
        int x0 = Math.min(pos.getX(), far.getX());
        int y0 = Math.min(pos.getY(), far.getY());
        int z0 = Math.min(pos.getZ(), far.getZ());
        int x1 = Math.max(pos.getX(), far.getX());
        int y1 = Math.max(pos.getY(), far.getY());
        int z1 = Math.max(pos.getZ(), far.getZ());
        Direction.Axis a = face.getAxis();
        if (a != Axis.X) {
            x0 -= half;
            x1 += half;
        }

        if (a != Axis.Y) {
            y0 -= half;
            y1 += half;
        }

        if (a != Axis.Z) {
            z0 -= half;
            z1 += half;
        }

        return new double[] {
            (double) x0, (double) y0, (double) z0, (double) (x1 + 1), (double) (y1 + 1), (double) (z1 + 1)
        };
    }

    void render(WorldRenderContext ctx, Vec3 cam, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            long nanos = System.nanoTime();
            float dt = Math.min(0.1F, (float) (nanos - this.lastNanos) / 1.0E9F);
            this.lastNanos = nanos;
            int[] r = reach(mc.player.getMainHandItem());
            double[] want = null;
            if (r != null) {
                HitResult var11 = mc.hitResult;
                if (var11 instanceof BlockHitResult) {
                    BlockHitResult bh = (BlockHitResult) var11;
                    if (bh.getType() == Type.BLOCK) {
                        want = box(bh.getBlockPos(), bh.getDirection(), r[0], r[1]);
                    }
                }
            }

            this.show += ((float) (want != null ? 1 : 0) - this.show)
                    * (1.0F - (float) Math.exp((double) (-dt * (float) (want != null ? 14 : 9))));
            if (want != null) {
                if (this.at != null
                        && !(Math.abs(this.at[0] - want[0])
                                        + Math.abs(this.at[1] - want[1])
                                        + Math.abs(this.at[2] - want[2])
                                > 8.0)) {
                    float k = 1.0F - (float) Math.exp((double) (-dt * 22.0F));

                    for (int i = 0; i < 6; ++i) {
                        double[] var10000 = this.at;
                        var10000[i] += (want[i] - this.at[i]) * (double) k;
                    }
                } else {
                    this.at = want.clone();
                }
            }

            if (!(this.show < 0.01F) && this.at != null) {
                double fov = Math.toRadians((double) Projection.fov());
                TrapZone.Frame f = new TrapZone.Frame(
                        ctx.matrices().last(),
                        cam,
                        (float) (2.0
                                * Math.tan(fov / 2.0)
                                / (double) Math.max(1, mc.getWindow().getHeight())),
                        pt,
                        (float) (nanos % 1000000000000L) / 1.0E9F);
                int c = this.colour.argb() | 0xFF000000;
                int light = Fx.lerp(c, -1, 0.35F);
                double e = 0.003;
                double x0 = this.at[0] - e;
                double y0 = this.at[1] - e;
                double z0 = this.at[2] - e;
                double x1 = this.at[3] + e;
                double y1 = this.at[4] + e;
                double z1 = this.at[5] + e;
                double[][] pts = new double[][] {
                    {x0, y0, z0},
                    {x1, y0, z0},
                    {x1, y0, z1},
                    {x0, y0, z1},
                    {x0, y1, z0},
                    {x1, y1, z0},
                    {x1, y1, z1},
                    {x0, y1, z1}
                };
                int[][] edges = new int[][] {
                    {0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}
                };

                for (boolean under : new boolean[] {true, false}) {
                    VertexConsumer vc =
                            ctx.consumers().getBuffer(Fx.ribbon(under ? Fx.Tex.LINE_GLOW : Fx.Tex.LINE_AA, false));
                    int lc = TrapZone.alpha(under ? c : light, this.show * (under ? 0.45F : 0.95F));
                    float px = under ? 11.0F : 2.2F;

                    for (int[] ed : edges) {
                        double[] a = pts[ed[0]];
                        double[] b = pts[ed[1]];
                        TrapZone.line(vc, f, new Vec3(a[0], a[1], a[2]), new Vec3(b[0], b[1], b[2]), px, lc, lc);
                    }

                    if (under && this.fill.on()) {
                        int fc = TrapZone.alpha(c, this.show * 0.07F);
                        TrapZone.face(vc, f, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, fc, fc, fc, fc);
                        TrapZone.face(vc, f, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, fc, fc, fc, fc);
                        TrapZone.face(vc, f, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, fc, fc, fc, fc);
                        TrapZone.face(vc, f, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, fc, fc, fc, fc);
                        TrapZone.face(vc, f, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, fc, fc, fc, fc);
                        TrapZone.face(vc, f, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, fc, fc, fc, fc);
                    }
                }

            } else {
                if (this.show < 0.01F) {
                    this.at = null;
                }
            }
        }
    }
}
