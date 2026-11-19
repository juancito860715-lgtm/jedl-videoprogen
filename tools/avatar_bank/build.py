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

SLOT_BY_SOURCE = {
    ("head", "head"): "HEAD",
    ("eyes", "head"): "EYES",
    ("mouth", "head"): "MOUTH",
    ("brows", "head"): "EYEBROWS",
    ("blush", "head"): "FACIAL_HAIR",  # Optional overlay; deliberately not a default.
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
    ("hand", "foreArmL"): "FOREARM_L",
    ("hand", "foreArmR"): "FOREARM_R",
    ("shoes", "shinL"): "SHOE_L",
    ("shoes", "shinR"): "SHOE_R",
    ("hat_crown", "head"): "HAT_BACK",
    ("hat_panel", "head"): "HAT_FRONT",
    ("hat_brim", "head"): "HAT_FRONT",
}
SUPPORTED_BONES = {
    "torso", "head", "armUL", "foreArmL", "armUR", "foreArmR",
    "thighL", "shinL", "thighR", "shinR"
}
# A source part may override the general slot mapping when its semantic role
# differs from the legacy source bucket.
def get_kotlin_slot(part):
    pid = part["id"]
    if pid.startswith("forearm_skin_"):
        return "HAND_L" if pid.endswith("_L") else "HAND_R"
    key = (part["slot"], part["bone"])
    try:
        return SLOT_BY_SOURCE[key]
    except KeyError as error:
        raise ValueError(f"No CharacterLayerSlot mapping for {pid}: {key}") from error

DEFAULT_PARTS = {
    "HEAD": "head_skin",
    "EYES": "face_eyes_dots",
    "MOUTH": "face_mouth_smile",
    "EYEBROWS": "face_brows_normal",
    "HAIR_BACK": "hair_short_back",
    "HAIR_FRONT": "hair_short_front",
    "TORSO": "torso_skin",
    "SHIRT": "shirt_tee_torso",
    "PANTS": "pants_jeans_waist",
    "UPPER_ARM_L": "armU_skin_L",
    "UPPER_ARM_R": "armU_skin_R",
    "SLEEVE_L": "shirt_tee_sleeve_L",
    "SLEEVE_R": "shirt_tee_sleeve_R",
    "FOREARM_L": "forearm_skin_open_L",
    "FOREARM_R": "forearm_skin_open_R",
    "HAND_L": "forearm_skin_open_L",
    "HAND_R": "forearm_skin_open_R",
    "THIGH_L": "thigh_jeans_L",
    "THIGH_R": "thigh_jeans_R",
    "SHIN_L": "shin_jeans_L",
    "SHIN_R": "shin_jeans_R",
    "SHOE_L": "shoe_sneaker_upper_L",
    "SHOE_R": "shoe_sneaker_upper_R",
}

def build():
    PNG_DIR.mkdir(parents=True, exist_ok=True)
    parts = []
    ids = set()
    for part_id, source in sorted(P.items(), key=lambda item: (item[1]["z"], item[0])):
        if part_id in ids:
            raise ValueError(f"Duplicate part id: {part_id}")
        ids.add(part_id)
        bone = source["bone"]
        if bone not in SUPPORTED_BONES or bone not in J:
            raise ValueError(f"Unsupported rig bone for {part_id}: {bone}")
        slot = get_kotlin_slot(source)
        png_name = f"{part_id}.png"
        metrics = render_piece(source["shapes"], J[bone], PNG_DIR / png_name)
        x0, y0, x1, y1 = metrics["bounds"]
        width_units, height_units = x1-x0, y1-y0
        pivot_x = (J[bone][0]-x0)/width_units
        pivot_y = (J[bone][1]-y0)/height_units
        if not (0.0 <= pivot_x <= 1.0 and 0.0 <= pivot_y <= 1.0):
            raise ValueError(f"Pivot outside normalized image bounds for {part_id}")
        item = {
            "id": part_id, "slot": slot, "boneId": bone,
            "file": f"{ASSET_REL}/{png_name}",
            "pivotX": round(pivot_x, 6), "pivotY": round(pivot_y, 6),
            "widthUnits": round(width_units, 6),
            "heightUnits": round(height_units, 6),
            "zIndex": int(source["z"]), "offsetX": 0.0, "offsetY": 0.0,
        }
        if source.get("tint"):
            item["tintChannel"] = source["tint"]
        parts.append(item)

    by_id = {part["id"]: part for part in parts}
    defaults = {}
    for slot, part_id in DEFAULT_PARTS.items():
        if part_id not in by_id:
            raise ValueError(f"Default part does not exist: {slot} -> {part_id}")
        if by_id[part_id]["slot"] != slot:
            raise ValueError(f"Default slot mismatch: {slot} -> {part_id} ({by_id[part_id]['slot']})")
        defaults[slot] = part_id

    manifest = {"id": "jedl_master_v1", "version": 1, "parts": parts, "defaults": defaults}
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    print(f"Generated {len(parts)} PNG parts; {len(defaults)} slot defaults.")
    print(f"Manifest: {OUT / 'manifest.json'}")
    return manifest

if __name__ == "__main__":
    build()
