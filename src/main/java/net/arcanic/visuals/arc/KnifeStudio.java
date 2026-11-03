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

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.Lighting.Entry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.arcanic.visuals.mixin.ItemInHandRendererAccess;
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.CachedPerspectiveProjectionMatrixBuffer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class KnifeStudio {
    public static MeleeDesign design;
    public static boolean knifeRight = true;
    public static ItemStack other;
    private static final ItemStack KNIFE;
    private static final ItemStack GUN;
    private static final float FOV = 70.0F;
    public static final List<ItemStack> ITEMS;

    private KnifeStudio() {}

    public static void open(MeleeDesign knife) {
        if (knife != null) {
            design = knife;
            MeleeModels.preload(knife);
            MeleeAnimator.resetStudio();
        }
    }

    public static void close() {
        design = null;
        MeleeAnimator.resetStudio();
    }

    public static void inspect() {
        MeleeAnimator.studio().onInspect();
    }

    /** A swing for a knife, a shot for a gun. */
    public static void attack() {
        if (design != null && design.getSlot() == MeleeSlot.BOW) {
            MeleeAnimator.studio().onFire();
        } else {
            MeleeAnimator.studio().onAttack(true);
        }
    }

    /** Whether the studio shows a gun, which takes both hands and so has nothing in the other one. */
    public static boolean gun() {
        return design != null && design.getSlot().twoHanded();
    }

    public static void register() {
        SpecialGuiElementRegistry.register((ctx) -> new Renderer(ctx.vertexConsumers()));
    }

    static {
        other = new ItemStack(Items.SHIELD);
        KNIFE = new ItemStack(Items.NETHERITE_SWORD);
        GUN = new ItemStack(Items.BOW);
        ITEMS = List.of(
                ItemStack.EMPTY,
                new ItemStack(Items.SHIELD),
                new ItemStack(Items.TOTEM_OF_UNDYING),
                new ItemStack(Items.GOLDEN_APPLE),
                new ItemStack(Items.ENCHANTED_GOLDEN_APPLE),
                new ItemStack(Items.ENDER_PEARL),
                new ItemStack(Items.OBSIDIAN),
                new ItemStack(Items.COBWEB),
                new ItemStack(Items.TORCH),
                new ItemStack(Items.COOKED_BEEF),
                new ItemStack(Items.SPLASH_POTION),
                new ItemStack(Items.EXPERIENCE_BOTTLE),
                new ItemStack(Items.WATER_BUCKET),
                new ItemStack(Items.FIREWORK_ROCKET));
    }

    public static record Picture(int x0, int y0, int x1, int y1) implements PictureInPictureRenderState {
        public float scale() {
            return 1.0F;
        }

        public ScreenRectangle scissorArea() {
            return null;
        }

        public ScreenRectangle bounds() {
            return PictureInPictureRenderState.getBounds(this.x0, this.y0, this.x1, this.y1, (ScreenRectangle) null);
        }
    }

    private static final class Renderer extends PictureInPictureRenderer<Picture> {
        private final CachedPerspectiveProjectionMatrixBuffer projection =
                new CachedPerspectiveProjectionMatrixBuffer("arcanic knife studio", 0.05F, 100.0F);

        Renderer(MultiBufferSource.BufferSource buffers) {
            super(buffers);
        }

        public Class<Picture> getRenderStateClass() {
            return Picture.class;
        }

        protected boolean textureIsReadyToBlit(Picture state) {
            return false;
        }

        protected String getTextureLabel() {
            return "arcanic knife studio";
        }

        public void close() {
            super.close();
            this.projection.close();
        }

        protected void renderToTexture(Picture state, PoseStack unused) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player != null && KnifeStudio.design != null) {
                RenderSystem.setProjectionMatrix(
                        this.projection.getBuffer(state.x1() - state.x0(), state.y1() - state.y0(), 70.0F),
                        ProjectionType.PERSPECTIVE);
                mc.gameRenderer.getLighting().setupFor(Entry.LEVEL);
                boolean knifeInMain = KnifeStudio.knifeRight == (player.getMainArm() == HumanoidArm.RIGHT);
                boolean gun = KnifeStudio.design.getSlot() == MeleeSlot.BOW;
                ItemStack held = gun ? KnifeStudio.GUN : KnifeStudio.KNIFE;
                ItemStack other = KnifeStudio.design.getSlot().twoHanded() ? ItemStack.EMPTY : KnifeStudio.other;
                ItemStack main = knifeInMain ? held : other;
                ItemStack off = knifeInMain ? other : held;
                ItemInHandRenderer hands = mc.gameRenderer.itemInHandRenderer;
                ItemInHandRendererAccess access = (ItemInHandRendererAccess) hands;
                ItemStack keptMain = access.arcanicvisuals$mainHandItem();
                ItemStack keptOff = access.arcanicvisuals$offHandItem();
                float mh = access.arcanicvisuals$mainHandHeight();
                float omh = access.arcanicvisuals$oMainHandHeight();
                float oh = access.arcanicvisuals$offHandHeight();
                float ooh = access.arcanicvisuals$oOffHandHeight();
                access.arcanicvisuals$setMainHandItem(main);
                access.arcanicvisuals$setOffHandItem(off);
                access.arcanicvisuals$setMainHandHeight(1.0F);
                access.arcanicvisuals$setOMainHandHeight(1.0F);
                access.arcanicvisuals$setOffHandHeight(1.0F);
                access.arcanicvisuals$setOOffHandHeight(1.0F);
                MeleeSkins.beginStudio(KnifeStudio.design, main, off);

                try {
                    hands.renderHandsWithItems(
                            1.0F,
                            new PoseStack(),
                            mc.gameRenderer.getFeatureRenderDispatcher().getSubmitNodeStorage(),
                            player,
                            15728880);
                } finally {
                    MeleeSkins.endStudio();
                    access.arcanicvisuals$setMainHandItem(keptMain);
                    access.arcanicvisuals$setOffHandItem(keptOff);
                    access.arcanicvisuals$setMainHandHeight(mh);
                    access.arcanicvisuals$setOMainHandHeight(omh);
                    access.arcanicvisuals$setOffHandHeight(oh);
                    access.arcanicvisuals$setOOffHandHeight(ooh);
                }
            }
        }
    }
}
