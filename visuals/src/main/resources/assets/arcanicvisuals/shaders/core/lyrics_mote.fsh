#version 330
// A mote of light: a soft round glow with a small bright core, premultiplied for additive blending.
in vec2 TexCoord;
in vec4 FragColor;
out vec4 OutColor;
void main() {
    float d = length(TexCoord * 2.0 - 1.0);
    float glow = exp(-d * d * 5.0) * (1.0 - smoothstep(0.85, 1.0, d));
    float core = 1.0 - smoothstep(0.0, 0.28, d);
    float a = (glow * 0.75 + core * 0.6) * FragColor.a;
    if (a <= 0.003) discard;
    vec3 rgb = mix(FragColor.rgb, vec3(1.0), core * 0.55);
    OutColor = vec4(rgb * a, a);
}
