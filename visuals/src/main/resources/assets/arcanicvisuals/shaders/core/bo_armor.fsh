#version 150

#moj_import <arcanicvisuals:bo_uniforms.glsl>
#moj_import <arcanicvisuals:arc_common.glsl>

in vec2 FragCoord;
in vec4 FragColor;
in vec3 WorldPos;
out vec4 OutColor;

const float HEX_SCALE = 3.0;

float hexEdge(vec2 gv) {
    float d = max(abs(gv.x), max(abs(gv.x * 0.5 + gv.y * 0.8660254), abs(gv.x * 0.5 - gv.y * 0.8660254)));
    return 0.5 - d;
}

vec4 hexGrid(vec2 p) {
    p *= HEX_SCALE;
    vec2 h = vec2(1.0, 1.7320508);
    vec2 a = mod(p, h) - h * 0.5;
    vec2 b = mod(p + h * 0.5, h) - h * 0.5;
    vec2 gv = dot(a, a) < dot(b, b) ? a : b;
    return vec4(hexEdge(gv), length(gv), p - gv);
}

void main() {
    vec3 faceN = normalize(cross(dFdx(WorldPos), dFdy(WorldPos)));
    vec3 an = abs(faceN);
    vec2 uv;
    if (an.y > an.x && an.y > an.z) {
        uv = WorldPos.xz;
    } else if (an.x > an.z) {
        uv = WorldPos.zy;
    } else {
        uv = WorldPos.xy;
    }

    vec4 hx = hexGrid(uv);
    float edgeDist = hx.x;
    vec2 cellId = hx.zw;

    float t = Time;

    vec3 metal = vec3(0.055, 0.06, 0.075);
    metal += 0.02 * sin(uv.y * 180.0 + uv.x * 40.0);
    metal *= 0.85 + 0.3 * hash12(cellId);
    metal += 0.04 * smoothstep(0.05, 0.35, edgeDist);

    vec3 viewDir = normalize(CameraPos - WorldPos);
    vec3 n = faceN * sign(dot(faceN, viewDir) + 1e-4);
    vec3 lightDir = normalize(vec3(0.5, 0.9, 0.4));
    vec3 halfDir = normalize(lightDir + viewDir);
    float spec = pow(max(dot(n, halfDir), 0.0), 24.0) * 0.25;

    float groove = smoothstep(0.09, 0.02, edgeDist);
    float core = smoothstep(0.045, 0.0, edgeDist);

    float pulse = 0.6 + 0.4 * sin(t * 2.0);
    float phase = hash12(cellId) * 6.2831;
    float wave = 0.5 + 0.5 * sin(t * 2.6 - dot(cellId, vec2(0.9, 1.7)) + phase * 0.35);
    float glow = groove * (0.2 + 0.8 * pulse * wave);

    vec3 tint = TintColor;
    vec3 glowCol = mix(tint * 0.3, tint * 1.1 + vec3(0.2), core * glow);

    vec3 color = metal + spec + glowCol * glow * 2.2;
    color = clamp(color, 0.0, 1.0);

    float finalAlpha = Fade * clamp(0.7 + glow * 0.5, 0.0, 1.0);
    OutColor = vec4(color, finalAlpha * FragColor.a);
}
