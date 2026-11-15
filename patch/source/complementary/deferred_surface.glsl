vec3 slo_receiver_position(sampler2D depthTexture, mat4 inverseProjection, vec2 uv) {
    vec2 clampedUV = clamp(uv, vec2(0.0), vec2(1.0));
    float depth = texture(depthTexture, clampedUV).r;
    vec4 point = inverseProjection * (vec4(clampedUV, depth, 1.0) * 2.0 - 1.0);
    return point.xyz / point.w;
}

vec3 slo_receiver_normal(sampler2D depthTexture, mat4 inverseProjection, vec2 uv, vec3 viewPosition) {
    vec2 pixel = 1.0 / vec2(textureSize(depthTexture, 0));
    vec3 left = viewPosition - slo_receiver_position(depthTexture, inverseProjection, uv - vec2(pixel.x, 0.0));
    vec3 right = slo_receiver_position(depthTexture, inverseProjection, uv + vec2(pixel.x, 0.0)) - viewPosition;
    vec3 down = viewPosition - slo_receiver_position(depthTexture, inverseProjection, uv - vec2(0.0, pixel.y));
    vec3 up = slo_receiver_position(depthTexture, inverseProjection, uv + vec2(0.0, pixel.y)) - viewPosition;
    vec3 dx = abs(left.z) < abs(right.z) ? left : right;
    vec3 dy = abs(down.z) < abs(up.z) ? down : up;
    vec3 normal = cross(dx, dy);
    float lengthSquared = dot(normal, normal);
    if (!(lengthSquared > 1e-10)) return vec3(0.0);
    return mat3(gbufferModelViewInverse) * normal * inversesqrt(lengthSquared);
}

bool slo_in_shadow_light(vec3 position, vec4 light, vec4 shape, vec4 axis) {
    vec3 offset = position - light.xyz;
    float range = max(abs(shape.x), 0.01);
    float tanHalf = max(axis.w, 0.0);
    return light.w > 0.0 && dot(offset, offset) <= range * range * (1.0 + tanHalf * tanHalf);
}

float slo_receiver_footprint(vec3 viewPosition) {
    return 2.0 * length(viewPosition) / max(abs(gbufferProjection[1][1]) * viewHeight, 1.0);
}

vec3 slo_deferred_shadow(vec3 color, vec2 uv, vec3 viewPosition, vec3 playerPosition, float skylight) {
    if (SLO_COMPLEMENTARY_DYNAMIC_LIGHT <= 0.0 || sloShadowMask == 0 || sloLightCount <= 0
        || !slo_has_shadow_light(playerPosition)) return color;
    vec3 normal = slo_receiver_normal(depthtex0, gbufferProjectionInverse, uv, viewPosition);
    if (dot(normal, normal) < 0.5) return color;
    // Complementary keeps lit RGB at this stage. Estimate the light lost to occlusion
    // without adding an albedo target to all opaque geometry.
    vec3 reflectance = clamp(sqrt(max(color, vec3(0.0))), 0.08, 1.0);
    slo_shadow_pixel_footprint = slo_receiver_footprint(viewPosition);
    slo_shadow_only = true;
    slo_shadow_lost = vec3(0.0);
    get_slo_lighting(playerPosition, normal, normal, 1.0, skylight, reflectance);
    vec3 lost = slo_shadow_lost;
    slo_shadow_only = false;
    vec3 delta = lost * SLO_COMPLEMENTARY_DYNAMIC_LIGHT * pow2(reflectance);
    return sqrt(max(pow2(color) - delta, pow2(color) * 0.36));
}

#ifdef VOXY
#include "/lib/slo/voxy_material.glsl"
uniform sampler2D colortex14;

vec3 slo_light_voxy(vec3 color, ivec2 pixel, vec2 uv) {
    if (sloLightCount <= 0) return color;
    vec4 material = texelFetch(colortex14, pixel, 0);
    int materialCode = int(round(material.a * 255.0)) - 1;
    if (materialCode < 0) return color;

    float opaqueDepth = texelFetch(vxDepthTexOpaque, pixel, 0).r;
    if (opaqueDepth >= 1.0) return color;
    vec4 point = vxProjInv * (vec4(uv, opaqueDepth, 1.0) * 2.0 - 1.0);
    vec3 viewPosition = point.xyz / point.w;
    vec3 playerPosition = mat3(vxModelViewInv) * viewPosition + vxModelViewInv[3].xyz;
    vec3 normal = slo_voxy_face_normal(materialCode % 6);
    int lightmap = materialCode / 6;
    float skylight = float(lightmap % 8) / 7.0;
    float ao = float(lightmap / 8) / 4.0;

    slo_shadow_pixel_footprint = slo_receiver_footprint(viewPosition);
    vec3 lightPower = slo_surface_power(playerPosition, normal, normal, ao, skylight, material.rgb);
    vec3 reflectance = material.rgb * pow2(1.0 - darknessLightFactor);
    return sqrt(pow2(max(color, vec3(0.0))) + pow2(reflectance) * lightPower);
}
#endif
