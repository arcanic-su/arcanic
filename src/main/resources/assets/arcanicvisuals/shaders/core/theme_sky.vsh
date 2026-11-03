#version 150

// a triangle over the whole screen, behind everything
out vec2 vNdc;

void main() {
    vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    vNdc = uv * 2.0 - 1.0;
    gl_Position = vec4(vNdc, 1.0, 1.0);
}
