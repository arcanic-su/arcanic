#version 150

// A theme sky shader; uniform interface adapted to the Arcanic renderer: the view direction comes from
// the inverted view-projection instead of a sky cube's positions.
layout(std140) uniform ThemeSky {
    mat4 InvViewProj;
    vec4 AccentTime;
    vec4 Modulator;
};
#define Time AccentTime.w
#define Accent AccentTime.rgb
#define ColorModulator Modulator

in vec2 vNdc;

vec3 skyDirection() {
    vec4 far = InvViewProj * vec4(vNdc, 1.0, 1.0);
    vec4 near = InvViewProj * vec4(vNdc, -1.0, 1.0);
    return far.xyz / far.w - near.xyz / near.w;
}

out vec4 OutColor;

const float TAU = 6.28318530718;

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash13(vec3 p) {
    p = fract(p * vec3(443.8975, 397.2973, 491.1871));
    p += dot(p, p.yxz + 19.19);
    return fract((p.x + p.y) * p.z);
}

float vnoise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash13(i + vec3(0.0, 0.0, 0.0)), hash13(i + vec3(1.0, 0.0, 0.0)), f.x),
            mix(hash13(i + vec3(0.0, 1.0, 0.0)), hash13(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
        mix(mix(hash13(i + vec3(0.0, 0.0, 1.0)), hash13(i + vec3(1.0, 0.0, 1.0)), f.x),
            mix(hash13(i + vec3(0.0, 1.0, 1.0)), hash13(i + vec3(1.0, 1.0, 1.0)), f.x), f.y),
        f.z);
}

float fbm(vec3 p, int oct) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 6; i++) {
        if (i >= oct) break;
        v += a * vnoise(p);
        p = p * 2.07 + vec3(1.7, 9.2, 4.3);
        a *= 0.5;
    }
    return v;
}

float ridged(vec3 p, int oct) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 6; i++) {
        if (i >= oct) break;
        float n = vnoise(p);
        n = 1.0 - abs(n - 0.5) * 2.0;
        v += a * n;
        p = p * 2.07 + vec3(1.7, 9.2, 4.3);
        a *= 0.5;
    }
    return v;
}

vec3 rotateY(vec3 p, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec3(p.x * c - p.z * s, p.y, p.x * s + p.z * c);
}

vec3 rotateX(vec3 p, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec3(p.x, p.y * c - p.z * s, p.y * s + p.z * c);
}

struct Palette {
    vec3 main;
    vec3 alt;
    vec3 comp;
    vec3 warm;     // warm shift
    vec3 cool;     // cool shift
    vec3 bright;   // brightened
    vec3 deep;
    vec3 core;
};

Palette buildPalette(vec3 accent) {
    Palette p;
    p.main   = accent;
    p.alt    = clamp(accent.gbr * 1.10, vec3(0.05), vec3(1.0));
    p.comp   = clamp(vec3(1.0) - accent * 0.75, vec3(0.05), vec3(1.0));
    p.warm   = clamp(mix(accent, vec3(1.00, 0.55, 0.18), 0.55), vec3(0.05), vec3(1.0));
    p.cool   = clamp(mix(accent, vec3(0.20, 0.55, 1.00), 0.55), vec3(0.05), vec3(1.0));
    p.bright = mix(accent, vec3(1.0), 0.55);
    p.deep   = accent * 0.18 + vec3(0.003, 0.002, 0.012);
    p.core   = mix(accent * 1.6, vec3(1.0, 0.95, 0.88), 0.50);
    return p;
}

vec3 nebulaPalette(float density, Palette pal) {
    float warmth = pal.main.r - pal.main.b;
    vec3 hot = mix(pal.cool, pal.warm, smoothstep(-0.30, 0.30, warmth));

    vec3 col;
    if (density < 0.35) {
        col = mix(pal.deep, pal.main * 0.75, density / 0.35);
    } else if (density < 0.72) {
        col = mix(pal.main * 0.75, hot, (density - 0.35) / 0.37);
    } else {
        col = mix(hot, pal.core, (density - 0.72) / 0.28);
    }
    return col;
}

vec3 nebulaCluster(vec3 d, vec3 center, vec3 col, float t, float seed) {
    float ddc = dot(d, center);
    if (ddc < 0.35) return vec3(0.0);
    float ang = acos(clamp(ddc, -1.0, 1.0));

    vec3 tang = d - center * ddc;
    vec3 q = tang * 4.5 + vec3(seed * 13.7) + vec3(t * 0.05, t * 0.04, t * 0.06);
    float shape = fbm(q, 3);
    shape = smoothstep(0.30, 0.90, shape);

    float halo = exp(-ang * 6.0);
    float core = exp(-ang * 90.0) * 2.5;
    float pulse = 0.85 + 0.15 * sin(t * 0.6 + seed * 4.0);

    vec3 ref = normalize(cross(center, vec3(0.0, 1.0, 0.001)));
    vec3 bin = cross(center, ref);
    float u = dot(tang, ref);
    float v = dot(tang, bin);
    float spikeH = exp(-abs(v) * 60.0) * exp(-abs(u) * 4.0);
    float spikeV = exp(-abs(u) * 60.0) * exp(-abs(v) * 4.0);
    float spikes = (spikeH + spikeV) * exp(-ang * 12.0) * 0.6;

    return col * (halo * shape * 1.2 + core * pulse + spikes);
}

vec3 starLayer(vec3 d, float density, float threshold, float size, vec3 baseTint) {
    vec3 p = d * density;
    vec3 i = floor(p);
    float h = hash13(i);
    if (h < threshold) return vec3(0.0);
    vec3 f = fract(p) - 0.5;
    float disc = 1.0 - smoothstep(0.0, size, length(f));
    float bright = (h - threshold) / (1.0 - threshold);
    float twinkle = 0.40 + 0.60 * sin(Time * 3.5 + h * 113.0);

    float spec = hash13(i + vec3(7.7, 13.3, 17.1));
    vec3 starCol;
    if (spec < 0.18)      starCol = vec3(1.00, 0.55, 0.40);
    else if (spec < 0.40) starCol = vec3(1.00, 0.82, 0.60);
    else if (spec < 0.65) starCol = vec3(1.00, 0.95, 0.85);
    else if (spec < 0.85) starCol = vec3(0.95, 0.96, 1.00);
    else                  starCol = vec3(0.60, 0.78, 1.00);
    starCol = mix(starCol, baseTint, 0.25);

    float intensity = disc * bright * (0.25 + 0.75 * twinkle);
    vec3 result = starCol * intensity;

    if (h > threshold + (1.0 - threshold) * 0.62) {
        float spikeH = exp(-abs(f.y) * 70.0) * exp(-abs(f.x) * 3.5);
        float spikeV = exp(-abs(f.x) * 70.0) * exp(-abs(f.y) * 3.5);
        float crossI = (spikeH + spikeV) * bright * twinkle * 0.55;
        result += starCol * crossI;
    }
    return result;
}

vec3 cometStreak(vec3 d, float t, vec3 col) {
    float cycle = 14.0;
    float phase = mod(t, cycle);
    float life = 3.6;
    if (phase > life) return vec3(0.0);

    float seed = floor(t / cycle);
    vec3 axis = normalize(vec3(
        hash11(seed * 1.13) - 0.5,
        hash11(seed * 2.31 + 1.0) * 0.6 + 0.2,
        hash11(seed * 3.47 + 2.0) - 0.5
    ));
    vec3 ref = normalize(cross(axis, vec3(0.0, 1.0, 0.001)));
    vec3 perp = cross(axis, ref);

    float u = phase / life;
    float ease = u * u * (3.0 - 2.0 * u);
    float angle = ease * 2.2;

    vec3 head = ref * cos(angle) + perp * sin(angle);
    float distHead = max(0.0, 1.0 - dot(d, head));
    float core = exp(-distHead * 1800.0);

    float tail = 0.0;
    for (int k = 1; k < 7; k++) {
        float a = angle - float(k) * 0.05;
        if (a < 0.0) break;
        vec3 tp = ref * cos(a) + perp * sin(a);
        float dt = max(0.0, 1.0 - dot(d, tp));
        tail += exp(-dt * (1300.0 - float(k) * 120.0)) * (1.0 - float(k) / 7.0) * 0.45;
    }

    float fade = smoothstep(0.0, 0.15, u) * (1.0 - smoothstep(0.85, 1.0, u));
    return col * (core * 1.8 + tail) * fade;
}

void main() {
    vec3 d = normalize(skyDirection());
    float t = Time;

    vec3 accent = max(Accent, vec3(0.05));
    Palette pal = buildPalette(accent);

    vec3 dr = rotateY(d, t * 0.05);
    dr = rotateX(dr, sin(t * 0.04) * 0.18);

    float breath = 0.72 + 0.28 * sin(t * 0.5);
    float corePulse = 0.80 + 0.20 * sin(t * 0.85);

    float vertical = abs(d.y);
    vec3 zenith     = pal.deep;
    vec3 horizonCol = mix(pal.deep, pal.main * 0.45, 0.65);
    vec3 base = mix(horizonCol, zenith, smoothstep(0.0, 0.85, vertical));

    vec3 mwAxis = normalize(vec3(0.55 + 0.05 * sin(t * 0.05), 0.30, 0.80));
    float mwAlong = abs(dot(d, mwAxis));
    float mwPlane = 1.0 - mwAlong * 1.45;
    mwPlane = smoothstep(0.18, 0.92, mwPlane);
    float mwNoise = fbm(dr * 4.5 + vec3(t * 0.05, 0.0, t * 0.03), 4);
    float milkyWay = mwPlane * (0.30 + 0.70 * mwNoise);
    float dustLane = smoothstep(0.85, 1.0, mwPlane) * smoothstep(0.4, 0.7, mwNoise);
    milkyWay *= 1.0 - dustLane * 0.65;

    vec3 q1 = dr * 1.35;
    vec3 warp1 = vec3(
        fbm(q1 * 0.65 + vec3(t * 0.085, 0.0, 0.0), 3),
        fbm(q1 * 0.65 + vec3(0.0, t * 0.07, 5.2), 3),
        fbm(q1 * 0.65 + vec3(7.4, 0.0, t * 0.075), 3)
    );
    q1 += (warp1 - 0.5) * 1.5 + vec3(t * 0.055, t * 0.035, t * 0.025);
    float n1 = fbm(q1, 5);
    float cloud1 = smoothstep(0.40, 0.96, n1);

    vec3 q2 = dr * 2.9 + vec3(t * 0.110, t * 0.085, -t * 0.065);
    float n2 = fbm(q2, 4);
    float cloud2 = smoothstep(0.50, 0.92, n2) * 0.65;

    vec3 q3 = dr * 2.1 + vec3(-t * 0.045, t * 0.055, t * 0.035);
    float n3 = ridged(q3, 4);
    float filaments = smoothstep(0.55, 0.93, n3) * 0.6;

    float density = cloud1 * 0.70 + cloud2 * 0.50 + filaments * 0.45 + milkyWay * 0.55;
    density *= breath;

    vec3 nebColor = nebulaPalette(density, pal);

    float phase = sin(t * 0.25 + n1 * 5.0) * 0.5 + 0.5;
    nebColor = mix(nebColor, pal.comp, phase * 0.20);

    vec3 color = base + nebColor * density * 1.45;

    float hotCore = smoothstep(0.75, 1.0, n1 * cloud1);
    hotCore = pow(hotCore, 1.6) * corePulse;
    color += pal.core * hotCore * 1.4;

    float streak = pow(smoothstep(0.72, 1.0, n1), 4.0);
    streak *= 0.55 + 0.6 * sin(t * 0.85 + n2 * 6.0);
    color += pal.main * streak * 0.85;

    vec3 c1Center = normalize(vec3( 0.55,  0.40,  0.75));
    vec3 c2Center = normalize(vec3(-0.65,  0.30, -0.45));
    vec3 c3Center = normalize(vec3( 0.40, -0.25, -0.85));
    color += nebulaCluster(d, c1Center, pal.warm   * 1.10, t, 1.0) * 0.95;
    color += nebulaCluster(d, c2Center, pal.cool   * 1.10, t, 2.0) * 0.85;
    color += nebulaCluster(d, c3Center, pal.alt    * 1.10, t, 3.0) * 0.75;

    float horizonBand = 1.0 - smoothstep(0.0, 0.35, abs(d.y));
    horizonBand = pow(horizonBand, 1.7);
    float az = atan(d.z, d.x);
    float dawn = sin(az + t * 0.04) * 0.5 + 0.5;
    dawn = pow(dawn, 1.6);
    vec3 atmosCol = mix(pal.deep * 2.5, pal.warm * 1.4, dawn);
    color += atmosCol * horizonBand * 0.55;

    if (d.y < 0.6) {
        float horizonFade = 1.0 - smoothstep(-0.05, 0.55, d.y);
        float wave = sin(d.x * 7.0 + t * 0.85)
                   * sin(d.z * 5.0 - t * 0.65)
                   * sin(d.x * 3.0 + d.z * 2.0 + t * 0.45);
        wave = wave * 0.5 + 0.5;
        wave = pow(wave, 2.2);
        float curtains = sin(atan(d.z, d.x) * 18.0 + t * 0.6) * 0.5 + 0.5;
        curtains = pow(curtains, 3.0);
        wave = mix(wave, wave * (0.4 + 0.8 * curtains), 0.5);
        float bandY = sin(t * 0.30 + d.x * 2.5) * 0.10 + 0.04;
        float band = 1.0 - smoothstep(0.0, 0.18, abs(d.y - bandY));
        wave = wave * (band * 0.6 + 0.4);
        vec3 auroraCol = mix(pal.main, pal.alt, 0.45);
        color += auroraCol * wave * horizonFade * 1.05;
        color += pal.comp * wave * horizonFade * 0.30;
    }

    vec3 starDir = rotateY(d, t * 0.012);
    vec3 starsA = starLayer(starDir, 250.0, 0.987, 0.035, pal.bright);
    vec3 starsB = starLayer(starDir, 100.0, 0.993, 0.055, mix(pal.bright, pal.main, 0.5));
    float starOcclusion = 1.0 - smoothstep(0.20, 0.65, density) * 0.85;
    color += starsA * starOcclusion;
    color += starsB * 1.55 * starOcclusion;

    color += cometStreak(d, t, mix(vec3(1.00, 0.92, 0.78), pal.bright, 0.30));

    color *= 1.0 - smoothstep(0.85, 1.0, vertical) * 0.18;

    // Tonemap.
    color = color / (1.0 + color * 0.42);
    color = pow(color, vec3(0.90));

    OutColor = vec4(color, 1.0) * ColorModulator;
}
