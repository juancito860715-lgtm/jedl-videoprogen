# JEDL Character Master v1.1 — Integration record

## Product decision

The approved JEDL Character Master v1.1 illustration is the visual identity reference for the default character. It is not treated as a finished rig or as a set of editable body parts.

The runtime character must be assembled from independently selectable slots:

- skin and face base
- eyes, eyebrows and mouth
- hair and cap
- top and bottom clothing
- hands and shoes
- accessories

Color channels are semantic regions (for example `hat.crown`, `hat.panel`, `shoes.upper`, and `shoes.sole`), not a global tint over the complete character.

## Important source-art limitation

The approved generated master currently exists as one flattened raster illustration. Inspection of the generated file shows WebP image data with an RGB color mode and a visible checkerboard baked into the pixels; it is not a transparent PNG. It also contains no independent layers for skin, eyes, clothing, hands, or shoes.

Therefore it must not be wired into the animated renderer as if it were a transparent, recolorable sprite. Doing so would display the checkerboard and would only produce a static image. A global color filter would recolor unrelated details and damage the ink/shading.

The approved image remains the canonical art-direction reference. Before it can become the runtime default, the art must be delivered as separate RGBA layers with consistent geometry, pivots, overlaps, and color-region masks.

## Catalog contract introduced in F2.5

`CharacterCatalog.kt` introduces a slot-based catalog model independent of the current 10-bone rig:

- `CharacterSlot` identifies semantic customization slots.
- `CharacterPartDefinition` describes an asset, its slot, draw order, optional bone attachment, view, and recolorable channels.
- `CharacterCatalog` declares available parts and default selections.
- `CharacterAppearanceDocument` stores one selected part per slot and independent ARGB color channels.
- Validators reject duplicate IDs, invalid paths, mismatched slots, unknown selections, and invalid color values.

This contract is a foundation; it is not yet connected to `PieceLibrary`, `AvatarRenderer`, the editor UI, or project persistence.

## Required art package before renderer integration

1. Master body layers: head/face base, neck, torso, upper/lower arms, hands, thighs, lower legs, and shoes.
2. Replaceable face layers: eyes, eyebrows, mouth/expression set.
3. Replaceable hair and cap layers.
4. Clothing layers with separate recolorable regions.
5. Front/back overlap variants where one sprite cannot safely cover both sides of a joint.
6. Per-part pivot, attachment point, z-order, view, and color-channel metadata.
7. Genuine RGBA transparency and a manifest whose paths all exist.

## Acceptance gates

- Catalog and appearance validators pass unit tests.
- Every referenced asset exists and decodes as RGBA.
- The default appearance assembles into the approved master identity.
- Changing one slot does not alter other slots.
- Recoloring one channel does not recolor unrelated regions.
- The assembled character remains attached through representative rig poses.
- Preview and export use the same draw order and transforms.
- CI is green before requesting device validation.
- Redmi Note 10 validation remains a separate physical acceptance gate.

## Scope protection

This work is on a feature branch chained after F2.4-A. It does not modify `main`. The existing vector avatar and current renderer remain as fallback until the complete layered art package and renderer integration are verified.
