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
        cls.manifest = avatar_build.build()
        cls.output = HERE / "out"

    def test_exactly_forty_unique_png_parts_are_generated(self):
        self.assertEqual(len(self.manifest["parts"]), 40)
        ids = [part["id"] for part in self.manifest["parts"]]
        self.assertEqual(len(ids), len(set(ids)))
        pngs = list((self.output / "png").glob("*.png"))
        self.assertEqual(len(pngs), 40)

    def test_manifest_slots_match_real_kotlin_enum(self):
        allowed = {
            "HEAD", "EAR_L", "EAR_R", "EYES", "EYEBROWS", "NOSE", "MOUTH",
            "FACIAL_HAIR", "HAIR_BACK", "HAIR_FRONT", "HAT_BACK", "HAT_FRONT",
            "NECK", "TORSO", "SHIRT", "PANTS", "UPPER_ARM_L", "UPPER_ARM_R",
            "SLEEVE_L", "SLEEVE_R", "FOREARM_L", "FOREARM_R", "HAND_L", "HAND_R",
            "THIGH_L", "THIGH_R", "SHIN_L", "SHIN_R", "FOOT_L", "FOOT_R",
            "SHOE_L", "SHOE_R", "GLASSES", "CHAIN", "MEDAL", "WATCH",
            "STICK_L", "STICK_R", "DRUM", "DRUM_HEAD", "DRUM_STAND"
        }
        self.assertTrue({p["slot"] for p in self.manifest["parts"]} <= allowed)
        by_id = {p["id"]: p for p in self.manifest["parts"]}
        for slot, part_id in self.manifest["defaults"].items():
            self.assertIn(slot, allowed)
            self.assertEqual(by_id[part_id]["slot"], slot)

    def test_every_png_path_pivot_and_bone_is_valid(self):
        for part in self.manifest["parts"]:
            with self.subTest(part=part["id"]):
                path = self.output / part["file"]
                self.assertTrue(path.is_file())
                self.assertGreater(path.stat().st_size, 0)
                self.assertGreater(part["widthUnits"], 0)
                self.assertGreater(part["heightUnits"], 0)
                self.assertGreaterEqual(part["pivotX"], 0)
                self.assertLessEqual(part["pivotX"], 1)
                self.assertGreaterEqual(part["pivotY"], 0)
                self.assertLessEqual(part["pivotY"], 1)
                self.assertIn(part["boneId"], avatar_build.SUPPORTED_BONES)

if __name__ == "__main__":
    unittest.main()
