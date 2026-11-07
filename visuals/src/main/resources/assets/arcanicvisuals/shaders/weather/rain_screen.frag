#version 150

in vec2 TexCoord;

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform mat4 InvViewProj;
uniform vec3 CamPos;
uniform vec3 Tint;
uniform float Time;
uniform float Drops;
uniform float Splashes;
uniform float Aspect;
uniform vec4 Roof;
uniform float RoofSpan;

out vec4 OutColor;

const float DROP_CELLS = 8.0;
const float DROP_REFRACT = 0.028;
const float SPLASH_CELLS = 1.1;
const float SPLASH_RANGE = 15.0;
const float SPLASH_UP = 0.62;
const float ROOF_RANGE = 128.0;
const float SPLASH_CHANCE = 0.3;
const float SPLASH_LIFE = 0.32;

float hash21(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

vec3 worldFromDepth(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 pos = InvViewProj * clip;
    return pos.xyz / pos.w;
}

vec3 faceNormal(vec2 uv, float depth, vec3 view) {
    vec2 texel = 1.0 / vec2(textureSize(Sampler1, 0));

    vec2 uvR = uv + vec2(texel.x, 0.0);
    vec2 uvU = uv + vec2(0.0, texel.y);

    vec3 ddx = worldFromDepth(uvR, texture(Sampler1, uvR).r) - view;
    vec3 ddy = worldFromDepth(uvU, texture(Sampler1, uvU).r) - view;

    vec3 n = cross(ddx, ddy);
    float len = length(n);
    if (len < 1e-8) return vec3(0.0, 1.0, 0.0);

    n /= len;
    return dot(n, -view) < 0.0 ? -n : n;
}

void main() {
    vec2 uv = TexCoord;
    vec2 offset = vec2(0.0);
    float glass = 0.0;

    if (Drops > 0.001) {
        vec2 grid = vec2(uv.x * Aspect, uv.y) * DROP_CELLS;
        vec2 cell = floor(grid);
        vec2 local = fract(grid);

        float seed = hash21(cell);
        float fall = 0.25 + seed * 0.55;
        float life = fract(Time * fall + seed * 7.13);

        vec2 center = vec2(0.15 + 0.7 * hash21(cell + 13.7), 1.0 - life);
        float radius = 0.12 + 0.13 * hash21(cell + 4.21);

        vec2 delta = (local - center) / radius;
        float dist = length(delta);

        float body = 1.0 - smoothstep(0.55, 1.0, dist);
        float alive = smoothstep(0.0, 0.12, life) * (1.0 - smoothstep(0.75, 1.0, life));
        float drop = body * alive * Drops;

        offset += delta * drop * DROP_REFRACT;
        glass += drop;
    }

    vec2 sampleUv = uv + offset;
    if (sampleUv.x < 0.0 || sampleUv.x > 1.0 || sampleUv.y < 0.0 || sampleUv.y > 1.0) sampleUv = uv;
    vec4 src = texture(Sampler0, sampleUv);
    vec3 color = src.rgb;

    if (Splashes > 0.001) {
        float depth = texture(Sampler1, uv).r;
        if (depth < 1.0) {
            vec3 view = worldFromDepth(uv, depth);
            float distance = length(view);
            if (distance < SPLASH_RANGE) {
                vec3 normal = faceNormal(uv, depth, view);
                if (normal.y > SPLASH_UP) {
                    vec3 world = CamPos + view;

                    bool covered = false;
                    if (Roof.w > 0.5) {
                        vec2 roofUv = (world.xz - Roof.xy) / RoofSpan;
                        if (roofUv.x >= 0.0 && roofUv.x <= 1.0 && roofUv.y >= 0.0 && roofUv.y <= 1.0) {
                            float stored = texture(Sampler2, roofUv).r * 255.0;
                            float top = stored - ROOF_RANGE + Roof.z;
                            covered = world.y < top - 0.6;
                        }
                    }
                    if (covered) { OutColor = vec4(color, src.a); return; }
                    vec2 scaled = world.xz * SPLASH_CELLS;
                    vec2 cell = floor(scaled);

                    if (hash21(cell + 21.7) < SPLASH_CHANCE) {
                        float seed = hash21(cell + 5.31);
                        float cycle = 0.8 + seed * 1.5;
                        float phase = fract((Time + seed * 41.0) / cycle);

                        if (phase < SPLASH_LIFE) {
                            float t = phase / SPLASH_LIFE;
                            vec2 jitter = vec2(hash21(cell + 11.13), hash21(cell + 17.97)) - 0.5;
                            vec2 local = fract(scaled) - 0.5 - jitter * 0.55;

                            float thickness = 0.03 + distance * 0.004;
                            float ring = abs(length(local) - t * 0.3);
                            float wave = (1.0 - smoothstep(0.0, thickness, ring)) * (1.0 - t) * (1.0 - t);

                            float fade = 1.0 - smoothstep(SPLASH_RANGE * 0.4, SPLASH_RANGE, distance);
                            color += Tint * wave * fade * Splashes * 0.32;
                        }
                    }
                }
            }
        }
    }

    color += Tint * glass * 0.05;

    OutColor = vec4(color, src.a);
}
