#!/usr/bin/env python3
"""Verify the generated avatar bank is actually embedded in the built APK."""
import argparse
import hashlib
import json
import zipfile
from pathlib import Path, PurePosixPath

ROOT = "assets/characters/jedl_master_v1"
EXPECTED_PARTS = 42

def verify(apk_path: Path):
    errors = []
    with zipfile.ZipFile(apk_path) as apk:
        names = set(apk.namelist())
        manifest_path = f"{ROOT}/manifest.json"
        if manifest_path not in names:
            raise SystemExit(f"Missing APK asset: {manifest_path}")
        manifest_bytes = apk.read(manifest_path)
        manifest = json.loads(manifest_bytes)
        parts = manifest.get("parts")
        if not isinstance(parts, list) or len(parts) != EXPECTED_PARTS:
            errors.append(f"Expected {EXPECTED_PARTS} parts, got {len(parts) if isinstance(parts, list) else 'invalid'}")
        seen_ids, seen_files = set(), set()
        for part in parts or []:
            part_id, rel = part.get("id"), part.get("file")
            if not part_id or part_id in seen_ids:
                errors.append(f"Missing or duplicate part id: {part_id!r}")
            seen_ids.add(part_id)
            if not rel or PurePosixPath(rel).is_absolute() or ".." in PurePosixPath(rel).parts:
                errors.append(f"Unsafe asset path for {part_id}: {rel!r}")
                continue
            asset_path = f"{ROOT}/{rel}"
            if asset_path in seen_files:
                errors.append(f"Duplicate asset path: {asset_path}")
            seen_files.add(asset_path)
            if asset_path not in names:
                errors.append(f"Missing APK asset: {asset_path}")
                continue
            data = apk.read(asset_path)
            if hashlib.sha256(data).hexdigest() != part.get("sha256"):
                errors.append(f"SHA-256 mismatch: {part_id}")
            if len(data) != part.get("byteSize"):
                errors.append(f"Byte-size mismatch: {part_id}")
            if not part.get("boneId") or not part.get("slot"):
                errors.append(f"Missing rig binding: {part_id}")
        packaged = {n for n in names if n.startswith(f"{ROOT}/png/") and n.lower().endswith(".png")}
        if len(packaged) != EXPECTED_PARTS:
            errors.append(f"Expected {EXPECTED_PARTS} packaged PNGs, found {len(packaged)}")
        if packaged != seen_files:
            errors.append("APK PNG inventory does not exactly match manifest inventory")
    apk_bytes = apk_path.read_bytes()
    return {
        "apk": apk_path.name,
        "apkBytes": len(apk_bytes),
        "apkSha256": hashlib.sha256(apk_bytes).hexdigest(),
        "manifest": f"{ROOT}/manifest.json",
        "manifestSha256": hashlib.sha256(manifest_bytes).hexdigest(),
        "parts": len(parts or []),
        "packagedPngs": len(packaged),
        "hashesVerified": len(parts or []) if not errors else None,
        "errors": errors,
        "status": "PASS" if not errors else "FAIL",
    }

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = verify(args.apk)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))
    if result["status"] != "PASS":
        raise SystemExit(1)

if __name__ == "__main__":
    main()
