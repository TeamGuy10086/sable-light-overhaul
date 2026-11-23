#version 330
uniform sampler2D sourceDepth;
uniform int factor;
void main() {
    ivec2 base = ivec2(gl_FragCoord.xy) * factor;
    ivec2 end = min(base + ivec2(factor), textureSize(sourceDepth, 0));
    float nearest = 1.0;
    for (int y = base.y; y < end.y; y++) {
        for (int x = base.x; x < end.x; x++) {
            nearest = min(nearest, texelFetch(sourceDepth, ivec2(x, y), 0).r);
        }
    }
    gl_FragDepth = nearest;
}
