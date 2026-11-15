from argparse import ArgumentParser
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

GUARD = "#if defined WORLD_OVERWORLD && defined PROGRAM_SHADOW_FALLBACK"
VERTEX = """
    vec3 shadow_clip_pos = project_ortho(gl_ProjectionMatrix, pos);
    shadow_clip_pos = distort_shadow_space(shadow_clip_pos);
"""
RECONSTRUCT = """
    vec2 q2 = slo_h_warped_xy * slo_h_warped_xy;
    float warped_radius = sqrt(sqrt(dot(q2, q2)));
    vec2 unwarped_xy = slo_h_warped_xy * (1.0 - SHADOW_DISTORTION) /
        max(1.0 - SHADOW_DISTORTION * warped_radius, 1e-5);
    vec3 plane = cross(dFdx(slo_h_unwarped), dFdy(slo_h_unwarped));
    float plane_length = max(length(plane), 1e-20);
    gl_FragDepth = gl_FragCoord.z;
    if (abs(plane.z) > 1e-4 * plane_length) {
        float depth = slo_h_unwarped.z -
            dot(plane.xy, unwarped_xy - slo_h_unwarped.xy) / plane.z;
        gl_FragDepth = clamp(depth * (0.5 * SHADOW_DEPTH_SCALE) + 0.5, 0.0, 1.0);
    }
"""


def replace_once(source, anchor, replacement):
    if source.count(anchor) != 1:
        raise ValueError(f"Unexpected shadow program anchor: {anchor.strip()}")
    return source.replace(anchor, replacement)


def patch(name, raw):
    newline = "\r\n" if b"\r\n" in raw else "\n"
    source = raw.decode("utf-8").replace("\r\n", "\n")
    if "slo_h_unwarped" in source or "#if defined COLORWHEEL" in source:
        raise ValueError(f"Expected the old unpatched shadow program: {name}")
    direction = "out" if name.endswith(".vsh") else "in"
    uv = f"{direction} vec2 uv;\n"
    declarations = (
        f"{GUARD}\n"
        f"noperspective {direction} vec3 slo_h_unwarped;\n"
        f"noperspective {direction} vec2 slo_h_warped_xy;\n"
        "#endif\n"
    )
    source = replace_once(source, uv, uv + declarations)
    if direction == "out":
        projection, warp = VERTEX.strip("\n").splitlines()
        source = replace_once(source, VERTEX, (
            f"\n{projection}\n{GUARD}\n"
            "    slo_h_unwarped = shadow_clip_pos;\n"
            f"#endif\n{warp}\n{GUARD}\n"
            "    slo_h_warped_xy = shadow_clip_pos.xy;\n"
            "#endif\n"
        ))
    else:
        source = replace_once(source, "void main() {\n",
                              f"void main() {{\n{GUARD}{RECONSTRUCT}#endif\n")
    return source.replace("\n", newline).encode("utf-8")


def build(source, output):
    shaders = source / "shaders"
    if (shaders / "world0/clrwl_shadow.vsh").exists():
        raise ValueError("Use the old Colorwheel fallback pack as input.")
    for ext in ("vsh", "fsh"):
        wrapper = (shaders / f"world0/shadow.{ext}").read_text(encoding="utf-8")
        for anchor in ("#define WORLD_OVERWORLD", "#define PROGRAM_SHADOW_FALLBACK",
                       f'#include "/program/shadow.{ext}"'):
            if anchor not in wrapper.splitlines():
                raise ValueError(f"Unexpected main-world shadow wrapper: {anchor}")
    replacements = {}
    for ext in ("vsh", "fsh"):
        name = f"shaders/program/shadow.{ext}"
        replacements[name] = patch(name, (source / name).read_bytes())
    output.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(output, "x", compression=ZIP_DEFLATED, compresslevel=6) as archive:
        for path in sorted(source.rglob("*")):
            if path.is_file():
                name = path.relative_to(source).as_posix()
                archive.writestr(name, replacements[name] if name in replacements else path.read_bytes())


if __name__ == "__main__":
    parser = ArgumentParser(description="Build an H-only candidate from the installed old SLO pack.")
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    if args.output.resolve().is_relative_to(args.source.resolve()):
        parser.error("The output must be outside the input folder.")
    build(args.source, args.output)
