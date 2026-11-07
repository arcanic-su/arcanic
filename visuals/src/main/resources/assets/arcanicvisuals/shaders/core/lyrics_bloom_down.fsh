#version 150
// Dual Kawase down step for the lyrics bloom: the centre four times and the four diagonal half-pixel taps.
layout(std140) uniform LyricsBloom { vec4 Params; }; // xy source texel size, z tap offset, w composite strength
uniform sampler2D Sampler0;
in vec2 TexCoord;
out vec4 OutColor;
void main() {
    vec2 h = Params.xy * 0.5 * Params.z;
    vec3 sum = texture(Sampler0, TexCoord).rgb * 4.0;
    sum += texture(Sampler0, TexCoord - h).rgb;
    sum += texture(Sampler0, TexCoord + h).rgb;
    sum += texture(Sampler0, TexCoord + vec2(h.x, -h.y)).rgb;
    sum += texture(Sampler0, TexCoord - vec2(h.x, -h.y)).rgb;
    OutColor = vec4(sum / 8.0, 1.0);
}
