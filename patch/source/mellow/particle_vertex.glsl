flat out int sloParticleKind;
out vec3 sloParticleLocal;
out vec3 sloParticleView;
flat out vec4 sloParticleParams;

void slo_decode_particle(inout vec4 tint, vec3 viewPosition) {
    int raw = int(floor(gl_MultiTexCoord1.x + 0.5)) & 65535;
    int tag = raw & 15;
    bool marked = (int(floor(gl_MultiTexCoord1.y + 0.5)) & 15) == 11;
    sloParticleKind = tag == 12 && raw > 255 ? 3 :
        (marked ? (tag == 13 ? 1 : (tag == 14 ? 2 : (tag == 15 ? 4 :
                   (tag == 11 ? 5 : (tag == 10 ? 6 : 0))))) : 0);
    sloParticleLocal = vec3(0.0);
    sloParticleParams = vec4(0.0);
    sloParticleView = viewPosition;
    vec2 uv = gl_MultiTexCoord0.xy;
    if (sloParticleKind == 3) {
        float q = float((raw >> 4) - 2048);
        sloParticleLocal = vec3(uv, sign(q) * (exp2(abs(q) / 128.0) - 1.0));
        sloParticleParams = vec4(tint.r, exp2((tint.g - 0.5) * 16.0), tint.b,
                                 float(int(gl_MultiTexCoord1.y + 0.5) & 65535) * 64.0 / 65535.0);
        tint.rgb = vec3(1.0);
    } else if (sloParticleKind == 4) {
        float seed = floor((uv.x + 1.5) / 4.0);
        sloParticleLocal = vec3(uv.x - seed * 4.0, uv.y, seed);
    } else if (sloParticleKind == 5) {
        sloParticleLocal = vec3(uv, 0.0);
    } else if (sloParticleKind == 6 && gl_Position.w > 0.0) {
        gl_Position.z = min(gl_Position.z, gl_Position.w * 0.999999);
    }
}
