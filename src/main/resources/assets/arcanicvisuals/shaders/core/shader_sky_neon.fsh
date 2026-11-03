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

vec3 worldDir() {
    float t = tan(radians(uFov) * 0.5);
    float aspect = uResolution.x / uResolution.y;
    return normalize(uCamRight * (vScreen.x * aspect * t)
                   + uCamUp    * (vScreen.y * t)
                   + uCamForward);
}

// Портированный shadertoy Nguyen2007-13, спроецированный на sky-купол.
// Оригинал строит u = 0.2*(fragCoord+fragCoord-v)/v.y, что даёт диапазон ~[-0.2..0.2]*aspect.
// Для sky-купола берём стереографическую проекцию направления взгляда на плоскость касательную к зениту:
// точки под нами дают u -> inf, точки на горизонте u ~ 1, зенит u = 0.
// Умножаем на 0.15 чтобы попасть в тот же малый диапазон, где 0.5 - dot(u,u) > 0.
void main() {
    vec3 dir = worldDir();

    // Стереографическая проекция от нижнего полюса на плоскость z=1:
    // (x, z) в плоскости, y — «высота». Клампим y чтобы не делить на 0 у нижней точки.
    float denom = 1.0 + max(dir.y, -0.95);
    vec2 uv = vec2(dir.x, dir.z) / denom;
    vec2 u = uv * 0.15 * uScale;

    vec2 v = vec2(1.0);
    vec4 z = vec4(uColor1, 0.0);
    vec4 o = vec4(0.0);
    float a = 0.5;
    float t = uTime * uSpeed;

    for (float i = 0.0; i < 19.0; ) {
        i += 1.0;

        vec4 tintPhase = vec4(uColor2, uColor3.x);
        float safeDenom = max(abs(0.5 - dot(u, u)), 0.05);
        o += (1.0 + cos(z + t + tintPhase))
           / length((1.0 + i * dot(v, v))
                    * sin(1.5 * u / safeDenom - 9.0 * u.yx + t));

        t += 1.0;
        v = cos(t - 7.0 * u * pow(a += 0.03, i)) - 5.0 * u;

        float c = cos(i + 0.02 * t - z.w * 11.0);
        float s = sin(i + 0.02 * t - z.w * 11.0);
        mat2 rot = mat2(c, -s, s, c);
        u = u * rot;

        u += tanh(40.0 * dot(u, u) * cos(1e2 * u.yx + t)) / 2e2
           + 0.2 * a * u
           + cos(4.0 / exp(dot(o, o) / 1e2) + t) / 3e2;
    }

    o = 25.6 / (min(o, vec4(13.0)) + 164.0 / max(o, vec4(0.001))) - dot(u, u) / 250.0;

    vec3 col = o.rgb * (0.5 + uIntensity * 40.0);

    float skyDist = mix(FogEnd, FogStart, clamp(dir.y, 0.0, 1.0));
    float fogValue = skyDist <= FogStart ? 0.0
                   : (skyDist < FogEnd ? smoothstep(FogStart, FogEnd, skyDist) : 1.0);
    col = mix(col, FogColor.rgb, fogValue * FogColor.a);

    fragColor = vec4(col, uAlpha);
}
