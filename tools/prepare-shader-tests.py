"""Prepare ignored shader contract fixtures from a supported user-owned Photon pack."""
import argparse
import gzip
import json
from pathlib import Path
import sys
import tempfile

sys.path.insert(0, str(Path(__file__).resolve().parent / "patcher"))
from engine import load_pack, patch_pack, select_variant


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("original", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    manifest = json.loads(gzip.decompress((root / "tools/patcher/payloads/Photon.json.gz").read_bytes()))
    original = load_pack(args.original)
    select_variant(original, manifest)
    targets = [root / "patch/baselines/photon", root / "patch/photon"]
    for target in targets:
        if target.exists():
            raise SystemExit(f"Refusing to overwrite existing fixtures: {target}. Use a clean checkout.")
    with tempfile.TemporaryDirectory(prefix="slo-shader-tests-") as temp:
        output = Path(temp) / "patched.zip"
        patch_pack(args.original, output, manifest, print)
        patched = load_pack(output)
        for target, files in zip(targets, (original, patched)):
            for name, data in files.items():
                dest = target / name
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(data)
    print("Prepared original and patched Photon fixtures (ignored by Git).")


if __name__ == "__main__":
    main()
