#version 150

#moj_import <arcanicvisuals:arc_customsky_common.glsl>

in vec2 texCoord;
out vec4 fragColor;

const mat2 ROT = mat2(0.80, 0.60, -0.60, 0.80);
const float TAU = 6.28318530718;
const float PI = 3.14159265359;
const int METEORS = 10;
const float FLIGHT_WINDOW = 0.16;
const float BEAM_HALF_WIDTH = 0.02;
const vec3 NIGHT_BASE = vec3(0.014, 0.021, 0.050);
const float TONEMAP_CEILING = 16.0;

float hash21Grid(vec2 p) {
    uvec2 q = uvec2(ivec2(p)) * uvec2(1597334673U, 3812015801U);
    uint n = (q.x ^ q.y) * 1597334673U;
    return float(n) * (1.0 / 4294967295.0);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21Grid(i);
    float b = hash21Grid(i + vec2(1.0, 0.0));
    float c = hash21Grid(i + vec2(0.0, 1.0));
    float d = hash21Grid(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float amp = 0.5;
    float sum = 0.0;
    for (int i = 0; i < 4; i++) {
        sum += amp * valueNoise(p);
        p = ROT * p * 2.03;
        amp *= 0.5;
    }
    return sum;
}

vec3 meteors(vec3 rd, float time, vec3 themeTint) {
    vec3 sum = vec3(0.0);
    for (int i = 0; i < METEORS; i++) {
        float fi = float(i);
        vec3 gh = skyHash33(vec3(fi * 13.0 + 7.0, 51.0, 3.0));

        float period = mix(2.2, 6.5, gh.x);
        float cycle = time / period + gh.y * 19.0;
        float fly = fract(cycle);
        if (fly > FLIGHT_WINDOW) {
            continue;
        }
        float progress = fly / FLIGHT_WINDOW;

        float shower = floor(cycle);
        vec3 rnd = skyHash33(vec3(fi * 29.0 + 5.0, shower, 11.0));
        vec3 rnd2 = skyHash33(vec3(fi * 31.0 + 3.0, shower, 23.0));

        float azimuth = rnd.x * TAU;
        float elevation = mix(0.12, 0.95, rnd.y);
        vec3 headStart = vec3(cos(elevation) * cos(azimuth), sin(elevation), cos(elevation) * sin(azimuth));

        vec3 east = normalize(cross(vec3(0.0, 1.0, 0.0), headStart));
        vec3 north = cross(headStart, east);
        vec3 travel = normalize(-north + east * (rnd.z - 0.5) * 1.6);
        vec3 planeNormal = normalize(cross(headStart, travel));
        vec3 basisA = headStart;
        vec3 basisB = cross(planeNormal, basisA);

        float offPlane = dot(rd, planeNormal);
        if (abs(offPlane) > BEAM_HALF_WIDTH) {
            continue;
        }

        float along = atan(dot(rd, basisB), dot(rd, basisA));

        float arc = mix(0.35, 0.80, rnd2.x);
        float head = progress * arc;
        float tailLength = mix(0.10, 0.22, rnd2.y) * (0.35 + 0.65 * progress);

        float s = along - head;
        if (s > 0.02 || s < -(tailLength + 0.05)) {
            continue;
        }

        float envelope = pow(sin(progress * PI), 0.65);

        float width = mix(0.0016, 0.0026, rnd2.z);
        float line = exp(-offPlane * offPlane / (width * width));

        float tailT = clamp(-s / tailLength, 0.0, 1.0);
        float profile = (1.0 - tailT) * (1.0 - tailT);
        float trail = line * profile * step(s, 0.0);

        float distanceToHead = length(vec2(s, offPlane));
        float headCore = exp(-distanceToHead * distanceToHead / (0.0035 * 0.0035)) * 2.5;
        float headGlow = exp(-distanceToHead * distanceToHead / (0.012 * 0.012)) * 0.5;

        float sparkle = 0.85 + 0.15 * sin(along * 240.0 + shower * 12.0);

        vec3 hot = vec3(1.0, 0.97, 0.90);
        vec3 warm = mix(vec3(1.0, 0.55, 0.22), themeTint, 0.45);
        vec3 col = mix(hot, warm, tailT);

        sum += (trail * 1.35 * sparkle + headCore + headGlow) * col * envelope;
    }
    return sum;
}

void main() {
    float time = misc.x;
    float brightness = misc.w;

    vec3 rd = skyRayDirection(texCoord);
    float axis = atan(rd.z, rd.x);
    vec3 tint = skyTint(axis, 0.5, time);

    vec3 col = NIGHT_BASE * (0.55 + 0.45 * smoothstep(-1.0, 1.0, rd.y));
    col += skyStarField(rd, time, 0.06, 0.25) * 1.05;

    float band = exp(-pow((abs(rd.y) - 0.18) * 2.4, 2.0));
    float milky = fbm(rd.xz * 3.2 + rd.y * 1.7 + time * 0.004);
    col += tint * band * pow(clamp(milky, 0.0, 1.0), 2.4) * 0.10;

    col += meteors(rd, time, tint);

    fragColor = vec4(tanh(min(col * brightness, vec3(TONEMAP_CEILING))), 1.0);
}
