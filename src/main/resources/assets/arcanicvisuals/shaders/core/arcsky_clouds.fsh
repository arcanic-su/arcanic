#version 150

#moj_import <arcanicvisuals:arc_common.glsl>
#moj_import <arcanicvisuals:arc_sky_common.glsl>
#moj_import <arcanicvisuals:arc_sky_uniforms.glsl>

const float CLOUD_SLAB_BOTTOM = 1.05;
const float CLOUD_SLAB_TOP = 1.55;
const float CLOUD_SLAB_MID = 1.30;
const float CLOUD_SCALE = 2.4;
const int CLOUD_STEPS_MAX = 14;
const float CLOUD_STEPS_MIN = 7.0;
const float CLOUD_STEEP_LOW = 0.03;
const float CLOUD_STEEP_HIGH = 0.55;
const float CLOUD_MAX_STEP = 3.0;
const float CLOUD_JITTER = 0.5;
const float CLOUD_DITHER_SCALE = 2048.0;
const float CLOUD_DITHER_SEED = 0.5;
const float CLOUD_DRIFT = 0.014;
const float CLOUD_SHEAR = 0.45;

const float CLOUD_COVER_LOW = 0.38;
const float CLOUD_COVER_SPAN = 0.13;
const float CLOUD_TOP_ERODE = 0.26;
const float CLOUD_BASE_SOFT = 0.08;
const float CLOUD_TOP_SOFT = 0.80;

const float CLOUD_ABSORB = 9.0;
const float CLOUD_LIGHT_ABSORB = 5.0;
const float CLOUD_BASE_SHADE = 0.35;
const float CLOUD_LIGHT_STEP = 0.40;
const float CLOUD_LIGHT_RISE = 0.22;
const float CLOUD_DENSITY_EPSILON = 0.02;
const float CLOUD_CUTOFF = 0.02;

const float CLOUD_HAZE_RANGE = 8.0;
const float CLOUD_HAZE_GAIN = 0.55;
const float CLOUD_DEPTH_HAZE = 0.34;
const float CLOUD_SKY_LEVEL = 0.34;
const float CLOUD_SKY_MID = 0.58;
const float CLOUD_HALO_TIGHT = 5.5;
const float CLOUD_HALO_GAIN = 0.80;
const float CLOUD_SILVER_TIGHT = 22.0;
const float CLOUD_SILVER_GAIN = 2.60;
const float CLOUD_AMBIENT = 0.35;
const float CLOUD_SHADOW_TINT = 0.42;
const float CLOUD_SUN_TINT = 0.35;

in vec3 vRay;
in vec4 FragColor;

out vec4 fragColor;

float cloudDensity(vec2 ground, float level) {
    float value = fbm3d(vec3(ground.x, level * CLOUD_SHEAR, ground.y));
    float threshold = CLOUD_COVER_LOW + CLOUD_TOP_ERODE * level * level;
    float profile = smoothstep(0.0, CLOUD_BASE_SOFT, level)
        * smoothstep(1.0, CLOUD_TOP_SOFT, level);
    return smoothstep(threshold, threshold + CLOUD_COVER_SPAN, value) * profile;
}

void main() {
    vec3 dir = normalize(vRay);

    vec3 skyLow = BottomRightColor.rgb;
    vec3 skyHigh = mix(BottomRightColor.rgb, TopLeftColor.rgb, CLOUD_SKY_MID) * CLOUD_SKY_LEVEL;
    vec3 light = skyLightDir(Time);
    vec3 backdrop = skyRamp(dir.y, skyLow, mix(skyLow, skyHigh, 0.5), skyHigh);
    backdrop += TopLeftColor.rgb * skyHalo(dir, light, CLOUD_HALO_TIGHT) * CLOUD_HALO_GAIN;

    if (dir.y <= 0.0) {
        fragColor = vec4(skyLow, 1.0);
        return;
    }

    float up = max(dir.y, SKY_PLANE_EPSILON);
    float slab = CLOUD_SLAB_TOP - CLOUD_SLAB_BOTTOM;
    int steps = max(int(mix(CLOUD_STEPS_MIN, float(CLOUD_STEPS_MAX),
        smoothstep(CLOUD_STEEP_LOW, CLOUD_STEEP_HIGH, up))), 1);
    float rayStep = min(slab / (up * float(steps)), CLOUD_MAX_STEP);

    vec3 midPlane = skyPlane(dir, CLOUD_SLAB_MID);
    float dither = hash13(vec3(midPlane.xy * CLOUD_DITHER_SCALE, CLOUD_DITHER_SEED));

    vec3 sunTint = mix(TopLeftColor.rgb, vec3(1.0), CLOUD_SUN_TINT);
    vec3 shadowTint = mix(BottomRightColor.rgb, TopLeftColor.rgb, CLOUD_SHADOW_TINT);
    float drift = Time * CLOUD_DRIFT;

    float transmittance = 1.0;
    vec3 scattered = vec3(0.0);
    for (int layer = 0; layer < CLOUD_STEPS_MAX; layer++) {
        if (layer >= steps || transmittance < CLOUD_CUTOFF) {
            break;
        }
        float level = clamp((float(layer) + 0.5 + (dither - 0.5) * CLOUD_JITTER)
            / float(steps), 0.0, 1.0);
        float height = CLOUD_SLAB_BOTTOM + slab * level;
        vec2 ground = skyPlaneDomain(dir.xz * (height / up)) * CLOUD_SCALE + vec2(drift, 0.0);
        float density = cloudDensity(ground, level);
        if (density > CLOUD_DENSITY_EPSILON) {
            float toward = cloudDensity(ground + light.xz * CLOUD_LIGHT_STEP,
                min(level + CLOUD_LIGHT_RISE, 1.0));
            float sunlight = exp(-(toward * CLOUD_LIGHT_ABSORB
                + (1.0 - level) * CLOUD_BASE_SHADE));
            float alpha = 1.0 - exp(-density * CLOUD_ABSORB * rayStep);
            scattered += transmittance * alpha * mix(shadowTint * CLOUD_AMBIENT, sunTint, sunlight);
            transmittance -= transmittance * alpha;
        }
    }

    float silver = skyHalo(dir, light, CLOUD_SILVER_TIGHT) * (1.0 - transmittance);
    vec3 col = backdrop * transmittance + scattered + sunTint * silver * CLOUD_SILVER_GAIN;

    float haze = 1.0 - skyPlaneFalloff(midPlane.z, CLOUD_HAZE_RANGE);
    col = mix(col, backdrop, haze * CLOUD_HAZE_GAIN);
    col = mix(skyLow, col, skyEffectFade(dir.y, SkyFullDome));
    col = mix(col, skyLow, skyDepthHaze(dir.y, SkyFullDome, CLOUD_DEPTH_HAZE));

    fragColor = vec4(col, 1.0);
}
