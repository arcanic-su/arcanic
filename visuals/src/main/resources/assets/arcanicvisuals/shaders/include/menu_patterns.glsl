const float MODE_NEBULA = 1.0;
const float MODE_SILK = 2.0;
const float MODE_CONTOUR = 3.0;
const float MODE_EMBER = 4.0;
const float MODE_EPSILON = 0.5;

const float TAU = 6.2831853;
const float RAMP_MID = 0.5;
const float RAMP_SPAN = 2.0;

const float POINT_MARGIN = 0.22;
const float POINT_SPAN = 0.56;
const float POINT_SEED_X = 3.17;
const float POINT_SEED_Y = 8.41;

const float NEBULA_TILE = 5.0;
const float NEBULA_DRIFT_LAPS = 1.0;
const vec2 NEBULA_WARP_SHIFT = vec2(4.6, 9.3);
const float NEBULA_WARP_SCALE = 1.7;
const float NEBULA_WARP_GAIN = 1.35;
const float NEBULA_CLOUD_SCALE = 1.25;
const float NEBULA_CLOUD_LOW = 0.50;
const float NEBULA_CLOUD_HIGH = 0.96;
const float NEBULA_CLOUD_CONTRAST = 1.5;
const float NEBULA_CLOUD_ALPHA = 1.0;
const float NEBULA_CORE_EXPONENT = 3.5;
const float NEBULA_CORE_GAIN = 0.26;
const float NEBULA_RAMP_BIAS = 0.2;
const float NEBULA_RAMP_GAIN = 1.1;
const vec3 NEBULA_SKY_TOP = vec3(0.024, 0.026, 0.057);
const vec3 NEBULA_SKY_BOTTOM = vec3(0.004, 0.005, 0.013);
const float NEBULA_SKY_TINT = 0.16;
const float NEBULA_DUST_GAIN = 0.12;
const vec3 NEBULA_STAR_COLOR = vec3(0.86, 0.90, 1.0);
const float NEBULA_STAR_TINT = 0.35;
const float NEBULA_VIGNETTE = 0.55;

const vec3 STAR_NEAR = vec3(26.0, 3.0, 0.72);
const vec3 STAR_MID = vec3(44.0, 2.0, 0.80);
const vec3 STAR_FAR = vec3(72.0, 1.0, 0.86);
const float STAR_NEAR_RADIUS = 0.0026;
const float STAR_MID_RADIUS = 0.0018;
const float STAR_FAR_RADIUS = 0.0012;
const float STAR_NEAR_GAIN = 1.0;
const float STAR_MID_GAIN = 0.72;
const float STAR_FAR_GAIN = 0.46;
const float STAR_TWINKLE_BASE = 0.62;
const float STAR_TWINKLE_GAIN = 0.38;
const float STAR_TWINKLE_LAPS = 9.0;

const float SILK_TILE = 3.0;
const float SILK_DRIFT_LAPS = 1.0;
const vec2 SILK_WARP_SHIFT = vec2(7.3, 2.9);
const float SILK_WARP_SCALE = 1.15;
const float SILK_WARP_GAIN = 1.6;
const float SILK_CROSS = 0.45;
const float SILK_FLOW_SCALE = 0.85;
const float SILK_BAND_COUNT = 3.6;
const float SILK_BAND_LAPS = 2.0;
const float SILK_SHEEN_EXPONENT = 16.0;
const float SILK_SHEEN_GAIN = 0.26;
const float SILK_RAMP_BIAS = 0.1;
const float SILK_RAMP_GAIN = 1.1;
const float SILK_BAND_TINT = 0.3;
const float SILK_BODY_FLOOR = 0.08;
const float SILK_BODY_GAIN = 0.5;
const vec3 SILK_BASE_TOP = vec3(0.026, 0.024, 0.045);
const vec3 SILK_BASE_BOTTOM = vec3(0.006, 0.006, 0.014);
const float SILK_DEPTH_EXPONENT = 1.4;
const float SILK_VIGNETTE = 0.42;

const float CONTOUR_TILE = 4.0;
const float CONTOUR_DRIFT_LAPS = 1.0;
const vec2 CONTOUR_WARP_SHIFT = vec2(2.7, 6.1);
const float CONTOUR_WARP_SCALE = 1.5;
const float CONTOUR_WARP_GAIN = 0.65;
const float CONTOUR_LEVELS = 16.0;
const float CONTOUR_LEVEL_LAPS = 5.0;
const float CONTOUR_MAJOR_EVERY = 5.0;
const float CONTOUR_MIN_GRADIENT = 1e-5;
const float CONTOUR_MINOR_WIDTH = 0.85;
const float CONTOUR_MAJOR_WIDTH = 1.5;
const float CONTOUR_LINE_AA = 0.8;
const float CONTOUR_MINOR_GAIN = 0.6;
const float CONTOUR_MAJOR_GAIN = 1.0;
const float CONTOUR_GLOW_WIDTH = 7.0;
const float CONTOUR_GLOW_GAIN = 0.2;
const float CONTOUR_HIGHLIGHT = 0.18;
const float CONTOUR_RAMP_BIAS = 0.15;
const float CONTOUR_RAMP_GAIN = 1.2;
const float CONTOUR_FILL_GAIN = 0.16;
const vec3 CONTOUR_BASE_TOP = vec3(0.020, 0.021, 0.040);
const vec3 CONTOUR_BASE_BOTTOM = vec3(0.005, 0.005, 0.013);
const float CONTOUR_VIGNETTE = 0.45;

vec3 accentAt(float position) {
    float clamped = clamp(position, 0.0, 1.0);
    if (clamped <= RAMP_MID) {
        return mix(TopLeftColor.rgb, BottomLeftColor.rgb, clamped * RAMP_SPAN);
    }
    return mix(BottomLeftColor.rgb, TopRightColor.rgb, (clamped - RAMP_MID) * RAMP_SPAN);
}

float vignetteAt(vec2 uv, float strength) {
    vec2 offset = (uv - 0.5) * 2.0;
    float falloff = dot(offset, offset) * 0.5;
    return 1.0 - strength * falloff * falloff;
}

float pointFalloff(vec2 delta, float radius) {
    float squared = dot(delta, delta) / (radius * radius);
    return exp(-squared);
}

float starLayer(vec2 uv, float aspect, float cycle, vec3 layer, float radius) {
    float columns = layer.x;
    float rows = max(floor(columns * aspect), 1.0);
    vec2 grid = vec2(uv.x * columns + cycle * columns * layer.y, uv.y * rows);
    vec2 cell = floor(grid);
    vec2 local = grid - cell;
    vec2 wrapped = mod(cell, vec2(columns, rows)) + columns;
    float pick = hash12(wrapped);
    if (pick < layer.z) {
        return 0.0;
    }
    vec2 position = vec2(
            POINT_MARGIN + POINT_SPAN * hash12(wrapped + POINT_SEED_X),
            POINT_MARGIN + POINT_SPAN * hash12(wrapped + POINT_SEED_Y));
    vec2 delta = (local - position) / vec2(columns, rows) * vec2(1.0, aspect);
    float twinkle = STAR_TWINKLE_BASE
            + STAR_TWINKLE_GAIN * sin(TAU * (cycle * STAR_TWINKLE_LAPS + pick));
    return pointFalloff(delta, radius) * twinkle;
}

vec3 nebula(vec2 uv, float aspect, float cycle, float shade) {
    vec2 field = vec2(uv.x, uv.y * aspect) * NEBULA_TILE;
    float drift = cycle * NEBULA_TILE * NEBULA_DRIFT_LAPS;
    vec2 warp = vec2(
            loopFbm(field * NEBULA_WARP_SCALE + vec2(drift, 0.0), NEBULA_TILE),
            loopFbm(field * NEBULA_WARP_SCALE + NEBULA_WARP_SHIFT - vec2(drift, 0.0), NEBULA_TILE));
    float clouds = loopFbm(field * NEBULA_CLOUD_SCALE + warp * NEBULA_WARP_GAIN + vec2(drift, 0.0),
            NEBULA_TILE);
    clouds = pow(smoothstep(NEBULA_CLOUD_LOW, NEBULA_CLOUD_HIGH, clouds), NEBULA_CLOUD_CONTRAST);

    vec3 tint = accentAt(NEBULA_RAMP_BIAS + clouds * NEBULA_RAMP_GAIN);
    vec3 sky = mix(NEBULA_SKY_TOP, NEBULA_SKY_BOTTOM, shade);
    sky += accentAt(0.0) * NEBULA_SKY_TINT * (1.0 - shade) * NEBULA_DUST_GAIN;

    float core = pow(clouds, NEBULA_CORE_EXPONENT);
    vec3 color = sky + tint * clouds * NEBULA_CLOUD_ALPHA + core * NEBULA_CORE_GAIN;

    float stars = starLayer(uv, aspect, cycle, STAR_NEAR, STAR_NEAR_RADIUS) * STAR_NEAR_GAIN
            + starLayer(uv, aspect, cycle, STAR_MID, STAR_MID_RADIUS) * STAR_MID_GAIN
            + starLayer(uv, aspect, cycle, STAR_FAR, STAR_FAR_RADIUS) * STAR_FAR_GAIN;
    vec3 starTone = mix(NEBULA_STAR_COLOR, accentAt(1.0), NEBULA_STAR_TINT);
    color += starTone * stars * (1.0 - core);

    return color;
}

vec3 silk(vec2 uv, float aspect, float cycle, float shade) {
    vec2 field = vec2(uv.x, uv.y * aspect) * SILK_TILE;
    float drift = cycle * SILK_TILE * SILK_DRIFT_LAPS;
    vec2 warp = vec2(
            loopFbm(field * SILK_WARP_SCALE + vec2(drift, drift * SILK_CROSS), SILK_TILE),
            loopFbm(field * SILK_WARP_SCALE + SILK_WARP_SHIFT - vec2(drift * SILK_CROSS, drift),
                    SILK_TILE));
    float flow = loopFbm(field * SILK_FLOW_SCALE + warp * SILK_WARP_GAIN, SILK_TILE);
    float bands = 0.5 + 0.5 * cos(TAU * (flow * SILK_BAND_COUNT + cycle * SILK_BAND_LAPS));
    float sheen = pow(bands, SILK_SHEEN_EXPONENT);

    vec3 tint = accentAt(SILK_RAMP_BIAS + flow * SILK_RAMP_GAIN + bands * SILK_BAND_TINT);
    vec3 base = mix(SILK_BASE_TOP, SILK_BASE_BOTTOM, pow(clamp(shade, 0.0, 1.0), SILK_DEPTH_EXPONENT));
    float body = SILK_BODY_FLOOR + SILK_BODY_GAIN * bands * bands;
    vec3 color = base + tint * body + vec3(sheen * SILK_SHEEN_GAIN);

    return color;
}

vec3 contour(vec2 uv, float aspect, float cycle, float shade) {
    vec2 field = vec2(uv.x, uv.y * aspect) * CONTOUR_TILE;
    float drift = cycle * CONTOUR_TILE * CONTOUR_DRIFT_LAPS;
    vec2 warp = vec2(
            loopFbm(field * CONTOUR_WARP_SCALE + vec2(drift, 0.0), CONTOUR_TILE),
            loopFbm(field * CONTOUR_WARP_SCALE + CONTOUR_WARP_SHIFT - vec2(0.0, drift), CONTOUR_TILE));
    float elevation = loopFbm(field + warp * CONTOUR_WARP_GAIN + vec2(drift, 0.0), CONTOUR_TILE);

    float levels = elevation * CONTOUR_LEVELS + cycle * CONTOUR_LEVEL_LAPS;
    float gradient = max(fwidth(levels), CONTOUR_MIN_GRADIENT);
    float ridge = (0.5 - abs(fract(levels) - 0.5)) / gradient;
    float major = 1.0 - step(0.5, mod(floor(levels), CONTOUR_MAJOR_EVERY));

    float width = mix(CONTOUR_MINOR_WIDTH, CONTOUR_MAJOR_WIDTH, major);
    float line = 1.0 - smoothstep(width - CONTOUR_LINE_AA, width + CONTOUR_LINE_AA, ridge);
    float glow = exp(-ridge / CONTOUR_GLOW_WIDTH) * CONTOUR_GLOW_GAIN;
    float strength = line * mix(CONTOUR_MINOR_GAIN, CONTOUR_MAJOR_GAIN, major) + glow;

    vec3 tint = accentAt(CONTOUR_RAMP_BIAS + elevation * CONTOUR_RAMP_GAIN);
    vec3 color = mix(CONTOUR_BASE_TOP, CONTOUR_BASE_BOTTOM, shade)
            + tint * elevation * CONTOUR_FILL_GAIN
            + tint * strength
            + vec3(line * major * CONTOUR_HIGHLIGHT);

    return color;
}

vec3 menuPattern(float mode, vec2 uv, float aspect, float cycle, float emberIntensity, int emberSwirl,
        vec3 frame) {
    if (abs(mode - MODE_NEBULA) < MODE_EPSILON) {
        return nebula(uv, aspect, cycle, frame.x);
    }
    if (abs(mode - MODE_SILK) < MODE_EPSILON) {
        return silk(uv, aspect, cycle, frame.x);
    }
    if (abs(mode - MODE_CONTOUR) < MODE_EPSILON) {
        return contour(uv, aspect, cycle, frame.x);
    }
    if (abs(mode - MODE_EMBER) < MODE_EPSILON) {
        return emberBody(uv, aspect, cycle, emberIntensity,
                TopLeftColor.rgb, BottomLeftColor.rgb, TopRightColor.rgb, emberSwirl, frame);
    }
    return vec3(0.0);
}

float menuVignetteStrength(float mode) {
    if (abs(mode - MODE_NEBULA) < MODE_EPSILON) {
        return NEBULA_VIGNETTE;
    }
    if (abs(mode - MODE_SILK) < MODE_EPSILON) {
        return SILK_VIGNETTE;
    }
    if (abs(mode - MODE_CONTOUR) < MODE_EPSILON) {
        return CONTOUR_VIGNETTE;
    }
    return 0.0;
}
