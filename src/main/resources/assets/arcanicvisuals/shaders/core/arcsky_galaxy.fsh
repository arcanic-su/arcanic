#version 150

#moj_import <arcanicvisuals:arc_common.glsl>
#moj_import <arcanicvisuals:arc_sky_common.glsl>
#moj_import <arcanicvisuals:arc_sky_uniforms.glsl>

const vec3 GALAXY_POLE = vec3(0.52, 0.66, -0.54);
const vec3 GALAXY_CORE_DIR = vec3(0.301, 0.451, 0.840);
const float GALAXY_CORE_DIST = 5.0;

const float GALAXY_NEAR = 0.5;
const int GALAXY_STEPS = 18;
const float GALAXY_RATIO = 1.22;

const float GALAXY_SCALE_HEIGHT = 0.45;
const float GALAXY_DISC_RADIUS = 1.6;
const float GALAXY_DISC_REACH = 6.0;
const float GALAXY_DISC_AMBIENT = 0.90;
const float GALAXY_CORE_BRIGHT = 1.40;
const float GALAXY_CORE_TINT = 1.30;
const float GALAXY_EMIT = 0.38;
const float GALAXY_DISC_EPSILON = 0.002;

const float GALAXY_DUST_FLATTEN = 0.42;
const float GALAXY_DUST_SCALE = 4.5;
const float GALAXY_DUST_LOW = 0.44;
const float GALAXY_DUST_HIGH = 0.64;
const float GALAXY_DUST_ABSORB = 5.0;
const float GALAXY_DRIFT = 0.012;
const float GALAXY_SHEAR_RATE = 0.020;
const float GALAXY_SHEAR_FALLOFF = 0.35;
const vec3 GALAXY_BASIS_UP = vec3(0.0, 1.0, 0.0);

const float GALAXY_DEEP_LEVEL = 0.045;

in vec3 vRay;
in vec4 FragColor;

out vec4 fragColor;

void main() {
    vec3 dir = normalize(vRay);
    vec3 deep = BottomRightColor.rgb * GALAXY_DEEP_LEVEL;

    if (SkyFullDome < 0.5 && dir.y <= 0.0) {
        fragColor = vec4(deep, 1.0);
        return;
    }

    vec3 ray = skyRotationY(SkyRotSin, SkyRotCos) * dir;
    vec3 pole = normalize(GALAXY_POLE);
    vec3 corePos = normalize(GALAXY_CORE_DIR) * GALAXY_CORE_DIST;
    vec3 discRight = normalize(cross(pole, GALAXY_BASIS_UP));
    vec3 discForward = cross(pole, discRight);
    vec2 corePlanar = vec2(dot(corePos, discRight), dot(corePos, discForward));
    float drift = Time * GALAXY_DRIFT;

    float travel = GALAXY_NEAR;
    float transmittance = 1.0;
    vec3 scattered = vec3(0.0);

    for (int layer = 0; layer < GALAXY_STEPS; layer++) {
        float next = travel * GALAXY_RATIO;
        float stepLength = next - travel;
        vec3 probe = ray * ((travel + next) * 0.5);
        travel = next;

        float height = abs(dot(probe, pole));
        float starDisc = exp(-height / GALAXY_SCALE_HEIGHT);
        if (starDisc <= GALAXY_DISC_EPSILON) {
            continue;
        }

        float coreDistance = length(probe - corePos);
        float bulge = exp(-coreDistance / GALAXY_DISC_RADIUS);
        float body = exp(-coreDistance / GALAXY_DISC_REACH);
        float glow = starDisc * (GALAXY_DISC_AMBIENT * body + GALAXY_CORE_BRIGHT * bulge);

        vec3 gas = mix(TopLeftColor.rgb, BottomRightColor.rgb,
            clamp(bulge * GALAXY_CORE_TINT, 0.0, 1.0));
        scattered += transmittance * gas * glow * GALAXY_EMIT * stepLength;

        float dustDisc = exp(-height / (GALAXY_SCALE_HEIGHT * GALAXY_DUST_FLATTEN));
        vec2 planar = vec2(dot(probe, discRight), dot(probe, discForward)) - corePlanar;
        float spin = GALAXY_SHEAR_RATE * Time / (1.0 + length(planar) * GALAXY_SHEAR_FALLOFF);
        float spinSin = sin(spin);
        float spinCos = cos(spin);
        vec2 turned = vec2(planar.x * spinCos - planar.y * spinSin,
            planar.x * spinSin + planar.y * spinCos) + corePlanar;
        vec3 sheared = discRight * turned.x + discForward * turned.y
            + pole * dot(probe, pole);
        float dustValue = fbm3d(sheared * GALAXY_DUST_SCALE + vec3(drift, 0.0, -drift));
        float dust = smoothstep(GALAXY_DUST_LOW, GALAXY_DUST_HIGH, dustValue) * dustDisc;
        transmittance *= exp(-dust * GALAXY_DUST_ABSORB * stepLength);
    }

    fragColor = vec4(deep + scattered, 1.0);
}
