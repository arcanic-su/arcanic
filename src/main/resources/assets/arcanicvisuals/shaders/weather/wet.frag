#version 150

in vec2 TexCoord;

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

uniform mat4 ViewProj;
uniform mat4 InvViewProj;
uniform vec3 CamPos;
uniform vec3 SkyTint;
uniform vec3 SunDir;
uniform float Reflectivity;
uniform float Wetness;
uniform float Ripple;
uniform float Gloss;
uniform float Steps;
uniform float HitThickness;
uniform float MaxDistance;
uniform float UpOnly;
uniform float Time;

out vec4 OutColor;

const int MAX_STEPS = 48;
const int REFINE_STEPS = 4;
const float STEP_GROWTH = 0.12;
const float SURFACE_BIAS = 0.035;
const float EDGE_FADE = 0.12;
const float SILHOUETTE_LIMIT = 0.55;
const float SPEC_POWER = 190.0;

vec3 worldFromDepth(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 pos = InvViewProj * clip;
    return pos.xyz / pos.w;
}

vec3 faceNormal(vec2 uv, float depth, vec3 view, out float discontinuity) {
    vec2 texel = 1.0 / vec2(textureSize(Sampler1, 0));

    vec2 uvL = uv - vec2(texel.x, 0.0);
    vec2 uvR = uv + vec2(texel.x, 0.0);
    vec2 uvD = uv - vec2(0.0, texel.y);
    vec2 uvU = uv + vec2(0.0, texel.y);

    float dL = texture(Sampler1, uvL).r;
    float dR = texture(Sampler1, uvR).r;
    float dD = texture(Sampler1, uvD).r;
    float dU = texture(Sampler1, uvU).r;

    bool leftCloser = abs(dL - depth) < abs(dR - depth);
    bool downCloser = abs(dD - depth) < abs(dU - depth);

    vec3 ddx = leftCloser ? view - worldFromDepth(uvL, dL) : worldFromDepth(uvR, dR) - view;
    vec3 ddy = downCloser ? view - worldFromDepth(uvD, dD) : worldFromDepth(uvU, dU) - view;

    float depthDistance = length(view);
    discontinuity = max(length(ddx), length(ddy)) / max(depthDistance, 1.0);

    vec3 n = cross(ddx, ddy);
    float len = length(n);
    if (len < 1e-8) return normalize(-view);

    n /= len;
    return dot(n, -view) < 0.0 ? -n : n;
}

float hash12(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

float rippleNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), f.x),
               mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), f.x), f.y);
}

vec2 rippleGradient(vec2 p) {
    const float e = 0.25;
    float base = rippleNoise(p);
    return vec2(rippleNoise(p + vec2(e, 0.0)) - base, rippleNoise(p + vec2(0.0, e)) - base) / e;
}

float interleavedGradient(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

float edgeFade(vec2 uv) {
    vec2 fade = smoothstep(vec2(0.0), vec2(EDGE_FADE), uv)
              * smoothstep(vec2(0.0), vec2(EDGE_FADE), 1.0 - uv);
    return fade.x * fade.y;
}

void main() {
    vec4 src = texture(Sampler0, TexCoord);
    float depth = texture(Sampler1, TexCoord).r;
    if (depth >= 1.0) { OutColor = src; return; }

    vec3 view = worldFromDepth(TexCoord, depth);
    float discontinuity;
    vec3 normal = faceNormal(TexCoord, depth, view, discontinuity);
    if (discontinuity > SILHOUETTE_LIMIT) { OutColor = src; return; }

    float mask = mix(1.0, smoothstep(0.5, 0.82, normal.y), UpOnly);
    if (mask <= 0.002) { OutColor = src; return; }

    vec3 world = CamPos + view;

    if (Ripple > 0.001 && normal.y > 0.1) {
        vec2 flow = world.xz * 0.85 + vec2(Time * 0.31, Time * 0.19);
        vec2 gradient = rippleGradient(flow);
        normal = normalize(normal + vec3(gradient.x, 0.0, gradient.y) * Ripple * 0.22);
    }

    vec3 incident = normalize(view);

    float lum = dot(src.rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 soaked = mix(vec3(lum), src.rgb, 1.0 + 0.4 * Wetness) * (1.0 - 0.32 * Wetness);
    soaked = mix(src.rgb, soaked, mask);

    vec3 reflected = reflect(incident, normal);
    vec3 origin = view + normal * SURFACE_BIAS;

    int steps = clamp(int(Steps), 8, MAX_STEPS);
    float stepLength = MaxDistance / float(steps);
    float jitter = interleavedGradient(gl_FragCoord.xy);

    float travelled = stepLength * (0.35 + jitter * 0.65);
    float previous = travelled;
    vec2 hitUv = vec2(0.0);
    float hit = 0.0;

    for (int i = 0; i < MAX_STEPS; i++) {
        if (i >= steps) break;

        vec3 marchPos = origin + reflected * travelled;
        vec4 clip = ViewProj * vec4(marchPos, 1.0);
        if (clip.w <= 0.001) break;

        vec2 uv = (clip.xy / clip.w) * 0.5 + 0.5;
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) break;

        float sceneDepth = texture(Sampler1, uv).r;
        if (sceneDepth < 1.0) {
            float behind = length(marchPos) - length(worldFromDepth(uv, sceneDepth));
            if (behind > 0.0 && behind < HitThickness + travelled * 0.08) {
                float lo = previous;
                float hi = travelled;
                for (int r = 0; r < REFINE_STEPS; r++) {
                    float mid = (lo + hi) * 0.5;
                    vec3 probe = origin + reflected * mid;
                    vec4 probeClip = ViewProj * vec4(probe, 1.0);
                    vec2 probeUv = clamp((probeClip.xy / max(probeClip.w, 1e-4)) * 0.5 + 0.5, 0.0, 1.0);
                    float probeDepth = texture(Sampler1, probeUv).r;
                    float probeBehind = length(probe) - length(worldFromDepth(probeUv, probeDepth));
                    if (probeBehind > 0.0) { hi = mid; uv = probeUv; } else { lo = mid; }
                }
                hitUv = uv;
                hit = edgeFade(uv) * (1.0 - smoothstep(MaxDistance * 0.6, MaxDistance, hi));
                break;
            }
        }

        previous = travelled;
        travelled += stepLength * (1.0 + float(i) * STEP_GROWTH);
    }

    vec3 reflection = mix(SkyTint, texture(Sampler0, hitUv).rgb, hit);

    float fresnel = 0.02 + 0.98 * pow(1.0 - clamp(dot(normal, -incident), 0.0, 1.0), 5.0);
    float amount = clamp(Reflectivity * (0.12 + 0.88 * fresnel), 0.0, 1.0) * mask;

    vec3 halfway = normalize(SunDir - incident);
    float specular = pow(max(dot(normal, halfway), 0.0), SPEC_POWER) * Gloss * mask * max(SunDir.y, 0.0);

    vec3 lit = mix(soaked, reflection, amount) + specular;

    OutColor = vec4(clamp(lit, 0.0, 1.0), src.a);
}
