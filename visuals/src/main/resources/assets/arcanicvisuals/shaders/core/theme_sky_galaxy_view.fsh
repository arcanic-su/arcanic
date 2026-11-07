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

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 hash33(vec3 p3) {
    p3 = fract(p3 * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yxz + 33.33);
    return fract((p3.xxy + p3.yxx) * p3.zyx);
}

vec3 rotateY(vec3 p, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec3(p.x * c - p.z * s, p.y, p.x * s + p.z * c);
}

vec3 rotateX(vec3 p, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec3(p.x, p.y * c - p.z * s, p.y * s + p.z * c);
}

float starGlow(vec3 direction, float scale, float density, float time) {
    vec3 p = direction * scale;
    vec3 cell = floor(p);
    vec3 h = hash33(cell);
    if (h.x > density) return 0.0;
    float distanceToStar = length(fract(p) - (0.2 + 0.6 * h));
    float brightness = hash11(h.y + 1.7);
    brightness *= brightness;
    brightness *= brightness;
    float twinkle = 0.6 + 0.4 * sin(time * 2.0 + h.z * 50.0);
    float core = smoothstep(0.06, 0.0, distanceToStar);
    float halo = exp(-distanceToStar * 12.0) * 0.5;
    return (core + halo) * (0.4 + brightness * 2.6) * twinkle;
}

void main() {
    vec3 direction = normalize(skyDirection());
    float time = Time * 0.35;

    vec3 flow = rotateY(direction, Time * 0.010);
    flow = rotateX(flow, sin(Time * 0.008) * 0.10);

    float longitude = atan(flow.z, flow.x);
    float latitude = asin(clamp(flow.y, -1.0, 1.0));
    vec4 baked = texture(Sampler0, vec2(longitude / 6.28318530718 + 0.5, 0.5 - latitude / 3.14159265359));

    vec3 nebula = pow(baked.rgb, vec3(1.0 / 0.85));
    nebula = nebula / max(1.0 - nebula, vec3(0.001));
    nebula *= 0.90 + 0.10 * sin(Time * 0.20 + baked.a * 4.0);

    vec3 color = vec3(0.010, 0.013, 0.024) + nebula;

    vec3 starDir = rotateY(direction, Time * 0.004);
    color += vec3(0.85, 0.90, 1.0) * starGlow(starDir, 240.0, 0.05, time);
    color += vec3(1.0, 0.96, 0.90)
            * starGlow(starDir * 1.7 + 31.0, 130.0, 0.025, time * 1.3)
            * 1.4;

    color = color / (1.0 + color);
    color = pow(color, vec3(0.85));
    color += vec3((hash21(gl_FragCoord.xy) - 0.5) / 255.0);

    OutColor = vec4(clamp(color, 0.0, 1.0), 1.0) * ColorModulator;
}
