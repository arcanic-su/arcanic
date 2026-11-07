#version 150
// Adds the blurred lyrics glow over the frame, scaled by the glow strength.
layout(std140) uniform LyricsBloom { vec4 Params; }; // w composite strength
uniform sampler2D Sampler0;
in vec2 TexCoord;
out vec4 OutColor;
void main() {
    OutColor = vec4(texture(Sampler0, TexCoord).rgb * Params.w, 1.0);
}
