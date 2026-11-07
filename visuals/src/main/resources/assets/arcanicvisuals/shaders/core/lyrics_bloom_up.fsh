#version 150
// Dual Kawase up step for the lyrics bloom: eight taps round the pixel, the diagonals weighted twice.
layout(std140) uniform LyricsBloom { vec4 Params; }; // xy source texel size, z tap offset, w composite strength
uniform sampler2D Sampler0;
in vec2 TexCoord;
out vec4 OutColor;
void main() {
    vec2 h = Params.xy * 0.5 * Params.z;
    vec3 sum = texture(Sampler0, TexCoord + vec2(-h.x * 2.0, 0.0)).rgb;
    sum += texture(Sampler0, TexCoord + vec2(-h.x, h.y)).rgb * 2.0;
    sum += texture(Sampler0, TexCoord + vec2(0.0, h.y * 2.0)).rgb;
    sum += texture(Sampler0, TexCoord + vec2(h.x, h.y)).rgb * 2.0;
    sum += texture(Sampler0, TexCoord + vec2(h.x * 2.0, 0.0)).rgb;
    sum += texture(Sampler0, TexCoord + vec2(h.x, -h.y)).rgb * 2.0;
    sum += texture(Sampler0, TexCoord + vec2(0.0, -h.y * 2.0)).rgb;
    sum += texture(Sampler0, TexCoord + vec2(-h.x, -h.y)).rgb * 2.0;
    OutColor = vec4(sum / 12.0, 1.0);
}
