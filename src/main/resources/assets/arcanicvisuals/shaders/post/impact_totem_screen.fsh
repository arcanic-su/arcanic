#version 330

// Экранная часть Totem Effect (проход конвейера PostPass): кольцо искажения от точки на экране,
// щель-разлом, радиальное размытие и стягивание к центру, хроматический сдвиг,
// обесцвечивание, вспышка и виньетка. Параметры — один UBO.

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

layout(std140) uniform Params {
    vec4 Center;  // xy — центр в uv, z — соотношение сторон (w/h), w — время
    vec4 Wave;    // x — радиус кольца, y — ширина, z — сила искажения, w — хроматика
    vec4 Grade;   // x — обесцвечивание, y — вспышка, zw — цвет вспышки (r, g)
    vec4 Grade2;  // x — цвет вспышки (b), y — виньетка, z — радиальное размытие, w — стягивание к центру
    vec4 Slit;    // flood: x - radius, y - edge width, z - grey outside, w - vivid colour behind the front
};

const vec3 LUMA = vec3(0.299, 0.587, 0.114);

vec3 sampleChroma(vec2 q, vec2 ch) {
    return vec3(
        texture(InSampler, clamp(q - ch, 0.0, 1.0)).r,
        texture(InSampler, clamp(q, 0.0, 1.0)).g,
        texture(InSampler, clamp(q + ch, 0.0, 1.0)).b);
}

void main() {
    vec2 uv = texCoord;
    vec2 d = uv - Center.xy;
    d.x *= Center.z;
    float dist = length(d);
    vec2 dir = dist > 1e-5 ? d / dist : vec2(0.0);
    vec2 dirUv = vec2(dir.x / Center.z, dir.y);

    // Гауссова полоса на фронте волны: пиксели тянутся к центру, за фронтом — светлая кромка.
    float band = exp(-pow((dist - Wave.x) / max(Wave.y, 1e-4), 2.0));
    vec2 off = dirUv * band * Wave.z;

    // Долли-зум: край кадра стягивается к цели.
    off += (uv - Center.xy) * Grade2.w;
    // Никакой сдвиг не рвёт кадр: не больше нескольких процентов экрана.
    off = clamp(off, vec2(-0.05), vec2(0.05));

    // Хроматика: сильнее на фронте волны, слабее — радиально по всему кадру.
    vec2 ch = clamp((off + dirUv * min(dist, 1.0) * 0.012) * Wave.w, vec2(-0.012), vec2(0.012));

    vec3 col = vec3(0.0);
    int taps = Grade2.z > 0.0 ? 8 : 1;
    for (int i = 0; i < 8; i++) {
        if (i >= taps) break;
        float k = float(i) / 7.0;
        vec2 q = uv - off - clamp((uv - Center.xy) * Grade2.z, vec2(-0.06), vec2(0.06)) * k;
        col += sampleChroma(q, ch);
    }
    col /= float(taps);
    col += band * Wave.z * 3.0;

    float luma = dot(col, LUMA);
    // colour flooding back from the centre: grey outside the front, more vivid just behind it
    float outside = smoothstep(Slit.x - Slit.y, Slit.x, dist) * Slit.z;
    float behind = exp(-pow((dist - (Slit.x - Slit.y)) / max(Slit.y, 1e-4), 2.0)) * Slit.w;
    col = mix(vec3(luma), col, 1.0 + behind);
    col = mix(col, vec3(luma), clamp(max(Grade.x, outside), 0.0, 1.0));

    float vd = length((uv - 0.5) * vec2(Center.z, 1.0));
    col *= 1.0 - Grade2.y * smoothstep(0.25, 0.95, vd);

    col = mix(col, vec3(Grade.z, Grade.w, Grade2.x), clamp(Grade.y, 0.0, 1.0));
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
