#version 150

in vec2 Uv;
in vec4 Col;

out vec4 OutColor;

void main() {
    vec2 d = (Uv - 0.5) * 2.0;
    float r = dot(d, d);
    if (r > 1.0) discard;

    float core = 1.0 - smoothstep(0.0, 1.0, r);
    float alpha = Col.a * core;
    if (alpha <= 0.003) discard;

    OutColor = vec4(Col.rgb, alpha);
}
