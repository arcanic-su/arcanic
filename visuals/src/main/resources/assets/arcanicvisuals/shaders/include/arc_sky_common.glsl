
const float SKY_HORIZON_FADE = 0.2;
const float SKY_DOME_FADE_LOW = -0.04;
const float SKY_DOME_FADE_HIGH = 0.16;
const float SKY_NADIR_DIM = 0.62;
const float SKY_NADIR_SPAN = 0.85;

const float SKY_HAZE_SPAN = 0.42;

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
const float SKY_PLANE_DETAIL_RANGE = 2.4;
const float SKY_PLANE_COMPRESS = 6.0;
const float SKY_PLANE_TWIST = 0.045;
const float SKY_GLOW_TIGHT = 3.0;
const float SKY_GLOW_GAIN = 0.55;

const float SKY_LIGHT_RATE = 0.021;
const float SKY_LIGHT_HEIGHT = 0.17;
const float SKY_RAMP_MID = 0.30;
const vec3 SKY_STAR_WARM = vec3(1.00, 0.78, 0.55);
const vec3 SKY_STAR_COOL = vec3(0.66, 0.80, 1.00);
const float SKY_STAR_HUE_STEP = 91.7;
const float SKY_STAR_TWINKLE_BASE = 0.62;
const float SKY_STAR_TWINKLE_GAIN = 0.38;
const float SKY_STAR_TWINKLE_RATE = 5.0;
const float SKY_STAR_TWINKLE_PHASE = 43.0;
const float SKY_STAR_SIZE_FLOOR = 0.30;
const float SKY_STAR_JITTER = 0.24;
const float SKY_STAR_JITTER_SEED = 7.13;
const float SKY_STAR_REACH = 0.26;
const float SKY_STAR_CORE_GAIN = 3.2;
const float SKY_STAR_HALO_GAIN = 0.42;

const vec3 SKY_LUMA = vec3(0.2126, 0.7152, 0.0722);
const float SKY_EXPOSURE = 2.0;
const float SKY_CONTRAST_BLEND = 0.22;
const float SKY_SATURATION = 1.14;

const float SKY_DITHER_AMPLITUDE = 2.0 / 255.0;
const vec3 SKY_DITHER_HASH_SCALE = vec3(0.1031, 0.1030, 0.0973);
const float SKY_DITHER_HASH_OFFSET = 33.33;
const vec3 SKY_DITHER_HASH_SCALE_B = vec3(0.0973, 0.1099, 0.1031);
const float SKY_DITHER_HASH_OFFSET_B = 19.19;

float skyHorizonBlend(float dirY) {
    return smoothstep(0.0, SKY_HORIZON_FADE, max(dirY, 0.0));
}

float skyDown(float dirY, float fullDome) {
    return max(-dirY, 0.0) * fullDome;
}

float skyVerticalAxis(float dirY, float fullDome) {
    return mix(dirY, dirY * 0.5 + 0.5, fullDome);
}

float skyDomeFade(float dirY, float fullDome) {
    return mix(1.0, smoothstep(SKY_DOME_FADE_LOW, SKY_DOME_FADE_HIGH, dirY), fullDome);
}

vec3 skySeaTone(vec3 primary, vec3 secondary, float scale) {
    return mix(primary, secondary, 0.5) * scale;
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

vec3 skyTonemap(vec3 color) {
    color = max(color, vec3(0.0));
    float luma = dot(color, SKY_LUMA);
    vec3 perChannel = color / (1.0 + color);
    vec3 preserved = color / (1.0 + luma);
    return mix(preserved, perChannel, perChannel);
}

vec3 skyGrade(vec3 color) {
    float luma = dot(color, SKY_LUMA);
    color = clamp(mix(vec3(luma), color, SKY_SATURATION), 0.0, 1.0);
    vec3 curved = color * color * (3.0 - 2.0 * color);
    return mix(color, curved, SKY_CONTRAST_BLEND);
}

vec3 skyFinish(vec3 color, float exposure) {
    return skyGrade(skyTonemap(color * exposure));
}

vec3 skyLightDir(float time) {
    float angle = time * SKY_LIGHT_RATE;
    return normalize(vec3(cos(angle), SKY_LIGHT_HEIGHT, sin(angle)));
}

float skyHalo(vec3 dir, vec3 light, float tightness) {
    return pow(max(dot(dir, light), 0.0), tightness);
}

vec3 skyRamp(float dirY, vec3 horizon, vec3 mid, vec3 zenith) {
    float up = clamp(dirY, 0.0, 1.0);
    return mix(mix(horizon, mid, smoothstep(0.0, SKY_RAMP_MID, up)),
               zenith, smoothstep(SKY_RAMP_MID, 1.0, up));
}

vec3 skyHorizonGlow(vec3 dir, vec3 light, vec3 baseTone, vec3 glowTone) {
    return mix(baseTone, glowTone, skyHalo(dir, light, SKY_GLOW_TIGHT) * SKY_GLOW_GAIN);
}

float skyBand(vec3 dir, vec3 axis, float width) {
    float offset = dot(dir, axis) / width;
    return exp(-offset * offset);
}

vec3 skyStars(vec3 dir, float time, float density, float scale, float gain) {
    vec3 p = dir * scale;
    vec3 cell = floor(p);
    float pick = hash13(cell);
    float threshold = 1.0 - density;
    if (pick <= threshold) {
        return vec3(0.0);
    }
    float size = (pick - threshold) / max(density, 1.0e-4);
    vec3 jitter = (hash33(cell + SKY_STAR_JITTER_SEED) - 0.5) * SKY_STAR_JITTER;
    float distance = length(p - cell - 0.5 - jitter);

    float radius = SKY_STAR_REACH * (SKY_STAR_SIZE_FLOOR + size);
    float falloff = max(1.0 - distance / radius, 0.0);
    if (falloff <= 0.0) {
        return vec3(0.0);
    }
    float squared = falloff * falloff;
    float quad = squared * squared;
    float core = quad * quad;
    float halo = squared;

    float twinkle = SKY_STAR_TWINKLE_BASE + SKY_STAR_TWINKLE_GAIN
        * sin(time * (1.0 + pick * SKY_STAR_TWINKLE_RATE) + pick * SKY_STAR_TWINKLE_PHASE);
    vec3 tint = mix(SKY_STAR_WARM, SKY_STAR_COOL, fract(pick * SKY_STAR_HUE_STEP));

    float brightness = (core * SKY_STAR_CORE_GAIN + halo * SKY_STAR_HALO_GAIN)
        * twinkle * gain * (SKY_STAR_SIZE_FLOOR + size);
    return mix(tint, vec3(1.0), core * core) * brightness;
}

float skyDepthHaze(float dirY, float fullDome, float gain) {
    float edge = 1.0 - min(abs(dirY) / SKY_HAZE_SPAN, 1.0);
    return edge * edge * gain * fullDome;
}

float skySeaMask(float dirY, float fullDome) {
    return smoothstep(0.0, SKY_SEA_RISE, skyDown(dirY, fullDome));
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

float skyNadirDepth(float dirY, float fullDome) {
    return mix(1.0, SKY_NADIR_DIM, smoothstep(0.0, SKY_NADIR_SPAN, skyDown(dirY, fullDome)));
}

float skyEffectFade(float dirY, float fullDome) {
    return max(skyHorizonBlend(dirY), fullDome);
}

float skyDitherHash(vec2 fragCoord) {
    vec3 p = fract(fragCoord.xyx * SKY_DITHER_HASH_SCALE);
    p += dot(p, p.yzx + SKY_DITHER_HASH_OFFSET);
    return fract((p.x + p.y) * p.z);
}

float skyDitherHashB(vec2 fragCoord) {
    vec3 p = fract(fragCoord.yxy * SKY_DITHER_HASH_SCALE_B);
    p += dot(p, p.zxy + SKY_DITHER_HASH_OFFSET_B);
    return fract((p.x + p.z) * p.y);
}

vec3 skyDither(vec3 color, vec2 fragCoord) {
    float triangular = skyDitherHash(fragCoord) + skyDitherHashB(fragCoord) - 1.0;
    return color + triangular * SKY_DITHER_AMPLITUDE;
}

mat3 skyRotationY(float sinAngle, float cosAngle) {
    return mat3(
        cosAngle, 0.0, sinAngle,
        0.0, 1.0, 0.0,
        -sinAngle, 0.0, cosAngle
    );
}
