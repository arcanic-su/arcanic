#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 2) in vec2 UV0;

out vec2 vTextureUV;

uniform vec2 uScreenSize;

void main() {
    vTextureUV = UV0;

    vec2 ndc = vec2(
            (Position.x / uScreenSize.x) * 2.0 - 1.0,
            (1.0 - Position.y / uScreenSize.y) * 2.0 - 1.0
    );
    gl_Position = vec4(ndc, 0.0, 1.0);
}
