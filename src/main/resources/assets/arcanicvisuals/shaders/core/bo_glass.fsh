#version 150

#moj_import <arcanicvisuals:bo_uniforms.glsl>
#moj_import <arcanicvisuals:arc_common.glsl>
#define Radius vec4(0.0, 0.0, 1.0, 0.2)
#define Progress 1.0

in vec2 FragCoord;
in vec4 FragColor;
in vec3 WorldPos;
in vec3 WaveNormal;
in float WaveH;
out vec4 OutColor;

const vec3 UP = vec3(0.0, 1.0, 0.0);
const vec3 FOAM = vec3(1.0);
const vec3 GLINT = vec3(0.85, 0.93, 1.0);

void main() {
    vec2 center = Radius.xy;
    float maxRadius = max(Radius.z, 0.001);
    float foamBand = max(Radius.w, 0.001);

    vec2 surface = WorldPos.xz;
    float radial = distance(surface, center) / maxRadius;

    float low = fbm(surface * 0.6);
    float mid = fbm(surface * 1.9 + vec2(7.0, 2.0));
    float bumps = (low - 0.5) * 1.4 + (mid - 0.5) * 0.6;
    float seepField = radial + bumps * 0.35;

    float emerge = Progress - seepField;
    if (emerge < -foamBand) discard;

    float t = Time;

    vec2 e = vec2(0.09, 0.0);
    float chop = fbm(surface * 2.4 + vec2(t * 0.3, -t * 0.2));
    float chopX = fbm((surface + e.xy) * 2.4 + vec2(t * 0.3, -t * 0.2)) - chop;
    float chopZ = fbm((surface + e.yx) * 2.4 + vec2(t * 0.3, -t * 0.2)) - chop;
    vec3 detail = vec3(-chopX, 0.5, -chopZ);
    vec3 nrm = normalize(WaveNormal + detail * 0.6);

    vec3 viewDir = normalize(CameraPos - WorldPos);
    float fresnel = pow(1.0 - clamp(dot(nrm, viewDir), 0.0, 1.0), 3.0);

    vec3 lightDir = normalize(vec3(0.35, 0.85, 0.4));
    vec3 halfDir = normalize(lightDir + viewDir);
    float glint = pow(max(dot(nrm, halfDir), 0.0), 120.0);
    float ndl = dot(nrm, lightDir) * 0.5 + 0.5;

    float wet = smoothstep(-foamBand, foamBand * 1.5, emerge);
    float shoreFoam = smoothstep(foamBand * 1.4, 0.0, abs(emerge)) * (0.4 + 0.6 * mid);
    float crest = smoothstep(0.05, 0.12, WaveH) * 0.6;
    float caustic = pow(clamp(mid, 0.0, 1.0), 3.0) * wet;

    vec3 tint = TintColor;
    vec3 deep = tint * 0.26;
    vec3 shallow = tint * 0.82;
    float depthMix = clamp(0.18 + fresnel * 0.5 + WaveH * 2.2, 0.0, 1.0);
    vec3 color = mix(deep, shallow, depthMix);
    color *= 0.68 + 0.46 * ndl;
    color = mix(color, tint * 1.2 + vec3(0.06), fresnel * 0.4);
    color += tint * caustic * 0.12;
    color += GLINT * glint * wet;
    color += FOAM * crest * wet * 0.7;
    color = mix(color, FOAM, clamp(shoreFoam * 0.72, 0.0, 1.0));
    color = clamp(color, 0.0, 1.0);

    float bodyAlpha = 0.6 + fresnel * 0.3 + glint * 0.5;
    bodyAlpha = max(bodyAlpha, shoreFoam);
    float alpha = Fade * clamp(bodyAlpha, 0.0, 1.0) * wet * FragColor.a;
    OutColor = vec4(color, alpha);
}
