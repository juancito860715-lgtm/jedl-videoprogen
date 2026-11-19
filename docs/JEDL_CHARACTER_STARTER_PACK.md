# JEDL Character Master — paquete inicial modular

Este paquete es una primera figura vectorial editable dibujada con Canvas nativo (sin bitmaps ni dependencias externas). Cada entrada del manifiesto es una pieza independiente y se dibuja con la matriz del hueso de PuppetRig.HUMANOID.

- Manifiesto: app/src/main/assets/characters/jedl_master_v1/manifest.json
- Pintor vectorial de piezas: CharacterPartPainter.kt
- Runtime/validador: CharacterLayerRuntime.kt
- Rig y motor de movimiento existentes: PuppetEngine.kt

Para sustituir una pieza por arte propio, añade un PNG RGBA en esta carpeta y cambia el campo file a su ruta relativa. Mantén slot, boneId, pivote y unidades para conservar el rig. Los IDs builtin:// usan el arte vectorial de reserva.

El pack incluye cabeza, orejas, ojos, cejas, nariz, boca, cabello trasero/frontal, gorra, cuello, torso, camiseta, pantalón, brazos, mangas, antebrazos, manos, muslos, espinillas, zapatos, gafas, cadena, medalla, reloj, baquetas, tambor y soporte.

Nota: es un prototipo funcional de estructura y animación, no el arte final de Juancito. Las formas se pueden reemplazar individualmente sin alterar las plantillas de animación.
