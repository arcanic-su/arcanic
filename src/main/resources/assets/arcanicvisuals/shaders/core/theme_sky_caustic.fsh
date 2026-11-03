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

in vec2 vNdc;

vec3 skyDirection() {
    vec4 far = InvViewProj * vec4(vNdc, 1.0, 1.0);
    vec4 near = InvViewProj * vec4(vNdc, -1.0, 1.0);
    return far.xyz / far.w - near.xyz / near.w;
}

out vec4 OutColor;

float hash13(vec3 p) {
    p = fract(p * vec3(443.8975, 397.2973, 491.1871));
    p += dot(p, p.yxz + 19.19);
    return fract((p.x + p.y) * p.z);
}

float caustic(vec2 uv, float t) {
    vec2 p = mod(uv * 6.28318, 6.28318) - 250.0;
    vec2 i = p;
    float c = 1.0;
    float inten = 0.0052;
    for (int n = 0; n < 5; n++) {
        float tn = t * 0.5 + float(n);
        i = p + vec2(cos(tn - i.x) + sin(tn + i.y),
                     sin(tn - i.y) + cos(tn + i.x));
        c += 1.0 / length(vec2(p.x / (sin(i.x + tn) / inten),
                               p.y / (cos(i.y + tn) / inten)));
    }
    c /= 5.0;
    c = 1.17 - pow(c, 1.4);
    return clamp(pow(abs(c), 8.0), 0.0, 5.0);
}

vec3 starfield(vec3 d, float t, vec3 tint) {
    vec3 p = d * 180.0;
    vec3 i = floor(p);
    float h = hash13(i);
    if (h < 0.991) return vec3(0.0);
    vec3 f = fract(p) - 0.5;
    float disc = exp(-dot(f, f) * 1400.0);
    float twinkle = 0.5 + 0.5 * sin(t * 2.0 + h * 113.0);
    return mix(vec3(0.95, 0.96, 1.00), tint, 0.20) * disc * twinkle * 1.1;
}

void main() {
    vec3 d = normalize(skyDirection());
    float t = Time * 0.30;

    vec3 accent = max(Accent, vec3(0.05));
    vec3 deep = accent * 0.05 + vec3(0.005, 0.008, 0.020);
    vec3 mid  = accent * 0.42;
    vec3 hot  = mix(accent, vec3(1.0), 0.55);

    vec2 uv = d.xz / (abs(d.y) + 0.30) * 0.50;

    float c1 = caustic(uv, t);
    float c2 = caustic(uv * 1.30 + vec2(7.3, 11.1), t * 0.78 + 3.1) * 0.55;
    float c  = c1 + c2;

    vec3 sky = mix(mid, deep, smoothstep(0.0, 0.75, d.y));

    float skyMask = smoothstep(-0.05, 0.22, d.y);
    vec3 color = sky + (accent * 0.95 + hot * 0.40) * c * skyMask;

    float core = smoothstep(0.65, 1.6, c);
    color += hot * core * 0.75;

    float starOcc = 1.0 - smoothstep(0.08, 0.55, c);
    color += starfield(d, Time, accent) * starOcc;

    color = color / (1.0 + color * 0.55);
    color = pow(color, vec3(0.92));

    OutColor = vec4(color, 1.0) * ColorModulator;
}
