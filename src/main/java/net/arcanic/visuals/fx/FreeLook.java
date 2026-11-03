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

import com.mojang.blaze3d.platform.InputConstants;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.arcanic.visuals.render.Anim;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;

public final class FreeLook extends Module {
    private final Setting.Mode key =
            this.add(new Setting.Mode("key", "Клавиша", "Левый Alt", "Левый Alt", "X", "V", "Z", "Колёсико мыши"));
    private final Setting.Mode mode =
            this.add(new Setting.Mode("mode", "Режим", "Удерживать", "Удерживать", "Переключать"));
    private final Setting.Num smooth =
            this.add(new Setting.Num("smooth", "Плавность камеры", 50.0, 0.0, 100.0, 5.0, "%"));
    private boolean held;
    private boolean owns;
    private boolean wasDown;
    private float yaw;
    private float pitch;
    private float camYaw;
    private float camPitch;
    private float zoom;
    private boolean fromFirst;
    private CameraType before;
    private long last;
    private long outAt;
    private long backAt;
    private float outFrom;
    private float backYaw;
    private float backPitch;
    private float backZoom;
    private float backMs;
    private boolean finishing;
    private static final float OUT_MS = 230.0F;

    public FreeLook() {
        super(
                "free_look",
                "Free Look",
                "Камера крутится вокруг тебя мышью, а ты идёшь туда же, куда шёл",
                Category.WORLD,
                false);
    }

    public boolean active() {
        return this.held && this.enabled();
    }

    public boolean owns() {
        return this.owns;
    }

    public boolean hideSelf() {
        return this.owns && this.fromFirst && (this.finishing || this.zoom < 0.16F);
    }

    public void turn(double dx, double dy) {
        this.yaw += (float) dx * 0.15F;
        this.pitch = Mth.clamp(this.pitch + (float) dy * 0.15F, -90.0F, 90.0F);
    }

    public void frame(float partial) {
        long t = System.nanoTime();
        float dt = this.last == 0L ? 0.0F : Math.min(0.05F, (float) (t - this.last) / 1.0E9F);
        this.last = t;
        LocalPlayer p = Minecraft.getInstance().player;
        if (this.owns && p != null) {
            long now = System.currentTimeMillis();
            if (this.held) {
                float k = 1.0F - (float) Math.exp((double) (-dt * (60.0F - this.smooth.f() * 0.5F)));
                this.camYaw += Mth.wrapDegrees(this.yaw - this.camYaw) * k;
                this.camPitch += (this.pitch - this.camPitch) * k;
                float o = Math.min(1.0F, (float) (now - this.outAt) / 230.0F);
                this.zoom = this.fromFirst ? this.outFrom + (1.0F - this.outFrom) * Anim.easeOutCubic(o) : 1.0F;
            } else {
                float b = Math.min(1.0F, (float) (now - this.backAt) / this.backMs);
                float e =
                        b < 0.5F ? 4.0F * b * b * b : 1.0F - (float) Math.pow((double) (-2.0F * b + 2.0F), 3.0) / 2.0F;
                this.camYaw = this.backYaw + Mth.wrapDegrees(p.getViewYRot(partial) - this.backYaw) * e;
                this.camPitch = this.backPitch + (p.getViewXRot(partial) - this.backPitch) * e;
                this.zoom = this.fromFirst ? this.backZoom * (1.0F - e) : 1.0F;
                if (b >= 1.0F) {
                    this.finishing = true;
                }
            }
        }
    }

    public void beforeFrame() {
        if (this.finishing) {
            this.release();
        }
    }

    public float yaw() {
        return this.camYaw;
    }

    public float pitch() {
        return this.camPitch;
    }

    public float zoom() {
        return this.fromFirst ? this.zoom : 1.0F;
    }

    private boolean down(Minecraft mc) {
        long win = mc.getWindow().handle();
        return switch (this.key.get()) {
            case "X" -> InputConstants.isKeyDown(mc.getWindow(), 88);
            case "V" -> InputConstants.isKeyDown(mc.getWindow(), 86);
            case "Z" -> InputConstants.isKeyDown(mc.getWindow(), 90);
            case "Колёсико мыши" -> GLFW.glfwGetMouseButton(win, 2) == 1;
            default -> InputConstants.isKeyDown(mc.getWindow(), 342);
        };
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            this.held = false;
            this.release();
        } else {
            if (this.finishing && !this.held) {
                this.release();
            }

            boolean d = mc.screen == null && this.down(mc);
            boolean want = this.mode.is("Переключать") ? this.held ^ (d && !this.wasDown) : d;
            this.wasDown = d;
            if (want && !this.held) {
                this.start(mc);
            } else if (!want && this.held) {
                this.back(mc);
            }
        }
    }

    private void start(Minecraft mc) {
        this.held = true;
        this.finishing = false;
        if (!this.owns) {
            this.yaw = mc.player.getYRot();
            this.pitch = mc.player.getXRot();
            this.camYaw = this.yaw;
            this.camPitch = this.pitch;
            this.before = mc.options.getCameraType();
            this.fromFirst = this.before.isFirstPerson();
            this.zoom = 0.0F;
            this.last = 0L;
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            this.owns = true;
        } else {
            this.yaw = this.camYaw;
            this.pitch = this.camPitch;
        }

        this.outFrom = this.zoom;
        this.outAt = System.currentTimeMillis();
    }

    private void back(Minecraft mc) {
        this.held = false;
        this.backAt = System.currentTimeMillis();
        this.backYaw = this.camYaw;
        this.backPitch = this.camPitch;
        this.backZoom = this.zoom;
        float turn = Math.abs(Mth.wrapDegrees(mc.player.getYRot() - this.camYaw))
                + Math.abs(mc.player.getXRot() - this.camPitch);
        this.backMs = Math.max(this.fromFirst ? 200.0F : 150.0F, 140.0F + Math.min(200.0F, turn) * 0.7F);
    }

    private void release() {
        if (this.owns) {
            this.owns = false;
            this.held = false;
            this.finishing = false;
            Minecraft mc = Minecraft.getInstance();
            if (this.before != null) {
                mc.options.setCameraType(this.before);
            }

            if (this.fromFirst && mc.player != null) {
                mc.gameRenderer.itemInHandRenderer.itemUsed(InteractionHand.MAIN_HAND);
                mc.gameRenderer.itemInHandRenderer.itemUsed(InteractionHand.OFF_HAND);
            }
        }
    }

    protected void onDisable() {
        this.held = false;
        this.release();
    }
}
