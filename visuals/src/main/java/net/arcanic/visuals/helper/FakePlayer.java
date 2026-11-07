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

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.phys.Vec3;

public final class FakePlayer extends Module {
    final Setting.Bool armour = this.add(new Setting.Bool("armour", "В моей броне", true));
    final Setting.Bool regen = this.add(new Setting.Bool("regen", "Восстанавливать здоровье", true));
    private static final int ID = -7777;
    private RemotePlayer dummy;
    private long hurtAt;

    public FakePlayer() {
        super(
                "fake_player",
                "Манекен",
                "Твоя копия перед тобой, чтобы бить и смотреть эффекты ударов",
                Category.UTILITY,
                false);
    }

    public static boolean is(Entity e) {
        FakePlayer m = Modules.FAKE_PLAYER;
        return m != null && m.dummy != null && e == m.dummy;
    }

    protected void onEnable() {
        this.spawn();
    }

    protected void onDisable() {
        this.remove();
    }

    private void spawn() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            this.remove();
            LocalPlayer me = mc.player;
            RemotePlayer d = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "Манекен")) {
                public PlayerSkin getSkin() {
                    LocalPlayer p = Minecraft.getInstance().player;
                    return p != null ? p.getSkin() : super.getSkin();
                }
            };
            d.setId(-7777);
            Vec3 look = Vec3.directionFromRotation(0.0F, me.getYRot()).scale(2.6);
            Vec3 at = me.position().add(look.x, 0.0, look.z);
            d.snapTo(at.x, at.y, at.z, me.getYRot() + 180.0F, 0.0F);
            d.setYHeadRot(me.getYRot() + 180.0F);
            d.setYBodyRot(me.getYRot() + 180.0F);
            if (this.armour.on()) {
                for (EquipmentSlot s : new EquipmentSlot[] {
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
                }) {
                    d.setItemSlot(s, me.getItemBySlot(s).copy());
                }
            }

            mc.level.addEntity(d);
            this.dummy = d;
        }
    }

    private void remove() {
        Minecraft mc = Minecraft.getInstance();
        if (this.dummy != null && mc.level != null && this.dummy.level() == mc.level) {
            mc.level.removeEntity(-7777, RemovalReason.DISCARDED);
        }

        this.dummy = null;
    }

    public static void hit(Entity target) {
        FakePlayer m = Modules.FAKE_PLAYER;
        if (m != null && m.dummy != null && target == m.dummy) {
            Minecraft mc = Minecraft.getInstance();
            RemotePlayer d = m.dummy;
            d.animateHurt(mc.player.getYRot());
            float dmg = (float) Math.max(1.0, mc.player.getAttributeValue(Attributes.ATTACK_DAMAGE))
                    * Math.max(0.2F, mc.player.getAttackStrengthScale(0.5F));
            d.setHealth(Math.max(1.0F, d.getHealth() - dmg));
            m.hurtAt = System.currentTimeMillis();
            mc.level.playLocalSound(
                    d.getX(), d.getY(), d.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0F, 1.0F, false);
        }
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            if (this.dummy == null || this.dummy.level() != mc.level || this.dummy.isRemoved()) {
                this.spawn();
            }

            if (this.dummy != null
                    && this.regen.on()
                    && System.currentTimeMillis() - this.hurtAt > 2500L
                    && this.dummy.getHealth() < this.dummy.getMaxHealth()) {
                this.dummy.setHealth(Math.min(this.dummy.getMaxHealth(), this.dummy.getHealth() + 1.0F));
            }

        } else {
            this.dummy = null;
        }
    }
}
