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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.arcanic.visuals.core.Modules;
import net.arcanic.visuals.mixin.ModelPartAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
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
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

public final class MeleeRenderer {
    private static final MeleeRenderer INSTANCE = new MeleeRenderer();
    private static final float MODEL_SCALE = 16.0F;
    private static final float DEGREES_TO_RADIANS = ((float) Math.PI / 180F);
    private static final float ARM_ALIGN_X = 0.0625F;
    private static final float ARM_ALIGN_Y = 0.5F;
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
    private MeleeSkeleton sidesModel;
    private byte[] sides;
    private static final float FIST_X = 0.0625F;
    private static final float FIST_Y = 0.6F;
    private static final float FIST_Z = -0.1F;
    private static final float DEFAULT_THIRD_SCALE = 1.6F;
    private static final float HELD_SIZE = 1.1F;
    private static final float TYPICAL_LENGTH = 0.95F;
    private static final float MIN_EVENING = 0.8F;
    private static final float MAX_EVENING = 1.25F;
    private static final Vector3f BLADE_IN_HAND = (new Vector3f(0.0F, -0.62F, -1.0F)).normalize();
    private static final Vector3f FLAT_IN_HAND = new Vector3f(1.0F, 0.0F, 0.0F);
    private static final String IDLE_CLIP = "static_idle";
    private static final String KNIFE_ROOT = "knife_and_right";
    private static final String KNIFE_ROOT_LEFT = "knife_and_left";
    private static final float ARM_PIVOT_X = -0.3125F;
    private static final float ARM_PIVOT_Y = 0.125F;
    private static final float ARM_ROLL = 0.1F;
    private static final Vector3f FIST_IN_ARM = new Vector3f(-0.0625F, 0.625F, 0.0F);
    private static final float AIM_REACH = 0.4F;
    private static final float AIM_RAISE_DEGREES = 2.0F;
    private static final float SWAY_PITCH_DEGREES = 0.3F;
    private static final float SWAY_YAW_DEGREES = 0.45F;
    private static final float SWAY_PITCH_RATE = 2.6F;
    private static final float SWAY_YAW_RATE = 1.3F;
    private static final float GUN_THIRD_SIZE = 0.27F;
    private static final float GUN_HIP_PITCH = 0.12F;
    private final Map<String, Hold> holds = new HashMap<>();
    private final Map<String, Rest> rests = new HashMap<>();

    private MeleeRenderer() {}

    public static MeleeRenderer getInstance() {
        return INSTANCE;
    }

    public void beginHands() {
        this.handModel = null;
        this.handBase = null;
        this.handAnimator = null;
        this.handItem = null;
        this.primaryClaimed = false;
    }

    public boolean deferHandItem(ItemStack item, float equipProgress) {
        MeleeDesign design = MeleeCatalog.equipped(MeleeSlot.KNIFE);
        if (design != null && MeleeModels.get(design) != null) {
            this.handItem = item;
            this.handItemEquipProgress = equipProgress;
            return true;
        } else {
            return false;
        }
    }

    public void flushHandItem(AbstractClientPlayer player, PoseStack base, SubmitNodeCollector collector, int light) {
        ItemStack item = this.handItem;
        this.handItem = null;
        if (item != null && this.handModel != null && this.handBase != null && this.handAnimator != null) {
            int bone = this.handModel.getLeftHandBone();
            if (bone >= 0 && bone < this.primaryBones.world.length) {
                AvatarRenderer renderer =
                        Minecraft.getInstance().getEntityRenderDispatcher().getPlayerRenderer(player);
                if (renderer != null) {
                    PlayerModel playerModel = (PlayerModel) renderer.getModel();
                    ModelPart arm = this.handMirrored ? playerModel.rightArm : playerModel.leftArm;
                    List<ModelPart.Cube> cubes = ((ModelPartAccess) (Object) arm).arcanicvisuals$cubes();
                    if (!cubes.isEmpty()) {
                        ModelPart.Cube cube = (ModelPart.Cube) cubes.getFirst();
                        PoseStack pose = new PoseStack();
                        pose.last().set(this.handBase);
                        pose.translate(0.0F, this.handItemEquipProgress * -0.6F, 0.0F);
                        pose.mulPose((Matrix4fc)
                                armAnchor(this.armPose, this.handModel, this.primaryBones.world[bone], bone, false));
                        if (this.handMirrored) {
                            pose.scale(-1.0F, 1.0F, 1.0F);
                        }

                        arm.translateAndRotate(pose);
                        pose.translate(
                                (cube.minX + cube.maxX) / 32.0F, cube.maxY / 16.0F, (cube.minZ + cube.maxZ) / 32.0F);
                        this.prepareGripRotation(this.handModel, this.handAnimator, playerModel);
                        pose.mulPose(
                                (Quaternionfc) (this.handMirrored ? this.mirroredGripRotation : this.gripRotation));
                        float size = Modules.KNIVES.inRigHand(item);
                        pose.scale(size, size, size);
                        ItemDisplayContext context = this.handMirrored
                                ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                                : ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
                        ItemDisplayContext viewContext = this.handMirrored
                                ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                                : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
                        Minecraft.getInstance()
                                .gameRenderer
                                .itemInHandRenderer
                                .renderItem(player, item, context, pose, collector, light);
                    }
                }
            }
        }
    }

    private void prepareGripRotation(MeleeSkeleton model, MeleeAnimator animator, PlayerModel skin) {
        MeleeClip clip = animator.referenceClip();
        if (this.gripModel != model || this.gripClip != clip) {
            this.gripModel = model;
            this.gripClip = clip;
            float[] idle = new float[model.boneCount() * 9];

            for (int i = 6; i < idle.length; i += 9) {
                idle[i] = idle[i + 1] = idle[i + 2] = 1.0F;
            }

            if (clip != null) {
                for (MeleeBoneTrack track : clip.getTracks()) {
                    track.sample(0.0F, idle);
                }
            }

            this.buildWorld(this.idleBones, model, idle, new boolean[model.boneCount()]);
            int bone = model.getLeftHandBone();

            for (boolean mirrored : new boolean[] {false, true}) {
                PoseStack rest = new PoseStack();
                if (mirrored) {
                    rest.scale(-1.0F, 1.0F, 1.0F);
                }

                this.applyCamera(model, idle, rest);
                this.applyViewAnchor(model, rest, 0.0F);
                rest.mulPose((Matrix4fc) armAnchor(new Matrix4f(), model, this.idleBones.world[bone], bone, false));
                if (mirrored) {
                    rest.scale(-1.0F, 1.0F, 1.0F);
                }

                (mirrored ? skin.rightArm : skin.leftArm).translateAndRotate(rest);
                rest.last()
                        .pose()
                        .getUnnormalizedRotation(mirrored ? this.mirroredGripRotation : this.gripRotation)
                        .normalize()
                        .invert();
            }
        }
    }

    public boolean renderFirstPerson(
            AbstractClientPlayer player,
            InteractionHand hand,
            PoseStack pose,
            SubmitNodeCollector collector,
            int light,
            float equipProgress) {
        MeleeDesign design = MeleeCatalog.equipped(MeleeSkins.stack(player, hand));
        if (design == null) {
            return false;
        } else {
            MeleeSkins skins = MeleeSkins.of(design);
            HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                    ? player.getMainArm()
                    : player.getMainArm().getOpposite();
            boolean mirrored = arm == HumanoidArm.LEFT;
            boolean primary = !this.primaryClaimed;
            MeleeAnimator animator = MeleeAnimator.forHand(primary);
            MeleeSkeleton model = animator.prepare(player, hand);
            if (model == null) {
                return false;
            } else {
                MeleeBuffer bones = primary ? this.primaryBones : this.secondaryBones;
                pose.pushPose();
                pose.translate(0.0F, equipProgress * -0.6F, 0.0F);
                if (mirrored) {
                    pose.scale(-1.0F, 1.0F, 1.0F);
                }

                this.applyCamera(model, animator, pose);
                float aim = animator.aim();
                this.applyAim(animator, aim, pose);
                this.applyViewAnchor(model, pose, aim);
                this.buildWorld(bones, model, animator.pose(), animator.hidden());
                this.placeHands(bones, model, design, skins, player, hand, primary);
                if (primary) {
                    this.handBase = pose.last().copy();
                    this.handAnimator = animator;
                }

                this.submitGeometry(bones, model, design, pose, collector, light, mirrored);
                this.submitFlash(bones, model, pose, collector, animator.flash(), animator.flashSpin());
                if (primary) {
                    this.submitArm(
                            bones, model, model.getRightHandBone(), true, mirrored, player, pose, collector, light);
                    if (!skins.leavesOtherHand(player, hand)) {
                        this.submitArm(
                                bones, model, model.getLeftHandBone(), false, mirrored, player, pose, collector, light);
                    }
                }

                pose.popPose();
                if (primary) {
                    this.primaryClaimed = true;
                    this.handMirrored = mirrored;
                    this.handModel = model;
                }

                return true;
            }
        }
    }

    private void placeHands(
            MeleeBuffer bones,
            MeleeSkeleton model,
            MeleeDesign design,
            MeleeSkins skins,
            AbstractClientPlayer player,
            InteractionHand hand,
            boolean primary) {
        MeleeSkins.Placement knife = skins.placement(MeleeSkins.knifeKey(design));
        MeleeSkins.Placement other = primary ? skins.rigOtherHand(player, hand) : null;
        // the hand without the skin, set for this skin: on top of everything else
        MeleeSkins.Placement free =
                primary ? skins.placement(MeleeSkins.otherHandKey(design)) : MeleeSkins.Placement.NONE;
        boolean freeMoved = free.moved() || free.size() != 1.0F;
        if (knife.moved() || knife.size() != 1.0F || other != null && other.moved() || freeMoved) {
            byte[] sides = this.knifeSides(model);
            Matrix4f[] at = new Matrix4f[3];

            for (int side = 1; side <= 2; side++) {
                boolean right = side == 1;
                MeleeSkins.Placement p = !right && other != null ? other : knife;
                Matrix4f move = (new Matrix4f()).translation((float) (right ? 1 : -1) * p.x(), p.y(), -p.z());
                int handBone = right ? model.getRightHandBone() : model.getLeftHandBone();
                if (handBone < 0) {
                    at[side] = move;
                } else {
                    Vector3f f = fist(model, bones, handBone, right, new Vector3f());
                    at[side] = (new Matrix4f(move))
                            .translate(f)
                            .scale(knife.size())
                            .translate(-f.x, -f.y, -f.z);
                    if (!right && design.getSlot().twoHanded()) {
                        // the support hand stays on the gun, wherever the gun's place and size take its grip
                        Vector3f to = at[1].transformPosition(new Vector3f(f)).sub(f);
                        bones.world[handBone].set(
                                (new Matrix4f()).translation(to).mul(bones.world[handBone]));
                    } else {
                        bones.world[handBone].set((new Matrix4f(move)).mul(bones.world[handBone]));
                    }
                }
                if (!right && freeMoved) {
                    Vector3f g = handBone < 0 ? new Vector3f() : fist(model, bones, handBone, false, new Vector3f());
                    Matrix4f extra = new Matrix4f()
                            .translation(-free.x(), free.y(), -free.z())
                            .translate(g)
                            .scale(free.size())
                            .translate(-g.x, -g.y, -g.z);
                    at[side] = new Matrix4f(extra).mul(at[side]);
                    if (handBone >= 0) bones.world[handBone].set(new Matrix4f(extra).mul(bones.world[handBone]));
                }
            }

            for (int i = 0; i < sides.length; i++) {
                Matrix4f m = at[sides[i]];
                if (m != null) {
                    bones.world[i].set((new Matrix4f(m)).mul(bones.world[i]));
                }
            }
        }
    }

    private static Vector3f fist(MeleeSkeleton model, MeleeBuffer bones, int handBone, boolean right, Vector3f dest) {
        float side = right ? 1.0F : -1.0F;
        return armAnchor(new Matrix4f(), model, bones.world[handBone], handBone, right)
                .translate(side * -0.3125F, 0.125F, 0.0F)
                .rotateZ(side * 0.1F)
                .transformPosition(side * FIST_IN_ARM.x, FIST_IN_ARM.y, FIST_IN_ARM.z, dest);
    }

    /**
     * Where the player's arm hangs from a hand bone: the bone's own pivot, which a rig puts at the top of its arm
     * stand-in (8 px up in the knife rigs, at the origin in the CS+ gun rigs), a pixel aside to centre the arm.
     */
    private static Matrix4f armAnchor(Matrix4f dest, MeleeSkeleton model, Matrix4f world, int bone, boolean right) {
        return dest.set(world)
                .translate(
                        model.pivotX(bone) / 16.0F + (right ? -0.0625F : 0.0625F),
                        model.pivotY(bone) / 16.0F,
                        model.pivotZ(bone) / 16.0F);
    }

    private byte[] knifeSides(MeleeSkeleton model) {
        if (this.sidesModel == model) {
            return this.sides;
        } else {
            int count = model.boneCount();
            Integer right = (Integer) model.getBoneIndex().get("knife_and_right");
            Integer left = (Integer) model.getBoneIndex().get("knife_and_left");
            byte[] out = new byte[count];

            for (int i = 0; i < count; i++) {
                if (model.meshOf(i) != null) {
                    byte side = (byte) (right == null ? 1 : 0);
                    boolean hand = false;

                    for (int b = i; b >= 0; b = model.parentOf(b)) {
                        String name = model.nameOf(b);
                        hand |= name.startsWith("righthand") || name.startsWith("lefthand");
                        if (right != null && b == right) {
                            side = 1;
                        }

                        if (left != null && b == left) {
                            side = 2;
                        }
                    }

                    out[i] = hand ? 0 : side;
                }
            }

            this.sidesModel = model;
            this.sides = out;
            return out;
        }
    }

    public boolean renderThirdPerson(
            AbstractClientPlayer player,
            ItemStack item,
            AvatarRenderState state,
            PlayerModel skin,
            HumanoidArm arm,
            PoseStack pose,
            SubmitNodeCollector collector,
            int light) {
        MeleeDesign design = MeleeCatalog.equipped(item);
        MeleeSkeleton model = MeleeModels.get(design);
        if (design != null && model != null && design.getSlot() == MeleeSlot.BOW) {
            return this.renderGunThirdPerson(design, model, state, skin, arm, pose, collector, light);
        } else if (design != null && model != null) {
            Hold hold = this.hold(design, model);
            if (hold == null) {
                return false;
            } else {
                boolean left = arm == HumanoidArm.LEFT;
                float evening = (float) Math.sqrt((double) (0.95F / hold.length()));
                float size =
                        MeleeSkins.of(design).thirdScale.f() / 1.6F * 1.1F * Math.max(0.8F, Math.min(1.25F, evening));
                pose.pushPose();
                pose.translate(left ? 0.0625F : -0.0625F, 0.6F, -0.1F);
                if (left) {
                    pose.scale(-1.0F, 1.0F, 1.0F);
                }

                pose.scale(size, size, size);
                pose.mulPose((Matrix4fc) hold.toHand());
                pose.translate(-hold.grip().x, -hold.grip().y, -hold.grip().z);
                this.submitGeometry(hold.bones(), model, design, pose, collector, light, left);
                pose.popPose();
                return true;
            }
        } else {
            return false;
        }
    }

    /**
     * A gun in third person sits with its grip in the fist. Held low it points ahead of the body however the arm
     * swings; with the bow pulled both arms come up to aim and the barrel runs along the arm.
     */
    private boolean renderGunThirdPerson(
            MeleeDesign design,
            MeleeSkeleton model,
            AvatarRenderState state,
            PlayerModel skin,
            HumanoidArm arm,
            PoseStack pose,
            SubmitNodeCollector collector,
            int light) {
        int grip = model.getThirdPersonGripBone();
        if (grip < 0) return false;
        MeleeBuffer bones = this.rest(design, model);
        boolean left = arm == HumanoidArm.LEFT;
        ModelPart part = left ? skin.leftArm : skin.rightArm;
        boolean aiming = (left ? state.leftArmPose : state.rightArmPose) == HumanoidModel.ArmPose.BOW_AND_ARROW;
        float size = GUN_THIRD_SIZE * MeleeSkins.of(design).thirdScale.f();
        pose.pushPose();
        pose.translate(left ? 0.0625F : -0.0625F, 0.6F, -0.1F);
        if (aiming) {
            pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees(90.0F));
        } else {
            pose.mulPose((Quaternionfc) new Quaternionf()
                    .rotationZYX(part.zRot, part.yRot, part.xRot)
                    .conjugate());
            pose.mulPose((Quaternionfc) Axis.XP.rotation(GUN_HIP_PITCH));
        }

        pose.mulPose((Quaternionfc) Axis.ZP.rotationDegrees(180.0F)); // the model's up is the body's -Y
        if (left) {
            pose.scale(-1.0F, 1.0F, 1.0F);
        }

        pose.scale(size, size, size);
        pose.translate(-model.pivotX(grip) / 16.0F, -model.pivotY(grip) / 16.0F, -model.pivotZ(grip) / 16.0F);
        this.submitGeometry(bones, model, design, pose, collector, light, left);
        this.submitFlash(bones, model, pose, collector, MeleeShots.flash(), 0.0F);
        pose.popPose();
        return true;
    }

    /** The gun at rest, in its still pose without the hands, for third person. */
    private MeleeBuffer rest(MeleeDesign design, MeleeSkeleton model) {
        MeleeVariant variant = design.getVariants().isEmpty()
                ? MeleeVariant.DEFAULT
                : (MeleeVariant) design.getVariants().getFirst();
        String key = design.getGeometryKey() + "|" + variant.suffix();
        Rest rest = (Rest) this.rests.get(key);
        if (rest == null || rest.model() != model) {
            rest = new Rest(model, this.restBones(model, variant));
            this.rests.put(key, rest);
        }

        return rest.bones();
    }

    private Hold hold(MeleeDesign design, MeleeSkeleton model) {
        MeleeVariant variant = MeleeAnimator.getInstance().variantOf(model);
        if (variant == null) {
            variant = design.getVariants().isEmpty()
                    ? MeleeVariant.DEFAULT
                    : (MeleeVariant) design.getVariants().getFirst();
        }

        String key = design.getGeometryKey() + "|" + variant.suffix();
        Hold hold = (Hold) this.holds.get(key);
        if (hold == null || hold.model() != model) {
            hold = this.computeHold(model, variant);
            if (hold != null) {
                this.holds.put(key, hold);
            }
        }

        return hold;
    }

    private Hold computeHold(MeleeSkeleton model, MeleeVariant variant) {
        int count = model.boneCount();
        MeleeBuffer bones = this.restBones(model, variant);
        List<Vector3f> points = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            float[] mesh = model.meshOf(i);
            if (bones.visible[i] && mesh != null) {
                for (int at = 0; at < mesh.length; at += 8) {
                    points.add(bones.world[i].transformPosition(mesh[at], mesh[at + 1], mesh[at + 2], new Vector3f()));
                }
            }
        }

        return this.holdOf(model, bones, points);
    }

    /** The skin in its still pose with only the blade (or the gun) left visible: no hands, no second knife. */
    private MeleeBuffer restBones(MeleeSkeleton model, MeleeVariant variant) {
        int count = model.boneCount();
        boolean[] hidden = new boolean[count];
        model.markHidden(variant.hiddenBones(), hidden);
        float[] idle = new float[count * 9];

        for (int i = 6; i < idle.length; i += 9) {
            idle[i] = idle[i + 1] = idle[i + 2] = 1.0F;
        }

        MeleeClip clip = model.clip("static_idle" + variant.suffix());
        if (clip == null) {
            clip = model.clip("static_idle");
        }

        if (clip != null) {
            for (MeleeBoneTrack track : clip.getTracks()) {
                track.sample(0.0F, idle);
            }
        }

        MeleeBuffer bones = new MeleeBuffer();
        this.buildWorld(bones, model, idle, hidden);
        Integer root = (Integer) model.getBoneIndex().get("knife_and_right");

        for (int i = 0; i < count; i++) {
            boolean under = root == null;
            boolean hand = false;

            for (int b = i; b >= 0; b = model.parentOf(b)) {
                String name = model.nameOf(b);
                hand |= name.startsWith("righthand") || name.startsWith("lefthand") || name.equals("knife_and_left");
                under |= root != null && b == root;
            }

            bones.visible[i] &= under && !hand;
        }

        return bones;
    }

    private Hold holdOf(MeleeSkeleton model, MeleeBuffer bones, List<Vector3f> points) {
        if (points.size() < 4) {
            return null;
        } else {
            Vector3f fist = new Vector3f();
            int handBone = model.getRightHandBone();
            if (handBone >= 0) {
                fist(model, bones, handBone, true, fist);
            }

            Vector3f c = new Vector3f();

            for (Vector3f q : points) {
                c.add(q);
            }

            c.div((float) points.size());
            double[][] cov = new double[3][3];

            for (Vector3f q : points) {
                double[] d = {(double) (q.x - c.x), (double) (q.y - c.y), (double) (q.z - c.z)};

                for (int a = 0; a < 3; a++) {
                    for (int b = 0; b < 3; b++) {
                        cov[a][b] += d[a] * d[b];
                    }
                }
            }

            Vector3f[] axes = principalAxes(cov);
            Vector3f u = axes[0];
            Vector3f v = axes[1];
            if ((new Vector3f(fist)).sub(c).dot(u) > 0.0F) {
                u.negate();
            }

            float tmin = Float.MAX_VALUE;
            float tmax = -Float.MAX_VALUE;

            for (Vector3f q : points) {
                float t = (new Vector3f(q)).sub(c).dot(u);
                tmin = Math.min(tmin, t);
                tmax = Math.max(tmax, t);
            }

            float length = tmax - tmin;
            float tf = (new Vector3f(fist)).sub(c).dot(u);
            float tg = Math.max(tmin + 0.12F * length, Math.min(tmin + 0.4F * length, tf));
            Vector3f grip = new Vector3f();
            int inSlab = 0;

            for (Vector3f q : points) {
                if (Math.abs((new Vector3f(q)).sub(c).dot(u) - tg) < 0.06F * length) {
                    grip.add(q);
                    ++inSlab;
                }
            }

            if (inSlab > 0) {
                grip.div((float) inSlab);
            } else {
                grip.set(c);
            }

            grip.add((new Vector3f(u)).mul(tg - (new Vector3f(grip)).sub(c).dot(u)));
            float bend = 0.0F;

            for (Vector3f q : points) {
                Vector3f r = (new Vector3f(q)).sub(c);
                if (r.dot(u) > tmin + 0.6F * length) {
                    bend += r.dot(v);
                }
            }

            if (bend < 0.0F) {
                v.negate();
            }

            Vector3f w = (new Vector3f(u)).cross(v).normalize();
            v = (new Vector3f(w)).cross(u).normalize();
            Vector3f d = new Vector3f(BLADE_IN_HAND);
            Vector3f flat = (new Vector3f(FLAT_IN_HAND))
                    .sub((new Vector3f(d)).mul(FLAT_IN_HAND.dot(d)))
                    .normalize();
            Vector3f edge = (new Vector3f(flat)).cross(d).normalize();
            Matrix4f target = (new Matrix4f()).set3x3(new Matrix3f(d, edge, flat));
            Matrix4f knifeFrame = (new Matrix4f()).set3x3(new Matrix3f(u, v, w));
            Matrix4f toHand = target.mul(knifeFrame.transpose3x3(new Matrix4f()));
            return new Hold(model, bones, grip, toHand, length);
        }
    }

    private static Vector3f[] principalAxes(double[][] m) {
        double[][] a = new double[][] {(double[]) m[0].clone(), (double[]) m[1].clone(), (double[]) m[2].clone()};
        double[][] e = new double[][] {
            {1.0, 0.0, 0.0},
            {0.0, 1.0, 0.0},
            {0.0, 0.0, 1.0}
        };

        for (int sweep = 0; sweep < 60; sweep++) {
            int p = 0;
            int q = 1;
            if (Math.abs(a[0][2]) > Math.abs(a[p][q])) {
                q = 2;
            }

            if (Math.abs(a[1][2]) > Math.abs(a[p][q])) {
                p = 1;
                q = 2;
            }

            if (Math.abs(a[p][q]) < 1.0E-12) {
                break;
            }

            double theta = 0.5 * Math.atan2(2.0 * a[p][q], a[q][q] - a[p][p]);
            double cs = Math.cos(theta);
            double sn = Math.sin(theta);

            for (int k = 0; k < 3; k++) {
                double kp = a[k][p];
                double kq = a[k][q];
                a[k][p] = cs * kp - sn * kq;
                a[k][q] = sn * kp + cs * kq;
            }

            for (int k = 0; k < 3; k++) {
                double pk = a[p][k];
                double qk = a[q][k];
                a[p][k] = cs * pk - sn * qk;
                a[q][k] = sn * pk + cs * qk;
            }

            for (int k = 0; k < 3; k++) {
                double kp = e[k][p];
                double kq = e[k][q];
                e[k][p] = cs * kp - sn * kq;
                e[k][q] = sn * kp + cs * kq;
            }
        }

        Integer[] order = {0, 1, 2};
        Arrays.sort(order, (x, y) -> Double.compare(a[y][y], a[x][x]));
        Vector3f[] out = new Vector3f[3];

        for (int i = 0; i < 3; i++) {
            int k = order[i];
            out[i] = (new Vector3f((float) e[0][k], (float) e[1][k], (float) e[2][k])).normalize();
        }

        return out;
    }

    private void applyCamera(MeleeSkeleton model, MeleeAnimator animator, PoseStack pose) {
        this.applyCamera(model, animator.pose(), pose);
    }

    private void applyCamera(MeleeSkeleton model, float[] channels, PoseStack pose) {
        int bone = model.getCameraBone();
        if (bone >= 0) {
            int i = bone * 9;
            if (i + 5 < channels.length) {
                pose.translate(channels[i + 3] / 16.0F, -channels[i + 4] / 16.0F, -channels[i + 5] / 16.0F);
                pose.mulPose((Quaternionfc) Axis.ZP.rotationDegrees(-channels[i + 2]));
                pose.mulPose((Quaternionfc) Axis.YP.rotationDegrees(channels[i + 1]));
                pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees(channels[i]));
            }
        }
    }

    /** A gun being aimed tips its muzzle up a little and sways with the breath, more the further it is raised. */
    private void applyAim(MeleeAnimator animator, float aim, PoseStack pose) {
        if (aim <= 0.0F) return;
        float t = animator.swayTime();
        float pitch = AIM_RAISE_DEGREES + SWAY_PITCH_DEGREES * (float) Math.sin(t * SWAY_PITCH_RATE);
        float yaw = SWAY_YAW_DEGREES * (float) Math.sin(t * SWAY_YAW_RATE);
        pose.mulPose((Quaternionfc) Axis.XP.rotationDegrees(aim * pitch));
        pose.mulPose((Quaternionfc) Axis.YP.rotationDegrees(aim * yaw));
    }

    /** Puts the eye at the skin's view point; a gun being aimed draws part of the way toward its sight line. */
    private void applyViewAnchor(MeleeSkeleton model, PoseStack pose, float aim) {
        int bone = model.getViewAnchorBone();
        if (bone >= 0) {
            float x = model.pivotX(bone);
            float y = model.pivotY(bone);
            float z = model.pivotZ(bone);
            int sight = model.getAimViewBone();
            if (aim > 0.0F && sight >= 0) {
                float k = aim * AIM_REACH;
                x += (model.pivotX(sight) - x) * k;
                y += (model.pivotY(sight) - y) * k;
                z += (model.pivotZ(sight) - z) * k;
            }

            pose.translate(-x / 16.0F, -y / 16.0F, -z / 16.0F);
        }
    }

    private void submitFlash(
            MeleeBuffer bones,
            MeleeSkeleton model,
            PoseStack pose,
            SubmitNodeCollector collector,
            float flash,
            float spin) {
        int bone = model.getMuzzleBone();
        if (flash <= 0.0F || bone < 0) return;
        Matrix4f muzzle = (new Matrix4f(bones.world[bone]))
                .translate(model.pivotX(bone) / 16.0F, model.pivotY(bone) / 16.0F, model.pivotZ(bone) / 16.0F);
        MuzzleFlash.submit(pose, collector, muzzle, flash, spin);
    }

    private void buildWorld(MeleeBuffer bones, MeleeSkeleton model, float[] channels, boolean[] hidden) {
        int count = model.boneCount();
        bones.ensureCapacity(count);

        for (int i = 0; i < count; i++) {
            bones.visible[i] = i >= hidden.length || !hidden[i];
            int c = i * 9;
            this.local.identity();
            this.local.translate(-channels[c + 3] / 16.0F, channels[c + 4] / 16.0F, channels[c + 5] / 16.0F);
            this.applyPivotRotation(
                    model,
                    i,
                    model.restRotationX(i) - channels[c] * ((float) Math.PI / 180F),
                    model.restRotationY(i) - channels[c + 1] * ((float) Math.PI / 180F),
                    model.restRotationZ(i) + channels[c + 2] * ((float) Math.PI / 180F),
                    channels[c + 6],
                    channels[c + 7],
                    channels[c + 8]);
            this.compose(bones, model, i);
        }

        this.hideHands(bones, model);
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
        float px = model.pivotX(bone) / 16.0F;
        float py = model.pivotY(bone) / 16.0F;
        float pz = model.pivotZ(bone) / 16.0F;
        this.local.translate(px, py, pz);
        this.rotation.rotationZ(rotZ).rotateY(rotY).rotateX(rotX);
        this.local.rotate(this.rotation);
        this.local.scale(scaleX, scaleY, scaleZ);
        this.local.translate(-px, -py, -pz);
    }

    private void compose(MeleeBuffer bones, MeleeSkeleton model, int bone) {
        int parent = model.parentOf(bone);
        if (parent < 0) {
            bones.world[bone].set(this.local);
        } else {
            bones.world[bone].set(bones.world[parent]).mul(this.local);
        }
    }

    private void hideHands(MeleeBuffer bones, MeleeSkeleton model) {
        if (model.getRightHandBone() >= 0) {
            bones.visible[model.getRightHandBone()] = false;
        }

        if (model.getLeftHandBone() >= 0) {
            bones.visible[model.getLeftHandBone()] = false;
        }
    }

    private void submitGeometry(
            MeleeBuffer bones,
            MeleeSkeleton model,
            MeleeDesign design,
            PoseStack pose,
            SubmitNodeCollector collector,
            int light,
            boolean mirrored) {
        this.prepareHolyBones(model, design);
        Identifier texture = design.getTexture();
        this.submitPass(bones, model, RenderTypes.entityTranslucent(texture), pose, collector, light, false, mirrored);
        if (this.holyPresent) {
            this.submitPass(
                    bones,
                    model,
                    RenderTypes.entityTranslucentEmissive(texture),
                    pose,
                    collector,
                    light,
                    true,
                    mirrored);
        }
    }

    private void prepareHolyBones(MeleeSkeleton model, MeleeDesign design) {
        int count = model.boneCount();
        if (!design.getKey().equals(this.holyKey) || this.holy.length != count) {
            this.holyKey = design.getKey();
            this.holy = new boolean[count];
            this.holyPresent = model.markBranches(design.getHolyBones(), this.holy);
        }
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
                if (mesh != null && bones.visible[bone] && this.holy[bone] == emissive) {
                    Matrix4f matrix = bones.combined[bone].set(last.pose()).mul(bones.world[bone]);
                    Matrix3f normals =
                            bones.normals[bone].set(last.normal()).mul(this.boneNormal.set(bones.world[bone]));
                    if (!mirrored) {
                        for (int at = 0; at < mesh.length; at += 8) {
                            this.submitVertex(consumer, matrix, normals, mesh, at, light);
                        }
                    } else {
                        for (int face = 0; face < mesh.length; face += 32) {
                            for (int corner = 3; corner >= 0; --corner) {
                                this.submitVertex(consumer, matrix, normals, mesh, face + corner * 8, light);
                            }
                        }
                    }
                }
            }
        });
    }

    private void submitVertex(
            VertexConsumer consumer, Matrix4f matrix, Matrix3f normals, float[] mesh, int at, int light) {
        this.normal.set(mesh[at + 5], mesh[at + 6], mesh[at + 7]).mul(normals).normalize();
        consumer.addVertex((Matrix4fc) matrix, mesh[at], mesh[at + 1], mesh[at + 2])
                .setColor(1.0F, 1.0F, 1.0F, 1.0F)
                .setUv(mesh[at + 3], mesh[at + 4])
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(this.normal.x, this.normal.y, this.normal.z);
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
        if (bone >= 0) {
            AvatarRenderer renderer =
                    Minecraft.getInstance().getEntityRenderDispatcher().getPlayerRenderer(player);
            if (renderer != null) {
                Identifier skin = player.getSkin().body().texturePath();
                boolean drawRight = right != mirrored;
                armAnchor(this.armPose, model, bones.world[bone], bone, right);
                pose.pushPose();
                pose.mulPose((Matrix4fc) this.armPose);
                if (mirrored) {
                    pose.scale(-1.0F, 1.0F, 1.0F);
                }

                if (drawRight) {
                    renderer.renderRightHand(
                            pose, collector, light, skin, player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
                } else {
                    renderer.renderLeftHand(
                            pose, collector, light, skin, player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
                }

                pose.popPose();
            }
        }
    }

    private static record Hold(MeleeSkeleton model, MeleeBuffer bones, Vector3f grip, Matrix4f toHand, float length) {}

    private static record Rest(MeleeSkeleton model, MeleeBuffer bones) {}
}
