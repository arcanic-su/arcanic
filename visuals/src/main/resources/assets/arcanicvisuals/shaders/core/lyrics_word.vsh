#version 330
// World lyrics: glyph quads placed in the world (camera-relative), drawn with the frame's own view rotation and
// projection (view bobbing included). PlanePos is the point on the word's sheet, in sheet units, so the sweep,
// dissolve grain and fill stay fixed on the letters however the camera moves.
// The word moves on its sheet as it lives: it rises into place and grows a little as it burns in, gives a small
// hop while it is sung, and floats up as it dissolves.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};
layout(std140) uniform LyricsWord {
    vec4 Origin;     // xyz sheet origin (camera-relative), w pass (0 body, 1 shadow)
    vec4 Right;      // xyz one sheet unit to the right, w atlas distance range
    vec4 Down;       // xyz one sheet unit down, w glyph size in sheet units
    vec4 Shape;      // x stroke thickness, y edge smoothness, z reveal progress, w leave progress
    vec4 Motion;     // x time in s, y word left edge, z word width (sheet units), w word top
    vec4 Fill;       // x sung fraction, y accent gradient, z glow radius, w glow intensity (shadow strength in pass 1)
    vec4 Accent;     // rgb accent, a per-word noise seed
    vec4 GlowColour; // rgba glow colour
};
in vec3 Position;
in vec2 UV0;
in vec4 Color;
out vec2 TexCoord;
out vec4 FragColor;
out vec2 PlanePos;

const float RISE = 0.3;      // how far below its place a word starts, in glyph heights
const float GROW = 0.1;      // how much smaller it starts
const float HOP = 0.08;      // the lift while it is sung
const float SWELL = 0.05;    // and the swell
const float DRIFT = 0.45;    // how far it floats up as it dissolves
const float SPREAD = 0.08;   // and how much it opens out

void main() {
    TexCoord = UV0;
    FragColor = Color;
    vec3 rel = Position - Origin.xyz;
    vec2 plane = vec2(dot(rel, Right.xyz) / dot(Right.xyz, Right.xyz), dot(rel, Down.xyz) / dot(Down.xyz, Down.xyz));
    PlanePos = plane;

    float size = Down.w;
    float reveal = clamp(Shape.z, 0.0, 1.0);
    float leave = clamp(Shape.w, 0.0, 1.0);
    float sung = clamp(Fill.x, 0.0, 1.0);
    float arrive = 1.0 - (1.0 - reveal) * (1.0 - reveal);
    float hop = sung > 0.0 && sung < 1.0 ? sin(3.14159265 * sung) : 0.0;
    float scale = (1.0 - GROW + GROW * arrive) * (1.0 + SWELL * hop) * (1.0 + SPREAD * leave);
    vec2 centre = vec2(Motion.y + Motion.z * 0.5, Motion.w + size * 0.45);
    vec2 moved = centre + (plane - centre) * scale;
    moved.y += size * (RISE * (1.0 - arrive) - HOP * hop - DRIFT * leave * leave);

    vec3 pos = Origin.xyz + Right.xyz * moved.x + Down.xyz * moved.y;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
}
