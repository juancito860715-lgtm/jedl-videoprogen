package com.jedl.videoprogen

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.hypot
import kotlin.math.sin

// Una CAPA del personaje: un pedazo del recorte + su pivote de rotacion.
class Piece(val bmp: Bitmap, val pivotX: Float, val pivotY: Float, val offX: Float, val offY: Float)

// Angulos de las capas en un instante de la animacion (grados).
data class PoseAngles(val armL: Float, val armR: Float, val legL: Float, val legR: Float, val bob: Float)

// Corta la persona recortada (fondo transparente) en 5 capas usando las 33 juntas:
// [0]=pierna izq, [1]=pierna der, [2]=torso+cabeza, [3]=brazo izq, [4]=brazo der
// (ese orden es el orden de dibujo: piernas debajo, brazos encima)
object CutoutAnimator {

    private fun dist(a: PointF, b: PointF): Float = hypot(a.x - b.x, a.y - b.y)

    fun buildPieces(subject: Bitmap, jts: List<PointF>): List<Piece>? {
        if (jts.size < 29) return null
        val w = subject.width
        val h = subject.height

        val sL = jts[11]; val sR = jts[12]
        val hL = jts[23]; val hR = jts[24]

        val armLen = maxOf(dist(sL, jts[13]), dist(sR, jts[14]), 1f)
        val legLen = maxOf(dist(hL, jts[25]), dist(hR, jts[26]), 1f)
        val shoulderW = maxOf(dist(sL, sR), 1f)

        val padA = 0.30f * armLen + 12f
        val padL = 0.35f * legLen + 12f
        val padT = 0.35f * shoulderW + 20f

        val pieces = mutableListOf<Piece>()

        // --- PIERNAS (dibujan primero, quedan debajo) ---
        pieces.add(strip(subject, jts[23], jts[25], jts[27], padL, jts[23], w, h))
        pieces.add(strip(subject, jts[24], jts[26], jts[28], padL, jts[24], w, h))

        // --- TORSO + CABEZA ---
        val topY = minOf(jts[0].y, sL.y, sR.y) - padT
        val botY = (hL.y + hR.y) / 2f + 0.25f * legLen
        val leftX = minOf(sL.x, sR.x, hL.x, hR.x) - padT * 0.6f
        val rightX = maxOf(sL.x, sR.x, hL.x, hR.x) + padT * 0.6f
        pieces.add(cropRect(subject, leftX, topY, rightX, botY, w, h))

        // --- BRAZOS (encima de todo) ---
        pieces.add(strip(subject, jts[11], jts[13], jts[15], padA, jts[11], w, h))
        pieces.add(strip(subject, jts[12], jts[14], jts[16], padA, jts[12], w, h))

        return pieces
    }

    // Franja que envuelve 3 juntas (ej: hombro-codo-muneca), pivote en la 1ra junta
    private fun strip(b: Bitmap, a: PointF, c: PointF, d: PointF, pad: Float, pivot: PointF, w: Int, h: Int): Piece {
        val left = minOf(a.x, c.x, d.x) - pad
        val top = minOf(a.y, c.y, d.y) - pad
        val right = maxOf(a.x, c.x, d.x) + pad
        val bottom = maxOf(a.y, c.y, d.y) + pad
        return cropRect(b, left, top, right, bottom, w, h, pivot)
    }

    private fun cropRect(b: Bitmap, l: Float, t: Float, r: Float, bo: Float, w: Int, h: Int, pivot: PointF? = null): Piece {
        val x = l.toInt().coerceIn(0, w - 1)
        val y = t.toInt().coerceIn(0, h - 1)
        val cw = ((r.toInt() - x).coerceAtLeast(4)).coerceAtMost(w - x)
        val ch = ((bo.toInt() - y).coerceAtLeast(4)).coerceAtMost(h - y)
        val piece = Bitmap.createBitmap(b, x, y, cw, ch)
        val px = pivot?.x ?: ((l + r) / 2f)
        val py = pivot?.y ?: ((t + bo) / 2f)
        return Piece(piece, px, py, x.toFloat(), y.toFloat())
    }

    // Plantillas de movimiento: p = 0..1 a lo largo de los 5 segundos
    fun anglesFor(template: String, p: Float, frameH: Float): PoseAngles {
        val t = p * 2f * Math.PI.toFloat()
        return when (template) {
            "wave" -> PoseAngles(
                armL = sin(t) * 3f,
                armR = -40f + sin(t * 2f) * 30f,
                legL = 0f, legR = 0f,
                bob = sin(t) * frameH * 0.010f
            )
            "walk" -> PoseAngles(
                armL = -sin(t * 2f) * 18f,
                armR = sin(t * 2f) * 18f,
                legL = sin(t * 2f) * 24f,
                legR = -sin(t * 2f) * 24f,
                bob = kotlin.math.abs(sin(t * 2f)) * frameH * 0.015f
            )
            else -> PoseAngles(
                armL = sin(t) * 2.5f,
                armR = -sin(t) * 2.5f,
                legL = 0f, legR = 0f,
                bob = sin(t) * frameH * 0.008f
            )
        }
    }
}
