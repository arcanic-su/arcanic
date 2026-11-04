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
import net.minecraft.resources.Identifier;

/**
 * One melee skin: what it replaces, its model and animation files, texture, slot icon, variants and third person.
 * A Premium skin's texture comes from the Arcanic API, not from the mod (see {@link PremiumSkins}).
 */
public final class MeleeDesign {
    private static final String MODEL_PATH = "/assets/arcanic/cosmetic/melee/%s.geo.json";
    private static final String ANIMATION_PATH = "/assets/arcanic/cosmetic/melee/%s.anim.json";
    private static final String TEXTURE_PATH = "textures/cosmetic/melee/%s.png";
    private static final String ICON_PATH = "textures/cosmetic/melee/slot/%s.png";
    private static final String NAMESPACE = "arcanic";
    private static final String ID_PREFIX = "melee_";
    private final String key;
    private final String geometryKey;
    private final String displayName;
    private final MeleeSlot slot;
    private final boolean premium;
    private final List<MeleeVariant> variants;
    private final List<String> holyBones;
    private final MeleeTransform thirdPersonRightHand;
    private final MeleeTransform thirdPersonLeftHand;
    private final Identifier texture;
    private final Identifier icon;
    private final int id;

    MeleeDesign(
            String key,
            String geometryKey,
            String displayName,
            MeleeSlot slot,
            boolean premium,
            List<MeleeVariant> variants,
            List<String> holyBones,
            MeleeTransform thirdPersonRightHand,
            MeleeTransform thirdPersonLeftHand) {
        this.key = key;
        this.geometryKey = geometryKey != null && !geometryKey.isEmpty() ? geometryKey : key;
        this.displayName = displayName;
        this.slot = slot;
        this.premium = premium;
        this.variants = variants;
        this.holyBones = holyBones;
        this.thirdPersonRightHand = thirdPersonRightHand;
        this.thirdPersonLeftHand = thirdPersonLeftHand;
        this.texture = Identifier.fromNamespaceAndPath(NAMESPACE, String.format(TEXTURE_PATH, key));
        this.icon = Identifier.fromNamespaceAndPath(NAMESPACE, String.format(ICON_PATH, key));
        this.id = (ID_PREFIX + key).hashCode();
    }

    public String getKey() {
        return key;
    }

    public String getGeometryKey() {
        return geometryKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public MeleeSlot getSlot() {
        return slot;
    }

    public boolean isPremium() {
        return premium;
    }

    public List<MeleeVariant> getVariants() {
        return variants;
    }

    public List<String> getHolyBones() {
        return holyBones;
    }

    public MeleeTransform getThirdPersonRightHand() {
        return thirdPersonRightHand;
    }

    public MeleeTransform getThirdPersonLeftHand() {
        return thirdPersonLeftHand;
    }

    public Identifier getTexture() {
        return texture;
    }

    public Identifier getIcon() {
        return icon;
    }

    public int getId() {
        return id;
    }

    public String modelResource() {
        return String.format(MODEL_PATH, geometryKey);
    }

    public String animationResource() {
        return String.format(ANIMATION_PATH, geometryKey);
    }
}
