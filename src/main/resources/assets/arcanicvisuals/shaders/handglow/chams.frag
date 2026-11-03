#version 330 core

// The held items filled with the glow colour, a chams look: one flat colour, its shading taken from the
// item's own light and dark texels so the shape and the pixels still read; laid over the item drawn premultiplied.
in vec2 TexCoord;

uniform sampler2D Sampler0; // the items drawn full bright, over transparent
uniform vec3 Colour;
uniform int Gradient;
uniform vec3 ColA;
uniform vec3 ColB;
uniform vec3 Band; // band height, scroll, screen height - in pixels
uniform float Alpha;

out vec4 OutColor;

void main() {
    vec4 s = texture(Sampler0, TexCoord);
    if (s.a < 0.01) discard;
    vec3 c = Colour;
    if (Gradient != 0) {
        float yTop = (1.0 - TexCoord.y) * Band.z;
        float phase = (yTop + Band.y) / Band.x;
        float i = floor(phase);
        float f = smoothstep(0.0, 1.0, phase - i);
        c = mod(i, 2.0) < 0.5 ? mix(ColA, ColB, f) : mix(ColB, ColA, f);
    }
    float l = dot(s.rgb / max(s.a, 0.001), vec3(0.299, 0.587, 0.114));
    vec3 shade = mix(c * 0.42, mix(c, vec3(1.0), 0.38), smoothstep(0.08, 0.92, l));
    float a = s.a * Alpha;
    OutColor = vec4(shade * a, a);
}
