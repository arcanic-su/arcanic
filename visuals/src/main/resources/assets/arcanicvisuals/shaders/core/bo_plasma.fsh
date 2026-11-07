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

float plasma(vec2 uv, float t) {
    float v = 0.0;

    v += sin(uv.x * 10.0 + t);
    v += sin((uv.y * 10.0 + t) * 0.5);
    v += sin((uv.x * 10.0 + uv.y * 10.0 + t) * 0.5);

    vec2 c = uv * 10.0 - vec2(5.0);
    v += sin(sqrt(c.x * c.x + c.y * c.y + 1.0) + t);

    c = uv * 10.0 - vec2(5.0 + sin(t * 0.5) * 3.0, 5.0 + cos(t * 0.7) * 3.0);
    v += sin(sqrt(c.x * c.x + c.y * c.y + 1.0) + t * 1.5);

    return v * 0.2;
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    vec2 uv = gl_FragCoord.xy / screenSize;

    float t = time * 0.8;

    float p = plasma(uv, t);

    float hue = p + time * 0.1;

    vec3 rainbow = hsv2rgb(vec3(hue, 0.8, 1.0));
    vec3 base = baseColor.rgb;

    float flowVal = 0.8 + p * 0.4;
    vec3 color = baseColor.rgb * flowVal;

    float glow = pow(abs(sin(p * 3.14159 * 2.0)), 2.0) * 0.5;
    color += baseColor.rgb * glow;

    float pulse = 0.8 + sin(time * 2.0 + p * 5.0) * 0.2;
    color *= pulse;

    color = clamp(color, 0.0, 1.0);

    float finalAlpha = alpha * (0.3 + abs(sin(p * 3.14159)) * 0.4);

    fragColor = vec4(color, finalAlpha * FragColor.a);
}
