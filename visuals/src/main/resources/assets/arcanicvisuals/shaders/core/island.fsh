#version 150
// Arcanic's dynamic island: up to three capsules (the island and its two satellites) melted into one liquid shape
// with a smooth minimum, so they bud off and swallow each other like drops. On top of the black body: a hairline
// rim lit from above, a soft shadow under it, a coloured glow for events, a wash of colour from the left, and two
// comets of light that run round the edge when something happens. Everything is worked out per pixel from the
// distance to the shape, so it stays crisp at any size and in every frame of a morph.
layout(std140) uniform Island {
    vec4 Size;   // xy target size in px, z time in s, w pixel scale (1 at 1080p)
    vec4 Main;   // the island: centre xy, half size zw (px, y down)
    vec4 Left;   // left satellite, same (z <= 0: none)
    vec4 Right;  // right satellite
    vec4 Radii;  // x island, y left, z right corner radius; w how far shapes melt together (px)
    vec4 Fill;   // body colour, a = its opacity
    vec4 Rim;    // rim colour, a = its strength
    vec4 Glow;   // glow colour, a = its strength
    vec4 Sweep;  // edge light colour, a = its strength
    vec4 Fx;     // x edge light position round the edge (0..1), y its length, z glow reach px, w shadow strength
    vec4 Fx2;    // x shadow blur px, y shadow drop px, z colour wash strength, w top sheen strength
    vec4 Tint;   // wash colour, a = how far it reaches across the island (0..1)
    vec4 Drop;   // the drop under the island: centre xy, half size zw (z <= 0: none)
    vec4 DropR;  // x its corner radius
};

in vec2 TexCoord;
out vec4 fragColor;

const float PI = 3.14159265;

float box(vec2 p, vec4 s, float r) {
    vec2 b = max(s.zw, vec2(0.0));
    r = min(r, min(b.x, b.y));
    vec2 q = abs(p - s.xy) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// polynomial smooth minimum: the neck between two drops
float smin(float a, float b, float k) {
    if (k <= 0.0) return min(a, b);
    float h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h * h * k * 0.25;
}

float shape(vec2 p) {
    float d = box(p, Main, Radii.x);
    if (Left.z > 0.05) d = smin(d, box(p, Left, Radii.y), Radii.w);
    if (Right.z > 0.05) d = smin(d, box(p, Right, Radii.z), Radii.w);
    if (Drop.z > 0.05) d = smin(d, box(p, Drop, DropR.x), Radii.w);
    return d;
}

// a comet of light: bright at its head, fading along a tail behind it
float comet(float behind, float len) {
    if (behind < -0.02) return 0.0;
    if (behind < 0.0) return smoothstep(-0.02, 0.0, behind);
    float t = clamp(1.0 - behind / max(len, 1e-3), 0.0, 1.0);
    return t * t;
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 p = TexCoord * Size.xy;
    float px = max(Size.w, 0.35);
    float d = shape(p);
    vec2 e = vec2(0.75, 0.0);
    vec2 n = vec2(shape(p + e.xy) - shape(p - e.xy), shape(p + e.yx) - shape(p - e.yx));
    n = n / max(length(n), 1e-4);

    float inside = smoothstep(0.65, -0.65, d);

    // the body: near black, a breath lighter at the top, a wash of colour from the left on events
    float top = Main.y - Main.w;
    float t = clamp((p.y - top) / max(2.0 * Main.w, 1.0), 0.0, 1.0);
    vec3 body = Fill.rgb + vec3(0.035) * (1.0 - t) * (1.0 - t);
    float wash = Fx2.z * (1.0 - smoothstep(0.0, max(Tint.a * 2.0 * Main.z, 1.0), p.x - (Main.x - Main.z)));
    body = mix(body, Tint.rgb, clamp(wash, 0.0, 1.0) * 0.5);

    // light on the edge: a hairline rim, brighter where the edge faces up, and a faint sheen under the top
    float ring = clamp(1.0 - abs(d + 0.9) / 0.9, 0.0, 1.0);
    float up = max(-n.y, 0.0);
    vec3 light = Rim.rgb * ring * Rim.a * (0.45 + 0.55 * up);
    light += vec3(1.0) * Fx2.w * up * smoothstep(-7.0 * px, -0.5, d) * (1.0 - smoothstep(-0.5, 0.5, d)) * 0.5;

    // two comets running round the edge, opposite each other
    float sweepIn = 0.0, sweepOut = 0.0;
    if (Sweep.a > 0.001) {
        vec2 v = (p - Main.xy) / max(Main.zw, vec2(1.0));
        float ang = atan(v.y, v.x) / (2.0 * PI) + 0.5;
        float head = Fx.x;
        float b1 = mod(head - ang + 1.5, 1.0) - 0.5;
        float b2 = mod(head + 0.5 - ang + 1.5, 1.0) - 0.5;
        float c = max(comet(b1, Fx.y), comet(b2, Fx.y));
        float edge = exp(-abs(d) / (1.1 * px));
        sweepIn = Sweep.a * c * edge;
        sweepOut = Sweep.a * c * exp(-max(d, 0.0) / (6.0 * px)) * 0.55;
    }
    light += Sweep.rgb * sweepIn;

    // the event glow: round the outside, and faintly just inside the edge
    float reach = max(Fx.z, 1.0);
    float glowOut = Glow.a * exp(-max(d, 0.0) / reach) * (1.0 - inside);
    light += Glow.rgb * Glow.a * 0.35 * exp(min(d, 0.0) / (reach * 0.35)) * inside;

    // the shadow under it, a little lower down
    float ds = shape(p - vec2(0.0, Fx2.y));
    float shadow = Fx.w * exp(-max(ds, 0.0) / max(Fx2.x, 1.0)) * (1.0 - inside);

    // compose: shadow, then the glow and the comets' bloom, then the body with its lights
    float aGlow = clamp(glowOut + sweepOut, 0.0, 1.0);
    vec3 cGlow = (Glow.rgb * glowOut + Sweep.rgb * sweepOut) / max(glowOut + sweepOut, 1e-4);
    float aOut = aGlow + shadow * (1.0 - aGlow);
    vec3 cOut = cGlow * aGlow / max(aOut, 1e-4);
    float aIn = Fill.a * inside;
    float a = aIn + aOut * (1.0 - aIn);
    vec3 col = (body * aIn + cOut * aOut * (1.0 - aIn)) / max(a, 1e-4);
    col += light * inside;
    a = max(a, clamp(max(max(light.r, light.g), light.b), 0.0, 1.0) * inside);

    // a whisper of noise so the soft glows do not band
    col += (hash(p + Size.z) - 0.5) / 255.0;
    if (a <= 0.002) discard;
    fragColor = vec4(clamp(col, 0.0, 1.0), clamp(a, 0.0, 1.0));
}
