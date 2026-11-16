package com.jedl.videoprogen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache

/** Required PNG piece bank. Initialization is fail-fast: no silent fallback. */
object PieceLibrary {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    @Volatile private var manifest: PieceManifest? = null
    @Volatile private var appContext: Context? = null

    @Synchronized
    fun init(context: Context) {
        if (manifest != null && appContext != null) return
        val app = context.applicationContext
        val styles = app.assets.list("pieces")
            ?: error("PNG_PIECES_REQUIRED: falta assets/pieces/")
        require(styles.contains("ritmo")) { "PNG_PIECES_REQUIRED: falta pieces/ritmo/" }
        val baseDir = "pieces/ritmo"
        val json = app.assets.open("$baseDir/manifest.json").bufferedReader().use { it.readText() }
        val parsed = PieceManifestParser.parse(json, "ritmo", baseDir)
            ?: error("PNG_PIECES_REQUIRED: manifest.json inválido")
        val expected = AvatarRenderOrder.backToFront.toSet()
        val actual = parsed.pieces.map { it.bone }
        require(actual.size == 10 && actual.toSet() == expected) {
            "PNG_PIECES_REQUIRED: el manifiesto debe declarar exactamente los 10 huesos del rig"
        }
        require(actual.distinct().size == actual.size) { "PNG_PIECES_REQUIRED: huesos duplicados" }
        require(parsed.pieces.map { it.file }.distinct().size == 10) {
            "PNG_PIECES_REQUIRED: archivos PNG duplicados"
        }
        parsed.pieces.forEach { def ->
            require(def.file.endsWith(".png", ignoreCase = true)) { "PNG_PIECES_REQUIRED: recurso no PNG: ${def.file}" }
            require(def.widthUnits.isFinite() && def.widthUnits > 0f) { "PNG_PIECES_REQUIRED: widthUnits inválido" }
            require(def.pivotX.isFinite() && def.pivotY.isFinite()) { "PNG_PIECES_REQUIRED: pivote inválido" }
            val bitmap = app.assets.open("$baseDir/${def.file}").use { BitmapFactory.decodeStream(it) }
                ?: error("PNG_PIECES_REQUIRED: PNG no decodificable: ${def.file}")
            require(bitmap.width > 0 && bitmap.height > 0) { "PNG_PIECES_REQUIRED: PNG vacío: ${def.file}" }
            cache.put(def.file, bitmap)
        }
        appContext = app
        manifest = parsed
    }

    fun isInitialized(): Boolean = manifest != null && appContext != null
    fun hasBitmapPieces(): Boolean = isInitialized()
    fun declaredPieceCount(): Int = manifest?.pieces?.size ?: 0
    fun declaredPngFilesAvailable(): Boolean = isInitialized()
    fun manifest(): PieceManifest? = manifest
    fun piece(bone: String): PieceDef? = manifest?.pieces?.firstOrNull { it.bone == bone }

    fun bitmap(def: PieceDef): Bitmap? {
        cache.get(def.file)?.let { return it }
        val app = appContext ?: error("PNG_PIECES_REQUIRED: PieceLibrary no inicializada")
        val man = manifest ?: error("PNG_PIECES_REQUIRED: manifiesto no cargado")
        val bitmap = app.assets.open("${man.baseDir}/${def.file}").use { BitmapFactory.decodeStream(it) }
            ?: error("PNG_PIECES_REQUIRED: PNG no decodificable: ${def.file}")
        cache.put(def.file, bitmap)
        return bitmap
    }
}
