#version 150

#moj_import <arcanicvisuals:arc_customsky_common.glsl>

uniform sampler2D NoiseTex;
uniform sampler2D History;

in vec2 texCoord;
out vec4 fragColor;

const int ITERATIONS = 200;
const float FAR = 16.0;
const float ENTRY_RADIUS = 8.0;
const float CAMERA_DISTANCE = 14.0;
const float INCLINATION = 0.10;
const float EXPOSURE = 0.024;
const float THEME_MIX = 0.5;
const float ZOOM = 2.4;
const float TAA_BLEND = 0.85;
const float TAA_CLAMP = 0.35;
const float SPIN = 0.16;
const float TAU = 6.28318531;
const float DITHER_STEP = 1.0 / 255.0;
const vec3 HOLE_DIRECTION = vec3(0.34, 0.62, 0.71);

float discNoise(vec3 x) {
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    vec2 uv = (p.xy + vec2(37.0, 17.0) * p.z) + f.xy;
    vec2 rg = textureLod(NoiseTex, (uv + 0.5) / 256.0, 0.0).yx;
    return -1.0 + 2.0 * mix(rg.x, rg.y, f.z);
}

vec3 discCoord(float angle, float radius, float frequency) {
    float scaled = 1.425 * frequency;
    return vec3(cos(angle) * scaled, sin(angle) * scaled, radius * frequency);
}

float octave(float angle, float radius, float frequency, float aa) {
    float weight = clamp(1.5 - aa * frequency * 1.5, 0.0, 1.0);
    if (weight <= 0.01) {
        return 0.5;
    }
    return mix(0.5, discNoise(discCoord(angle, radius, frequency)) * 0.5 + 0.5, weight);
}

float pcurve(float x, float a, float b) {
    float k = pow(a + b, a + b) / (pow(a, a) * pow(b, b));
    return k * pow(x, a) * pow(1.0 - x, b);
}

float sdTorus(vec3 p, vec2 t) {
    vec2 q = vec2(length(p.xz) - t.x, p.y);
    return length(q) - t.y;
}

vec3 lensedStars(vec3 dir, float time, float pixelAngle) {
    vec3 accum = vec3(0.0);
    vec3 p = dir * 42.0;
    float scale = 42.0;
    float dens = 0.05;
    float amp = 1.0;
    for (int i = 0; i < 4; i++) {
        float fade = clamp(2.0 - pixelAngle * scale / 0.17, 0.0, 1.0);
        if (fade > 0.01) {
            vec3 id = floor(p);
            vec3 q = fract(p) - 0.5;
            vec3 rn = skyHash33(id);
            float core = smoothstep(0.44, 0.05, length(q));
            float hit = step(rn.x, dens);
            float twinkle = 0.80 + 0.20 * sin(time * (0.6 + rn.z * 1.3) + rn.y * 47.0);
            vec3 tint = mix(vec3(1.0, 0.72, 0.45), vec3(0.72, 0.86, 1.0), rn.y);
            accum += hit * core * core * tint * (0.35 + 0.65 * rn.z) * twinkle * amp * fade;
        }
        p = p * 1.63 + 17.0;
        scale *= 1.63;
        dens *= 0.62;
        amp *= 0.78;
    }
    return accum;
}

void haze(inout vec3 color, vec3 pos, float alpha, vec3 mainColor) {
    if (dot(pos, pos) > 36.0) {
        return;
    }
    float torusDistance = length(sdTorus(pos + vec3(0.0, -0.05, 0.0), vec2(1.0, 0.01)));
    float glow = 1.0 / (pow(torusDistance, 2.0) + 0.001);
    glow *= length(pos) < 0.5 ? 0.0 : 1.0;
    color += mainColor * glow * (2.9 / float(ITERATIONS)) * (1.0 - alpha);
}

void gasDisc(inout vec3 color, inout float alpha, vec3 pos, float time,
             vec3 mainColor, float aa, vec3 eyeVector) {
    float discRadius = 3.2;
    float discWidth = 5.3;
    float discInner = discRadius - discWidth * 0.5;

    vec3 discNormal = vec3(0.0, 1.0, 0.0);
    float discThickness = 0.1;

    float distFromCenter = length(pos);
    if (distFromCenter > 7.0) {
        return;
    }
    float distFromDisc = dot(discNormal, pos);

    float radialGradient = 1.0 - clamp((distFromCenter - discInner) / discWidth * 0.5, 0.0, 1.0);
    float coverage = pcurve(radialGradient, 4.0, 0.9);

    discThickness *= radialGradient;
    coverage *= clamp(1.0 - abs(distFromDisc) / max(discThickness, 1e-5), 0.0, 1.0);

    vec3 tangent = normalize(vec3(-pos.z, 0.0, pos.x));
    float doppler = pow(clamp(1.0 - 0.55 * dot(tangent, eyeVector), 0.45, 1.9), -2.5);
    vec3 dopplerTint = mix(vec3(1.30, 0.72, 0.45), vec3(0.80, 0.92, 1.45),
            clamp((doppler - 0.7) * 0.9, 0.0, 1.0));

    vec3 dustLit = mainColor * doppler * dopplerTint;
    float dustGlow = 1.0 / (pow(1.0 - radialGradient, 2.0) * 290.0 + 0.002);
    vec3 dustColor = dustLit * dustGlow * 8.2;

    coverage = clamp(coverage * 0.7, 0.0, 1.0);

    float fade = pow((abs(distFromCenter - discInner) + 0.4), 4.0) * 0.04;
    float bloomFactor = 1.0 / (pow(distFromDisc, 2.0) * 40.0 + fade + 0.00002);
    vec3 bloom = dustLit * pow(bloomFactor, 1.5);

    bloom *= mix(vec3(1.7, 1.1, 1.0), vec3(0.5, 0.6, 1.0), vec3(pow(radialGradient, 2.0)));
    bloom *= mix(vec3(1.7, 0.5, 0.1), vec3(1.0), vec3(pow(radialGradient, 0.5)));

    dustColor = mix(dustColor, bloom * 150.0, clamp(1.0 - coverage, 0.0, 1.0));
    coverage = clamp(coverage + bloomFactor * bloomFactor * 0.1, 0.0, 1.0);

    if (coverage < 0.01) {
        return;
    }

    float angle = atan(-pos.x, -pos.z);
    float radius = (distFromCenter * 1.5 + 0.55 + distFromDisc * 1.5) * 0.95 + time * 0.012;

    float omega = SPIN * pow(3.2 / max(distFromCenter, 0.75), 1.5);
    float angleA = angle + mod(time * omega, TAU);
    float angleB = angle + mod(time * omega * 0.45, TAU);

    float detail = 1.0;
    detail *= octave(angleA, radius, 3.0, aa);
    detail *= octave(angleB, radius, 6.0, aa);
    detail *= octave(angleA, radius, 12.0, aa);
    detail *= octave(angleB, radius, 24.0, aa);

    float mask = 2.0;
    float radius2 = radius + 30.0;
    mask *= octave(angleB, radius2, 3.0, aa);
    mask *= octave(angleA, radius2, 6.0, aa);
    mask *= octave(angleB, radius2, 12.0, aa);
    mask *= octave(angleA, radius2, 24.0, aa);
    mask *= octave(angleB, radius2, 48.0, aa);
    mask *= octave(angleA, radius2, 92.0, aa);

    dustColor *= detail * 0.998 + 0.002;
    coverage *= mask;

    float bandAngle = angle + mod(time * omega * 0.5, TAU);
    float band = discNoise(discCoord(bandAngle, radius, 1.35)) * 0.5 + 0.5;
    float grain = discNoise(discCoord(bandAngle, radius + 70.0, 3.46)) * 0.5 + 0.5;
    vec3 discTexture = mix(vec3(0.95, 0.55, 0.26), vec3(0.42, 0.60, 1.0), band) * (0.45 + 0.80 * grain);
    dustColor *= pow(discTexture, vec3(2.0)) * 4.0;

    float arm = 0.5 + 0.5 * cos(2.0 * angle + 2.6 * log(max(distFromCenter, 0.3)) + time * SPIN * 4.5);
    dustColor *= 0.40 + 1.05 * arm * arm;
    coverage *= 0.78 + 0.22 * arm;

    float hotAngleA = abs(mod(angleA - 1.35, TAU) - 3.14159265);
    float hotRadiusA = distFromCenter - 1.5;
    float hotAngleB = abs(mod(angleB - 4.2, TAU) - 3.14159265);
    float hotRadiusB = distFromCenter - 2.7;
    float hotspot = exp(-(hotAngleA * hotAngleA * 0.5 + hotRadiusA * hotRadiusA * 7.0)) * 3.4
                  + exp(-(hotAngleB * hotAngleB * 0.9 + hotRadiusB * hotRadiusB * 5.0)) * 1.8;
    dustColor *= 1.0 + hotspot;

    coverage = clamp(coverage * 1200.0 / float(ITERATIONS), 0.0, 1.0);
    dustColor = max(vec3(0.0), dustColor);
    coverage *= pcurve(radialGradient, 4.0, 0.9);

    color = (1.0 - alpha) * dustColor * coverage + color;
    alpha = (1.0 - alpha) * coverage + alpha;
}

void warpSpace(inout vec3 eyeVector, vec3 rayPos) {
    float singularityDistance = length(rayPos);
    float warpFactor = 1.0 / (pow(singularityDistance, 2.0) + 0.000001);
    vec3 towardSingularity = -rayPos / max(singularityDistance, 1e-4);
    eyeVector = normalize(eyeVector + towardSingularity * warpFactor * 5.0 / float(ITERATIONS));
}

vec3 toLocal(vec3 dir, vec3 basisX, vec3 basisY, vec3 basisZ) {
    vec3 local = vec3(dot(dir, basisX), dot(dir, basisY), dot(dir, basisZ));
    local.xy /= ZOOM;
    return normalize(local);
}

vec3 sampleHistory(vec2 uv, vec2 res) {
    vec2 samplePos = uv * res;
    vec2 texPos = floor(samplePos - 0.5) + 0.5;
    vec2 f = samplePos - texPos;

    vec2 w0 = f * (-0.5 + f * (1.0 - 0.5 * f));
    vec2 w1 = 1.0 + f * f * (-2.5 + 1.5 * f);
    vec2 w2 = f * (0.5 + f * (2.0 - 1.5 * f));
    vec2 w3 = f * f * (-0.5 + 0.5 * f);

    vec2 w12 = w1 + w2;
    vec2 offset12 = w2 / w12;

    vec2 p0 = (texPos - 1.0) / res;
    vec2 p3 = (texPos + 2.0) / res;
    vec2 p12 = (texPos + offset12) / res;

    vec3 result = vec3(0.0);
    result += textureLod(History, vec2(p0.x, p0.y), 0.0).rgb * (w0.x * w0.y);
    result += textureLod(History, vec2(p12.x, p0.y), 0.0).rgb * (w12.x * w0.y);
    result += textureLod(History, vec2(p3.x, p0.y), 0.0).rgb * (w3.x * w0.y);
    result += textureLod(History, vec2(p0.x, p12.y), 0.0).rgb * (w0.x * w12.y);
    result += textureLod(History, vec2(p12.x, p12.y), 0.0).rgb * (w12.x * w12.y);
    result += textureLod(History, vec2(p3.x, p12.y), 0.0).rgb * (w3.x * w12.y);
    result += textureLod(History, vec2(p0.x, p3.y), 0.0).rgb * (w0.x * w3.y);
    result += textureLod(History, vec2(p12.x, p3.y), 0.0).rgb * (w12.x * w3.y);
    result += textureLod(History, vec2(p3.x, p3.y), 0.0).rgb * (w3.x * w3.y);

    return clamp(result, vec3(0.0), vec3(1.0));
}

void main() {
    float time = misc.x;
    float brightness = misc.w;

    vec2 res = vec2(textureSize(History, 0));

    vec3 rd = skyRayDirection(texCoord);
    vec3 rdJitter = skyRayDirection(texCoord + taa.xy / res);

    vec3 holeDir = normalize(HOLE_DIRECTION);
    vec3 upReference = vec3(0.0, 1.0, 0.0);
    vec3 tangent = normalize(upReference - dot(upReference, holeDir) * holeDir);
    vec3 basisY = normalize(tangent * cos(INCLINATION) - holeDir * sin(INCLINATION));
    vec3 basisX = normalize(cross(basisY, holeDir));
    vec3 basisZ = cross(basisX, basisY);

    vec3 tint = skyTint(atan(rd.z, rd.x), 0.5, time);
    vec3 mainColor = mix(vec3(1.0), tint, THEME_MIX);

    vec3 cameraWorld = -holeDir * CAMERA_DISTANCE;
    vec3 pos = vec3(dot(cameraWorld, basisX), dot(cameraWorld, basisY), dot(cameraWorld, basisZ));
    vec3 eyeVector = toLocal(rdJitter, basisX, basisY, basisZ);
    float pixelAngle = length(toLocal(skyRayDirection(texCoord + vec2(1.0, 0.0) / res), basisX, basisY, basisZ)
            - toLocal(rd, basisX, basisY, basisZ));
    float aa = pixelAngle * CAMERA_DISTANCE * 1.425;

    vec3 color = vec3(0.0);
    float alpha = 0.0;
    float captured = 0.0;

    float impact = length(cross(pos, eyeVector));
    float along = dot(pos, eyeVector);

    if (impact < ENTRY_RADIUS && along < 0.0) {
        float entry = -along - sqrt(max(0.0, ENTRY_RADIUS * ENTRY_RADIUS - impact * impact));
        float stepLength = FAR / float(ITERATIONS);
        float dither = fract(skyHash21(gl_FragCoord.xy) + taa.w);
        vec3 rayPos = pos + eyeVector * (entry + dither * stepLength);

        for (int i = 0; i < ITERATIONS; i++) {
            warpSpace(eyeVector, rayPos);
            rayPos += eyeVector * stepLength;
            gasDisc(color, alpha, rayPos, time, mainColor, aa, eyeVector);
            haze(color, rayPos, alpha, mainColor);

            float distanceSquared = dot(rayPos, rayPos);
            captured = max(captured, 1.0 - smoothstep(0.32, 0.85, sqrt(distanceSquared)));
            if (alpha > 0.995) {
                break;
            }
            if (distanceSquared > 49.0 && dot(rayPos, eyeVector) > 0.0) {
                break;
            }
        }
    }

    color *= EXPOSURE;

    float ring = pow(clamp(captured * (1.0 - captured) * 4.0, 0.0, 1.0), 6.0);
    color += mix(vec3(1.0), mainColor, 0.35) * ring * 0.6 * (1.0 - alpha);
    color *= brightness;

    vec3 current = skyEncodeHDR(color);

    if (taa.z > 0.5) {
        vec4 projected = prevViewProj * vec4(rd, 0.0);
        if (projected.w > 1e-5) {
            vec2 previousUv = projected.xy / projected.w * 0.5 + 0.5;
            vec2 guard = 1.5 / res;
            if (all(greaterThan(previousUv, guard)) && all(lessThan(previousUv, 1.0 - guard))) {
                vec3 history = clamp(sampleHistory(previousUv, res),
                        current - TAA_CLAMP, current + TAA_CLAMP);
                current = mix(current, history, TAA_BLEND);
            }
        }
    }

    current += (fract(skyHash21(gl_FragCoord.xy + 13.7) + taa.w) - 0.5) * DITHER_STEP;

    fragColor = vec4(max(current, vec3(0.0)), 1.0);
}
