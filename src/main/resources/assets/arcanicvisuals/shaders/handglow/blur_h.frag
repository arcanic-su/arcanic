#version 330 core

in vec2 TexCoord;

uniform sampler2D Sampler0;
uniform vec2 Resolution;
uniform float Offset;

out vec4 OutColor;

// Linear-sampled 5-tap Gaussian. Mathematically identical to the 9-tap
// kernel { 0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216 } when
// the texture uses GL_LINEAR filtering - adjacent sample pairs are
// merged via bilinear interpolation.
const float O1  = 1.3846153846;
const float O2  = 3.2307692308;
const float WC0 = 0.2270270270;
const float WC1 = 0.3162162162;
const float WC2 = 0.0702702703;

void main() {
    vec2 step = vec2(Resolution.x * Offset, 0.0);

    vec4 c  = texture(Sampler0, TexCoord) * WC0;
    c += texture(Sampler0, TexCoord + step * O1) * WC1;
    c += texture(Sampler0, TexCoord - step * O1) * WC1;
    c += texture(Sampler0, TexCoord + step * O2) * WC2;
    c += texture(Sampler0, TexCoord - step * O2) * WC2;

    OutColor = c;
}
