#version 330
// World lyrics word, after the EvoVisuals lyrics font: an MTSDF glyph that burns in along a noisy front with an
// ember edge, dissolves into drifting grain when it leaves, fills with the accent while it is sung, and carries a
// slow shimmer band and a soft glow. Pass 1 draws only a soft dark backdrop under the letters for bright skies.
// Evo measures the sweep and noise in screen pixels; here they are measured on the word's own sheet (the same scale:
// one glyph height), so they stay on the letters instead of swimming when the camera or the word moves.
layout(std140) uniform LyricsWord {
    vec4 Origin;
    vec4 Right;
    vec4 Down;
    vec4 Shape;
    vec4 Motion;
    vec4 Fill;
    vec4 Accent;
    vec4 GlowColour;
};
uniform sampler2D Sampler0;
in vec2 TexCoord;
in vec4 FragColor;
in vec2 PlanePos;
out vec4 OutColor;

const float MTSDF_ALPHA_MIX = 0.28;
const float EDGE_GAMMA = 0.8;
const float MIN_EDGE_PX = 0.5;
const float MAX_SOFTNESS = 9.0;
const float NOISE_FREQUENCY = 1.35;
const float REVEAL_SWEEP_WEIGHT = 0.62;
const float REVEAL_NOISE_WEIGHT = 0.38;
const float REVEAL_OVERSHOOT = 1.18;
const float REVEAL_LEAD = 0.09;
const float REVEAL_EDGE_WIDTH = 0.085;
const float EXIT_EDGE_WIDTH = 0.07;
const float EXIT_DRIFT = 0.45;
const float SHIMMER_SPEED = 0.22;
const float SHIMMER_WIDTH = 0.16;
const float SHIMMER_GAIN = 0.1;
const float SHIMMER_SLANT = 0.035;
const float GLOW_FIELD_SCALE = 2.0;
const float GLOW_SHAPE_BASE = 6.0;
const float GLOW_MIN_RADIUS = 0.25;
const float EMBER_GAIN = 0.9;
const float EMBER_LIFT = 0.2;
const float ALPHA_CUTOFF = 0.004;
const float SHADOW_INNER = -0.24;
const float SHADOW_OUTER = 0.05;
const float SHADOW_POWER = 1.4;
const float SUNG_TINT = 0.62;
const float SUNG_LIFT = 0.18;
const float FILL_EDGE_WIDTH = 0.08;
const float FILL_EDGE_GAIN = 0.32;
const float GRADIENT_TINT = 0.75;
const float GRADIENT_WAVE_SPEED = 0.35;

float median(vec3 c) {
    return max(min(c.r, c.g), min(max(c.r, c.g), c.b));
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * .1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = p - i;
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), u.x),
               mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 x) {
    float v = 0.0;
    float a = 0.5;
    vec2 shift = vec2(100.0);
    mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.5));
    for (int i = 0; i < 5; ++i) {
        v += a * valueNoise(x);
        x = rot * x * 2.0 + shift;
        a *= 0.5;
    }
    return v;
}

void main() {
    vec4 tex = texture(Sampler0, TexCoord);
    float dist = mix(median(tex.rgb), tex.a, MTSDF_ALPHA_MIX) - 0.5 + Shape.x;
    vec2 unitRange = vec2(Right.w) / vec2(textureSize(Sampler0, 0));
    vec2 screenTexSize = vec2(1.0) / fwidth(TexCoord);
    float screenPxRange = max(0.5 * dot(unitRange, screenTexSize), 1.0);

    float reveal = clamp(Shape.z, 0.0, 1.0);
    float leave = clamp(Shape.w, 0.0, 1.0);
    float time = Motion.x;
    float glyphScale = max(Down.w, 1.0e-3);
    vec2 local = vec2(PlanePos.x - Motion.y, PlanePos.y) / glyphScale;
    float along = clamp((PlanePos.x - Motion.y) / max(Motion.z, 1.0e-3), 0.0, 1.0);

    float grain = fbm(local * NOISE_FREQUENCY + vec2(Accent.a * 17.0, 0.0));
    float exitGrain = fbm(local * NOISE_FREQUENCY * 1.7 - vec2(0.0, time * EXIT_DRIFT));

    float front = reveal * REVEAL_OVERSHOOT - REVEAL_LEAD;
    float threshold = along * REVEAL_SWEEP_WEIGHT + grain * REVEAL_NOISE_WEIGHT;
    float revealMask = smoothstep(threshold - REVEAL_EDGE_WIDTH, threshold, front);
    float revealEdge = revealMask * (1.0 - smoothstep(threshold, threshold + REVEAL_EDGE_WIDTH * 2.0, front));
    revealEdge *= 1.0 - reveal;

    float exitMask = smoothstep(leave - EXIT_EDGE_WIDTH, leave, exitGrain);
    float exitEdge = exitMask * (1.0 - smoothstep(leave, leave + EXIT_EDGE_WIDTH * 2.0, exitGrain));
    exitEdge *= step(0.001, leave);

    float visible = revealMask * exitMask;
    vec3 accent = Accent.rgb;

    if (Origin.w > 0.5) {
        float shade = pow(smoothstep(SHADOW_INNER, SHADOW_OUTER, dist), SHADOW_POWER);
        float shadeAlpha = shade * Fill.w * visible * FragColor.a;
        if (shadeAlpha <= ALPHA_CUTOFF) {
            discard;
        }
        OutColor = vec4(FragColor.rgb, shadeAlpha);
        return;
    }

    // at least half a pixel of edge, so thin strokes of small or far words do not sparkle as they move
    float softness = max(Shape.y * mix(MAX_SOFTNESS, 1.0, smoothstep(0.0, 1.0, reveal)), MIN_EDGE_PX);
    float alpha = pow(smoothstep(-softness, softness, dist * screenPxRange), EDGE_GAMMA);

    float band = fract(time * SHIMMER_SPEED - along - local.y * SHIMMER_SLANT);
    float shimmer = smoothstep(0.0, SHIMMER_WIDTH, band) * (1.0 - smoothstep(SHIMMER_WIDTH, SHIMMER_WIDTH * 2.0, band));

    float gradientWave = 0.5 + 0.5 * sin((along - time * GRADIENT_WAVE_SPEED) * 3.14159);
    vec3 base = mix(FragColor.rgb, accent + vec3(0.12), Fill.y * GRADIENT_TINT * gradientWave);

    float sung = clamp(Fill.x, 0.0, 1.0);
    float filled = step(along, sung) * step(0.001, sung);
    vec3 sungColor = mix(base, accent + vec3(SUNG_LIFT), SUNG_TINT);
    base = mix(base, sungColor, filled);
    // the bright fill edge eases in as the word starts and out as it ends, instead of popping on and off
    float fillEdge = (1.0 - smoothstep(0.0, FILL_EDGE_WIDTH, abs(along - sung))) * smoothstep(0.0, 0.06, sung) * (1.0 - smoothstep(0.94, 1.0, sung));
    base += (accent + vec3(0.5)) * fillEdge * FILL_EDGE_GAIN;

    base += accent * shimmer * SHIMMER_GAIN;
    float ember = clamp((revealEdge + exitEdge) * EMBER_GAIN, 0.0, 1.0);
    vec3 rgb = mix(base, accent + vec3(EMBER_LIFT), ember);

    float bodyAlpha = alpha * visible;
    float glow = 0.0;
    if (Fill.w > 0.0) {
        float outside = clamp(-dist * GLOW_FIELD_SCALE, 0.0, 1.0);
        float shape = GLOW_SHAPE_BASE / max(Fill.z, GLOW_MIN_RADIUS);
        glow = pow(1.0 - outside, shape) * Fill.w * GlowColour.a * visible;
        rgb = mix(GlowColour.rgb, rgb, alpha);
    }

    float outAlpha = max(bodyAlpha, glow) * FragColor.a;
    if (outAlpha <= ALPHA_CUTOFF) {
        discard;
    }
    OutColor = vec4(rgb, outAlpha);
}
