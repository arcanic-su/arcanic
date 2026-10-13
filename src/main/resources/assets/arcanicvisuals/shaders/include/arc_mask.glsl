// mask.glsl
// Clean edge alpha-fade for scrollable and clipped regions.
// Affects ONLY the alpha channel - a true dissolve at the bounds, NEVER black shadows/vignettes.
// MaskTopY / MaskBottomY / MaskLeftX / MaskRightX are already in gl_FragCoord screen space.
// Every edge carries its own fade width, so a container can be soft on one side and hard on
// the other - a scroll view fades top and bottom while its sides stay flush with the layout.
// Requires arcanic:common.glsl for applyEdgeMask.

float applyAlphaMask(float alpha) {
    return applyEdgeMask(alpha, gl_FragCoord.xy,
            vec4(MaskTopY, MaskBottomY, MaskLeftX, MaskRightX),
            vec4(MaskFadeTop, MaskFadeBottom, MaskFadeLeft, MaskFadeRight));
}
