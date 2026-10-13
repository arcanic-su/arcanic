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

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.fx.PaintRender;
import net.arcanic.visuals.mixin.ModelPartAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Draws the melee skin: an animated rig in first person, a static model in third person. */
public final class MeleeRenderer {
    private static final MeleeRenderer INSTANCE = new MeleeRenderer();
    private static final float MODEL_SCALE = 16.0F;
    private static final float MODEL_FLIP_DEGREES = 180.0F;
    private static final float DEGREES_TO_RADIANS = (float) (Math.PI / 180.0);
    private static final float ARM_ALIGN_X = 0.0625F;
    private static final float ARM_ALIGN_Y = 0.5F;
    private static final String THIRD_PERSON_ANCHOR = "thirdperson_hand";
    private static final float EQUIP_DROP = -0.6F;
    private static final float MIRROR_SIGN = -1.0F;
    private static final int FACE_STRIDE = 32;

    private final MeleeBuffer primaryBones = new MeleeBuffer();
    private final MeleeBuffer secondaryBones = new MeleeBuffer();
    private boolean[] holy = new boolean[0];
    private String holyKey;
    private boolean holyPresent;
    private boolean primaryClaimed;
    private boolean handMirrored;
    private MeleeSkeleton handModel;
    private ItemStack handItem;
    private PoseStack.Pose handBase;
    private MeleeAnimator handAnimator;
    private final MeleeBuffer idleBones = new MeleeBuffer();
    private MeleeSkeleton gripModel;
    private MeleeClip gripClip;
    private final Quaternionf gripRotation = new Quaternionf();
    private final Quaternionf mirroredGripRotation = new Quaternionf();
    private float handItemEquipProgress;
    private final Matrix4f local = new Matrix4f();
    private final Matrix4f armPose = new Matrix4f();
    private final Matrix3f boneNormal = new Matrix3f();
    private final Quaternionf rotation = new Quaternionf();
    private final Vector3f normal = new Vector3f();

    private MeleeRenderer() {}

    public static MeleeRenderer getInstance() {
        return INSTANCE;
    }

    public void beginHands() {
        handModel = null;
        handBase = null;
        handAnimator = null;
        handItem = null;
        primaryClaimed = false;
    }

    public boolean deferHandItem(ItemStack item, float equipProgress) {
        MeleeDesign design = MeleeCatalog.equipped();
        if (design == null || MeleeModels.get(design) == null) return false;
        handItem = item;
        handItemEquipProgress = equipProgress;
        return true;
    }

    /** Draws the deferred item at the end of the skin's arm, following the animated wrist. */
    public void flushHandItem(AbstractClientPlayer player, PoseStack base, SubmitNodeCollector collector, int light) {
        ItemStack item = handItem;
        handItem = null;
        if (item == null || handModel == null || handBase == null || handAnimator == null) return;
        int bone = handModel.getLeftHandBone();
        if (bone < 0 || bone >= primaryBones.world.length) return;
        AvatarRenderer renderer =
                Minecraft.getInstance().getEntityRenderDispatcher().getPlayerRenderer(player);
        if (renderer == null) return;
        var playerModel = (PlayerModel) renderer.getModel();
        var arm = handMirrored ? playerModel.rightArm : playerModel.leftArm;

        // The arm's own cubes give the real wrist position for slim, wide and mirrored skins.
        var cubes = ((ModelPartAccess) (Object) arm).arcanicvisuals$cubes();
        if (cubes.isEmpty()) return;
        var cube = cubes.getFirst();
        PoseStack pose = new PoseStack();
        pose.last().set(handBase);
        pose.translate(0, handItemEquipProgress * EQUIP_DROP, 0);
        pose.mulPose(armPose.set(primaryBones.world[bone]).translate(ARM_ALIGN_X, ARM_ALIGN_Y, 0));
        if (handMirrored) pose.scale(-1, 1, 1);
        arm.translateAndRotate(pose);
        pose.translate((cube.minX + cube.maxX) / 32f, cube.maxY / 16f, (cube.minZ + cube.maxZ) / 32f);
        prepareGripRotation(handModel, handAnimator, playerModel);
        pose.mulPose(handMirrored ? mirroredGripRotation : gripRotation);

        ItemDisplayContext context =
                handMirrored ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        ItemDisplayContext viewContext =
                handMirrored ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        // This remains a first-person submission for the weapon-colour module.
        boolean painting = PaintRender.firstPerson(player, item, viewContext);
        if (painting) PaintRender.pendingItem++;
        try {
            Minecraft.getInstance()
                    .gameRenderer
                    .itemInHandRenderer
                    .renderItem(player, item, context, pose, collector, light);
        } finally {
            if (painting) PaintRender.pendingItem--;
        }
    }

    private void prepareGripRotation(MeleeSkeleton model, MeleeAnimator animator, PlayerModel skin) {
        MeleeClip clip = animator.referenceClip();
        // Arm dimensions change with the skin, but its resting rotation is fixed.
        if (gripModel == model && gripClip == clip) return;
        gripModel = model;
        gripClip = clip;
        float[] idle = new float[model.boneCount() * MeleeClip.BONE_STRIDE];
        for (int i = MeleeClip.SCALE_OFFSET; i < idle.length; i += MeleeClip.BONE_STRIDE)
            idle[i] = idle[i + 1] = idle[i + 2] = 1;
        if (clip != null) for (MeleeBoneTrack track : clip.getTracks()) track.sample(0, idle);
        buildWorld(idleBones, model, idle, new boolean[model.boneCount()]);
        int bone = model.getLeftHandBone();
        for (boolean mirrored : new boolean[] {false, true}) {
            PoseStack rest = new PoseStack();
            if (mirrored) rest.scale(-1, 1, 1);
            applyCamera(model, idle, rest);
            applyViewAnchor(model, rest);
            rest.mulPose(new Matrix4f(idleBones.world[bone]).translate(ARM_ALIGN_X, ARM_ALIGN_Y, 0));
            if (mirrored) rest.scale(-1, 1, 1);
            (mirrored ? skin.rightArm : skin.leftArm).translateAndRotate(rest);
            rest.last()
                    .pose()
                    .getUnnormalizedRotation(mirrored ? mirroredGripRotation : gripRotation)
                    .normalize()
                    .invert();
        }
    }

    /** Draws the skin in one hand in first person. */
    public boolean renderFirstPerson(
            AbstractClientPlayer player,
            InteractionHand hand,
            PoseStack pose,
            SubmitNodeCollector collector,
            int light,
            float equipProgress) {
        MeleeDesign design = MeleeCatalog.equipped();
        if (design == null) return false;
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                ? player.getMainArm()
                : player.getMainArm().getOpposite();
        boolean mirrored = arm == HumanoidArm.LEFT;
        boolean primary = !primaryClaimed;
        MeleeAnimator animator = primary ? MeleeAnimator.getInstance() : MeleeAnimator.offHand();
        MeleeSkeleton model = animator.prepare(player, hand);
        if (model == null) return false;
        MeleeBuffer bones = primary ? primaryBones : secondaryBones;

        pose.pushPose();
        pose.translate(0.0F, equipProgress * EQUIP_DROP, 0.0F);
        if (mirrored) pose.scale(MIRROR_SIGN, 1.0F, 1.0F);
        applyCamera(model, animator, pose);
        applyViewAnchor(model, pose);
        buildWorld(bones, model, animator.pose(), animator.hidden());
        if (primary) {
            handBase = pose.last().copy();
            handAnimator = animator;
        }
        submitGeometry(bones, model, design, pose, collector, light, mirrored);
        if (primary) {
            submitArm(bones, model, model.getRightHandBone(), true, mirrored, player, pose, collector, light);
            submitArm(bones, model, model.getLeftHandBone(), false, mirrored, player, pose, collector, light);
        }
        pose.popPose();

        if (primary) {
            primaryClaimed = true;
            handMirrored = mirrored;
            handModel = model;
        }
        return true;
    }

    public boolean renderThirdPerson(
            AbstractClientPlayer player, HumanoidArm arm, PoseStack pose, SubmitNodeCollector collector, int light) {
        MeleeDesign design = MeleeCatalog.equipped();
        MeleeSkeleton model = MeleeModels.get(design);
        if (design == null || model == null) return false;
        MeleeTransform transform =
                arm == HumanoidArm.RIGHT ? design.getThirdPersonRightHand() : design.getThirdPersonLeftHand();
        pose.pushPose();
        pose.translate((arm == HumanoidArm.LEFT ? -1.0F : 1.0F) / 16.0F, 0.125F, -0.625F);
        pose.translate(
                transform.translation()[0] / MODEL_SCALE,
                transform.translation()[1] / MODEL_SCALE,
                transform.translation()[2] / MODEL_SCALE);
        pose.mulPose(Axis.XP.rotationDegrees(transform.rotation()[0]));
        pose.mulPose(Axis.YP.rotationDegrees(transform.rotation()[1]));
        pose.mulPose(Axis.ZP.rotationDegrees(transform.rotation()[2]));
        pose.scale(transform.scale()[0], transform.scale()[1], transform.scale()[2]);
        float arcanicSize = Modules.KNIVES.thirdScale.f(); // bigger knife, grip stays in the hand
        pose.scale(arcanicSize, arcanicSize, arcanicSize);
        pose.mulPose(Axis.ZP.rotationDegrees(MODEL_FLIP_DEGREES));
        Integer anchor = model.getBoneIndex().get(THIRD_PERSON_ANCHOR);
        if (anchor != null) {
            pose.translate(
                    -model.pivotX(anchor) / MODEL_SCALE,
                    -model.pivotY(anchor) / MODEL_SCALE,
                    -model.pivotZ(anchor) / MODEL_SCALE);
        }
        buildRest(secondaryBones, model);
        submitGeometry(secondaryBones, model, design, pose, collector, light, false);
        pose.popPose();
        return true;
    }

    private void applyCamera(MeleeSkeleton model, MeleeAnimator animator, PoseStack pose) {
        applyCamera(model, animator.pose(), pose);
    }

    private void applyCamera(MeleeSkeleton model, float[] channels, PoseStack pose) {
        int bone = model.getCameraBone();
        if (bone < 0) return;
        int i = bone * MeleeClip.BONE_STRIDE;
        if (i + 5 >= channels.length) return;
        pose.translate(channels[i + 3] / 16f, -channels[i + 4] / 16f, -channels[i + 5] / 16f);
        pose.mulPose(Axis.ZP.rotationDegrees(-channels[i + 2]));
        pose.mulPose(Axis.YP.rotationDegrees(channels[i + 1]));
        pose.mulPose(Axis.XP.rotationDegrees(channels[i]));
    }

    private void applyViewAnchor(MeleeSkeleton model, PoseStack pose) {
        int bone = model.getViewAnchorBone();
        if (bone < 0) return;
        pose.translate(
                -model.pivotX(bone) / MODEL_SCALE,
                -model.pivotY(bone) / MODEL_SCALE,
                -model.pivotZ(bone) / MODEL_SCALE);
    }

    private void buildRest(MeleeBuffer bones, MeleeSkeleton model) {
        int count = model.boneCount();
        bones.ensureCapacity(count);
        for (int i = 0; i < count; i++) {
            bones.visible[i] = true;
            local.identity();
            applyPivotRotation(
                    model, i, model.restRotationX(i), model.restRotationY(i), model.restRotationZ(i), 1.0F, 1.0F, 1.0F);
            compose(bones, model, i);
        }
        hideHands(bones, model);
    }

    private void buildWorld(MeleeBuffer bones, MeleeSkeleton model, float[] channels, boolean[] hidden) {
        int count = model.boneCount();
        bones.ensureCapacity(count);
        for (int i = 0; i < count; i++) {
            bones.visible[i] = !(i < hidden.length && hidden[i]);
            int c = i * MeleeClip.BONE_STRIDE;
            local.identity();
            local.translate(
                    -channels[c + 3] / MODEL_SCALE, channels[c + 4] / MODEL_SCALE, channels[c + 5] / MODEL_SCALE);
            applyPivotRotation(
                    model,
                    i,
                    model.restRotationX(i) - channels[c] * DEGREES_TO_RADIANS,
                    model.restRotationY(i) - channels[c + 1] * DEGREES_TO_RADIANS,
                    model.restRotationZ(i) + channels[c + 2] * DEGREES_TO_RADIANS,
                    channels[c + 6],
                    channels[c + 7],
                    channels[c + 8]);
            compose(bones, model, i);
        }
        hideHands(bones, model);
    }

    private void applyPivotRotation(
            MeleeSkeleton model,
            int bone,
            float rotX,
            float rotY,
            float rotZ,
            float scaleX,
            float scaleY,
            float scaleZ) {
        float px = model.pivotX(bone) / MODEL_SCALE;
        float py = model.pivotY(bone) / MODEL_SCALE;
        float pz = model.pivotZ(bone) / MODEL_SCALE;
        local.translate(px, py, pz);
        rotation.rotationZ(rotZ).rotateY(rotY).rotateX(rotX);
        local.rotate(rotation);
        local.scale(scaleX, scaleY, scaleZ);
        local.translate(-px, -py, -pz);
    }

    private void compose(MeleeBuffer bones, MeleeSkeleton model, int bone) {
        int parent = model.parentOf(bone);
        if (parent < 0) bones.world[bone].set(local);
        else bones.world[bone].set(bones.world[parent]).mul(local);
    }

    private void hideHands(MeleeBuffer bones, MeleeSkeleton model) {
        if (model.getRightHandBone() >= 0) bones.visible[model.getRightHandBone()] = false;
        if (model.getLeftHandBone() >= 0) bones.visible[model.getLeftHandBone()] = false;
    }

    private void submitGeometry(
            MeleeBuffer bones,
            MeleeSkeleton model,
            MeleeDesign design,
            PoseStack pose,
            SubmitNodeCollector collector,
            int light,
            boolean mirrored) {
        prepareHolyBones(model, design);
        Identifier texture = design.getTexture();
        submitPass(bones, model, RenderTypes.entityTranslucent(texture), pose, collector, light, false, mirrored);
        if (holyPresent)
            submitPass(
                    bones,
                    model,
                    RenderTypes.entityTranslucentEmissive(texture),
                    pose,
                    collector,
                    light,
                    true,
                    mirrored);
    }

    private void prepareHolyBones(MeleeSkeleton model, MeleeDesign design) {
        int count = model.boneCount();
        if (design.getKey().equals(holyKey) && holy.length == count) return;
        holyKey = design.getKey();
        holy = new boolean[count];
        holyPresent = model.markBranches(design.getHolyBones(), holy);
    }

    private void submitPass(
            MeleeBuffer bones,
            MeleeSkeleton model,
            RenderType type,
            PoseStack pose,
            SubmitNodeCollector collector,
            int light,
            boolean emissive,
            boolean mirrored) {
        int count = model.boneCount();
        collector.submitCustomGeometry(pose, type, (last, consumer) -> {
            for (int bone = 0; bone < count; bone++) {
                float[] mesh = model.meshOf(bone);
                if (mesh == null || !bones.visible[bone] || holy[bone] != emissive) continue;
                Matrix4f matrix = bones.combined[bone].set(last.pose()).mul(bones.world[bone]);
                Matrix3f normals = bones.normals[bone].set(last.normal()).mul(boneNormal.set(bones.world[bone]));
                if (mirrored) {
                    for (int face = 0; face < mesh.length; face += FACE_STRIDE) {
                        for (int corner = 3; corner >= 0; corner--) {
                            submitVertex(
                                    consumer,
                                    matrix,
                                    normals,
                                    mesh,
                                    face + corner * MeleeSkeleton.VERTEX_STRIDE,
                                    light);
                        }
                    }
                } else {
                    for (int at = 0; at < mesh.length; at += MeleeSkeleton.VERTEX_STRIDE) {
                        submitVertex(consumer, matrix, normals, mesh, at, light);
                    }
                }
            }
        });
    }

    private void submitVertex(
            VertexConsumer consumer, Matrix4f matrix, Matrix3f normals, float[] mesh, int at, int light) {
        normal.set(mesh[at + 5], mesh[at + 6], mesh[at + 7]).mul(normals).normalize();
        consumer.addVertex(matrix, mesh[at], mesh[at + 1], mesh[at + 2])
                .setColor(1.0F, 1.0F, 1.0F, 1.0F)
                .setUv(mesh[at + 3], mesh[at + 4])
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(normal.x, normal.y, normal.z);
    }

    private void submitArm(
            MeleeBuffer bones,
            MeleeSkeleton model,
            int bone,
            boolean right,
            boolean mirrored,
            AbstractClientPlayer player,
            PoseStack pose,
            SubmitNodeCollector collector,
            int light) {
        if (bone < 0) return;
        AvatarRenderer renderer =
                Minecraft.getInstance().getEntityRenderDispatcher().getPlayerRenderer(player);
        if (renderer == null) return;
        Identifier skin = player.getSkin().body().texturePath();
        boolean drawRight = right != mirrored; // a mirrored rig swaps which arm goes where
        armPose.set(bones.world[bone]).translate(right ? -ARM_ALIGN_X : ARM_ALIGN_X, ARM_ALIGN_Y, 0.0F);
        pose.pushPose();
        pose.mulPose(armPose);
        if (mirrored) pose.scale(MIRROR_SIGN, 1.0F, 1.0F);
        if (drawRight)
            renderer.renderRightHand(
                    pose, collector, light, skin, player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
        else
            renderer.renderLeftHand(pose, collector, light, skin, player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
        pose.popPose();
    }
}
