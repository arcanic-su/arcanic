layout(std140) uniform BlockOverlay {
    vec4 BoTint;    // rgb tint, a = fade
    vec4 BoParams;  // x time, y/z resolution
    vec4 BoCamera;  // xyz camera position
};
#define TintColor (BoTint.rgb)
#define Fade (BoTint.a)
#define Time (BoParams.x)
#define Resolution (BoParams.yz)
#define CameraPos (BoCamera.xyz)
