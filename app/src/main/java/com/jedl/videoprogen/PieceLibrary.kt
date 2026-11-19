package com.jedl.videoprogen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache

// ============================================================
// F1 — BIBLIOTECA DE PIEZAS (carga + caché con desalojo explícito)
// ============================================================
// F1: sin manifests de PNG en assets => el renderer usa las
// piezas vectoriales validadas (licencia limpia, cero riesgo).
// F2: se sueltan los PNGs del banco + manifest en
// assets/pieces/<estilo>/ y ESTE archivo los sirve tal cual,
// con LruCache en KiB. El avatar activo y su vista se marcan
// no-desalojables (partición fija) cuando entre el Banco (F2).
// ============================================================
object PieceLibrary {

    // Presupuesto F1: 4 MiB (F2 lo sube a ~24 MiB con partición fija)
    private val cache = object : LruCache<String, Bitmap>(4 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    @Volatile private var manifest: PieceManifest? = null
    @Volatile private var appContext: Context? = null

    /** Carga el primer estilo con manifest válido en assets/pieces/. Idempotente. */
    fun init(context: Context) {
        if (manifest != null) return
        appContext = context.applicationContext
        manifest = try {
            val styles = context.assets.list("pieces") ?: return
            var found: PieceManifest? = null
            for (style in styles) {
                val json = context.assets.open("pieces/" + style + "/manifest.json")
                    .bufferedReader().use { it.readText() }
                found = PieceManifestParser.parse(json, style, "pieces/" + style)
                if (found != null) break
            }
            found
        } catch (e: Exception) {
            null
        }
    }

    fun hasBitmapPieces(): Boolean = manifest != null

    /** Estado de inicialización real; no fuerza la carga de recursos. */
    fun isInitialized(): Boolean = appContext != null

    /** Comprueba presencia de los archivos declarados sin decodificar bitmaps. */
    fun declaredPngFilesAvailable(): Boolean {
        val ctx = appContext ?: return false
        val man = manifest ?: return false
        if (man.pieces.isEmpty()) return false
        return man.pieces.all { def ->
            try {
                ctx.assets.open(man.baseDir + "/" + def.file).use { true }
            } catch (_: Exception) {
                false
            }
        }
    }

    fun declaredPieceCount(): Int = manifest?.pieces?.size ?: 0

    fun manifest(): PieceManifest? = manifest

    fun piece(bone: String): PieceDef? =
        manifest?.pieces?.firstOrNull { it.bone == bone }

    /** Decodifica con caché; null si el PNG falta (el renderer cae a vector). */
    fun bitmap(def: PieceDef): Bitmap? {
        cache.get(def.file)?.let { return it }
        val ctx = appContext ?: return null
        val man = manifest ?: return null
        return try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
            val bmp = ctx.assets.open(man.baseDir + "/" + def.file).use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
            cache.put(def.file, bmp)
            bmp
        } catch (e: Exception) {
            null
        }
    }
}
