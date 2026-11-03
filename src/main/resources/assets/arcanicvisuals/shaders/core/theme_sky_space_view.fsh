#version 150

// A theme sky shader; uniform interface adapted to the Arcanic renderer: the view direction comes from
// the inverted view-projection instead of a sky cube's positions.
layout(std140) uniform ThemeSky {
    mat4 InvViewProj;
    vec4 AccentTime;
    vec4 Modulator;
};
#define Time AccentTime.w
#define Accent AccentTime.rgb
#define ColorModulator Modulator

uniform sampler2D Sampler0;

in vec2 vNdc;

vec3 skyDirection() {
    vec4 far = InvViewProj * vec4(vNdc, 1.0, 1.0);
    vec4 near = InvViewProj * vec4(vNdc, -1.0, 1.0);
    return far.xyz / far.w - near.xyz / near.w;
}

out vec4 OutColor;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
    p += dot(p, p.yzx + 19.19);
    return fract((p.x + p.y) * p.z);
}

void main() {
    vec3 dir = normalize(skyDirection());

    float longitude = atan(dir.z, dir.x);
    float latitude = asin(clamp(dir.y, -1.0, 1.0));
    vec3 stored = texture(Sampler0, vec2(longitude / 6.28318530718 + 0.5, 0.5 - latitude / 3.14159265359)).rgb;

    vec3 col = pow(stored, vec3(1.0 / 0.92));
    col = 0.85 * col / max(1.0 - col, vec3(0.001));

    vec3 theme = max(Accent, vec3(0.03));
    float themeMax = max(theme.r, max(theme.g, theme.b));
    vec3 themeNorm = themeMax > 0.001 ? theme / themeMax : vec3(0.55, 0.52, 0.65);

    vec3 sd = dir * 520.0;
    vec3 cell = floor(sd);
    vec3 fr = fract(sd) - 0.5;
    float h = hash(cell);
    float star = smoothstep(0.986, 0.998, h) * smoothstep(0.42, 0.0, length(fr));
    col += mix(vec3(0.95, 0.97, 1.0), mix(vec3(1.0), themeNorm, 0.35), 0.4) * star * 3.2;

    col = col / (col + vec3(0.85));
    col = pow(col, vec3(0.92));
    OutColor = vec4(col, 1.0) * ColorModulator;
}
