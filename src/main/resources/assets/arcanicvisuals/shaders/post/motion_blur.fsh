#version 330

// Motion Blur: the new frame of the world over the ones before it, the older ones fading.

uniform sampler2D InSampler;
uniform sampler2D PrevSampler;

in vec2 texCoord;
out vec4 fragColor;

layout(std140) uniform Params {
    vec4 Blur; // x: how much of the past stays
};

void main() {
    vec3 now = texture(InSampler, texCoord).rgb;
    vec3 past = texture(PrevSampler, texCoord).rgb;
    fragColor = vec4(mix(now, past, Blur.x), 1.0);
}
