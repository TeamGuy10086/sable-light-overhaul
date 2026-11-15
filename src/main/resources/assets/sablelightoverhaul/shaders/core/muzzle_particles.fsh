#version 150

#moj_import <fog.glsl>
#moj_import <sablelightoverhaul:slo_gas.glsl>
#moj_import <sablelightoverhaul:slo_smoke.glsl>
#moj_import <sablelightoverhaul:slo_native_depth.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform vec2 sloNativeScreenSize;
uniform mat4 ModelViewMat;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;
in vec3 sloGasPos;
in vec3 sloViewPos;
flat in vec4 sloGasParams;
flat in int sloGas;

out vec4 fragColor;

void main() {
    float sceneDistance = 1e6;
    if (uHasLod == 1) {
        vec4 lodPoint = slo_native_lod_point(gl_FragCoord.xy / sloNativeScreenSize);
        if (abs(lodPoint.w) > 1e-8) {
            vec3 lodView = (ModelViewMat * (lodPoint / lodPoint.w)).xyz;
            if (lodView.z < 0.0) {
                sceneDistance = length(lodView);
                if (sloGas != 1 && length(sloViewPos) > sceneDistance + 0.13) discard;
            }
        }
    }
    vec4 color;
    if (sloGas == 1) color = slo_gas_volume(sloGasPos,sloGasParams,vertexColor.a,sloViewPos,sceneDistance)*ColorModulator;
    else if(sloGas==2) {
        color=slo_smoke_tinted(sloGasPos.xy,round(sloGasPos.z),vertexColor)*ColorModulator;
        // The lightmap rode in sloGasParams so it cannot corrupt the steady/lifecycle channels.
        color.rgb*=sloGasParams.rgb;
    }
    else if(sloGas==3) color=slo_ribbon_sample(sloGasPos.xy)*vertexColor*ColorModulator;
    else if(sloGas==4) color = vec4(vertexColor.rgb * ColorModulator.rgb,1.0);
    else color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if(sloGas==2 && abs(round(sloGasPos.z))>=128.0) {
        color=slo_thermal_coverage(color);
        color.rgb+=vec3(2.2,.72,.12)*sloThermalHeat*ColorModulator.rgb;
    }
    if (color.a < 0.002) {
        discard;
    }
    // Tracers have their own finite feed range, independent of terrain distance.
    fragColor = sloGas==4 ? color : linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
