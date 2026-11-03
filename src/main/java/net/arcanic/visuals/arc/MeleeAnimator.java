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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Drives the first-person animation of a melee skin, and of a gun: its aim while the bow is pulled and its shots. */
public final class MeleeAnimator {
    private static final MeleeAnimator INSTANCE = new MeleeAnimator();
    private static final MeleeAnimator OFF_HAND = new MeleeAnimator();
    // the knife studio's picture runs its own, so it never restarts or steals the game's animation
    private static final MeleeAnimator STUDIO = new MeleeAnimator();
    private static final MeleeAnimator STUDIO_OFF_HAND = new MeleeAnimator();
    private static final String CLIP_STATIC_IDLE = "static_idle";
    private static final String CLIP_DRAW = "draw";
    private static final String CLIP_DRAW_ALTERNATE = "draw_1";
    private static final String CLIP_INSPECT = "inspect";
    private static final String CLIP_INSPECT_ALTERNATE = "inspect_1";
    private static final String CLIP_MELEE_PRIMARY = "melee_";
    private static final String CLIP_MELEE_SECONDARY = "melee_3";
    private static final String CLIP_RUN = "run";
    private static final String CLIP_RUN_START = "run_start";
    private static final String CLIP_RUN_END = "run_end";
    private static final String CLIP_IDLE = "idle";
    private static final String CLIP_SHOOT = "shoot";
    private static final String CLIP_BOLT = "bolt";
    private static final float TRANSITION_INSTANT = 0.0F;
    private static final float TRANSITION_ACTION = 0.2F;
    private static final float TRANSITION_RUN_START = 0.2F;
    private static final float TRANSITION_RUN_LOOP = 0.2F;
    private static final float TRANSITION_RUN_END = 0.3F;
    private static final float TRANSITION_IDLE = 0.3F;
    private static final float TRANSITION_BOLT = 0.1F;
    private static final float BOLT_DELAY = 0.25F;
    private static final float AIM_RATE = 9.0F;
    private static final float AIM_LOOSE = 0.05F;
    private static final float FLASH_SECONDS = 0.07F;
    private static final float MAX_DELTA_SECONDS = 0.25F;
    private static final float NANOS_PER_SECOND = 1.0E9F;
    private static final float RUN_CYCLE_DISTANCE = 2.0F;

    private static final int PRIMARY_ATTACK_VARIANTS = 2;
    private static final int RUN_MODE_IDLE = -1;
    private static final int RUN_MODE_CYCLE = 0;
    private static final float NEUTRAL_SCALE = 1.0F;
    private static final int OFF_HAND_SLOT = 40;

    private final MeleePlayer baseTrack = new MeleePlayer();
    private final MeleePlayer mainTrack = new MeleePlayer();
    private final MeleePlayer movementTrack = new MeleePlayer();
    // a shot's kick, laid over whatever the hands are doing: its keys are offsets from the pose, not a pose
    private final MeleePlayer kickTrack = new MeleePlayer();
    private MeleeSkeleton model;
    private String modelKey;
    private boolean renderedSinceTick;
    private MeleeVariant variant = MeleeVariant.DEFAULT;
    private float[] staticPose = new float[0];
    private float[] additivePose = new float[0];
    private float[] staticFade = new float[0];
    private float[] additiveFade = new float[0];
    private float[] kickPose = new float[0];
    private float[] composedPose = new float[0];
    private boolean[] hidden = new boolean[0];
    private float staticFadeRemaining;
    private float staticFadeDuration;
    private float additiveFadeRemaining;
    private float additiveFadeDuration;
    private MeleeStage mainState = MeleeStage.START;
    private MeleeMotion movementState = MeleeMotion.IDLE;
    private int attackIndex;
    private int runMode = RUN_MODE_IDLE;
    private float runAnchor;
    private long lastNanos;
    private Item heldItem;
    private int heldSlot = -1;
    private MeleeSlot slot = MeleeSlot.KNIFE;
    private float aim;
    private float swayTime;
    private float flashLeft;
    private float flashSpin;
    private float boltIn;
    private int shotsSeen;

    private MeleeAnimator() {}

    public static MeleeAnimator getInstance() {
        return INSTANCE;
    }

    public static MeleeAnimator offHand() {
        return OFF_HAND;
    }

    /** The animator for one hand: the game's, or the knife studio's while its picture is drawn. */
    static MeleeAnimator forHand(boolean primary) {
        if (MeleeSkins.inStudio()) return primary ? STUDIO : STUDIO_OFF_HAND;
        return primary ? INSTANCE : OFF_HAND;
    }

    public static MeleeAnimator studio() {
        return STUDIO;
    }

    public static void resetStudio() {
        STUDIO.reset();
        STUDIO_OFF_HAND.reset();
    }

    public static void resetAll() {
        INSTANCE.reset();
        OFF_HAND.reset();
    }

    public void reset() {
        model = null;
        modelKey = null;
        baseTrack.clear();
        mainTrack.clear();
        movementTrack.clear();
        kickTrack.clear();
        boltIn = 0.0F;
        mainState = MeleeStage.START;
        movementState = MeleeMotion.IDLE;
        runMode = RUN_MODE_IDLE;
        attackIndex = 0;
        heldItem = null;
        heldSlot = -1;
        staticFadeRemaining = 0.0F;
        additiveFadeRemaining = 0.0F;
        lastNanos = 0L;
        slot = MeleeSlot.KNIFE;
        aim = 0.0F;
        flashLeft = 0.0F;
    }

    public void stop() {
        if (model != null) reset();
    }

    public void restart() {
        mainState = MeleeStage.START;
        movementState = MeleeMotion.IDLE;
        runMode = RUN_MODE_IDLE;
        mainTrack.clear();
        movementTrack.clear();
        kickTrack.clear();
        boltIn = 0.0F;
        staticFadeRemaining = 0.0F;
        additiveFadeRemaining = 0.0F;
        flashLeft = 0.0F;
        shotsSeen = MeleeShots.count(); // a shot loosed before this gun was drawn is not its own
    }

    MeleeClip referenceClip() {
        return baseTrack.getClip();
    }

    /** The variant shown for {@code skeleton}, or null when this animator is not on it. */
    MeleeVariant variantOf(MeleeSkeleton skeleton) {
        return skeleton != null && skeleton == model ? variant : null;
    }

    public float[] pose() {
        return composedPose;
    }

    public boolean[] hidden() {
        return hidden;
    }

    /** How far a gun is raised toward the eye while its bow is pulled, eased, 0 to 1. */
    float aim() {
        return aim * aim * (3.0F - 2.0F * aim);
    }

    /** Seconds of the gun's breathing sway; it runs on while the gun is held. */
    float swayTime() {
        return swayTime;
    }

    /** The muzzle flash of this gun's last shot, 1 as it fires and 0 once it has burnt out. */
    float flash() {
        return flashLeft / FLASH_SECONDS;
    }

    float flashSpin() {
        return flashSpin;
    }

    public boolean consumeRendered() {
        boolean rendered = renderedSinceTick;
        renderedSinceTick = false;
        return rendered;
    }

    public MeleeSkeleton prepare(AbstractClientPlayer player, InteractionHand hand) {
        if (player == null) return null;
        ItemStack held = MeleeSkins.stack(player, hand);
        MeleeDesign design = MeleeCatalog.equipped(held);
        if (design == null) return null;
        MeleeSkeleton skeleton = MeleeModels.get(design);
        if (skeleton == null) return null;
        renderedSinceTick = true;
        if (skeleton != model || !design.getKey().equals(modelKey)) bind(design, skeleton);
        else if (heldChanged(player, hand)) restart();
        float delta = delta();
        advance(player, delta);
        if (slot == MeleeSlot.BOW) aimAndFire(player, hand, delta);
        compose();
        return model;
    }

    /** The gun follows the bow: raised while it is pulled, fired when a pull is let go. */
    private void aimAndFire(AbstractClientPlayer player, InteractionHand hand, float delta) {
        float pull = 0.0F;
        if (!MeleeSkins.inStudio() && player instanceof LocalPlayer local) {
            pull = MeleeShots.pull(
                    local, hand, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
            if (MeleeShots.count() != shotsSeen) {
                shotsSeen = MeleeShots.count();
                if (MeleeShots.hand() == hand) onFire();
            }
        }
        aim += (pull - aim) * (1.0F - (float) Math.exp(-AIM_RATE * delta));
        swayTime += delta;
        flashLeft = Math.max(0.0F, flashLeft - delta);
        if (boltIn > 0.0F) {
            boltIn -= delta;
            if (boltIn <= 0.0F) {
                boltIn = 0.0F;
                playMain(variantName(CLIP_BOLT), TRANSITION_BOLT);
            }
        }
    }

    /**
     * A shot: the kick and the flash at once, then, once the gun settles, the right hand works the bolt the way
     * the draw does.
     */
    public void onFire() {
        if (model == null || slot != MeleeSlot.BOW) return;
        MeleeClip kick = model.clip(variantName(CLIP_SHOOT));
        if (kick != null) kickTrack.play(kick, MeleeLoop.ONCE_STOP);
        if (mainTrack.isActive()) {
            beginStaticFade(TRANSITION_ACTION); // a shot cuts a draw or an inspect short
            mainTrack.clear();
        }
        mainState = MeleeStage.IDLE;
        boltIn = BOLT_DELAY;
        flashLeft = FLASH_SECONDS;
        flashSpin = ThreadLocalRandom.current().nextFloat() * (float) Math.PI;
    }

    public void onAttack(boolean primary) {
        if (model == null || mainState != MeleeStage.IDLE || slot == MeleeSlot.BOW) return;
        if (primary) {
            String clip = variantName(CLIP_MELEE_PRIMARY + (attackIndex + 1));
            attackIndex = (attackIndex + 1) % PRIMARY_ATTACK_VARIANTS;
            playMain(clip, TRANSITION_ACTION);
        } else {
            playMain(variantName(CLIP_MELEE_SECONDARY), TRANSITION_ACTION);
        }
    }

    public void onInspect() {
        if (model != null && mainState == MeleeStage.IDLE && aim < AIM_LOOSE && boltIn <= 0.0F) {
            String alternate = variantName(CLIP_INSPECT_ALTERNATE);
            boolean useAlternate =
                    model.hasClip(alternate) && ThreadLocalRandom.current().nextBoolean();
            playMain(useAlternate ? alternate : variantName(CLIP_INSPECT), TRANSITION_ACTION);
        }
    }

    private boolean heldChanged(AbstractClientPlayer player, InteractionHand hand) {
        Item item = MeleeSkins.stack(player, hand).getItem();
        int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : OFF_HAND_SLOT;
        if (item == heldItem && slot == heldSlot) return false;
        heldItem = item;
        heldSlot = slot;
        return true;
    }

    private void bind(MeleeDesign design, MeleeSkeleton skeleton) {
        heldItem = null;
        heldSlot = -1;
        model = skeleton;
        modelKey = design.getKey();
        slot = design.getSlot();
        aim = 0.0F;
        int bones = skeleton.boneCount();
        int channels = bones * MeleeClip.BONE_STRIDE;
        staticPose = new float[channels];
        additivePose = new float[channels];
        staticFade = new float[channels];
        additiveFade = new float[channels];
        kickPose = new float[channels];
        composedPose = new float[channels];
        hidden = new boolean[bones];
        List<MeleeVariant> variants = design.getVariants();
        variant = variants.isEmpty()
                ? MeleeVariant.DEFAULT
                : variants.get(ThreadLocalRandom.current().nextInt(variants.size()));
        skeleton.markHidden(variant.hiddenBones(), hidden);
        restart();
        baseTrack.play(skeleton.clip(variantName(CLIP_STATIC_IDLE)), MeleeLoop.LOOP);
        lastNanos = 0L;
    }

    private String variantName(String clip) {
        String suffix = variant.suffix();
        if (suffix.isEmpty()) return clip;
        String named = clip + suffix;
        return model != null && model.hasClip(named) ? named : clip;
    }

    private float delta() {
        long now = System.nanoTime();
        if (lastNanos == 0L) {
            lastNanos = now;
            return 0.0F;
        }
        float seconds = (float) (now - lastNanos) / NANOS_PER_SECOND;
        lastNanos = now;
        if (Minecraft.getInstance().isPaused()) return 0.0F;
        return Math.max(0.0F, Math.min(seconds, MAX_DELTA_SECONDS));
    }

    private void advance(AbstractClientPlayer player, float delta) {
        baseTrack.advance(delta);
        mainTrack.advance(delta);
        movementTrack.advance(delta);
        kickTrack.advance(delta);
        staticFadeRemaining = Math.max(0.0F, staticFadeRemaining - delta);
        additiveFadeRemaining = Math.max(0.0F, additiveFadeRemaining - delta);
        updateMain();
        updateMovement(player);
    }

    private void updateMain() {
        if (mainState == MeleeStage.START) {
            String alternate = variantName(CLIP_DRAW_ALTERNATE);
            boolean useAlternate =
                    model.hasClip(alternate) && ThreadLocalRandom.current().nextBoolean();
            playMain(useAlternate ? alternate : variantName(CLIP_DRAW), TRANSITION_INSTANT);
            mainState = MeleeStage.IDLE;
        }
    }

    private void updateMovement(AbstractClientPlayer player) {
        boolean sprinting = player.isSprinting() && mainTrack.isStopped();
        if (movementState == MeleeMotion.IDLE) {
            if (sprinting && model.hasClip(variantName(CLIP_RUN_START))) {
                movementState = MeleeMotion.RUN;
                runMode = RUN_MODE_IDLE;
                playMovement(variantName(CLIP_RUN_START), MeleeLoop.ONCE_HOLD, TRANSITION_RUN_START);
            } else if (slot.idles()
                    && (movementTrack.getClip() == null || movementTrack.isHolding())
                    && model.hasClip(variantName(CLIP_IDLE))) {
                playMovement(variantName(CLIP_IDLE), MeleeLoop.LOOP, TRANSITION_IDLE);
            }
            return;
        }
        if (!sprinting) {
            movementState = MeleeMotion.IDLE;
            runMode = RUN_MODE_IDLE;
            playMovement(variantName(CLIP_RUN_END), MeleeLoop.ONCE_HOLD, TRANSITION_RUN_END);
            return;
        }
        if (runMode != RUN_MODE_CYCLE && movementTrack.isHolding()) {
            runMode = RUN_MODE_CYCLE;
            runAnchor = HandMovement.distance();
            playMovement(variantName(CLIP_RUN), MeleeLoop.LOOP, TRANSITION_RUN_LOOP);
        }
        if (runMode == RUN_MODE_CYCLE && player.onGround()) {
            float phase = (HandMovement.distance() - runAnchor) % RUN_CYCLE_DISTANCE;
            if (phase < 0.0F) phase += RUN_CYCLE_DISTANCE;
            movementTrack.setProgress(phase / RUN_CYCLE_DISTANCE);
        }
    }

    private void playMain(String name, float fade) {
        MeleeClip clip = model.clip(name);
        if (clip == null) return;
        beginStaticFade(fade);
        mainTrack.play(clip, MeleeLoop.ONCE_STOP);
    }

    private void playMovement(String name, MeleeLoop loop, float fade) {
        MeleeClip clip = model.clip(name);
        if (clip == null) {
            movementTrack.clear();
            return;
        }
        beginAdditiveFade(fade);
        movementTrack.play(clip, loop);
    }

    private void beginStaticFade(float duration) {
        if (duration <= 0.0F) {
            staticFadeRemaining = 0.0F;
            return;
        }
        System.arraycopy(staticPose, 0, staticFade, 0, staticPose.length);
        staticFadeDuration = duration;
        staticFadeRemaining = duration;
    }

    private void beginAdditiveFade(float duration) {
        if (duration <= 0.0F) {
            additiveFadeRemaining = 0.0F;
            return;
        }
        System.arraycopy(additivePose, 0, additiveFade, 0, additivePose.length);
        additiveFadeDuration = duration;
        additiveFadeRemaining = duration;
    }

    private void compose() {
        fillNeutral(staticPose);
        baseTrack.evaluate(staticPose);
        if (mainTrack.isActive()) mainTrack.evaluate(staticPose);
        blend(staticPose, staticFade, staticFadeRemaining, staticFadeDuration);

        fillNeutral(additivePose);
        if (movementTrack.isActive()) movementTrack.evaluate(additivePose);
        blend(additivePose, additiveFade, additiveFadeRemaining, additiveFadeDuration);

        fillNeutral(kickPose);
        if (kickTrack.isActive()) kickTrack.evaluate(kickPose);

        for (int bone = 0; bone < composedPose.length; bone += MeleeClip.BONE_STRIDE) {
            for (int axis = 0; axis < 3; axis++) {
                int rotation = bone + MeleeClip.ROTATION_OFFSET + axis;
                int position = bone + MeleeClip.POSITION_OFFSET + axis;
                int scale = bone + MeleeClip.SCALE_OFFSET + axis;
                composedPose[rotation] = staticPose[rotation] + additivePose[rotation] + kickPose[rotation];
                composedPose[position] = staticPose[position] + additivePose[position] + kickPose[position];
                composedPose[scale] = staticPose[scale] * additivePose[scale] * kickPose[scale];
            }
        }
    }

    private static void blend(float[] pose, float[] from, float remaining, float duration) {
        if (remaining <= 0.0F || duration <= 0.0F) return;
        float t = 1.0F - remaining / duration;
        for (int i = 0; i < pose.length; i++) {
            float start = from[i];
            pose[i] = start + (pose[i] - start) * t;
        }
    }

    private static void fillNeutral(float[] pose) {
        Arrays.fill(pose, 0.0F);
        for (int i = MeleeClip.SCALE_OFFSET; i < pose.length; i += MeleeClip.BONE_STRIDE) {
            pose[i] = NEUTRAL_SCALE;
            pose[i + 1] = NEUTRAL_SCALE;
            pose[i + 2] = NEUTRAL_SCALE;
        }
    }
}
