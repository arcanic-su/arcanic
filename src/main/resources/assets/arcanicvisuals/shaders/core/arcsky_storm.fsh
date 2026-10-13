#version 150

#moj_import <arcanicvisuals:arc_common.glsl>
#moj_import <arcanicvisuals:arc_sky_common.glsl>
#moj_import <arcanicvisuals:arc_sky_depth_gate.glsl>
#moj_import <arcanicvisuals:arc_sky_uniforms.glsl>

const float STORM_DOME_BIAS = 0.5;
const float STORM_CAMERA_DRIFT = 0.0009;
const float STORM_DECK_HEIGHT = 0.62;
const float STORM_FLOOR_HEIGHT = 1.30;
const float STORM_CLOUD_DRIFT_X = 0.012;
const float STORM_CLOUD_DRIFT_Z = -0.009;

const float STORM_BROAD_SCALE = 1.55;
const float STORM_TORN_SCALE = 4.15;
const float STORM_TORN_WARP = 1.70;
const vec2 STORM_VEIN_SCALE = vec2(9.0, 2.1);
const float STORM_VEIN_DRIFT_X = -0.04;
const float STORM_VEIN_DRIFT_Y = 0.01;
const float STORM_BROAD_WEIGHT = 0.78;
const float STORM_TORN_WEIGHT = 0.50;
const float STORM_MASS_LOW = 0.24;
const float STORM_MASS_HIGH = 0.88;

const float STORM_UNDERSIDE_HIGH = 0.92;
const float STORM_MIST_HIGH = 0.04;

const vec3 STORM_HORIZON = vec3(0.28, 0.31, 0.33);
const vec3 STORM_ZENITH = vec3(0.055, 0.065, 0.085);
const vec3 STORM_CLOUD_LIGHT = vec3(0.35, 0.37, 0.39);
const vec3 STORM_CLOUD_DARK = vec3(0.035, 0.040, 0.055);
const vec3 STORM_MIST = vec3(0.30, 0.33, 0.35);
const vec3 STORM_HORIZON_TONE = vec3(0.150, 0.163, 0.176);
const float STORM_CLOUD_SHADE = 0.60;
const float STORM_UNDERSIDE_SHADE = 0.48;
const float STORM_CLOUD_MIX = 0.58;
const float STORM_CLOUD_MIX_GAIN = 0.34;
const vec2 STORM_STREAK_SCALE = vec2(0.65, 2.4);
const float STORM_STREAK_DRIFT = 0.035;
const float STORM_EXPOSURE = 0.58;
const float STORM_STREAK_GAIN = 0.13;
const float STORM_VEIN_GAIN = 0.13;
const float STORM_MIST_GAIN = 0.24;
const float STORM_TINT = 0.30;
const float STORM_FLOOR_SCALE = 0.70;
const float STORM_FLOOR_SHIFT = 211.0;
const float STORM_REACH_EPSILON = 0.004;
const float STORM_GLOW_LEVEL = 0.55;
const vec3 STORM_SILVER = vec3(0.86, 0.90, 0.96);
const float STORM_BREAK_LOW = 0.10;
const float STORM_BREAK_HIGH = 0.55;
const float STORM_BREAK_TIGHT = 7.0;
const float STORM_BREAK_GAIN = 1.25;
const float STORM_RIM_LOW = 0.28;
const float STORM_RIM_HIGH = 0.58;
const float STORM_RIM_TOP = 0.92;
const float STORM_RIM_TIGHT = 3.0;
const float STORM_RIM_GAIN = 0.70;

const float STORM_RATE = 3.20;
const float STORM_STRIKE_CHANCE = 0.58;
const float STORM_FLASH_DECAY = 28.0;
const float STORM_SECOND_DELAY = 0.16;
const float STORM_SECOND_DECAY = 36.0;
const float STORM_SECOND_GAIN = 0.55;
const float STORM_GLOW_DELAY = 0.32;
const float STORM_GLOW_DECAY = 18.0;
const float STORM_GLOW_GAIN = 0.22;

const float STORM_BOLT_EPSILON = 0.001;
const float STORM_BOLT_MIN_HEIGHT = 0.05;
const float STORM_BOLT_SPREAD = 0.62;
const float STORM_BOLT_ANCHOR_SEED = 3.1;
const float STORM_BOLT_BASE = 0.18;
const float STORM_BOLT_WOBBLE_SCALE = 28.0;
const float STORM_BOLT_WOBBLE_SEED = 9.2;
const float STORM_BOLT_WOBBLE = 0.022;
const float STORM_BOLT_JITTER_SCALE = 57.0;
const float STORM_BOLT_JITTER_RATE = 0.85;
const float STORM_BOLT_JITTER = 0.010;
const float STORM_BOLT_CORE = 0.006;
const float STORM_BOLT_EDGE = 0.030;
const float STORM_BOLT_RISE_LOW = 0.02;
const float STORM_BOLT_RISE_HIGH = 0.22;
const float STORM_BOLT_TOP_LOW = 0.76;
const float STORM_BOLT_TOP_HIGH = 1.02;
const float STORM_BRANCH_CORE = 0.004;
const float STORM_BRANCH_A_EDGE = 0.022;
const float STORM_BRANCH_A_SLOPE = 0.34;
const float STORM_BRANCH_A_OFFSET = 0.13;
const float STORM_BRANCH_A_LOW = 0.20;
const float STORM_BRANCH_A_MID = 0.36;
const float STORM_BRANCH_A_HIGH = 0.56;
const float STORM_BRANCH_A_GAIN = 0.55;
const float STORM_BRANCH_B_EDGE = 0.020;
const float STORM_BRANCH_B_SLOPE = 0.27;
const float STORM_BRANCH_B_OFFSET = 0.16;
const float STORM_BRANCH_B_LOW = 0.34;
const float STORM_BRANCH_B_MID = 0.48;
const float STORM_BRANCH_B_HIGH = 0.68;
const float STORM_BRANCH_B_GAIN = 0.42;

const float STORM_FLASH_LOW = 0.02;
const float STORM_FLASH_HIGH = 0.82;
const float STORM_FLASH_BASE = 0.35;
const float STORM_FLASH_NOISE_SCALE = 9.0;
const float STORM_FLASH_NOISE_GAIN = 0.45;
const float STORM_FLASH_GAIN = 0.95;
const float STORM_BOLT_CEILING = 0.86;
const float STORM_LIGHTNING_NEUTRAL = 0.65;
const vec3 STORM_LIGHTNING = vec3(0.76, 0.84, 0.92);

const float STORM_TAU = 6.28318530718;
const float STORM_HASH_SCALE = 91.3458;
const float STORM_HASH_SEED = 47453.5453;

in vec3 vRay;
in vec4 FragColor;

out vec4 fragColor;

float stormHash(float p) {
    return fract(sin(p * STORM_HASH_SCALE) * STORM_HASH_SEED);
}

vec3 stormSky(vec3 dir, float up, vec3 haze) {
    float dome = mix(up, dir.y, SkyFullDome) * STORM_DOME_BIAS + STORM_DOME_BIAS;
    vec3 base = mix(STORM_HORIZON, STORM_ZENITH, smoothstep(0.0, 1.0, dome));

    bool ceiling = dir.y >= 0.0;
    vec3 plane = skyPlane(dir, ceiling ? STORM_DECK_HEIGHT : STORM_FLOOR_HEIGHT);
    float reach = skyPlaneFalloff(plane.z, SKY_PLANE_RANGE);
    if (reach <= STORM_REACH_EPSILON) {
        return haze;
    }

    vec2 cloudUv = plane.xy * (ceiling ? 1.0 : STORM_FLOOR_SCALE)
        + (ceiling ? vec2(0.0) : vec2(STORM_FLOOR_SHIFT));
    cloudUv += CameraPos.xz * STORM_CAMERA_DRIFT
        + vec2(Time * STORM_CLOUD_DRIFT_X, Time * STORM_CLOUD_DRIFT_Z);

    float broadClouds = fbm(cloudUv * STORM_BROAD_SCALE);
    float tornClouds = fbm(cloudUv * STORM_TORN_SCALE + broadClouds * STORM_TORN_WARP);
    float stormVeins = fbm(cloudUv * STORM_VEIN_SCALE
        + vec2(Time * STORM_VEIN_DRIFT_X, Time * STORM_VEIN_DRIFT_Y));

    float cloudMass = smoothstep(STORM_MASS_LOW, STORM_MASS_HIGH,
        broadClouds * STORM_BROAD_WEIGHT + tornClouds * STORM_TORN_WEIGHT);
    float underside = 1.0 - smoothstep(0.0, STORM_UNDERSIDE_HIGH, up);
    float horizonMist = 1.0 - smoothstep(0.0, STORM_MIST_HIGH, up);

    vec3 clouds = mix(STORM_CLOUD_LIGHT, STORM_CLOUD_DARK,
        cloudMass * (STORM_CLOUD_SHADE + underside * STORM_UNDERSIDE_SHADE));
    float streakShadow = fbm(cloudUv * STORM_STREAK_SCALE + vec2(0.0, Time * STORM_STREAK_DRIFT));

    vec3 light = skyLightDir(Time);
    float breakThrough = smoothstep(STORM_BREAK_HIGH, STORM_BREAK_LOW, cloudMass)
        * skyHalo(dir, light, STORM_BREAK_TIGHT);
    float rim = smoothstep(STORM_RIM_LOW, STORM_RIM_HIGH, cloudMass)
        * (1.0 - smoothstep(STORM_RIM_HIGH, STORM_RIM_TOP, cloudMass))
        * skyHalo(dir, light, STORM_RIM_TIGHT);
    clouds += STORM_SILVER * (breakThrough * STORM_BREAK_GAIN + rim * STORM_RIM_GAIN);

    vec3 sky = mix(base, clouds, STORM_CLOUD_MIX + cloudMass * STORM_CLOUD_MIX_GAIN);
    sky *= STORM_EXPOSURE + streakShadow * STORM_STREAK_GAIN
        - stormVeins * underside * STORM_VEIN_GAIN;
    sky = mix(sky, STORM_MIST, horizonMist * STORM_MIST_GAIN);

    vec3 tinted = mix(sky, sky * TopLeftColor.rgb, STORM_TINT);
    return mix(haze, tinted, reach);
}

float stormPulse() {
    float stormTime = Time * STORM_RATE;
    float cell = floor(stormTime);
    float local = fract(stormTime);
    float strike = step(STORM_STRIKE_CHANCE, stormHash(cell));
    float firstFlash = exp(-local * STORM_FLASH_DECAY);
    float secondFlash = exp(-abs(local - STORM_SECOND_DELAY) * STORM_SECOND_DECAY) * STORM_SECOND_GAIN;
    float afterGlow = exp(-abs(local - STORM_GLOW_DELAY) * STORM_GLOW_DECAY) * STORM_GLOW_GAIN;

    return strike * clamp(firstFlash + secondFlash + afterGlow, 0.0, 1.0);
}

float stormBolt(vec3 dir, float pulse) {
    if (pulse <= STORM_BOLT_EPSILON || dir.y < STORM_BOLT_MIN_HEIGHT) {
        return 0.0;
    }

    float cell = floor(Time * STORM_RATE);
    float anchor = mix(-STORM_BOLT_SPREAD, STORM_BOLT_SPREAD, stormHash(cell + STORM_BOLT_ANCHOR_SEED));
    vec2 uv = vec2(dir.x - anchor, dir.y - STORM_BOLT_BASE);
    uv.x += sin(uv.y * STORM_BOLT_WOBBLE_SCALE + stormHash(cell + STORM_BOLT_WOBBLE_SEED) * STORM_TAU) * STORM_BOLT_WOBBLE;
    uv.x += sin(uv.y * STORM_BOLT_JITTER_SCALE + Time * STORM_BOLT_JITTER_RATE) * STORM_BOLT_JITTER;

    float mainBolt = (1.0 - smoothstep(STORM_BOLT_CORE, STORM_BOLT_EDGE, abs(uv.x)))
        * smoothstep(STORM_BOLT_RISE_LOW, STORM_BOLT_RISE_HIGH, uv.y)
        * (1.0 - smoothstep(STORM_BOLT_TOP_LOW, STORM_BOLT_TOP_HIGH, uv.y));
    float branchA = (1.0 - smoothstep(STORM_BRANCH_CORE, STORM_BRANCH_A_EDGE,
            abs(uv.x - uv.y * STORM_BRANCH_A_SLOPE + STORM_BRANCH_A_OFFSET)))
        * smoothstep(STORM_BRANCH_A_LOW, STORM_BRANCH_A_MID, uv.y)
        * (1.0 - smoothstep(STORM_BRANCH_A_MID, STORM_BRANCH_A_HIGH, uv.y));
    float branchB = (1.0 - smoothstep(STORM_BRANCH_CORE, STORM_BRANCH_B_EDGE,
            abs(uv.x + uv.y * STORM_BRANCH_B_SLOPE - STORM_BRANCH_B_OFFSET)))
        * smoothstep(STORM_BRANCH_B_LOW, STORM_BRANCH_B_MID, uv.y)
        * (1.0 - smoothstep(STORM_BRANCH_B_MID, STORM_BRANCH_B_HIGH, uv.y));

    return clamp((mainBolt + branchA * STORM_BRANCH_A_GAIN + branchB * STORM_BRANCH_B_GAIN) * pulse, 0.0, 1.0);
}

void main() {
    if (skyCoveredByGeometry(gl_FragCoord.xy)) {
        discard;
    }
    vec3 dir = normalize(vRay);
    vec3 skyLight = skyLightDir(Time);
    vec3 horizonTone = mix(STORM_HORIZON_TONE, STORM_HORIZON_TONE * TopLeftColor.rgb, STORM_TINT);
    vec3 horizon = skyHorizonGlow(dir, skyLight, horizonTone, STORM_SILVER * STORM_GLOW_LEVEL);

    if (SkyFullDome < 0.5 && dir.y <= 0.0) {
        fragColor = vec4(skyFinish(horizonTone, SKY_EXPOSURE), FragColor.a);
        return;
    }

    float up = clamp(dir.y, 0.0, 1.0);
    vec3 finalCol = stormSky(dir, up, horizon);

    vec3 lightning = mix(STORM_LIGHTNING, STORM_LIGHTNING * TopLeftColor.rgb, 1.0 - STORM_LIGHTNING_NEUTRAL);
    float pulse = stormPulse();
    float cloudFlash = pulse * smoothstep(STORM_FLASH_LOW, STORM_FLASH_HIGH, up)
        * (STORM_FLASH_BASE + fbm(dir.xz * STORM_FLASH_NOISE_SCALE) * STORM_FLASH_NOISE_GAIN);

    finalCol += lightning * cloudFlash * STORM_FLASH_GAIN;
    finalCol = mix(finalCol, lightning, clamp(stormBolt(dir, pulse), 0.0, STORM_BOLT_CEILING));
    finalCol = mix(horizonTone, finalCol, skyEffectFade(dir.y, SkyFullDome));
    finalCol *= skyNadirDepth(dir.y, SkyFullDome);

    fragColor = vec4(skyDither(skyFinish(finalCol, SKY_EXPOSURE), gl_FragCoord.xy), FragColor.a);
}
