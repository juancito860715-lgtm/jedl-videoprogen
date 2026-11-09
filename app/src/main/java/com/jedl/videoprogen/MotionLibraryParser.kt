package com.jedl.videoprogen

import android.content.res.AssetManager
import org.json.JSONObject

// ============================================================
// BIBLIOTECA DE MOVIMIENTOS — LECTOR DE ARCHIVOS (v0.8.0)
// ============================================================
// Extrae los keyframes (ángulos en GRADOS por hueso de la
// biblioteca) y la duración de los archivos JSON/CSV que viven en
// app/src/main/assets/motions/.
//
// FORMATO JSON (motions/*.json):
// {
//   "id": "walk",
//   "label": "Caminar",
//   "durationMs": 5000,
//   "loop": true,
//   "convention": "jedl",
//   "keyframes": [
//     { "t": 0.00, "bones": { "mixamorig:LeftUpLeg": 0.0, ... }, "bob": 0.0, "sway": 0.0 },
//     { "t": 1.00, "bones": { ... igual que t=0 para loop perfecto }
//   ]
// }
//
// FORMATO CSV (motions/*.csv):
//   #id=walk
//   #label=Caminar
//   #durationMs=5000
//   #loop=true
//   #convention=jedl
//   t,mixamorig:LeftUpLeg,mixamorig:LeftLeg,mixamorig:RightUpLeg,mixamorig:RightLeg,bob,sway
//   0.00,0.0,0.0,0.0,0.0,0.0,0.0
//   ...
//
// Reglas: t en 0..1 (normalizado), mínimo 2 keyframes, archivo
// corrupto -> null (la app NUNCA se rompe: cae a las fórmulas).
// Los ángulos van EN GRADOS; la conversión de nombres y de
// convención la hace AnimationRetargeter.retarget().
// ============================================================

class MotionLibraryParser private constructor() {

    /** Una acción de la biblioteca lista para inyectar en PuppetEngine. */
    class MotionLibraryEntry(
        val id: String,             // id de la acción ("walk" pisa la fórmula "walk")
        val label: String,          // nombre visible ("Caminar")
        val template: MotionTemplate, // keyframes retargetados al rig JEDL
        val durationMs: Long        // duración original del clip (informativa)
    )

    companion object {

        /** Lee un archivo de la biblioteca (por extensión). null = inválido. */
        fun parseFile(fileName: String, text: String): MotionLibraryEntry? {
            return try {
                when {
                    fileName.endsWith(".json", ignoreCase = true) -> parseJson(text)
                    fileName.endsWith(".csv", ignoreCase = true) -> parseCsv(text)
                    else -> null
                }
            } catch (_: Exception) {
                null   // JSON/CSV corrupto: jamás tira la app
            }
        }

        /**
         * Carga TODAS las acciones de assets/<dir> (llamar en hilo de fondo).
         * Devuelve solo las válidas; assets ilegibles -> lista vacía.
         */
        fun loadFromAssets(assets: AssetManager, dir: String = "motions"): List<MotionLibraryEntry> {
            val out = ArrayList<MotionLibraryEntry>()
            try {
                val names = assets.list(dir) ?: emptyArray()
                for (name in names.sorted()) {
                    if (!name.endsWith(".json") && !name.endsWith(".csv")) continue
                    val text = assets.open(dir + "/" + name).bufferedReader().use { it.readText() }
                    val entry = parseFile(name, text)
                    if (entry != null) out.add(entry)
                }
            } catch (_: Exception) {
                // sin assets o sin permisos: lista vacía, la app sigue
            }
            return out
        }

        // ---------------- JSON ----------------
        private fun parseJson(text: String): MotionLibraryEntry? {
            val root = JSONObject(text)
            val id = root.optString("id").trim()
            if (id.isEmpty()) return null
            val label = root.optString("label", id).ifBlank { id }
            val durationMs = root.optLong("durationMs", 5000L).coerceIn(500L, 60000L)
            val loop = root.optBoolean("loop", false)
            val convention = root.optString("convention", "jedl")
            val arr = root.optJSONArray("keyframes") ?: return null
            val frames = ArrayList<PoseFrame>()
            for (i in 0 until arr.length()) {
                val kf = arr.getJSONObject(i)
                val t = kf.optDouble("t", -1.0)
                if (t.isNaN() || t < 0.0 || t > 1.0) return null   // t debe estar en 0..1
                val raw = HashMap<String, Float>()
                val bones = kf.optJSONObject("bones")
                if (bones != null) {
                    for (name in bones.keys()) {
                        val v = bones.optDouble(name)
                        if (!v.isNaN()) raw[name] = v.toFloat()
                    }
                }
                val pose = AnimationRetargeter.retarget(
                    raw, convention,
                    kf.optDouble("bob", 0.0).toFloat(),
                    kf.optDouble("sway", 0.0).toFloat()
                )
                frames.add(PoseFrame(t.toFloat(), pose))
            }
            if (frames.size < 2) return null
            frames.sortBy { it.t }
            return MotionLibraryEntry(id, label, MotionTemplate(label, frames, loop), durationMs)
        }

        // ---------------- CSV ----------------
        private fun parseCsv(text: String): MotionLibraryEntry? {
            var id = ""
            var label = ""
            var durationMs = 5000L
            var loop = false
            var convention = "jedl"
            var header: List<String>? = null
            val frames = ArrayList<PoseFrame>()
            for (rawLine in text.lines()) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue
                if (line.startsWith("#")) {   // metadata: #clave=valor
                    val body = line.removePrefix("#")
                    val eq = body.indexOf('=')
                    if (eq <= 0) continue
                    val k = body.substring(0, eq).trim().lowercase()
                    val v = body.substring(eq + 1).trim()
                    when (k) {
                        "id" -> id = v
                        "label" -> label = v
                        "durationms" -> durationMs = v.toLongOrNull()?.coerceIn(500L, 60000L) ?: 5000L
                        "loop" -> loop = v.equals("true", ignoreCase = true)
                        "convention" -> convention = v
                    }
                    continue
                }
                if (header == null) {
                    if (line.startsWith("t,")) header = line.split(',')
                    continue
                }
                val cols = header!!
                val parts = line.split(',')
                if (parts.size != cols.size) continue
                val t = parts[0].trim().toFloatOrNull() ?: continue
                if (t < 0f || t > 1f) return null
                val raw = HashMap<String, Float>()
                // columnas de huesos: todo lo que está entre t y bob/sway
                for (c in 1 until cols.size - 2) {
                    val v = parts[c].trim().toFloatOrNull() ?: continue
                    raw[cols[c].trim()] = v
                }
                val bob = parts[cols.size - 2].trim().toFloatOrNull() ?: 0f
                val sway = parts[cols.size - 1].trim().toFloatOrNull() ?: 0f
                frames.add(PoseFrame(t, AnimationRetargeter.retarget(raw, convention, bob, sway)))
            }
            if (id.isEmpty() || frames.size < 2) return null
            if (label.isEmpty()) label = id
            frames.sortBy { it.t }
            return MotionLibraryEntry(id, label, MotionTemplate(label, frames, loop), durationMs)
        }
    }
}
