"""Build three standalone Windows GUI executables from already generated deltas."""
import argparse
from pathlib import Path
import shutil
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("payloads", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--release", default="0.3.4")
    args = parser.parse_args()
    tools = Path(__file__).resolve().parent
    staging = Path.cwd() / "build/patcher-exes"
    for family in ("Photon", "Complementary", "Mellow"):
        stage = staging / family
        stage.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(args.payloads / (family + ".json.gz"), stage / "payload.json.gz")
        subprocess.run([sys.executable, "-m", "PyInstaller", "--noconfirm", "--clean", "--onefile",
                        "--windowed", "--noupx", "--name", f"{family}-SLO-Patcher-{args.release}",
                        "--distpath", str(args.output.resolve()), "--workpath", str(stage / "work"),
                        "--specpath", str(stage), "--add-data", str(stage / "payload.json.gz") + ";.",
                        str(tools / "app.py")], check=True)


if __name__ == "__main__":
    main()
