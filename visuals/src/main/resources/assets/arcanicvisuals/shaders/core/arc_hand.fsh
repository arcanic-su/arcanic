#version 150

layout(std140) uniform ArcHand { vec4 HandColor; vec2 Resolution; float Time; float HandShaderMode; float HandShaderOnly; };
// common.glsl
// helper functions and constants for client shaders

/**
 * Computes the Signed Distance Function (SDF) for a rectangle with the ability
 * to set individual corner radii.
 *
 * @param CenterPosition - point coordinate relative to the rectangle center.
 * @param Size - half the width and height of the rectangle (i.e. from center to edge).
 * @param Radius - corner radii in the following order:
 *                 (top-left, top-right, bottom-right, bottom-left).
 *
 * @return float - distance from the point to the nearest rectangle surface.
 *                 Negative value means inside, positive means outside.
 */
float roundedBoxSDF(vec2 CenterPosition, vec2 Size, vec4 Radius) {
    // Clamp radii so they do not exceed half the rectangle sides.
    Radius = min(Radius, vec4(min(Size.x, Size.y)));

    // Pick the radius depending on the quadrant the point is in.
    Radius.xy = (CenterPosition.x > 0.0) ? Radius.xy : Radius.zw;
    Radius.x  = (CenterPosition.y > 0.0) ? Radius.x  : Radius.y;

    // Offset coordinates by radius and compute distance.
    vec2 q = abs(CenterPosition) - Size + Radius.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - Radius.x;
}

/**
 * Quad vertices helper.
 *
 * Vertices go clockwise:
 * 0 - top-left (0, 0)
 * 1 - bottom-left (0, 1)
 * 2 - bottom-right (1, 1)
 * 3 - top-right (1, 0)
 */
const vec2[4] RECT_VERTICES_COORDS = vec2[] (
    vec2(0.0, 0.0),
    vec2(0.0, 1.0),
    vec2(1.0, 1.0),
    vec2(1.0, 0.0)
);

vec2 rvertexcoord(int id) {
    return RECT_VERTICES_COORDS[id % 4];
}

float rdist(vec2 pos, vec2 size, vec4 radius) {
    radius = min(radius, vec4(min(size.x, size.y)));
    radius.xy = (pos.x > 0.0) ? radius.xy : radius.wz;
    radius.x  = (pos.y > 0.0) ? radius.x : radius.y;

    vec2 v = abs(pos) - size + radius.x;
    return min(max(v.x, v.y), 0.0) + length(max(v, 0.0)) - radius.x;
}

float ralpha(vec2 size, vec2 coord, vec4 radius, float smoothness) {
    vec2 center = size * 0.5;
    float dist = rdist(center - (coord * size), center, radius);
    return 1.0 - smoothstep(-smoothness, 0.0, dist);
}

/**
 * Alpha falloff for one mask edge.
 *
 * @param distance - signed distance from the edge, positive inside the kept region.
 * @param fade     - width of the falloff band in pixels; 0 means a hard cut.
 */
float maskEdgeFactor(float distance, float fade) {
    if (fade <= 0.0) {
        return distance < 0.0 ? 0.0 : 1.0;
    }
    float t = clamp(distance / fade, 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

/**
 * Rectangular alpha mask with an independent falloff per edge.
 * Modifies ONLY alpha - never the RGB channels, so this can never introduce a
 * black vignette.
 *
 * The fragment coordinate is passed in rather than read from gl_FragCoord so this
 * header stays compilable from vertex shaders, which also import common.glsl.
 *
 * @param alpha     - incoming fragment alpha.
 * @param fragCoord - gl_FragCoord.xy of the caller.
 * @param bounds    - (topY, bottomY, leftX, rightX) in gl_FragCoord space. Both Y
 *                    entries negative disables the mask entirely.
 * @param fades     - (fadeTop, fadeBottom, fadeLeft, fadeRight) band widths in pixels,
 *                    matching the screen orientation of the bounds.
 */
float applyEdgeMask(float alpha, vec2 fragCoord, vec4 bounds, vec4 fades) {
    if (bounds.x < 0.0 && bounds.y < 0.0) {
        return alpha;
    }

    float edge = 1.0;

    if (bounds.x >= 0.0 || bounds.y >= 0.0) {
        float lowY = min(bounds.x, bounds.y);
        float highY = max(bounds.x, bounds.y);
        edge *= maskEdgeFactor(fragCoord.y - lowY, fades.y);
        edge *= maskEdgeFactor(highY - fragCoord.y, fades.x);
    }

    if (bounds.z >= 0.0 || bounds.w >= 0.0) {
        float lowX = min(bounds.z, bounds.w);
        float highX = max(bounds.z, bounds.w);
        edge *= maskEdgeFactor(fragCoord.x - lowX, fades.z);
        edge *= maskEdgeFactor(highX - fragCoord.x, fades.w);
    }

    return alpha * edge;
}

float hash12(vec2 p) {
    vec3 p3  = fract(vec3(p.xyx) * .1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float common_noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = p - i;
    vec2 u = f*f*(3.0-2.0*f);
    return mix(mix(hash12(i + vec2(0.0,0.0)), hash12(i + vec2(1.0,0.0)), u.x),
               mix(hash12(i + vec2(0.0,1.0)), hash12(i + vec2(1.0,1.0)), u.x), u.y);
}

float fbm(vec2 x) {
    float v = 0.0;
    float a = 0.5;
    vec2 shift = vec2(100.0);
    mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.5));
    for (int i = 0; i < 5; ++i) {
        v += a * common_noise(x);
        x = rot * x * 2.0 + shift;
        a *= 0.5;
    }
    return v;
}

const int LOOP_OCTAVES = 4;
const float LOOP_LACUNARITY = 2.0;
const float LOOP_GAIN = 0.5;
const vec2 LOOP_OCTAVE_SHIFT = vec2(37.1, 11.7);

/**
 * Value noise whose lattice wraps every `tile` cells, so translating the sample
 * point by a whole multiple of `tile` reproduces the exact same field. This is
 * what lets an animated background loop without a visible seam.
 */
float loopNoise(vec2 p, float tile) {
    vec2 i = floor(p);
    vec2 f = p - i;
    vec2 u = f * f * (3.0 - 2.0 * f);
    vec2 low = mod(i, tile);
    vec2 high = mod(i + 1.0, tile);
    return mix(mix(hash12(low), hash12(vec2(high.x, low.y)), u.x),
               mix(hash12(vec2(low.x, high.y)), hash12(high), u.x), u.y);
}

/**
 * Fractal brownian motion built on loopNoise; every octave wraps on its own
 * period so the whole sum stays seamless over `tile` units.
 */
float loopFbm(vec2 p, float tile) {
    float total = 0.0;
    float amplitude = LOOP_GAIN;
    float period = tile;
    for (int i = 0; i < LOOP_OCTAVES; ++i) {
        total += amplitude * loopNoise(p, period);
        p = p * LOOP_LACUNARITY + LOOP_OCTAVE_SHIFT;
        period *= LOOP_LACUNARITY;
        amplitude *= LOOP_GAIN;
    }
    return total;
}

// 3D Hash
const uint HASH_LCG_MULTIPLIER = 1664525u;
const uint HASH_LCG_INCREMENT = 1013904223u;
const uint HASH_MIX_SHIFT = 16u;
const int HASH_LATTICE_BIAS = 1048576;
const float HASH_UINT_TO_UNIT = 1.0 / 4294967296.0;

float hash13(vec3 p3) {
    uvec3 v = uvec3(ivec3(floor(p3)) + HASH_LATTICE_BIAS);
    v = v * HASH_LCG_MULTIPLIER + HASH_LCG_INCREMENT;
    v.x += v.y * v.z;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    v ^= v >> HASH_MIX_SHIFT;
    v.x += v.y * v.z;
    v.y += v.z * v.x;
    v.z += v.x * v.y;
    return float(v.x) * HASH_UINT_TO_UNIT;
}

// 3D Value Noise
vec3 quinticFade(vec3 f) {
    return f*f*f*(f*(f*6.0-15.0)+10.0);
}

float common_noise3d(vec3 p) {
    vec3 i = floor(p);
    vec3 f = p - i;
    vec3 u = quinticFade(f);

    float n000 = hash13(i + vec3(0.0,0.0,0.0));
    float n100 = hash13(i + vec3(1.0,0.0,0.0));
    float n010 = hash13(i + vec3(0.0,1.0,0.0));
    float n110 = hash13(i + vec3(1.0,1.0,0.0));
    float n001 = hash13(i + vec3(0.0,0.0,1.0));
    float n101 = hash13(i + vec3(1.0,0.0,1.0));
    float n011 = hash13(i + vec3(0.0,1.0,1.0));
    float n111 = hash13(i + vec3(1.0,1.0,1.0));

    float mixX00 = mix(n000, n100, u.x);
    float mixX10 = mix(n010, n110, u.x);
    float mixX01 = mix(n001, n101, u.x);
    float mixX11 = mix(n011, n111, u.x);

    float mixY0 = mix(mixX00, mixX10, u.y);
    float mixY1 = mix(mixX01, mixX11, u.y);

    return mix(mixY0, mixY1, u.z);
}

// 3D FBM
float fbm3d(vec3 x) {
    float v = 0.0;
    float a = 0.5;
    vec3 shift = vec3(100.0);
    // Simple 3D rotation matrix to avoid alignment artifacts
    mat3 rot = mat3(
        0.36,  0.48, -0.80,
       -0.80,  0.60,  0.00,
        0.48,  0.64,  0.60
    );
    for (int i = 0; i < 3; ++i) {
        v += a * common_noise3d(x);
        x = rot * x * 2.0 + shift;
        a *= 0.5;
    }
    return v;
}

// 3D to 3D Hash
vec3 hash33(vec3 p3) {
    p3 = fract(p3 * vec3(.1031, .1030, .0973));
    p3 += dot(p3, p3.yxz+33.33);
    return fract((p3.xxy + p3.yxx)*p3.zyx);
}

// 3D Cellular/Voronoi Noise
// Returns vec2(min_dist, id)
vec2 cellular3d(vec3 p, float t) {
    vec3 n = floor(p);
    vec3 f = p - n;
    float min_dist = 10.0;
    float id = 0.0;
    
    for(int k=-1; k<=1; k++) {
        for(int j=-1; j<=1; j++) {
            for(int i=-1; i<=1; i++) {
                vec3 g = vec3(float(i),float(j),float(k));
                vec3 o = hash33(n + g);
                // Animate the points
                o = 0.5 + 0.5 * sin(t + 6.2831 * o);
                vec3 r = g + o - f;
                float d = dot(r,r);
                if(d < min_dist) {
                    min_dist = d;
                    id = hash13(n + g);
                }
            }
        }
    }
    return vec2(sqrt(min_dist), id);
}


in vec2 FragCoord;
in vec2 TexCoord;
in vec4 FragColor;

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

out vec4 OutColor;

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}


#define COSMOS_ITERATIONS 14
#define COSMOS_FORMUPARAM2 0.79
#define COSMOS_VOLSTEPS 5
#define COSMOS_STEPSIZE 0.390
#define COSMOS_ZOOM 0.900
#define COSMOS_TILE 0.850
#define COSMOS_BRIGHTNESS 0.003
#define COSMOS_DARKMATTER 0.400
#define COSMOS_DISTFADING 0.560
#define COSMOS_SATURATION 0.800
#define COSMOS_TRANSVERSE_SPEED 1.8
#define COSMOS_CLOUD 0.11

float cosmos_triangle(float x, float a) {
    return 2.0 * abs(2.0 * ((x/a) - floor((x/a) + 0.5))) - 1.0;
}

float cosmos_field(in vec3 p, float t) {
    float strength = 7. + .03 * log(1.e-6 + fract(sin(t) * 4373.11));
    float accum = 0.;
    float prev = 0.;
    float tw = 0.;

    float mag = dot(p, p);
    p = abs(p) / mag + vec3(-.5, -.8 + 0.1*sin(t*0.7 + 2.0), -1.1+0.3*cos(t*0.3));
    float w = exp(0.);
    accum += w * exp(-strength * pow(abs(mag - prev), 2.3));
    tw += w;
    prev = mag;

    return max(0., 5. * accum / tw - .7);
}

vec3 cosmos_effect(vec2 uv, float t) {
    vec2 uvs = uv * 2.0 - 1.0;

    float time2 = t;
    float speed = .01 * cos(time2*0.02 + 3.1415926/4.0);
    float formuparam = COSMOS_FORMUPARAM2;

    float a_xz = 0.9;
    float a_yz = -.6;
    float a_xy = 0.9 + t*0.08;

    mat2 rot_xz = mat2(cos(a_xz),sin(a_xz),-sin(a_xz),cos(a_xz));
    mat2 rot_yz = mat2(cos(a_yz),sin(a_yz),-sin(a_yz),cos(a_yz));
    mat2 rot_xy = mat2(cos(a_xy),sin(a_xy),-sin(a_xy),cos(a_xy));

    vec3 dir = vec3(uvs*COSMOS_ZOOM, 1.);
    vec3 from = vec3(0.0, 0.0, 0.0);

    vec3 forward = vec3(0.,0.,1.);
    from.x += COSMOS_TRANSVERSE_SPEED * cos(0.01*t) + 0.001*t;
    from.y += COSMOS_TRANSVERSE_SPEED * sin(0.01*t) + 0.001*t;
    from.z += 0.003*t;

    dir.xy *= rot_xy;
    forward.xy *= rot_xy;
    dir.xz *= rot_xz;
    forward.xz *= rot_xz;
    dir.yz *= rot_yz;
    forward.yz *= rot_yz;

    from.xy *= -rot_xy;
    from.xz *= rot_xz;
    from.yz *= rot_yz;

    float zooom = (time2-3311.)*speed;
    from += forward * zooom;
    float sampleShift = mod(zooom, COSMOS_STEPSIZE);
    float zoffset = -sampleShift;
    sampleShift /= COSMOS_STEPSIZE;

    float s = 0.24;
    float s3 = s + COSMOS_STEPSIZE/2.0;
    vec3 v = vec3(0.);
    float t3 = 0.0;

    vec3 backCol2 = vec3(0.);
    for (int r = 0; r < COSMOS_VOLSTEPS; r++) {
        vec3 p2 = from + (s+zoffset)*dir;
        vec3 p3 = from + (s3+zoffset)*dir;

        p2 = abs(vec3(COSMOS_TILE) - mod(p2, vec3(COSMOS_TILE*2.)));
        p3 = abs(vec3(COSMOS_TILE) - mod(p3, vec3(COSMOS_TILE*2.)));
        t3 = cosmos_field(p3, t);

        float pa = 0., a = 0.;
        for (int i = 0; i < COSMOS_ITERATIONS; i++) {
            p2 = abs(p2)/dot(p2,p2) - formuparam;
            float D = abs(length(p2)-pa);
            a += i > 7 ? min(12., D) : D;
            pa = length(p2);
        }

        a *= a*a;
        float s1 = s + zoffset;
        float fade = pow(COSMOS_DISTFADING, max(0., float(r)-sampleShift));

        v += fade;

        if (r == 0)
            fade *= (1. - sampleShift);
        if (r == COSMOS_VOLSTEPS-1)
            fade *= sampleShift;

        v += vec3(s1, s1*s1, s1*s1*s1*s1) * a * COSMOS_BRIGHTNESS * fade;
        backCol2 += mix(.11, 1., 1.0) * vec3(1.8 * t3 * t3 * t3, 1.4 * t3 * t3, t3) * fade;

        s += COSMOS_STEPSIZE;
        s3 += COSMOS_STEPSIZE;
    }

    v = mix(vec3(length(v)), v, COSMOS_SATURATION);

    vec4 forCol2 = vec4(v*.01, 1.);
    backCol2 *= COSMOS_CLOUD;
    backCol2.b *= 1.8;
    backCol2.r *= 0.05;

    backCol2.b = 0.5*mix(backCol2.g, backCol2.b, 0.8);
    backCol2.g = 0.0;
    backCol2.bg = mix(backCol2.gb, backCol2.bg, 0.5*(cos(t*0.01) + 1.0));

    vec4 result = forCol2 + vec4(backCol2, 1.0);
    result.rgb = mix(result.rgb, result.rgb * HandColor.rgb * 2.0, 0.5);

    return result.rgb;
}


#define NEBULA_NUM_OCTAVES 5

float nebula_random(vec2 pos) {
    return fract(sin(dot(pos.xy, vec2(13.9898, 78.233))) * 43758.5453123);
}

float nebula_noise(vec2 pos) {
    vec2 i = floor(pos);
    vec2 f = fract(pos);
    float a = nebula_random(i + vec2(0.0, 0.0));
    float b = nebula_random(i + vec2(1.0, 0.0));
    float c = nebula_random(i + vec2(0.0, 1.0));
    float d = nebula_random(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float nebula_fbm(vec2 pos, float t) {
    float v = 0.0;
    float a = 0.5;
    vec2 shift = vec2(100.0);
    mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.5));
    for (int i = 0; i < NEBULA_NUM_OCTAVES; i++) {
        float dir = mod(float(i), 2.0) > 0.5 ? 1.0 : -1.0;
        v += a * nebula_noise(pos + dir * t * 0.3);
        pos = rot * pos * 2.0 + shift;
        a *= 0.5;
    }
    return v;
}

vec3 nebula_effect(vec2 uv, float t) {
    vec2 p = (uv * 2.0 - 1.0) * 3.0;

    vec2 q = vec2(0.0);
    q.x = nebula_fbm(p + vec2(0.0, 0.0), t * 0.8);
    q.y = nebula_fbm(p + vec2(1.0, 1.0), t * 0.6);

    vec2 r = vec2(0.0);
    r.x = nebula_fbm(p + 1.0 * q + vec2(1.7, 1.2), t * 0.7);
    r.y = nebula_fbm(p + 1.0 * q + vec2(8.3, 2.8), t * 0.9);

    float f = nebula_fbm(p + r, t);

    vec3 color1 = HandColor.rgb * 1.4;
    vec3 color2 = HandColor.rgb * 0.5;
    vec3 color3 = vec3(HandColor.r * 0.4, HandColor.g * 0.3, HandColor.b * 1.4);

    vec3 color = mix(color1, color2, clamp((f * f) * 4.0, 0.0, 1.0));
    color = mix(color, color1, clamp(length(q), 0.0, 1.0));
    color = mix(color, color3, clamp(length(r.x), 0.0, 1.0));

    color = (f * f * f + 0.6 * f * f + 0.5 * f) * color;
    color = clamp(color * 1.8, 0.0, 1.0);

    return color;
}


const vec3 cobweb_eps = vec3(0.001, 0., 0.);
const int cobweb_iter = 64;
const float cobweb_sq = 0.70710678;

float cobweb_c(vec3 p) {
    vec3 q = abs(mod(p + vec3(cos(p.z*0.5), cos(p.x*0.5), cos(p.y*0.5)), 2.0) - 1.0);
    float a = q.x + q.y + q.z - min(min(q.x, q.y), q.z) - max(max(q.x, q.y), q.z);
    q = vec3(p.x+p.y, p.y+p.z, p.z+p.x) * cobweb_sq;
    q = abs(mod(q, 2.0) - 1.0);
    float b = q.x + q.y + q.z - min(min(q.x, q.y), q.z) - max(max(q.x, q.y), q.z);
    return min(a, b);
}

vec3 cobweb_n(vec3 p) {
    float o = cobweb_c(p);
    return normalize(o - vec3(cobweb_c(p - cobweb_eps), cobweb_c(p - cobweb_eps.zxy), cobweb_c(p - cobweb_eps.yzx)));
}

vec3 cobweb_effect(vec2 uv, float t) {
    float aspect = Resolution.x / Resolution.y;
    vec2 p = uv * 2. - 1.;
    vec2 m = vec2(sin(t * 0.3) * 0.3, cos(t * 0.2) * 0.3);
    p.x *= aspect;
    m.x *= aspect;

    vec3 o = vec3(0., 0., t * 0.5);
    vec3 s = vec3(m, 0.);
    vec3 b = vec3(0., 0., 0.);
    vec3 d = vec3(p, 1.) / 32.;
    vec3 tv = vec3(.5);
    vec3 a;

    for (int i = 0; i < cobweb_iter; ++i) {
        float h = cobweb_c(b + s + o);
        b += h * 10.0 * d;
        tv += h;
    }
    tv /= float(cobweb_iter);
    a = cobweb_n(b + s + o);
    float x = dot(a, tv);

    vec3 diffuse = HandColor.rgb;
    tv = (tv + pow(x, 4.)) * (1. - tv * .01) * diffuse;
    tv *= b.z * 0.125;

    vec2 vig = p * 0.43;
    vig.y *= aspect;
    float vig_amount = 1.0 - length(vig) * 0.5;

    vec3 color = tv * 3.0 * vig_amount;
    return color;
}


float plasma_wave(vec2 uv, float t) {
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

vec3 plasma_effect(vec2 uv, float t) {
    float pt = t * 0.8;
    float p = plasma_wave(uv, pt);

    vec3 base = HandColor.rgb;

    float baseHue = 0.0;
    float baseSat = 0.0;
    float baseVal = max(max(base.r, base.g), base.b);

    if (baseVal > 0.0) {
        float minVal = min(min(base.r, base.g), base.b);
        baseSat = (baseVal - minVal) / baseVal;

        if (base.r >= base.g && base.r >= base.b) {
            baseHue = (base.g - base.b) / (baseVal - minVal + 0.001);
        } else if (base.g >= base.r && base.g >= base.b) {
            baseHue = 2.0 + (base.b - base.r) / (baseVal - minVal + 0.001);
        } else {
            baseHue = 4.0 + (base.r - base.g) / (baseVal - minVal + 0.001);
        }
        baseHue /= 6.0;
        if (baseHue < 0.0) baseHue += 1.0;
    }

    float flowHue = baseHue + p * 0.15 + sin(t * 0.3) * 0.05;
    float flowSat = 0.7 + sin(p * 3.14159) * 0.3;
    float flowVal = 0.8 + p * 0.4;

    vec3 color = hsv2rgb(vec3(flowHue, flowSat, flowVal));

    float glow = pow(abs(sin(p * 3.14159 * 2.0)), 2.0) * 0.5;
    color += HandColor.rgb * glow;

    float pulse = 0.8 + sin(t * 2.0 + p * 5.0) * 0.2;
    color *= pulse;

    color = clamp(color, 0.0, 1.0);
    return color;
}


#define GLASS_SPREAD 22.0
#define GLASS_REFR_BASE 0.006
#define GLASS_REFR_EDGE 0.085
#define GLASS_CHROMATIC 1.22
#define GLASS_FROST 8.0

float glass_height(vec2 uv, vec2 texel) {
    float h = texture(Sampler0, uv).a;
    for (int i = 0; i < 8; i++) {
        float ang = 0.785398 * float(i);
        vec2 offs = vec2(cos(ang), sin(ang)) * texel * GLASS_SPREAD;
        h += texture(Sampler0, uv + offs).a;
        h += texture(Sampler0, uv + offs * 0.5).a;
    }
    return h / 17.0;
}

vec4 liquid_glass_effect(vec2 uv, float t) {
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));

    float hC = glass_height(uv, texel);
    float mask = smoothstep(0.3, 0.52, hC);
    if (mask <= 0.004) {
        return vec4(0.0);
    }

    vec2 gs = texel * 4.0;
    float hx = glass_height(uv + vec2(gs.x, 0.0), texel) - glass_height(uv - vec2(gs.x, 0.0), texel);
    float hy = glass_height(uv + vec2(0.0, gs.y), texel) - glass_height(uv - vec2(0.0, gs.y), texel);
    vec3 normal = normalize(vec3(-hx, -hy, gs.x * 12.0));

    float rim = 1.0 - smoothstep(0.4, 0.95, hC);
    vec2 refr = normal.xy * (GLASS_REFR_BASE + GLASS_REFR_EDGE * rim);

    vec2 ripple = vec2(
        sin(uv.y * 90.0 + t * 2.2) + 0.6 * sin(uv.x * 55.0 - t * 1.7),
        cos(uv.x * 85.0 + t * 2.0) + 0.6 * cos(uv.y * 50.0 - t * 1.4)
    ) * 0.0018;
    refr += ripple * mask;

    vec3 scene;
    scene.r = texture(Sampler1, uv + refr).r;
    scene.g = texture(Sampler1, uv + refr * GLASS_CHROMATIC).g;
    scene.b = texture(Sampler1, uv + refr * GLASS_CHROMATIC * GLASS_CHROMATIC).b;

    vec3 frost = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        float ang = 0.785398 * float(i) + 0.39;
        vec2 dir = vec2(cos(ang), sin(ang)) * texel * GLASS_FROST;
        frost += texture(Sampler1, uv + refr + dir).rgb;
        frost += texture(Sampler1, uv + refr + dir * 0.45).rgb;
    }
    scene = mix(scene, frost * 0.0625, 0.6);

    float luma = dot(scene, vec3(0.299, 0.587, 0.114));
    scene = mix(vec3(luma), scene, 1.25);
    scene = scene * 1.08 + 0.02;

    vec3 tint = HandColor.rgb;
    scene = mix(scene, scene * (0.75 + 0.5 * tint), 0.15);

    vec3 lightDir = normalize(vec3(0.4 * sin(t * 0.5), 0.7 + 0.1 * cos(t * 0.35), 0.55));
    float spec = pow(max(dot(reflect(-lightDir, normal), vec3(0.0, 0.0, 1.0)), 0.0), 60.0);

    float edgeLine = smoothstep(0.36, 0.46, hC) * (1.0 - smoothstep(0.5, 0.68, hC));

    scene += spec * 0.7;
    scene += edgeLine * 0.22 * mix(vec3(1.0), tint, 0.4);
    scene += pow(rim, 3.0) * 0.1;

    float alpha = mask * HandColor.a;
    return vec4(clamp(scene, 0.0, 1.0), alpha);
}


float matrix_hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float matrix_glyph(vec2 cellUv, vec2 id, float t) {
    float switchSeed = floor(t * (1.5 + matrix_hash(id) * 5.0));
    vec2 g = floor(cellUv * vec2(4.0, 5.0));
    float bit = step(0.4, matrix_hash(g * 0.31 + id * 3.7 + switchSeed * vec2(11.3, 17.9)));
    vec2 f = fract(cellUv * vec2(4.0, 5.0));
    vec2 border = step(vec2(0.15), f) * step(f, vec2(0.85));
    vec2 margin = step(vec2(0.08), cellUv) * step(cellUv, vec2(0.92));
    return bit * border.x * border.y * margin.x * margin.y;
}

vec3 matrix_effect(vec2 uv, float t) {
    float aspect = Resolution.x / Resolution.y;
    vec2 p = vec2(uv.x * aspect, uv.y);

    vec3 tint = HandColor.rgb;
    vec3 color = tint * 0.12;

    for (int layer = 0; layer < 3; layer++) {
        float fl = float(layer);
        float scale = 26.0 + fl * 14.0;
        vec2 q = p * vec2(scale * 0.55, scale) + vec2(fl * 37.31, fl * 11.7);

        float colId = floor(q.x);
        float colSpeed = 2.5 + matrix_hash(vec2(colId, fl)) * 6.0;
        float colOffset = matrix_hash(vec2(colId, fl + 5.0)) * 100.0;
        q.y += t * colSpeed + colOffset;

        vec2 id = floor(q);
        vec2 cellUv = fract(q);

        float trailLen = 10.0 + floor(matrix_hash(vec2(colId, fl + 9.0)) * 14.0);
        float phase = fract(id.y / trailLen);
        float fade = pow(1.0 - phase, 1.6);
        float head = step(phase, 1.0 / trailLen);

        float glyph = matrix_glyph(cellUv, id, t);
        float layerFade = 1.0 - fl * 0.3;

        vec3 streak = mix(tint * 2.2, vec3(1.2), head * 0.85);
        color += streak * glyph * fade * layerFade;
    }

    return clamp(color, 0.0, 1.6);
}


#define CB_EMISSION_STRENGTH 2.2
#define CB_SHIMMER_SPEED 0.4
#define CB_SHIMMER_STRENGTH 0.12

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.x) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 cosmicblast_effect(vec2 uv, float t) {
    float aspect = Resolution.x / Resolution.y;
    vec2 p = vec2(uv.x * aspect, uv.y);

    float n1 = nebula_fbm(p * 3.5 + vec2(0.1, 0.2), t * 0.033);
    float n2 = nebula_fbm(p * 5.0 + vec2(t * 0.01, 0.0), t * 0.05);
    float n3 = nebula_fbm(p * 8.0 - vec2(0.0, t * 0.008), t * 0.04);

    vec3 baseHsv = rgb2hsv(HandColor.rgb);
    float hue = baseHsv.x;
    float sat = min(baseHsv.y * 1.3, 1.0);
    float val = max(baseHsv.z, 0.7);

    vec3 nebulaA = hsv2rgb(vec3(hue, sat, val * 0.8));
    vec3 nebulaB = hsv2rgb(vec3(fract(hue + 0.16), sat, val * 0.9));
    vec3 nebulaC = hsv2rgb(vec3(fract(hue + 0.87), sat * 0.9, val));

    vec3 albedo = mix(nebulaA, nebulaB, n1);
    albedo = mix(albedo, nebulaC, n2 * 0.5);
    albedo = mix(albedo, albedo * 0.15 + vec3(0.02), n3 * 0.4);

    float starNoise = matrix_hash(floor(p * 180.0));
    float star = step(0.97, starNoise) * smoothstep(0.97, 1.0, starNoise);
    float twinkle = 0.6 + 0.4 * sin(t * 3.0 + starNoise * 40.0);
    albedo += star * twinkle * vec3(1.0, 0.95, 0.9) * 1.5;

    float shimmerNoise = nebula_noise(p * 12.0 + vec2(t * CB_SHIMMER_SPEED));
    vec3 shimmerColor = mix(nebulaB, nebulaC, shimmerNoise);
    float shimmerBand = 0.5 + 0.5 * sin((p.x + p.y) * 9.0 - t * 1.3);
    albedo += shimmerBand * shimmerColor * CB_SHIMMER_STRENGTH;

    float emissionMask = smoothstep(0.8, 1.15, n1 * n2 * 2.0);
    vec3 glowColor = mix(nebulaB, vec3(1.0), 0.45);
    vec3 emission = emissionMask * glowColor * CB_EMISSION_STRENGTH * 0.5;

    float planetMask = step(0.86, nebula_fbm(p * 2.0 + vec2(3.1, 1.7), 0.0));
    emission += planetMask * nebulaB * 0.9;

    for (int i = 0; i < 2; i++) {
        float fi = float(i);
        vec2 q = p * 3.2 + vec2(fi * 0.5, fi * 0.37);
        vec2 id = floor(q);
        vec2 cuv = fract(q) - 0.5;
        cuv += (vec2(matrix_hash(id + fi), matrix_hash(id + fi + 4.2)) - 0.5) * 0.4;

        float phase = fract(t * 0.55 + matrix_hash(id * 1.7 + fi * 9.1));
        float seed = matrix_hash(id + fi * 23.0) * 43.0 + floor(t * 0.55 + matrix_hash(id * 1.7 + fi * 9.1)) * 7.0;

        float r = length(cuv);
        float ang = atan(cuv.y, cuv.x);

        float ringR = phase * 0.45;
        float ring = exp(-pow((r - ringR) * 16.0, 2.0));
        float rays = 0.5 + 0.5 * sin(ang * 14.0 + seed);
        float sparks = 0.6 + 0.4 * sin(ang * 43.0 - seed * 3.0);
        float fade = pow(1.0 - phase, 1.6);
        float core = exp(-r * 12.0) * max(0.0, 1.0 - phase * 3.0) * 2.5;

        float alive = step(matrix_hash(id * 3.3 + fi + floor(t * 0.55)), 0.65);
        vec3 blastCol = mix(glowColor, mix(nebulaB, nebulaC, matrix_hash(vec2(seed, 5.0))), 0.55);
        emission += (ring * rays * sparks * fade * 2.4 + core) * blastCol * alive;
    }

    vec3 color = albedo * (0.75 + 0.25 * shimmerBand) + emission;
    color = color / (color + vec3(0.85));
    color *= 1.6;

    return clamp(color, 0.0, 1.5);
}


float an_star(vec2 p, float t) {
    vec2 cell = floor(p * 26.0);
    float n = matrix_hash(cell);
    float rare = step(0.975, n);
    vec2 f = fract(p * 26.0) - 0.5;
    vec2 jitter = vec2(matrix_hash(cell + 7.3), matrix_hash(cell + 3.1)) - 0.5;
    float spot = smoothstep(0.24, 0.05, length(f - jitter * 0.5));
    return rare * spot * (0.55 + 0.45 * sin(t * 3.0 + n * 30.0));
}

vec3 aliennebula_effect(vec2 uv, float t) {
    float aspect = Resolution.x / Resolution.y;
    vec2 p = vec2(uv.x * aspect, uv.y) * 3.5;

    vec2 q = vec2(nebula_fbm(p, t * 0.15), nebula_fbm(p + vec2(5.2, 1.3), t * 0.12));
    float n = nebula_fbm(p + q * 2.0, t * 0.1);

    vec3 baseHsv = rgb2hsv(HandColor.rgb);
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

    color += c3 * smoothstep(0.62, 0.9, n) * 1.2;
    color += vec3(0.9, 0.95, 1.0) * an_star(p, t);

    float pulse = 0.9 + 0.1 * sin(t * 0.9 + n * 6.0);
    return clamp(color * pulse, 0.0, 1.5);
}


// ===== Ported Sirius hand shaders (GLSL math only, no external code) =====
// Coordinate convention: pass uv = TexCoord (0..1). fragCoord = uv * Resolution.
// Original uniforms mapped: time->Time, resolution->Resolution, speed->vec2(1.0),
// shift->1.6, mouse->center. Effects return rgb; hand alpha/opacity applied in main.

float sir_field_a(vec3 p, float t) {
    float strength = 7.0 + 0.03 * log(1.0e-6 + fract(sin(t) * 4373.11));
    float accum = 0.0, prev = 0.0, tw = 0.0;
    for (int i = 0; i < 6; ++i) {
        float mag = dot(p, p);
        p = abs(p) / mag + vec3(-0.5, -0.8 + 0.1 * sin(t * 0.2 + 2.0), -1.1 + 0.3 * cos(t * 0.15));
        float w = exp(-float(i) / 7.0);
        accum += w * exp(-strength * pow(abs(mag - prev), 2.3));
        tw += w; prev = mag;
    }
    return max(0.0, 5.0 * accum / tw - 0.7);
}

float sir_field_b(vec3 p, float t) {
    float strength = 7.0 + 0.03 * log(1.0e-6 + fract(sin(t) * 4373.11));
    float accum = 0.0, prev = 0.0, tw = 0.0;
    for (int i = 0; i < 6; ++i) {
        float mag = dot(p, p);
        p = abs(p) / mag + vec3(-0.5, -0.8 + 0.1 * sin(t * 0.7 + 2.0), -1.1 + 0.3 * cos(t * 0.3));
        float w = exp(-float(i) / 7.0);
        accum += w * exp(-strength * pow(abs(mag - prev), 2.3));
        tw += w; prev = mag;
    }
    return max(0.0, 5.0 * accum / tw - 0.7);
}

float sir_field_yip(vec3 p, float t) {
    float strength = 7.0 + 0.09 * log(1.0e-6 + fract(sin(t) * 373.11));
    float accum = 0.0, prev = 0.0, tw = 0.0;
    for (int i = 0; i < 7; ++i) {
        float mag = dot(p, p);
        p = abs(p) / mag + vec3(-0.5, -0.8 + 0.1 * sin(-t * 0.1 + 2.0), -1.1 + 0.3 * cos(t * 0.3));
        float w = exp(-float(i) / 7.0);
        accum += w * exp(-strength * pow(abs(mag - prev), 2.3));
        tw += w; prev = mag;
    }
    return max(0.0, 5.0 * accum / tw - 0.7);
}

float sir_field_hidef(vec3 p) {
    float strength = 7.0 + 0.03 * log(1.0e-6 + fract(sin(0.0) * 4373.11));
    float accum = 0.0, prev = 0.0, tw = 0.0;
    for (int i = 0; i < 9; ++i) {
        float mag = dot(p, p);
        p = abs(p) / mag + vec3(-0.5, -0.8, -1.1);
        float w = exp(-float(i) / 7.0);
        accum += w * exp(-strength * pow(abs(mag - prev), 2.3));
        tw += w; prev = mag;
    }
    return max(0.0, 5.0 * accum / tw - 0.7);
}

// aqua / purple: tiled caustic
vec3 sir_caustic(vec2 uv, float t, vec3 add) {
    float letime = t * 0.5 + 23.0;
    vec2 p = mod(uv * 6.28318530718, 6.28318530718) - 250.0;
    vec2 i = p;
    float c = 1.0;
    float inten = 0.005;
    for (int n = 0; n < 5; n++) {
        float tt = letime * (1.0 - (3.5 / float(n + 1)));
        i = p + vec2(cos(tt - i.x) + sin(tt + i.y), sin(tt - i.y) + cos(tt + i.x));
        c += 1.0 / length(vec2(p.x / (sin(i.x + tt) / inten), p.y / (cos(i.y + tt) / inten)));
    }
    c /= 5.0;
    c = 1.17 - pow(c, 1.4);
    vec3 colour = vec3(pow(abs(c), 8.0));
    return clamp(colour + add, 0.0, 1.0);
}

vec3 sir_golden(vec2 tc, float t) {
    vec2 uv = tc - 0.5;
    uv.y *= Resolution.y / Resolution.x;
    vec3 dir = vec3(uv * 2.0, 1.0);
    float a1 = 5.0;
    mat2 rot1 = mat2(cos(a1), sin(a1), -sin(a1), cos(a1));
    dir.xz *= rot1; dir.xy *= rot1;
    vec3 from = vec3(0.0);
    from += vec3(0.0, 0.1 * t, 0.0);
    from.xz *= rot1; from.xy *= rot1;
    float s = 0.1, fade = 0.05;
    vec3 v = vec3(0.05);
    for (int r = 0; r < 12; r++) {
        vec3 p = from + s * dir * 1.5;
        p = abs(vec3(0.40) - mod(p, vec3(0.40 * 2.0)));
        p.x += float(r * r) * 0.01;
        p.y += float(r) * 0.02;
        float pa, a = pa = 0.0;
        for (int i = 0; i < 15; i++) {
            p = abs(p) / dot(p, p) - 0.330;
            a += abs(length(p) - pa * 0.1);
            pa = length(p);
        }
        float dm = max(0.1, 0.40 - a * a * 0.9);
        a *= a * a * 2.0;
        if (r > 3) fade *= 1.0 - dm;
        v += vec3(dm, dm * 0.0, 0.6);
        v += fade;
        v += vec3(s, s * s, s * s * s * s * s) * a * 0.0019 * fade;
        fade *= 0.99;
        s += 0.10;
    }
    v = mix(vec3(length(v)), v, 1.60);
    return v * 0.01;
}

vec3 sir_flow(vec2 tc, float t) {
    vec2 fragCoord = tc * Resolution;
    vec2 uv2 = 2.0 * fragCoord / Resolution.xy - 1.0;
    vec2 uv = uv2 * Resolution.xy / max(Resolution.x, Resolution.y);
    float time2 = t * 1.9;
    float speed = 0.005 * cos(time2 * 0.02 + 3.1415926 / 4.0);
    float formuparam = 0.89;
    float a_xz = 0.9, a_yz = -0.6, a_xy = 0.9 + t * 0.04;
    mat2 rot_xz = mat2(cos(a_xz), sin(a_xz), -sin(a_xz), cos(a_xz));
    mat2 rot_yz = mat2(cos(a_yz), sin(a_yz), -sin(a_yz), cos(a_yz));
    mat2 rot_xy = mat2(cos(a_xy), sin(a_xy), -sin(a_xy), cos(a_xy));
    float v2 = 1.0;
    vec3 dir = vec3(uv * 3.900, 1.0);
    vec3 from = vec3(0.0);
    from.x -= 0.5 * (-0.5); from.y -= 0.5 * (-0.5);
    vec3 forward = vec3(0.0, 0.0, 1.0);
    from.x += 1.1 * cos(0.01 * t) + 0.001 * t;
    from.y += 1.1 * sin(0.01 * t) + 0.001 * t;
    from.z += 0.003 * t;
    dir.xy *= rot_xy; forward.xy *= rot_xy;
    dir.xz *= rot_xz; forward.xz *= rot_xz;
    dir.yz *= rot_yz; forward.yz *= rot_yz;
    from.xy *= -rot_xy; from.xz *= rot_xz; from.yz *= rot_yz;
    float zooom = (time2 - 3311.0) * speed;
    from += forward * zooom;
    float sampleShift = mod(zooom, 0.190);
    float zoffset = -sampleShift;
    sampleShift /= 0.190;
    float s = 0.24, s3 = 0.24 + 0.190 / 2.0;
    vec3 v = vec3(0.0); float t3 = 0.0; vec3 backCol2 = vec3(0.0);
    for (int r = 0; r < 10; r++) {
        vec3 p2 = from + (s + zoffset) * dir;
        vec3 p3 = (from + (s3 + zoffset) * dir) * (1.9 / 3.900);
        p2 = abs(vec3(0.450) - mod(p2, vec3(0.450 * 2.0)));
        p3 = abs(vec3(0.450) - mod(p3, vec3(0.450 * 2.0)));
        t3 = sir_field_a(p3, t);
        float pa, a = pa = 0.0;
        for (int i = 0; i < 4; i++) {
            p2 = abs(p2) / dot(p2, p2) - formuparam;
            float D = abs(length(p2) - pa);
            if (i > 2) { a += i > 7 ? min(12.0, D) : D; }
            pa = length(p2);
        }
        a *= a * a;
        float s1 = s + zoffset;
        float fade = pow(0.560, max(0.0, float(r) - sampleShift));
        v += fade;
        if (r == 0) fade *= (1.0 - sampleShift);
        if (r == 9) fade *= sampleShift;
        v += vec3(s1, s1 * s1, s1 * s1 * s1 * s1) * a * 0.2 * fade;
        backCol2 += mix(0.4, 1.0, v2) * vec3(0.20 * t3 * t3 * t3, 0.4 * t3 * t3, t3 * 0.7) * fade;
        s += 0.190; s3 += 0.190;
    }
    v = mix(vec3(length(v)), v, 0.400);
    vec3 forCol2 = v * 0.01;
    backCol2 *= 0.2;
    backCol2.b *= 1.8; backCol2.r *= 0.05;
    return forCol2 + backCol2;
}

vec3 sir_smoky(vec2 tc, float t) {
    vec2 fragCoord = tc * Resolution;
    vec2 uv2 = 2.0 * fragCoord / Resolution.xy - 1.0;
    vec2 uv = uv2 * Resolution.xy / max(Resolution.x, Resolution.y);
    float time2 = t;
    float speed = 0.011 * cos(time2 * 0.02 + 3.1415926 / 4.0);
    float formuparam = 0.89;
    float a_xz = 0.9, a_yz = -0.6, a_xy = 0.9 + t * 0.04;
    mat2 rot_xz = mat2(cos(a_xz), sin(a_xz), -sin(a_xz), cos(a_xz));
    mat2 rot_yz = mat2(cos(a_yz), sin(a_yz), -sin(a_yz), cos(a_yz));
    mat2 rot_xy = mat2(cos(a_xy), sin(a_xy), -sin(a_xy), cos(a_xy));
    float v2 = 1.0;
    vec3 dir = vec3(uv * 3.900, 1.0);
    vec3 from = vec3(0.0);
    from.x -= 5.0 * (0.0 - 0.5); from.y -= 5.0 * (0.0 - 0.5);
    vec3 forward = vec3(0.0, 0.0, 1.0);
    from.x += 1.1 * cos(0.01 * t) + 0.001 * t;
    from.y += 1.1 * sin(0.01 * t) + 0.001 * t;
    from.z += 0.003 * t;
    dir.xy *= rot_xy; forward.xy *= rot_xy;
    dir.xz *= rot_xz; forward.xz *= rot_xz;
    dir.yz *= rot_yz; forward.yz *= rot_yz;
    from.xy *= -rot_xy; from.xz *= rot_xz; from.yz *= rot_yz;
    float zooom = (time2 - 3311.0) * speed;
    from += forward * zooom;
    float sampleShift = mod(zooom, 0.190);
    float zoffset = -sampleShift;
    sampleShift /= 0.190;
    float s = 0.24, s3 = 0.24 + 0.190 / 2.0;
    vec3 v = vec3(0.0); float t3 = 0.0; vec3 backCol2 = vec3(0.0);
    for (int r = 0; r < 10; r++) {
        vec3 p2 = from + (s + zoffset) * dir;
        vec3 p3 = (from + (s3 + zoffset) * dir) * (1.9 / 3.900);
        p2 = abs(vec3(0.450) - mod(p2, vec3(0.450 * 2.0)));
        p3 = abs(vec3(0.450) - mod(p3, vec3(0.450 * 2.0)));
        t3 = sir_field_a(p3, t);
        float pa, a = pa = 0.0;
        for (int i = 0; i < 4; i++) {
            p2 = abs(p2) / dot(p2, p2) - formuparam;
            float D = abs(length(p2) - pa);
            if (i > 2) { a += i > 7 ? min(12.0, D) : D; }
            pa = length(p2);
        }
        a *= a * a;
        float s1 = s + zoffset;
        float fade = pow(0.560, max(0.0, float(r) - sampleShift));
        v += fade;
        if (r == 0) fade *= (1.0 - sampleShift);
        if (r == 9) fade *= sampleShift;
        v += vec3(s1, s1 * s1, s1 * s1 * s1 * s1) * a * 0.2 * fade;
        backCol2 += mix(0.4, 1.0, v2) * vec3(0.20 * t3 * t3 * t3, 0.4 * t3 * t3, t3 * 0.7) * fade;
        s += 0.190; s3 += 0.190;
    }
    v = mix(vec3(length(v)), v, 0.400);
    vec3 forCol2 = v * 0.01;
    backCol2 *= 0.2;
    return forCol2 + backCol2;
}

vec3 sir_galaxy(vec2 tc, float t) {
    vec2 fragCoord = tc * Resolution;
    vec2 uv2 = 2.0 * fragCoord / vec2(512.0) - 1.0;
    vec2 uv = uv2;
    float time2 = t;
    float speed = -0.004 * cos(time2 * 0.02 + 3.1415926 / 4.0);
    float formuparam = 0.5;
    float a_xz = 0.9, a_yz = -0.6, a_xy = 0.9 + t * 0.04;
    mat2 rot_xz = mat2(cos(a_xz), sin(a_xz), -sin(a_xz), cos(a_xz));
    mat2 rot_yz = mat2(cos(a_yz), sin(a_yz), -sin(a_yz), cos(a_yz));
    mat2 rot_xy = mat2(cos(a_xy), sin(a_xy), -sin(a_xy), cos(a_xy));
    float v2 = 1.0;
    vec3 dir = vec3(uv * 0.900, 1.0);
    vec3 from = vec3(0.0);
    vec3 forward = vec3(0.0, 0.0, 1.0);
    from.x += 0.18 * 6.0 * cos(0.3 * t) + 0.01 * t;
    from.y += 0.18 * 6.0 * sin(0.2 * t) + 0.01 * t;
    dir.x -= 0.4 * (0.18 * 7.0 * cos(0.3 * t) + 0.01 * t);
    dir.y += 0.2 * (0.18 * 7.0 * sin(0.2 * t) + 0.01 * t);
    from.z += 0.009 * t;
    dir.xy *= rot_xy; forward.xy *= rot_xy;
    dir.xz *= rot_xz; forward.xz *= rot_xz;
    dir.yz *= rot_yz; forward.yz *= rot_yz;
    from.xy *= -rot_xy; from.xz *= rot_xz; from.yz *= rot_yz;
    float zooom = (time2 - 3311.0) * speed;
    from += forward * zooom;
    float sampleShift = mod(zooom, 0.290);
    float zoffset = -sampleShift;
    sampleShift /= 0.290;
    float s = 0.24, s3 = 0.24 + 0.290 / 2.0;
    vec3 v = vec3(0.0); float t3 = 0.0; vec3 backCol2 = vec3(0.0);
    for (int r = 0; r < 8; r++) {
        vec3 p2 = from + (s + zoffset) * dir;
        vec3 p3 = from + (s3 + zoffset) * dir;
        p2 = abs(vec3(0.95) - mod(p2, vec3(0.95 * 2.0)));
        p3 = abs(vec3(0.95) - mod(p3, vec3(0.95 * 2.0)));
        t3 = sir_field_b(p3, t);
        float pa, a = pa = 0.0;
        for (int i = 0; i < 10; i++) {
            p2 = abs(p2) / dot(p2, p2) - formuparam;
            float D = abs(length(p2) - pa);
            a += i > 7 ? min(12.0, D) : D;
            pa = length(p2);
        }
        a *= a * a;
        float s1 = s + zoffset;
        float fade = pow(0.66, max(0.0, float(r) - sampleShift));
        v += fade;
        if (r == 0) fade *= (1.0 - sampleShift);
        if (r == 7) fade *= sampleShift;
        v += vec3(s1, s1 * s1, s1 * s1 * s1 * s1) * a * 0.003 * fade;
        backCol2 += mix(0.0, v2, fade) * vec3(1.8 * t3 * t3 * t3, 1.4 * t3 * t3, t3) * fade;
        s += 0.290; s3 += 0.290;
    }
    v = mix(vec3(length(v)), v, 0.800);
    vec3 forCol2 = v * 0.01;
    backCol2 *= 0.31;
    backCol2.b *= 1.8; backCol2.r *= 0.05;
    backCol2.b = 0.5 * mix(backCol2.g, backCol2.b, 0.8);
    backCol2.g = 0.0;
    return forCol2 + backCol2;
}

vec3 sir_homie(vec2 tc, float t) {
    vec2 fragCoord = tc * Resolution;
    vec2 uv2 = 2.0 * fragCoord / vec2(512.0) - 1.0;
    vec2 uv = uv2;
    float time2 = t;
    float speed = 0.01 * cos(time2 * 0.02 + 3.1415926 / 4.0);
    float formuparam = 0.79;
    float a_xz = 0.9, a_yz = -0.6, a_xy = 0.9 + t * 0.08;
    mat2 rot_xz = mat2(cos(a_xz), sin(a_xz), -sin(a_xz), cos(a_xz));
    mat2 rot_yz = mat2(cos(a_yz), sin(a_yz), -sin(a_yz), cos(a_yz));
    mat2 rot_xy = mat2(cos(a_xy), sin(a_xy), -sin(a_xy), cos(a_xy));
    float v2 = 1.0;
    vec3 dir = vec3(uv * 0.900, 1.0);
    vec3 from = vec3(0.0);
    vec2 mo = vec2(sin(t) / 48.0, cos(t) / 48.0);
    from.x -= 5.0 * (mo.x - 0.5); from.y -= 5.0 * (mo.y - 0.5);
    vec3 forward = vec3(0.0, 0.0, 1.0);
    from.x += 1.8 * cos(0.01 * t) + 0.001 * t;
    from.y += 1.8 * sin(0.01 * t) + 0.001 * t;
    from.z += 0.003 * t;
    dir.xy *= rot_xy; forward.xy *= rot_xy;
    dir.xz *= rot_xz; forward.xz *= rot_xz;
    dir.yz *= rot_yz; forward.yz *= rot_yz;
    from.xy *= -rot_xy; from.xz *= rot_xz; from.yz *= rot_yz;
    float zooom = (time2 - 3311.0) * speed;
    from += forward * zooom;
    float sampleShift = mod(zooom, 0.390);
    float zoffset = -sampleShift;
    sampleShift /= 0.390;
    float s = 0.24, s3 = 0.24 + 0.390 / 2.0;
    vec3 v = vec3(0.0); float t3 = 0.0; vec3 backCol2 = vec3(0.0);
    for (int r = 0; r < 5; r++) {
        vec3 p2 = from + (s + zoffset) * dir;
        vec3 p3 = from + (s3 + zoffset) * dir;
        p2 = abs(vec3(0.85) - mod(p2, vec3(0.85 * 2.0)));
        p3 = abs(vec3(0.85) - mod(p3, vec3(0.85 * 2.0)));
        t3 = sir_field_b(p3, t);
        float pa, a = pa = 0.0;
        for (int i = 0; i < 14; i++) {
            p2 = abs(p2) / dot(p2, p2) - formuparam;
            float D = abs(length(p2) - pa);
            a += i > 7 ? min(12.0, D) : D;
            pa = length(p2);
        }
        a *= a * a;
        float s1 = s + zoffset;
        float fade = pow(0.560, max(0.0, float(r) - sampleShift));
        v += fade;
        if (r == 0) fade *= (1.0 - sampleShift);
        if (r == 4) fade *= sampleShift;
        v += vec3(s1, s1 * s1, s1 * s1 * s1 * s1) * a * 0.003 * fade;
        backCol2 += mix(0.4, 1.0, v2) * vec3(1.8 * t3 * t3 * t3, 1.4 * t3 * t3, t3) * fade;
        s += 0.390; s3 += 0.390;
    }
    v = mix(vec3(length(v)), v, 0.800);
    vec3 forCol2 = v * 0.01;
    backCol2 *= 0.11;
    backCol2.b *= 1.8; backCol2.r *= 0.05;
    backCol2.b = 0.5 * mix(backCol2.g, backCol2.b, 0.8);
    backCol2.g = 0.0;
    backCol2.bg = mix(backCol2.gb, backCol2.bg, 0.5 * (cos(t * 0.01) + 1.0));
    return forCol2 + backCol2;
}

vec3 sir_yippie(vec2 tc, float t) {
    vec2 fragCoord = tc * Resolution;
    vec2 uv2 = 4.0 * fragCoord / vec2(1024.0) - 1.0;
    vec2 uv = uv2;
    float time2 = t;
    float speed = 0.00005 * cos(time2 * 0.0002 + 3.1415926 / 2.0);
    float formuparam = 0.79;
    float a_xz = 0.9, a_yz = -0.9, a_xy = 0.1 + t * 0.003;
    mat2 rot_xz = mat2(cos(a_xz), sin(a_xz), -sin(a_xz), cos(a_xz));
    mat2 rot_yz = mat2(cos(a_yz), sin(a_yz), -sin(a_yz), cos(a_yz));
    mat2 rot_xy = mat2(cos(a_xy), sin(a_xy), -sin(a_xy), cos(a_xy));
    float v2 = 1.0;
    vec3 dir = vec3(uv * 1.0, 1.0);
    vec3 from = vec3(0.0);
    from.x -= 2.0 * (0.0 - 0.5); from.y -= 2.0 * (0.0 - 0.5);
    vec3 forward = vec3(0.0, 0.0, 5.0);
    from.x += 1.0 * cos(0.01 * t) + 0.001 * t;
    from.y += 1.0 * sin(0.01 * t) + 0.001 * t;
    from.z += 0.003 * t;
    dir.xy *= rot_xy; forward.xy *= rot_xy;
    dir.xz *= rot_xz; forward.xz *= rot_xz;
    dir.yz *= rot_yz; forward.yz *= rot_yz;
    from.xy *= -rot_xy; from.xz *= rot_xz; from.yz *= rot_yz;
    float zooom = (time2 - 6311.0) * speed;
    from += forward * zooom;
    float sampleShift = mod(zooom, 0.290);
    float zoffset = -sampleShift;
    sampleShift /= 0.290;
    float s = 0.14, s3 = 0.14 + 0.290 / 2.0;
    vec3 v = vec3(0.0); float t3 = 0.0; vec3 backCol2 = vec3(0.0);
    for (int r = 0; r < 7; r++) {
        vec3 p2 = from + (s + zoffset) * dir;
        vec3 p3 = from + (s3 + zoffset) * dir;
        p2 = abs(vec3(0.85) - mod(p2, vec3(0.85 * 2.0)));
        p3 = abs(vec3(0.85) - mod(p3, vec3(0.85 * 2.0)));
        t3 = sir_field_yip(p3, t);
        float pa, a = pa = 0.0;
        for (int i = 0; i < 12; i++) {
            p2 = abs(p2) / dot(p2, p2) - formuparam;
            float D = abs(length(p2) - pa);
            a += i > 6 ? min(12.0, D) : D;
            pa = length(p2);
        }
        a *= a * a;
        float s1 = s + zoffset;
        float fade = pow(0.560, max(0.0, float(r) - sampleShift));
        v += fade;
        if (r == 0) fade *= (1.3 - sampleShift);
        if (r == 6) fade *= sampleShift;
        v += vec3(s1, s1 * s1, s1 * s1 * s1 * s1) * a * 0.0015 * fade;
        backCol2 += mix(0.5, 1.0, v2) * vec3(0.8 * t3 * t3 * t3, 1.4 * t3 * t3, t3) * fade;
        s += 0.290; s3 += 0.290;
    }
    v = mix(vec3(length(v)), v, 0.90);
    vec3 forCol2 = v * 0.02;
    backCol2 *= 0.17;
    backCol2.b *= 0.9; backCol2.r *= 0.15;
    backCol2.b = 0.8 * mix(backCol2.g, backCol2.b, 0.9);
    backCol2.g = 0.02;
    backCol2.bg = mix(backCol2.gb, backCol2.bg, 0.5 * (cos(t * 0.05) + 2.0));
    return forCol2 + backCol2;
}

vec3 sir_hidef(vec2 tc, float t) {
    vec2 fragCoord = tc * Resolution;
    vec2 uv2 = fragCoord / Resolution.xy - 0.5;
    uv2.y *= Resolution.y / Resolution.x;
    vec2 uv = uv2;
    float a_xz = 0.9, a_yz = -2.6, a_xy = 0.9;
    mat2 rot_xz = mat2(cos(a_xz), sin(a_xz), -sin(a_xz), cos(a_xz));
    mat2 rot_yz = mat2(cos(a_yz), sin(a_yz), -sin(a_yz), cos(a_yz));
    mat2 rot_xy = mat2(cos(a_xy), sin(a_xy), -sin(a_xy), cos(a_xy));
    float v2 = 1.0;
    vec3 dir = vec3(uv * 1.900, 1.0);
    vec3 from = vec3(0.0);
    from.x -= 0.1 * (0.0 - 0.5); from.y -= 0.1 * (0.0 - 0.5);
    from.x += 10.0 * cos(0.004 * t);
    from.y += 10.0 * sin(0.00777777 * t);
    from.z += 0.003 * t;
    vec3 forward = vec3(0.0, 0.0, 1.0);
    dir.xy *= rot_xy; dir.xz *= rot_xz; dir.yz *= rot_yz;
    forward.xy *= rot_xy; forward.xz *= rot_xz; forward.yz *= rot_yz;
    from.xy *= rot_xy; from.xz *= rot_xz; from.yz *= rot_yz;
    float zoffset = 0.0, sampleShift = 0.0;
    float s = 0.74, s3 = 0.74 + 0.190 / 2.0;
    vec3 v = vec3(0.0); float t3 = 0.0; vec3 backCol2 = vec3(0.0);
    for (int r = 0; r < 9; r++) {
        vec3 p2 = from + (s + zoffset) * dir;
        vec3 p3 = from + (s3 + zoffset) * dir;
        p2 = abs(vec3(0.5) - mod(p2, vec3(0.5 * 2.0)));
        p3 = abs(vec3(0.5) - mod(p3, vec3(0.5 * 2.0)));
        t3 = sir_field_hidef(p3);
        float a = 0.0;
        float s1 = s + zoffset;
        float fade = pow(0.76, max(0.0, float(r) - sampleShift));
        v += fade;
        if (r == 0) fade *= (1.0 - sampleShift);
        if (r == 8) fade *= sampleShift;
        v += vec3(s1, s1 * s1, s1 * s1 * s1 * s1) * a * 0.008 * fade;
        backCol2 += mix(0.4, 1.0, v2) * vec3(1.8 * t3 * t3 * t3, 1.4 * t3 * t3, t3) * fade;
        s += 0.190; s3 += 0.190;
    }
    v = mix(vec3(length(v)), v, 0.850);
    vec3 forCol2 = v * 0.01;
    backCol2 *= 0.09;
    backCol2.b *= 1.8; backCol2.r *= 0.05;
    backCol2.b = 0.5 * mix(backCol2.g, backCol2.b, 0.8);
    backCol2.g = 0.0;
    return forCol2 + backCol2;
}

float sirGang_rand(vec2 n) { return fract(cos(dot(n, vec2(2.9898, 20.1414))) * 5.5453); }
float sirGang_noise(vec2 n) {
    const vec2 d = vec2(0.0, 1.0);
    vec2 b = floor(n), f = smoothstep(vec2(0.0), vec2(1.0), fract(n));
    return mix(mix(sirGang_rand(b), sirGang_rand(b + d.yx), f.x), mix(sirGang_rand(b + d.xy), sirGang_rand(b + d.yy), f.x), f.y);
}
float sirGang_fbm(vec2 n) {
    float total = 0.0, amplitude = 1.5;
    for (int i = 0; i < 18; i++) { total += sirGang_noise(n) * amplitude; n += n; amplitude *= 0.45; }
    return total;
}
vec3 sir_gang(vec2 tc, float t) {
    vec2 fragCoord = tc * Resolution;
    vec3 c1 = vec3(0.502, 0.1059, 0.1059);
    vec3 c2 = vec3(167.0 / 255.0, 93.0 / 255.0, 110.0 / 255.0);
    vec3 c3 = vec3(0.4902, 0.5333, 0.4902);
    vec3 c4 = vec3(0.2118, 0.3451, 0.2706);
    vec3 c5 = vec3(0.3176, 0.2549, 0.4);
    vec3 c6 = vec3(0.8, 0.3569, 0.3569);
    vec2 p = fragCoord * 5.0 / Resolution.xx;
    float q = sirGang_fbm(p - t * 0.05);
    vec2 r = vec2(sirGang_fbm(p + q + t * 1.0 - p.x - p.y), sirGang_fbm(p + q - t * 1.0));
    vec3 c = mix(c1, c2, sirGang_fbm(p + r)) + mix(c3, c4, r.x) - mix(c5, c6, r.y);
    float grad = fragCoord.y / Resolution.y;
    vec3 result = c * cos(1.6 * fragCoord.y / Resolution.y);
    result *= 1.15 - grad;
    return result;
}

float sirHoly_rand(vec2 n) { return fract(cos(dot(n, vec2(12.9898, 4.1414))) * 43758.5453); }
float sirHoly_noise(vec2 n) {
    const vec2 d = vec2(0.0, 1.0);
    vec2 b = floor(n), f = smoothstep(vec2(0.0), vec2(1.0), fract(n));
    return mix(mix(sirHoly_rand(b), sirHoly_rand(b + d.yx), f.x), mix(sirHoly_rand(b + d.xy), sirHoly_rand(b + d.yy), f.x), f.y);
}
float sirHoly_fbm(vec2 n) {
    float total = 0.0, amplitude = 1.6;
    for (int i = 0; i < 4; i++) { total += sirHoly_noise(n) * amplitude; n += n; amplitude *= 0.5; }
    return total;
}
vec3 sir_holy(vec2 tc, float t) {
    vec2 fragCoord = tc * Resolution;
    vec3 c1 = vec3(126.0 / 255.0, 0.0 / 255.0, 97.0 / 255.0);
    vec3 c2 = vec3(173.0 / 255.0, 0.0 / 255.0, 161.4 / 255.0);
    vec3 c3 = vec3(0.2, 0.0, 0.0);
    vec3 c4 = vec3(164.0 / 255.0, 1.0 / 255.0, 214.4 / 255.0);
    vec3 c5 = vec3(0.1);
    vec3 c6 = vec3(0.9);
    vec2 p = fragCoord * 8.0 / Resolution.xx;
    float q = sirHoly_fbm(p - t * 0.1);
    vec2 r = vec2(sirHoly_fbm(p + q + t * 1.0 - p.x - p.y), sirHoly_fbm(p + q - t * 1.0));
    vec3 c = mix(c1, c2, sirHoly_fbm(p + r)) + mix(c3, c4, r.y) - mix(c5, c6, r.x);
    float grad = fragCoord.y / Resolution.y;
    vec3 result = c * cos(1.6 * fragCoord.y / Resolution.y);
    result *= 1.0 - grad;
    return result;
}

float sirTeal_rand(vec2 st) { return fract(sin(dot(st.xy, vec2(12.9898, 78.233))) * 43758.5453123); }
float sirTeal_noise(vec2 st) {
    vec2 i = floor(st);
    vec2 f = fract(st);
    float a = sirTeal_rand(i);
    float b = sirTeal_rand(i + vec2(1.0, 0.0));
    float c = sirTeal_rand(i + vec2(0.0, 1.0));
    float d = sirTeal_rand(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}
float sirTeal_fbm(vec2 st) {
    float v = 0.0;
    float a = 0.5;
    vec2 shift = vec2(100.0);
    mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.5));
    for (int i = 0; i < 5; ++i) { v += a * sirTeal_noise(st); st = rot * st * 2.0 + shift; a *= 0.5; }
    return v;
}
vec3 sir_teal(vec2 tc, float t) {
    vec2 fragCoord = tc * Resolution;
    vec2 st = fragCoord / Resolution.xy * 3.0;
    vec2 q = vec2(0.0);
    q.x = sirTeal_fbm(st);
    q.y = sirTeal_fbm(st + vec2(1.0));
    vec2 r = vec2(0.0);
    r.x = sirTeal_fbm(st + 1.0 * q + vec2(1.7, 9.2) + 0.15 * t);
    r.y = sirTeal_fbm(st + 1.0 * q + vec2(8.3, 2.8) + 0.126 * t);
    float f = sirTeal_fbm(st + r);
    vec3 color = mix(vec3(0.101961, 0.619608, 0.666667), vec3(0.666667, 0.666667, 0.498039), clamp((f * f) * 4.0, 0.0, 1.0));
    color = mix(color, vec3(0.0, 0.0, 0.164706), clamp(length(q), 0.0, 1.0));
    color = mix(color, vec3(0.666667, 1.0, 1.0), clamp(length(r.x), 0.0, 1.0));
    return (f * f * f + 0.6 * f * f + 0.5 * f) * color;
}

float sirGamer_rand(vec2 pos) { return fract(sin(dot(pos.xy, vec2(12.9898, 78.233))) * 43758.5453123); }
float sirGamer_noise(vec2 pos) {
    vec2 i = floor(pos);
    vec2 f = fract(pos);
    float a = sirGamer_rand(i + vec2(0.0, 0.0));
    float b = sirGamer_rand(i + vec2(1.0, 0.0));
    float c = sirGamer_rand(i + vec2(0.0, 1.0));
    float d = sirGamer_rand(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}
float sirGamer_fbm(vec2 pos) {
    float v = 0.0;
    float a = 0.5;
    vec2 shift = vec2(100.0);
    mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.5));
    for (int i = 0; i < 16; i++) { v += a * sirGamer_noise(pos); pos = rot * pos * 2.0 + shift; a *= 0.5; }
    return v;
}
vec3 sir_gamer(vec2 tc, float t) {
    vec2 fragCoord = tc * Resolution;
    vec2 p = (fragCoord * 2.0 - Resolution.xy) / min(Resolution.x, Resolution.y);
    float time2 = 3.0 * t / 2.0;
    vec2 q = vec2(0.0);
    q.x = sirGamer_fbm(p);
    q.y = sirGamer_fbm(p + vec2(1.0));
    vec2 r = vec2(0.0);
    r.x = sirGamer_fbm(p + 1.0 * q + vec2(1.7, 9.2) + 0.15 * time2);
    r.y = sirGamer_fbm(p + 1.0 * q + vec2(8.3, 2.8) + 0.126 * time2);
    float f = sirGamer_fbm(p + r);
    vec3 color = mix(vec3(0.101961, 0.866667, 0.319608), vec3(0.666667, 0.598039, 0.366667), clamp((f * f) * 4.0, 0.0, 1.0));
    color = mix(color, vec3(0.34509803921, 0.06666666666, 0.83137254902), clamp(length(q), 0.0, 1.0));
    color = mix(color, vec3(0.1, -0.5, 0.1), clamp(length(r.x), 0.0, 1.0));
    color = (f * f * f + 0.6 * f * f + 0.5 * f) * color;
    return color;
}

vec2 sirTech_rotate(vec2 p, float a) { return vec2(p.x * cos(a) - p.y * sin(a), p.x * sin(a) + p.y * cos(a)); }
float sirTech_rand1(float n) { return fract(sin(n) * 43758.5453123); }
float sirTech_rand2v(vec2 n) { return fract(sin(dot(n, vec2(591.32, 391.32)))); }
float sirTech_rand3(vec3 n) { return fract(sin(dot(n, vec3(591.32, 391.32, 623.54)))); }
vec2 sirTech_rnd2(vec2 p) { return fract(vec2(sin(p.x * 591.32 + p.y * 154.077), cos(p.x * 391.32 + p.y * 49.077))); }
vec3 sirTech_voronoi(vec2 x, out vec4 cellCenters) {
    float k = 0.8;
    vec2 p = floor(x), f = fract(x);
    vec2 i1 = vec2(0.0), i2 = vec2(0.0);
    vec3 res = vec3(8.0);
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 b = vec2(i, j);
            vec2 r = b - f + sirTech_rnd2(p + b) * k;
            float d = (abs(r.x) + abs(r.y));
            if (d < res.x) { res.z = res.y; res.y = res.x; res.x = d; i2 = i1; i1 = p + b; }
            else if (d < res.y) { res.z = res.y; res.y = d; i2 = p + b; }
            else if (d < res.z) { res.z = d; }
        }
    }
    cellCenters = vec4(i1, i2);
    return res;
}
float sirTech_cubicPulse(float c, float w, float x) { x = abs(x - c); if (x > w) return 0.0; x /= w; return 1.0 - x * x * (3.0 - 2.0 * x); }
vec3 sir_techno(vec2 tc, float t) {
    vec2 fragCoord = tc * Resolution;
    vec2 iRes = Resolution;
    vec2 uv = (fragCoord / iRes.xy - 0.5) * 2.0;
    uv.x *= iRes.x / iRes.y;
    uv = sirTech_rotate(uv, sin(t * 0.10));
    uv.x += t * 0.3;
    float scale = 4.0, width = 0.6;
    vec4 cc;
    vec3 vr = sirTech_voronoi(uv * scale + 10.0, cc);
    float d = vr.y - vr.x;
    if (vr.z - vr.y < width && vr.y - vr.x < width) d = max(width - (vr.z - vr.y), d);
    vec2 cellHashes = vec2(sirTech_rand2v(cc.xy), sirTech_rand2v(cc.zw));
    float wire = sirTech_cubicPulse(width, 0.06, d);
    float PI = 3.14159265359;
    float lightX = (sirTech_rotate(uv, PI / 8.0).x + t * 0.5) * 5.0;
    float lightHash1 = sirTech_rand1(floor(lightX)); float lightValue1 = fract(lightX);
    lightX = (sirTech_rotate(uv, PI * 5.0 / 8.0).x + t * 0.2) * 5.0; float lightHash2 = sirTech_rand1(floor(lightX) + 0.5); float lightValue2 = fract(lightX);
    lightX = (sirTech_rotate(uv, PI * 9.0 / 8.0).x + t * 0.2) * 5.0; float lightHash3 = sirTech_rand1(floor(lightX) + 0.5); float lightValue3 = fract(lightX);
    lightX = (sirTech_rotate(uv, PI * 13.0 / 8.0).x + t * 0.2) * 5.0; float lightHash4 = sirTech_rand1(floor(lightX) + 0.5); float lightValue4 = fract(lightX);
    float light = 0.0; float lightFrequency = 0.002;
    if (sirTech_rand3(vec3(cellHashes.xy, lightHash1)) < lightFrequency) light = wire * sirTech_cubicPulse(0.5, 0.25, lightValue1) * 3.0;
    if (sirTech_rand3(vec3(cellHashes.xy, lightHash2)) < lightFrequency) light += wire * sirTech_cubicPulse(0.5, 0.25, lightValue2) * 3.0;
    if (sirTech_rand3(vec3(cellHashes.xy, lightHash3)) < lightFrequency) light += wire * sirTech_cubicPulse(0.5, 0.25, lightValue3) * 3.0;
    if (sirTech_rand3(vec3(cellHashes.xy, lightHash4)) < lightFrequency) light += wire * sirTech_cubicPulse(0.5, 0.25, lightValue4) * 3.0;
    if ((cellHashes.x - cellHashes.y) > 0.0) { float w = sirTech_cubicPulse(width - 0.1, 0.06, d); wire += w; }
    scale *= 0.4;
    vec3 vr2 = sirTech_voronoi(uv * scale + 30.0, cc);
    d = vr2.y - vr2.x;
    if (vr2.z - vr2.y < width && vr2.y - vr2.x < width) d = max(width - (vr2.z - vr2.y), d);
    cellHashes = vec2(sirTech_rand2v(cc.xy), sirTech_rand2v(cc.zw));
    float backWire = sirTech_cubicPulse(width, 0.06, d);
    if ((cellHashes.x - cellHashes.y) > 0.0) { float w = sirTech_cubicPulse(width - 0.1, 0.06, d); backWire += w; }
    wire = max(wire, backWire * 0.3);
    wire += vr.x * 0.3 + 0.3;
    wire = wire * 0.4 + light;
    vec3 col = clamp(vec3(0.4, 0.8, 1.0) * wire, vec3(0.0), vec3(1.0));
    col *= 0.7;
    return col;
}

vec2 sirShel_rotate(vec2 p, float a) { return vec2(p.x * cos(a) - p.y * sin(a), p.x * sin(a) + p.y * cos(a)); }
float sirShel_rand(float n) { return fract(sin(n) * 43758.5453123); }
vec2 sirShel_rand2(vec2 p) { return fract(vec2(sin(p.x * 591.32 + p.y * 154.077), cos(p.x * 391.32 + p.y * 49.077))); }
float sirShel_noise1(float p) { float fl = floor(p); float fc = fract(p); return mix(sirShel_rand(fl), sirShel_rand(fl + 1.0), fc); }
float sirShel_voronoi(vec2 x) {
    vec2 p = floor(x), f = fract(x);
    vec2 res = vec2(8.0);
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 b = vec2(i, j);
            vec2 r = b - f + sirShel_rand2(p + b);
            float d = max(abs(r.x), abs(r.y));
            if (d < res.x) { res.y = res.x; res.x = d; } else if (d < res.y) { res.y = d; }
        }
    }
    return res.y - res.x;
}
vec3 sirShel_image(vec2 fragCoord, float t, vec2 iRes) {
    float flicker = sirShel_noise1(t * 2.0) * 0.8 + 0.4;
    vec2 uv = fragCoord.xy / iRes.xy;
    uv = (uv - 0.5) * 2.0;
    vec2 suv = uv;
    uv.x *= iRes.x / iRes.y;
    float v = 0.0;
    v = 1.0 - length(uv) * 1.3;
    uv *= 0.6 + sin(t * 0.1) * 0.4;
    uv = sirShel_rotate(uv, sin(t * 0.3) * 1.0);
    uv += t * 0.4;
    float a = 0.6, f = 1.0;
    for (int i = 0; i < 3; i++) {
        float v1 = sirShel_voronoi(uv * f + 5.0);
        float v2 = 0.0;
        if (i > 0) {
            v2 = sirShel_voronoi(uv * f * 0.5 + 50.0 + t);
            float va = 1.0 - smoothstep(0.0, 0.1, v1);
            float vb = 1.0 - smoothstep(0.0, 0.08, v2);
            v += a * pow(va * (0.5 + vb), 2.0);
        }
        v1 = 1.0 - smoothstep(0.0, 0.3, v1);
        v2 = a * (sirShel_noise1(v1 * 5.5 + 0.1));
        if (i == 0) v += v2 * flicker; else v += v2;
        f *= 3.0; a *= 0.7;
    }
    v *= exp(-0.6 * length(suv)) * 1.2;
    vec3 cexp = vec3(6.0, 4.0, 2.0);
    return vec3(pow(v, cexp.x), pow(v, cexp.y), pow(v, cexp.z)) * 2.0;
}
vec3 sir_sheldon(vec2 tc, float t) {
    vec2 fragCoord = tc * Resolution;
    vec2 position = fragCoord / Resolution.xy;
    float color = 0.0;
    color += sin(position.x * cos(t / 15.0) * 80.0) + cos(position.y * cos(t / 15.0) * 10.0);
    color += sin(position.y * sin(t / 10.0) * 40.0) + cos(position.x * sin(t / 25.0) * 40.0);
    color += sin(position.x * sin(t / 5.0) * 10.0) + sin(position.y * sin(t / 35.0) * 80.0);
    color *= sin(t / 10.0) * 0.5;
    vec2 fc = vec2(color, color * 0.5);
    return sirShel_image(fc, t, Resolution);
}

float sirNet_N21(vec2 p) { p = fract(p * vec2(2.15, 8.3)); p += dot(p, p + 2.5); return fract(p.x * p.y); }
vec2 sirNet_N22(vec2 p) { float n = sirNet_N21(p); return vec2(n, sirNet_N21(p + n)); }
vec2 sirNet_getPos(vec2 id, vec2 offset, float t) { vec2 n = sirNet_N22(id + offset); float x = cos(t * n.x); float y = sin(t * n.y); return vec2(x, y) * 0.4 + offset; }
float sirNet_distLine(vec2 p, vec2 a, vec2 b) { vec2 pa = p - a; vec2 ba = b - a; float tt = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - tt * ba); }
float sirNet_getLine(vec2 p, vec2 a, vec2 b, float iResY) { float dd = sirNet_distLine(p, a, b); float dx = 15.0 / iResY; return smoothstep(dx, 0.0, dd) * smoothstep(1.2, 0.3, length(a - b)); }
float sirNet_layer(vec2 st, float t, float iResY) {
    float m = 0.0;
    vec2 gv = fract(st) - 0.5;
    vec2 id = floor(st);
    vec2 pointPos = sirNet_getPos(id, vec2(0.0), t);
    m += smoothstep(0.05, 0.03, length(gv - pointPos));
    vec2 pp[9];
    pp[0] = sirNet_getPos(id, vec2(-1.0, -1.0), t);
    pp[1] = sirNet_getPos(id, vec2(-1.0, 0.0), t);
    pp[2] = sirNet_getPos(id, vec2(-1.0, 1.0), t);
    pp[3] = sirNet_getPos(id, vec2(0.0, -1.0), t);
    pp[4] = sirNet_getPos(id, vec2(0.0, 0.0), t);
    pp[5] = sirNet_getPos(id, vec2(0.0, 1.0), t);
    pp[6] = sirNet_getPos(id, vec2(1.0, -1.0), t);
    pp[7] = sirNet_getPos(id, vec2(1.0, 0.0), t);
    pp[8] = sirNet_getPos(id, vec2(1.0, 1.0), t);
    for (int j = 0; j <= 8; j++) {
        m += sirNet_getLine(gv, pp[4], pp[j], iResY);
        vec2 temp = (gv - pp[j]) * 100.0;
        m += 1.0 / dot(temp, temp) * (sin(10.0 * t + fract(pp[j].x) * 20.0) * 0.5 + 0.5);
    }
    m += sirNet_getLine(gv, pp[1], pp[3], iResY);
    m += sirNet_getLine(gv, pp[1], pp[5], iResY);
    m += sirNet_getLine(gv, pp[3], pp[7], iResY);
    m += sirNet_getLine(gv, pp[5], pp[7], iResY);
    return m;
}
vec3 sir_network(vec2 tc, float t) {
    vec2 fragCoord = tc * Resolution;
    vec2 iRes = Resolution;
    vec2 uv = (fragCoord - 0.5 * iRes.xy) / iRes.y;
    float m = 0.0;
    float theta = t * 0.1;
    mat2 rot = mat2(cos(theta), -sin(theta), sin(theta), cos(theta));
    vec2 gradient = uv;
    uv = rot * uv;
    for (float i = 0.0; i < 1.0; i += 0.25) {
        float depth = fract(i + t * 0.1);
        m += sirNet_layer(uv * mix(10.0, 0.5, depth) + i * 20.0, t, iRes.y) * smoothstep(0.0, 0.2, depth) * smoothstep(1.0, 0.8, depth);
    }
    vec3 baseColor = sin(vec3(1.0) * t * 0.2) * 0.5 + 1.0;
    vec3 col = (m - gradient.y) * baseColor;
    return vec3(col.r * 1.0, col.g * 0.0, col.b * 1.0);
}

vec3 sir_dispatch(int mode, vec2 uv, float t) {
    if (mode == 14) return sir_caustic(uv, t, vec3(0.0, 0.35, 0.5));
    if (mode == 15) return sir_caustic(uv, t, vec3(0.727, 0.221, 0.765));
    if (mode == 16) return sir_golden(uv, t);
    if (mode == 17) return sir_flow(uv, t);
    if (mode == 18) return sir_galaxy(uv, t);
    if (mode == 19) return sir_smoky(uv, t);
    if (mode == 20) return sir_homie(uv, t);
    if (mode == 21) return sir_yippie(uv, t);
    if (mode == 22) return sir_hidef(uv, t);
    if (mode == 23) return sir_gang(uv, t);
    if (mode == 24) return sir_holy(uv, t);
    if (mode == 25) return sir_teal(uv, t);
    if (mode == 26) return sir_gamer(uv, t);
    if (mode == 27) return sir_techno(uv, t);
    if (mode == 28) return sir_sheldon(uv, t);
    return sir_network(uv, t);
}


void main() {
    vec4 hand = texture(Sampler0, TexCoord);
    int mode = int(HandShaderMode + 0.5);

    if (mode == 8) {
        vec4 glass = liquid_glass_effect(TexCoord, Time);
        if (glass.a <= 0.004) {
            discard;
        }
        OutColor = glass * FragColor;
        return;
    }

    if (hand.a <= 0.004) {
        discard;
    }

    if (mode >= 14) {
        vec3 sc = sir_dispatch(mode, TexCoord, Time);
        float alpha = hand.a * HandColor.a;
        OutColor = vec4(sc, alpha) * FragColor;
        return;
    }

    vec3 base = HandColor.rgb;
    vec3 effect = base;

    if (mode == 1) {
        float hue = fract(TexCoord.x * 0.5 + TexCoord.y * 0.25 + Time * 0.1);
        effect = hsv2rgb(vec3(hue, 1.0, 1.0));
    } else if (mode == 2) {
        float wave = 0.5 + 0.5 * sin((TexCoord.x + TexCoord.y) * 12.0 - Time * 3.0);
        effect = base * (0.45 + 0.85 * wave);
    } else if (mode == 3) {
        float pulse = 0.5 + 0.5 * sin(Time * 4.5);
        effect = base * mix(0.25, 2.0, pulse);
    } else if (mode == 4) {
        effect = cosmos_effect(TexCoord, Time);
    } else if (mode == 5) {
        effect = nebula_effect(TexCoord, Time);
    } else if (mode == 6) {
        effect = cobweb_effect(TexCoord, Time);
    } else if (mode == 7) {
        effect = plasma_effect(TexCoord, Time);
    } else if (mode == 9) {
        effect = matrix_effect(TexCoord, Time);
    } else if (mode == 10) {
        effect = cosmicblast_effect(TexCoord, Time);
    } else if (mode == 11) {
        effect = aliennebula_effect(TexCoord, Time);
    }

    float luma = dot(hand.rgb, vec3(0.299, 0.587, 0.114));
    vec3 finalRgb;
    if (mode == 9) {
        finalRgb = effect * (0.5 + 0.7 * luma);
    } else if (mode == 10) {
        finalRgb = effect * (0.7 + 0.5 * luma);
    } else if (mode == 11) {
        finalRgb = effect * (0.6 + 0.55 * luma);
    } else if (HandShaderOnly > 0.5) {
        finalRgb = effect * (0.35 + 0.85 * luma);
    } else {
        finalRgb = mix(hand.rgb, hand.rgb * effect * 1.6, 0.65);
    }

    float alpha = hand.a * HandColor.a;
    OutColor = vec4(finalRgb, alpha) * FragColor;
}
