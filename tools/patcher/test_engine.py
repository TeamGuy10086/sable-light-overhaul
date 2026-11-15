import json
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile
from build_payloads import variant
from engine import PatchError, load_pack, patch_pack


class EngineTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.source = self.root / "原包.zip"
        with ZipFile(self.source, "w") as z:
            z.writestr("upstream/shaders/main.fsh", b"// base\nvoid main() {}\n")
            z.writestr("upstream/LICENSE", b"Original license\r\n")
        self.target = self.root / "target"
        (self.target / "shaders").mkdir(parents=True)
        (self.target / "shaders/main.fsh").write_bytes(b"// base\n// SLO\nvoid main() {}\n")
        (self.target / "LICENSE").write_bytes(b"Original license\r\n")
        self.manifest = {"release": "test", "mod_version": "test", "variants": [variant("Test 1", self.source, self.target)]}
        self.output = self.root / "新补丁.zip"

    def test_success_preserves_input_and_license(self):
        before = self.source.read_bytes()
        result = patch_pack(self.source, self.output, self.manifest)
        self.assertTrue(result["success"])
        self.assertEqual(before, self.source.read_bytes())
        out = load_pack(self.output)
        self.assertEqual(out["LICENSE"], b"Original license\r\n")
        self.assertEqual(out["shaders/main.fsh"], (self.target / "shaders/main.fsh").read_bytes())

    def test_repatch_rejected(self):
        patch_pack(self.source, self.output, self.manifest)
        with self.assertRaisesRegex(PatchError, "Already patched"):
            patch_pack(self.output, self.root / "twice.zip", self.manifest)

    def test_wrong_version(self):
        with ZipFile(self.source, "w") as z:
            z.writestr("shaders/main.fsh", b"different release")
        with self.assertRaisesRegex(PatchError, "Unsupported"):
            patch_pack(self.source, self.output, self.manifest)
        self.assertFalse(self.output.exists())

    def test_preserve_existing_output(self):
        self.output.write_bytes(b"do not overwrite")
        with self.assertRaises(PatchError):
            patch_pack(self.source, self.output, self.manifest)
        self.assertEqual(self.output.read_bytes(), b"do not overwrite")

    def test_traversal(self):
        with ZipFile(self.source, "w") as z:
            z.writestr("../shaders/main.fsh", b"bad")
        with self.assertRaisesRegex(PatchError, "Unsafe"):
            load_pack(self.source)

    def test_case_collision(self):
        with ZipFile(self.source, "w") as z:
            z.writestr("shaders/main.fsh", b"one")
            z.writestr("shaders/MAIN.fsh", b"two")
        with self.assertRaisesRegex(PatchError, "Duplicate"):
            load_pack(self.source)

    def test_corrupt_zip(self):
        self.source.write_bytes(b"not a zip")
        with self.assertRaisesRegex(PatchError, "Invalid"):
            load_pack(self.source)

    def test_tampered_payload(self):
        self.manifest["variants"][0]["changes"]["shaders/main.fsh"]["after"] = "wrong"
        with self.assertRaisesRegex(PatchError, "verification failed"):
            patch_pack(self.source, self.output, self.manifest)
        self.assertFalse(self.output.exists())

    def test_directory_line_endings(self):
        folder = self.root / "original"
        (folder / "shaders").mkdir(parents=True)
        (folder / "shaders/main.fsh").write_bytes(b"// base\r\nvoid main() {}\r\n")
        self.assertTrue(patch_pack(folder, self.output, self.manifest)["success"])


if __name__ == "__main__":
    unittest.main()
