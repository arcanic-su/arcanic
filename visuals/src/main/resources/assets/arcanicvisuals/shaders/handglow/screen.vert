#version 330 core

// a triangle over the whole target; TexCoord as a full-screen quad samples it
out vec2 TexCoord;

void main() {
    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    TexCoord = p;
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
