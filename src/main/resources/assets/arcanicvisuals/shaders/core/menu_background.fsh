#version 330
uniform sampler2D Sampler0;
in vec2 splashState;
in vec4 splashTint;
in vec2 clipPosition;
out vec4 fragColor;
vec4 TopLeftColor;
vec4 BottomLeftColor;
vec4 TopRightColor;
#moj_import <arcanicvisuals:menu_noise.glsl>
#moj_import <arcanicvisuals:ember.glsl>
#moj_import <arcanicvisuals:menu_patterns.glsl>
vec3 backdrop(float mode,vec2 uv,float aspect){
 vec3 c=menuPattern(mode,uv,aspect,splashState.x,1.0,EMBER_SWIRL_ITERATIONS,emberScreenFrame(uv,aspect));
 c*=abs(mode-MODE_EMBER)<MODE_EPSILON?emberVignette(uv):vignetteAt(uv,menuVignetteStrength(mode));
 return c;
}
void main(){
 vec2 uv=vec2(clipPosition.x*.5+.5,.5-clipPosition.y*.5);
 vec2 pixel=max(abs(vec2(dFdx(clipPosition.x),dFdy(clipPosition.y))),vec2(1e-6));
 float aspect=pixel.x/pixel.y;
 TopLeftColor=texelFetch(Sampler0,ivec2(0,0),0);
 BottomLeftColor=texelFetch(Sampler0,ivec2(1,0),0);
 TopRightColor=texelFetch(Sampler0,ivec2(2,0),0);
 float mode=round(splashTint.r*255.0),previous=round(splashTint.g*255.0),fade=splashState.y;
 vec3 color=backdrop(mode,uv,aspect);
 if(previous!=mode&&fade<1.0)color=mix(backdrop(previous,uv,aspect),color,fade*fade*(3.0-2.0*fade));
 // Arcanic's animated fields with a calmer luminance and a readable central surface.
 color*=.60;
 color+=(hash12(gl_FragCoord.xy)-.5)/255.0;
 fragColor=vec4(clamp(color,0.0,1.0),splashTint.a);
}
