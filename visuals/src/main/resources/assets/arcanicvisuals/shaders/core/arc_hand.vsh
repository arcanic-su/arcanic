#version 150
out vec2 FragCoord;out vec2 TexCoord;out vec4 FragColor;
void main(){ vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);TexCoord=p;FragCoord=p;FragColor=vec4(1);gl_Position=vec4(p*2.0-1.0,0.0,1.0); }
