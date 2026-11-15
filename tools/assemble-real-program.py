"""Assemble a shader program the way Iris inlines it (recursive #include inlining).

Why this exists: validate-photon-patch.ps1 compiles the lighting block *standalone*, with
hand-written stubs. That catches syntax errors inside the block but it cannot catch anything
that depends on where the block lands: an enclosing construct in the real file, a name the
pack already declared, a conditional that is live in one program and not another.

BOUNDARY -- this is a locator, not a gate. The expansion is naive: it has no Iris macro
environment (MC_VERSION, IS_IRIS, the pack options) and it expands includes inside dead #if
branches as well, so it reports errors in pack code that Iris' own preprocessing removes. Use it
to read the text around a driver line number (pair it with measure-flattening.py), never as a
pass/fail check. The gate is the lint plus the four glslang scenarios in validate-photon-patch.ps1.

    python tools/assemble-real-program.py <pack-shaders-dir> <entry-relative-path> <out-file>

Example:
    python tools/assemble-real-program.py \
        "D:\\...\\shaderpacks\\photon1.3b + SLO Patch D\\shaders" world0/deferred4.fsh out.frag
"""

import os
import re
import sys

INCLUDE = re.compile(r'^\s*#include\s+"([^"]+)"')


def expand(root, path, out, depth=0, stack=()):
    if depth > 64:
        raise RuntimeError("include depth exceeded at " + path)
    with open(path, encoding="utf-8", errors="replace") as handle:
        text = handle.read()
    for number, line in enumerate(text.split("\n"), 1):
        match = INCLUDE.match(line)
        if not match:
            out.append(line)
            continue
        target = match.group(1).lstrip("/")
        resolved = os.path.join(root, target.replace("/", os.sep))
        if not os.path.exists(resolved):
            out.append("// MISSING INCLUDE: " + target)
            continue
        if resolved in stack:
            out.append("// RECURSIVE INCLUDE SKIPPED: " + target)
            continue
        # Iris resolves includes textually: the include line is replaced by the file content.
        expand(root, resolved, out, depth + 1, stack + (resolved,))


def main():
    if len(sys.argv) != 4:
        print(__doc__)
        return 2
    root, entry, destination = sys.argv[1], sys.argv[2], sys.argv[3]
    out = []
    expand(root, os.path.join(root, entry.replace("/", os.sep)), out)
    with open(destination, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(out) + "\n")
    print("wrote {} ({} lines)".format(destination, len(out)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
