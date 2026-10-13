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

import net.arcanic.visuals.core.Category;
import net.arcanic.visuals.core.Module;
import net.arcanic.visuals.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Melee skins: animated knife models that replace the held sword in first person. */
public final class MeleeSkins extends Module {
    public final Setting.Mode skin = add(new Setting.Mode(
            "skin",
            "Нож",
            "Karambit",
            MeleeCatalog.all().stream().map(MeleeDesign::getDisplayName).toArray(String[]::new)));
    public final Setting.Bool firstPerson = add(new Setting.Bool("first_person", "От первого лица", true));
    public final Setting.Bool thirdPerson = add(new Setting.Bool("third_person", "От третьего лица", true));
    public final Setting.Num thirdScale = add(new Setting.Num(
                    "third_scale", "Размер от 3-го лица", 1.6, .8, 2.5, .05, "×"))
            .showIf(thirdPerson::on);

    public MeleeSkins() {
        super(
                "arc_melee",
                "Ножи CS",
                "Ножи Arcanic, оригинальные скины и анимации. Осмотр — G.",
                Category.COSMETICS,
                false);
    }

    public MeleeDesign selected() {
        return MeleeCatalog.all().stream()
                .filter(s -> s.getDisplayName().equals(skin.get()))
                .findFirst()
                .orElse(MeleeCatalog.all().getFirst());
    }

    public boolean applies(ItemStack stack) {
        return enabled() && stack != null && !stack.isEmpty() && stack.is(ItemTags.SWORDS);
    }

    // ---- which hand shows a knife
    private static LocalPlayer player() {
        return Minecraft.getInstance().player;
    }

    public boolean active() {
        return enabled() && MeleeCatalog.equipped() != null;
    }

    public boolean holdsSkinned(InteractionHand hand) {
        var p = player();
        return p != null && applies(p.getItemInHand(hand));
    }

    public boolean handles(InteractionHand hand) {
        return active() && holdsSkinned(hand);
    }

    public boolean handlesAnyHand() {
        return handles(InteractionHand.MAIN_HAND) || handles(InteractionHand.OFF_HAND);
    }

    public boolean rendersFirstPerson(ItemStack stack) {
        return firstPerson.on() && active() && applies(stack);
    }

    public boolean rendersThirdPerson(ItemStack stack) {
        return thirdPerson.on() && active() && applies(stack);
    }

    public boolean hidesEmptyMainHand(Player p, InteractionHand hand, ItemStack stack) {
        return p != null
                && hand == InteractionHand.MAIN_HAND
                && stack != null
                && stack.isEmpty()
                && rendersFirstPerson(p.getOffhandItem());
    }

    public static boolean keepsVanillaPose(ItemStack stack) {
        return stack != null && stack.has(DataComponents.BLOCKS_ATTACKS);
    }

    public static boolean usesHand(Player p, InteractionHand hand) {
        return p != null && p.isUsingItem() && p.getUsedItemHand() == hand;
    }

    public boolean attachesHandItem(Player p, InteractionHand hand, ItemStack stack) {
        if (!firstPerson.on()
                || p == null
                || stack == null
                || stack.isEmpty()
                || keepsVanillaPose(stack)
                || stack.has(DataComponents.MAP_ID)
                || usesHand(p, hand)) return false;
        var other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return holdsSkinned(other);
    }

    @Override
    public void tick() {
        boolean first = firstPerson.on() && handlesAnyHand(), third = thirdPerson.on() && handlesAnyHand();
        if (!first && !third) {
            MeleeAnimator.getInstance().stop();
            MeleeAnimator.offHand().stop();
            return;
        }
        if (!MeleeAnimator.getInstance().consumeRendered())
            MeleeAnimator.getInstance().stop();
        if (!MeleeAnimator.offHand().consumeRendered()) MeleeAnimator.offHand().stop();
    }

    @Override
    protected void onDisable() {
        MeleeAnimator.resetAll();
    }
}
