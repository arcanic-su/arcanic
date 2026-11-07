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

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0/3.0, 2.0/3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + 1.0e-10)),
                d / (q.x + 1.0e-10), q.x);
}

vec3 hsv2rgb(vec3 c) {
    vec3 rgb = clamp(abs(mod(c.x * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
    return c.z * mix(vec3(1.0), rgb, c.y);
}

void main() {
    vec3 dir = worldDir();
    vec3 p = dir * uScale;
    float ts = uTime * uSpeed * 0.3;

    // Одноступенчатый domain warp — один fbm вместо четырёх.
    vec3 warp = vec3(ts, ts * 0.7, -ts * 0.5);
    float w = fbm3(p * 0.8 + warp);
    float flow = fbm3(p + w * 3.0);

    vec3 baseHsv = rgb2hsv(uColor);
    float hue = fract(baseHsv.x + (flow - 0.5) * 0.6);
    float sat = clamp(baseHsv.y + 0.3, 0.5, 1.0);
    float val = 0.15 + pow(flow, 1.5) * 1.4;

    vec3 col = hsv2rgb(vec3(hue, sat, val));

    float highlight = pow(smoothstep(0.7, 0.95, flow), 3.0);
    col += vec3(highlight * 0.4);

    col *= (0.5 + uIntensity * 40.0);

    // Vanilla-like linear_fog. dir.y=0 (горизонт) → distance=FogEnd (полный fog).
    // dir.y=1 (зенит) → distance=FogStart (нет fog). При FogEnd маленьком — весь sky в fog.
    float skyDist = mix(FogEnd, FogStart, clamp(dir.y, 0.0, 1.0));
    float fogValue = skyDist <= FogStart ? 0.0
                   : (skyDist < FogEnd ? smoothstep(FogStart, FogEnd, skyDist) : 1.0);
    col = mix(col, FogColor.rgb, fogValue * FogColor.a);

    fragColor = vec4(col, uAlpha);
}
