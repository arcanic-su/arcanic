#version 330
#moj_import <minecraft:dynamictransforms.glsl>
// MTSDF text, after arcanic's MSDF_TEXT mode: a near-hard edge at one screen pixel, four rotated sub-samples when the
// glyph is small, and a hair of extra weight on very thin stems so small light-on-dark text keeps its body.
uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
const float RANGE = 10.0;
const float EDGE = 0.2;
float median(vec3 m) { return max(min(m.r, m.g), min(max(m.r, m.g), m.b)); }
float cover(vec2 uv, float px, float thick) { return smoothstep(-EDGE, EDGE, (median(texture(Sampler0, uv).rgb) - 0.5 + thick) * px); }
void main() {
    vec2 d = fwidth(texCoord0);
    vec2 duv = d * vec2(textureSize(Sampler0, 0));
    float px = RANGE * inversesqrt(dot(duv, duv));
    float thick = max(0.0, 2.0 - px) * 0.02;
    float a;
    if (px > 8.0) a = cover(texCoord0, px, thick);
    else {
        vec2 sx = d * vec2(0.125, 0.375), sy = d * vec2(-0.375, 0.125);
        a = (cover(texCoord0 + sx, px, thick) + cover(texCoord0 - sx, px, thick) + cover(texCoord0 + sy, px, thick) + cover(texCoord0 - sy, px, thick)) * 0.25;
    }
    if (a <= 0.004) discard;
    fragColor = vec4(vertexColor.rgb, vertexColor.a * a) * ColorModulator;
}
