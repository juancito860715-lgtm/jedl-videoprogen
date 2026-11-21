#!/usr/bin/env python3
"""Validate and fetch explicitly redistributable JEDL offline asset packs.

The catalog is deliberately fail-closed: commercial use, modification and
redistribution must each be affirmatively documented for every pack.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ALLOWED_LICENSES = {
    "CC0-1.0",
    "CC-BY-4.0",
    "CC-BY-SA-4.0",
    "MIT",
    "Apache-2.0",
}
REQUIRED_FIELDS = {
    "id", "version", "category", "source_url", "download_url", "sha256",
    "license_id", "license_url", "commercial_use_allowed",
    "modification_allowed", "redistribution_allowed", "redistribution_scope",
    "attribution",
}


class CatalogError(ValueError):
    pass


def _https_url(value: object, field: str, pack_id: str) -> None:
    if not isinstance(value, str):
        raise CatalogError(f"{pack_id}: {field} must be a URL string")
    parsed = urllib.parse.urlparse(value)
    if parsed.scheme != "https" or not parsed.netloc:
        raise CatalogError(f"{pack_id}: {field} must use HTTPS")


def validate_catalog(catalog: object) -> list[str]:
    if not isinstance(catalog, dict) or catalog.get("schema_version") != 1:
        raise CatalogError("catalog schema_version must be 1")
    packs = catalog.get("packs")
    if not isinstance(packs, list):
        raise CatalogError("catalog packs must be a list")

    seen: set[str] = set()
    accepted: list[str] = []
    for pack in packs:
        if not isinstance(pack, dict):
            raise CatalogError("each pack entry must be an object")
        missing = REQUIRED_FIELDS - set(pack)
        if missing:
            raise CatalogError(f"pack is missing fields: {', '.join(sorted(missing))}")

        pack_id = pack["id"]
        if not isinstance(pack_id, str) or not re.fullmatch(r"[a-z0-9][a-z0-9-]{1,62}", pack_id):
            raise CatalogError("pack id must be a lowercase slug")
        if pack_id in seen:
            raise CatalogError(f"duplicate pack id: {pack_id}")
        seen.add(pack_id)

        for field in ("source_url", "download_url", "license_url"):
            _https_url(pack[field], field, pack_id)

        if not isinstance(pack["sha256"], str) or not re.fullmatch(r"[0-9a-f]{64}", pack["sha256"]):
            raise CatalogError(f"{pack_id}: sha256 must be 64 lowercase hex characters")
        if pack["license_id"] not in ALLOWED_LICENSES:
            raise CatalogError(f"{pack_id}: unsupported or unreviewed license {pack['license_id']!r}")

        for field in ("commercial_use_allowed", "modification_allowed", "redistribution_allowed"):
            if pack[field] is not True:
                raise CatalogError(f"{pack_id}: {field} must be explicitly true")

        if pack["redistribution_scope"] != "offline-app-pack":
            raise CatalogError(f"{pack_id}: redistribution_scope must be offline-app-pack")
        if pack["license_id"] != "CC0-1.0" and not str(pack["attribution"]).strip():
            raise CatalogError(f"{pack_id}: attribution text is required for {pack['license_id']}")
        if not isinstance(pack["version"], str) or not pack["version"].strip():
            raise CatalogError(f"{pack_id}: version is required")
        if not isinstance(pack["category"], str) or not pack["category"].strip():
            raise CatalogError(f"{pack_id}: category is required")
        accepted.append(pack_id)
    return accepted


def load_catalog(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise CatalogError(f"cannot read catalog {path}: {exc}") from exc


def fetch_packs(catalog: dict, output: Path) -> list[Path]:
    output.mkdir(parents=True, exist_ok=True)
    results: list[Path] = []
    for pack in catalog["packs"]:
        pack_id = pack["id"]
        filename = f"{pack_id}-{pack['version']}.zip"
        destination = output / filename
        digest = hashlib.sha256()
        request = urllib.request.Request(
            pack["download_url"],
            headers={"User-Agent": "JEDL-Asset-Pack-CI/1.0"},
        )
        try:
            with urllib.request.urlopen(request, timeout=60) as response, destination.open("wb") as target:
                while True:
                    chunk = response.read(1024 * 1024)
                    if not chunk:
                        break
                    digest.update(chunk)
                    target.write(chunk)
        except (OSError, urllib.error.URLError) as exc:
            destination.unlink(missing_ok=True)
            raise CatalogError(f"{pack_id}: download failed: {exc}") from exc

        if digest.hexdigest() != pack["sha256"]:
            destination.unlink(missing_ok=True)
            raise CatalogError(f"{pack_id}: SHA-256 mismatch")

        # Reject broken archives and unsafe paths before publishing the file.
        import zipfile
        try:
            with zipfile.ZipFile(destination) as archive:
                for item in archive.infolist():
                    member = Path(item.filename)
                    if member.is_absolute() or ".." in member.parts:
                        raise CatalogError(f"{pack_id}: unsafe path in archive: {item.filename}")
                    if (item.external_attr >> 16) & 0o170000 == 0o120000:
                        raise CatalogError(f"{pack_id}: symlink entries are not accepted")
                if archive.testzip() is not None:
                    raise CatalogError(f"{pack_id}: archive CRC validation failed")
        except zipfile.BadZipFile as exc:
            raise CatalogError(f"{pack_id}: downloaded file is not a valid ZIP") from exc

        metadata = dict(pack)
        metadata_path = output / f"{pack_id}-{pack['version']}.license.json"
        metadata_path.write_text(json.dumps(metadata, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        results.append(destination)
    return results


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("validate", "download"))
    parser.add_argument("--catalog", default="asset-packs/catalog.json")
    parser.add_argument("--output", default="build/asset-packs")
    args = parser.parse_args()

    try:
        catalog = load_catalog(Path(args.catalog))
        ids = validate_catalog(catalog)
        print(f"Catalog valid: {len(ids)} approved pack(s)")
        if args.command == "download":
            files = fetch_packs(catalog, Path(args.output))
            print(f"Downloaded and verified {len(files)} pack archive(s)")
        return 0
    except CatalogError as exc:
        print(f"::error::{exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
