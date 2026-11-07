#version 330
// Arcanic satin: a slowly folding sheet of violet satin. A layered swirl bends a soft height field, which is lit like
// fabric (a broad diffuse light, a narrow sheen on the crests); the light gathers in a diagonal band through the
// right of the screen and the left stays deep for the interface. Palette from Sampler0: accent, mid, end.
uniform sampler2D Sampler0;
in vec2 splashState;
in vec4 splashTint;
in vec2 clipPosition;
out vec4 fragColor;

float hash(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }

vec2 swirl(vec2 p, float t) {
    for (int i = 1; i <= 7; i++) {
        float k = float(i);
        p.x += 0.55 / k * sin(t * (0.55 + 0.07 * k) + k * 1.31 * p.y + 0.6 * k);
        p.y += 0.50 / k * cos(t * (0.48 + 0.05 * k) + k * 1.07 * p.x - 0.4 * k);
    }
    return p;
}
float height(vec2 p, float t) {
    vec2 w = swirl(p, t);
    float m = 0.5 + 0.5 * sin(w.x * 2.5 + 0.6) * cos(w.y * 1.7 - 0.3);
    // layered plates with soft edges: dark gaps, a body, and a bright upper sheet
    return smoothstep(0.30, 0.56, m) * 0.6 + smoothstep(0.52, 0.80, m) * 0.4;
}

vec3 ramp(float v, vec3 a, vec3 m, vec3 b) {
    vec3 c = mix(vec3(0.012, 0.008, 0.024), a * 0.22, smoothstep(0.05, 0.35, v));
    c = mix(c, a * 0.75, smoothstep(0.30, 0.58, v));
    c = mix(c, m, smoothstep(0.52, 0.78, v));
    c = mix(c, mix(b, vec3(1.0), 0.35), smoothstep(0.74, 0.95, v));
    return c;
}

void main() {
    vec2 uv = vec2(clipPosition.x * 0.5 + 0.5, 0.5 - clipPosition.y * 0.5);
    vec2 px = max(abs(vec2(dFdx(clipPosition.x), dFdy(clipPosition.y))), vec2(1e-6));
    float aspect = px.y / px.x;
    vec2 s = vec2((uv.x - 0.5) * aspect, uv.y - 0.5);
    float t = splashState.x * 300.0 * 0.22;
    vec3 A = texelFetch(Sampler0, ivec2(0, 0), 0).rgb;
    vec3 M = texelFetch(Sampler0, ivec2(1, 0), 0).rgb;
    vec3 B = texelFetch(Sampler0, ivec2(2, 0), 0).rgb;

    // the sheet runs diagonally (top right to bottom centre), stretched along its folds
    float ang = -1.05;
    vec2 p = mat2(cos(ang), sin(ang), -sin(ang), cos(ang)) * s;
    p *= vec2(0.9, 1.9);
    p += vec2(0.35, 0.0);

    float e = 0.004;
    float h = height(p, t);
    float hx = height(p + vec2(e, 0.0), t), hy = height(p + vec2(0.0, e), t);
    vec3 n = normalize(vec3((h - hx) / e * 0.11, (h - hy) / e * 0.11, 1.0));
    vec3 L = normalize(vec3(-0.45, -0.55, 0.70));
    float diff = max(dot(n, L), 0.0);
    float sheen = pow(max(dot(reflect(-L, n), vec3(0.0, 0.0, 1.0)), 0.0), 40.0);

    // where the light lives: a soft diagonal band through the right side of the screen
    vec2 d = s - vec2(0.42, 0.0);
    float across = dot(d, normalize(vec2(1.0, -0.55)));
    float band = exp(-across * across * 5.5) * smoothstep(-0.9, 0.2, s.x);
    float bloom = exp(-dot(s - vec2(0.38, 0.12), s - vec2(0.38, 0.12)) * 2.2);

    float v = (h * 0.72 + (diff - 0.6) * 0.5) * (0.30 + 0.80 * band) + 0.04;
    vec3 col = ramp(clamp(v, 0.0, 1.0), A, M, B);
    col += mix(B, vec3(1.0), 0.5) * sheen * band * 0.8;
    col += A * bloom * 0.10;

    vec2 vg = uv - vec2(0.52, 0.5);
    col *= 1.0 - 1.05 * dot(vg, vg);
    col *= 0.72;
    col += (hash(gl_FragCoord.xy + fract(t)) - 0.5) / 255.0;
    fragColor = vec4(clamp(col, 0.0, 1.0), splashTint.a);
}
