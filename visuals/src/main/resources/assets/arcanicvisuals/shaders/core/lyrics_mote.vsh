#version 330
// World lyrics motes: small camera-facing quads (camera-relative corners), drawn with the frame's own view rotation
// and projection like the words.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};
in vec3 Position;
in vec2 UV0;
in vec4 Color;
out vec2 TexCoord;
out vec4 FragColor;
void main() {
    TexCoord = UV0;
    FragColor = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
