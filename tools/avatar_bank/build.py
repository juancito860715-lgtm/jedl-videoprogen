#!/usr/bin/env python3
"""Build deterministic transparent PNG pieces and the Android layer manifest."""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from kitlib import render_piece
from pieces import P, J

OUT = HERE / "out"
PNG_DIR = OUT / "png"
ASSET_REL = "png"

# The values are the actual CharacterLayerSlot enum constants in
# CharacterLayerRuntime.kt. Slot selection is based on both source slot and bone.
SLOT_BY_SOURCE = {
    ("head", "head"): "HEAD",
    ("eyes", "head"): "EYES",
    ("mouth", "head"): "MOUTH",
    ("hair_back", "head"): "HAIR_BACK",
    ("hair_front", "head"): "HAIR_FRONT",
    ("torso", "torso"): "TORSO",
    ("shirt", "torso"): "SHIRT",
    ("shirt", "armUL"): "SLEEVE_L",
    ("shirt", "armUR"): "SLEEVE_R",
    ("pants", "torso"): "PANTS",
    ("pants", "thighL"): "THIGH_L",
    ("pants", "thighR"): "THIGH_R",
    ("pants", "shinL"): "SHIN_L",
    ("pants", "shinR"): "SHIN_R",
    ("arm_skin", "armUL"): "UPPER_ARM_L",
    ("arm_skin", "armUR"): "UPPER_ARM_R",
    # In the supplied drawing set, "hand" is the forearm segment.
    ("hand", "foreArmL"): "FOREARM_L",
    ("hand", "foreArmR"): "FOREARM_R",
    ("shoes", "shinL"): "SHOE_L",
    ("shoes", "shinR"): "SHOE_R",
}
SUPPORTED_BONES = {
    "torso", "head", "armUL", "foreArmL", "armUR", "foreArmR",
    "thighL", "shinL", "thighR", "shinR"
}

def build():
    PNG_DIR.mkdir(parents=True, exist_ok=True)
    parts = []
    defaults = {}
    seen_slots = set()
    for part_id, source in sorted(P.items(), key=lambda item: (item[1]["z"], item[0])):
        source_slot, bone = source["slot"], source["bone"]
        key = (source_slot, bone)
        if key not in SLOT_BY_SOURCE:
            raise ValueError(f"No CharacterLayerSlot mapping for {part_id}: {key}")
        if bone not in SUPPORTED_BONES or bone not in J:
            raise ValueError(f"Unsupported rig bone for {part_id}: {bone}")
        slot = SLOT_BY_SOURCE[key]
        if slot in seen_slots:
            raise ValueError(f"More than one default candidate for slot {slot}; assign variants explicitly")
        seen_slots.add(slot)

        png_name = f"{part_id}.png"
        metrics = render_piece(source["shapes"], J[bone], PNG_DIR / png_name)
        x0, y0, x1, y1 = metrics["bounds"]
        width_units, height_units = x1 - x0, y1 - y0
        pivot_x = (J[bone][0] - x0) / width_units
        pivot_y = (J[bone][1] - y0) / height_units
        if not (0.0 <= pivot_x <= 1.0 and 0.0 <= pivot_y <= 1.0):
            raise ValueError(f"Pivot outside normalized image bounds for {part_id}")

        item = {
            "id": part_id,
            "slot": slot,
            "boneId": bone,
            "file": f"{ASSET_REL}/{png_name}",
            "pivotX": round(pivot_x, 6),
            "pivotY": round(pivot_y, 6),
            "widthUnits": round(width_units, 6),
            "heightUnits": round(height_units, 6),
            "zIndex": int(source["z"]),
            "offsetX": 0.0,
            "offsetY": 0.0,
        }
        if source.get("tint"):
            item["tintChannel"] = source["tint"]
        parts.append(item)
        defaults[slot] = part_id

    required = {
        "HEAD", "EYES", "MOUTH", "HAIR_BACK", "HAIR_FRONT", "TORSO",
        "SHIRT", "PANTS", "UPPER_ARM_L", "UPPER_ARM_R", "SLEEVE_L",
        "SLEEVE_R", "FOREARM_L", "FOREARM_R", "THIGH_L", "THIGH_R",
        "SHIN_L", "SHIN_R", "SHOE_L", "SHOE_R"
    }
    missing = sorted(required - set(defaults))
    if missing:
        raise ValueError(f"Manifest defaults missing required slots: {missing}")

    manifest = {"id": "jedl_master_v1", "version": 1, "parts": parts, "defaults": defaults}
    (OUT / "manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    print(f"Generated {len(parts)} PNG parts; {len(defaults)} slot defaults.")
    print(f"Manifest: {OUT / 'manifest.json'}")

if __name__ == "__main__":
    build()
