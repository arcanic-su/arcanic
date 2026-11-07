#version 150

#moj_import <arcanicvisuals:bo_uniforms.glsl>
#moj_import <arcanicvisuals:arc_common.glsl>

in vec2 FragCoord;
in vec4 FragColor;
out vec4 OutColor;

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

void main() {
    vec2 uv = gl_FragCoord.xy / Resolution;
    float aspect = Resolution.x / Resolution.y;
    vec2 p = vec2(uv.x * aspect, uv.y);
    float t = Time;

    vec3 tint = TintColor;
    vec3 color = tint * 0.12;
    float intensity = 0.0;

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
        float contrib = glyph * fade * layerFade;

        vec3 streak = mix(tint * 2.2, vec3(1.2), head * 0.85);
        color += streak * contrib;
        intensity = max(intensity, contrib);
    }

    color = clamp(color, 0.0, 1.6);
    float alpha = Fade * FragColor.a * (0.3 + 0.7 * clamp(intensity, 0.0, 1.0));
    OutColor = vec4(color, alpha);
}
