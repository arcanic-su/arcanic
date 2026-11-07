#version 150
// One dual-Kawase style step: the centre and four diagonal taps at a growing distance. Run a few times, half size,
// it gives the soft frosted backdrop behind the menu and HUD panels (arcanic's look).
layout(std140) uniform Frost { vec4 Params; };   // xy = source texel size, z = tap distance in texels
uniform sampler2D Sampler0;
in vec2 TexCoord;
out vec4 fragColor;
void main() {
    vec2 o = Params.xy * Params.z;
    vec3 c = texture(Sampler0, TexCoord).rgb * 4.0;
    c += texture(Sampler0, TexCoord + vec2(-o.x, -o.y)).rgb;
    c += texture(Sampler0, TexCoord + vec2( o.x, -o.y)).rgb;
    c += texture(Sampler0, TexCoord + vec2(-o.x,  o.y)).rgb;
    c += texture(Sampler0, TexCoord + vec2( o.x,  o.y)).rgb;
    fragColor = vec4(c / 8.0, 1.0);
}