#version 150
out vec2 vScreen;
void main() {
    vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    vScreen = uv * 2.0 - 1.0;
    gl_Position = vec4(vScreen, 1.0, 1.0);
}
