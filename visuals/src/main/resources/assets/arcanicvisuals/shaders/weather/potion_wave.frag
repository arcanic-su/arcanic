#version 150

// Potion Drink's two waves over the whole world, from the depth buffer: the first runs out from where you drank
// and leaves the world grey behind it; the second follows and brings the colour back, richer for a while, its front a
// band of the potion's colours turning into the rainbow, two faint echoes behind it.

in vec2 TexCoord;

uniform sampler2D Sampler0; // the frame
uniform sampler2D Sampler1; // its depth
uniform mat4 InvViewProj;
uniform vec3 Origin;        // the waves' centre, from the camera
uniform vec4 Waves;         // first radius, second radius, first front's width, second front's width
uniform vec4 Look;          // grey, vivid, the sky's distance, how much of the rainbow
uniform vec4 Fronts;        // first front's light, second front's light, echoes, 0
uniform vec3 Colour1;
uniform vec3 Colour2;
uniform float Time;

out vec4 OutColor;

const vec3 LUMA = vec3(0.299, 0.587, 0.114);

vec3 worldFromDepth(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 pos = InvViewProj * clip;
    return pos.xyz / pos.w;
}

vec3 hue(float h) {
    return clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
}

float band(float d, float at, float width) {
    float x = (d - at) / max(width, 1e-3);
    return exp(-x * x);
}

void main() {
    vec4 src = texture(Sampler0, TexCoord);
    float depth = texture(Sampler1, TexCoord).r;
    bool sky = depth >= 1.0;
    vec3 view = worldFromDepth(TexCoord, sky ? 0.9999 : depth);
    vec3 rel = view - Origin;
    float d = sky ? Look.z : length(rel);
    vec3 dir = sky ? normalize(view) : rel / max(length(rel), 1e-3);

    // behind the first front and not yet reached by the second: grey, a touch darker
    float passed1 = 1.0 - smoothstep(Waves.x - Waves.z, Waves.x, d);
    float waiting2 = smoothstep(Waves.y - Waves.w, Waves.y, d);
    float grey = passed1 * waiting2 * Look.x;
    vec3 col = src.rgb;
    float luma = dot(col, LUMA);
    col = mix(col, vec3(luma) * 0.9, grey);

    // just behind the second front the colours come back richer than they were, easing back to themselves
    float vivid = Look.y * band(d, Waves.y - Waves.w * 2.5, Waves.w * 3.0) * (1.0 - waiting2);
    float l2 = dot(col, LUMA);
    col = mix(vec3(l2), col, 1.0 + vivid);
    col *= 1.0 + vivid * 0.12;

    // the fronts: soft bands of light running over the ground, the walls and at last the sky
    float skyK = sky ? 0.45 : 1.0;
    float b1 = band(d, Waves.x, Waves.z * 0.55);
    col += Colour1 * b1 * Fronts.x * skyK;
    float az = atan(dir.z, dir.x) / 6.2831853 + 0.5;
    vec3 rainbow = hue(fract(az * 2.0 + d * 0.012 - Time * 0.18));
    vec3 c2 = mix(Colour2, rainbow * 0.85 + Colour2 * 0.25, Look.w);
    float b2 = band(d, Waves.y, Waves.w * 0.55);
    float echoes = band(d, Waves.y - Waves.w * 2.2, Waves.w * 0.22) + band(d, Waves.y - Waves.w * 4.0, Waves.w * 0.18) * 0.6;
    col += c2 * (b2 * Fronts.y + echoes * Fronts.z) * skyK;

    OutColor = vec4(clamp(col, 0.0, 1.0), src.a);
}
