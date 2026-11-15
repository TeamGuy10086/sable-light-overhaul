"""Author-side delta generation. Input packs are required, never included in payloads."""
import argparse
import base64
import difflib
import gzip
import json
from pathlib import Path
from engine import canonical, digest, load_pack, shader_hashes


def operations(before, after):
    # Line-wise copy ranges reference the user's original; unchanged upstream text
    # is never embedded. Only inserted/replacement lines travel in the patcher.
    left, right = before.splitlines(keepends=True), after.splitlines(keepends=True)
    offsets = [0]
    for line in left:
        offsets.append(offsets[-1] + len(line))
    result = []
    for tag, a, b, c, d in difflib.SequenceMatcher(None, left, right, autojunk=False).get_opcodes():
        if tag == "equal":
            result.append(["copy", offsets[a], offsets[b] - offsets[a]])
        elif tag in {"insert", "replace"}:
            result.append(["add", base64.b64encode(b"".join(right[c:d])).decode()])
    return result


def variant(label, source, target, overlay=False):
    base = load_pack(source)
    if overlay:
        patched = dict(base)
        for path in Path(target).rglob("*"):
            if path.is_file():
                patched[path.relative_to(target).as_posix()] = path.read_bytes()
    else:
        patched = load_pack(target)
    if set(base) - set(patched):
        raise ValueError("Target removed original files: " + str(set(base) - set(patched)))
    changes = {}
    for name, data in patched.items():
        # Upstream attribution and licenses must survive byte-for-byte.
        if "license" in name.lower() and name in base and data != base[name]:
            raise ValueError("License was modified: " + name)
        before, after = canonical(name, base.get(name, b"")), canonical(name, data)
        if before != after:
            changes[name] = {"before": digest(before), "after": digest(after), "ops": operations(before, after)}
    return {"label": label, "base": shader_hashes(base), "changes": changes}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("config", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    config = json.loads(args.config.read_text(encoding="utf-8"))
    args.output.mkdir(parents=True, exist_ok=True)
    for family in config["families"]:
        manifest = {k: family[k] for k in ("family", "links")}
        manifest.update(release=config["release"], mod_version=config["mod_version"], schema=1)
        manifest["variants"] = [variant(**v) for v in family["variants"]]
        raw = json.dumps(manifest, ensure_ascii=False, separators=(",", ":")).encode()
        path = args.output / (family["family"] + ".json.gz")
        path.write_bytes(gzip.compress(raw, mtime=0))
        print(f"{family['family']}: {len(manifest['variants'])} supported variant(s), {path.stat().st_size} bytes")


if __name__ == "__main__":
    main()
