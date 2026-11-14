package com.jedl.videoprogen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

// ============================================================
// F1 — RENDER DEL AVATAR POR PIEZAS (v0.7.0-f1)
// ============================================================
// Cada pieza es ARTE PRE-DIBUJADO (vectorial en F1; PNG con
// manifest en F2+) colgado de la MATRIZ de su hueso: se dibuja
// en el espacio local del hueso (pivote = articulación) y SOLAPA
// hacia la pieza madre, así las articulaciones NUNCA se separan.
// La cápsula rígida de v0.6.0 queda eliminada.
//
// CONTRATOS CONGELADOS (F1):
//  * firma draw(canvas, solved, spec, face, direction) intacta
//  * orden de dibujo: pieza de cabeza -> cara (espacio del hueso)
//  * matriz del hueso = espacio de ESCENA (export = preview)
//
// Correcciones de v0.6.0 incorporadas de raíz:
//  * head: la pieza cuelga de la matriz del hueso head, que apunta
//    del cuello HACIA ARRIBA (el renderer viejo asumía lo
//    contrario y dibujaba la cabeza dentro del torso).
//  * hombros cubiertos: semiancho de camiseta 0.13 >= rig 0.10.
// ============================================================
object AvatarRenderer {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mTmp = Matrix()

    private const val FACE_LINE = 0xFF262626.toInt()

    // ============================================================
    // DIAGNÓSTICO v0.8.1 (Misión 1 del dueño): SOLO rectángulos
    // de color sólido colgados de la MATRIZ de cada hueso. Sin
    // foto, sin arte vectorial, sin PNGs. Cada rect nace EXACTO
    // en su articulación (y=0 local): un despegue se ve al instante.
    // Verifica jerarquía: worldDeg = worldDegPadre + reposo + anim
    // (si el torso rota, cabeza y brazos DEBEN seguir pegados).
    // Al aprobar la Misión 1: poner en false (no borrar nada más).
    // ============================================================
    private const val DEBUG_RECTS = false

    @Volatile var lastRenderPath: String = "NOT_RENDERED"
        private set

    private fun darker(argb: Int): Int {
        val a = (argb ushr 24) and 0xFF
        val r = ((argb ushr 16) and 0xFF) * 3 / 4
        val g = ((argb ushr 8) and 0xFF) * 3 / 4
        val b = (argb and 0xFF) * 3 / 4
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    // ---------- API (firma congelada) ----------
    fun draw(
        canvas: Canvas, solved: Map<String, BonePose>,
        spec: AvatarSpec, face: Bitmap?, direction: String
    ) {
        if (DEBUG_RECTS) {
            lastRenderPath = "DEBUG_RECTS"
            drawDebugRects(canvas, solved)
            return
        }

        // Registrar la ruta realmente ejecutada, no solo la disponibilidad del manifest.
        if (PieceLibrary.hasBitmapPieces() && drawBitmapPieces(canvas, solved, spec)) {
            lastRenderPath = "PNG_PIECES"
            return
        }
        lastRenderPath = "VECTOR_FALLBACK"

        // Piezas vectoriales del estilo base. El orden procede del contrato compartido.
        for (name in AvatarRenderOrder.backToFront) {
            when (name) {
                "armUR", "armUL" -> bone(canvas, solved, name) { upperArm(it, spec) }
                "foreArmR", "foreArmL" -> bone(canvas, solved, name) { foreArm(it, spec) }
                "thighR", "thighL" -> bone(canvas, solved, name) { thigh(it, spec) }
                "shinR", "shinL" -> bone(canvas, solved, name) { shin(it, spec) }
                "torso" -> bone(canvas, solved, name) { torso(it, spec) }
                "head" -> bone(canvas, solved, name) { head(it, spec, face, direction) }
            }
        }
    }

    private inline fun bone(
        canvas: Canvas, solved: Map<String, BonePose>,
        name: String, block: (Canvas) -> Unit
    ) {
        val bp = solved[name] ?: return
        val save = canvas.save()
        canvas.concat(bp.matrix)   // espacio local del hueso (pivote = articulación)
        block(canvas)
        canvas.restoreToCount(save)
    }

    // ---------- DIAGNÓSTICO v0.8.1: RECTÁNGULOS SÓLIDOS (Misión 1) ----------
    // Colores: torso ROJO, cabeza AZUL, brazos VERDE (oscuro = antebrazo),
    // piernas AMARILLO (naranja = espinilla). El torso mide 0.22 de ancho
    // para que los hombros del rig (±0.10) caigan DENTRO del rojo y se vea
    // que los brazos cuelgan de él. Orden de dibujo = el de las piezas F1.
    private fun drawDebugRects(canvas: Canvas, solved: Map<String, BonePose>) {
        for (name in AvatarRenderOrder.backToFront) {
            val bp = solved[name] ?: continue
            val save = canvas.save()
            canvas.concat(bp.matrix)   // espacio local del hueso (pivote = articulación)
            when (name) {
                "torso" -> rect(canvas, 0xFFE53935.toInt(), 0.220f, 0.000f, 0.280f)
                "head" -> rect(canvas, 0xFF1E88E5.toInt(), 0.170f, 0.000f, 0.190f)
                "armUL", "armUR" -> rect(canvas, 0xFF43A047.toInt(), 0.054f, 0.000f, 0.145f)
                "foreArmL", "foreArmR" -> rect(canvas, 0xFF2E7D32.toInt(), 0.046f, 0.000f, 0.135f)
                "thighL", "thighR" -> rect(canvas, 0xFFFDD835.toInt(), 0.074f, 0.000f, 0.190f)
                "shinL", "shinR" -> rect(canvas, 0xFFFB8C00.toInt(), 0.058f, 0.000f, 0.180f)
            }
            canvas.restoreToCount(save)
        }
    }

    private fun rect(c: Canvas, color: Int, w: Float, y0: Float, y1: Float) {
        p.style = Paint.Style.FILL; p.color = color
        c.drawRect(-w / 2f, y0, w / 2f, y1, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.006f
        p.color = 0xFF0B0C10.toInt()
        c.drawRect(-w / 2f, y0, w / 2f, y1, p)
    }

    // ---------- PIEZAS VECTORIALES (espacio local de cada hueso) ----------

    private fun limb(c: Canvas, y0: Float, y1: Float, w: Float, color: Int) {
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = w + 0.008f; p.color = darker(color)
        c.drawLine(0f, y0, 0f, y1, p)
        p.strokeWidth = w; p.color = color
        c.drawLine(0f, y0, 0f, y1, p)
    }

    private fun circle(c: Canvas, x: Float, y: Float, r: Float, fill: Int) {
        p.style = Paint.Style.FILL; p.color = fill
        c.drawCircle(x, y, r, p)
    }

    private fun circle(c: Canvas, x: Float, y: Float, r: Float, fill: Int, stroke: Int, sw: Float) {
        p.style = Paint.Style.FILL; p.color = fill
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = sw; p.color = stroke
        c.drawCircle(x, y, r, p)
    }

    private fun fillStroke(c: Canvas, path: Path, fill: Int, stroke: Int, sw: Float) {
        p.style = Paint.Style.FILL; p.color = fill
        c.drawPath(path, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = sw; p.color = stroke
        c.drawPath(path, p)
    }

    private fun upperArm(c: Canvas, spec: AvatarSpec) {
        limb(c, -0.012f, 0.150f, 0.054f, spec.skin)          // brazo en piel
        // manga (camiseta), solapa el hombro
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 0.074f; p.color = darker(spec.shirt)
        c.drawLine(0f, -0.018f, 0f, 0.058f, p)
        p.strokeWidth = 0.066f; p.color = spec.shirt
        c.drawLine(0f, -0.018f, 0f, 0.058f, p)
    }

    private fun foreArm(c: Canvas, spec: AvatarSpec) {
        limb(c, -0.010f, 0.136f, 0.046f, spec.skin)          // antebrazo
        circle(c, 0f, 0.146f, 0.030f, darker(spec.skin))     // mano (borde)
        circle(c, 0f, 0.146f, 0.026f, spec.skin)
    }

    private fun thigh(c: Canvas, spec: AvatarSpec) {
        // sobresale -0.042 hacia la cadera: solapa el pantalón
        limb(c, -0.042f, 0.196f, 0.074f, spec.pants)
    }

    private fun shin(c: Canvas, spec: AvatarSpec) {
        limb(c, -0.010f, 0.176f, 0.062f, spec.pants)
        // zapato
        val shoe = Path().apply {
            moveTo(-0.030f, 0.150f)
            lineTo(-0.037f, 0.186f)
            lineTo(0.062f, 0.186f)
            quadTo(0.086f, 0.177f, 0.060f, 0.152f)
            close()
        }
        fillStroke(c, shoe, spec.shoes, darker(spec.shoes), 0.005f)
    }

    private fun torso(c: Canvas, spec: AvatarSpec) {
        // pelvis (pantalón) BAJO la camiseta
        val pelvis = Path().apply {
            moveTo(-0.105f, 0.065f)
            cubicTo(-0.112f, 0.020f, -0.112f, -0.030f, -0.098f, -0.052f)
            cubicTo(-0.050f, -0.068f, 0.050f, -0.068f, 0.098f, -0.052f)
            cubicTo(0.112f, -0.030f, 0.112f, 0.020f, 0.105f, 0.065f)
            cubicTo(0.060f, 0.085f, -0.060f, 0.085f, -0.105f, 0.065f)
            close()
        }
        fillStroke(c, pelvis, spec.pants, darker(spec.pants), 0.006f)
        // camiseta: cubre hombros ±0.10 (semiancho 0.13), dobladillo sobre el pantalón
        val shirt = Path().apply {
            moveTo(-0.100f, 0.030f)
            cubicTo(-0.108f, 0.120f, -0.100f, 0.160f, -0.118f, 0.220f)
            cubicTo(-0.130f, 0.245f, -0.130f, 0.272f, -0.112f, 0.292f)
            cubicTo(-0.060f, 0.315f, -0.020f, 0.318f, 0.0f, 0.318f)
            cubicTo(0.020f, 0.318f, 0.060f, 0.315f, 0.112f, 0.292f)
            cubicTo(0.130f, 0.272f, 0.130f, 0.245f, 0.118f, 0.220f)
            cubicTo(0.100f, 0.160f, 0.108f, 0.120f, 0.100f, 0.030f)
            cubicTo(0.060f, 0.012f, -0.060f, 0.012f, -0.100f, 0.030f)
            close()
        }
        fillStroke(c, shirt, spec.shirt, darker(spec.shirt), 0.006f)
    }

    // ---------- CABEZA (espacio local del hueso head: pivote=cuello, +y ARRIBA) ----------

    private fun head(c: Canvas, spec: AvatarSpec, face: Bitmap?, direction: String) {
        // piel: centro a 0.115 del cuello (la punta del hueso head)
        circle(c, 0f, 0.115f, 0.088f, spec.skin, darker(spec.skin), 0.005f)
        if (direction == "espalda") {
            // nuca: pelo cubriendo casi toda la cabeza, SIN cara
            if (spec.hairStyle != 3) circle(c, 0f, 0.112f, 0.092f, spec.hair)
            return
        }
        // oreja (perfil)
        circle(c, -0.028f, 0.115f, 0.016f, darker(spec.skin), darker(spec.skin), 0.004f)
        // la cara SIEMPRE después de la pieza de piel y en el mismo espacio local
        if (face != null) faceFromPhoto(c, face) else faceDrawn(c, spec, direction)
        hair(c, spec, direction)
    }

    private fun faceDrawn(c: Canvas, spec: AvatarSpec, direction: String) {
        p.style = Paint.Style.FILL; p.color = FACE_LINE
        if (direction == "frente") {
            c.drawCircle(-0.030f, 0.128f, 0.009f, p)
            c.drawCircle(0.030f, 0.128f, 0.009f, p)
            strokePath(c, FACE_LINE, 0.004f, Path().apply {
                moveTo(-0.020f, 0.085f); quadTo(0.0f, 0.102f, 0.020f, 0.085f)
            })
            if (spec.beard) beard(c, spec, true)
        } else {
            c.drawCircle(0.030f, 0.128f, 0.009f, p)                       // ojo
            strokePath(c, FACE_LINE, 0.004f, Path().apply {                // ceja
                moveTo(0.014f, 0.142f); quadTo(0.030f, 0.148f, 0.046f, 0.139f)
            })
            strokePath(c, FACE_LINE, 0.004f, Path().apply {                // sonrisa
                moveTo(0.020f, 0.082f); quadTo(0.040f, 0.094f, 0.058f, 0.077f)
            })
            strokePath(c, darker(spec.skin), 0.004f, Path().apply {       // nariz
                moveTo(0.082f, 0.118f); quadTo(0.095f, 0.110f, 0.087f, 0.096f)
            })
            if (spec.beard) beard(c, spec, false)
        }
    }

    private fun beard(c: Canvas, spec: AvatarSpec, front: Boolean) {
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 0.026f; p.color = spec.hair
        if (front) c.drawLine(-0.030f, 0.062f, 0.030f, 0.062f, p)
        else c.drawLine(0.014f, 0.068f, 0.038f, 0.052f, p)
    }

    private fun strokePath(c: Canvas, color: Int, w: Float, path: Path) {
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = w; p.color = color
        c.drawPath(path, p)
    }

    // Cara recortada de la foto (FaceCutter), montada EN el espacio local del hueso.
    private fun faceFromPhoto(c: Canvas, face: Bitmap) {
        val r = 0.072f
        val save = c.save()
        c.clipPath(Path().apply { addCircle(0f, 0.115f, r, Path.Direction.CCW) })
        val side = r * 2f
        val s = maxOf(side / face.width.toFloat(), side / face.height.toFloat())
        val w = face.width * s
        val h = face.height * s
        p.alpha = 235
        c.drawBitmap(face, null, RectF(-w / 2f, 0.115f - h / 2f, w / 2f, 0.115f + h / 2f), p)
        p.alpha = 255
        c.restoreToCount(save)
    }

    private fun hair(c: Canvas, spec: AvatarSpec, direction: String) {
        when (spec.hairStyle) {
            0 -> strokePath(c, spec.hair, 0.030f, Path().apply {          // corto
                moveTo(-0.080f, 0.158f); quadTo(-0.040f, 0.212f, 0.030f, 0.200f)
            })
            1 -> {                                                        // afro
                circle(c, -0.015f, 0.215f, 0.052f, spec.hair)
                circle(c, -0.075f, 0.130f, 0.030f, spec.hair)
                circle(c, 0.045f, 0.190f, 0.026f, spec.hair)
            }
            2 -> {                                                        // gorra a juego
                val dome = Path().apply {
                    addArc(RectF(-0.089f, 0.057f, 0.089f, 0.235f), 180f, 180f)
                    close()
                }
                fillStroke(c, dome, spec.shirt, darker(spec.shirt), 0.004f)
                if (direction == "frente") {
                    strokePath(c, spec.shirt, 0.022f, Path().apply {
                        moveTo(-0.085f, 0.150f); lineTo(0.085f, 0.150f)
                    })
                } else {
                    val visor = Path().apply {
                        moveTo(0.040f, 0.150f); lineTo(0.165f, 0.140f)
                        quadTo(0.176f, 0.136f, 0.162f, 0.126f)
                        lineTo(0.040f, 0.130f); close()
                    }
                    fillStroke(c, visor, spec.shirt, darker(spec.shirt), 0.004f)
                }
            }
            3 -> { }                                                      // calvo
            else -> {                                                     // largo
                strokePath(c, spec.hair, 0.034f, Path().apply {
                    moveTo(-0.080f, 0.160f); quadTo(-0.040f, 0.212f, 0.030f, 0.200f)
                })
                strokePath(c, spec.hair, 0.036f, Path().apply {
                    moveTo(-0.080f, 0.130f); lineTo(-0.086f, 0.010f)
                })
                strokePath(c, spec.hair, 0.030f, Path().apply {
                    moveTo(-0.045f, 0.175f); lineTo(-0.052f, -0.005f)
                })
            }
        }
    }

    // ---------- PIEZAS PNG DEL BANCO (F2+: manifest congelado de PieceSet) ----------

    private fun drawBitmapPieces(
        canvas: Canvas, solved: Map<String, BonePose>, spec: AvatarSpec
    ): Boolean {
        for (b in AvatarRenderOrder.backToFront) {
            val def = PieceLibrary.piece(b) ?: return false
            val bmp = PieceLibrary.bitmap(def) ?: return false
            val bp = solved[b] ?: return false
            val save = canvas.save()
            canvas.concat(bp.matrix)
            val s = def.widthUnits / bmp.width
            mTmp.reset()
            mTmp.postTranslate(-def.pivotX, -def.pivotY)
            mTmp.postScale(s, s)
            p.colorFilter = tintFilter(spec, def.tint)
            canvas.drawBitmap(bmp, mTmp, p)
            p.colorFilter = null
            canvas.restoreToCount(save)
        }
        return true
    }

    private fun tintFilter(spec: AvatarSpec, tint: String): ColorMatrixColorFilter? {
        val argb = when (tint) {
            "skin" -> spec.skin; "hair" -> spec.hair; "shirt" -> spec.shirt
            "pants" -> spec.pants; "shoes" -> spec.shoes; else -> return null
        }
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val bl = (argb and 0xFF) / 255f
        return ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            r, 0f, 0f, 0f, 0f,
            0f, g, 0f, 0f, 0f,
            0f, 0f, bl, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))
    }
}
