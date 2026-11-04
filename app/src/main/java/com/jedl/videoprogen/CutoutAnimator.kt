package com.jedl.videoprogen

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

// Una CAPA del personaje con jerarquia: el antebrazo hereda el giro del brazo,
// la espinilla hereda el giro del muslo (parentKey = la capa de la que depende).
class Piece(
    val bmp: Bitmap, val offX: Float, val offY: Float,
    val pivotX: Float, val pivotY: Float,     // pivote propio (codo, rodilla...)
    val parentX: Float, val parentY: Float,   // pivote del padre (hombro, cadera...)
    val angleKey: String,                     // "upperArmL", "shinR", "torso"...
    val parentKey: String?                    // null = no hereda
)

// Segmento capsula para las mascaras (ax,ay)-(bx,by) con radio r
data class Capsule(val ax: Float, val ay: Float, val bx: Float, val by: Float, val r: Float)

// Corta la persona recortada (fondo transparente) en 9 capas jerarquicas:
// [0]=espinilla izq [1]=espinilla der [2]=muslo izq [3]=muslo der [4]=torso
// [5]=brazo izq [6]=brazo der [7]=antebrazo izq [8]=antebrazo der
// (orden de dibujo: espinillas bajo muslos, torso sobre caderas, antebrazos encima)
object CutoutAnimator {

    // distancia cuadrada punto-segmento (evita sqrt para rapidez)
    private fun distSeg2(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 <= 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        val cx = ax + t * dx; val cy = ay + t * dy
        val ex = px - cx; val ey = py - cy
        return ex * ex + ey * ey
    }

    private fun d(a: PointF, b: PointF): Float = maxOf(hypot(a.x - b.x, a.y - b.y), 1f)

    // Punto extendido mas alla de "to" en la direccion from->to (incluye mano/pie)
    private fun extend(from: PointF, to: PointF, frac: Float): PointF {
        val len = d(from, to)
        return PointF(to.x + (to.x - from.x) / len * (len * frac), to.y + (to.y - from.y) / len * (len * frac))
    }

    fun buildPieces(subject: Bitmap, jts: List<PointF>): List<Piece>? {
        if (jts.size < 29) return null

        val sL = jts[11]; val sR = jts[12]
        val eL = jts[13]; val eR = jts[14]
        val wL = jts[15]; val wR = jts[16]
        val hL = jts[23]; val hR = jts[24]
        val kL = jts[25]; val kR = jts[26]
        val aL = jts[27]; val aR = jts[28]

        val upperL = d(sL, eL); val foreL = d(eL, wL)
        val upperR = d(sR, eR); val foreR = d(eR, wR)
        val thighL = d(hL, kL); val shinL = d(kL, aL)
        val thighR = d(hR, kR); val shinR = d(kR, aR)

        val rUpL = 0.30f * upperL + 10f; val rFoL = 0.28f * foreL + 10f
        val rUpR = 0.30f * upperR + 10f; val rFoR = 0.28f * foreR + 10f
        val rThL = 0.32f * thighL + 12f; val rShL = 0.30f * shinL + 12f
        val rThR = 0.32f * thighR + 12f; val rShR = 0.30f * shinR + 12f

        val handL = extend(eL, wL, 0.15f); val handR = extend(eR, wR, 0.15f)
        val footL = extend(kL, aL, 0.15f); val footR = extend(kR, aR, 0.15f)

        // capsulas de las extremidades (para el torso = todo lo demas)
        val caps = listOf(
            Capsule(sL.x, sL.y, eL.x, eL.y, rUpL), Capsule(eL.x, eL.y, handL.x, handL.y, rFoL),
            Capsule(sR.x, sR.y, eR.x, eR.y, rUpR), Capsule(eR.x, eR.y, handR.x, handR.y, rFoR),
            Capsule(hL.x, hL.y, kL.x, kL.y, rThL), Capsule(kL.x, kL.y, footL.x, footL.y, rShL),
            Capsule(hR.x, hR.y, kR.x, kR.y, rThR), Capsule(kR.x, kR.y, footR.x, footR.y, rShR)
        )

        val pieces = mutableListOf<Piece>()

        // --- ESPINILLAS (debajo de todo), heredan el giro del muslo ---
        pieces.add(capsulePiece(subject, kL, footL, rShL, kL.x, kL.y, "shinL", "thighL", hL.x, hL.y))
        pieces.add(capsulePiece(subject, kR, footR, rShR, kR.x, kR.y, "shinR", "thighR", hR.x, hR.y))

        // --- MUSLOS ---
        pieces.add(capsulePiece(subject, hL, kL, rThL, hL.x, hL.y, "thighL", null, 0f, 0f))
        pieces.add(capsulePiece(subject, hR, kR, rThR, hR.x, hR.y, "thighR", null, 0f, 0f))

        // --- TORSO: TODO lo que no es extremidad (incluye cabeza y caderas) ---
        pieces.add(torsoPiece(subject, caps))

        // --- BRAZOS ---
        pieces.add(capsulePiece(subject, sL, eL, rUpL, sL.x, sL.y, "upperArmL", null, 0f, 0f))
        pieces.add(capsulePiece(subject, sR, eR, rUpR, sR.x, sR.y, "upperArmR", null, 0f, 0f))

        // --- ANTEBRAZOS (encima, heredan el giro del hombro) ---
        pieces.add(capsulePiece(subject, eL, handL, rFoL, eL.x, eL.y, "foreArmL", "upperArmL", sL.x, sL.y))
        pieces.add(capsulePiece(subject, eR, handR, rFoR, eR.x, eR.y, "foreArmR", "upperArmR", sR.x, sR.y))

        return pieces
    }

    // Pieza = solo los pixeles de la persona dentro de la capsula (sin rectangulos)
    private fun capsulePiece(
        subject: Bitmap, a: PointF, b: PointF, r: Float,
        pivX: Float, pivY: Float, key: String,
        parent: String?, parX: Float, parY: Float
    ): Piece {
        val w = subject.width; val h = subject.height
        val r2 = r * r
        val left = (minOf(a.x, b.x) - r).toInt().coerceIn(0, w - 1)
        val top = (minOf(a.y, b.y) - r).toInt().coerceIn(0, h - 1)
        val right = (maxOf(a.x, b.x) + r).toInt().coerceAtLeast(left + 4).coerceAtMost(w)
        val bottom = (maxOf(a.y, b.y) + r).toInt().coerceAtLeast(top + 4).coerceAtMost(h)
        val cw = right - left; val ch = bottom - top
        val px = IntArray(cw * ch)
        subject.getPixels(px, 0, cw, left, top, cw, ch)
        for (j in 0 until ch) {
            val wy = (top + j).toFloat()
            for (i in 0 until cw) {
                val idx = j * cw + i
                if (px[idx] ushr 24 == 0) continue   // fondo transparente
                if (distSeg2((left + i).toFloat(), wy, a.x, a.y, b.x, b.y) > r2) px[idx] = 0
            }
        }
        val bmp = Bitmap.createBitmap(px, cw, ch, Bitmap.Config.ARGB_8888)
        return Piece(bmp, left.toFloat(), top.toFloat(), pivX, pivY, parX, parY, key, parent)
    }

    // Torso = toda la persona MENOS las capsulas de las extremidades
    private fun torsoPiece(subject: Bitmap, caps: List<Capsule>): Piece {
        val w = subject.width; val h = subject.height
        val px = IntArray(w * h)
        subject.getPixels(px, 0, w, 0, 0, w, h)
        for (j in 0 until h) {
            val wy = j.toFloat()
            for (i in 0 until w) {
                val idx = j * w + i
                if (px[idx] ushr 24 == 0) continue
                val wx = i.toFloat()
                for (cap in caps) {
                    val r2 = cap.r * cap.r
                    if (distSeg2(wx, wy, cap.ax, cap.ay, cap.bx, cap.by) <= r2) { px[idx] = 0; break }
                }
            }
        }
        val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        return Piece(bmp, 0f, 0f, w / 2f, h / 2f, 0f, 0f, "torso", null)
    }

    // Plantillas de movimiento: p = 0..1 en los 5 segundos.
    // Devuelve un mapa: clave = capa, valor = grados ("bob" = fraccion de altura).
    fun anglesFor(template: String, p: Float): Map<String, Float> {
        val t = p * 2f * Math.PI.toFloat()
        val m = mutableMapOf<String, Float>()
        m["thighL"] = 0f; m["shinL"] = 0f; m["thighR"] = 0f; m["shinR"] = 0f
        when (template) {
            "wave" -> {
                // brazo derecho levantado saludando con el ANTEBRAZO (dobla el codo)
                m["upperArmL"] = sin(t) * 3f
                m["foreArmL"] = sin(t) * 4f
                m["upperArmR"] = -22f + sin(t) * 6f
                m["foreArmR"] = -30f + sin(t * 2f) * 35f
                m["bob"] = sin(t) * 0.010f
            }
            "walk" -> {
                // piernas alternando con rodilla que dobla; brazos en contrafase
                m["thighL"] = sin(t * 2f) * 20f
                m["shinL"] = 15f * maxOf(0f, sin(t * 2f))
                m["thighR"] = -sin(t * 2f) * 20f
                m["shinR"] = 15f * maxOf(0f, -sin(t * 2f))
                m["upperArmL"] = -sin(t * 2f) * 15f
                m["foreArmL"] = -sin(t * 2f) * 8f
                m["upperArmR"] = sin(t * 2f) * 15f
                m["foreArmR"] = sin(t * 2f) * 8f
                m["bob"] = abs(sin(t * 2f)) * 0.015f
            }
            else -> {   // idle: respiracion suave
                m["upperArmL"] = sin(t) * 2.5f
                m["foreArmL"] = sin(t) * 3f
                m["upperArmR"] = -sin(t) * 2.5f
                m["foreArmR"] = -sin(t) * 3f
                m["bob"] = sin(t) * 0.008f
            }
        }
        return m
    }
}
