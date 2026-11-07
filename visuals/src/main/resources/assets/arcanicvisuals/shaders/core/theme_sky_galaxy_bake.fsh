#version 150

// A theme sky, baked into an equirectangular texture; uniform interface adapted to the Arcanic renderer.
layout(std140) uniform SkyBake {
    vec4 AccentTime;
    vec4 Size;
};
#define Time AccentTime.w
#define Accent AccentTime.rgb
#define TexCoord (gl_FragCoord.xy / Size.xy)

out vec4 OutColor;

vec3 hash33(vec3 p3) {
    p3 = fract(p3 * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yxz + 33.33);
    return fract((p3.xxy + p3.yxx) * p3.zyx);
}

float valueNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash33(i).x;
    float n100 = hash33(i + vec3(1.0, 0.0, 0.0)).x;
    float n010 = hash33(i + vec3(0.0, 1.0, 0.0)).x;
    float n110 = hash33(i + vec3(1.0, 1.0, 0.0)).x;
    float n001 = hash33(i + vec3(0.0, 0.0, 1.0)).x;
    float n101 = hash33(i + vec3(1.0, 0.0, 1.0)).x;
    float n011 = hash33(i + vec3(0.0, 1.0, 1.0)).x;
    float n111 = hash33(i + vec3(1.0)).x;
    float nx00 = mix(n000, n100, f.x);
    float nx10 = mix(n010, n110, f.x);
    float nx01 = mix(n001, n101, f.x);
    float nx11 = mix(n011, n111, f.x);
    return mix(mix(nx00, nx10, f.y), mix(nx01, nx11, f.y), f.z);
}

float fbm(vec3 p) {
    float sum = 0.0;
    float amplitude = 0.5;
    float frequency = 1.0;
    for (int i = 0; i < 5; i++) {
        sum += amplitude * valueNoise(p * frequency);
        frequency *= 2.02;
        amplitude *= 0.5;
    }
    return sum;
}

void main() {
    float longitude = (TexCoord.x - 0.5) * 6.28318530718;
    float latitude = (0.5 - TexCoord.y) * 3.14159265359;
    vec3 direction = vec3(cos(latitude) * cos(longitude), sin(latitude), cos(latitude) * sin(longitude));
    float time = Time * 0.35;

    vec3 primary = max(Accent, vec3(0.05));
    vec3 secondary = mix(primary, vec3(0.16, 0.52, 1.0), 0.55);
    secondary = mix(secondary, vec3(1.0, 0.54, 0.22), 0.28);

    vec3 position = direction * 2.2 + vec3(time * 0.030, time * 0.018, -time * 0.022);
    vec3 warp = vec3(
        fbm(position * 0.7 + vec3(time * 0.08, 0.0, 0.0)),
        fbm(position * 0.7 + vec3(5.2, time * 0.07, 1.3)),
        fbm(position * 0.7 + vec3(1.7, 9.2, -time * 0.06))
    );
    position += (warp - 0.5) * 2.0;
    float firstNoise = fbm(position * 1.1);
    float secondNoise = fbm(position * 2.4 + 4.0);
    float density = pow(
        smoothstep(0.32, 0.95, firstNoise * 0.7 + secondNoise * 0.3),
        1.4
    );
    float hue = fbm(position * 0.6 + 9.0);
    vec3 nebula = mix(primary, secondary, smoothstep(0.18, 0.85, hue));
    nebula = mix(nebula, nebula * 1.7 + 0.25, density);

    vec3 color = nebula * density * 1.35;
    color += mix(primary, secondary, 0.5) * firstNoise * firstNoise * 0.12;

    color = color / (1.0 + color);
    color = pow(color, vec3(0.85));

    OutColor = vec4(clamp(color, 0.0, 1.0), clamp(firstNoise, 0.0, 1.0));
}
