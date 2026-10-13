#version 150

#moj_import <arcanicvisuals:arc_common.glsl>
#moj_import <arcanicvisuals:arc_sky_common.glsl>
#moj_import <arcanicvisuals:arc_sky_uniforms.glsl>

const float NEBULA_NEAR = 0.55;
const float NEBULA_FAR = 4.6;
const int NEBULA_STEPS = 14;
const float NEBULA_JITTER = 0.3;
const float NEBULA_DITHER_SCALE = 4096.0;

const float NEBULA_SCALE = 1.25;
const float NEBULA_WARP_SCALE = 0.55;
const float NEBULA_WARP_GAIN = 1.35;
const float NEBULA_DRIFT = 0.035;
const float NEBULA_FILAMENT_LOW = 0.50;
const float NEBULA_FILAMENT_HIGH = 0.78;
const float NEBULA_FILAMENT_POWER = 1.55;
const float NEBULA_NOISE_MEAN = 0.5;

const vec3 NEBULA_CENTER_DIR = vec3(0.10, 0.22, -0.05);
const float NEBULA_CENTER_DIST = 2.4;
const float NEBULA_REACH = 1.9;

const vec3 NEBULA_CORE_A = vec3(0.62, 0.35, -0.70);
const vec3 NEBULA_CORE_B = vec3(-0.55, -0.12, 0.83);
const float NEBULA_CORE_DIST_A = 2.1;
const float NEBULA_CORE_DIST_B = 3.4;
const float NEBULA_CORE_FALLOFF = 1.9;
const float NEBULA_CORE_GAIN_A = 2.6;
const float NEBULA_CORE_GAIN_B = 1.4;
const float NEBULA_HALO_TIGHT = 2.0;
const float NEBULA_HALO_GAIN = 0.35;

const float NEBULA_ABSORB = 2.4;
const float NEBULA_EMIT = 0.40;
const float NEBULA_DEEP_LEVEL = 0.05;
const float NEBULA_DENSITY_EPSILON = 0.002;
const float NEBULA_CUTOFF = 0.02;
const float NEBULA_DEPTH_HAZE = 0.30;

in vec3 vRay;
in vec4 FragColor;

out vec4 fragColor;

float nebulaDensity(vec3 p, float drift) {
    float warp = fbm3d(p * NEBULA_WARP_SCALE + vec3(drift, 0.0, -drift)) - NEBULA_NOISE_MEAN;
    vec3 q = p * NEBULA_SCALE + vec3(warp, warp, -warp) * NEBULA_WARP_GAIN;
    float value = fbm3d(q);
    float envelope = exp(-length(p - normalize(NEBULA_CENTER_DIR) * NEBULA_CENTER_DIST)
        / NEBULA_REACH);
    return pow(smoothstep(NEBULA_FILAMENT_LOW, NEBULA_FILAMENT_HIGH, value),
        NEBULA_FILAMENT_POWER) * envelope;
}

float nebulaGlow(vec3 p, vec3 core, float distance, float gain) {
    vec3 offset = p - core * distance;
    return gain / (1.0 + dot(offset, offset) * NEBULA_CORE_FALLOFF);
}

void main() {
    vec3 dir = normalize(vRay);
    vec3 deep = BottomRightColor.rgb * NEBULA_DEEP_LEVEL;

    if (SkyFullDome < 0.5 && dir.y <= 0.0) {
        fragColor = vec4(deep, 1.0);
        return;
    }

    vec3 p = skyRotationY(SkyRotSin, SkyRotCos) * dir;
    vec3 coreA = normalize(NEBULA_CORE_A);
    vec3 coreB = normalize(NEBULA_CORE_B);
    float drift = Time * NEBULA_DRIFT;
    float dither = hash13(p * NEBULA_DITHER_SCALE);

    float stepLength = (NEBULA_FAR - NEBULA_NEAR) / float(NEBULA_STEPS);
    float transmittance = 1.0;
    vec3 scattered = vec3(0.0);

    for (int layer = 0; layer < NEBULA_STEPS; layer++) {
        if (transmittance < NEBULA_CUTOFF) {
            break;
        }
        float travel = NEBULA_NEAR + stepLength
            * (float(layer) + 0.5 + (dither - 0.5) * NEBULA_JITTER);
        vec3 probe = p * travel;
        float density = nebulaDensity(probe, drift);
        if (density > NEBULA_DENSITY_EPSILON) {
            float glow = nebulaGlow(probe, coreA, NEBULA_CORE_DIST_A, NEBULA_CORE_GAIN_A)
                + nebulaGlow(probe, coreB, NEBULA_CORE_DIST_B, NEBULA_CORE_GAIN_B);
            vec3 gas = mix(BottomRightColor.rgb, TopLeftColor.rgb, clamp(density, 0.0, 1.0));
            float alpha = 1.0 - exp(-density * NEBULA_ABSORB * stepLength);
            scattered += transmittance * alpha * (gas * NEBULA_EMIT + TopLeftColor.rgb * glow);
            transmittance -= transmittance * alpha;
        }
    }

    float coreHalo = skyHalo(p, coreA, NEBULA_HALO_TIGHT) + skyHalo(p, coreB, NEBULA_HALO_TIGHT);
    vec3 col = deep * transmittance + scattered
        + TopLeftColor.rgb * coreHalo * NEBULA_HALO_GAIN * transmittance;
    col = mix(deep, col, skyEffectFade(dir.y, SkyFullDome));
    col = mix(col, deep, skyDepthHaze(dir.y, SkyFullDome, NEBULA_DEPTH_HAZE));

    fragColor = vec4(col, 1.0);
}
