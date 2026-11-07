#version 330
#moj_import <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
float median(float a,float b,float c){return max(min(a,b),min(max(a,b),c));}
void main(){
    vec3 sampleColor=texture(Sampler0,texCoord0).rgb;
    float distance=median(sampleColor.r,sampleColor.g,sampleColor.b)-0.5;
    vec2 unitRange=vec2(10.0)/vec2(textureSize(Sampler0,0));
    float screenRange=max(0.5*dot(unitRange,1.0/fwidth(texCoord0)),1.0);
    float coverage=clamp(distance*screenRange+0.5,0.0,1.0);
    if(coverage<0.001)discard;
    fragColor=vec4(vertexColor.rgb,vertexColor.a*coverage)*ColorModulator;
}
