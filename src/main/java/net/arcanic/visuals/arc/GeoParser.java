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

/** Reads a Bedrock {@code .geo.json} model: bones with pivots and rotations, cubes with box UV or per-face UV. */
public class GeoParser {
    private static final int FACE_CORNERS = 4;
    private static final int UV_QUARTER_TURN = 90;
    private static final int UV_FULL_TURN = 360;
    private static final String INVALID_UV_ROTATION = "Invalid face UV rotation: ";

    public static GeoParts parse(String json) {
        try {
            return parse(JsonParser.parseString(json).getAsJsonObject());
        } catch (Exception e) {
            return null;
        }
    }

    private static GeoParts parse(JsonObject root) {
        JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
        if (geometries == null || geometries.size() == 0) return null;
        JsonObject geometry = geometries.get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        int textureWidth = description != null && description.has("texture_width")
                ? description.get("texture_width").getAsInt()
                : 64;
        int textureHeight = description != null && description.has("texture_height")
                ? description.get("texture_height").getAsInt()
                : 64;
        GeoParts parts = new GeoParts();
        parts.textureWidth = textureWidth;
        parts.textureHeight = textureHeight;
        JsonArray bones = geometry.getAsJsonArray("bones");
        if (bones != null) {
            List<float[]> frontRects = collectFrontRects(bones);
            Map<String, GeoPart> byName = new HashMap<>();
            for (JsonElement element : bones) {
                GeoPart bone = parseBone(element.getAsJsonObject(), textureWidth, textureHeight, frontRects);
                byName.put(bone.name, bone);
            }
            // link children to parents; a bone whose parent is missing becomes a root
            for (JsonElement element : bones) {
                JsonObject json = element.getAsJsonObject();
                GeoPart bone = byName.get(json.get("name").getAsString());
                GeoPart parent = json.has("parent") && !json.get("parent").isJsonNull()
                        ? byName.get(json.get("parent").getAsString())
                        : null;
                if (parent != null) {
                    parent.children.add(bone);
                    bone.parent = parent;
                } else {
                    parts.roots.add(bone);
                }
            }
        }
        return parts;
    }

    private static List<float[]> collectFrontRects(JsonArray bones) {
        List<float[]> rects = new ArrayList<>();
        for (JsonElement element : bones) {
            JsonObject bone = element.getAsJsonObject();
            if (!bone.has("cubes")) continue;
            for (JsonElement cubeElement : bone.getAsJsonArray("cubes")) {
                JsonObject cube = cubeElement.getAsJsonObject();
                if (!cube.has("uv") || !cube.get("uv").isJsonArray()) continue;
                JsonArray uv = cube.getAsJsonArray("uv");
                float[] size = readVec(cube, "size", new float[] {1.0F, 1.0F, 1.0F});
                float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0.0F;
                float width = size[0] + inflate * 2.0F;
                float height = size[1] + inflate * 2.0F;
                float depth = size[2] + inflate * 2.0F;
                rects.add(boxRect("north", uv.get(0).getAsFloat(), uv.get(1).getAsFloat(), width, height, depth));
            }
        }
        return rects;
    }

    private static boolean isRectClaimed(List<float[]> rects, float[] rect) {
        for (float[] other : rects) {
            if (rect[0] < other[2] && other[0] < rect[2] && rect[1] < other[3] && other[1] < rect[3]) return true;
        }
        return false;
    }

    private static GeoPart parseBone(JsonObject json, int textureWidth, int textureHeight, List<float[]> frontRects) {
        GeoPart bone = new GeoPart(json.get("name").getAsString());
        if (json.has("pivot")) {
            JsonArray pivot = json.getAsJsonArray("pivot");
            bone.pivotX = -pivot.get(0).getAsFloat();
            bone.pivotY = pivot.get(1).getAsFloat();
            bone.pivotZ = pivot.get(2).getAsFloat();
        }
        if (json.has("rotation")) {
            JsonArray rotation = json.getAsJsonArray("rotation");
            bone.setRotX((float) Math.toRadians(-rotation.get(0).getAsFloat()));
            bone.setRotY((float) Math.toRadians(-rotation.get(1).getAsFloat()));
            bone.setRotZ((float) Math.toRadians(rotation.get(2).getAsFloat()));
        }
        if (json.has("cubes")) {
            for (JsonElement cube : json.getAsJsonArray("cubes")) {
                bone.cubes.add(parseCube(cube.getAsJsonObject(), textureWidth, textureHeight, frontRects));
            }
        }
        return bone;
    }

    private static GeoModel parseCube(JsonObject json, int textureWidth, int textureHeight, List<float[]> frontRects) {
        float[] origin = readVec(json, "origin", new float[] {0.0F, 0.0F, 0.0F});
        float[] size = readVec(json, "size", new float[] {1.0F, 1.0F, 1.0F});
        float[] pivot = readVec(json, "pivot", origin.clone());
        float[] rotation = readVec(json, "rotation", new float[] {0.0F, 0.0F, 0.0F});
        float inflate = json.has("inflate") ? json.get("inflate").getAsFloat() : 0.0F;
        boolean mirror = json.has("mirror") && json.get("mirror").getAsBoolean();

        GeoModel cube = new GeoModel(size[0], size[1], size[2]);
        cube.pivot = new GeoVec(-pivot[0], pivot[1], pivot[2]);
        cube.rotation = new GeoVec((float) Math.toRadians(-rotation[0]), (float) Math.toRadians(-rotation[1]), (float)
                Math.toRadians(rotation[2]));
        cube.inflate = inflate;
        cube.mirror = mirror;
        buildFaces(cube, origin, size, inflate, json, textureWidth, textureHeight, frontRects);
        return cube;
    }

    private static void buildFaces(
            GeoModel cube,
            float[] origin,
            float[] size,
            float inflate,
            JsonObject json,
            int textureWidth,
            int textureHeight,
            List<float[]> frontRects) {
        float x = origin[0] - inflate;
        float y = origin[1] - inflate;
        float z = origin[2] - inflate;
        float width = size[0] + inflate * 2.0F;
        float height = size[1] + inflate * 2.0F;
        float depth = size[2] + inflate * 2.0F;
        float minX = -(x + width) / 16.0F;
        float maxX = -x / 16.0F;
        float minY = y / 16.0F;
        float maxY = (y + height) / 16.0F;
        float minZ = z / 16.0F;
        float maxZ = (z + depth) / 16.0F;

        JsonObject faceUv = null;
        float u = 0.0F;
        float v = 0.0F;
        if (json.has("uv")) {
            JsonElement uv = json.get("uv");
            if (uv.isJsonArray()) {
                JsonArray box = uv.getAsJsonArray();
                u = box.get(0).getAsFloat();
                v = box.get(1).getAsFloat();
            } else if (uv.isJsonObject()) {
                faceUv = uv.getAsJsonObject();
            }
        }

        // A flat card that shares its south rectangle with another cube shows its own front on the back, mirrored.
        boolean southUsesFront = depth == 0.0F
                && faceUv == null
                && isRectClaimed(frontRects, boxRect("south", u, v, width, height, depth));

        cube.faces[0] = makeFace(
                "north",
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                0.0F,
                0.0F,
                -1.0F,
                faceUv,
                u,
                v,
                width,
                height,
                depth,
                textureWidth,
                textureHeight,
                cube.mirror,
                false);
        cube.faces[1] = makeFace(
                "south",
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                0.0F,
                0.0F,
                1.0F,
                faceUv,
                u,
                v,
                width,
                height,
                depth,
                textureWidth,
                textureHeight,
                cube.mirror,
                southUsesFront);
        cube.faces[2] = makeFace(
                "west",
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                -1.0F,
                0.0F,
                0.0F,
                faceUv,
                u,
                v,
                width,
                height,
                depth,
                textureWidth,
                textureHeight,
                cube.mirror,
                false);
        cube.faces[3] = makeFace(
                "east",
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                1.0F,
                0.0F,
                0.0F,
                faceUv,
                u,
                v,
                width,
                height,
                depth,
                textureWidth,
                textureHeight,
                cube.mirror,
                false);
        cube.faces[4] = makeFace(
                "up",
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                0.0F,
                1.0F,
                0.0F,
                faceUv,
                u,
                v,
                width,
                height,
                depth,
                textureWidth,
                textureHeight,
                cube.mirror,
                false);
        cube.faces[5] = makeFace(
                "down",
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                0.0F,
                -1.0F,
                0.0F,
                faceUv,
                u,
                v,
                width,
                height,
                depth,
                textureWidth,
                textureHeight,
                cube.mirror,
                false);
    }

    private static GeoQuad makeFace(
            String face,
            float minX,
            float minY,
            float minZ,
            float maxX,
            float maxY,
            float maxZ,
            float normalX,
            float normalY,
            float normalZ,
            JsonObject faceUv,
            float u,
            float v,
            float width,
            float height,
            float depth,
            int textureWidth,
            int textureHeight,
            boolean mirror,
            boolean useFront) {
        int quarterTurns = 0;
        float u0;
        float v0;
        float u1;
        float v1;
        if (faceUv != null) {
            if (!faceUv.has(face)) return null;
            JsonObject entry = faceUv.getAsJsonObject(face);
            if (entry.has("uv_rotation")) {
                int degrees = entry.get("uv_rotation").getAsInt();
                if (degrees < 0 || degrees >= UV_FULL_TURN || degrees % UV_QUARTER_TURN != 0) {
                    throw new IllegalArgumentException(INVALID_UV_ROTATION + degrees);
                }
                quarterTurns = degrees / UV_QUARTER_TURN;
            }
            JsonArray uv = entry.getAsJsonArray("uv");
            JsonArray uvSize = entry.getAsJsonArray("uv_size");
            float faceU = uv.get(0).getAsFloat();
            float faceV = uv.get(1).getAsFloat();
            u0 = faceU / textureWidth;
            v0 = faceV / textureHeight;
            u1 = (faceU + uvSize.get(0).getAsFloat()) / textureWidth;
            v1 = (faceV + uvSize.get(1).getAsFloat()) / textureHeight;
        } else {
            String source = face;
            boolean flipU = mirror;
            if (useFront) {
                source = "north";
                flipU = !mirror;
            } else if (mirror && "east".equals(face)) {
                source = "west";
            } else if (mirror && "west".equals(face)) {
                source = "east";
            }
            float[] rect = boxRect(source, u, v, width, height, depth);
            u0 = rect[flipU ? 2 : 0] / textureWidth;
            v0 = rect[1] / textureHeight;
            u1 = rect[flipU ? 0 : 2] / textureWidth;
            v1 = rect[3] / textureHeight;
        }

        GeoVertex[] corners = buildCorners(face, minX, minY, minZ, maxX, maxY, maxZ, u0, v0, u1, v1);
        if (quarterTurns != 0) {
            // turn the texture on the face by moving each corner's UV along
            float[] us = new float[FACE_CORNERS];
            float[] vs = new float[FACE_CORNERS];
            for (int i = 0; i < FACE_CORNERS; i++) {
                us[i] = corners[i].u;
                vs[i] = corners[i].v;
            }
            for (int i = 0; i < FACE_CORNERS; i++) {
                int from = (i + quarterTurns) % FACE_CORNERS;
                corners[i].u = us[from];
                corners[i].v = vs[from];
            }
        }
        return new GeoQuad(corners, normalX, normalY, normalZ);
    }

    private static float[] boxRect(String face, float u, float v, float width, float height, float depth) {
        switch (face) {
            case "up":
                return new float[] {u + depth, v, u + depth + width, v + depth};
            case "down":
                return new float[] {u + depth + width, v, u + depth + width * 2.0F, v + depth};
            case "west":
                return new float[] {u, v + depth, u + depth, v + depth + height};
            case "north":
                return new float[] {u + depth, v + depth, u + depth + width, v + depth + height};
            case "east":
                return new float[] {u + depth + width, v + depth, u + depth * 2.0F + width, v + depth + height};
            case "south":
                return new float[] {
                    u + depth * 2.0F + width, v + depth, u + depth * 2.0F + width * 2.0F, v + depth + height
                };
            default:
                return new float[] {u, v, u, v};
        }
    }

    private static GeoVertex[] buildCorners(
            String face,
            float minX,
            float minY,
            float minZ,
            float maxX,
            float maxY,
            float maxZ,
            float u0,
            float v0,
            float u1,
            float v1) {
        GeoVertex[] corners = new GeoVertex[FACE_CORNERS];
        switch (face) {
            case "north":
                corners[0] = new GeoVertex(minX, maxY, minZ, u0, v0);
                corners[1] = new GeoVertex(maxX, maxY, minZ, u1, v0);
                corners[2] = new GeoVertex(maxX, minY, minZ, u1, v1);
                corners[3] = new GeoVertex(minX, minY, minZ, u0, v1);
                break;
            case "south":
                corners[0] = new GeoVertex(maxX, maxY, maxZ, u0, v0);
                corners[1] = new GeoVertex(minX, maxY, maxZ, u1, v0);
                corners[2] = new GeoVertex(minX, minY, maxZ, u1, v1);
                corners[3] = new GeoVertex(maxX, minY, maxZ, u0, v1);
                break;
            case "west":
                corners[0] = new GeoVertex(minX, maxY, maxZ, u0, v0);
                corners[1] = new GeoVertex(minX, maxY, minZ, u1, v0);
                corners[2] = new GeoVertex(minX, minY, minZ, u1, v1);
                corners[3] = new GeoVertex(minX, minY, maxZ, u0, v1);
                break;
            case "east":
                corners[0] = new GeoVertex(maxX, maxY, minZ, u0, v0);
                corners[1] = new GeoVertex(maxX, maxY, maxZ, u1, v0);
                corners[2] = new GeoVertex(maxX, minY, maxZ, u1, v1);
                corners[3] = new GeoVertex(maxX, minY, minZ, u0, v1);
                break;
            case "up":
                corners[0] = new GeoVertex(minX, maxY, minZ, u0, v0);
                corners[1] = new GeoVertex(maxX, maxY, minZ, u1, v0);
                corners[2] = new GeoVertex(maxX, maxY, maxZ, u1, v1);
                corners[3] = new GeoVertex(minX, maxY, maxZ, u0, v1);
                break;
            case "down":
                corners[0] = new GeoVertex(minX, minY, maxZ, u0, v0);
                corners[1] = new GeoVertex(maxX, minY, maxZ, u1, v0);
                corners[2] = new GeoVertex(maxX, minY, minZ, u1, v1);
                corners[3] = new GeoVertex(minX, minY, minZ, u0, v1);
                break;
            default:
                break;
        }
        return corners;
    }

    private static float[] readVec(JsonObject json, String field, float[] fallback) {
        if (!json.has(field)) return fallback;
        JsonArray array = json.getAsJsonArray(field);
        float[] out = new float[array.size()];
        for (int i = 0; i < out.length; i++) out[i] = array.get(i).getAsFloat();
        return out;
    }
}
