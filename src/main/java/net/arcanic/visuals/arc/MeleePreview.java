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

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.arcanic.visuals.mixin.GuiGraphicsAccess;
import net.arcanic.visuals.mixin.RenderPipelinesAccess;
import net.arcanic.visuals.render.Px;
import net.arcanic.visuals.render.TexWarm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

/** Draws the projected, depth-sorted card geometry with vanilla GUI submission. */
public final class MeleePreview {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(
                    RenderPipelinesAccess.arcanicvisuals$guiTextureSnippet())
            .withLocation(Identifier.fromNamespaceAndPath("arcanicvisuals", "pipeline/melee_preview"))
            .withCull(false)
            .build();

    public static void draw(
            GuiGraphics g, MeleeDesign skin, int x, int y, int w, int h, float hover, int clipTop, int clipBottom) {
        MeleeCardMesh model = MeleeCardBuilder.get(skin);
        if (model == null || model.width() == 0 || model.height() == 0) return;
        if (!TexWarm.ready(skin.getTexture())) return; // decoded off the render thread, shows a moment later
        var tex = Minecraft.getInstance().getTextureManager().getTexture(skin.getTexture());
        float scale = Math.min(w / model.width(), h / model.height()) * .84f * (1 + .06f * hover);
        var matrix = new Matrix3x2f(g.pose());
        var bounds = new ScreenRectangle(x, y, w, h).transformMaxBounds(matrix);
        var clip = new ScreenRectangle(x, clipTop, w, Math.max(0, clipBottom - clipTop)).transformMaxBounds(matrix);
        bounds = bounds.intersection(clip);
        if (bounds == null || bounds.height() == 0) return;
        ((GuiGraphicsAccess) g)
                .arcanicvisuals$state()
                .submitGuiElement(new State(
                        matrix,
                        model.quads(),
                        x + w / 2f,
                        y + h / 2f,
                        scale,
                        Px.am(0xFFFFFFFF, Px.fade),
                        TextureSetup.singleTexture(tex.getTextureView(), tex.getSampler()),
                        bounds));
    }

    private record State(
            Matrix3x2f pose,
            float[] quads,
            float cx,
            float cy,
            float scale,
            int colour,
            TextureSetup textureSetup,
            ScreenRectangle bounds)
            implements GuiElementRenderState {
        public RenderPipeline pipeline() {
            return PIPELINE;
        }

        public ScreenRectangle scissorArea() {
            return bounds;
        }

        public void buildVertices(VertexConsumer vc) {
            for (int i = 0; i < quads.length; i += 4)
                vc.addVertexWith2DPose(pose, cx + quads[i] * scale, cy + quads[i + 1] * scale)
                        .setUv(quads[i + 2], quads[i + 3])
                        .setColor(colour);
        }
    }
}
