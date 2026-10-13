#version 330
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
out vec4 fragColor;
void main() {
    vec4 sampleColor = texture(Sampler0, texCoord0);
    // Arcanic includes both straight-alpha sprites and opaque sprites on black.
    // Move baked intensity into coverage, so neither white transparent pixels nor black backgrounds leak.
    float intensity = max(sampleColor.r, max(sampleColor.g, sampleColor.b));
    float coverage = sampleColor.a * intensity;
    if (coverage < 0.001) discard;
    vec4 color = vec4(sampleColor.rgb / max(intensity, 0.001), coverage) * vertexColor * ColorModulator;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
