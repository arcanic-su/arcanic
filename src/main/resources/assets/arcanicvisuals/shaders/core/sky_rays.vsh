#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec3 vRay;
out vec4 FragColor;

void main() {
    vRay = Position;
    FragColor = Color;
    gl_Position = vec4(UV0, 0.0, 1.0);
}
