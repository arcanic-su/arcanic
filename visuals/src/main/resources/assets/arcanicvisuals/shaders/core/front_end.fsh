#version 330

#moj_import <arcanicvisuals:ember.glsl>

uniform sampler2D Sampler0;

in vec2 splashState;
in vec4 splashTint;
in vec2 clipPosition;

out vec4 fragColor;

const int PALETTE_START = 0;
const int PALETTE_MIDDLE = 1;
const int PALETTE_END = 2;
const float MIN_PIXEL = 1e-6;
const float BAR_OFFSET_BOTTOM = 0.12;
const float BAR_HALF_OF_WIDTH = 0.16;
const float BAR_HALF_OF_HEIGHT = 0.26;
const float BAR_THICKNESS = 0.0026;
const float BAR_MIN_THICKNESS = 2.0;
const float BAR_TRACK_ALPHA = 0.12;
const float BAR_TAIL_HEAT = 0.45;
const float BAR_GLOW_HEAT = 0.8;
const float BAR_GLOW_RADIUS = 9.0;
const float BAR_GLOW_GAIN = 0.55;
const float BAR_GLOW_THRESHOLD = 0.001;
const float EDGE_SOFTNESS = 0.5;
const float DITHER_STRENGTH = 1.5 / 255.0;
const vec2 DITHER_SEED = vec2(0.7, 0.31);

float splashHash(vec2 point) {
    vec3 p = fract(vec3(point.xyx) * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

void main() {
    vec2 pixel = max(abs(vec2(dFdx(clipPosition.x), dFdy(clipPosition.y))), vec2(MIN_PIXEL));
    vec2 resolution = 2.0 / pixel;
    vec2 uv = vec2(clipPosition.x * 0.5 + 0.5, 0.5 - clipPosition.y * 0.5);
    vec2 frag = uv * resolution;
    float aspect = resolution.y / resolution.x;
    float progress = clamp(splashState.y, 0.0, 1.0);

    vec3 start = texelFetch(Sampler0, ivec2(PALETTE_START, 0), 0).rgb;
    vec3 middle = texelFetch(Sampler0, ivec2(PALETTE_MIDDLE, 0), 0).rgb;
    vec3 end = texelFetch(Sampler0, ivec2(PALETTE_END, 0), 0).rgb;
    vec3 color = ember(uv, aspect, splashState.x, progress, start, middle, end) * 0.46;

    float barAlpha = splashTint.r;
    float halfWidth = min(resolution.x * BAR_HALF_OF_WIDTH, resolution.y * BAR_HALF_OF_HEIGHT);
    float thickness = max(BAR_MIN_THICKNESS, resolution.y * BAR_THICKNESS);
    float radius = thickness * 0.5;
    vec2 barCenter = vec2(resolution.x * 0.5, resolution.y * (1.0 - BAR_OFFSET_BOTTOM));
    vec2 q = abs(frag - barCenter) - vec2(halfWidth - radius, 0.0);
    float track = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    float trackMask = 1.0 - smoothstep(-EDGE_SOFTNESS, EDGE_SOFTNESS, track);

    float left = barCenter.x - halfWidth;
    float head = left + progress * 2.0 * halfWidth;
    float fillMask = trackMask * clamp(head - frag.x + EDGE_SOFTNESS, 0.0, 1.0);
    float along = clamp((frag.x - left) / max(head - left, 1.0), 0.0, 1.0);
    vec3 fillColor = emberRamp(mix(BAR_TAIL_HEAT, 1.0, along), start, middle, end);
    float glow = exp(-length(frag - vec2(head, barCenter.y)) / (thickness * BAR_GLOW_RADIUS))
            * BAR_GLOW_GAIN * step(BAR_GLOW_THRESHOLD, progress);

    color = mix(color, vec3(1.0), trackMask * BAR_TRACK_ALPHA * barAlpha);
    color = mix(color, fillColor, fillMask * barAlpha);
    color += emberRamp(BAR_GLOW_HEAT, start, middle, end) * glow * barAlpha;
    color += (splashHash(gl_FragCoord.xy + DITHER_SEED) - 0.5) * DITHER_STRENGTH;

    fragColor = vec4(clamp(color, 0.0, 1.0), splashTint.a);
}
