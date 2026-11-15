#include "/lib/slo/clouds.glsl"

float sloCloudHit;
vec2 sloCloudLayer = vec2(0.0);

void slo_record_cloud(inout vec4 cloud, vec3 direction, float layerWeight, bool integrated) {
    float opacity = clamp((1.0 - cloud.a) * layerWeight, 0.0, 1.0);
    if (opacity < 0.002 || sloCloudHit <= 0.0) return;
    if (sloCloudLayer.x == 0.0 || sloCloudHit < sloCloudLayer.x) sloCloudLayer.x = sloCloudHit;
    sloCloudLayer.y = 1.0 - (1.0 - sloCloudLayer.y) * (1.0 - opacity);

    vec3 position = direction * sloCloudHit;
    cloud.rgb += slo_cloud_light_screen(position, direction, cloud.a);
    if (!integrated) {
        cloud.rgb += slo_cloud_light(position, direction, 1.0 - cloud.a)
                     * (1.0 - cloud.a) * sloCloudSplit;
    }
}
