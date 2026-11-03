#version 150
// Animated sky equations; uniform interface adapted to the Arcanic renderer.
layout(std140) uniform ShaderSky {
    vec4 TimeResolution;
    vec4 ColorAlpha;
    vec4 Effect;
    vec4 CameraRight;
    vec4 CameraUp;
    vec4 CameraForward;
    vec4 Neon1;
    vec4 Neon2;
    vec4 Neon3;
    vec4 FogRange;
    vec4 FogTint;
};
#define uTime TimeResolution.x
#define uResolution TimeResolution.yz
#define uSpeed TimeResolution.w
#define uColor ColorAlpha.rgb
#define uAlpha ColorAlpha.a
#define uScale Effect.x
#define uIntensity Effect.y
#define uFov Effect.z
#define uCamRight CameraRight.xyz
#define uCamUp CameraUp.xyz
#define uCamForward CameraForward.xyz
#define uColor1 Neon1.rgb
#define uColor2 Neon2.rgb
#define uColor3 Neon3.rgb
#define FogStart FogRange.x
#define FogEnd FogRange.y
#define FogColor FogTint
#define FogShape 0


in vec2 vScreen;

out vec4 fragColor;

float hash3(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float vnoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash3(i),                    hash3(i + vec3(1,0,0)), u.x),
                   mix(hash3(i + vec3(0,1,0)),      hash3(i + vec3(1,1,0)), u.x), u.y),
               mix(mix(hash3(i + vec3(0,0,1)),      hash3(i + vec3(1,0,1)), u.x),
                   mix(hash3(i + vec3(0,1,1)),      hash3(i + vec3(1,1,1)), u.x), u.y), u.z);
}

vec3 worldDir() {
    float t = tan(radians(uFov) * 0.5);
    float aspect = uResolution.x / uResolution.y;
    return normalize(uCamRight * (vScreen.x * aspect * t)
                   + uCamUp    * (vScreen.y * t)
                   + uCamForward);
}

// Точечная звезда в клетке решётки. Разбиваем dir на клетки, в каждой клетке
// хешем определяем: есть ли звезда, где она в клетке, её размер и фазу мерцания.
// Даёт настоящие ТОЧЕЧНЫЕ звёзды, а не размытый noise-градиент.
float starLayer(vec3 dir, float density, float ts) {
    vec3 c = floor(dir);
    vec3 f = fract(dir) - 0.5;

    // Есть ли звезда в этой клетке (~10-30% клеток по density).
    float has = step(1.0 - density, hash3(c));
    if (has < 0.5) return 0.0;

    // Позиция в клетке (-0.4..0.4), размер, фаза.
    vec3 pos = vec3(hash3(c + 1.7), hash3(c + 3.1), hash3(c + 5.9)) - 0.5;
    pos *= 0.8;
    float size = 0.02 + 0.06 * hash3(c + 9.3);
    float phase = hash3(c + 11.7) * 6.28318;

    // Мерцание: индивидуальная частота и амплитуда.
    float twinkle = 0.4 + 0.6 * (0.5 + 0.5 * sin(ts * (1.5 + hash3(c + 13.1) * 2.5) + phase));

    // Плавное падение яркости от центра клетки. Работаем с угловым
    // расстоянием — звезда видна на нескольких пикселях, не только точно на центре.
    float d = length(f - pos);
    return smoothstep(size, 0.0, d) * twinkle;
}

void main() {
    vec3 dir = worldDir();
    float ts = uTime * uSpeed;

    vec3 col = vec3(0.02, 0.02, 0.05);

    // Млечный путь.
    float milkyBand = 1.0 - abs(dir.y * 0.7 + dir.x * 0.3 - 0.1);
    milkyBand = pow(clamp(milkyBand, 0.0, 1.0), 8.0);
    float milkyClouds = vnoise3(dir * uScale * 2.0);
    col += vec3(0.15, 0.18, 0.3) * milkyBand * milkyClouds;

    // Три слоя звёзд — разные плотности и масштабы.
    float small  = starLayer(dir * 90.0, 0.35, ts);
    float medium = starLayer(dir * 45.0, 0.18, ts) * 1.5;
    float big    = starLayer(dir * 22.0, 0.10, ts) * 2.5;

    // Оттенок звезды от её позиции.
    float tint = hash3(floor(dir * 30.0));
    vec3 starColor = mix(uColor * 0.85, uColor * 1.15, tint);

    col += starColor * (small + medium + big);
    col *= (0.6 + uIntensity * 40.0);

    // Vanilla-like linear_fog. dir.y=0 (горизонт) → distance=FogEnd (полный fog).
    // dir.y=1 (зенит) → distance=FogStart (нет fog). При FogEnd маленьком — весь sky в fog.
    float skyDist = mix(FogEnd, FogStart, clamp(dir.y, 0.0, 1.0));
    float fogValue = skyDist <= FogStart ? 0.0
                   : (skyDist < FogEnd ? smoothstep(FogStart, FogEnd, skyDist) : 1.0);
    col = mix(col, FogColor.rgb, fogValue * FogColor.a);

    fragColor = vec4(col, uAlpha);
}
