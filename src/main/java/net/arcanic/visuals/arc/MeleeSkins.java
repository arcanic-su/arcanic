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
package net.arcanic.visuals.arc;

import java.util.List;
import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Skins of one kind: knives drawn in place of swords, or guns in place of bows. */
public final class MeleeSkins extends Module {
    public final MeleeSlot slot;
    public final Setting.Mode skin;
    public final Setting.Bool firstPerson;
    public final Setting.Bool thirdPerson;
    public final Setting.Num thirdScale;
    public final Setting.Sizes sizes;
    private static final float HAND_ITEM_SIZE = 0.5F;
    private static final float SHIELD_SIZE = 0.75F;
    private static MeleeDesign studioDesign;
    private static ItemStack studioMain;
    private static ItemStack studioOff;
    public static final String EMPTY_HAND = "hand:empty";
    public static final String[] AXES;

    public MeleeSkins() {
        this(
                MeleeSlot.KNIFE,
                "arc_melee",
                "Ножи CS",
                "Ножи Arcanic, оригинальные скины и анимации. Осмотр — G.",
                "Нож",
                "Karambit",
                new double[] {1.6, 0.8, 2.5});
    }

    private MeleeSkins(
            MeleeSlot slot,
            String id,
            String name,
            String description,
            String skinName,
            String skinDefault,
            double[] thirdScaleRange) {
        super(id, name, description, Category.COSMETICS, false);
        this.slot = slot;
        this.skin = this.add(new Setting.Mode("skin", skinName, skinDefault, (String[])
                MeleeCatalog.of(slot).stream().map(MeleeDesign::getDisplayName).toArray(String[]::new)));
        this.firstPerson = this.add(new Setting.Bool("first_person", "От первого лица", true));
        this.thirdPerson = this.add(new Setting.Bool("third_person", "От третьего лица", true));
        Setting.Num num = this.add(new Setting.Num(
                "third_scale",
                "Размер от 3-го лица",
                thirdScaleRange[0],
                thirdScaleRange[1],
                thirdScaleRange[2],
                0.05,
                "×"));
        this.thirdScale = (Setting.Num) num.showIf(this.thirdPerson::on);
        this.sizes = (this.add(new Setting.Sizes("fp_sizes", "Размеры от 1-го лица"))).showIf(() -> false);
    }

    /** The AWP and any other gun drawn in place of a bow; the bow itself still shoots as it always does. */
    public static MeleeSkins guns() {
        return new MeleeSkins(
                MeleeSlot.BOW,
                "arc_bow",
                "AWP CS",
                "AWP из CS2 вместо лука: натяжение поднимает прицел, выстрел, затвор и вспышка. Механика лука не"
                        + " меняется. Осмотр — G.",
                "Оружие",
                "AWP",
                new double[] {1.0, 0.6, 1.6});
    }

    public static MeleeSkins of(MeleeSlot slot) {
        return slot == MeleeSlot.BOW ? Modules.GUNS : Modules.KNIVES;
    }

    public static MeleeSkins of(MeleeDesign design) {
        return of(design.getSlot());
    }

    /** The skins that draw {@code stack} in first person instead of the game, or null. */
    public static MeleeSkins rendering(ItemStack stack) {
        MeleeSlot slot = MeleeSlot.of(stack);
        if (slot == null) return null;
        MeleeSkins skins = of(slot);
        return skins.rendersFirstPerson(stack) ? skins : null;
    }

    /** The skins that draw {@code stack} in third person instead of the game, or null. */
    public static MeleeSkins renderingThirdPerson(ItemStack stack) {
        MeleeSlot slot = MeleeSlot.of(stack);
        if (slot == null) return null;
        MeleeSkins skins = of(slot);
        return skins.rendersThirdPerson(stack) ? skins : null;
    }

    /**
     * The chosen skin. A Premium one only while the account has Premium and its texture is here; until then (or
     * without Premium) the first free skin of the kind is drawn instead.
     */
    public MeleeDesign selected() {
        List<MeleeDesign> designs = MeleeCatalog.of(this.slot);
        for (MeleeDesign design : designs) {
            if (design.getDisplayName().equals(this.skin.get()) && PremiumSkins.ready(design)) return design;
        }
        for (MeleeDesign design : designs) {
            if (!design.isPremium()) return design;
        }
        return null;
    }

    /** On in the game, or the studio's picture shows a skin of this kind. */
    private boolean on() {
        return inStudio() ? studioDesign.getSlot() == this.slot : this.enabled();
    }

    public boolean applies(ItemStack stack) {
        return this.on() && this.slot.holds(stack);
    }

    public static void beginStudio(MeleeDesign design, ItemStack main, ItemStack off) {
        studioDesign = design;
        studioMain = main;
        studioOff = off;
    }

    public static void endStudio() {
        studioDesign = null;
        studioMain = ItemStack.EMPTY;
        studioOff = ItemStack.EMPTY;
    }

    public static boolean inStudio() {
        return studioDesign != null;
    }

    static MeleeDesign studioDesign() {
        return studioDesign;
    }

    public static ItemStack stack(Player p, InteractionHand hand) {
        if (studioDesign != null) {
            return hand == InteractionHand.MAIN_HAND ? studioMain : studioOff;
        } else {
            return p == null ? ItemStack.EMPTY : p.getItemInHand(hand);
        }
    }

    private static LocalPlayer player() {
        return Minecraft.getInstance().player;
    }

    public boolean active() {
        return this.on() && MeleeCatalog.equipped(this.slot) != null;
    }

    public boolean holdsSkinned(InteractionHand hand) {
        LocalPlayer p = player();
        return (p != null || inStudio()) && this.applies(stack(p, hand));
    }

    public boolean handles(InteractionHand hand) {
        return this.active() && this.holdsSkinned(hand);
    }

    public boolean handlesAnyHand() {
        return this.handles(InteractionHand.MAIN_HAND) || this.handles(InteractionHand.OFF_HAND);
    }

    public boolean rendersFirstPerson(ItemStack stack) {
        return (this.firstPerson.on() || inStudio()) && this.active() && this.applies(stack);
    }

    public boolean rendersThirdPerson(ItemStack stack) {
        return this.thirdPerson.on() && this.active() && this.applies(stack);
    }

    public boolean hidesEmptyMainHand(Player p, InteractionHand hand, ItemStack stack) {
        return p != null
                && hand == InteractionHand.MAIN_HAND
                && stack != null
                && stack.isEmpty()
                && this.rendersFirstPerson(stack(p, InteractionHand.OFF_HAND));
    }

    /**
     * A gun in the main hand is held with both hands, so the off hand's item is put out of view, unless that
     * hand is busy with it (eating, raising a shield).
     */
    public boolean hidesOtherHandItem(Player p, InteractionHand hand, ItemStack stack) {
        return this.slot.twoHanded()
                && p != null
                && hand == InteractionHand.OFF_HAND
                && stack != null
                && !stack.isEmpty()
                && !usesHand(p, hand)
                && this.rendersFirstPerson(stack(p, InteractionHand.MAIN_HAND));
    }

    public static boolean keepsVanillaPose(ItemStack stack) {
        return stack != null && stack.has(DataComponents.BLOCKS_ATTACKS);
    }

    public static boolean usesHand(Player p, InteractionHand hand) {
        return !inStudio() && p != null && p.isUsingItem() && p.getUsedItemHand() == hand;
    }

    public boolean attachesHandItem(Player p, InteractionHand hand, ItemStack stack) {
        return !this.slot.twoHanded()
                        && (this.firstPerson.on() || inStudio())
                        && p != null
                        && stack != null
                        && !stack.isEmpty()
                        && !keepsVanillaPose(stack)
                        && !stack.has(DataComponents.MAP_ID)
                        && !usesHand(p, hand)
                        && rendering(stack) == null
                ? this.holdsSkinned(other(hand))
                : false;
    }

    public boolean leavesOtherHand(Player p, InteractionHand knifeHand) {
        if (p == null) {
            return false;
        } else {
            InteractionHand other = other(knifeHand);
            ItemStack stack = stack(p, other);
            if (stack.isEmpty()) return false;
            if (this.slot.twoHanded()) return !this.hidesOtherHandItem(p, other, stack);
            return rendering(stack) == null && !this.attachesHandItem(p, other, stack);
        }
    }

    private static InteractionHand other(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    public static String knifeKey(MeleeDesign design) {
        return "knife:" + design.getKey();
    }

    /** The hand that does not hold the skin: where it sits and how large, kept for each skin. */
    public static String otherHandKey(MeleeDesign design) {
        return knifeKey(design) + "/hand";
    }

    public static String itemKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static String handKey(ItemStack stack) {
        return stack != null && !stack.isEmpty() ? itemKey(stack) : "hand:empty";
    }

    public static String axisKey(String key, String axis) {
        return key + "#" + axis;
    }

    public Placement placement(String key) {
        float size = (float) Math.max(0.2, Math.min(3.0, this.sizes.of(key)));
        return new Placement(
                (float) this.sizes.of(axisKey(key, "x"), 0.0),
                (float) this.sizes.of(axisKey(key, "y"), 0.0),
                (float) this.sizes.of(axisKey(key, "z"), 0.0),
                size);
    }

    public float knifeSize(MeleeDesign design) {
        return design == null ? 1.0F : this.placement(knifeKey(design)).size();
    }

    public float itemSize(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                ? this.placement(itemKey(stack)).size()
                : 1.0F;
    }

    public float inRigHand(ItemStack stack) {
        return 0.5F * this.itemSize(stack);
    }

    public Placement besideKnife(Player p, InteractionHand hand, ItemStack stack) {
        if (stack != null && !stack.isEmpty() && !this.rendersFirstPerson(stack)) {
            if (!this.rendersFirstPerson(stack(p, other(hand)))) {
                return null;
            } else {
                Placement at = this.placement(itemKey(stack));
                float base = keepsVanillaPose(stack) ? 0.75F : 1.0F;
                return new Placement(at.x(), at.y(), at.z(), base * at.size());
            }
        } else {
            return null;
        }
    }

    public Placement rigOtherHand(Player p, InteractionHand knifeHand) {
        if (this.slot.twoHanded()) return null; // the support hand stays on the gun
        InteractionHand other = other(knifeHand);
        ItemStack stack = stack(p, other);
        if (stack.isEmpty()) {
            return this.placement("hand:empty");
        } else {
            return this.attachesHandItem(p, other, stack) ? this.placement(itemKey(stack)) : null;
        }
    }

    private boolean drawsAnyHand() {
        return (this.firstPerson.on() || this.thirdPerson.on()) && this.handlesAnyHand();
    }

    /** Each client tick, whichever skins are on: lets the hand animators go once nothing skinned is drawn. */
    public static void tickAnimators() {
        if (!Modules.KNIVES.drawsAnyHand() && !Modules.GUNS.drawsAnyHand()) {
            MeleeAnimator.getInstance().stop();
            MeleeAnimator.offHand().stop();
        } else {
            if (!MeleeAnimator.getInstance().consumeRendered()) {
                MeleeAnimator.getInstance().stop();
            }

            if (!MeleeAnimator.offHand().consumeRendered()) {
                MeleeAnimator.offHand().stop();
            }
        }
    }

    protected void onDisable() {
        MeleeAnimator.resetAll();
    }

    static {
        studioMain = ItemStack.EMPTY;
        studioOff = ItemStack.EMPTY;
        AXES = new String[] {"x", "y", "z"};
    }

    public static record Placement(float x, float y, float z, float size) {
        public static final Placement NONE = new Placement(0.0F, 0.0F, 0.0F, 1.0F);

        public boolean moved() {
            return this.x != 0.0F || this.y != 0.0F || this.z != 0.0F;
        }
    }
}
