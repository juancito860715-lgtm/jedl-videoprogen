# JEDL Character Master — original layered artwork v1

La lámina de referencia recibida fija la dirección visual: personaje ilustrado 2D, contorno oscuro, volumen y sombreado cálido, gorra negra con emblema rojo abstracto, camiseta blanca con gráfica musical original, jeans azules y zapatillas blancas.

## Pipeline de arte

- Fuente editable: `tools/character_art/generate_parts.py`.
- El generador escribe un SVG independiente por ID en `tools/character_art/generated/`.
- Cada SVG se rasteriza a PNG RGBA transparente de 256×256 en `app/src/main/assets/characters/jedl_master_v1/layers/`.
- El manifiesto asigna cada PNG a un hueso, pivote normalizado, tamaño local, offset y orden Z.
- CI valida transparencia, dimensiones, IDs únicos, cobertura de huesos y rutas individuales; publica los SVG y `avatar-preview.png` como artifact `character-art-preview`.
- El PNG de preview es una composición de arte en pose neutra para inspección visual; la validación definitiva de matrices y articulaciones sigue correspondiendo al runtime del rig.

## Conexión al rig

El flujo de ejecución permanece:
`PuppetEngine.poseForActive()` → `PuppetRig.HUMANOID.solve()` → `AvatarRenderer.draw()` → `CharacterLayerLibrary.draw()`.

Cada PNG se dibuja usando la matriz mundial del hueso indicado por `boneId`. Los puntos de giro y dimensiones siguen expresados en el marco local normalizado; no se hornea la pose en el bitmap. La cara importada se monta sobre la capa `HEAD` en el marco normalizado de cabeza.

## Partes

El manifiesto mantiene piezas independientes para cabeza, orejas, ojos, cejas, nariz, boca, barba, pelo, gorra, cuello, torso/camiseta, pantalón, mangas, brazos, antebrazos, manos, muslos, espinillas, pies, zapatos, gafas, cadena, medalla, reloj, baquetas, tambor y soporte. Variantes de pelo, ojos y boca tienen PNG separados.

## Color y personalización

Piel, pelo, camiseta, pantalón y calzado conservan sus canales de apariencia mediante `PorterDuff.Mode.MULTIPLY`; la gorra conserva el negro y el emblema rojo de la obra original, sin recibir el tinte de camiseta. El arte no depende de SRC_IN.

## Límites de validación

La generación de SVG/PNG y el test JVM del manifiesto se consideran aprobados únicamente cuando el workflow completo termina en `success`. El preview no sustituye la prueba en Redmi Note 10: hay que comprobar alineación de articulaciones, continuidad en movimiento, memoria, montaje de cara y exportación MP4 en el dispositivo.
