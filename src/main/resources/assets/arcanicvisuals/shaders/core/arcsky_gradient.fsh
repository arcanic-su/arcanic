#version 150

#moj_import <arcanicvisuals:arc_common.glsl>
#moj_import <arcanicvisuals:arc_sky_common.glsl>
#moj_import <arcanicvisuals:arc_sky_depth_gate.glsl>
#moj_import <arcanicvisuals:arc_sky_uniforms.glsl>

in vec3 vRay;
in vec4 FragColor;

out vec4 fragColor;

const float GRADIENT_SEA_OPACITY = 1.0;
const float GRADIENT_MID_BLEND = 0.42;
const float GRADIENT_MID_LIFT = 1.12;
const float GRADIENT_HALO_TIGHT = 4.5;
const float GRADIENT_HALO_GAIN = 0.48;
const float GRADIENT_DISC_TIGHT = 240.0;
const float GRADIENT_DISC_GAIN = 3.20;
const float GRADIENT_ZENITH_DEEPEN = 0.82;

void main() {
    if (skyCoveredByGeometry(gl_FragCoord.xy)) {
        discard;
    }
    vec3 dir = normalize(vRay);
    vec3 horizonTone = BottomRightColor.rgb;

    if (SkyFullDome < 0.5 && dir.y <= 0.0) {
        fragColor = vec4(skyFinish(horizonTone, SKY_EXPOSURE), FragColor.a);
        return;
    }

    vec3 light = skyLightDir(Time);
    vec3 mid = mix(horizonTone, TopLeftColor.rgb, GRADIENT_MID_BLEND) * GRADIENT_MID_LIFT;
    vec3 zenith = TopLeftColor.rgb * GRADIENT_ZENITH_DEEPEN;

    float sunReach = skyEffectFade(dir.y, SkyFullDome);
    vec3 col = skyRamp(dir.y, horizonTone, mid, zenith);
    col += mix(horizonTone, TopLeftColor.rgb, 0.5)
        * skyHalo(dir, light, GRADIENT_HALO_TIGHT) * GRADIENT_HALO_GAIN * sunReach;
    col += mix(TopLeftColor.rgb, vec3(1.0), 0.6)
        * skyHalo(dir, light, GRADIENT_DISC_TIGHT) * GRADIENT_DISC_GAIN * sunReach;

    float seaMask = skySeaMask(dir.y, SkyFullDome) * GRADIENT_SEA_OPACITY;
    if (seaMask > 0.0) {
        col = mix(col, skySea(dir, Time, horizonTone, TopLeftColor.rgb), seaMask);
    }
    col *= skyNadirDepth(dir.y, SkyFullDome);

    fragColor = vec4(skyDither(skyFinish(col, SKY_EXPOSURE), gl_FragCoord.xy), FragColor.a);
}
