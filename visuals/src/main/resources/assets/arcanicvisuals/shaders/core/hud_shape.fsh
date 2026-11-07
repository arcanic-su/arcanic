#version 330
#moj_import <minecraft:dynamictransforms.glsl>
// HUD shapes worked out per pixel: a rounded panel (filled, or only its inner rim), a timer ring and a glare band.
// The quad carries no uniforms of its own, so its parameters ride in the texture coordinate (see HudShape.java):
// each one is 8 * P + 2 + 4 * f, f the position across the quad (0..1) and P a whole-number parameter. The quad's size
// in pixels comes back from how fast f changes from one pixel to the next.
uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

float box(vec2 p, vec2 hs, float r) {
    vec2 q = abs(p) - hs + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    vec2 P = floor(texCoord0 / 8.0);
    vec2 f = (texCoord0 - 8.0 * P - 2.0) / 4.0;
    vec2 size = 1.0 / max(fwidth(f), vec2(1.0e-5));
    vec2 p = (f - 0.5) * size;
    vec2 hs = size * 0.5;
    float a;
#if defined(RING)
    // P.x: progress in thousandths, P.y: thickness in thousandths of the radius; it starts at the top, clockwise
    float outer = min(hs.x, hs.y) - 0.5;
    float inner = max(outer - P.y / 1000.0 * outer, 0.0);
    float d = length(p);
    a = clamp(outer - d + 0.5, 0.0, 1.0) * clamp(d - inner + 0.5, 0.0, 1.0);
    float progress = P.x / 1000.0;
    if (progress < 0.9995) {
        float angle = atan(p.x, -p.y);
        if (angle < 0.0) angle += 6.28318530718;
        float target = progress * 6.28318530718;
        float aa = max(fwidth(angle), 0.02);
        a *= 1.0 - smoothstep(target - aa, target + aa, angle);
    }
#elif defined(GLARE)
    // P.x: corner radius in quarter pixels, P.y: the band's place across the panel (thousandths, from -0.5),
    // plus 4096 when it runs along the other diagonal
    float r = min(P.x / 4.0, min(hs.x, hs.y));
    float shape = clamp(0.5 - box(p, hs, r), 0.0, 1.0);
    float flip = step(4096.0, P.y);
    float head = (P.y - flip * 4096.0) / 1000.0 - 0.5;
    float y = mix(f.y, 1.0 - f.y, flip);
    float sweep = (f.x - y * 0.55 + 0.55) / 1.55;
    float band = smoothstep(head - 0.08 - 0.2, head - 0.08, sweep) * (1.0 - smoothstep(head + 0.08, head + 0.28, sweep));
    float middle = 0.78 + 0.22 * (1.0 - abs(f.y - 0.5) * 2.0);
    a = shape * band * middle;
#else
    // P.x: corner radius in quarter pixels, P.y: rim thickness in sixteenths of a pixel (0 fills the panel)
    float r = min(P.x / 4.0, min(hs.x, hs.y));
    float d = box(p, hs, r);
    a = clamp(0.5 - d, 0.0, 1.0);
    if (P.y > 0.0) a *= clamp(d + P.y / 16.0 + 0.5, 0.0, 1.0);
#endif
    // the panel's texture is not drawn, only bound; this keeps the sampler in use
    a *= step(-1.0, texture(Sampler0, vec2(0.5)).a);
    if (a <= 0.002) discard;
    fragColor = vec4(vertexColor.rgb, vertexColor.a * a) * ColorModulator;
}
