#version 330 core

// Textured full-screen quads: the texture times a colour
in vec2 TexCoord;

uniform sampler2D Sampler0;
uniform vec2 Shift;
uniform vec4 Mul;

out vec4 OutColor;

void main() {
    OutColor = texture(Sampler0, TexCoord + Shift) * Mul;
}
