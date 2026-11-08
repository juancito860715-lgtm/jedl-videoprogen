package com.jedl.videoprogen

import org.json.JSONObject

// ============================================================
// F1 — CONTRATO CONGELADO DE PIEZAS (manifest.json)
// ============================================================
// El manifest define el arte del banco de avatares: pieza ->
// hueso -> pivote -> tinte. CONGELADO EN F1: cambiar el arte
// (CC0, IA, dibujado) es reemplazar PNGs + manifest, SIN tocar
// Kotlin. F3 añade solo el campo OPCIONAL "view" (default
// "perfil") y "faceRect" (frente), sin romper el schema.
//
// Coordenadas: el pivote es la articulación del hueso, en
// píxeles del PNG; el PNG se dibuja en el espacio local del
// hueso escalado por widthUnits/bitmap.width.
// ============================================================

data class PieceDef(
    val bone: String,        // hueso del rig: torso, head, armUL, armUR, foreArmL, foreArmR, thighL, thighR, shinL, shinR
    val file: String,        // PNG relativo al manifest ("torso.png")
    val pivotX: Float,       // pivote X en píxeles del PNG (la articulación)
    val pivotY: Float,       // pivote Y en píxeles del PNG
    val widthUnits: Float,   // ancho del PNG en unidades de muñeco
    val tint: String         // skin | hair | shirt | pants | shoes | none
)

data class FaceRect(
    val x: Float, val y: Float,  // esquina superior izquierda, espacio local del hueso head
    val w: Float, val h: Float
)

data class PieceManifest(
    val style: String,            // nombre del estilo del banco
    val baseDir: String,          // carpeta de assets ("pieces/ritmo")
    val view: String,             // "perfil" (F1) | "frente" | "espalda" (F3)
    val faceRect: FaceRect?,      // F3: dónde montar la cara/expresión (frente)
    val pieces: List<PieceDef>
)

object PieceManifestParser {

    // Parseo tolerante: un campo opcional ausente NO rompe el manifest.
    fun parse(json: String, style: String, baseDir: String): PieceManifest? {
        return try {
            val root = JSONObject(json)
            val view = root.optString("view", "perfil")
            val faceRect = if (root.has("faceRect")) {
                val fr = root.getJSONObject("faceRect")
                FaceRect(
                    fr.getDouble("x").toFloat(), fr.getDouble("y").toFloat(),
                    fr.getDouble("w").toFloat(), fr.getDouble("h").toFloat()
                )
            } else null
            val arr = root.getJSONArray("pieces")
            val list = ArrayList<PieceDef>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    PieceDef(
                        bone = o.getString("bone"),
                        file = o.getString("file"),
                        pivotX = o.getDouble("pivotX").toFloat(),
                        pivotY = o.getDouble("pivotY").toFloat(),
                        widthUnits = o.getDouble("widthUnits").toFloat(),
                        tint = o.optString("tint", "none")
                    )
                )
            }
            PieceManifest(style, baseDir, view, faceRect, list)
        } catch (e: Exception) {
            null // manifest inválido => el renderer cae a las piezas vectoriales
        }
    }
}
