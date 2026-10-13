#version 150

#moj_import <arcanicvisuals:arc_sky_uniforms.glsl>
#moj_import <arcanicvisuals:arc_common.glsl>
#moj_import <arcanicvisuals:arc_sky_common.glsl>
#moj_import <arcanicvisuals:arc_sky_depth_gate.glsl>
#moj_import <arcanicvisuals:arc_ember.glsl>
#moj_import <arcanicvisuals:arc_menu_patterns.glsl>

const float MENU_SKY_EMBER_INTENSITY = 1.0;
const int MENU_SKY_EMBER_SWIRL = 5;
const float MENU_SKY_LOOP_MIN = 1.0;
const float MENU_SKY_SIZE_MIN = 0.05;
const float MENU_SKY_ASPECT = 1.0;
const float MENU_SKY_HORIZON_LEVEL = 0.07;
const float MENU_SKY_HALF = 0.5;
const float MENU_SKY_BLOOM_SPREAD = 0.25;
const float MENU_SKY_EMBER_DENSITY = 3.0;

in vec3 vRay;
in vec4 FragColor;

out vec4 fragColor;

vec2 domeToMenu(vec3 dir, float size) {
    vec2 disc = dir.xz / (1.0 + abs(dir.y));
    return MENU_SKY_HALF + disc * (MENU_SKY_HALF / max(size, MENU_SKY_SIZE_MIN));
}

vec3 domeFrame(vec3 dir) {
    float drop = 1.0 - abs(dir.y);
    float azimuth = atan(dir.z, dir.x);
    return vec3(drop, MENU_SKY_HALF + MENU_SKY_HALF * sin(azimuth), drop * MENU_SKY_BLOOM_SPREAD);
}

void main() {
    if (skyCoveredByGeometry(gl_FragCoord.xy)) {
        discard;
    }
    vec3 dir = normalize(vRay);
    vec3 horizonTone = TopLeftColor.rgb * MENU_SKY_HORIZON_LEVEL;

    if (SkyFullDome < 0.5 && dir.y <= 0.0) {
        fragColor = vec4(horizonTone, FragColor.a);
        return;
    }

    float loop = max(Backdrop.z, MENU_SKY_LOOP_MIN);
    float cycle = mod(Time, loop) / loop;
    vec2 uv = domeToMenu(dir, Backdrop.w);
    if (abs(Backdrop.x - MODE_EMBER) < MODE_EPSILON) {
        uv = MENU_SKY_HALF + (uv - MENU_SKY_HALF) * MENU_SKY_EMBER_DENSITY;
    }

    vec3 color = menuPattern(Backdrop.x, uv, MENU_SKY_ASPECT, cycle, MENU_SKY_EMBER_INTENSITY,
            MENU_SKY_EMBER_SWIRL, domeFrame(dir)) * Backdrop.y;
    color = mix(horizonTone, color, skyEffectFade(dir.y, SkyFullDome));

    fragColor = vec4(clamp(skyDither(color, gl_FragCoord.xy), 0.0, 1.0), FragColor.a);
}
