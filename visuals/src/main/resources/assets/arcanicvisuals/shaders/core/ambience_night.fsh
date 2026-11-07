#version 330

// Ambience's night mode, as a lightmap pass: the tint in rgb, the strength in alpha (blended over)
layout(std140) uniform NightPass {
    vec4 Night;
};

out vec4 fragColor;

void main() {
    vec2 texCoord = gl_FragCoord.xy / 16.0;
    // Skip the (15,15) corner - GUI/hotbar items sample only this pixel and
    // tinting it would colour the inventory/hotbar icons.
    if (texCoord.x >= 15.0 / 16.0 && texCoord.y >= 15.0 / 16.0) discard;

    // Build a deterministic night lightmap from raw block/sky levels.
    float bbLevel = floor(texCoord.x * 16.0) / 15.0;
    float sbLevel = floor(texCoord.y * 16.0) / 15.0;
    float bb = bbLevel / (4.0 - 3.0 * bbLevel);
    float sb = sbLevel / (4.0 - 3.0 * sbLevel);
    // Block light dominates at night; sky light is heavily attenuated.
    float lit = max(bb, sb * 0.4);
    // Visibility floor - keeps caves and dark spots navigable.
    lit = mix(0.42, 1.0, lit);
    // Dim/mid areas carry the night tint; bright spots (torches, lava)
    // desaturate smoothly toward white so highlights stay readable.
    float desat = smoothstep(0.55, 1.0, lit);
    vec3 night = mix(Night.rgb, vec3(1.0), desat) * lit;
    fragColor = vec4(night, Night.a);
}
