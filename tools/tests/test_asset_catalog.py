import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from asset_pack import CatalogError, validate_catalog


class AssetCatalogTests(unittest.TestCase):
    def valid_pack(self):
        return {
            "id": "kenney-example",
            "version": "1.0.0",
            "category": "characters",
            "source_url": "https://kenney.nl/assets/example",
            "download_url": "https://example.org/example.zip",
            "sha256": "a" * 64,
            "license_id": "CC0-1.0",
            "license_url": "https://creativecommons.org/publicdomain/zero/1.0/",
            "commercial_use_allowed": True,
            "modification_allowed": True,
            "redistribution_allowed": True,
            "redistribution_scope": "offline-app-pack",
            "attribution": ""
        }

    def test_accepts_pack_with_explicit_redistribution_rights(self):
        catalog = {"schema_version": 1, "packs": [self.valid_pack()]}
        self.assertEqual(validate_catalog(catalog), ["kenney-example"])

    def test_rejects_pack_without_redistribution_permission(self):
        pack = self.valid_pack()
        pack["redistribution_allowed"] = False
        with self.assertRaises(CatalogError):
            validate_catalog({"schema_version": 1, "packs": [pack]})

    def test_rejects_commercial_use_not_confirmed(self):
        pack = self.valid_pack()
        pack["commercial_use_allowed"] = False
        with self.assertRaises(CatalogError):
            validate_catalog({"schema_version": 1, "packs": [pack]})

    def test_rejects_unpinned_or_invalid_checksum(self):
        pack = self.valid_pack()
        pack["sha256"] = "not-a-hash"
        with self.assertRaises(CatalogError):
            validate_catalog({"schema_version": 1, "packs": [pack]})

    def test_rejects_duplicate_pack_ids(self):
        pack = self.valid_pack()
        with self.assertRaises(CatalogError):
            validate_catalog({"schema_version": 1, "packs": [pack, dict(pack)]})

    def test_rejects_non_https_download(self):
        pack = self.valid_pack()
        pack["download_url"] = "http://example.org/example.zip"
        with self.assertRaises(CatalogError):
            validate_catalog({"schema_version": 1, "packs": [pack]})


if __name__ == "__main__":
    unittest.main()
