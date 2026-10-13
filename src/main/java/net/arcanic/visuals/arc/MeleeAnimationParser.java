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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/** Reads a Bedrock-style {@code .anim.json}: one clip per animation, with bone keyframes and sound cues. */
final class MeleeAnimationParser {
    private static final String ANIMATIONS = "animations";
    private static final String ANIMATION_LENGTH = "animation_length";
    private static final String BONES = "bones";
    private static final String ROTATION = "rotation";
    private static final String POSITION = "position";
    private static final String SCALE = "scale";
    private static final String SOUND_EFFECTS = "sound_effects";
    private static final String EFFECT = "effect";
    private static final String PRE = "pre";
    private static final String POST = "post";
    private static final String LERP_MODE = "lerp_mode";
    private static final String CATMULLROM = "catmullrom";
    private static final float NEUTRAL_SCALE = 1.0F;

    private MeleeAnimationParser() {}

    static Map<String, MeleeClip> parse(String json, Map<String, Integer> boneIndex) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject animations = root.getAsJsonObject(ANIMATIONS);
        if (animations == null) return Map.of();
        Map<String, MeleeClip> clips = new HashMap<>();
        for (Entry<String, JsonElement> entry : animations.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            MeleeClip clip = parseClip(entry.getKey(), entry.getValue().getAsJsonObject(), boneIndex);
            if (clip != null) clips.put(entry.getKey(), clip);
        }
        return Map.copyOf(clips);
    }

    private static MeleeClip parseClip(String name, JsonObject animation, Map<String, Integer> boneIndex) {
        List<MeleeBoneTrack> tracks = new ArrayList<>();
        float longest = 0;
        JsonObject bones = animation.getAsJsonObject(BONES);
        if (bones != null) {
            for (Entry<String, JsonElement> entry : bones.entrySet()) {
                Integer bone = boneIndex.get(entry.getKey());
                if (bone == null || !entry.getValue().isJsonObject()) continue;
                JsonObject channels = entry.getValue().getAsJsonObject();
                MeleeKeyframes rotation = parseChannel(channels.get(ROTATION), 0);
                MeleeKeyframes position = parseChannel(channels.get(POSITION), 0);
                MeleeKeyframes scale = parseChannel(channels.get(SCALE), NEUTRAL_SCALE);
                if (rotation != null || position != null || scale != null) {
                    longest = Math.max(longest, longestTime(rotation, position, scale));
                    tracks.add(new MeleeBoneTrack(bone, rotation, position, scale));
                }
            }
        }
        if (tracks.isEmpty()) return null;
        float length = animation.has(ANIMATION_LENGTH)
                ? animation.get(ANIMATION_LENGTH).getAsFloat()
                : longest;
        return new MeleeClip(
                name,
                Math.max(length, 0.0F),
                tracks.toArray(new MeleeBoneTrack[0]),
                soundTimes(animation),
                soundNames(animation));
    }

    private static float longestTime(MeleeKeyframes... channels) {
        float longest = 0;
        for (MeleeKeyframes channel : channels) {
            if (channel != null) longest = Math.max(longest, channel.lastTime());
        }
        return longest;
    }

    private static MeleeKeyframes parseChannel(JsonElement channel, float fallback) {
        if (channel == null || channel.isJsonNull()) return null;
        if (!channel.isJsonObject()) {
            float[] value = readVector(channel, fallback);
            return new MeleeKeyframes(new float[] {0.0F}, value, value.clone(), new boolean[] {false});
        }

        TreeMap<Float, JsonElement> keys = new TreeMap<>();
        for (Entry<String, JsonElement> entry : channel.getAsJsonObject().entrySet()) {
            try {
                keys.put(Float.parseFloat(entry.getKey()), entry.getValue());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (keys.isEmpty()) return null;

        int count = keys.size();
        float[] times = new float[count];
        float[] pre = new float[count * 3];
        float[] post = new float[count * 3];
        boolean[] smooth = new boolean[count];
        int i = 0;
        for (Entry<Float, JsonElement> key : keys.entrySet()) {
            times[i] = key.getKey();
            readKeyframe(key.getValue(), fallback, pre, post, smooth, i);
            i++;
        }
        return new MeleeKeyframes(times, pre, post, smooth);
    }

    private static void readKeyframe(
            JsonElement keyframe, float fallback, float[] pre, float[] post, boolean[] smooth, int index) {
        int offset = index * 3;
        if (!keyframe.isJsonObject()) {
            float[] value = readVector(keyframe, fallback);
            store(value, pre, offset);
            store(value, post, offset);
            return;
        }
        JsonObject object = keyframe.getAsJsonObject();
        smooth[index] =
                object.has(LERP_MODE) && CATMULLROM.equals(object.get(LERP_MODE).getAsString());
        float[] after =
                object.has(POST) ? readVector(object.get(POST), fallback) : readVector(object.get(PRE), fallback);
        float[] before = object.has(PRE) ? readVector(object.get(PRE), fallback) : after;
        store(before, pre, offset);
        store(after, post, offset);
    }

    private static void store(float[] value, float[] into, int offset) {
        into[offset] = value[0];
        into[offset + 1] = value[1];
        into[offset + 2] = value[2];
    }

    private static float[] readVector(JsonElement element, float fallback) {
        float[] out = {fallback, fallback, fallback};
        if (element == null || element.isJsonNull()) return out;
        if (element.isJsonPrimitive()) {
            float value = element.getAsFloat();
            out[0] = value;
            out[1] = value;
            out[2] = value;
            return out;
        }
        if (!element.isJsonArray()) return out;
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < 3 && i < array.size(); i++) {
            JsonElement item = array.get(i);
            out[i] = item.isJsonPrimitive() && item.getAsJsonPrimitive().isNumber() ? item.getAsFloat() : fallback;
        }
        return out;
    }

    private static float[] soundTimes(JsonObject animation) {
        TreeMap<Float, String> cues = soundCues(animation);
        float[] times = new float[cues.size()];
        int i = 0;
        for (Float time : cues.keySet()) times[i++] = time;
        return times;
    }

    private static String[] soundNames(JsonObject animation) {
        return soundCues(animation).values().toArray(new String[0]);
    }

    private static TreeMap<Float, String> soundCues(JsonObject animation) {
        TreeMap<Float, String> cues = new TreeMap<>();
        JsonObject effects = animation.getAsJsonObject(SOUND_EFFECTS);
        if (effects == null) return cues;
        for (Entry<String, JsonElement> entry : effects.entrySet()) {
            try {
                JsonElement cue = entry.getValue();
                String sound =
                        cue.isJsonObject() ? cue.getAsJsonObject().get(EFFECT).getAsString() : cue.getAsString();
                if (sound != null && !sound.isEmpty()) cues.put(Float.parseFloat(entry.getKey()), sound);
            } catch (Exception ignored) {
            }
        }
        return cues;
    }
}
