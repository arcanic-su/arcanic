const float EMBER_TAU = 6.2831853;
const float EMBER_LAPS = 36.0;
const float EMBER_TIME_OFFSET = -1.495;
const float EMBER_FIELD_SCALE = 0.82;
const float EMBER_ROTATION = -1.3708;
const float EMBER_SWIRL = 0.5;
const int EMBER_SWIRL_ITERATIONS = 16;
const float EMBER_SWIRL_Y_STEP = 1.5;
const float EMBER_SHAPE_SCALE = 2.075;
const float EMBER_PROPORTION_SHIFT = -0.336;
const vec2 EMBER_LOW_EDGE = vec2(0.1855, 0.5196);
const vec2 EMBER_HIGH_EDGE = vec2(0.4855, 0.8246);
const float EMBER_LOW_WEIGHT = 0.62;
const float EMBER_HIGH_WEIGHT = 0.5;

const float EMBER_ASH_GAIN = 0.025;
const float EMBER_DEEP_GAIN = 0.3;
const float EMBER_GLOW_WHITE = 0.45;
const float EMBER_CREAM_WHITE = 0.85;
const vec3 EMBER_WHITE = vec3(1.0);
const float EMBER_STOP_DEEP = 0.22;
const float EMBER_STOP_BASE = 0.42;
const float EMBER_STOP_MIDDLE = 0.6;
const float EMBER_STOP_GLOW = 0.78;
const float EMBER_STOP_CREAM = 0.97;

const vec2 EMBER_BLOOM_CENTER = vec2(0.5, 0.46);
const float EMBER_BLOOM_FALLOFF = 3.2;
const float EMBER_BLOOM_GAIN = 0.95;
const float EMBER_AMBIENT = 0.42;
const float EMBER_HEAT_FLOOR = 0.6;
const float EMBER_VIGNETTE = 0.55;

const float EMBER_BASE_TOP = 0.06;
const float EMBER_BASE_BOTTOM = 0.17;
const float EMBER_HALO_FALLOFF = 6.0;
const float EMBER_HALO_GAIN = 0.3;
const int EMBER_HAZE_ITERATIONS = 4;
const float EMBER_HAZE_SCALE = 0.55;
const float EMBER_HAZE_GAIN = 0.62;
const float EMBER_SATURATION = 1.3;
const vec3 EMBER_LUMA = vec3(0.2126, 0.7152, 0.0722);

vec3 emberRamp(float heat, vec3 start, vec3 middle, vec3 end) {
    vec3 color = mix(start * EMBER_ASH_GAIN, start * EMBER_DEEP_GAIN, smoothstep(0.0, EMBER_STOP_DEEP, heat));
    color = mix(color, start, smoothstep(EMBER_STOP_DEEP, EMBER_STOP_BASE, heat));
    color = mix(color, middle, smoothstep(EMBER_STOP_BASE, EMBER_STOP_MIDDLE, heat));
    color = mix(color, mix(end, EMBER_WHITE, EMBER_GLOW_WHITE), smoothstep(EMBER_STOP_MIDDLE, EMBER_STOP_GLOW, heat));
    return mix(color, mix(end, EMBER_WHITE, EMBER_CREAM_WHITE), smoothstep(EMBER_STOP_GLOW, EMBER_STOP_CREAM, heat));
}

float emberField(vec2 uv, float aspect, float cycle, float scale, int iterations) {
    float time = cycle * EMBER_TAU * EMBER_LAPS + EMBER_TIME_OFFSET;
    vec2 field = (uv - 0.5) * vec2(1.0 / aspect, 1.0) * scale;
    float c = cos(EMBER_ROTATION);
    float s = sin(EMBER_ROTATION);
    field = mat2(c, s, -s, c) * field + 0.5;

    for (int i = 1; i <= iterations; i++) {
        float order = float(i);
        field.x += EMBER_SWIRL / order * cos(time + order * EMBER_SWIRL_Y_STEP * field.y);
        field.y += EMBER_SWIRL / order * cos(time + order * field.x);
    }

    vec2 checks = field * EMBER_SHAPE_SCALE;
    float mixer = 0.5 + 0.5 * sin(checks.x) * cos(checks.y) + EMBER_PROPORTION_SHIFT;
    float low = smoothstep(EMBER_LOW_EDGE.x, EMBER_LOW_EDGE.y, mixer);
    float high = smoothstep(EMBER_HIGH_EDGE.x, EMBER_HIGH_EDGE.y, mixer);
    return clamp(low * EMBER_LOW_WEIGHT + high * EMBER_HIGH_WEIGHT, 0.0, 1.0);
}

vec3 emberScreenFrame(vec2 uv, float aspect) {
    vec2 offset = (uv - EMBER_BLOOM_CENTER) * vec2(1.0 / aspect, 1.0);
    return vec3(uv.y, uv.x, dot(offset, offset));
}

vec3 emberBody(vec2 uv, float aspect, float cycle, float intensity, vec3 start, vec3 middle, vec3 end,
        int swirlIterations, vec3 frame) {
    float radius2 = frame.z;
    float energy = mix(EMBER_HEAT_FLOOR, 1.0, intensity);
    float bloom = exp(-radius2 * EMBER_BLOOM_FALLOFF) * EMBER_BLOOM_GAIN;
    float mask = clamp(EMBER_AMBIENT + bloom * energy, 0.0, 1.0);

    float heat = emberField(uv, aspect, cycle, EMBER_FIELD_SCALE, swirlIterations) * mask;
    float haze = emberField(uv, aspect, cycle, EMBER_FIELD_SCALE * EMBER_HAZE_SCALE, EMBER_HAZE_ITERATIONS);

    vec3 base = start * mix(EMBER_BASE_TOP, EMBER_BASE_BOTTOM, frame.x);
    vec3 halo = middle * exp(-radius2 * EMBER_HALO_FALLOFF) * EMBER_HALO_GAIN * energy;
    vec3 mist = mix(start, end, frame.y) * haze * haze * EMBER_HAZE_GAIN * energy;
    vec3 color = max(emberRamp(heat, start, middle, end), base) + halo + mist;

    float luma = dot(color, EMBER_LUMA);
    return max(mix(vec3(luma), color, EMBER_SATURATION), 0.0);
}

float emberVignette(vec2 uv) {
    vec2 edge = uv - 0.5;
    return 1.0 - dot(edge, edge) * EMBER_VIGNETTE;
}

vec3 ember(vec2 uv, float aspect, float cycle, float intensity, vec3 start, vec3 middle, vec3 end) {
    return emberBody(uv, aspect, cycle, intensity, start, middle, end, EMBER_SWIRL_ITERATIONS,
            emberScreenFrame(uv, aspect)) * emberVignette(uv);
}
