package com.jedl.videoprogen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.hypot

// ============================================================
// FASE B — RENDER DEL PERSONAJE POR PIEZAS (v0.6.0)
// ============================================================
// Dibuja el avatar (AvatarSpec) sobre las ARTICULACIONES RESUELTAS
// del rig existente (PuppetRig.HUMANOID.solve). Cada pieza es una
// cápsula (línea con extremos redondeados) o un círculo entre
// joint y tip del hueso correspondiente:
//   - piernas: thigh joint -> shin joint -> shin tip (+ zapato)
//   - torso:  cadera (raíz) -> cuello (tip del torso)
//   - brazos: hombro -> codo -> mano (+ manga de la camiseta)
//   - cabeza: centro a 0.098 unidades SOBRE el cuello, en dirección
//     CONTRARIA al hueso head (el hueso apunta hacia abajo desde
//     el cuello: girarlo tumba la cabeza y la cara lo sigue).
// Convención del rig: 0 grados = abajo, 180 = arriba, positivo =
// horario en pantalla; y crece hacia abajo.
//
// Cara: DIBUJADA (ojos/cejas/boca planos) o la CARA RECORTADA DE LA
// FOTO (FaceCutter) montada en la cabeza con recorte circular y el
// alfa suave del recorte fundiéndose con el círculo de piel.
//
// Direcciones (pedido del dueño: ver todas al caminar):
//   "der"    perfil derecho (dibujo normal)
//   "izq"    perfil izquierdo = ESPEJO (el caller hace scale(-1,1))
//   "frente" de frente (sin espejo; los ángulos los ajusta
//            PuppetEngine.applyDirection)
//   "espalda" de espaldas: pelo de la nuca, SIN cara
//   "3/4"    diagonal aproximada (torso/cabeza girados unos grados)
// ============================================================
object AvatarRenderer {

    // Radio de la cabeza (unidades del muñeco)
    private const val HEAD_R = 0.085f
    // Distancia cuello -> centro de la cabeza (unidades)
    private const val NECK_TO_CENTER = 0.098f

    fun draw(
        canvas: Canvas, solved: Map<String, BonePose>,
        spec: AvatarSpec, face: Bitmap?, direction: String
    ) {
        val torso = solved["torso"] ?: return
        val head = solved["head"] ?: return
        val armUL = solved["armUL"]; val foreArmL = solved["foreArmL"]
        val armUR = solved["armUR"]; val foreArmR = solved["foreArmR"]
        val thighL = solved["thighL"]; val shinL = solved["shinL"]
        val thighR = solved["thighR"]; val shinR = solved["shinR"]

        // 1) PIERNAS (detrás de todo)
        if (thighL != null && shinL != null) drawLeg(canvas, thighL, shinL, spec)
        if (thighR != null && shinR != null) drawLeg(canvas, thighR, shinR, spec)

        // 2) TORSO: camiseta + cadera del pantalón
        capsule(canvas, torso.joint.x, torso.joint.y, torso.tip.x, torso.tip.y, 0.150f, spec.shirt)
        capsule(canvas, torso.joint.x, torso.joint.y + 0.012f, torso.joint.x, torso.joint.y + 0.070f, 0.156f, spec.pants)

        // 3) BRAZOS (encima del torso)
        if (armUL != null && foreArmL != null) drawArm(canvas, armUL, foreArmL, spec)
        if (armUR != null && foreArmR != null) drawArm(canvas, armUR, foreArmR, spec)

        // 4) CUELLO + CABEZA
        drawHead(canvas, head, torso, spec, face, direction)
    }

    // ---------- PIEZAS ----------

    private fun drawLeg(canvas: Canvas, thigh: BonePose, shin: BonePose, spec: AvatarSpec) {
        // muslo y espinilla del mismo color (pantalón largo)
        capsule(canvas, thigh.joint.x, thigh.joint.y, shin.joint.x, shin.joint.y, 0.068f, spec.pants)
        capsule(canvas, shin.joint.x, shin.joint.y, shin.tip.x, shin.tip.y, 0.054f, spec.pants)
        // zapato: círculo en el tobillo + puntera corta hacia el pie
        circle(canvas, shin.tip.x, shin.tip.y, 0.034f, spec.shoes)
        val fx = shin.tip.x + (shin.tip.x - shin.joint.x) * 0.10f
        val fy = shin.tip.y + 0.016f
        capsule(canvas, shin.tip.x, shin.tip.y, fx, fy, 0.052f, spec.shoes)
    }

    private fun drawArm(canvas: Canvas, upper: BonePose, fore: BonePose, spec: AvatarSpec) {
        // brazo completo en piel
        capsule(canvas, upper.joint.x, upper.joint.y, fore.joint.x, fore.joint.y, 0.054f, spec.skin)
        capsule(canvas, fore.joint.x, fore.joint.y, fore.tip.x, fore.tip.y, 0.046f, spec.skin)
        // manga corta de la camiseta (primer tramo del hombro)
        val sx = upper.joint.x + (fore.joint.x - upper.joint.x) * 0.45f
        val sy = upper.joint.y + (fore.joint.y - upper.joint.y) * 0.45f
        capsule(canvas, upper.joint.x, upper.joint.y, sx, sy, 0.062f, spec.shirt)
        // mano
        circle(canvas, fore.tip.x, fore.tip.y, 0.032f, spec.skin)
    }

    private fun drawHead(
        canvas: Canvas, head: BonePose, torso: BonePose,
        spec: AvatarSpec, face: Bitmap?, direction: String
    ) {
        // centro de la cabeza: sobre el cuello, dirección contraria al
        // hueso head (que apunta hacia abajo desde el cuello)
        val len = hypot(head.tip.x - head.joint.x, head.tip.y - head.joint.y)
        val k = if (len > 1e-5f) NECK_TO_CENTER / len else 0.85f
        val cx = head.joint.x + (head.joint.x - head.tip.x) * k
        val cy = head.joint.y + (head.joint.y - head.tip.y) * k
        // inclinación de la cabeza (para que la cara siga al hueso):
        // worldDeg neutro del head = 180 (torso 180 + reposo 0)
        val tiltDeg = head.worldDeg - 180f

        // cuello (del tip del torso hacia el centro de la cabeza)
        capsule(canvas, torso.tip.x, torso.tip.y, cx, cy, 0.030f, spec.skin)

        // círculo de piel
        circle(canvas, cx, cy, HEAD_R, spec.skin)

        val save = canvas.save()
        canvas.rotate(tiltDeg, cx, cy)

        if (direction == "espalda") {
            // DE ESPALDAS: nuca de pelo, sin cara
            drawHairBack(canvas, cx, cy, spec)
        } else {
            drawHair(canvas, cx, cy, spec, direction)
            if (face != null) {
                drawFaceFromPhoto(canvas, cx, cy, face)
            } else {
                drawFaceDrawn(canvas, cx, cy, spec)
            }
        }
        canvas.restoreToCount(save)
    }

    // ---------- CABELLO (estilos) ----------

    private fun drawHair(canvas: Canvas, cx: Float, cy: Float, spec: AvatarSpec, direction: String) {
        val r = HEAD_R
        when (spec.hairStyle) {
            0 -> { // corto: casquete superior
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.color = spec.hair
                p.style = Paint.Style.FILL
                canvas.drawArc(RectF(cx - r * 1.02f, cy - r * 1.02f, cx + r * 1.02f, cy + r * 1.02f), 180f, 130f, true, p)
            }
            1 -> { // afro: bola grande arriba + laterales
                circle(canvas, cx, cy - r * 0.72f, r * 0.78f, spec.hair)
                circle(canvas, cx - r * 0.82f, cy - r * 0.28f, r * 0.42f, spec.hair)
                circle(canvas, cx + r * 0.82f, cy - r * 0.28f, r * 0.42f, spec.hair)
            }
            2 -> { // gorra a juego con la camiseta + pelo asomando
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.color = spec.hair
                p.style = Paint.Style.FILL
                canvas.drawArc(RectF(cx - r * 1.02f, cy - r * 1.02f, cx + r * 1.02f, cy + r * 1.02f), 180f, 130f, true, p)
                val cap = Paint(Paint.ANTI_ALIAS_FLAG)
                cap.color = spec.shirt
                cap.style = Paint.Style.FILL
                canvas.drawArc(RectF(cx - r * 1.06f, cy - r * 1.10f, cx + r * 1.06f, cy + r * 0.05f), 180f, 180f, true, cap)
                // visera hacia la derecha de pantalla (se espeja solita en "izq")
                if (direction != "frente") {
                    capsule(canvas, cx + r * 0.10f, cy - r * 0.42f, cx + r * 1.18f, cy - r * 0.30f, 0.030f, spec.shirt)
                } else {
                    // de frente la visera se ve de frente: franja corta
                    capsule(canvas, cx - r * 0.9f, cy - r * 0.38f, cx + r * 0.9f, cy - r * 0.38f, 0.026f, spec.shirt)
                }
            }
            3 -> { // calvo: nada (solo piel)
            }
            else -> { // largo: casquete + mechones a los lados
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.color = spec.hair
                p.style = Paint.Style.FILL
                canvas.drawArc(RectF(cx - r * 1.04f, cy - r * 1.04f, cx + r * 1.04f, cy + r * 1.04f), 180f, 180f, true, p)
                capsule(canvas, cx - r * 0.95f, cy - r * 0.30f, cx - r * 0.95f, cy + r * 0.85f, 0.052f, spec.hair)
                capsule(canvas, cx + r * 0.95f, cy - r * 0.30f, cx + r * 0.95f, cy + r * 0.85f, 0.052f, spec.hair)
            }
        }
    }

    private fun drawHairBack(canvas: Canvas, cx: Float, cy: Float, spec: AvatarSpec) {
        // nuca: pelo cubriendo casi toda la cabeza
        val r = HEAD_R
        if (spec.hairStyle == 3) { circle(canvas, cx, cy, HEAD_R + 0.002f, spec.skin); return }
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = spec.hair
        p.style = Paint.Style.FILL
        canvas.drawArc(RectF(cx - r * 1.05f, cy - r * 1.05f, cx + r * 1.05f, cy + r * 1.05f), 90f, 240f, true, p)
        if (spec.hairStyle == 1) { // afro: bola completa detrás
            circle(canvas, cx, cy - r * 0.55f, r * 0.85f, spec.hair)
        }
        if (spec.hairStyle == 4) { // largo: melena baja
            capsule(canvas, cx - r * 0.95f, cy - r * 0.30f, cx - r * 0.95f, cy + r * 0.95f, 0.055f, spec.hair)
            capsule(canvas, cx + r * 0.95f, cy - r * 0.30f, cx + r * 0.95f, cy + r * 0.95f, 0.055f, spec.hair)
        }
    }

    // ---------- CARA ----------

    // Cara dibujada (estilo flat: ojos de punto, cejas, sonrisa)
    private fun drawFaceDrawn(canvas: Canvas, cx: Float, cy: Float, spec: AvatarSpec) {
        val r = HEAD_R
        // cejas
        val brow = Paint(Paint.ANTI_ALIAS_FLAG)
        brow.color = spec.hair
        brow.style = Paint.Style.STROKE
        brow.strokeCap = Paint.Cap.ROUND
        brow.strokeWidth = 0.012f
        canvas.drawLine(cx - r * 0.55f, cy - r * 0.28f, cx - r * 0.20f, cy - r * 0.32f, brow)
        canvas.drawLine(cx + r * 0.20f, cy - r * 0.32f, cx + r * 0.55f, cy - r * 0.28f, brow)
        // ojos
        val eye = Paint(Paint.ANTI_ALIAS_FLAG)
        eye.color = 0xFF212121.toInt()
        eye.style = Paint.Style.FILL
        canvas.drawCircle(cx - r * 0.38f, cy - r * 0.06f, r * 0.10f, eye)
        canvas.drawCircle(cx + r * 0.38f, cy - r * 0.06f, r * 0.10f, eye)
        // sonrisa
        val mouth = Paint(Paint.ANTI_ALIAS_FLAG)
        mouth.color = 0xFF212121.toInt()
        mouth.style = Paint.Style.STROKE
        mouth.strokeCap = Paint.Cap.ROUND
        mouth.strokeWidth = 0.014f
        canvas.drawArc(RectF(cx - r * 0.42f, cy + r * 0.08f, cx + r * 0.42f, cy + r * 0.60f), 25f, 130f, false, mouth)
        // barba/bigote opcional
        if (spec.beard) {
            val bp = Paint(Paint.ANTI_ALIAS_FLAG)
            bp.color = spec.hair
            bp.style = Paint.Style.FILL
            canvas.drawArc(RectF(cx - r * 0.62f, cy + r * 0.02f, cx + r * 0.62f, cy + r * 0.95f), 55f, 70f, true, bp)
        }
    }

    // Cara de la foto: montada con recorte circular (el alfa suave del
    // recorte elíptico del FaceCutter se funde con el círculo de piel)
    private fun drawFaceFromPhoto(canvas: Canvas, cx: Float, cy: Float, face: Bitmap) {
        val rf = HEAD_R * 0.97f
        val clip = Path()
        clip.addCircle(cx, cy, rf, Path.Direction.CCW)
        val save = canvas.save()
        canvas.clipPath(clip)
        val bp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(face, null, RectF(cx - rf, cy - rf, cx + rf, cy + rf), bp)
        canvas.restoreToCount(save)
    }

    // ---------- PRIMITIVAS ----------

    private fun capsule(canvas: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        p.style = Paint.Style.STROKE
        p.strokeWidth = width
        p.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(x0, y0, x1, y1, p)
    }

    private fun circle(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        p.style = Paint.Style.FILL
        canvas.drawCircle(x, y, radius, p)
    }
}
