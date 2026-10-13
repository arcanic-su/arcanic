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

