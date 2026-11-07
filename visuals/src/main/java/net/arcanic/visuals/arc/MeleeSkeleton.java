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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class MeleeSkeleton {
    public static final int VERTEX_STRIDE = 8;
    public static final int VERTICES_PER_FACE = 4;
    static final String BONE_RIGHT_HAND = "righthand_pos";
    static final String BONE_LEFT_HAND = "lefthand_pos";
    static final String BONE_CAMERA = "camera";
    static final String BONE_VIEW_ANCHOR = "idle_view";
    static final String BONE_AIM_VIEW = "aim_view";
    static final String BONE_MUZZLE = "muzzle_flash";
    static final String BONE_THIRD_PERSON_GRIP = "thirdperson_hand";
    private static final float MODEL_SCALE = 16.0F;
    private static final int VECTOR_STRIDE = 3;
    private final String[] boneNames;
    private final int[] boneParents;
    private final float[] bonePivots;
    private final float[] boneRestRotation;
    private final float[][] boneMeshes;
    private final Map<String, Integer> boneIndex;
    private final Map<String, MeleeClip> clips;
    private final int rightHandBone;
    private final int leftHandBone;
    private final int cameraBone;
    private final int viewAnchorBone;
    private final int aimViewBone;
    private final int muzzleBone;
    private final int thirdPersonGripBone;

    private MeleeSkeleton(
            String[] boneNames,
            int[] boneParents,
            float[] bonePivots,
            float[] boneRestRotation,
            float[][] boneMeshes,
            Map<String, Integer> boneIndex,
            Map<String, MeleeClip> clips) {
        this.boneNames = boneNames;
        this.boneParents = boneParents;
        this.bonePivots = bonePivots;
        this.boneRestRotation = boneRestRotation;
        this.boneMeshes = boneMeshes;
        this.boneIndex = boneIndex;
        this.clips = clips;
        this.rightHandBone = (Integer) boneIndex.getOrDefault("righthand_pos", -1);
        this.leftHandBone = (Integer) boneIndex.getOrDefault("lefthand_pos", -1);
        this.cameraBone = (Integer) boneIndex.getOrDefault("camera", -1);
        this.viewAnchorBone = (Integer) boneIndex.getOrDefault("idle_view", this.cameraBone);
        this.aimViewBone = (Integer) boneIndex.getOrDefault(BONE_AIM_VIEW, -1);
        this.muzzleBone = (Integer) boneIndex.getOrDefault(BONE_MUZZLE, -1);
        this.thirdPersonGripBone = (Integer) boneIndex.getOrDefault(BONE_THIRD_PERSON_GRIP, -1);
    }

    public int boneCount() {
        return this.boneNames.length;
    }

    public int parentOf(int bone) {
        return this.boneParents[bone];
    }

    public float pivotX(int bone) {
        return this.bonePivots[bone * 3];
    }

    public float pivotY(int bone) {
        return this.bonePivots[bone * 3 + 1];
    }

    public float pivotZ(int bone) {
        return this.bonePivots[bone * 3 + 2];
    }

    public float restRotationX(int bone) {
        return this.boneRestRotation[bone * 3];
    }

    public float restRotationY(int bone) {
        return this.boneRestRotation[bone * 3 + 1];
    }

    public float restRotationZ(int bone) {
        return this.boneRestRotation[bone * 3 + 2];
    }

    public float[] meshOf(int bone) {
        return this.boneMeshes[bone];
    }

    public Map<String, Integer> getBoneIndex() {
        return this.boneIndex;
    }

    public MeleeClip clip(String name) {
        return (MeleeClip) this.clips.get(name);
    }

    public boolean hasClip(String name) {
        return this.clips.containsKey(name);
    }

    public String nameOf(int bone) {
        return this.boneNames[bone];
    }

    public int getRightHandBone() {
        return this.rightHandBone;
    }

    public int getLeftHandBone() {
        return this.leftHandBone;
    }

    public int getCameraBone() {
        return this.cameraBone;
    }

    public int getViewAnchorBone() {
        return this.viewAnchorBone;
    }

    /** Where the eye goes to look down a gun's sight, or -1. */
    public int getAimViewBone() {
        return this.aimViewBone;
    }

    /** The point a gun's muzzle flash starts from, or -1. */
    public int getMuzzleBone() {
        return this.muzzleBone;
    }

    /** The point of a gun that sits in the fist in third person, or -1. */
    public int getThirdPersonGripBone() {
        return this.thirdPersonGripBone;
    }

    public void markHidden(List<String> bones, boolean[] marked) {
        this.markBranches(bones, marked);
    }

    public boolean markBranches(List<String> bones, boolean[] marked) {
        Arrays.fill(marked, false);
        if (bones.isEmpty()) {
            return false;
        } else {
            boolean found = false;

            for (String name : bones) {
                Integer bone = this.boneIndex.get(name);
                if (bone != null) {
                    marked[bone] = true;
                    found = true;
                }
            }

            if (!found) {
                return false;
            } else {
                for (int i = 0; i < marked.length; i++) {
                    int parent = this.boneParents[i];
                    if (parent >= 0 && marked[parent]) {
                        marked[i] = true;
                    }
                }

                return true;
            }
        }
    }

    static MeleeSkeleton bake(GeoParts parts, String animationJson) {
        List<GeoPart> bones = new ArrayList<>();

        for (GeoPart root : parts.roots) {
            collect(root, bones);
        }

        int count = bones.size();
        String[] names = new String[count];
        int[] parents = new int[count];
        float[] pivots = new float[count * 3];
        float[] restRotation = new float[count * 3];
        float[][] meshes = new float[count][];
        Map<String, Integer> index = new HashMap(count);

        for (int i = 0; i < count; i++) {
            index.put(((GeoPart) bones.get(i)).name, i);
        }

        for (int i = 0; i < count; i++) {
            GeoPart bone = (GeoPart) bones.get(i);
            names[i] = bone.name;
            parents[i] = bone.parent == null ? -1 : (Integer) index.getOrDefault(bone.parent.name, -1);
            pivots[i * 3] = bone.pivotX;
            pivots[i * 3 + 1] = bone.pivotY;
            pivots[i * 3 + 2] = bone.pivotZ;
            restRotation[i * 3] = bone.getRotX();
            restRotation[i * 3 + 1] = bone.getRotY();
            restRotation[i * 3 + 2] = bone.getRotZ();
            meshes[i] = bakeMesh(bone);
        }

        Map<String, MeleeClip> clips = MeleeAnimationParser.parse(animationJson, index);
        return new MeleeSkeleton(names, parents, pivots, restRotation, meshes, Map.copyOf(index), clips);
    }

    private static void collect(GeoPart bone, List<GeoPart> out) {
        out.add(bone);

        for (GeoPart child : bone.children) {
            collect(child, out);
        }
    }

    private static float[] bakeMesh(GeoPart bone) {
        int vertices = 0;

        for (GeoModel cube : bone.cubes) {
            for (GeoQuad face : cube.faces) {
                if (face != null) {
                    vertices += 4;
                }
            }
        }

        if (vertices == 0) {
            return null;
        } else {
            float[] mesh = new float[vertices * 8];
            Quaternionf rotation = new Quaternionf();
            Vector3f v = new Vector3f();
            int at = 0;

            for (GeoModel cube : bone.cubes) {
                rotation.rotationZ(cube.rotation.getZ())
                        .rotateY(cube.rotation.getY())
                        .rotateX(cube.rotation.getX());
                float px = cube.pivot.getX() / 16.0F;
                float py = cube.pivot.getY() / 16.0F;
                float pz = cube.pivot.getZ() / 16.0F;

                for (GeoQuad face : cube.faces) {
                    if (face != null) {
                        v.set(normalX(cube, face), normalY(cube, face), normalZ(cube, face));
                        rotation.transform(v);
                        float nx = v.x;
                        float ny = v.y;
                        float nz = v.z;

                        for (GeoVertex corner : face.corners) {
                            v.set(
                                    corner.position.getX() - px,
                                    corner.position.getY() - py,
                                    corner.position.getZ() - pz);
                            rotation.transform(v);
                            mesh[at] = v.x + px;
                            mesh[at + 1] = v.y + py;
                            mesh[at + 2] = v.z + pz;
                            mesh[at + 3] = corner.u;
                            mesh[at + 4] = corner.v;
                            mesh[at + 5] = nx;
                            mesh[at + 6] = ny;
                            mesh[at + 7] = nz;
                            at += 8;
                        }
                    }
                }
            }

            return mesh;
        }
    }

    private static float normalX(GeoModel cube, GeoQuad face) {
        float n = face.normal.getX();
        boolean flat = cube.size.getY() == 0.0F || cube.size.getZ() == 0.0F;
        return flat && n < 0.0F ? -n : n;
    }

    private static float normalY(GeoModel cube, GeoQuad face) {
        float n = face.normal.getY();
        boolean flat = cube.size.getX() == 0.0F || cube.size.getZ() == 0.0F;
        return flat && n < 0.0F ? -n : n;
    }

    private static float normalZ(GeoModel cube, GeoQuad face) {
        float n = face.normal.getZ();
        boolean flat = cube.size.getX() == 0.0F || cube.size.getY() == 0.0F;
        return flat && n < 0.0F ? -n : n;
    }
}
