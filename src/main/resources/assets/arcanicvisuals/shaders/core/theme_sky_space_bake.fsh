#version 150

// A theme sky, baked into an equirectangular texture; uniform interface adapted to the Arcanic renderer.
layout(std140) uniform SkyBake {
    vec4 AccentTime;
    vec4 Size;
};
#define Time AccentTime.w
#define Accent AccentTime.rgb
#define TexCoord (gl_FragCoord.xy / Size.xy)

out vec4 OutColor;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
    p += dot(p, p.yzx + 19.19);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 x) {
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(p), hash(p + vec3(1,0,0)), f.x), mix(hash(p + vec3(0,1,0)), hash(p + vec3(1,1,0)), f.x), f.y), mix(mix(hash(p + vec3(0,0,1)), hash(p + vec3(1,0,1)), f.x), mix(hash(p + vec3(0,1,1)), hash(p + vec3(1,1,1)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float a = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 3; i++) {
        a += amp * noise(p);
        p *= 2.1;
        amp *= 0.5;
    }
    return a;
}

void main() {
    float longitude = (TexCoord.x - 0.5) * 6.28318530718;
    float latitude = (0.5 - TexCoord.y) * 3.14159265359;
    vec3 dir = vec3(cos(latitude) * cos(longitude), sin(latitude), cos(latitude) * sin(longitude));

    vec3 theme = max(Accent, vec3(0.03));
    float themeMax = max(theme.r, max(theme.g, theme.b));
    vec3 themeNorm = themeMax > 0.001 ? theme / themeMax : vec3(0.55, 0.52, 0.65);

    vec3 col = mix(vec3(0.008, 0.01, 0.045), theme * 0.22, 0.78);
    float zen = dir.y * 0.5 + 0.5;
    col += mix(vec3(0.02, 0.03, 0.08), theme * 0.14, 0.72) * pow(1.0 - zen, 2.5);

    vec3 p = dir * 2.8;
    float n = fbm(p + vec3(Time * 0.03, Time * 0.018, -Time * 0.02));
    float n2 = noise(p.yzx * 1.38 + vec3(1.9, 2.1, 0.7));
    float nebMask = smoothstep(0.24, 0.92, n * n2);
    vec3 nebula = mix(vec3(0.45, 0.12, 0.55), themeNorm * vec3(0.55, 0.35, 0.65) + theme * 0.35, 0.58);
    nebula = mix(nebula, mix(vec3(0.1, 0.35, 0.65), theme * 0.42, 0.52), n);
    col += nebula * 0.85 * pow(nebMask, 0.65);

    vec3 galAxis = normalize(vec3(0.1, 0.84, 0.16));
    float galW = abs(dot(dir, galAxis));
    col += mix(vec3(0.35, 0.32, 0.55), theme * 0.55 + themeNorm * 0.2, 0.55) * pow(1.0 - galW, 6.0) * 0.75;

    col = col / (col + vec3(0.85));
    col = pow(col, vec3(0.92));

    OutColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
