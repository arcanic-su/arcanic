#version 330 core

// The flame: the trail moved up by the rise speed, in 24 strips each swaying on its own sum of sines
in vec2 TexCoord;

uniform sampler2D Sampler0;
uniform float Rise;   // in uv
uniform float Wobble; // in uv
uniform float Time;   // seconds

out vec4 OutColor;

void main() {
    vec2 src = vec2(TexCoord.x, TexCoord.y - Rise);
    float strip = floor((1.0 - src.y) * 24.0);
    float f12 = strip * 0.45;
    float t = Time;
    float sway = sin(t * 4.5 + f12) * Wobble
            + sin(t * 1.7 + f12 * 2.1) * (Wobble * 0.5)
            + sin(t * 7.3 + f12 * 3.7) * (Wobble * 0.32)
            + sin(t * 2.3 + f12 * 1.3) * (Wobble * 0.45)
            + sin(t * 0.61 + f12 * 0.7) * (Wobble * 0.35);
    src.x -= sway;
    if (src.x < 0.0 || src.x > 1.0 || src.y < 0.0 || src.y > 1.0) {
        OutColor = vec4(0.0);
        return;
    }
    OutColor = texture(Sampler0, src);
}
