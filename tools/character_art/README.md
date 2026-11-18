# JEDL Character Art — visual reference received

The owner supplied a 1536×864 character-design sheet as the visual target for JEDL Studio.

## Visual direction extracted from the reference

- Polished 2D character illustration with confident dark ink contours, warm skin shading, dimensional forms and clean highlights.
- Consistent character identity across front, profile and three-quarter views; head close-ups provide expression and facial-detail guidance.
- Black baseball cap with a red front mark, white graphic T-shirt, blue jeans, white sneakers and percussion accessories.
- Slim, balanced adult cartoon proportions; articulated arms and legs must remain readable when posed.
- Keep the character recognizable across the full-body pose, turnarounds and expression variants.

## Asset-production contract

This sheet is a visual reference, not a cutout/rig-ready sprite atlas. Do not crop the sheet into runtime body parts or use the whole sheet as a character texture. Create original, editable SVG artwork under this directory, separated into independently drawable layers aligned to the rig pivots. Rasterize those SVG parts in CI to transparent RGBA PNGs (maximum 512 px per part) under `app/src/main/assets/characters/jedl_master_v1/layers/`.

Required initial parts include head/face, ears, hair, cap, neck, torso/shirt, upper arms/sleeves, forearms/hands, thighs, shins, and shoes. The percussion sticks and timbal/drum are optional scene props and must not be fused into the body rig.

Preserve the visual direction without copying the reference sheet as a flat bitmap. Do not substitute capsule primitives or generic placeholder shapes and do not claim the art gate complete until the rasterized parts, manifest validation, and preview artifact exist.

## Current state

The visual target is now supplied. The final layered SVG/PNG artwork and avatar preview still need to be authored and validated; receipt of the reference alone does not complete the art gate.
