# JEDL Character Master v1.1 — Definitive layered asset contract

Status: **SPECIFICATION FROZEN — ART ASSETS NOT YET VERIFIED**

This contract defines the production source-art package for the default customizable JEDL character. It is engine-neutral: the native Android renderer remains the current runtime, while Godot/Blender/Rive may be evaluated without changing the source asset format.

## 1. Non-negotiable source format

- One independent image per drawable part; PNG RGBA, genuine alpha channel.
- sRGB, straight (unassociated) alpha, transparent pixels outside the artwork.
- No checkerboard, matte, background, baked shadow, or merged limbs.
- No sprite sheet as the only source. A packed atlas may be generated later from the individual source PNGs.
- Every part uses a tight crop plus a normalized pivot (pivotX, pivotY) in the part's own image bounds.
- Each part declares its attachment bone, draw order, semantic slot, and optional recolor masks.
- Source artwork is authored against one canonical front-facing reference canvas, recommended 1024 × 1536 px. Individual PNGs may be tightly cropped, but pivots are measured against the canonical reference.
- Runtime package target: 256–768 px on the long edge for ordinary body parts; 1024 px maximum for head/face and instrument details.

## 2. Required master parts

| Group | Required independent assets | Rig attachment / pivot |
|---|---|---|
| Base body | neck.png, torso_base.png, pelvis_base.png | torso; neck base, torso center, pelvis center |
| Head | head_base.png, ear_left.png, ear_right.png | head; neck pivot for head, ear roots |
| Face | eyes_front.png, eyes_three_quarter.png, eyebrows.png, mouth_neutral.png, mouth_smile.png, mouth_open.png, nose.png | head; facial pivots relative to head |
| Hair | hair_front.png, hair_back.png | head; scalp origin |
| Hat | cap_back.png, cap_front.png | head; crown/hat band pivot |
| Upper limbs | upper_arm_L.png, upper_arm_R.png, sleeve_L.png, sleeve_R.png | upper-arm bones; shoulder pivot |
| Lower limbs | forearm_L.png, forearm_R.png | forearm bones; elbow pivot |
| Hands | hand_L.png, hand_R.png | wrist attachment; wrist pivot |
| Clothing | shirt_front.png, shirt_back.png, pants_front.png, pants_back.png | torso/pelvis; waist and collar anchors |
| Legs | thigh_L.png, thigh_R.png, shin_L.png, shin_R.png | thigh/shin bones; hip/knee pivots |
| Shoes | shoe_L.png, shoe_R.png | shin/foot attachment; ankle pivot |
| Accessories | glasses.png, chain.png, pendant.png, optional watch | head/torso/forearm |
| Percussion props | stick_L.png, stick_R.png, snare_drum.png, drum_stand.png | wrist/hand anchors for sticks; independent instrument root |

L/R is anatomical left/right, not screen left/right. Mirroring is allowed only for parts explicitly marked mirrorSafe=true; asymmetrical hands, shoes, clothing details, and props need separate art.

## 3. Rig contract and required extension

The current rig contains 10 bones: torso, head, armUL, foreArmL, armUR, foreArmR, thighL, shinL, thighR, shinR.

That is a useful humanoid base, but it is not yet a complete percussion rig. Before percussion animation is production-ready, add:
- wristL parented to foreArmL
- wristR parented to foreArmR
- footL parented to shinL
- footR parented to shinR
- independent prop transforms stickL, stickR, and snareRoot (props are attachments, not deforming body bones).

The hands follow wrist transforms. A stick pivot belongs at the grip point, not at its center. The snare is anchored to a stable torso/scene attachment and must not inherit unintended arm rotation.

## 4. Overlap and draw order

Use explicit back-to-front order, never alphabetical order. Recommended baseline:
1. Drum stand / rear instrument supports.
2. Rear shoe, rear shin, rear thigh.
3. Rear arm and sleeve.
4. Torso back, shirt back, pelvis and pants.
5. Neck and head base.
6. Hair back, cap back.
7. Face details: ears, nose, eyes, eyebrows, mouth.
8. Glasses, chain and pendant.
9. Shirt front and torso details.
10. Front arm and sleeve.
11. Forearm and hand.
12. Sticks.
13. Front leg/shoe details and foreground drum rim where camera view requires it.

For each joint, art must overlap across the pivot by at least 8–12% of the adjoining segment length. Create front/back sleeve or limb variants where a single flat image cannot preserve occlusion during rotation.

## 5. Recolor contract

Recolor only named masks, not the whole bitmap. Example semantic channels: skin.base, skin.shadow, shirt.main, shirt.trim, pants.main, cap.crown, cap.panel, cap.bill, shoe.upper, shoe.sole, hair.base.

Each mask must be a grayscale PNG or embedded alpha mask with the same dimensions as its owning part. If a region is not independently masked, it is not independently recolorable.

## 6. Manifest contract

The catalog is metadata, not a substitute for files. Each referenced file must exist and decode as RGBA. Suggested record:

    {
      "id": "hand_r",
      "slot": "HANDS",
      "assetPath": "characters/jedl_master_v1/hands/hand_r.png",
      "boneId": "wristR",
      "pivot": { "x": 0.18, "y": 0.52 },
      "zIndex": 120,
      "view": "front",
      "mirrorSafe": false,
      "colorChannels": ["skin.base", "skin.shadow"]
    }

Validation must reject missing files, RGB/no-alpha images, non-finite or out-of-range pivots, duplicate IDs, unsafe paths, missing bone references, duplicate slot defaults, and missing recolor masks.

## 7. Definition of done for the master package

A source-art package is accepted only when:
1. Every required PNG exists and has real alpha.
2. A contact sheet shows every part individually and the assembled default character.
3. The assembled result matches the approved JEDL Character Master identity and proportions.
4. Head, shoulders, elbows, wrists, hips, knees, and ankles remain visually connected through representative poses.
5. Face, clothing, cap, and skin variants can be swapped independently.
6. Recoloring one semantic channel leaves all other pixels unchanged.
7. Percussion test pose shows both sticks correctly attached to hands and the snare stable.
8. Android preview and exported frame use identical transforms and z-order.

A code-only CI pass cannot satisfy items 2–8. They require rendered visual evidence.

## 8. Existing flattened master image

The previously reviewed master image is a flattened RGB WebP with its checkerboard baked into the pixels. It remains an art-direction reference only. It is not a valid runtime part, atlas, or transparent character layer. Do not use automatic background removal as a substitute for original clean layer artwork where limbs, facial details, or accessories overlap.