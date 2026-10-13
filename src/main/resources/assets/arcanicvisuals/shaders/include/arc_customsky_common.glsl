layout(std140) uniform SkyParams {
    vec4 cornerRays[4];
    vec4 misc;
    vec4 skyColor;
    vec4 skyColor2;
    vec4 taa;
    mat4 prevViewProj;
    vec4 skyExtra;
};

const float SKY_GRADIENT_DUAL = 0.5;
const float SKY_HDR_CEILING = 0.9975;
const float SKY_HDR_FLOOR = 0.0055;
const float SKY_GAMMA = 2.2;
const float SKY_DOME_FADE_LOW = -0.04;
const float SKY_DOME_FADE_HIGH = 0.16;
const float SKY_NADIR_DIM = 0.62;
const float SKY_NADIR_SPAN = 0.85;

const float SKY_LIGHT_RATE = 0.021;
const float SKY_LIGHT_HEIGHT = 0.17;

const float SKY_SEA_RISE = 0.34;
const float SKY_SEA_SCALE = 1.5;
const float SKY_SEA_DRIFT_X = 0.010;
const float SKY_SEA_DRIFT_Z = 0.006;
const float SKY_SEA_SLAB_TOP = 1.02;
const float SKY_SEA_SLAB_BOTTOM = 1.42;
const float SKY_SEA_SLAB_MID = 1.22;
const int SKY_SEA_STEPS_MAX = 10;
const float SKY_SEA_STEPS_MIN = 5.0;
const float SKY_SEA_STEEP_LOW = 0.03;
const float SKY_SEA_STEEP_HIGH = 0.55;
const float SKY_SEA_MAX_STEP = 2.6;
const float SKY_SEA_JITTER = 0.8;
const float SKY_SEA_DITHER_SCALE = 2048.0;
const float SKY_SEA_ABSORB = 6.0;
const float SKY_SEA_LIGHT_ABSORB = 3.4;
const float SKY_SEA_SELF_ABSORB = 0.9;
const float SKY_SEA_LIGHT_STEP = 0.42;
const float SKY_SEA_LIGHT_RISE = 0.18;
const float SKY_SEA_SHEAR = 0.5;
const vec2 SKY_SEA_SHEAR_DIR = vec2(0.857, 0.514);
const float SKY_SEA_CUTOFF = 0.02;
const float SKY_SEA_DENSITY_EPSILON = 0.02;
const float SKY_SEA_COVER_LOW = 0.36;
const float SKY_SEA_COVER_SPAN = 0.16;
const float SKY_SEA_TOP_ERODE = 0.28;
const float SKY_SEA_TOP_SOFT = 0.30;
const float SKY_SEA_BASE_SOFT = 0.86;
const float SKY_SEA_DETAIL_BLEND = 0.72;
const float SKY_SEA_HAZE_RANGE = 7.0;
const float SKY_SEA_HAZE_GAIN = 0.60;
const float SKY_SEA_DEPTH_SPAN = 0.66;
const float SKY_SEA_DEEP = 0.16;
const float SKY_SEA_CREST_TINT = 0.55;
const float SKY_SEA_SHADOW_TINT = 0.10;
const float SKY_SEA_RIM_SPAN = 0.16;
const float SKY_SEA_RIM_GAIN = 0.45;
const float SKY_SEA_GLINT_TIGHT = 90.0;
const float SKY_SEA_GLINT_GAIN = 0.55;
const int SKY_SEA_OCTAVES = 6;
const int SKY_SEA_COARSE_OCTAVES = 3;
const float SKY_SEA_MARCH_LOD = 3.0;
const float SKY_SEA_LIGHT_LOD = 2.0;
const float SKY_SEA_LACUNARITY = 2.03;
const float SKY_SEA_GAIN = 0.5;
const float SKY_SEA_MEAN = 0.5;
const vec2 SKY_SEA_OCTAVE_SHIFT = vec2(37.1, 11.7);
const mat2 SKY_SEA_ROTATION = mat2(0.8776, 0.4794, -0.4794, 0.8776);
const float SKY_SEA_LOD_BIAS = 1.0;
const float SKY_SEA_LOD_FLOOR = 1.0;
const float SKY_SEA_LOD_EPSILON = 1.0e-5;
const float SKY_SEA_AXIS_EPSILON = 1.0e-4;
const uint SKY_SEA_HASH_X = 1664525u;
const uint SKY_SEA_HASH_Y = 1013904223u;
const uint SKY_SEA_HASH_MIX_A = 0x7feb352du;
const uint SKY_SEA_HASH_MIX_B = 0x846ca68bu;
const uint SKY_SEA_HASH_SHIFT_A = 15u;
const uint SKY_SEA_HASH_SHIFT_B = 16u;
const int SKY_SEA_HASH_BIAS = 1048576;
const float SKY_SEA_HASH_NORMALIZE = 1.0 / 4294967296.0;
const float SKY_PLANE_EPSILON = 0.0022;
const float SKY_PLANE_RANGE = 7.5;
const float SKY_PLANE_COMPRESS = 6.0;
const float SKY_PLANE_TWIST = 0.045;

vec3 skyRayDirection(vec2 uv) {
    vec3 bottom = mix(cornerRays[1].xyz, cornerRays[2].xyz, uv.x);
    vec3 top = mix(cornerRays[0].xyz, cornerRays[3].xyz, uv.x);
    return normalize(mix(bottom, top, uv.y));
}

float skyDown(float dirY) {
    return max(-dirY, 0.0) * skyExtra.y;
}

float skyVerticalAxis(float dirY) {
    return mix(dirY, dirY * 0.5 + 0.5, skyExtra.y);
}

vec2 skyPlaneDomain(vec2 groundUv) {
    float radius = length(groundUv);
    if (radius <= SKY_PLANE_EPSILON) {
        return groundUv;
    }
    float compressed = SKY_PLANE_COMPRESS * log(1.0 + radius / SKY_PLANE_COMPRESS);
    float twist = compressed * SKY_PLANE_TWIST;
    float sinTwist = sin(twist);
    float cosTwist = cos(twist);
    vec2 unit = groundUv / radius;
    return vec2(unit.x * cosTwist - unit.y * sinTwist,
                unit.x * sinTwist + unit.y * cosTwist) * compressed;
}

vec3 skyPlane(vec3 dir, float height) {
    vec2 ground = dir.xz * (height / max(abs(dir.y), SKY_PLANE_EPSILON));
    return vec3(skyPlaneDomain(ground), length(ground));
}

float skyPlaneFalloff(float distance, float range) {
    return exp(-distance / range);
}

float skyDomeFade(float dirY) {
    return mix(1.0, smoothstep(SKY_DOME_FADE_LOW, SKY_DOME_FADE_HIGH, dirY), skyExtra.y);
}

float skySeaMask(float dirY) {
    return smoothstep(0.0, SKY_SEA_RISE, skyDown(dirY));
}

vec3 skyDecodeHDR(vec3 c) {
    c = pow(min(c, vec3(SKY_HDR_CEILING)), vec3(SKY_GAMMA));
    return c / max(1.0 - c, vec3(SKY_HDR_FLOOR));
}

vec3 skyEncodeHDR(vec3 c) {
    c = max(c, vec3(0.0));
    return pow(c / (1.0 + c), vec3(1.0 / SKY_GAMMA));
}

vec3 skyHash33(vec3 q) {
    uvec3 p = uvec3(ivec3(q));
    p = p * uvec3(374761393U, 1103515245U, 668265263U) + p.zxy + p.yzx;
    p = p.yzx * (p.zxy ^ (p >> 3U));
    return vec3(p ^ (p >> 16U)) * (1.0 / vec3(0xffffffffU));
}

float skyHash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float skyHalo(vec3 dir, vec3 light, float tightness) {
    return pow(max(dot(dir, light), 0.0), tightness);
}

vec3 skyLightDir(float time) {
    float angle = time * SKY_LIGHT_RATE;
    return normalize(vec3(cos(angle), SKY_LIGHT_HEIGHT, sin(angle)));
}

float skySeaHash(vec2 lattice) {
    uvec2 cell = uvec2(ivec2(lattice) + SKY_SEA_HASH_BIAS);
    uint h = cell.x * SKY_SEA_HASH_X + cell.y * SKY_SEA_HASH_Y;
    h ^= h >> SKY_SEA_HASH_SHIFT_B;
    h *= SKY_SEA_HASH_MIX_A;
    h ^= h >> SKY_SEA_HASH_SHIFT_A;
    h *= SKY_SEA_HASH_MIX_B;
    h ^= h >> SKY_SEA_HASH_SHIFT_B;
    return float(h) * SKY_SEA_HASH_NORMALIZE;
}

float skySeaNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = p - i;
    vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    return mix(mix(skySeaHash(i), skySeaHash(i + vec2(1.0, 0.0)), u.x),
               mix(skySeaHash(i + vec2(0.0, 1.0)), skySeaHash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float skySeaLod(vec2 domain, float scale) {
    float footprint = length(fwidth(domain)) * scale;
    float octaves = footprint > SKY_SEA_LOD_EPSILON
        ? -log2(footprint) - SKY_SEA_LOD_BIAS
        : float(SKY_SEA_OCTAVES);
    return clamp(octaves, SKY_SEA_LOD_FLOOR, float(SKY_SEA_OCTAVES));
}

vec2 skySeaField(vec2 p, float lod) {
    float coarse = 0.0;
    float total = 0.0;
    float amplitude = SKY_SEA_GAIN;
    for (int octave = 0; octave < SKY_SEA_OCTAVES; octave++) {
        float weight = clamp(lod - float(octave), 0.0, 1.0);
        if (weight <= 0.0) {
            break;
        }
        float band = amplitude * weight * (skySeaNoise(p) - SKY_SEA_MEAN);
        total += band;
        if (octave < SKY_SEA_COARSE_OCTAVES) {
            coarse += band;
        }
        p = SKY_SEA_ROTATION * p * SKY_SEA_LACUNARITY + SKY_SEA_OCTAVE_SHIFT;
        amplitude *= SKY_SEA_GAIN;
    }
    return vec2(SKY_SEA_MEAN + coarse, SKY_SEA_MEAN + total);
}

vec2 skySeaLightOffset(vec3 light) {
    float span = length(light.xz);
    if (span <= SKY_SEA_AXIS_EPSILON) {
        return vec2(SKY_SEA_LIGHT_STEP, 0.0);
    }
    return light.xz / span * SKY_SEA_LIGHT_STEP;
}

float skySeaDensity(vec2 domain, float level, float lod) {
    vec2 field = skySeaField(domain, lod);
    float value = mix(field.x, field.y, SKY_SEA_DETAIL_BLEND);
    float rise = 1.0 - level;
    float threshold = SKY_SEA_COVER_LOW + SKY_SEA_TOP_ERODE * rise * rise;
    float profile = smoothstep(0.0, SKY_SEA_TOP_SOFT, level)
        * smoothstep(1.0, SKY_SEA_BASE_SOFT, level);
    return smoothstep(threshold, threshold + SKY_SEA_COVER_SPAN, value) * profile;
}

vec3 skySea(vec3 dir, float time, vec3 horizonTone, vec3 accent) {
    vec2 drift = vec2(time * SKY_SEA_DRIFT_X, time * SKY_SEA_DRIFT_Z);
    float down = max(-dir.y, SKY_PLANE_EPSILON);
    vec3 midPlane = skyPlane(dir, SKY_SEA_SLAB_MID);
    float midLod = skySeaLod(midPlane.xy, SKY_SEA_SCALE);

    vec3 light = skyLightDir(time);
    vec2 lightOffset = skySeaLightOffset(light);

    int steps = max(int(mix(SKY_SEA_STEPS_MIN, float(SKY_SEA_STEPS_MAX),
        smoothstep(SKY_SEA_STEEP_LOW, SKY_SEA_STEEP_HIGH, down))), 1);
    float slab = SKY_SEA_SLAB_BOTTOM - SKY_SEA_SLAB_TOP;
    float rayStep = min(slab / (down * float(steps)), SKY_SEA_MAX_STEP);
    float dither = skySeaHash(floor(midPlane.xy * SKY_SEA_DITHER_SCALE));

    vec3 crest = mix(horizonTone, accent, SKY_SEA_CREST_TINT);
    vec3 deep = mix(horizonTone, horizonTone * SKY_SEA_DEEP,
        smoothstep(0.0, SKY_SEA_DEPTH_SPAN, down));
    vec3 shadowTone = mix(deep, crest, SKY_SEA_SHADOW_TINT);

    vec3 scattered = vec3(0.0);
    float transmittance = 1.0;
    float above = 0.0;
    for (int layer = 0; layer < SKY_SEA_STEPS_MAX; layer++) {
        if (layer >= steps || transmittance < SKY_SEA_CUTOFF) {
            break;
        }
        float level = clamp((float(layer) + 0.5 + (dither - 0.5) * SKY_SEA_JITTER)
            / float(steps), 0.0, 1.0);
        float height = SKY_SEA_SLAB_TOP + slab * level;
        vec2 domain = skyPlaneDomain(dir.xz * (height / down)) * SKY_SEA_SCALE
            + drift + SKY_SEA_SHEAR_DIR * (height * SKY_SEA_SHEAR);
        float lod = clamp(midLod - log2(height / SKY_SEA_SLAB_MID),
            SKY_SEA_LOD_FLOOR, SKY_SEA_MARCH_LOD);
        float density = skySeaDensity(domain, level, lod);
        if (density > SKY_SEA_DENSITY_EPSILON) {
            float toward = skySeaDensity(domain + lightOffset,
                max(level - SKY_SEA_LIGHT_RISE, 0.0), min(lod, SKY_SEA_LIGHT_LOD));
            float sun = exp(-(toward * SKY_SEA_LIGHT_ABSORB + above * SKY_SEA_SELF_ABSORB));
            float alpha = 1.0 - exp(-density * SKY_SEA_ABSORB * rayStep);
            scattered += transmittance * alpha * mix(shadowTone, crest, sun);
            transmittance -= transmittance * alpha;
            above += density * rayStep;
        }
    }

    vec3 sea = scattered + transmittance * deep;
    sea += accent * skyHalo(dir, vec3(light.x, -light.y, light.z), SKY_SEA_GLINT_TIGHT)
        * SKY_SEA_GLINT_GAIN * (1.0 - transmittance);

    float haze = 1.0 - skyPlaneFalloff(midPlane.z, SKY_SEA_HAZE_RANGE);
    sea = mix(sea, horizonTone, haze * SKY_SEA_HAZE_GAIN);

    float rim = 1.0 - smoothstep(0.0, SKY_SEA_RIM_SPAN, down);
    return mix(sea, crest, rim * SKY_SEA_RIM_GAIN);
}

float skyNadirDepth(float dirY) {
    return mix(1.0, SKY_NADIR_DIM, smoothstep(0.0, SKY_NADIR_SPAN, skyDown(dirY)));
}

float skyBand(vec3 dir, vec3 axis, float width) {
    float offset = dot(dir, axis) / width;
    return exp(-offset * offset);
}

vec3 skyTint(float axis, float t, float time) {
    vec3 primary = skyColor.rgb;
    if (skyColor.w < SKY_GRADIENT_DUAL) {
        return primary;
    }
    float k = clamp(0.5 + 0.5 * sin(axis * 2.0 + time * 0.15) + (t - 0.5) * 0.7, 0.0, 1.0);
    return mix(primary, skyColor2.rgb, k);
}

vec3 skyStarField(vec3 dir, float time, float density, float twinkle) {
    vec3 accum = vec3(0.0);
    vec3 p = dir * 42.0;
    float dens = density;
    float amp = 1.0;
    for (int i = 0; i < 4; i++) {
        vec3 id = floor(p);
        vec3 q = fract(p) - 0.5;
        vec3 rn = skyHash33(id);
        float core = smoothstep(0.32, 0.0, length(q));
        float hit = step(rn.x, dens);
        float tw = 1.0 - twinkle + twinkle * (0.55 + 0.45 * sin(time * (1.0 + rn.z * 2.2) + rn.y * 47.0));
        vec3 tint = mix(vec3(1.0, 0.74, 0.50), vec3(0.72, 0.86, 1.0), rn.y);
        accum += hit * core * core * tint * (0.35 + 0.65 * rn.z) * tw * amp;
        p = p * 1.63 + 17.0;
        dens *= 0.62;
        amp *= 0.78;
    }
    return accum;
}
