#version 330 core

// A solid quad of the glow colour over the captured items, blended (DST_ALPHA, ZERO, ZERO, ONE).
// With Gradient the colour is a two-colour gradient running up the screen (bands a quarter of the height,
// 80 GUI pixels a second), laid on the items themselves so the glow carries it.
in vec2 TexCoord;

uniform vec4 Color;
uniform int Gradient;
uniform vec3 ColA;
uniform vec3 ColB;
uniform vec3 Band; // band height, scroll, screen height - in pixels

out vec4 OutColor;

void main() {
    vec4 c = Color;
    if (Gradient != 0) {
        float yTop = (1.0 - TexCoord.y) * Band.z;
        float phase = (yTop + Band.y) / Band.x;
        float i = floor(phase);
        float f = smoothstep(0.0, 1.0, phase - i);
        c = vec4(mod(i, 2.0) < 0.5 ? mix(ColA, ColB, f) : mix(ColB, ColA, f), 1.0);
    }
    OutColor = c;
}
