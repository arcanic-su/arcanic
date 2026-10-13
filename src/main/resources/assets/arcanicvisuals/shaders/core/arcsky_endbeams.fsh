#version 150

#moj_import <arcanicvisuals:arc_customsky_common.glsl>

uniform sampler2D NoiseTex;
uniform sampler2D History;

in vec2 texCoord;
out vec4 fragColor;

const int BEAM_SAMPLES = 32;
const float BEAM_MAX_DISTANCE = 1536.0;
const float BEAM_DISTRIBUTION = 1.3;
const float BEAM_FOG_CURVE = 0.55;

const float BEAM_CENTER_OFFSET = 25.0;
const float BEAM_HEIGHT = 1.0;
const float BEAM_INTENSITY = 3.0;
const float BEAM_ENERGY = 1.0;
const float BEAM_PLANE_SCALE = 0.12;
const float BEAM_WIND_RATE = 0.035;

const float BEAM_NOISE_LOW = 0.04375;
const float BEAM_NOISE_MID = 0.175;
const float BEAM_NOISE_HIGH = 0.7;
const float BEAM_WEIGHT_LOW = 2.0;
const float BEAM_WEIGHT_MID = 0.5;
const float BEAM_WEIGHT_HIGH = 0.5;
const float BEAM_WEIGHT_TOTAL = 3.0;
const float BEAM_WIND_LOW = 0.5;
const float FIRE_NOISE_SCALE = 0.1;
const float FIRE_CONTRAST = 1.6;

const float BEAM_SPREAD_BASE = 2000.0;
const float BEAM_SPREAD_FIRE = 40.0;
const float BEAM_SPREAD_UNIT = 0.001;
const float BEAM_SPREAD_RADIUS = 250.0;
const float BEAM_SPREAD_RADIUS_GAIN = 0.5;
const float BEAM_ALTITUDE_WIDE = 4.0;
const float BEAM_ALTITUDE_SPLIT = 0.5;
const float BEAM_ALTITUDE_POWER = 7.0;
const float BEAM_NOISE_MIX = 0.7;
const float BEAM_FIRE_MIX = 0.3;
const float BEAM_POWER = 1.75;
const float BEAM_DISTANCE_GAIN = 128.0;
const float ALTITUDE_EPSILON = 1.0e-3;

const float PURPLE_GAIN = 3.2;
const float EMBER_INTENSITY = 300.0;
const float EMBER_ENERGY_GAIN = 1050.0;
const float EMBER_BASE = 0.0004;
const float EMBER_GAIN = 0.2;
const float EMBER_DISTANCE = 256.0;
const float EMBER_CENTER = 0.5;
const float NORMALIZE_EPSILON = 1.0e-4;

const float STAR_PROJECTION = 0.65;
const float STAR_PLANE_SCALE = 0.5;
const float STAR_LOWER_OFFSET = 100.0;
const float STAR_GRID = 1024.0;
const float STAR_THRESHOLD = 0.7;
const float STAR_GAIN = 3000.0;
const float STAR_FADE_FLOOR = 0.015;
const float STAR_FADE_CEILING = 0.05;
const float STAR_HORIZON_EDGE = 0.25;
const float STAR_INTENSITY = 1.0;
const vec2 STAR_HASH_BASIS = vec2(12.9898, 4.1414);
const float STAR_HASH_SCALE = 43758.54953;
const float STAR_HASH_OFFSET_A = 0.1;
const float STAR_HASH_OFFSET_B = 0.23;

const float BASE_SKY_LEVEL = 0.03;
const float BASE_TINT = 0.97;
const float EXPOSURE = 0.185;

const float IGN_SCALE = 52.9829189;
const vec2 IGN_BASIS = vec2(0.06711056, 0.00583715);
const float TAA_BLEND = 0.92;
const float TAA_CLAMP = 0.25;
const float TAA_GUARD = 1.5;
const float TAA_EPSILON = 1.0e-5;
const float DITHER_STEP = 1.0 / 255.0;
const float DITHER_OFFSET = 13.7;

float interleavedGradient(vec2 p) {
    return fract(IGN_SCALE * fract(dot(p, IGN_BASIS)));
}

vec3 emberColor() {
    return skyColor.w < SKY_GRADIENT_DUAL ? skyColor.rgb : skyColor2.rgb;
}

float starHash(vec2 pos) {
    return fract(sin(dot(pos, STAR_HASH_BASIS)) * STAR_HASH_SCALE);
}

vec3 enderStars(vec3 dir, vec3 tint) {
    vec3 projected = STAR_PROJECTION * dir / (abs(dir.y) + length(dir.xz));
    vec2 cell = projected.xz * STAR_PLANE_SCALE;
    if (dir.y < 0.0) {
        cell += STAR_LOWER_OFFSET;
    }
    cell = floor(cell * STAR_GRID) / STAR_GRID;

    float star = starHash(cell)
            * starHash(cell + STAR_HASH_OFFSET_A)
            * starHash(cell + STAR_HASH_OFFSET_B);
    star = max(star - STAR_THRESHOLD, 0.0);
    star *= star;

    float up = abs(dir.y);
    float fade = 1.0 - up;
    vec3 stars = star * tint * STAR_GAIN;
    stars *= min(fade * fade + STAR_FADE_FLOOR, STAR_FADE_CEILING) + STAR_FADE_FLOOR;
    stars *= smoothstep(0.0, STAR_HORIZON_EDGE, up);
    return stars * STAR_INTENSITY;
}

float beamNoise(vec2 planeCoord, vec2 wind) {
    float noise = textureLod(NoiseTex, planeCoord * BEAM_NOISE_MID - wind, 0.0).b * BEAM_WEIGHT_MID;
    noise += textureLod(NoiseTex, planeCoord * BEAM_NOISE_LOW + wind * BEAM_WIND_LOW, 0.0).b * BEAM_WEIGHT_LOW;
    noise += textureLod(NoiseTex, planeCoord * BEAM_NOISE_HIGH - wind, 0.0).b * BEAM_WEIGHT_HIGH;
    return noise / BEAM_WEIGHT_TOTAL;
}

vec3 enderBeams(vec3 pos, vec2 wind, vec3 purple, vec3 ember) {
    float radius = length(pos.xz);
    vec2 planeCoord = pos.xz * BEAM_PLANE_SCALE;

    float noise = beamNoise(planeCoord, wind);
    float fire = textureLod(NoiseTex, abs(planeCoord * FIRE_NOISE_SCALE) - wind, 0.0).b;
    fire = clamp((fire - EMBER_CENTER) * FIRE_CONTRAST + EMBER_CENTER, 0.0, 1.0);

    float spread = (BEAM_SPREAD_BASE * noise * noise + BEAM_SPREAD_FIRE * fire)
            * BEAM_SPREAD_UNIT * BEAM_HEIGHT * (BEAM_SPREAD_RADIUS + BEAM_SPREAD_RADIUS_GAIN * radius);
    spread = max(spread, ALTITUDE_EPSILON);

    float altitude = abs(pos.y - BEAM_CENTER_OFFSET);
    float altitudeFactor = BEAM_ALTITUDE_SPLIT * (1.0 - smoothstep(0.0, spread, altitude))
            + BEAM_ALTITUDE_SPLIT * (1.0 - smoothstep(0.0, BEAM_ALTITUDE_WIDE * spread, altitude));

    float shape = noise * noise * BEAM_NOISE_MIX + BEAM_FIRE_MIX * fire;
    shape *= pow(altitudeFactor, BEAM_ALTITUDE_POWER);
    shape = pow(shape, BEAM_POWER);

    float emberEdge = fire - EMBER_CENTER;
    emberEdge *= emberEdge;
    vec3 beamColor = purple + ember
            * (EMBER_BASE + EMBER_GAIN * smoothstep(0.0, EMBER_DISTANCE, radius) * emberEdge * emberEdge);

    return shape * BEAM_INTENSITY * beamColor * (1.0 + smoothstep(0.0, BEAM_DISTANCE_GAIN, radius));
}

float fogWeight(float distance) {
    float t = pow(distance / BEAM_MAX_DISTANCE, BEAM_FOG_CURVE);
    return t * t * (3.0 - 2.0 * t);
}

vec3 sampleHistory(vec2 uv, vec2 res) {
    vec2 samplePos = uv * res;
    vec2 texPos = floor(samplePos - 0.5) + 0.5;
    vec2 f = samplePos - texPos;

    vec2 w0 = f * (-0.5 + f * (1.0 - 0.5 * f));
    vec2 w1 = 1.0 + f * f * (-2.5 + 1.5 * f);
    vec2 w2 = f * (0.5 + f * (2.0 - 1.5 * f));
    vec2 w3 = f * f * (-0.5 + 0.5 * f);

    vec2 w12 = w1 + w2;
    vec2 offset12 = w2 / w12;

    vec2 p0 = (texPos - 1.0) / res;
    vec2 p3 = (texPos + 2.0) / res;
    vec2 p12 = (texPos + offset12) / res;

    vec3 result = vec3(0.0);
    result += textureLod(History, vec2(p0.x, p0.y), 0.0).rgb * (w0.x * w0.y);
    result += textureLod(History, vec2(p12.x, p0.y), 0.0).rgb * (w12.x * w0.y);
    result += textureLod(History, vec2(p3.x, p0.y), 0.0).rgb * (w3.x * w0.y);
    result += textureLod(History, vec2(p0.x, p12.y), 0.0).rgb * (w0.x * w12.y);
    result += textureLod(History, vec2(p12.x, p12.y), 0.0).rgb * (w12.x * w12.y);
    result += textureLod(History, vec2(p3.x, p12.y), 0.0).rgb * (w3.x * w12.y);
    result += textureLod(History, vec2(p0.x, p3.y), 0.0).rgb * (w0.x * w3.y);
    result += textureLod(History, vec2(p12.x, p3.y), 0.0).rgb * (w12.x * w3.y);
    result += textureLod(History, vec2(p3.x, p3.y), 0.0).rgb * (w3.x * w3.y);

    return clamp(result, vec3(0.0), vec3(1.0));
}

void main() {
    float time = misc.x;
    float brightness = misc.w;
    vec2 res = vec2(textureSize(History, 0));

    vec3 rd = skyRayDirection(texCoord);
    vec3 direction = skyRayDirection(texCoord + taa.xy / res);

    vec3 tint = skyColor.rgb;
    vec3 purple = normalize(max(tint * tint * tint, vec3(NORMALIZE_EPSILON)))
            * (PURPLE_GAIN - BEAM_ENERGY);
    vec3 ember = emberColor() * (EMBER_INTENSITY + EMBER_ENERGY_GAIN * BEAM_ENERGY);

    float dither = fract(interleavedGradient(gl_FragCoord.xy) + taa.w);
    vec2 wind = vec2(time * BEAM_WIND_RATE);

    vec3 color = mix(vec3(1.0), tint, BASE_TINT) * BASE_SKY_LEVEL;
    color += enderStars(direction, tint);

    float lastWeight = 0.0;
    for (int i = 0; i < BEAM_SAMPLES; i++) {
        float t = (float(i) + dither) / float(BEAM_SAMPLES);
        float travel = pow(t, BEAM_DISTRIBUTION) * BEAM_MAX_DISTANCE;
        float weight = fogWeight(travel);
        color += enderBeams(direction * travel, wind, purple, ember) * (weight - lastWeight);
        lastWeight = weight;
    }

    color *= EXPOSURE * brightness;

    vec3 current = skyEncodeHDR(color);

    if (taa.z > 0.5) {
        vec4 projected = prevViewProj * vec4(rd, 0.0);
        if (projected.w > TAA_EPSILON) {
            vec2 previousUv = projected.xy / projected.w * 0.5 + 0.5;
            vec2 guard = TAA_GUARD / res;
            if (all(greaterThan(previousUv, guard)) && all(lessThan(previousUv, 1.0 - guard))) {
                vec3 history = clamp(sampleHistory(previousUv, res),
                        current - TAA_CLAMP, current + TAA_CLAMP);
                current = mix(current, history, TAA_BLEND);
            }
        }
    }

    current += (fract(skyHash21(gl_FragCoord.xy + DITHER_OFFSET) + taa.w) - 0.5) * DITHER_STEP;

    fragColor = vec4(max(current, vec3(0.0)), 1.0);
}
