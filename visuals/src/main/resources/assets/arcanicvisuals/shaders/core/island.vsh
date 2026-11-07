#version 150
// One triangle over the whole target; TexCoord runs 0..1 across it (row 0 is the island's top).
out vec2 TexCoord;
void main() {
    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    TexCoord = p;
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
