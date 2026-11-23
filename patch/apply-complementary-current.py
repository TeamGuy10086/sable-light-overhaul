"""Build the local SLO adapter from Complementary Unbound r5.8.1 or its Euphoria Patches 1.9.3 build."""
import argparse
from hashlib import sha512
from io import BytesIO
from pathlib import Path
import shutil
import subprocess
import tempfile
from zipfile import ZipFile
import re


PATCH = Path(__file__).resolve().parent
SOURCE = PATCH / "source"
NATIVE = PATCH.parent / "src/main/resources/assets/sablelightoverhaul/shaders/include"
# Published by Modrinth for ComplementaryUnbound_r5.8.1.zip.
BASE_SHA512 = (
    "9098dd9e0c18b80f7aba2839cea33ce9a614d97665bbfcac87ccce6e4771667c4160"
    "2d99088852cb1642ccab20b2ceff9b98af8f2e795bd0d3b90b7c9cbab914"
)


def insert_before(text, anchor, addition):
    if text.count(anchor) != 1:
        raise ValueError(f"Expected one patch anchor: {anchor}")
    return text.replace(anchor, addition + anchor, 1)


def edit(root, path, transform):
    target = root / path
    target.write_text(transform(target.read_text(encoding="utf-8")), encoding="utf-8", newline="\n")


def install_lighting_options(shaders):
    key = "SLO_COMPLEMENTARY_EMISSION"
    diffuse = "SLO_COMPLEMENTARY_DYNAMIC_LIGHT"
    tracer = "SLO_COMPLEMENTARY_TRACER_GAIN"
    signal_core = "SLO_COMPLEMENTARY_SIGNAL_CORE"
    signal_halo = "SLO_COMPLEMENTARY_SIGNAL_HALO"
    sliders = f"{key} {diffuse} {tracer} {signal_core} {signal_halo}"
    edit(shaders, "lib/common.glsl", lambda text:
         f"#define {key} 0.7 //[0.0 0.1 0.2 0.3 0.4 0.5 0.6 0.7 0.8 0.9 1.0 1.1 1.2 1.3 1.4 1.5 1.6 1.7 1.8 1.9 2.0]\n"
         f"#define {diffuse} 2.0 //[0.0 0.25 0.5 0.75 1.0 1.25 1.5 1.75 2.0 2.25 2.5 3.0 3.5 4.0]\n"
         f"#define {tracer} 6.0 //[0.0 1.0 2.0 3.0 4.0 5.0 6.0 8.0 10.0 12.0]\n"
         f"#define {signal_core} 4.0 //[0.0 0.5 1.0 1.5 2.0 3.0 4.0 6.0 8.0 12.0]\n"
         f"#define {signal_halo} 3.0 //[0.0 0.5 1.0 1.5 2.0 3.0 4.0 6.0 8.0 12.0]\n" + text)

    def settings(text):
        root = " [MATERIAL_SETTINGS] [OTHER_SETTINGS]"
        if text.count(root) != 1 or text.count("    sliders=") != 1:
            raise ValueError("Unexpected Complementary settings layout.")
        return text.replace(root, root + " [SLO_SETTINGS]").replace(
            "    sliders=", f"    sliders={sliders} ", 1
        ) + f"\nscreen.SLO_SETTINGS={sliders}\n"

    edit(shaders, "shaders.properties", settings)
    edit(shaders, "lang/en_US.lang", lambda text: text + f"""
screen.SLO_SETTINGS=Sable Light Overhaul
option.{key}=Global Emission Strength
option.{key}.comment=Scales material emission and SLO emissive effects. 1.0 restores full intensity.
option.{diffuse}=Dynamic Surface Light Strength
option.{diffuse}.comment=Scales the light SLO sources cast on surfaces. Does not affect their own emission or glow.
option.{tracer}=Tracer Emission Gain
option.{tracer}.comment=Additional gain for opaque tracer lines, applied with Global Emission Strength.
option.{signal_core}=Flare Core
option.{signal_core}.comment=Brightness of the burning point, independent of Global Emission Strength.
option.{signal_halo}=Flare Halo
option.{signal_halo}.comment=Brightness of the soft halo and horizontal streak, independent of the burning point.
""")
    zh_path = shaders / "lang/zh_CN.lang"
    zh_base = zh_path.read_text(encoding="utf-8") if zh_path.exists() else ""
    zh_path.write_text(zh_base + ("\n" if zh_base else "") + f"""screen.SLO_SETTINGS=Sable Light Overhaul
option.{key}=全局自发光强度
option.{key}.comment=调节材质和 SLO 特效的自发光。1.0 恢复原强度。
option.{diffuse}=动态表面照明强度
option.{diffuse}.comment=调节 SLO 光源照到物体表面时的亮度。自发光与辉光由各自的设置控制。
option.{tracer}=亮线自发光增益
option.{tracer}.comment=对不透明亮线额外乘以此倍率，同时服从全局自发光强度。
option.{signal_core}=信号弹／照明弹发光强度
option.{signal_core}.comment=调节两类弹的燃烧核心的亮度，独立于全局自发光。
option.{signal_halo}=信号弹／照明弹光晕强度
option.{signal_halo}.comment=调节两类弹的光晕和横向光纹，独立于燃烧核心。
""", encoding="utf-8", newline="\n")
    edit(shaders, "lib/lighting/mainLighting.glsl", lambda text: insert_before(
        text, "    vec3 finalDiffuse = ", "    emission *= SLO_COMPLEMENTARY_EMISSION;\n"))


def install_surface(shaders):
    edit(shaders, "lib/lighting/mainLighting.glsl", lambda text: insert_before(
        insert_before(text, "void DoLighting(", '#ifndef VOXY_PATCH\n#include "/lib/slo/surface.glsl"\n#endif\n\n'),
        "    finalDiffuse = sqrt(", """    #ifndef VOXY_PATCH
    if (sloLightCount > 0) {
        vec3 sloSurfaceNormal = normalize(mat3(gbufferModelViewInverse) * normalM);
        finalDiffuse += slo_surface_power(playerPos, sloSurfaceNormal, worldGeoNormal,
                                          vanillaAO, lightmap.y, color.rgb);
    }
    #elif defined VOXY_OPAQUE
        gbufferSloMaterial = slo_encode_voxy_material(color.rgb, worldGeoNormal, lightmap.y, vanillaAO);
    #endif
"""))


def install_voxy_material(shaders, euphoria):
    slot = 15 if euphoria else 14
    location = "#ifdef SS_BLOCKLIGHT\nlayout(location = 3)\n#else\nlayout(location = 2)\n#endif\n" if euphoria else "layout(location = 2) "
    edit(shaders, "program/voxy_opaque.glsl", lambda text: insert_before(
        text, "layout(location = 0) out vec4 gbufferData0;",
        '#include "/lib/slo/voxy_material.glsl"\n' + location + 'out vec4 gbufferSloMaterial;\n'))
    edit(shaders, "program/voxy.json", lambda text: insert_before(
        text, '\n    ],\n    "translucentDrawBuffers"', (f'\n        , {slot}' if euphoria else f',\n        {slot}')))
    edit(shaders, "lib/pipelineSettings.glsl", lambda text: insert_before(
        text, "const int colortex18Format", f"const int colortex{slot}Format = RGBA8; // SLO Voxy material\n"))
    if euphoria:
        edit(shaders, "lib/slo/deferred_surface.glsl", lambda text: text.replace("colortex14", "colortex15"))


def install_deferred_surface(shaders):
    bounds = "".join(
        f"    if ((sloShadowMask & (1 << {i})) != 0 && "
        f"slo_in_shadow_light(position, sloLight{i}, sloShape{i}, sloAxis{i})) return true;\n"
        for i in range(32)
    )
    edit(shaders, "lib/slo/deferred_surface.glsl", lambda text: insert_before(
        text, "vec3 slo_deferred_shadow(", """bool slo_has_shadow_light(vec3 position) {
""" + bounds + """    return false;
}

"""))

    def modify(text):
        text = insert_before(text, "#ifdef VL_CLOUDS_ACTIVE\n    #include \"/lib/atmospherics/clouds/mainClouds.glsl\"", """#define SLO_COMPLEMENTARY_SHADOW_RESOLVE
#include "/lib/slo/surface_depth.glsl"
#include "/lib/slo/surface.glsl"
#include "/lib/slo/deferred_surface.glsl"
#undef SLO_COMPLEMENTARY_SHADOW_RESOLVE

""")
        text = insert_before(text, "        waterRefColor = color.rgb;\n        DoFog(", """        if (z0 > 0.56 && sloShadowMask != 0) {
            color.rgb = slo_deferred_shadow(color.rgb, texCoord, viewPos.xyz, playerPos, texture6.b);
        }
""")
        anchor = re.search(r"(?m)^ {16}waterRefColor = color\.rgb;\n[ \t]*\n {16}DoFog\(", text)
        if anchor is None:
            raise ValueError("Missing Complementary LOD fog anchor.")
        return insert_before(text, anchor[0], """                #ifdef VOXY
                    color.rgb = slo_light_voxy(color.rgb, texelCoord, texCoord);
                #endif
""")

    edit(shaders, "program/deferred1.glsl", modify)


def install_light_kernel(shaders):
    def modify(text):
        text = text.replace("#if defined PROGRAM_DEFERRED4", "#ifdef SLO_COMPLEMENTARY_SHADOW_RESOLVE")
        text = text.replace("ivec2(gl_FragCoord.xy) & 511",
                            "ivec2(gl_FragCoord.xy) % textureSize(noisetex, 0)")
        text = text.replace("textureSize(depthtex1, 0)", "textureSize(depthtex0, 0)")
        start = text.index("        float sampled = texelFetch(depthtex1")
        end = text.index("        if (delta > SLO_SHADOW_BIAS", start)
        text = text[:start] + """        if (ray_view.z >= -0.001) break;
        float delta = -ray_view.z - slo_shadow_depth(uv);
""" + text[end:]
        text = insert_before(text, "vec3 slo_slot(", """#ifdef SLO_COMPLEMENTARY_SHADOW_RESOLVE
bool slo_shadow_only = false;
vec3 slo_shadow_lost = vec3(0.0);
float slo_shadow_pixel_footprint = 0.0;
#endif

""")
        text = insert_before(text, "        if (light.w <= 0.0) return vec3(0.0);", """#ifdef SLO_COMPLEMENTARY_SHADOW_RESOLVE
        if (slo_shadow_only && (sloShadowMask & (1 << slot)) == 0) return vec3(0.0);
#endif
""")
        text = text.replace("            if (visibility <= 0.0) return vec3(0.0);",
                            "            if (!slo_shadow_only && visibility <= 0.0) return vec3(0.0);")
        footprint = "    float selfFootprint = max(length(dFdx(scene_pos)), length(dFdy(scene_pos)));"
        text = insert_before(text, footprint, """#ifdef SLO_COMPLEMENTARY_SHADOW_RESOLVE
    float selfFootprint = slo_shadow_pixel_footprint;
#else
""").replace(footprint, footprint + "\n#endif", 1)
        text = insert_before(text, "vec3 slo_lamp_emission(", "vec3 slo_cmp_lamp_emission = vec3(0.0);\n\n")
        lamp = "return slo_lamp_emission(tint.rgb, tint.w) * max(0.15, light.w);"
        if text.count(lamp) != 1:
            raise ValueError("Unexpected SLO lamp emission path.")
        text = text.replace(lamp, """slo_cmp_lamp_emission += slo_lamp_emission(tint.rgb, tint.w) * max(0.15, light.w);
                return vec3(0.0);""")
        text = insert_before(text, "    sloFieldSkylight = skylight;", "    slo_cmp_lamp_emission = vec3(0.0);\n")
        return text.replace("        return tint.rgb * (light.w * falloff * diffuse * occlusion * visibility * scale);", """        vec3 radiance = tint.rgb * (light.w * falloff * diffuse * occlusion * scale);
#ifdef SLO_COMPLEMENTARY_SHADOW_RESOLVE
        if (slo_shadow_only) slo_shadow_lost += radiance * (1.0 - visibility);
#endif
        return radiance * visibility;""")

    edit(shaders, "lib/slo/light.glsl", modify)


def install_particles(shaders, euphoria):
    def modify(text):
        targets = "    /* DRAWBUFFERS:063 */"
        if text.count(targets) != 1:
            raise ValueError("Unexpected Complementary particle render targets.")
        text = text.replace(targets, "    /* RENDERTARGETS: 0,6,3,13 */", 1)
        if euphoria:
            text = text.replace("    /* RENDERTARGETS: 0,6,3,13 */", """    #if defined PHOTONICS_LIGHTING
        /* DRAWBUFFERS:063 */
    #else
        /* RENDERTARGETS: 0,6,3,11 */
    #endif""", 1)
            text = text.replace("/* DRAWBUFFERS:0639 */", "/* RENDERTARGETS: 0,6,3,9,11 */")
            text = text.replace("gl_FragData[3] = vec4(0.0, 0.0, 0.0, SSBLMask);",
                                "gl_FragData[3] = vec4(0.0, 0.0, 0.0, SSBLMask);\n        gl_FragData[4] = vec4(0.0);")

        clear_coverage = "    gl_FragData[3] = vec4(0.0);\n"
        if euphoria:
            clear_coverage = "#ifndef PHOTONICS_LIGHTING\n" + clear_coverage + "#endif\n"
        text = insert_before(text, "    gl_FragData[0] = color;", clear_coverage)
        text = insert_before(text, "flat in vec4 glColor;", """flat in int sloParticleKind;
in vec3 sloParticleLocal;
in vec3 sloParticleView;
in vec4 sloPlasmaTint;
flat in vec4 sloParticleParams;

""")
        text = insert_before(text, "#include \"/lib/util/dither.glsl\"", '#include "/lib/slo/particle_fragment.glsl"\n')
        text = insert_before(text, "    #ifdef VL_CLOUDS_ACTIVE\n        float cloudLinearDepth", """    float sloLodZ = slo_particle_lod_z(ivec2(gl_FragCoord.xy), screenPos.xy);
    float sloFragmentZ = sloParticleKind > 0 ? -sloParticleView.z : -viewPos.z;
    if (sloLodZ < sloFragmentZ) discard;

    if (sloParticleKind > 0) {
        vec4 tint = sloParticleKind == 7 ? sloPlasmaTint : glColor;
        vec4 particle = slo_particle_color(sloParticleKind, sloParticleLocal, sloParticleParams,
                                           tint, sloParticleView, screenPos.xy, texCoord, sloLodZ);
        #if MC_VERSION >= 11500
            if (sloParticleKind != 6) {
                vec3 ray = normalize(viewPos);
                float sky = 0.0;
                float alpha = particle.a;
                DoFog(particle, sky, lViewPos, playerPos, dot(ray, upVec), dot(ray, sunVec), dither, false, 0.0);
                particle.a = alpha;
            }
        #endif
        gl_FragData[0] = particle;
        gl_FragData[1] = vec4(0.0, OSIEBCA * 254.0, 0.0, 1.0);
        gl_FragData[2] = vec4(0.0);
        // RGB and source alpha follow an independent over operation on colortex13.
        float coverage = clamp(particle.a, 0.0, 1.0);
        float logDepth = log2(1.0 + length(sloParticleView));
        gl_FragData[3] = vec4(coverage, logDepth * coverage, 0.0, coverage);
        return;
    }

""")
        if euphoria:
            text = text.replace("gl_FragData[3] = vec4(coverage, logDepth * coverage, 0.0, coverage);", """#if defined SS_BLOCKLIGHT
            gl_FragData[3] = vec4(0.0);
            gl_FragData[4] = vec4(coverage, logDepth * coverage, 0.0, coverage);
        #elif !defined PHOTONICS_LIGHTING
            gl_FragData[3] = vec4(coverage, logDepth * coverage, 0.0, coverage);
        #endif""")
        text = insert_before(text, "flat out vec4 glColor;", """flat out int sloParticleKind;
out vec3 sloParticleLocal;
out vec3 sloParticleView;
out vec4 sloPlasmaTint;
flat out vec4 sloParticleParams;

""")
        return insert_before(text, "    #ifdef FLICKERING_FIX", """    int raw = int(floor(gl_MultiTexCoord1.x + 0.5)) & 65535;
    int tag = raw & 15;
    bool marked = (int(floor(gl_MultiTexCoord1.y + 0.5)) & 15) == 11;
    sloParticleKind = tag == 12 && raw > 255 ? 3 :
        (marked ? (tag == 13 ? 1 : (tag == 14 ? 2 : (tag == 15 ? 4 :
                   (tag == 11 ? 5 : (tag == 10 ? 6 : (tag == 9 ? 7 : 0)))))) : 0);
    sloParticleLocal = vec3(0.0);
    sloParticleParams = vec4(0.0);
    sloPlasmaTint = gl_Color;
    sloParticleView = (gl_ModelViewMatrix * gl_Vertex).xyz;
    vec2 uv = gl_MultiTexCoord0.xy;
    if (sloParticleKind == 3) {
        float q = float((raw >> 4) - 2048);
        sloParticleLocal = vec3(uv, sign(q) * (exp2(abs(q) / 128.0) - 1.0));
        sloParticleParams = vec4(gl_Color.r, exp2((gl_Color.g - 0.5) * 16.0),
                                 gl_Color.b, float(int(gl_MultiTexCoord1.y + 0.5) & 65535) * 64.0 / 65535.0);
        glColor = vec4(1.0, 1.0, 1.0, gl_Color.a);
    } else if (sloParticleKind == 4) {
        float seed = floor((uv.x + 1.5) / 4.0);
        sloParticleLocal = vec3(uv.x - seed * 4.0, uv.y, seed);
    } else if (sloParticleKind == 5) {
        sloParticleLocal = vec3(uv, 0.0);
    } else if (sloParticleKind == 6 && gl_Position.w > 0.0) {
        gl_Position.z = min(gl_Position.z, gl_Position.w * 0.999999);
    }

""")

    edit(shaders, "program/gbuffers_textured.glsl", modify)


def install_wave_coverage(shaders, euphoria):
    slot = 11 if euphoria else 13
    if euphoria:
        edit(shaders, "lib/slo/wave.glsl", lambda text:
             text.replace("uniform sampler2D colortex13;", "#ifndef PHOTONICS_LIGHTING\nuniform sampler2D colortex11;\n#endif").replace(
                 "    vec2 smoke = texture(colortex13, uv).rg;",
                 "#ifdef PHOTONICS_LIGHTING\n    return 0.0;\n#else\n    vec2 smoke = texture(colortex11, uv).rg;"
             ).replace("waveDistance - smokeDistance);", "waveDistance - smokeDistance);\n#endif"))
    edit(shaders, "lib/pipelineSettings.glsl", lambda text: insert_before(
        text, "const int colortex18Format", f"const int colortex{slot}Format = RG16F; // SLO smoke coverage and log depth\n" if not euphoria else "#ifndef PHOTONICS_LIGHTING\nconst int colortex11Format = RG16F;\n#endif\n"))
    edit(shaders, "shaders.properties", lambda text: text +
         f"\nblend.gbuffers_textured.colortex{slot}=ONE ONE_MINUS_SRC_ALPHA ONE ONE_MINUS_SRC_ALPHA\n")


def install_wave(shaders):
    slots = range(16)
    uniforms = "".join(f"uniform vec4 sloWaveA{i}, sloWaveB{i};\n" for i in slots)
    active = " || ".join(f"sloWaveB{i}.y > 0.0" for i in slots)
    accumulate = "".join(
        f"        slo_accumulate_wave(sloWaveA{i}, sloWaveB{i}, sloRay, sloDistance, sloPixelAngle, sloEffect, sloFirstHit);\n"
        for i in slots
    )
    body = f"""    if (z0 > 0.56 && ({active})) {{
        vec3 sloRay = normalize(mat3(gbufferModelViewInverse) * viewPos.xyz);
        float sloDistance = min(slo_scene_distance(texCoord, true), slo_cloud_distance(texCoord));
        float sloPixelAngle = 2.0 / max(abs(gbufferProjection[1][1]) * viewHeight, 1.0);
        vec4 sloEffect = vec4(0.0);
        float sloFirstHit = 1e20;
{accumulate}        color = slo_apply_wave(color, texCoord, sloEffect, sloFirstHit);
    }}

"""
    edit(shaders, "program/composite1.glsl", lambda text: insert_before(
        insert_before(text, "void main() {\n    vec3 color = texelFetch", '#include "/lib/slo/wave.glsl"\n' + uniforms + "\n"),
        "    /* TM5723:", body))


def install_clouds(shaders):
    for file, anchor, lighting in (
        ("unboundClouds.glsl", "            volumetricClouds.rgb = mix(",
         "            colorSample += slo_cloud_light(cloudPlayerPos, nPlayerPos, opacityFactor) * sloCloudSplit;"),
        ("reimaginedClouds.glsl", "            volumetricClouds.rgb = colorSample;",
         "            colorSample += slo_cloud_light(cloudPlayerPos, nPlayerPos, 1.0) * sloCloudSplit;"),
    ):
        edit(shaders, "lib/atmospherics/clouds/" + file, lambda text: insert_before(
            text, anchor, "            #ifdef SLO_CLOUD_LIGHTING\n" + lighting + "\n            #endif\n"))
    edit(shaders, "program/deferred1.glsl", lambda text: insert_before(
        insert_before(text, '#include "/lib/atmospherics/clouds/mainClouds.glsl"',
                      '#include "/lib/slo/clouds.glsl"\n    '),
        "            color = mix(color, vec4(clouds.rgb, 0.0), clouds.a);", """            if (clouds.a > 0.0 && cloudLinearDepth > 0.0 && cloudLinearDepth < 1.0) {
                vec3 sloRay = normalize(playerPos - gbufferModelViewInverse[3].xyz);
                vec3 sloCloudPosition = sloRay * (cloudLinearDepth * cloudLinearDepth * renderDistance);
                vec3 sloCloudLight = slo_cloud_light_screen(sloCloudPosition, sloRay, 1.0 - clouds.a);
                clouds.rgb += sloCloudLight / max(clouds.a, 0.001);
            }
"""))


def build(original, output, powershell):
    original, output = original.resolve(), output.resolve()
    if output.exists() or original in output.parents:
        raise ValueError("Choose a new shader pack directory outside the source.")
    euphoria = original.is_dir()
    if euphoria:
        version = original / "shaders/lib/misc/myFile.glsl"
        if (not (original / "Made for Complementary Shaders r5.8.1.txt").is_file()
            or not version.is_file()
            or not version.read_text(encoding="utf-8").startswith("// Euphoria Patches 1.9.3\n")):
            raise ValueError("Expected the generated r5.8.1 + Euphoria Patches 1.9.3 directory.")
    else:
        archive = original.read_bytes()
        if sha512(archive).hexdigest() != BASE_SHA512:
            raise ValueError("Expected the official Complementary Unbound r5.8.1 ZIP.")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="slo-complementary-", dir=output.parent) as temp:
        staging = Path(temp) / "pack"
        if euphoria:
            shutil.copytree(original, staging)
        else:
            with ZipFile(BytesIO(archive)) as source:
                source.extractall(staging)
        shaders = staging / "shaders"
        slo = shaders / "lib/slo"
        if slo.exists():
            raise ValueError("The source already contains an SLO adapter.")
        shutil.copytree(SOURCE / "complementary", slo)
        for source, name in (
            (NATIVE / "slo_gas.glsl", "gas.glsl"),
            (NATIVE / "slo_smoke.glsl", "smoke.glsl"),
            (SOURCE / "slo_wave_geometry.glsl", "wave_geometry.glsl"),
            (SOURCE / "slo_clouds.glsl", "clouds.glsl"),
            (SOURCE / "slo_light.glsl", "light.glsl"),
        ):
            shutil.copyfile(source, slo / name)
        (slo / "surface.glsl").write_text("""#ifndef INCLUDE_LIGHTING_SLO_LIGHT
const float blocklight_scale = 1.0;
const float emission_scale = SLO_COMPLEMENTARY_EMISSION;
#define time_noon noonFactor
float cube(float value) { return value * value * value; }
#include "/lib/slo/light.glsl"
#undef time_noon

// Complementary takes the square root after accumulating diffuse light.
// SLO light is linear already. Only the lamp surface emission is an amplitude.
vec3 slo_surface_power(vec3 position, vec3 normal, vec3 flatNormal, float ao, float skylight, vec3 albedo) {
    vec3 light = get_slo_lighting(position, normal, flatNormal, ao, skylight, albedo);
    return max(light, vec3(0.0)) * SLO_COMPLEMENTARY_DYNAMIC_LIGHT + pow2(slo_cmp_lamp_emission);
}
#endif
""", encoding="utf-8", newline="\n")
        install_lighting_options(shaders)
        install_surface(shaders)
        install_light_kernel(shaders)
        install_voxy_material(shaders, euphoria)
        install_deferred_surface(shaders)
        install_particles(shaders, euphoria)
        install_wave_coverage(shaders, euphoria)
        install_wave(shaders)
        install_clouds(shaders)
        subprocess.run([powershell, "-NoProfile", "-File", str(PATCH / "apply-spotlights.ps1"),
                        "-OutShaders", str(shaders), "-Complementary"], check=True)
        edit(shaders, "lib/common.glsl", lambda text: """// Iris records boolean options only when the define is referenced.
#define SLO_COMPLEMENTARY_BRIDGE
#ifdef SLO_COMPLEMENTARY_BRIDGE
const int sloComplementaryBridgeVersion = 1;
#endif

""" + text)
        (staging / "SLO-credits.txt").write_text(
            "SLO adapter for Complementary Unbound by Complementary Development (EminGT)\n"
            "https://www.complementary.dev/shaders/\n"
            "Local Sable Light Overhaul compatibility patch.\n" +
            ("Euphoria Patches by SpacEagle17. https://euphoriapatches.com/\n" if euphoria else ""),
            encoding="utf-8",
        )
        staging.rename(output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--powershell", default="pwsh")
    args = parser.parse_args()
    build(args.source, args.output, args.powershell)
    print(args.output.resolve())


if __name__ == "__main__":
    main()
