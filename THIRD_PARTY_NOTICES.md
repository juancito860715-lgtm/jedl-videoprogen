# Third-party notices and licensing inventory

This is an attribution and inventory record, not a legal opinion or a warranty that
all upstream rights have been independently cleared.

## Bundled model files

| Component | Upstream/source | License evidence | Distribution notes |
|---|---|---|---|
| White-box CartoonGAN TFLite model (whitebox_cartoon_gan_dr.tflite) | Sayak Paul, Kaggle model spsayakpaul/cartoongan, variation tfLite/dr/1; based on the White-box CartoonGAN paper by Xinrui Wang and Jinze Yu | Kaggle model card identifies Apache 2.0. | Preserve Apache 2.0 notices and attribution. https://www.kaggle.com/models/spsayakpaul/cartoongan/tfLite/dr/1 |
| MediaPipe Selfie Multiclass model (selfie_multiclass_256x256.tflite) | Google MediaPipe model storage | Upstream model card identifies Apache 2.0. | Preserve license and attribution to Google/MediaPipe. https://huggingface.co/litert-community/MediaPipe-ImageSegmenter-Selfie-Multiclass |
| MediaPipe Pose Landmarker (pose_landmarker.task) | Google MediaPipe model storage | MediaPipe project is Apache 2.0; the exact task bundle's embedded model card/notice must be retained and checked against the downloaded binary. | Pin and record SHA-256. Do not infer model weights' grant solely from the SDK license. https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker |

## Runtime libraries declared in app/build.gradle.kts

The direct AndroidX, Jetpack Compose, Kotlin coroutines, MediaPipe Tasks Vision,
TensorFlow Lite, and Material dependencies are published under Apache-2.0 terms,
subject to each artifact's included notices and any transitive dependency notices.
ML Kit Subject Segmentation is an unbundled Google Play services API; use is governed
by Google APIs / ML Kit terms, not solely by an open-source license:
https://developers.google.com/ml-kit/terms
https://developers.google.com/ml-kit/vision/subject-segmentation/android

## Test-only dependencies

- junit:junit:4.13.2: Eclipse Public License 1.0.
- org.json:json:20231013: JSON License. This is test scope only in the current
  Gradle configuration; do not describe it as Apache/MIT.

## Project-generated avatar bank

The 42 PNG parts are generated from tools/avatar_bank/pieces.py by project code.
The manifest records each PNG's SHA-256 and byte size. This provenance statement
does not grant rights in third-party material or establish copyright ownership
in the generator source.

## Scope and release gate

- The repository now includes LICENSE (Apache-2.0) and NOTICE identifying JEDL Studio
  for original JEDL-authored material only, subject to the scope limitations in NOTICE.
- This inventory is not a legal opinion or a blanket commercial-rights certification.
- Before commercial distribution, complete a dependency/SBOM audit for all resolved
  transitive artifacts and retain upstream license/NOTICE files for bundled models.
- SDK terms, model terms, trademarks, patents, privacy obligations, and rights in
  user-created content remain separate from the project source-code license.
