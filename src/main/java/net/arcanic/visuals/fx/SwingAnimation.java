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
import java.util.Arrays;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionfc;

public final class SwingAnimation extends Module {
    public final Setting.Mode mode = this.add(new Setting.Mode(
            "mode", "Режим", "Плавная", new String[] {"Плавная", "Панч", "Слайд", "Свэговая", "В себя"}));
    public final Setting.Num strength = this.add(new Setting.Num("strength", "Сила взмаха", 1.0, 0.0, 15.0, 1.0, ""));
    public final Setting.Bool mainOnly = this.add(new Setting.Bool("main_only", "Только в главной руке", true));
    public final Setting.Num smooth =
            (Setting.Num) this.add(new Setting.Num("smooth", "Плавность", 2.0, 1.0, 3.0, 0.1, ""));
    private final float[] progress = new float[2];
    private final long[] lastNanos = new long[2];

    public SwingAnimation() {
        super("swing_animation", "Swing Animation", "Пять стилей взмаха", Category.COMBAT, false);
    }

    public boolean activeFor(InteractionHand hand) {
        return this.enabled() && (!this.mainOnly.on() || hand == InteractionHand.MAIN_HAND);
    }

    protected void onEnable() {
        this.reset();
    }

    protected void onDisable() {
        this.reset();
    }

    private void reset() {
        Arrays.fill(this.progress, 0.0F);
        Arrays.fill(this.lastNanos, 0L);
    }

    private float step(int arm) {
        long now = System.nanoTime();
        long last = this.lastNanos[arm];
        this.lastNanos[arm] = now;
        if (last == 0L) {
            return 0.0F;
        } else {
            float dt = (float) (now - last) / 1.0E9F;
            return dt <= 0.0F ? 0.0F : Math.min(dt, 0.25F);
        }
    }

    public void apply(float swing, HumanoidArm arm, PoseStack pose) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        int slot = arm == HumanoidArm.RIGHT ? 0 : 1;
        float shown = this.progress[slot];
        float dt = this.step(slot);
        if (swing >= shown) {
            shown = swing;
        } else if (shown > 0.0F) {
            shown += dt / Math.max(0.05F, 0.3F * (this.smooth.f() / 2.0F));
            if (shown >= 1.0F) {
                shown = 0.0F;
            }
        }

        this.progress[slot] = shown;
        float power = this.strength.f() / 20.0F;
        switch (SwingMode.of((String) this.mode.get())) {
            case SMOOTH:
                float a = Mth.sin((double) (shown * shown * (float) Math.PI));
                float b = Mth.sin((double) (Mth.sqrt(shown) * (float) Math.PI));
                pose.mulPose((Quaternionfc) Axis.YP.rotationDegrees((float) side * (45.0F - a * 20.0F * power)));
                pose.mulPose((Quaternionfc) Axis.ZP.rotationDegrees((float) side * b * -20.0F * power));
                pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees(b * -80.0F * power));
                pose.mulPose((Quaternionfc) Axis.YP.rotationDegrees((float) (side * -45)));
                break;
            case PUNCH:
                float k = Mth.sin((double) (shown * (float) Math.PI));
                pose.translate(0.0F, 0.02F, -(k * 0.002F * power));
                pose.scale(1.0F, 1.0F, k * power * 0.5F + 1.0F);
                pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees(-90.0F * k * power));
                break;
            case SLIDE:
                k = Mth.sin((double) (shown * (float) Math.PI));
                pose.translate((float) side * 0.19F, 0.17F, -0.28F);
                pose.mulPose((Quaternionfc) Axis.YP.rotationDegrees((float) (side * 90)));
                pose.mulPose((Quaternionfc) Axis.ZP.rotationDegrees((float) (side * -60)));
                pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees(-90.0F - power * 50.0F * k));
                break;
            case SWAG:
                k = Mth.sin((double) (shown * (float) Math.PI));
                pose.translate(0.0F, 0.2F, -1.5F * k / 5.0F * power);
                pose.mulPose((Quaternionfc) Axis.YP.rotationDegrees(80.0F));
                pose.mulPose((Quaternionfc) Axis.ZN.rotationDegrees(45.0F));
                pose.mulPose((Quaternionfc) Axis.YP.rotationDegrees(-10.0F));
                pose.translate(0.0F, 0.0F, -0.4F * k * power);
                pose.mulPose((Quaternionfc) Axis.XN.rotationDegrees(k * -100.0F * power));
                pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees(k * -180.0F * power));
                pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees(-70.0F));
                break;
            case SELF_BACK:
                k = Mth.sin((double) (shown * (float) Math.PI));
                pose.mulPose((Quaternionfc) Axis.YP.rotationDegrees((float) (90 * side)));
                pose.mulPose((Quaternionfc) Axis.ZP.rotationDegrees((float) (-70 * side)));
                pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees((-100.0F - 60.0F * k) * power));
        }
    }
}
