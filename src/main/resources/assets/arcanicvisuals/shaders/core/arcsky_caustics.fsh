#version 150

#moj_import <arcanicvisuals:arc_common.glsl>
#moj_import <arcanicvisuals:arc_sky_common.glsl>
#moj_import <arcanicvisuals:arc_sky_depth_gate.glsl>
#moj_import <arcanicvisuals:arc_sky_uniforms.glsl>

const float CAUSTICS_TAU = 6.28318530718;
const int CAUSTICS_ITERATIONS = 5;
const float CAUSTICS_CEILING_HEIGHT = 0.95;
const float CAUSTICS_FLOOR_HEIGHT = 1.45;
const float CAUSTICS_DOMAIN_SCALE = 1.35;
const float CAUSTICS_DRIFT_X = 0.018;
const float CAUSTICS_DRIFT_Z = -0.012;
const float CAUSTICS_DOMAIN_SHIFT = 250.0;
const float CAUSTICS_THICKNESS = 0.005;
const float CAUSTICS_TIME_SCALE = 0.45;
const float CAUSTICS_TIME_OFFSET = 23.0;
const float CAUSTICS_PHASE_GAIN = 3.5;
const float CAUSTICS_CONTRAST_BIAS = 1.17;
const float CAUSTICS_CONTRAST_POWER = 1.4;
const float CAUSTICS_HIGHLIGHT_POWER = 7.0;
const float CAUSTICS_TINT_FLOOR = 0.55;
const float CAUSTICS_FLOOR_SCALE = 0.85;
const float CAUSTICS_FLOOR_SHIFT = 137.0;
const float CAUSTICS_FLOOR_TINT = 0.45;
const float CAUSTICS_GLOW_WHITEN = 0.55;
const float CAUSTICS_REACH_EPSILON = 0.004;
const float CAUSTICS_DETAIL_FLOOR = 0.25;
const float CAUSTICS_BASE_LOW = 0.12;
const float CAUSTICS_BASE_HIGH = 0.30;
const float CAUSTICS_BASE_BLEND = 0.72;
const float CAUSTICS_POOL_TIGHT = 2.2;
const float CAUSTICS_POOL_FLOOR = 0.34;
const float CAUSTICS_GLINT_TIGHT = 40.0;
const float CAUSTICS_GLINT_GAIN = 1.70;

in vec3 vRay;
in vec4 FragColor;

out vec4 fragColor;

float causticsWeave(vec2 domain) {
    vec2 p = mod(domain * CAUSTICS_TAU, CAUSTICS_TAU) - CAUSTICS_DOMAIN_SHIFT;
    vec2 walker = p;
    float accum = 1.0;
    float localTime = Time * CAUSTICS_TIME_SCALE + CAUSTICS_TIME_OFFSET;

    for (int n = 0; n < CAUSTICS_ITERATIONS; n++) {
        float t = localTime * (1.0 - CAUSTICS_PHASE_GAIN / float(n + 1));
        walker = p + vec2(cos(t - walker.x) + sin(t + walker.y), sin(t - walker.y) + cos(t + walker.x));
        accum += 1.0 / length(vec2(
            p.x / (sin(walker.x + t) / CAUSTICS_THICKNESS),
            p.y / (cos(walker.y + t) / CAUSTICS_THICKNESS)));
    }

    accum /= float(CAUSTICS_ITERATIONS);
    return CAUSTICS_CONTRAST_BIAS - pow(abs(accum), CAUSTICS_CONTRAST_POWER);
}

void main() {
    if (skyCoveredByGeometry(gl_FragCoord.xy)) {
        discard;
    }
    vec3 dir = normalize(vRay);
    vec3 light = skyLightDir(Time);
    vec3 horizonTone = TopLeftColor.rgb * CAUSTICS_TINT_FLOOR;
    vec3 haze = skyHorizonGlow(dir, light, horizonTone,
        mix(TopLeftColor.rgb, vec3(1.0), CAUSTICS_GLOW_WHITEN));

    if (SkyFullDome < 0.5 && dir.y <= 0.0) {
        fragColor = vec4(skyFinish(horizonTone, SKY_EXPOSURE), FragColor.a);
        return;
    }

    bool ceiling = dir.y >= 0.0;
    float height = ceiling ? CAUSTICS_CEILING_HEIGHT : CAUSTICS_FLOOR_HEIGHT;
    vec3 plane = skyPlane(dir, height);
    float reach = skyPlaneFalloff(plane.z, SKY_PLANE_RANGE);

    vec3 finalCol = haze;
    if (reach > CAUSTICS_REACH_EPSILON) {
        vec2 drift = ceiling
            ? vec2(Time * CAUSTICS_DRIFT_X, Time * CAUSTICS_DRIFT_Z)
            : vec2(CAUSTICS_FLOOR_SHIFT - Time * CAUSTICS_DRIFT_Z, Time * CAUSTICS_DRIFT_X);
        float scale = ceiling ? CAUSTICS_DOMAIN_SCALE : CAUSTICS_FLOOR_SCALE;
        vec2 domain = plane.xy * scale + drift;

        float pool = CAUSTICS_POOL_FLOOR
            + (1.0 - CAUSTICS_POOL_FLOOR) * skyHalo(dir, light, CAUSTICS_POOL_TIGHT);
        float detail = skyPlaneFalloff(plane.z, SKY_PLANE_DETAIL_RANGE);
        float highlight = pow(abs(causticsWeave(domain)), CAUSTICS_HIGHLIGHT_POWER)
            * pool * mix(CAUSTICS_DETAIL_FLOOR, 1.0, detail);

        vec3 body = ceiling
            ? TopLeftColor.rgb * CAUSTICS_BASE_HIGH
            : mix(BottomRightColor.rgb, TopLeftColor.rgb, CAUSTICS_FLOOR_TINT) * CAUSTICS_BASE_LOW;
        vec3 veins = clamp(vec3(highlight) + body, 0.0, 1.0);
        veins += mix(TopLeftColor.rgb, vec3(1.0), 0.5)
            * skyHalo(dir, light, CAUSTICS_GLINT_TIGHT) * CAUSTICS_GLINT_GAIN;

        finalCol = mix(haze, veins, reach);
    }
    finalCol = mix(horizonTone, finalCol, skyEffectFade(dir.y, SkyFullDome));
    finalCol *= skyNadirDepth(dir.y, SkyFullDome);

    fragColor = vec4(skyDither(skyFinish(finalCol, SKY_EXPOSURE), gl_FragCoord.xy), FragColor.a);
}
