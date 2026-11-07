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

float fbm3(vec3 p) {
    float v = 0.5 * vnoise3(p);          p *= 2.03;
    v +=      0.25 * vnoise3(p);         p *= 2.03;
    v +=      0.125 * vnoise3(p);
    return v;
}

vec3 worldDir() {
    float t = tan(radians(uFov) * 0.5);
    float aspect = uResolution.x / uResolution.y;
    return normalize(uCamRight * (vScreen.x * aspect * t)
                   + uCamUp    * (vScreen.y * t)
                   + uCamForward);
}

void main() {
    vec3 dir = worldDir();
    float ts = uTime * uSpeed * 0.25;

    // Плавный фон по всему куполу — без резкой границы у горизонта.
    // Внизу почти чёрный, вверху лёгкий оттенок uColor.
    float skyFade = smoothstep(-0.3, 0.5, dir.y);
    vec3 col = mix(uColor * 0.02, uColor * 0.08, skyFade);

    // Штора рендерится только если dir.y > 0. Ниже — просто плавный фон,
    // так что перехода не видно.
    if (dir.y > -0.05) {
        float h = clamp(sqrt(max(dir.y, 0.0)), 0.0, 1.0);

        vec3 p = vec3(dir.x, 0.0, dir.z) * uScale;
        float warp = fbm3(p * 0.6 + vec3(ts, 0.0, ts * 0.7));

        float band = sin((dir.x * 3.0 + warp * 6.0 + dir.z * 2.0) * uScale * 0.3 + ts);
        band = pow(0.5 + 0.5 * band, 4.0);

        float curtain = band * (1.0 - h * 0.85) * smoothstep(0.0, 0.15, dir.y);
        float threads = vnoise3(vec3(dir.x, dir.y * 3.0, dir.z) * uScale * 2.0 + vec3(ts * 2.0));
        curtain *= 0.5 + 0.6 * threads;

        vec3 topColor = uColor.bgr * 0.7 + vec3(0.4, 0.1, 0.5) * 0.6;
        vec3 gradient = mix(uColor, topColor, h);

        col += gradient * curtain * (1.2 + uIntensity * 40.0);
        col += gradient * curtain * curtain * 0.3;
    }

    // Vanilla-like linear_fog. dir.y=0 (горизонт) → distance=FogEnd (полный fog).
    // dir.y=1 (зенит) → distance=FogStart (нет fog). При FogEnd маленьком — весь sky в fog.
    float skyDist = mix(FogEnd, FogStart, clamp(dir.y, 0.0, 1.0));
    float fogValue = skyDist <= FogStart ? 0.0
                   : (skyDist < FogEnd ? smoothstep(FogStart, FogEnd, skyDist) : 1.0);
    col = mix(col, FogColor.rgb, fogValue * FogColor.a);

    fragColor = vec4(col, uAlpha);
}
