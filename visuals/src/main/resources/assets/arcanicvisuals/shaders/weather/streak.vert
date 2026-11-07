#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ViewProj;
uniform vec3 CamPos;
uniform vec3 CamRight;
uniform vec3 CamUp;
uniform vec3 Motion;
uniform vec3 Cell;
uniform vec4 Tint;
uniform float Time;
uniform float Size;
uniform float Drift;
uniform float Flicker;
uniform float Stretch;
uniform sampler2D Sampler0;
uniform vec4 Roof;
uniform float RoofSpan;

out vec2 Uv;
out vec4 Col;

const float TAU = 6.2831853;
const float EDGE_FADE_START = 0.34;
const float EDGE_FADE_END = 0.5;
const float ROOF_RANGE = 128.0;

void main() {
    float phase = Color.r * TAU;
    float speedVar = 0.55 + Color.g * 0.9;
    float sizeVar = 0.5 + Color.b * 1.1;

    vec3 seeded = Position + Motion * (Time * speedVar);
    seeded.x += sin(Time * 0.71 * speedVar + phase) * Drift;
    seeded.z += cos(Time * 0.53 * speedVar + phase * 1.37) * Drift;
    seeded.y += sin(Time * 0.31 * speedVar + phase * 0.79) * Drift * 0.35;

    vec3 rel = mod(seeded - CamPos + Cell * 0.5, Cell) - Cell * 0.5;

    float size = Size * sizeVar;

    vec3 flow = Motion;
    float flowLen = length(flow);
    vec2 axis = vec2(0.0, 1.0);
    float stretch = 1.0;
    if (flowLen > 1e-4 && Stretch > 1.001) {
        vec3 dir = flow / flowLen;
        vec2 screenDir = vec2(dot(dir, CamRight), dot(dir, CamUp));
        float screenLen = length(screenDir);
        if (screenLen > 1e-3) {
            axis = screenDir / screenLen;
            float across = pow(clamp(screenLen, 0.0, 1.0), 2.5);
            stretch = mix(1.0, Stretch, across);
        }
    }
    vec2 perp = vec2(-axis.y, axis.x);

    vec2 local = UV0 - 0.5;
    vec2 planar = axis * (local.y * size * stretch) + perp * (local.x * size);
    vec3 offset = CamRight * planar.x + CamUp * planar.y;

    gl_Position = ViewProj * vec4(rel + offset, 1.0);

    float twinkle = mix(1.0, 0.3 + 0.7 * (0.5 + 0.5 * sin(Time * 3.3 * speedVar + phase * 2.1)), Flicker);

    vec3 norm = abs(rel) / (Cell * 0.5);
    float edge = 1.0 - smoothstep(EDGE_FADE_START, EDGE_FADE_END, max(max(norm.x, norm.y), norm.z) * 0.5);

    float roofCut = 1.0;
    if (Roof.w > 0.5) {
        vec3 world = CamPos + rel;
        vec2 roofUv = (world.xz - Roof.xy) / RoofSpan;
        if (roofUv.x >= 0.0 && roofUv.x <= 1.0 && roofUv.y >= 0.0 && roofUv.y <= 1.0) {
            float stored = texture(Sampler0, roofUv).r * 255.0;
            float top = stored - ROOF_RANGE + Roof.z;
            roofCut = smoothstep(top - 1.5, top + 0.5, world.y);
        }
    }

    Uv = UV0;
    Col = vec4(Tint.rgb, Tint.a * Color.a * twinkle * edge * roofCut);
}
