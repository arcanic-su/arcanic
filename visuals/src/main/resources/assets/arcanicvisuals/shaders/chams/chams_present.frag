#version 330 core

in vec2 vTextureUV;

uniform sampler2D uTexture;

out vec4 FragColor;

void main() {
    vec4 sampled = texture(uTexture, vTextureUV);
    if (sampled.a <= 0.01) {
        discard;
    }

    FragColor = sampled;
}
