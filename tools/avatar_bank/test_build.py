import json
import sys
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import build as avatar_build

class AvatarBankBuildTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        avatar_build.build()
        cls.output = HERE / "out"
        cls.manifest = json.loads((cls.output / "manifest.json").read_text(encoding="utf-8"))

    def test_manifest_uses_only_real_character_layer_slots(self):
        allowed = {
            "HEAD", "EYES", "MOUTH", "HAIR_BACK", "HAIR_FRONT", "TORSO",
            "SHIRT", "PANTS", "UPPER_ARM_L", "UPPER_ARM_R", "SLEEVE_L",
            "SLEEVE_R", "FOREARM_L", "FOREARM_R", "THIGH_L", "THIGH_R",
            "SHIN_L", "SHIN_R", "SHOE_L", "SHOE_R"
        }
        self.assertEqual(set(self.manifest["defaults"]), allowed)
        self.assertEqual({p["slot"] for p in self.manifest["parts"]}, allowed)

    def test_every_manifest_png_exists_and_has_normalized_pivot(self):
        for part in self.manifest["parts"]:
            with self.subTest(part=part["id"]):
                self.assertTrue((self.output / part["file"]).is_file())
                self.assertGreater(part["widthUnits"], 0)
                self.assertGreater(part["heightUnits"], 0)
                self.assertGreaterEqual(part["pivotX"], 0)
                self.assertLessEqual(part["pivotX"], 1)
                self.assertGreaterEqual(part["pivotY"], 0)
                self.assertLessEqual(part["pivotY"], 1)
                self.assertIn(part["boneId"], avatar_build.SUPPORTED_BONES)

    def test_part_ids_are_unique(self):
        ids = [part["id"] for part in self.manifest["parts"]]
        self.assertEqual(len(ids), len(set(ids)))

if __name__ == "__main__":
    unittest.main()
