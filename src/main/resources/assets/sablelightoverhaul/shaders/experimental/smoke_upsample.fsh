#version 330
in vec2 uv;out vec4 color;
uniform sampler2D smokeTex,lowDepth,sceneDepth;
void main(){vec2 dim=vec2(textureSize(smokeTex,0));vec2 p=uv*dim-.5;ivec2 b=ivec2(floor(p));vec2 f=fract(p);
float d=texture(sceneDepth,uv).r;vec4 smoke=vec4(0);float sum=0;
for(int y=0;y<2;y++)for(int x=0;x<2;x++){
ivec2 q=clamp(b+ivec2(x,y),ivec2(0),ivec2(dim)-1);
float w=(x==0?1-f.x:f.x)*(y==0?1-f.y:f.y);
w*=exp(-abs(texelFetch(lowDepth,q,0).r-d)*200.0);
smoke+=texelFetch(smokeTex,q,0)*w;sum+=w;}
color=sum>1e-6?smoke/sum:vec4(0);}
