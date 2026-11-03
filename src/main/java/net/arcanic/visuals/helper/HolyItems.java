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

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public final class HolyItems {
    public static final Map<String, Trap> TRAPS = Map.of(
            "ALTERNATIVE_TRAP",
            new Trap("ALTERNATIVE_TRAP", "Трапка", 3, 4, 3, 0, 11.0F, 3.0F),
            "STUN",
            new Trap("STUN", "Стан", 30, 30, 30, -15, 15.0F, 0.0F));
    public static final Map<String, Blast> BLASTS =
            Map.of("EXPLOSIVE_TRAP", new Blast("EXPLOSIVE_TRAP", "Взрывная трапка", 4.0F, 2.75F, 6.5F, 8));
    public static final Tnt TNT_A = new Tnt("TNT_A", "Динамит A", 12.0F, 3.0F);
    public static final Tnt TNT_B = new Tnt("TNT_B", "Динамит B", 40.0F, 10.0F);
    public static final Tnt C4 = new Tnt("C4", "C4", 4.0F, 1.0F);
    public static final Tnt PLAIN = new Tnt("TNT", "TNT", 4.0F, 1.0F);
    private static final Pattern A_NAME = Pattern.compile("(ДИНAМИТ|TNT|DYNAMITE)[\\s_-]*A(?![0-9A-ZА-Я])");
    private static final Pattern B_NAME = Pattern.compile("(ДИНAМИТ|TNT|DYNAMITE)[\\s_-]*B(?![0-9A-ZА-Я])");
    private static final Pattern C4_NAME = Pattern.compile("(?<![0-9A-ZА-Я])C4(?![0-9A-ZА-Я])");

    private HolyItems() {}

    private static String kindOf(ItemStack s) {
        String id = id(s);
        if (id == null || !TRAPS.containsKey(id) && !BLASTS.containsKey(id)) {
            if (s != null && !s.isEmpty()) {
                String name = s.getHoverName()
                        .getString()
                        .replaceAll("[&§][0-9a-fk-orA-FK-OR]", "")
                        .trim();
                if (name.equalsIgnoreCase("Стан")) {
                    return "STUN";
                } else {
                    return id != null && id.toUpperCase(Locale.ROOT).contains("STUN") ? "STUN" : id;
                }
            } else {
                return null;
            }
        } else {
            return id;
        }
    }

    public static String id(ItemStack s) {
        if (s != null && !s.isEmpty()) {
            CustomData data = (CustomData) s.get(DataComponents.CUSTOM_DATA);
            return data == null
                    ? null
                    : (String) data.copyTag()
                            .getCompound("pyrotechnic-item")
                            .flatMap((t) -> t.getString("name"))
                            .orElse(null);
        } else {
            return null;
        }
    }

    public static Blast heldBlast(Player p) {
        if (p == null) {
            return null;
        } else {
            for (InteractionHand hand : InteractionHand.values()) {
                String id = id(p.getItemInHand(hand));
                if (id != null && BLASTS.containsKey(id)) {
                    return (Blast) BLASTS.get(id);
                }
            }

            return null;
        }
    }

    public static float blastDamage(LivingEntity e, Vec3 at, Blast b) {
        return !e.getBoundingBox().intersects(b.box(at)) ? 0.0F : explosionDamage(e, at, b.power());
    }

    public static float explosionDamage(LivingEntity e, Vec3 at, float power) {
        return explosionDamage(e, at, power, (BlockPos) null, true);
    }

    public static float explosionDamage(LivingEntity e, Vec3 at, float power, BlockPos self, boolean lift) {
        double dist = e.position().distanceTo(at);
        if (dist > (double) (power * 2.0F)) {
            return 0.0F;
        } else {
            Vec3 from = lift ? at.add(0.0, 0.5, 0.0) : at;
            return damageAt(e, dist, seenFrom(e, from, self), power);
        }
    }

    public static float seenFrom(LivingEntity e, Vec3 at, BlockPos self) {
        return self == null ? ServerExplosion.getSeenPercent(at, e) : seen(at, e, self, (BlockPos) null, Vec3.ZERO);
    }

    public static float explosionDamage(LivingEntity e, Vec3 at, float power, BlockPos self, Vec3 shift) {
        double dist = e.position().add(shift).distanceTo(at);
        return dist > (double) (power * 2.0F)
                ? 0.0F
                : damageAt(e, dist, seen(at, e, self, (BlockPos) null, shift), power);
    }

    public static float explosionDamageBehind(LivingEntity e, Vec3 at, float power, BlockPos solid, Vec3 shift) {
        double dist = e.position().add(shift).distanceTo(at);
        return dist > (double) (power * 2.0F)
                ? 0.0F
                : damageAt(e, dist, seen(at, e, (BlockPos) null, solid, shift), power);
    }

    public static float seenBehind(LivingEntity e, Vec3 at, BlockPos solid) {
        return seen(at, e, (BlockPos) null, solid, Vec3.ZERO);
    }

    public static float damageAt(LivingEntity e, double dist, float seen, float power) {
        double reach = (double) (power * 2.0F);
        double d = dist / reach;
        if (d > 1.0) {
            return 0.0F;
        } else {
            double impact = Math.max(0.0, (1.0 - d) * (double) seen);
            float dmg = (float) ((impact * impact + impact) / 2.0 * 7.0 * reach + 1.0);
            if (e instanceof Player) {
                float var10000;
                switch (HolyEvents.onHolyWorld() ? Difficulty.NORMAL : e.level().getDifficulty()) {
                    case PEACEFUL -> var10000 = 0.0F;
                    case EASY -> var10000 = Math.min(dmg / 2.0F + 1.0F, dmg);
                    case NORMAL -> var10000 = dmg;
                    case HARD -> var10000 = dmg * 3.0F / 2.0F;
                    default -> throw new MatchException((String) null, (Throwable) null);
                }

                dmg = var10000;
            }

            if (dmg <= 0.0F) {
                return 0.0F;
            } else {
                DamageSource src = e.level().damageSources().explosion((Entity) null, (Entity) null);
                dmg = CombatRules.getDamageAfterAbsorb(e, dmg, src, (float) e.getArmorValue(), (float)
                        e.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
                int epf = 0;

                try {
                    Registry<Enchantment> reg = e.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                    Holder.Reference<Enchantment> prot =
                            (Holder.Reference) reg.get(Enchantments.PROTECTION).orElse(null);
                    Holder.Reference<Enchantment> blast = (Holder.Reference)
                            reg.get(Enchantments.BLAST_PROTECTION).orElse(null);

                    for (EquipmentSlot slot : new EquipmentSlot[] {
                        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
                    }) {
                        ItemStack s = e.getItemBySlot(slot);
                        if (prot != null) {
                            epf += EnchantmentHelper.getItemEnchantmentLevel(prot, s);
                        }

                        if (blast != null) {
                            epf += 2 * EnchantmentHelper.getItemEnchantmentLevel(blast, s);
                        }
                    }
                } catch (RuntimeException ignored) {
                }

                return dmg * (1.0F - (float) Math.min(20, epf) / 25.0F);
            }
        }
    }

    private static String latin(String s) {
        StringBuilder b = new StringBuilder(s.length());

        for (char c : s.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "")
                .toUpperCase(Locale.ROOT)
                .toCharArray()) {
            char var10001;
            switch (c) {
                case 'А' -> var10001 = 'A';
                case 'В' -> var10001 = 'B';
                case 'Е' -> var10001 = 'E';
                case 'С' -> var10001 = 'C';
                default -> var10001 = c;
            }

            b.append(var10001);
        }

        return b.toString();
    }

    public static Tnt tnt(ItemStack s) {
        if (s != null && !s.isEmpty() && s.is(Items.TNT)) {
            String learned = ItemCatalog.tntFor(id(s));
            if (learned != null) {
                for (Tnt t : new Tnt[] {TNT_A, TNT_B, C4}) {
                    if (t.id().equals(learned)) {
                        return t;
                    }
                }
            }

            return tntByName(s);
        } else {
            return null;
        }
    }

    public static Tnt tntByName(ItemStack s) {
        if (s != null && !s.isEmpty() && s.is(Items.TNT)) {
            String own = id(s);
            String key = latin((own == null ? "" : own) + " " + s.getHoverName().getString());
            if (C4_NAME.matcher(key).find()) {
                return C4;
            } else if (A_NAME.matcher(key).find()) {
                return TNT_A;
            } else {
                return B_NAME.matcher(key).find() ? TNT_B : null;
            }
        } else {
            return null;
        }
    }

    public static Tnt heldTnt(Player p) {
        if (p == null) {
            return null;
        } else {
            for (InteractionHand hand : InteractionHand.values()) {
                Tnt t = tnt(p.getItemInHand(hand));
                if (t != null) {
                    return t;
                }
            }

            return null;
        }
    }

    private static float seen(Vec3 at, Entity e, BlockPos self, BlockPos solid, Vec3 shift) {
        AABB box = e.getBoundingBox().move(shift);
        AABB cube = solid == null ? null : new AABB(solid);
        double dx = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double dy = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double dz = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        double ox = (1.0 - Math.floor(1.0 / dx) * dx) / 2.0;
        double oz = (1.0 - Math.floor(1.0 / dz) * dz) / 2.0;
        int clear = 0;
        int all = 0;

        for (double x = 0.0; x <= 1.0; x += dx) {
            for (double y = 0.0; y <= 1.0; y += dy) {
                for (double z = 0.0; z <= 1.0; z += dz) {
                    Vec3 p = new Vec3(
                            box.minX + (box.maxX - box.minX) * x + ox,
                            box.minY + (box.maxY - box.minY) * y,
                            box.minZ + (box.maxZ - box.minZ) * z + oz);
                    BlockHitResult hit = e.level().clip(new ClipContext(p, at, Block.COLLIDER, Fluid.NONE, e));
                    boolean open = hit.getType() == Type.MISS
                            || self != null && hit.getBlockPos().equals(self);
                    if (open && cube != null && cube.clip(p, at).isPresent()) {
                        open = false;
                    }

                    if (open) {
                        ++clear;
                    }

                    ++all;
                }
            }
        }

        return all == 0 ? 0.0F : (float) clear / (float) all;
    }

    public static Trap heldTrap(Player p) {
        if (p == null) {
            return null;
        } else {
            for (InteractionHand hand : InteractionHand.values()) {
                String id = kindOf(p.getItemInHand(hand));
                if (id != null && TRAPS.containsKey(id)) {
                    return (Trap) TRAPS.get(id);
                }
            }

            return null;
        }
    }

    public static record Trap(String id, String name, int w, int h, int d, int floor, float seconds, float damage) {
        public AABB box(BlockPos feet) {
            int x = feet.getX() - (this.w - 1) / 2;
            int y = feet.getY() + this.floor;
            int z = feet.getZ() - (this.d - 1) / 2;
            return new AABB((double) x, (double) y, (double) z, (double) (x + this.w), (double) (y + this.h), (double)
                    (z + this.d));
        }
    }

    public static record Blast(String id, String name, float power, float fuse, float crater, int size) {
        public AABB box(Vec3 at) {
            int x = (int) Math.round(at.x) - this.size / 2;
            int z = (int) Math.round(at.z) - this.size / 2;
            int y = (int) Math.floor(at.y) - 1;
            return new AABB(
                    (double) x, (double) y, (double) z, (double) (x + this.size), (double) (y + this.size), (double)
                            (z + this.size));
        }
    }

    public static record Tnt(String id, String name, float power, float scale) {
        public float breaks() {
            return 5.32F * this.scale;
        }

        public float reach() {
            return this.power * 2.0F;
        }
    }
}
