#version 150

#moj_import <arcanicvisuals:bo_uniforms.glsl>
#moj_import <arcanicvisuals:arc_common.glsl>

in vec2 FragCoord;
in vec4 FragColor;
out vec4 OutColor;

#define time Time
#define screenSize Resolution
#define baseColor vec4(TintColor, 1.0)
#define alpha Fade
#define fragColor OutColor

#define NUM_OCTAVES 4

float holyRand(vec2 n) {
    return fract(cos(dot(n, vec2(12.9898, 4.1414))) * 43758.5453);
}

float holyNoise(vec2 n) {
    const vec2 d = vec2(0.0, 1.0);
    vec2 b = floor(n);
    vec2 f = smoothstep(vec2(0.0), vec2(1.0), fract(n));
    return mix(mix(holyRand(b), holyRand(b + d.yx), f.x), mix(holyRand(b + d.xy), holyRand(b + d.yy), f.x), f.y);
}

float holyFbm(vec2 n) {
    float total = 0.0;
    float amplitude = 1.6;
    for (int i = 0; i < NUM_OCTAVES; i++) {
        total += holyNoise(n) * amplitude;
        n += n;
        amplitude *= 0.5;
    }
    return total;
}

void main() {
    vec2 fragCoord = gl_FragCoord.xy;

    vec3 tint = baseColor.rgb;
    vec3 c1 = tint * 0.5;
    vec3 c2 = tint * 0.85;
    vec3 c3 = vec3(0.08);
    vec3 c4 = tint * 1.25;
    vec3 c5 = vec3(0.1);
    vec3 c6 = vec3(0.9);

    vec2 p = fragCoord * 8.0 / screenSize.xx;
    float q = holyFbm(p - time * 0.1);
    vec2 r = vec2(holyFbm(p + q + time - p.x - p.y), holyFbm(p + q - time));
    vec3 c = mix(c1, c2, holyFbm(p + r)) + mix(c3, c4, r.y) - mix(c5, c6, r.x);

    float grad = fragCoord.y / screenSize.y;
    vec3 color = c * cos(1.6 * grad);
    color *= 1.0 - grad;

    color = max(color, vec3(0.0)) * 1.7;
    color = color / (color + vec3(0.85));
    color += tint * 0.1;
    color = clamp(color, 0.0, 1.0);

    float glow = clamp(length(color), 0.0, 1.0);

    fragColor = vec4(color, alpha * (0.22 + glow * 0.42) * FragColor.a);
}
