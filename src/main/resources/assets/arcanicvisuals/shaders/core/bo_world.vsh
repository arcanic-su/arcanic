#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <arcanicvisuals:bo_uniforms.glsl>
#moj_import <arcanicvisuals:arc_common.glsl>

in vec3 Position;
in vec4 Color;

out vec2 FragCoord;
out vec4 FragColor;
out vec3 WorldPos;
out vec3 WaveNormal;
out float WaveH;

void main() {
    FragCoord = rvertexcoord(gl_VertexID);
    FragColor = Color;
    WorldPos = Position + CameraPos;
    WaveNormal = vec3(0.0, 1.0, 0.0);
    WaveH = 0.0;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
