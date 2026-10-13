layout(std140) uniform ArcSky {
    vec4 TopLeftColor;
    vec4 BottomLeftColor;
    vec4 TopRightColor;
    vec4 BottomRightColor;
    vec4 Backdrop;
    vec3 CameraPos;
    float Time;
    float SkyRotSin;
    float SkyRotCos;
    float SkyFullDome;
    float SkyPad0;
    vec2 Resolution;
    vec2 SkyPad1;
};
