#version 150

#moj_import <arcanicvisuals:arc_customsky_common.glsl>

in vec2 texCoord;
out vec4 fragColor;

const int CURTAIN_LAYERS = 32;
const int WARP_OCTAVES = 5;

const float LAYER_STRIDE = 1.5;
const float CURTAIN_SPAN = float(CURTAIN_LAYERS) * LAYER_STRIDE;
const float PLANE_BASE = 0.8;
const float PLANE_CURVE = 0.002;
const float PLANE_EXPONENT = 1.4;
const float RAY_GAIN = 2.0;
const float LAYER_DECAY = 0.065;
const float LAYER_BIAS = 2.5;
const float LAYER_RAMP = 5.0;
const float LAYER_BLEND = 0.5;
const float DITHER_AMOUNT = 0.035;
const float DITHER_RAMP = 15.0;

const float WARP_START_AMPLITUDE = 1.8;
const float WARP_START_SCALE = 2.5;
const float WARP_FREQUENCY = 1.85;
const float WARP_STRENGTH = 0.75;
const float WARP_SPEED = 0.06;
const float BASE_LACUNARITY = 1.3;
const float WARP_SCALE_DECAY = 0.45;
const float AMPLITUDE_DECAY = 0.42;
const float FEEDBACK_GAIN = 1.21;
const float FEEDBACK_BIAS = 0.02;
const float DENSITY_SHARPNESS = 29.0;
const float DENSITY_EXPONENT = 1.3;
const float DENSITY_CEILING = 0.55;
const float DENSITY_CONTRAST = 2.1;
const float BAND_ROTATION = 0.06;
const float TRI_MIN = 0.01;
const float TRI_MAX = 0.49;

const float DRIFT_SPEED = 0.02;
const float CURTAIN_GAIN = 2.6;
const float CORE_WHITEN = 0.60;
const float CORE_SHARPNESS = 2.0;
const float SLOPE_FLOOR = 0.012;
const float CURTAIN_HEIGHT = 0.85;
const float CURTAIN_FLOOR_HEIGHT = 1.60;
const float REACH_EPSILON = 0.004;
const float BANDING_DITHER = 0.0015;
const float BANDING_DITHER_SCALE = 1.37;

const vec3 NIGHT_HORIZON = vec3(0.006, 0.008, 0.020);
const vec3 NIGHT_ZENITH = vec3(0.002, 0.003, 0.010);
const float AURORA_GLOW_AXIS = 0.35;
const float AURORA_GLOW_WIDTH = 0.13;
const float AURORA_GLOW_GAIN = 0.030;
const mat2 WARP_ROTATION = mat2(0.95534, 0.29552, -0.29552, 0.95534);

mat2 spin(float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return mat2(c, s, -s, c);
}

float triWave(float x) {
    return clamp(abs(fract(x) - 0.5), TRI_MIN, TRI_MAX);
}

vec2 triWave2(vec2 p) {
    return vec2(triWave(p.x) + triWave(p.y), triWave(p.y + triWave(p.x)));
}

float curtainDensity(vec2 p, float time) {
    float amplitude = WARP_START_AMPLITUDE;
    float warpScale = WARP_START_SCALE;
    float sum = 0.0;
    p *= spin(p.x * BAND_ROTATION);
    vec2 base = p;
    for (int octave = 0; octave < WARP_OCTAVES; octave++) {
        vec2 warp = triWave2(base * WARP_FREQUENCY) * WARP_STRENGTH;
        warp *= spin(time * WARP_SPEED);
        p -= warp / warpScale;
        base *= BASE_LACUNARITY;
        warpScale *= WARP_SCALE_DECAY;
        amplitude *= AMPLITUDE_DECAY;
        p *= FEEDBACK_GAIN + (sum - 1.0) * FEEDBACK_BIAS;
        sum += triWave(p.x + triWave(p.y)) * amplitude;
        p *= -WARP_ROTATION;
    }
    float density = clamp(1.0 / pow(sum * DENSITY_SHARPNESS, DENSITY_EXPONENT), 0.0, DENSITY_CEILING);
    return pow(density / DENSITY_CEILING, DENSITY_CONTRAST) * DENSITY_CEILING;
}

vec3 curtains(vec3 rd, float time, float axis, float domeFade) {
    vec3 accum = vec3(0.0);
    vec3 average = vec3(0.0);
    float dither = skyHash21(gl_FragCoord.xy);
    float slope = max(abs(rd.y), SLOPE_FLOOR) * RAY_GAIN;
    for (int layer = 0; layer < CURTAIN_LAYERS; layer++) {
        float depth = float(layer) * LAYER_STRIDE;
        float jitter = (dither - 0.5) * DITHER_AMOUNT * smoothstep(0.0, DITHER_RAMP, depth);
        float travel = (PLANE_BASE + pow(depth, PLANE_EXPONENT) * PLANE_CURVE) * (1.0 + jitter) / slope;
        vec3 hit = rd * travel;
        float density = curtainDensity(skyPlaneDomain(hit.zx) + vec2(time * DRIFT_SPEED, 0.0), time);
        vec3 tint = skyTint(axis, depth / CURTAIN_SPAN, time);
        float core = pow(density / DENSITY_CEILING, CORE_SHARPNESS);
        vec3 emission = mix(tint, vec3(1.0), core * CORE_WHITEN) * density;
        average = mix(average, emission, LAYER_BLEND);
        accum += average * exp2(-depth * LAYER_DECAY - LAYER_BIAS) * smoothstep(0.0, LAYER_RAMP, depth);
    }
    return accum * CURTAIN_GAIN * domeFade;
}

void main() {
    float time = misc.x;
    float brightness = misc.w;

    vec3 rd = skyRayDirection(texCoord);
    vec3 color = mix(NIGHT_HORIZON, NIGHT_ZENITH, clamp(skyVerticalAxis(rd.y), 0.0, 1.0));
    color += skyTint(atan(rd.z, rd.x), AURORA_GLOW_AXIS, time)
        * skyBand(rd, vec3(0.0, 1.0, 0.0), AURORA_GLOW_WIDTH) * AURORA_GLOW_GAIN;

    vec3 plane = skyPlane(rd, rd.y >= 0.0 ? CURTAIN_HEIGHT : CURTAIN_FLOOR_HEIGHT);
    float reach = skyPlaneFalloff(plane.z, SKY_PLANE_RANGE)
        * max(step(0.0, rd.y), skyExtra.y);
    if (reach > REACH_EPSILON) {
        color += curtains(rd, time, atan(rd.z, rd.x), reach);
    }

    vec3 encoded = skyEncodeHDR(color * brightness * skyNadirDepth(rd.y));
    encoded += (skyHash21(gl_FragCoord.xy * BANDING_DITHER_SCALE) - 0.5) * BANDING_DITHER;

    fragColor = vec4(max(encoded, vec3(0.0)), 1.0);
}
