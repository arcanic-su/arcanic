#version 150

#moj_import <arcanicvisuals:bo_uniforms.glsl>
#moj_import <arcanicvisuals:arc_common.glsl>

in vec2 FragCoord;
in vec4 FragColor;
in vec3 WorldPos;
out vec4 OutColor;

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.x) / (6.0 * d + e)), d / (q.x + e), q.x);
}

float an_stars(vec2 p, float t) {
    vec2 cell = floor(p * 12.0);
    float n = hash12(cell);
    float rare = step(0.976, n);
    vec2 f = fract(p * 12.0) - 0.5;
    vec2 jitter = vec2(hash12(cell + 7.3), hash12(cell + 3.1)) - 0.5;
    float spot = smoothstep(0.22, 0.04, length(f - jitter * 0.5));
    return rare * spot * (0.55 + 0.45 * sin(t * 3.0 + n * 30.0));
}

void main() {
    vec3 faceN = normalize(cross(dFdx(WorldPos), dFdy(WorldPos)));
    vec3 an = abs(faceN);
    vec2 p;
    if (an.y > an.x && an.y > an.z) {
        p = WorldPos.xz;
    } else if (an.x > an.z) {
        p = WorldPos.zy;
    } else {
        p = WorldPos.xy;
    }

    float t = Time;
    vec2 uv = p * 3.0;

    vec2 q = vec2(fbm(uv + t * 0.05), fbm(uv + vec2(5.2, 1.3) - t * 0.04));
    float n = fbm(uv + q * 2.0 + t * 0.03);

    vec3 baseHsv = rgb2hsv(TintColor);
    float hue = baseHsv.x;
    float sat = min(baseHsv.y * 1.2, 1.0);
    float val = max(baseHsv.z, 0.75);

    vec3 c1 = hsv2rgb(vec3(fract(hue - 0.06 + 1.0), sat, val * 0.14));
    vec3 c2 = hsv2rgb(vec3(hue, sat, val * 0.5));
    vec3 c3 = hsv2rgb(vec3(fract(hue + 0.07), sat, val * 0.95));
    vec3 c4 = hsv2rgb(vec3(fract(hue + 0.15), sat * 0.85, val));

    vec3 color = mix(c1, c2, smoothstep(0.2, 0.5, n));
    color = mix(color, c3, smoothstep(0.5, 0.72, n));
    color = mix(color, c4, smoothstep(0.72, 0.9, n) * (0.5 + 0.5 * sin(t * 1.5)));

    float star = an_stars(p, t);
    color += vec3(0.9, 0.95, 1.0) * star;

    vec3 viewDir = normalize(CameraPos - WorldPos);
    vec3 nrm = faceN * sign(dot(faceN, viewDir) + 1e-4);
    vec3 lightDir = normalize(vec3(0.5, 0.9, 0.4));
    vec3 halfDir = normalize(lightDir + viewDir);
    float spec = pow(max(dot(nrm, halfDir), 0.0), 24.0) * 0.3;

    vec3 emissive = c3 * smoothstep(0.62, 0.9, n) * 1.2;

    float pulse = 0.9 + 0.1 * sin(t * 0.9 + n * 6.0);
    color = clamp((color + emissive) * pulse + spec, 0.0, 1.0);

    float finalAlpha = Fade * clamp(0.62 + 0.3 * smoothstep(0.35, 0.85, n) + 0.4 * star, 0.0, 1.0);
    OutColor = vec4(color, finalAlpha * FragColor.a);
}
