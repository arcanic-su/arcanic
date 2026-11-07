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

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resources.sounds.SoundInstance;

/** Plays one clip: keeps its time, loops, holds or stops it at the end, and starts its sounds on cue. */
final class MeleePlayer {
    private static final int SOUND_CAPACITY = 4;
    private final List<SoundInstance> playing = new ArrayList<>(SOUND_CAPACITY);
    private MeleeClip clip;
    private MeleeLoop mode = MeleeLoop.ONCE_STOP;
    private float time;
    private int soundCursor;
    private boolean stopped = true;

    void play(MeleeClip clip, MeleeLoop mode) {
        stopSounds();
        this.clip = clip;
        this.mode = mode;
        this.time = 0;
        this.soundCursor = 0;
        this.stopped = clip == null;
    }

    void clear() {
        stopSounds();
        clip = null;
        mode = MeleeLoop.ONCE_STOP;
        time = 0;
        soundCursor = 0;
        stopped = true;
    }

    private void stopSounds() {
        for (int i = 0; i < playing.size(); i++) MeleeSoundIds.stop(playing.get(i));
        playing.clear();
    }

    MeleeClip getClip() {
        return clip;
    }

    boolean isStopped() {
        return stopped || clip == null;
    }

    boolean isHolding() {
        return clip != null && mode == MeleeLoop.ONCE_HOLD && time >= clip.getLength();
    }

    boolean isActive() {
        return clip != null && !stopped;
    }

    void setProgress(float progress) {
        if (clip == null) return;
        float length = clip.getLength();
        if (length <= 0) return;
        time = Math.max(0.0F, Math.min(progress, 1.0F)) * length;
    }

    void advance(float delta) {
        if (clip == null || stopped) return;
        float length = clip.getLength();
        if (length <= 0) {
            fireSounds(0);
            return;
        }
        float next = time + delta;
        if (next < length) {
            time = next;
            fireSounds(next);
            return;
        }
        fireSounds(length);
        switch (mode) {
            case LOOP:
                time = next % length;
                soundCursor = 0;
                fireSounds(time);
                break;
            case ONCE_HOLD:
                time = length;
                break;
            default:
                time = length;
                stopped = true;
        }
    }

    void evaluate(float[] pose) {
        if (clip == null) return;
        for (MeleeBoneTrack track : clip.getTracks()) track.sample(time, pose);
    }

    void markBones(boolean[] moved) {
        if (clip == null) return;
        for (MeleeBoneTrack track : clip.getTracks()) moved[track.getTarget()] = true;
    }

    private void fireSounds(float upTo) {
        int count = clip.soundCount();
        while (soundCursor < count && clip.soundTime(soundCursor) <= upTo) {
            SoundInstance sound = MeleeSoundIds.play(clip.soundName(soundCursor));
            soundCursor += 1;
            if (sound != null) {
                pruneFinishedSounds();
                playing.add(sound);
            }
        }
    }

    private void pruneFinishedSounds() {
        for (int i = playing.size() - 1; i >= 0; i--) {
            if (!MeleeSoundIds.isActive(playing.get(i))) playing.remove(i);
        }
    }
}
