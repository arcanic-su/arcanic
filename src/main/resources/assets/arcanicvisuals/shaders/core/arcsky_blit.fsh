#version 150
// The sky is worked out on a smaller picture (it is soft, a fraction of the pixels is plenty) and stretched over the screen here.
uniform sampler2D Sampler0;
in vec2 texCoord;
out vec4 fragColor;
void main() { fragColor = texture(Sampler0, texCoord); }
