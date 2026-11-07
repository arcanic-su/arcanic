#version 330 core

in vec2 TexCoord;

uniform sampler2D Sampler0; // sharp entity buffer (full-res, linear)
uniform sampler2D Sampler1; // blurred glow (downscaled, linear)
uniform vec2 Resolution;    // 1/texW, 1/texH of Sampler0
uniform float Offset;       // glow intensity multiplier
uniform float OutlineStrength; // outline brightness multiplier (1.0 = vanilla)
uniform float OutlineRadius;   // outline sample radius in pixels (1.0 = vanilla)
uniform vec2  GlowOffset;      // halo-only UV shift in source pixels (-y = halo up on screen)

out vec4 OutColor;

void main() {
    vec4 center = texture(Sampler0, TexCoord);

    // Inside entity body - render nothing
    if (center.a > 0.05) {
        OutColor = vec4(0.0);
        return;
    }

    // 8-direction outline sample at OutlineRadius px (sub-pixel offsets engage
    // bilinear filter on the source texture -> anti-aliased outline instead of
    // staircase). Higher radius = thicker outline ring.
    vec2 px = Resolution * OutlineRadius;
    vec4 s[8];
    s[0] = texture(Sampler0, TexCoord + vec2( px.x, 0.0));
    s[1] = texture(Sampler0, TexCoord + vec2(-px.x, 0.0));
    s[2] = texture(Sampler0, TexCoord + vec2(0.0,  px.y));
    s[3] = texture(Sampler0, TexCoord + vec2(0.0, -px.y));
    s[4] = texture(Sampler0, TexCoord + vec2( px.x,  px.y));
    s[5] = texture(Sampler0, TexCoord + vec2(-px.x,  px.y));
    s[6] = texture(Sampler0, TexCoord + vec2( px.x, -px.y));
    s[7] = texture(Sampler0, TexCoord + vec2(-px.x, -px.y));

    // Average color of the touched neighbors (weighted by their alpha).
    vec3 nColorAcc = vec3(0.0);
    float nAlphaAcc = 0.0;
    float maxA = 0.0;
    for (int i = 0; i < 8; ++i) {
        nColorAcc += s[i].rgb;
        nAlphaAcc += s[i].a;
        maxA = max(maxA, s[i].a);
    }
    vec3 outlineRgb = nColorAcc / max(nAlphaAcc, 0.001);

    // 4-tap box smoothing of low-res glow when sampled at full-res
    // (kills the chunky look from upscaling a downscaled buffer).
    vec2 gpx = Resolution * 4.5;
    vec2 gOff = GlowOffset * Resolution;
    vec4 g0 = texture(Sampler1, TexCoord + gOff + vec2( gpx.x,  gpx.y));
    vec4 g1 = texture(Sampler1, TexCoord + gOff + vec2(-gpx.x,  gpx.y));
    vec4 g2 = texture(Sampler1, TexCoord + gOff + vec2( gpx.x, -gpx.y));
    vec4 g3 = texture(Sampler1, TexCoord + gOff + vec2(-gpx.x, -gpx.y));
    vec4 blur = (g0 + g1 + g2 + g3) * 0.25;

    // Glow contribution (premultiplied for additive ONE,ONE blend)
    vec3 glow = blur.rgb * Offset;

    // Outline contribution - soft falloff using maxA so edges fade smoothly.
    vec3 outline = outlineRgb * maxA * OutlineStrength;

    // Combine: outline overpowers near edge, glow takes over further out
    OutColor = vec4(glow + outline, 1.0);
}
