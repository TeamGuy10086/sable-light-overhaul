#version 150

#moj_import <fog.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out float vertexDistance;
out vec2 texCoord0;
out vec4 vertexColor;
out vec3 sloGasPos;
out vec3 sloViewPos;
flat out vec4 sloGasParams;
flat out int sloGas;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexDistance = fog_distance(Position, FogShape);
    sloViewPos=(ModelViewMat*vec4(Position,1.0)).xyz;
    texCoord0 = UV0;
    sloGas = (UV2.x & 15) == 12 ? 1 : ((UV2.y & 15)==11?((UV2.x & 15)==15?2:((UV2.x & 15)==11?3:0)):0);
    if ((UV2.x & 15)==10 && (UV2.y & 15)==11) {
        sloGas=4;
        if(gl_Position.w>0.0) gl_Position.z=min(gl_Position.z,gl_Position.w*0.999999);
    }
    sloGasPos = vec3(0.0);
    sloGasParams = vec4(0.0);
    if (sloGas == 1) {
        float q = float(((UV2.x & 65535) >> 4)-2048);
        sloGasPos = vec3(UV0,sign(q)*(exp2(abs(q)/128.0)-1.0));
        sloGasParams = vec4(Color.r,exp2((Color.g-0.5)*16.0),Color.b,float(UV2.y & 65535)*64.0/65535.0);
        vertexColor = vec4(1.0,1.0,1.0,Color.a);
    } else {
        if(sloGas==2) {
            float seed=floor((UV0.x+1.5)/4.0);
            sloGasPos=vec3(UV0.x-seed*4.0,UV0.y,seed);
            // Thermal smoke's red channel carries the steady opacity and alpha the lifecycle
            // factor; the lightmap sample would corrupt both, so it travels in sloGasParams and
            // the fragment shader applies it to the composed colour only.
            vec4 sloLight=texelFetch(Sampler2, UV2 / 16, 0);
            sloGasParams=vec4(sloLight.rgb,1.0);
            vertexColor=vec4(Color.rgb,Color.a*sloLight.a);
        } else {
            vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
        }
        if(sloGas==3) sloGasPos=vec3(UV0,0.0);
    }
}
