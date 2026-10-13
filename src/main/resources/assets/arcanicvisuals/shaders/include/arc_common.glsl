// common.glsl
// helper functions and constants for client shaders

/**
 * Computes the Signed Distance Function (SDF) for a rectangle with the ability
 * to set individual corner radii.
 *
 * @param CenterPosition - point coordinate relative to the rectangle center.
 * @param Size - half the width and height of the rectangle (i.e. from center to edge).
 * @param Radius - corner radii in the following order:
 *                 (top-left, top-right, bottom-right, bottom-left).
 *
 * @return float - distance from the point to the nearest rectangle surface.
 *                 Negative value means inside, positive means outside.
 */
float roundedBoxSDF(vec2 CenterPosition, vec2 Size, vec4 Radius) {
    // Clamp radii so they do not exceed half the rectangle sides.
    Radius = min(Radius, vec4(min(Size.x, Size.y)));

    // Pick the radius depending on the quadrant the point is in.
    Radius.xy = (CenterPosition.x > 0.0) ? Radius.xy : Radius.zw;
    Radius.x  = (CenterPosition.y > 0.0) ? Radius.x  : Radius.y;

    // Offset coordinates by radius and compute distance.
    vec2 q = abs(CenterPosition) - Size + Radius.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - Radius.x;
}

/**
 * Quad vertices helper.
 *
 * Vertices go clockwise:
 * 0 - top-left (0, 0)
 * 1 - bottom-left (0, 1)
 * 2 - bottom-right (1, 1)
 * 3 - top-right (1, 0)
 */
const vec2[4] RECT_VERTICES_COORDS = vec2[] (
    vec2(0.0, 0.0),
    vec2(0.0, 1.0),
    vec2(1.0, 1.0),
    vec2(1.0, 0.0)
);

vec2 rvertexcoord(int id) {
    return RECT_VERTICES_COORDS[id % 4];
}

float rdist(vec2 pos, vec2 size, vec4 radius) {
    radius = min(radius, vec4(min(size.x, size.y)));
    radius.xy = (pos.x > 0.0) ? radius.xy : radius.wz;
    radius.x  = (pos.y > 0.0) ? radius.x : radius.y;

    vec2 v = abs(pos) - size + radius.x;
    return min(max(v.x, v.y), 0.0) + length(max(v, 0.0)) - radius.x;
}

float ralpha(vec2 size, vec2 coord, vec4 radius, float smoothness) {
    vec2 center = size * 0.5;
    float dist = rdist(center - (coord * size), center, radius);
    return 1.0 - smoothstep(-smoothness, 0.0, dist);
}

/**
 * Alpha falloff for one mask edge.
 *
 * @param distance - signed distance from the edge, positive inside the kept region.
 * @param fade     - width of the falloff band in pixels; 0 means a hard cut.
 */
float maskEdgeFactor(float distance, float fade) {
    if (fade <= 0.0) {
        return distance < 0.0 ? 0.0 : 1.0;
    }
    float t = clamp(distance / fade, 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

/**
 * Rectangular alpha mask with an independent falloff per edge.
 * Modifies ONLY alpha - never the RGB channels, so this can never introduce a
 * black vignette.
 *
 * The fragment coordinate is passed in rather than read from gl_FragCoord so this
 * header stays compilable from vertex shaders, which also import common.glsl.
 *
 * @param alpha     - incoming fragment alpha.
 * @param fragCoord - gl_FragCoord.xy of the caller.
 * @param bounds    - (topY, bottomY, leftX, rightX) in gl_FragCoord space. Both Y
 *                    entries negative disables the mask entirely.
 * @param fades     - (fadeTop, fadeBottom, fadeLeft, fadeRight) band widths in pixels,
 *                    matching the screen orientation of the bounds.
 */
float applyEdgeMask(float alpha, vec2 fragCoord, vec4 bounds, vec4 fades) {
    if (bounds.x < 0.0 && bounds.y < 0.0) {
        return alpha;
    }

    float edge = 1.0;

    if (bounds.x >= 0.0 || bounds.y >= 0.0) {
        float lowY = min(bounds.x, bounds.y);
        float highY = max(bounds.x, bounds.y);
        edge *= maskEdgeFactor(fragCoord.y - lowY, fades.y);
        edge *= maskEdgeFactor(highY - fragCoord.y, fades.x);
    }

    if (bounds.z >= 0.0 || bounds.w >= 0.0) {
        float lowX = min(bounds.z, bounds.w);
        float highX = max(bounds.z, bounds.w);
        edge *= maskEdgeFactor(fragCoord.x - lowX, fades.z);
        edge *= maskEdgeFactor(highX - fragCoord.x, fades.w);
    }

    return alpha * edge;
}

float hash12(vec2 p) {
    vec3 p3  = fract(vec3(p.xyx) * .1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float common_noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = p - i;
    vec2 u = f*f*(3.0-2.0*f);
    return mix(mix(hash12(i + vec2(0.0,0.0)), hash12(i + vec2(1.0,0.0)), u.x),
               mix(hash12(i + vec2(0.0,1.0)), hash12(i + vec2(1.0,1.0)), u.x), u.y);
}

float fbm(vec2 x) {
    float v = 0.0;
    float a = 0.5;
    vec2 shift = vec2(100.0);
    mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.5));
    for (int i = 0; i < 5; ++i) {
        v += a * common_noise(x);
        x = rot * x * 2.0 + shift;
        a *= 0.5;
    }
    return v;
}

const int LOOP_OCTAVES = 4;
const float LOOP_LACUNARITY = 2.0;
const float LOOP_GAIN = 0.5;
const vec2 LOOP_OCTAVE_SHIFT = vec2(37.1, 11.7);

/**
 * Value noise whose lattice wraps every `tile` cells, so translating the sample
 * point by a whole multiple of `tile` reproduces the exact same field. This is
 * what lets an animated background loop without a visible seam.
 */
float loopNoise(vec2 p, float tile) {
    vec2 i = floor(p);
    vec2 f = p - i;
    vec2 u = f * f * (3.0 - 2.0 * f);
    vec2 low = mod(i, tile);
    vec2 high = mod(i + 1.0, tile);
    return mix(mix(hash12(low), hash12(vec2(high.x, low.y)), u.x),
               mix(hash12(vec2(low.x, high.y)), hash12(high), u.x), u.y);
}

/**
 * Fractal brownian motion built on loopNoise; every octave wraps on its own
 * period so the whole sum stays seamless over `tile` units.
 */
float loopFbm(vec2 p, float tile) {
    float total = 0.0;
    float amplitude = LOOP_GAIN;
    float period = tile;
    for (int i = 0; i < LOOP_OCTAVES; ++i) {
        total += amplitude * loopNoise(p, period);
        p = p * LOOP_LACUNARITY + LOOP_OCTAVE_SHIFT;
        period *= LOOP_LACUNARITY;
        amplitude *= LOOP_GAIN;
    }
    return total;
}

// 3D Hash
const uint HASH_LCG_MULTIPLIER = 1664525u;
const uint HASH_LCG_INCREMENT = 1013904223u;
const uint HASH_MIX_SHIFT = 16u;
const int HASH_LATTICE_BIAS = 1048576;
const float HASH_UINT_TO_UNIT = 1.0 / 4294967296.0;

float hash13(vec3 p3) {
    uvec3 v = uvec3(ivec3(floor(p3)) + HASH_LATTICE_BIAS);
    v = v * HASH_LCG_MULTIPLIER + HASH_LCG_INCREMENT;
    v.x += v.y * v.z;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    v ^= v >> HASH_MIX_SHIFT;
    v.x += v.y * v.z;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    return float(v.x) * HASH_UINT_TO_UNIT;
}

// 3D Value Noise
vec3 quinticFade(vec3 f) {
    return f*f*f*(f*(f*6.0-15.0)+10.0);
}

float common_noise3d(vec3 p) {
    vec3 i = floor(p);
    vec3 f = p - i;
    vec3 u = quinticFade(f);

    float n000 = hash13(i + vec3(0.0,0.0,0.0));
    float n100 = hash13(i + vec3(1.0,0.0,0.0));
    float n010 = hash13(i + vec3(0.0,1.0,0.0));
    float n110 = hash13(i + vec3(1.0,1.0,0.0));
    float n001 = hash13(i + vec3(0.0,0.0,1.0));
    float n101 = hash13(i + vec3(1.0,0.0,1.0));
    float n011 = hash13(i + vec3(0.0,1.0,1.0));
    float n111 = hash13(i + vec3(1.0,1.0,1.0));

    float mixX00 = mix(n000, n100, u.x);
    float mixX10 = mix(n010, n110, u.x);
    float mixX01 = mix(n001, n101, u.x);
    float mixX11 = mix(n011, n111, u.x);

    float mixY0 = mix(mixX00, mixX10, u.y);
    float mixY1 = mix(mixX01, mixX11, u.y);

    return mix(mixY0, mixY1, u.z);
}

// 3D FBM
float fbm3d(vec3 x) {
    float v = 0.0;
    float a = 0.5;
    vec3 shift = vec3(100.0);
    // Simple 3D rotation matrix to avoid alignment artifacts
    mat3 rot = mat3(
        0.36,  0.48, -0.80,
       -0.80,  0.60,  0.00,
        0.48,  0.64,  0.60
    );
    for (int i = 0; i < 3; ++i) {
        v += a * common_noise3d(x);
        x = rot * x * 2.0 + shift;
        a *= 0.5;
    }
    return v;
}

// 3D to 3D Hash
vec3 hash33(vec3 p3) {
    p3 = fract(p3 * vec3(.1031, .1030, .0973));
    p3 += dot(p3, p3.yxz+33.33);
    return fract((p3.xxy + p3.yxx)*p3.zyx);
}

// 3D Cellular/Voronoi Noise
// Returns vec2(min_dist, id)
vec2 cellular3d(vec3 p, float t) {
    vec3 n = floor(p);
    vec3 f = p - n;
    float min_dist = 10.0;
    float id = 0.0;
    
    for(int k=-1; k<=1; k++) {
        for(int j=-1; j<=1; j++) {
            for(int i=-1; i<=1; i++) {
                vec3 g = vec3(float(i),float(j),float(k));
                vec3 o = hash33(n + g);
                // Animate the points
                o = 0.5 + 0.5 * sin(t + 6.2831 * o);
                vec3 r = g + o - f;
                float d = dot(r,r);
                if(d < min_dist) {
                    min_dist = d;
                    id = hash13(n + g);
                }
            }
        }
    }
    return vec2(sqrt(min_dist), id);
}
