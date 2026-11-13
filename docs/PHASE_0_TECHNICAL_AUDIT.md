# JEDL Studio VideoProGen — Fase 0: auditoría técnica

Fecha de revisión: 2026-11-12  
Base auditada: `main` (`644f2c726c9949bd1a12bd0b361ea13b71d80e25`)  
Estado: auditoría estática del repositorio; no equivale a prueba en dispositivo.

## 1. Resumen ejecutivo

El proyecto contiene un prototipo funcional en varias áreas, pero aún no una estación audiovisual multipista y multicapa. Se recomienda evolución incremental: conservar los subsistemas que tienen valor y reemplazar las fronteras arquitectónicas insuficientes.

## 2. Inventario y decisión

| Subsistema | Evidencia en el repositorio | Decisión |
|---|---|---|
| Rig y poses | `PuppetEngine.kt`, `SkinnedMesh.kt`, `AvatarRenderer.kt` | Conservar; aislar del ciclo de vida de la UI y añadir pruebas |
| Biblioteca de movimientos | 20 archivos JSON/CSV en `assets/motions`, parser y retargeter | Conservar; validar todos los assets y normalizar esquema |
| Personajes por piezas | `Avatar.kt`, `PieceSet.kt`, `PieceLibrary.kt` | Conservar como base; completar banco editable |
| Clonación desde vídeo | `VideoMotionCloner.kt`, MediaPipe Pose Landmarker | Conservar como prototipo; añadir muestreo controlado, cancelación y liberación garantizada |
| Composición | `SceneRenderer.kt` | Reutilizar el renderizador, ampliar a lista ordenada de capas/objetos |
| Exportación | `Mp4Exporter.kt`, `ExportService.kt` | No declarar validada; corregir ciclo de vida, concurrencia, publicación y probar hardware |
| Voz | `TtsManager.kt` | Conservar como síntesis del sistema, no confundir con audio exportado |
| Segmentación | MediaPipe local y ML Kit unbundled | Preferir modelo local incluido; ML Kit no garantiza primer uso offline |
| UI | `MainActivity.kt` (~90 KB) | Refactorizar por pantallas/estado; no seguir agregando módulos aquí |
| Proyectos | No existe modelo/repositorio de proyecto completo | Crear núcleo versionado, guardado atómico y reapertura |
| Timeline multipista | No se identifica un modelo de pistas/clips de audio y vídeo | Construir después del núcleo de proyectos |
| Pruebas | No hay árbol `app/src/test` ni `app/src/androidTest` en main | Incorporar pruebas por módulos desde esta fase |
| Documentación/licencia | README de 18 bytes; no se identifica LICENSE | Documentar y verificar procedencia/licencias antes de distribuir |
 
## 3. Riesgos prioritarios

### P0 — seguridad y reproducibilidad
- `debug.keystore` está versionado y `app/build.gradle.kts` contiene contraseñas fijas. Es material de depuración, no debe reutilizarse como firma de publicación. Retirar secretos del código y separar configuración debug/release.
- El workflow declara `contents: write` y puede hacer `git push` desde CI al generar un keystore. El build debe ser de solo lectura y nunca modificar ramas automáticamente.
- El workflow descarga modelos durante la compilación. Eso puede ser aceptable para preparar el APK, pero debe quedar separado de la ejecución offline de la app. Los modelos deben fijarse por versión, comprobar tamaño/hash y documentar licencias.

### P1 — integridad de exportación
- `ExportService` utiliza campos estáticos globales para transportar estado y un archivo temporal fijo. Dos exportaciones o una recreación de actividad pueden cruzar datos.
- La copia a MediaStore no comprueba de forma suficiente el flujo de salida ni limpia el registro si la escritura falla.
- El servicio usa un WakeLock con duración fija; necesita cancelación y liberación inequívoca.
- El exportador del PR #1 tiene una mejora sustancial de selección de encoder y gestión de buffers, pero solo se ha demostrado compilación CI. Faltan pruebas reales en Redmi Note 10, reproducción, sincronía y medición de memoria/temperatura.

### P1 — arquitectura
- `MainActivity` contiene estado, selección de medios, edición, animación y exportación. Esto dificulta pruebas y aumenta el riesgo de regresiones.
- `SceneRenderer` actualmente representa un fondo, un actor y un texto; no existe una lista general de objetos/capas transformables.
- No existe formato serializado de proyecto ni migraciones de esquema.

### P2 — comportamiento offline y rendimiento
- MediaPipe Pose y segmentación local dependen de modelos incluidos en assets; confirmar integridad de assets en el APK final.
- ML Kit Subject Segmentation está declarado unbundled y puede necesitar descarga inicial.
- Procesamiento de imágenes usa arrays de tamaño ancho×alto; establecer límites y perfiles de resolución para evitar presión de RAM.
- La clonación limita a 150 frames, pero necesita extracción incremental de vídeo y cierre garantizado del detector incluso al fallar.

## 4. Estado del PR #1

PR #1 (`fix/stage1-mp4-exporter`) sigue abierto y no fusionado. Su CI de compilación no demuestra funcionamiento del codificador físico. Mantenerlo separado hasta probar exportación/reproducción real en el Redmi Note 10. No se debe integrar por el solo hecho de que compile.

## 5. Arquitectura objetivo acordada

- Proyecto versionado y recuperable.
- Escenas con lienzo y colección ordenada de capas independientes.
- Capas de imagen, fondo, personaje, texto y posteriormente clips audiovisuales.
- Transformaciones independientes: posición, escala, rotación, opacidad y visibilidad.
- Motor de animación separado de la UI y asociado a personajes/capas.
- Timeline multipista con clips temporizados para vídeo, voz, música, efectos y subtítulos.
- Renderizador único para preview y export, consumiendo el mismo documento de escena.
- Procesamiento local, límites adaptativos de RAM/temperatura y degradación de calidad controlada para el Redmi Note 10.

## 6. Criterios de salida de Fase 0

- Inventario y riesgos documentados.
- Componentes a conservar y a reemplazar identificados.
- Orden de construcción definido.
- No se declara ninguna función validada en hardware sin evidencia.

## 7. Siguiente fase

Fase 1: núcleo de proyectos. Implementar documento versionado, escenas, capas, transformaciones, guardado atómico, listado, apertura y eliminación segura. Después conectar el modelo al editor visual y al renderizador. El primer incremento de código no debe fingir que ya existe una interfaz multipista.
