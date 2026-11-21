# JEDL Character Master — starter pack v1

Este paquete es la primera implementación funcional del prototipo modular. No pretende fijar el diseño artístico final: cada capa puede sustituirse sin cambiar el rig ni las plantillas de movimiento.

## Integración

El recorrido de render es:

`PuppetEngine.poseForActive()` → `PuppetRig.HUMANOID.solve()` → `AvatarRenderer.draw()` → `CharacterLayerLibrary.draw()` → `CharacterPartPainter`.

La misma ruta se usa desde `SceneRenderer`, por lo que el personaje modular se dibuja en la vista previa y en los cuadros renderizados/exportados. Las piezas se transforman con la matriz mundial del hueso asignado.

## Banco inicial

- 39 capas predeterminadas que cubren los 10 huesos del rig.
- Capas separadas para cabeza, orejas, ojos, cejas, nariz, boca, pelo, gorra, cuello, torso, pantalón, mangas, brazos, antebrazos, manos, muslos, espinillas, pies, zapatos, gafas, cadena, medalla, reloj, baquetas, tambor y soporte.
- Variantes adicionales de pelo corto, afro y largo; ojos cerrados; boca neutra y abierta; barba opcional.
- Orden Z explícito, pivotes normalizados, offsets y dimensiones por pieza.
- La configuración existente de piel, pelo, camiseta, pantalón, zapatos, estilo de pelo y barba se conserva mediante `CharacterLayerAppearance.fromAvatarSpec()`.

## Arte y sustitución

Las piezas iniciales se dibujan como vectores independientes en `CharacterPartPainter.kt`. Esto evita cargar texturas grandes en el Redmi Note 10 y permite probar el rig sin esperar a la ilustración final.

Para reemplazar una pieza por arte propio:
1. Añadir un PNG RGBA transparente dentro de `app/src/main/assets/characters/jedl_master_v1/`.
2. Cambiar el `file` de la pieza en `manifest.json` a su ruta PNG relativa.
3. Mantener `slot`, `boneId`, pivote, dimensiones y orden Z.
4. Para cambiar de variante, seleccionar el id alternativo de ese mismo slot.

El cargador comprueba el manifiesto y los archivos. Si falta una imagen o el manifiesto no es válido, conserva el renderer vectorial anterior como fallback; no deja el lienzo en blanco.

## Pruebas incluidas

- Contrato, validación y parsing del manifiesto.
- Cobertura de piezas dibujables.
- Asignación de cada capa a un hueso existente.
- Sustitución independiente de gorra, pelo y barba.
- Movimiento de antebrazo y pierna calculado mediante el solver matemático puro, sin depender de los stubs de Android Matrix en pruebas JVM.

La compilación/CI debe quedar verde antes de declarar esta subfase validada. La prueba final de rendimiento y aspecto requiere instalar el APK en el Redmi Note 10.
