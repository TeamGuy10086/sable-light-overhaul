#version 150

#moj_import <sablelightoverhaul:slo_native_depth.glsl>

uniform sampler2D DepthSampler;
uniform vec3 GlowCentre;
uniform vec4 GlowShape;
uniform vec3 GlowColor;
uniform vec2 ScreenSize;
out vec4 fragColor;

void main() {
    vec2 centre = GlowCentre.xy * 0.5 + 0.5;
    float depth = min(texture(DepthSampler, centre).r, slo_native_projected_depth(slo_native_lod_point(centre)));
    if (GlowCentre.z > depth) discard;
    vec2 ndc = gl_FragCoord.xy / ScreenSize * 2.0 - 1.0;
    vec2 offset = ndc - GlowCentre.xy;
    vec2 scaled = offset / GlowShape.xy;
    float radius = length(offset * vec2(ScreenSize.x / ScreenSize.y, 1.0))
                   / max(GlowShape.y * GlowShape.w, 1e-6);
    float ring = GlowShape.w > 0.0 ? (0.72 * exp(-48.0 * radius * radius)
               + 0.32 * exp(-5.0 * radius)) * (1.0 - smoothstep(0.65, 1.0, radius)) : 0.0;
    vec2 axisPoint = vec2(scaled.x, scaled.y / max(GlowShape.z, 1e-6));
    float axisRadius = length(axisPoint);
    float axis = GlowShape.z > 0.0 ? (0.55 * exp(-6.0 * axisRadius * axisRadius)
               + 0.15 * exp(-18.0 * axisRadius * axisRadius))
               * (1.0 - smoothstep(0.62, 1.0, axisRadius)) : 0.0;
    float amount = ring + axis;
    if (amount <= 0.0) discard;
    fragColor = vec4(GlowColor * amount, 0.0);
}
