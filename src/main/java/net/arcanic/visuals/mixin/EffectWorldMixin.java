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
package net.arcanic.visuals.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.arcanic.visuals.fx.impact.ImpactDraw;
import net.arcanic.visuals.fx.impact.ImpactEffects;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LevelRenderer.class})
public abstract class EffectWorldMixin {
    @Inject(
            method = {"renderLevel"},
            at = {@At("HEAD")})
    private void arcanicvisuals$effectsProjection(
            GraphicsResourceAllocator allocator,
            DeltaTracker delta,
            boolean outline,
            Camera camera,
            Matrix4f modelView,
            Matrix4f projection,
            Matrix4f cullProjection,
            GpuBufferSlice fog,
            Vector4f fogColour,
            boolean sky,
            CallbackInfo ci) {
        ImpactDraw.PROJECTION.set(projection);
    }

    @Inject(
            method = {"renderLevel"},
            at = {@At("RETURN")})
    private void arcanicvisuals$effectsWorld(
            GraphicsResourceAllocator allocator,
            DeltaTracker delta,
            boolean outline,
            Camera camera,
            Matrix4f modelView,
            Matrix4f projection,
            Matrix4f cullProjection,
            GpuBufferSlice fog,
            Vector4f fogColour,
            boolean sky,
            CallbackInfo ci) {
        PoseStack ps = new PoseStack();
        ps.last().pose().set(modelView);
        ImpactEffects.world(ps, delta.getGameTimeDeltaPartialTick(true));
    }
}
