"""Build the SLO shader bridge from the locally tested Mellow v3.5 archive."""

import argparse
import hashlib
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
from zipfile import ZipFile


PATCH = Path(__file__).resolve().parent
SOURCE = PATCH / "source"
NATIVE = PATCH.parent / "src/main/resources/assets/sablelightoverhaul/shaders/include"
BASE_HASH = "b46c5460cae9afe4d5b68bf6ac398661c01278d1c50b99070931f1477ecf8899"
DIMENSIONS = ("world0", "world-1", "world1", "world_modded")
OPTIONS = {
    "SLO_MELLOW_LIGHT": ("1.0", "0.0 0.5 1.0 1.5 2.0 3.0 4.0",
                        "Dynamic Surface Light Strength", "动态表面照明强度"),
    "SLO_MELLOW_EMISSION": ("1.0", "0.0 0.25 0.5 0.75 1.0 1.5 2.0",
                           "SLO Emission Strength", "SLO 自发光强度"),
    "SLO_MELLOW_TRACER_GAIN": ("4.0", "0.0 1.0 2.0 3.0 4.0 6.0 8.0 12.0",
                              "Tracer Emission Gain", "亮线自发光增益"),
    "SLO_MELLOW_FLARE_CORE": ("3.0", "0.0 0.5 1.0 2.0 3.0 4.0 6.0 8.0",
                             "Flare Core", "信号弹／照明弹发光强度"),
    "SLO_MELLOW_FLARE_HALO": ("2.0", "0.0 0.5 1.0 2.0 3.0 4.0 6.0 8.0",
                             "Flare Halo", "信号弹／照明弹光晕强度"),
}
SHADOW_OPTION = "SLO_MELLOW_LIGHT_SHADOWS"


def replace(text, old, new):
    if text.count(old) != 1:
        raise ValueError(f"Expected one Mellow patch anchor: {old!r}")
    return text.replace(old, new, 1)


def before(text, anchor, addition):
    return replace(text, anchor, addition + anchor)


def read(path):
    return path.read_text(encoding="utf-8")


def write(path, text):
    path.write_text(text, encoding="utf-8", newline="\n")


def install_options(shaders):
    slo = shaders / "lib/slo"
    options = "\n".join(f"#define {key} {value} // [{values}]"
                        for key, (value, values, _, _) in OPTIONS.items())
    write(slo / "options.glsl", """#define SLO_MELLOW_BRIDGE
#ifdef SLO_MELLOW_BRIDGE
const int sloMellowBridgeVersion = 1;
#endif
""" + options + f"\n#define {SHADOW_OPTION} 0 // [0 1]\n")
    properties = shaders / "shaders.properties"
    text = read(properties)
    text = re.sub(r"(?m)^screen=(.*)$", r"screen=\1 [SLO_SETTINGS]", text, count=1)
    text = before(text, "sliders=", "screen.SLO_SETTINGS=" + " ".join((*OPTIONS, SHADOW_OPTION)) + "\n")
    text = replace(text, "sliders=", "sliders=" + " ".join(OPTIONS) + " ")
    text = replace(text, "particles.ordering=mixed", "particles.ordering=after")
    for dimension in DIMENSIONS:
        text = replace(text, f"program.{dimension}/composite1.enabled=false\n", "")
    for program in ("gbuffers_textured", "gbuffers_textured_lit",
                    "gbuffers_particles", "gbuffers_particles_translucent"):
        text += f"\nblend.{program}.colortex13=ONE ONE_MINUS_SRC_ALPHA ONE ONE_MINUS_SRC_ALPHA"
    text += "\nflip.deferred1.colortex12=true"
    write(properties, text + "\n")
    for file, column in (("en_US.lang", 2), ("zh_CN.lang", 3)):
        path = shaders / "lang" / file
        text = read(path) if path.exists() else ""
        labels = "\n".join(f"option.{key}={value[column]}" for key, value in OPTIONS.items())
        if column == 2:
            labels += f"\noption.{SHADOW_OPTION}=SLO Light Shadows\nvalue.{SHADOW_OPTION}.0=Off\nvalue.{SHADOW_OPTION}.1=On"
        else:
            labels += f"\noption.{SHADOW_OPTION}=SLO 光源阴影\nvalue.{SHADOW_OPTION}.0=关闭\nvalue.{SHADOW_OPTION}.1=开启"
        write(path, text + "\nscreen.SLO_SETTINGS=Sable Light Overhaul\n" + labels + "\n")


def install_surface(shaders):
    paths = ("gbuffers_terrain.fsh", "gbuffers_entities.fsh", "gbuffers_hand.fsh",
             "gbuffers_water.fsh", "gbuffers_textured_lit.fsh", "dh_terrain.fsh",
             "dh_water.fsh")
    for name in paths:
        path = shaders / "program" / name
        text = before(read(path), "vec3 tweak_lightmap(",
                      '#include "/lib/slo/options.glsl"\n#include "/lib/slo/surface.glsl"\n')
        text = before(text, "    return LightColorFinal;", """    if (sloLightCount > 0) {
        mat3 toWorld = mat3(gbufferModelViewInverse);
        LightColorFinal += slo_surface_light(PlayerPos, normalize(toWorld * PackNormal),
                                            normalize(toWorld * TBN[2]), glcolor.a,
                                            LightmapCoords.y, Albedo);
    }
""")
        write(path, text)

    path = shaders / "program/voxy_terrain.glsl"
    text = before(read(path), "layout(location = 0) out vec4 Color;", """#include "/lib/slo/voxy_material.glsl"
layout(location = 1) out vec4 sloVoxyMaterial;
layout(location = 2) out float sloVoxyDepth;
""")
    text = before(text, "    Color.xyz = TweakedLM;", """    sloVoxyMaterial = slo_encode_voxy_material(Color.rgb,
        normalize(mat3(vxModelViewInv) * TBN[2]), LightmapCoords.y, glcolor.a);
    // Perspective clip W gives forward view depth, independent of the depth-buffer convention.
    sloVoxyDepth = 1.0 / gl_FragCoord.w;
""")
    write(path, text)
    for name in ("voxy_terrain.glsl", "voxy_water.glsl"):
        path = shaders / "program" / name
        text = replace(read(path), "vec2 dCoordx = dFdx(texcoord), dCoordy = dFdy(texcoord);",
                       "vec2 dCoordx, dCoordy;")
        text = replace(text, "    map_voxy_param_to_varying(param);",
                       "    map_voxy_param_to_varying(param);\n"
                       "    dCoordx = dFdx(texcoord);\n    dCoordy = dFdy(texcoord);")
        write(path, text)
    path = shaders / "program/voxy.json"
    write(path, replace(read(path), '"opaqueDrawBuffers": [0]', '"opaqueDrawBuffers": [0, 14, 15]'))


def install_light_kernel(slo):
    text = read(SOURCE / "slo_light.glsl")
    text = text.replace("#if defined PROGRAM_DEFERRED4", "#ifdef SLO_MELLOW_SHADOW_RESOLVE")
    text = text.replace("ivec2(gl_FragCoord.xy) & 511",
                        "ivec2(gl_FragCoord.xy) % textureSize(noisetex, 0)")
    # Hard shadows use the emitter center and fixed intervals even with TAA off.
    # Keep the original single emitter sample for soft shadows.
    text = replace(text, "float slo_visibility(vec3 scene_pos, vec3 light_scene_pos,",
                   "float slo_visibility(vec3 scene_pos, vec3 normal, vec3 light_scene_pos,")
    text = before(text, "    vec3 origin_view = (gbufferModelView * vec4(scene_pos, 1.0)).xyz;", """
    scene_pos += normal * clamp(slo_shadow_pixel_footprint * 0.75, 0.04, 0.2);
""")
    start = text.index("float slo_visibility(")
    start = text.index("    float dither = texelFetch(noisetex", start)
    end = text.index("\n\n", start)
    text = text[:start] + "    const float dither = 0.5;" + text[end:]
    text = replace(text, "vec2 jitter_offset = gl_FragCoord.xy / screen_size - origin_screen.xy;",
                   "vec2 jitter_offset = slo_mellow_shadow_jitter;")
    text = before(text, "float slo_visibility(", """vec2 slo_mellow_shadow_jitter = vec2(0.0);
float slo_shadow_pixel_footprint = 0.0;

""")
    text = replace(text, "slo_visibility(scene_pos, light.xyz, light.w, softness, ceiling)",
                   "slo_visibility(scene_pos, flat_normal, light.xyz, light.w, softness, ceiling)")
    text = before(text, "    for (int i = 0; i < SLO_SHADOW_STEPS_MAX; i++) {",
                  "    float occlusion = 0.0;\n")
    start = text.index("        float sampled = texelFetch(depthtex1")
    end = text.index("\n}\n", start)
    text = text[:start] + """        if (ray_view.z >= -0.001) break;
        float delta = -ray_view.z - slo_shadow_depth(uv);
        // Allow for a pixel's depth footprint at grazing angles. Blend only the
        // uncertainty band so a tap near a threshold cannot flip the full shadow.
        float footprint = 2.0 * -ray_view.z / max(abs(gbufferProjection[1][1]) * screen_size.y, 1.0);
        float bias = SLO_SHADOW_BIAS + clamp(2.0 * footprint, 0.02, 0.20);
        float margin = max(0.025, footprint);
        float hit = smoothstep(bias, bias + margin, delta)
                  * (1.0 - smoothstep(2.0 * thickness, 2.0 * thickness + margin, delta));
        occlusion = max(occlusion, hit);
        if (occlusion >= 1.0) return 0.0;
    }

    return 1.0 - occlusion;
""" + text[end:]
    text = before(text, "vec3 slo_view_to_screen(", """bool slo_shadow_only = false;
vec3 slo_shadow_lost = vec3(0.0);

""")
    text = before(text, "        if (light.w <= 0.0) return vec3(0.0);", """#ifdef SLO_MELLOW_SHADOW_RESOLVE
        if (slo_shadow_only && (sloShadowMask & (1 << slot)) == 0) return vec3(0.0);
#endif
""")
    footprint = "    float selfFootprint = max(length(dFdx(scene_pos)), length(dFdy(scene_pos)));"
    text = replace(text, footprint, """#ifdef SLO_MELLOW_SHADOW_RESOLVE
    float selfFootprint = slo_shadow_pixel_footprint;
#else
""" + footprint + "\n#endif")
    text = before(text, "vec3 slo_lamp_emission(", "vec3 slo_mellow_lamp_emission = vec3(0.0);\n\n")
    text = replace(text, "return slo_lamp_emission(tint.rgb, tint.w) * max(0.15, light.w);",
                   "slo_mellow_lamp_emission += slo_lamp_emission(tint.rgb, tint.w) * max(0.15, light.w);\n"
                   "                return vec3(0.0);")
    text = before(text, "    sloFieldSkylight = skylight;", "    slo_mellow_lamp_emission = vec3(0.0);\n")
    text = replace(text, "            if (visibility <= 0.0) return vec3(0.0);",
                   "            if (!slo_shadow_only && visibility <= 0.0) return vec3(0.0);")
    text = replace(text, "        return tint.rgb * (light.w * falloff * diffuse * occlusion * visibility * scale);",
                   """        vec3 radiance = tint.rgb * (light.w * falloff * diffuse * occlusion * scale);
#ifdef SLO_MELLOW_SHADOW_RESOLVE
        if (slo_shadow_only) slo_shadow_lost += radiance * (1.0 - visibility);
#endif
        return radiance * visibility;""")
    write(slo / "light.glsl", text)
    path = slo / "deferred_surface.glsl"
    cases = "".join(f"    if ((sloShadowMask & (1 << {i})) != 0 && "
                    f"slo_in_shadow_light(position, sloLight{i}, sloShape{i}, sloAxis{i})) return true;\n"
                    for i in range(32))
    write(path, before(read(path), "float slo_receiver_footprint(",
                       "bool slo_has_shadow_light(vec3 position) {\n" + cases + "    return false;\n}\n\n"))


def install_particles(shaders):
    program = shaders / "program"
    path = program / "gbuffers_basic.vsh"
    text = before(read(path), "flat out vec4 glcolor_flat;", """#ifdef GBUFFERS_TEXTURED
#include "/lib/slo/particle_vertex.glsl"
#endif
""")
    text = before(text, "    glcolor_flat = glcolor;", """#ifdef GBUFFERS_TEXTURED
    slo_decode_particle(glcolor, ViewPos);
#endif
""")
    write(path, text)
    path = program / "gbuffers_textured_lit.vsh"
    text = before(read(path), "void main() {", '#include "/lib/slo/particle_vertex.glsl"\n')
    text = replace(text, "    gl_Position.xy += taaJitter * gl_Position.w;\n    #endif\n}",
                   "    gl_Position.xy += taaJitter * gl_Position.w;\n    #endif\n"
                   "    slo_decode_particle(glcolor, ViewPos);\n}")
    write(path, text)

    path = program / "gbuffers_basic.fsh"
    text = replace(read(path), "/* DRAWBUFFERS:0 */", """#ifdef GBUFFERS_TEXTURED
#include "/lib/slo/options.glsl"
uniform vec2 resolutionInv;
uniform sampler2D depthtex0, depthtex1;
uniform mat4 gbufferProjectionInverse;
#ifdef VOXY
    uniform sampler2D vxDepthTexOpaque, vxDepthTexTrans;
    uniform mat4 vxProjInv;
    #define dhDepthTex0 vxDepthTexTrans
    #define dhDepthTex1 vxDepthTexOpaque
    #define dhProjectionInverse vxProjInv
#elif defined DISTANT_HORIZONS
    uniform sampler2D dhDepthTex0, dhDepthTex1;
    uniform mat4 dhProjectionInverse;
#endif
#include "/lib/slo/particle_fragment.glsl"
/* RENDERTARGETS: 0,13 */
layout(location = 1) out vec4 sloCoverage;
#else
/* DRAWBUFFERS:0 */
#endif""")
    text = before(text, "    Color = glcolor_flat;", """#ifdef GBUFFERS_TEXTURED
    sloCoverage = vec4(0.0);
    if (sloParticleKind > 0) {
        Color = slo_render_particle(gl_FragCoord.xy * resolutionInv, texcoord, glcolor_flat);
        sloCoverage = slo_particle_coverage(Color.a);
        return;
    }
#endif
""")
    write(path, text)

    path = program / "gbuffers_textured_lit.fsh"
    text = replace(read(path), "/* DRAWBUFFERS:0 */", """uniform sampler2D depthtex0;
#ifdef VOXY
    uniform sampler2D vxDepthTexTrans;
    #define dhDepthTex0 vxDepthTexTrans
#elif defined DISTANT_HORIZONS
    uniform sampler2D dhDepthTex0;
#endif
#include "/lib/slo/particle_fragment.glsl"
/* RENDERTARGETS: 0,13 */
layout(location = 1) out vec4 sloCoverage;""")
    text = before(text, "    mat3 TBN = tbn_decode(Normal, Tangent);", """    sloCoverage = vec4(0.0);
    if (sloParticleKind > 0) {
        Color = slo_render_particle(gl_FragCoord.xy * resolutionInv, texcoord, glcolor);
        sloCoverage = slo_particle_coverage(Color.a);
        return;
    }
#ifdef SLO_MELLOW_PARTICLE_PASS
    if (-sloParticleView.z > slo_opaque_lod_z(gl_FragCoord.xy * resolutionInv)) discard;
#endif
""")
    write(path, text)
    for dimension in DIMENSIONS:
        for ext in ("fsh", "vsh"):
            src = shaders / dimension / f"gbuffers_textured_lit.{ext}"
            for name in ("gbuffers_particles", "gbuffers_particles_translucent"):
                text = before(read(src), '#include "/program/', "#define SLO_MELLOW_PARTICLE_PASS\n")
                write(src.with_name(f"{name}.{ext}"), text)


def install_clouds(text):
    text = before(text, "vec4 get_clouds_flat(", '#include "/lib/slo/cloud.glsl"\n')
    text = before(text, "    return vec4(CloudColor * Noise * DENSITY, Transmittance);",
                  "    sloCloudHit = 1000.0 / max(PlayerPosN.y, 0.001);\n")
    text = before(text, "    return vec4(Scattering, Transmittance);",
                  "    sloCloudHit = 1500.0 / max(PlayerPosN.y, 0.001);\n")
    text = before(text, "        Scattering *= CLOUD_SCATTERING * TotalTransmittance",
                  """        if (sloCloudHit <= 0.0 && 1.0 - TotalTransmittance * Transmittance > 0.02)
            sloCloudHit = Pos.y / PlayerPosN.y;
        Scattering += slo_cloud_light(PlayerPosN * (Pos.y / PlayerPosN.y), PlayerPosN, Base)
                      * sloCloudSplit;
""")
    text = before(text, "    if(Density < 1e-2) return vec4(0,0,0,1);",
                  "    sloCloudHit = Density < 1e-2 ? 0.0 : Pos.y / PlayerPosN.y;\n")
    text = before(text, "    vec4 CloudColor; vec3 OgColor = SkyColor;",
                  "    float sloLayerWeight = linstep(0, 0.2, PlayerPosN.y) * (CLOUD_OPACITY + 0.5);\n")
    text = before(text, "        SkyColor = blend_vl(SkyColor, CloudColor);",
                  "        slo_record_cloud(CloudColor, PlayerPosN, sloLayerWeight, false);\n"
                  "        sloCloudHit = 0.0;\n")
    return before(text, "    SkyColor = blend_vl(SkyColor, CloudColor);\n\n    SkyColor = mix(",
                  "    slo_record_cloud(CloudColor, PlayerPosN, sloLayerWeight, CLOUD_STYLE == 2);\n")


def install_deferred(shaders):
    path = shaders / "program/deferred1.fsh"
    text = before(read(path), "vec4 get_clouds_flat(", """#include "/lib/slo/options.glsl"
uniform float darknessLightFactor;
uniform vec2 taaJitter;
#include "/lib/slo/depth.glsl"
float slo_shadow_depth(vec2 uv) {
    ivec2 size = textureSize(depthtex1, 0);
    ivec2 pixel = clamp(ivec2(uv * vec2(size)), ivec2(0), size - 1);
    float vanilla = texelFetch(depthtex1, pixel, 0).r;
    float result = vanilla > 0.0 && vanilla < 1.0
        ? -slo_view_position(gbufferProjectionInverse, uv, vanilla).z : 1e20;
    return min(result, slo_opaque_lod_z(uv));
}
#if SLO_MELLOW_LIGHT_SHADOWS == 1
    #define SLO_MELLOW_SHADOW_RESOLVE
#endif
#include "/lib/slo/surface.glsl"
#undef SLO_MELLOW_SHADOW_RESOLVE
#include "/lib/slo/deferred_surface.glsl"
""")
    text = install_clouds(text)
    text = replace(text, "/* DRAWBUFFERS:0 */", """
// Like Mellow's other format directives, keep these visible to Iris's line parser.
/*
const int colortex12Format = RGBA32F;
const int colortex13Format = RG16F;
const int colortex14Format = RGBA8;
const int colortex15Format = R32F;
*/
// Iris parses these directives before compiling GLSL and expects four explicit values.
const vec4 colortex12ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex13ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex14ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex15ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
/* RENDERTARGETS: 0,12 */
layout(location = 1) out vec4 sloDepthData;""")
    text = before(text, "        Color.rgb = get_fog_main(ScreenPos, PlayerPos,", """        if (Depth < 1.0) {
            #ifdef VOXY
            if (IsDH) {
                Color.rgb = slo_light_voxy(Color.rgb, ivec2(gl_FragCoord.xy), texcoord);
            }
            #endif
            #if SLO_MELLOW_LIGHT_SHADOWS == 1
                #ifdef VOXY
                if (!IsDH)
                #endif
            {
                Color.rgb = slo_deferred_shadow(Color.rgb, texcoord, ViewPos, view_player(ViewPos, IsDH), IsDH);
            }
            #endif
        }
""")
    text = text.rstrip()
    text = text[:-1] + """    sloDepthData.zw = vec2(0.0, 1.0);
    #ifdef DIMENSION_OVERWORLD
        sloDepthData.xy = sloCloudLayer;
    #else
        sloDepthData.xy = vec2(0.0);
    #endif
}
"""
    write(path, text)


def install_composite(shaders, powershell):
    path = shaders / "program/composite1.fsh"
    write(path, '#include "/lib/slo/options.glsl"\n' + read(SOURCE / "mellow/composite.fsh"))
    subprocess.run([powershell, "-NoProfile", "-File", str(PATCH / "apply-spotlights.ps1"),
                    "-OutShaders", str(shaders), "-Mellow"], check=True)
    slots = range(16)
    declarations = "".join(f"uniform vec4 sloWaveA{i}, sloWaveB{i};\n" for i in slots)
    active = " || ".join(f"sloWaveB{i}.y > 0.0" for i in slots)
    samples = "".join(
        f"        slo_accumulate_wave(sloWaveA{i}, sloWaveB{i}, sloRay, sloDistance, sloPixelAngle, sloEffect, sloHit);\n"
        for i in slots
    )
    text = before(read(path), "void main() {",
                  '#define noonFactor dayStrength\n#include "/lib/slo/wave.glsl"\n' + declarations)
    text = replace(text, "    Color = texture(colortex0, texcoord);", f"""    Color = texture(colortex0, texcoord);
    if (texture(depthtex0, texcoord).r > 0.56 && ({active})) {{
        vec3 sloRay = slo_world_ray(texcoord);
        float sloDistance = min(slo_scene_distance(texcoord, false), slo_cloud_distance(texcoord));
        float sloPixelAngle = 2.0 / max(abs(gbufferProjection[1][1]) * textureSize(colortex0, 0).y, 1.0);
        vec4 sloEffect = vec4(0.0);
        float sloHit = 1e20;
{samples}        Color.rgb = slo_apply_wave(Color.rgb, texcoord, sloEffect, sloHit);
    }}""")
    write(path, text)
    # Mellow extrapolates its bloom mix when the HDR luminance exceeds the blend range.
    # Keep the original curve through ordinary highlights and bound the blend for SLO effects.
    path = shaders / "program/composite7.fsh"
    write(path, replace(read(path), "mix(Color.rgb, FinalBloom, BloomFactor * BLOOM_STRENGTH)",
                        "mix(Color.rgb, FinalBloom, clamp(BloomFactor * BLOOM_STRENGTH, 0.0, 1.0))"))


def build(original, output, powershell):
    original, output = original.resolve(), output.resolve()
    if output.exists():
        raise FileExistsError(output)
    if hashlib.sha256(original.read_bytes()).hexdigest() != BASE_HASH:
        raise ValueError("Expected the Mellow v3.5 ZIP used to build this adapter.")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="slo-mellow-", dir=output.parent) as directory:
        stage = Path(directory) / "pack"
        with ZipFile(original) as archive:
            archive.extractall(stage)
        shaders = stage / "shaders"
        slo = shaders / "lib/slo"
        slo.mkdir()
        for name in ("depth.glsl", "surface.glsl", "deferred_surface.glsl", "cloud.glsl",
                     "particle_vertex.glsl", "particle_fragment.glsl"):
            shutil.copyfile(SOURCE / "mellow" / name, slo / name)
        for name in ("gas", "smoke"):
            shutil.copyfile(NATIVE / f"slo_{name}.glsl", slo / f"{name}.glsl")
        for name in ("wave_geometry", "clouds"):
            shutil.copyfile(SOURCE / f"slo_{name}.glsl", slo / f"{name}.glsl")
        for name in ("wave.glsl", "voxy_material.glsl"):
            shutil.copyfile(SOURCE / "complementary" / name, slo / name)
        install_options(shaders)
        install_light_kernel(slo)
        install_surface(shaders)
        install_particles(shaders)
        install_deferred(shaders)
        install_composite(shaders, powershell)
        write(stage / "SLO-credits.txt", "Mellow v3.5 by TheCMK. See LICENSE and LICENSE-APACHE.\n"
                                        "Sable Light Overhaul compatibility patch.\n")
        stage.rename(output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--powershell", default="pwsh")
    args = parser.parse_args()
    build(args.source, args.output, args.powershell)


if __name__ == "__main__":
    main()
