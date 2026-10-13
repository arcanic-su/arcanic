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

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;

/** Animations: first-person swing styles and the CS-style item inspect (toss, turn, spin). */
public final class HandAnim extends Module {
    public final Setting.Mode swing = add(new Setting.Mode(
            "swing",
            "Удар",
            "Ванильный",
            "Ванильный",
            "Плавный",
            "Рубящий",
            "Укол",
            "Вращение",
            "Слайд",
            "Старый 1.7"));
    public final Setting.Num swingSpeed = add(new Setting.Num("swing_speed", "Темп удара", 1, .5, 2, .05, "×"));
    public final Setting.Bool noSweep = add(new Setting.Bool("no_sweep", "Скрыть взмах (sweep)", true));
    public final Setting.Bool noEquip = add(new Setting.Bool("no_equip", "Без опускания при смене", false));
    public final Setting.Mode inspect = add(
            new Setting.Mode("inspect", "Осмотр [G]", "Авто", "Авто", "Перехват", "Подброс", "Разворот", "Прокрут"));
    public final Setting.Num inspectSpeed = add(new Setting.Num("inspect_speed", "Темп осмотра", 1, .5, 2, .05, "×"));

    private long inspectStart = -1;
    private String inspectStyle;
    private int inspectSlot;

    public HandAnim() {
        super("hand_anim", "Анимации", "Стили удара и осмотр предмета как в CS", Category.HANDS, false);
    }

    public void startInspect() {
        var p = Minecraft.getInstance().player;
        if (p == null || p.getMainHandItem().isEmpty()) return;
        ItemStack it = p.getMainHandItem();
        String s = inspect.get();
        if (s.equals("Авто"))
            s = it.is(ItemTags.SWORDS) || it.is(ItemTags.AXES)
                    ? "Перехват"
                    : it.getMaxStackSize() > 1 ? "Прокрут" : "Разворот";
        inspectStyle = s;
        inspectStart = System.currentTimeMillis();
        inspectSlot = p.getInventory().getSelectedSlot();
    }

    public void cancelInspect() {
        inspectStart = -1;
    }

    private float duration() {
        return (float) ((inspectStyle == null
                        ? 2.4
                        : switch (inspectStyle) {
                            case "Перехват" -> 3.6;
                            case "Подброс" -> 2.9;
                            default -> 2.4;
                        })
                / inspectSpeed.get());
    }
    /** 0..1 progress of the running inspect, or -1. */
    public float inspectProgress() {
        if (inspectStart < 0) return -1;
        var p = Minecraft.getInstance().player;
        if (p == null || p.getInventory().getSelectedSlot() != inspectSlot || p.swinging) {
            inspectStart = -1;
            return -1;
        }
        float t = (System.currentTimeMillis() - inspectStart) / 1000f / duration();
        if (t >= 1) {
            inspectStart = -1;
            return -1;
        }
        return t;
    }

    private static final float[][] TOSS = {
        {0, 0, 0, 0, 0, 0, 0},
        {.10f, -.06f, -.06f, .02f, 12, -12, 6}, // dip (anticipation)
        {.20f, -.20f, .10f, .04f, -15, -32, 14}, // bring to the centre
        {.30f, -.27f, .30f, .02f, -150, -34, 14}, // released, rising and flipping
        {.38f, -.29f, .40f, 0, -330, -34, 14}, // apex
        {.47f, -.28f, .22f, .02f, -560, -34, 14}, // falling
        {.54f, -.22f, .04f, .05f, -720, -32, 14}, // caught
        {.58f, -.21f, .00f, .05f, -725, -30, 12}, // catch bump
        {.70f, -.28f, .08f, -.02f, -720, -120, 26}, // turn to show the blade
        {.84f, -.29f, .09f, -.02f, -718, -128, 28}, // hold (slight drift)
        {1, 0, 0, 0, -720, 0, 0},
    };

    private static final float[][] TURN = {
        {0, 0, 0, 0, 0, 0, 0},
        {.16f, -.22f, .08f, .05f, -18, -55, 18},
        {.36f, -.24f, .10f, .05f, -12, -70, 22},
        {.58f, -.27f, .12f, .05f, 8, 105, -24},
        {.80f, -.27f, .12f, .05f, 12, 118, -26},
        {1, 0, 0, 0, 0, 0, 0},
    };
    private static final float[][] SPIN = {
        {0, 0, 0, 0, 0, 0, 0},
        {.14f, -.2f, .1f, .05f, -10, -20, -20},
        {.40f, -.2f, .12f, .05f, -10, -20, 300},
        {.70f, -.2f, .12f, .05f, -10, -20, 700},
        {.82f, -.2f, .1f, .05f, -6, -18, 725},
        {1, 0, 0, 0, 0, 0, 720},
    };

    /*
     * Regrip: the off hand comes up from below, the blade is flipped over into it, the off hand turns it to show
     * the edge, then flips it back; the main arm shows up empty while the item is away. Item keys are offsets from the
     * normal held pose (x to the right, y up, z towards you; degrees); arm keys move the vanilla first-person arm.
     */
    private static final float[][] CATCH_ITEM = {
        {0, 0, 0, 0, 0, 0, 0},
        {.10f, -.05f, -.05f, .02f, 10, -10, 6}, // dip
        {.20f, -.18f, .06f, .03f, -14, -26, 12}, // raise towards the middle
        {.27f, -.22f, .08f, .03f, -26, -28, 14},
        {.36f, -.56f, .46f, -.22f, -200, -22, 8}, // tossed up, flipping across
        {.45f, -.96f, .18f, -.12f, -340, -16, 4},
        {.50f, -1.08f, .02f, .03f, -364, -12, 2}, // caught by the off hand
        {.55f, -1.07f, .04f, .03f, -360, -12, 2},
        {.64f, -1.0f, .17f, -.02f, -362, 62, -18}, // turned to show the blade
        {.72f, -1.0f, .19f, -.02f, -360, 74, -20},
        {.77f, -1.07f, .04f, .03f, -360, -12, 2},
        {.84f, -.56f, .44f, -.22f, -190, -22, 8}, // tossed back
        {.91f, -.16f, .06f, .03f, -10, -24, 12},
        {1, 0, 0, 0, 0, 0, 0},
    };
    private static final float[][] CATCH_OFF = { // the off-hand arm: x, y, z, then turns around its own shoulder
        {0, 0, -.7f, 0, 0, 0, 0},
        {.14f, 0, -.36f, 0, 0, 0, 0},
        {.27f, 0, -.06f, 0, -4, 0, 0},
        {.43f, .01f, .0f, 0, -8, 0, 0},
        {.50f, 0, -.05f, 0, 5, 0, 0}, // the catch dips the hand
        {.55f, 0, -.01f, 0, 0, 0, 0},
        {.64f, .03f, .09f, -.02f, -8, -10, -4}, // raise and turn to present
        {.72f, .03f, .11f, -.02f, -9, -12, -5},
        {.79f, 0, .0f, 0, -10, 0, 0},
        {.86f, 0, -.02f, 0, -3, 0, 0},
        {.94f, 0, -.42f, 0, 0, 0, 0},
        {1, 0, -.7f, 0, 0, 0, 0},
    };
    private static final float[][] CATCH_MAIN = { // the main arm, empty while the blade is in the other hand
        {0, 0, -.8f, 0, 0, 0, 0},
        {.40f, 0, -.8f, 0, 0, 0, 0},
        {.49f, 0, -.10f, 0, -6, 0, 0},
        {.56f, 0, -.01f, 0, 0, 0, 0},
        {.72f, 0, .0f, 0, 3, 0, 0},
        {.79f, 0, -.04f, 0, -8, 0, 0},
        {.86f, 0, -.8f, 0, 0, 0, 0},
        {1, 0, -.8f, 0, 0, 0, 0},
    };

    private static float[] sample(float[][] k, float t) {
        int i = 0;
        while (i < k.length - 2 && t > k[i + 1][0]) i++;
        float u = Math.max(0, Math.min(1, (t - k[i][0]) / Math.max(1e-4f, k[i + 1][0] - k[i][0])));
        float[] a0 = k[Math.max(0, i - 1)], a1 = k[i], a2 = k[i + 1], a3 = k[Math.min(k.length - 1, i + 2)];
        float[] v = new float[6];
        for (int j = 0; j < 6; j++) v[j] = catmull(a0[j + 1], a1[j + 1], a2[j + 1], a3[j + 1], u);
        return v;
    }

    public float[] armPose(boolean offHand, float t) {
        if (!"Перехват".equals(inspectStyle) || t < 0) return null;
        return sample(offHand ? CATCH_OFF : CATCH_MAIN, t);
    }

    public static void applyArm(PoseStack pose, float[] v, float mirror, float armSide) {
        pose.translate(v[0] * mirror, v[1], v[2]);
        float px = .62f * armSide, py = -.62f, pz = -.6f;
        pose.translate(px, py, pz);
        pose.mulPose(Axis.XP.rotationDegrees(v[3]));
        pose.mulPose(Axis.YP.rotationDegrees(v[4] * armSide));
        pose.mulPose(Axis.ZP.rotationDegrees(v[5] * armSide));
        pose.translate(-px, -py, -pz);
    }

    private static float catmull(float p0, float p1, float p2, float p3, float u) {
        float u2 = u * u, u3 = u2 * u;
        return .5f
                * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3);
    }

    public void applyInspect(PoseStack pose, float t, float side) {
        float[][] k =
                switch (inspectStyle) {
                    case "Перехват" -> CATCH_ITEM;
                    case "Разворот" -> TURN;
                    case "Прокрут" -> SPIN;
                    default -> TOSS;
                };
        float[] v = sample(k, t);
        // idle sway while the item is presented (fades in/out with the animation)
        float env = (float) Math.sin(Math.PI * t);
        double time = System.currentTimeMillis() / 1000.0;
        v[1] += (float) Math.sin(time * 2.1) * .012f * env;
        v[3] += (float) Math.sin(time * 1.7) * 2.5f * env;
        v[5] += (float) Math.cos(time * 1.3) * 2f * env;
        pose.translate(v[0] * side, v[1], v[2]);
        // rotate around the middle of the held item, not the grip
        pose.translate(0, .18f, -.05f);
        pose.mulPose(Axis.YP.rotationDegrees(v[4] * side));
        pose.mulPose(Axis.ZP.rotationDegrees(v[5] * side));
        pose.mulPose(Axis.XP.rotationDegrees(v[3]));
        pose.translate(0, -.18f, .05f);
    }

    /** Applies the chosen swing on top of the held-item pose (vanilla's own swing is zeroed). */
    public void applySwing(PoseStack pose, float t, float side) {
        if (t <= 0) return;
        float s1 = (float) Math.sin(t * Math.PI),
                s2 = (float) Math.sin(Math.sqrt(t) * Math.PI),
                sq = (float) Math.sin(t * t * Math.PI);
        switch (swing.get()) {
            case "Плавный" -> {
                pose.translate(side * -.08f * s1, .06f * s1, -.12f * s1);
                pose.mulPose(Axis.YP.rotationDegrees(side * -25 * s2));
                pose.mulPose(Axis.XP.rotationDegrees(-55 * s1));
                pose.mulPose(Axis.ZP.rotationDegrees(side * -12 * s1));
            }
            case "Рубящий" -> {
                pose.translate(0, .12f * s1, -.1f * s1);
                pose.mulPose(Axis.XP.rotationDegrees(-95 * s2 + 25 * sq));
            }
            case "Укол" -> {
                pose.translate(side * -.05f * s1, .04f * s1, -.5f * s2);
                pose.mulPose(Axis.XP.rotationDegrees(-70 * s1));
            }
            case "Вращение" -> {
                float e = t < .5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
                pose.translate(side * -.1f * s1, .1f * s1, -.1f * s1);
                pose.translate(0, .18f, -.05f);
                pose.mulPose(Axis.XP.rotationDegrees(-360 * e));
                pose.translate(0, -.18f, .05f);
            }
            case "Слайд" -> {
                pose.translate(side * -.32f * s1, -.06f * s1, -.08f * s1);
                pose.mulPose(Axis.ZP.rotationDegrees(side * 35 * s2));
                pose.mulPose(Axis.YP.rotationDegrees(side * -15 * s1));
            }
            case "Старый 1.7" -> {
                pose.translate(0, .1f * s2, 0);
                pose.mulPose(Axis.YP.rotationDegrees(side * (-20 * sq)));
                pose.mulPose(Axis.ZP.rotationDegrees(side * (-20 * s2)));
                pose.mulPose(Axis.XP.rotationDegrees(-80 * s2));
            }
            default -> {}
        }
    }

    public boolean customSwing() {
        return enabled() && !swing.is("Ванильный");
    }
}
