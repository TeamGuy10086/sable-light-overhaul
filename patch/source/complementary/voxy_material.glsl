#ifndef SLO_VOXY_MATERIAL_INCLUDED
#define SLO_VOXY_MATERIAL_INCLUDED

// Voxy emits axis-aligned voxel faces. Zero alpha is reserved for no opaque receiver.
vec4 slo_encode_voxy_material(vec3 albedo, vec3 normal, float skylight, float ao) {
    vec3 axis = abs(normal);
    int face = axis.x > axis.y && axis.x > axis.z ? (normal.x < 0.0 ? 1 : 0)
             : axis.y > axis.z ? (normal.y < 0.0 ? 3 : 2)
             : (normal.z < 0.0 ? 5 : 4);
    int sky = int(round(clamp(skylight, 0.0, 1.0) * 7.0));
    int ambient = int(round(clamp(ao, 0.0, 1.0) * 4.0));
    return vec4(albedo, float(1 + face + 6 * (sky + 8 * ambient)) / 255.0);
}

vec3 slo_voxy_face_normal(int face) {
    float direction = (face & 1) == 0 ? 1.0 : -1.0;
    if (face < 2) return vec3(direction, 0.0, 0.0);
    if (face < 4) return vec3(0.0, direction, 0.0);
    return vec3(0.0, 0.0, direction);
}
#endif
