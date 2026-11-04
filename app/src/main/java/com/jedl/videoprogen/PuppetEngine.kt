package com.jedl.videoprogen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ============================================================
// MOTOR DE ANIMACIÓN 2D — MUÑECO PRE-RIGGEADO (v0.4.0)
// ============================================================
// Estrategia "Intercambio de Textura": la foto del usuario YA NO se
// corta en pedazos ni se rota por partes. En su lugar:
//   1) Un muñeco de HUESOS con articulaciones perfectas se anima con
//      jerarquía de matrices (NUNCA se rompe, NUNCA deja huecos).
//   2) La cara y el torso de la foto se pegan como TEXTURA RÍGIDA
//      sobre los huesos cabeza/torso: se mueven con ellos sin doblarse.
// Todo es Canvas 2D nativo + android.graphics.Matrix: sin librerías
// pesadas, optimizado para un móvil de 4 GB de RAM a 30 FPS.
// ============================================================

// Un hueso del muñeco: va de su articulación (origen local) a su punta.
class Bone(
    val name: String,
    val parent: String?,        // null = raíz (el torso, anclado a la cadera)
    val length: Float,          // largo en unidades del muñeco
    val width: Float,           // grosor de la cápsula (0 = hueso invisible)
    val restDeg: Float,         // ángulo de reposo (grados, horario en pantalla)
    val attachX: Float,         // anclaje en el frame del padre (unidades)
    val attachY: Float
)

// Estado resuelto de un hueso en un cuadro concreto.
class BonePose(
    val joint: PointF,          // posición de la articulación (unidades)
    val tip: PointF,            // posición de la punta (unidades)
    val worldDeg: Float,        // rotación total acumulada (para texturas)
    val matrix: Matrix          // matriz acumulada (los hijos la heredan)
)

// Esqueleto: resuelve la jerarquía completa con android.graphics.Matrix.
// Los huesos se declaran SIEMPRE padres antes que hijos.
class Skeleton(val bones: List<Bone>) {

    fun solve(angles: Map<String, Float>, rootX: Float, rootY: Float): LinkedHashMap<String, BonePose> {
        val out = LinkedHashMap<String, BonePose>()
        for (b in bones) {
            val parentPose = b.parent?.let { out[it] }
            val m = Matrix()
            var parentDeg = 0f
            if (parentPose == null) {
                m.setTranslate(rootX, rootY)
            } else {
                m.set(parentPose.matrix)
                parentDeg = parentPose.worldDeg
            }
            m.postTranslate(b.attachX, b.attachY)
            val anim = angles[b.name] ?: 0f
            m.postRotate(b.restDeg + anim)
            val pts = floatArrayOf(0f, 0f, 0f, b.length)
            m.mapPoints(pts)
            out[b.name] = BonePose(PointF(pts[0], pts[1]), PointF(pts[2], pts[3]), parentDeg + b.restDeg + anim, m)
        }
        return out
    }
}

// Muñecos base. Convención de ángulos: 0 = hacia abajo, 180 = hacia
// arriba, positivo = giro horario en pantalla. Altura total ≈ 0.85.
object PuppetRig {
    val HUMANOID = Skeleton(
        listOf(
            // nombre      padre      largo   grosor  reposo  anclajeX anclajeY
            Bone("torso",    null,     0.280f, 0.170f, 180f,  0.00f, 0.000f), // cadera -> cuello
            Bone("head",     "torso",  0.115f, 0.000f,   0f,  0.00f, 0.280f), // cuello -> centro de la cabeza
            Bone("armUL",    "torso",  0.145f, 0.055f, 180f,  0.10f, 0.260f), // hombro izquierdo (pantalla)
            Bone("foreArmL", "armUL",  0.135f, 0.050f,   0f,  0.00f, 0.145f), // codo -> mano izquierda
            Bone("armUR",    "torso",  0.145f, 0.055f, 180f, -0.10f, 0.260f), // hombro derecho (pantalla)
            Bone("foreArmR", "armUR",  0.135f, 0.050f,   0f,  0.00f, 0.145f), // codo -> mano derecha
            Bone("thighL",   "torso",  0.190f, 0.070f, 180f,  0.07f, 0.020f), // cadera -> rodilla izquierda
            Bone("shinL",    "thighL", 0.180f, 0.058f,   0f,  0.00f, 0.190f), // rodilla -> pie izquierdo
            Bone("thighR",   "torso",  0.190f, 0.070f, 180f, -0.07f, 0.020f), // cadera -> rodilla derecha
            Bone("shinR",    "thighR", 0.180f, 0.058f,   0f,  0.00f, 0.190f)  // rodilla -> pie derecho
        )
    )
}

// Pose de un cuadro: ángulos por hueso + rebote (bob) y balanceo (sway) de la raíz.
class Pose(val angles: Map<String, Float>, val bob: Float, val sway: Float)

object PuppetEngine {

    private val boneByName: Map<String, Bone> = PuppetRig.HUMANOID.bones.associateBy { it.name }

    // ---------- PLANTILLAS DE MOVIMIENTO (p = progreso 0..1 del clip de 5 s) ----------
    fun poseFor(template: String, p: Float): Pose {
        val t = p * 2f * Math.PI.toFloat()
        val a = LinkedHashMap<String, Float>()
        var bob = 0f
        var sway = 0f
        when (template) {
            "wave" -> {  // SALUDAR: brazo derecho (pantalla) levantado, la mano saluda
                a["armUR"] = -150f + sin(t) * 6f          // brazo arriba a la derecha
                a["foreArmR"] = 20f + sin(t * 2f) * 38f   // el codo dobla: la mano saluda
                a["armUL"] = sin(t) * 3f
                a["foreArmL"] = sin(t) * 4f
                a["head"] = 4f + sin(t) * 2f              // cabeza ladeada hacia el saludo
                a["torso"] = sin(t) * 1.5f
                bob = sin(t) * 0.006f
                sway = -sin(t) * 0.004f
            }
            "walk" -> {  // CAMINAR: pasos alternos con rodilla doblada + rebote
                val c = t * 2f                            // 2 ciclos de paso por clip
                val stepL = max(0f, sin(c))
                val stepR = max(0f, -sin(c))
                a["thighL"] = 7f * stepL
                a["shinL"] = -32f * stepL                 // la pierna levantada recoge el pie
                a["thighR"] = 7f * stepR
                a["shinR"] = -32f * stepR
                a["armUL"] = -12f * sin(c)                // brazos en contrafase
                a["foreArmL"] = -7f * sin(c)
                a["armUR"] = 12f * sin(c)
                a["foreArmR"] = 7f * sin(c)
                a["torso"] = sin(c) * 2.5f
                a["head"] = -sin(c) * 2f
                bob = 0.010f * (1f - max(stepL, stepR))   // rebote al apoyar el pie
                sway = 0.012f * sin(c)                    // balanceo de cadera
            }
            else -> {  // IDLE (reposo): respiración suave
                a["armUL"] = sin(t) * 2.5f
                a["foreArmL"] = sin(t) * 3f
                a["armUR"] = -sin(t) * 2.5f
                a["foreArmR"] = -sin(t) * 3f
                a["torso"] = sin(t) * 1f
                a["head"] = -sin(t) * 1.2f
                bob = sin(t) * 0.006f
                sway = sin(t) * 0.003f
            }
        }
        return Pose(a, bob, sway)
    }

    // Mezcla lineal entre dos poses (transición suave entre plantillas)
    fun lerpPose(a: Pose, b: Pose, t: Float): Pose {
        if (t >= 1f) return b
        if (t <= 0f) return a
        val keys = a.angles.keys.toSet() + b.angles.keys
        val m = LinkedHashMap<String, Float>()
        for (k in keys) m[k] = (a.angles[k] ?: 0f) * (1f - t) + (b.angles[k] ?: 0f) * t
        return Pose(m, a.bob * (1f - t) + b.bob * t, a.sway * (1f - t) + b.sway * t)
    }

    // ---------- DIBUJO DEL MUÑECO ----------
    private val RIM = Color.parseColor("#F5B841")     // borde dorado
    private val BODY = Color.parseColor("#3A3E46")   // cuerpo del muñeco

    // El canvas ya viene trasladado a la cadera y escalado a unidades del muñeco.
    fun drawPuppet(canvas: Canvas, solved: Map<String, BonePose>, skin: PuppetSkin?) {
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = RIM; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BODY; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BODY }
        val rimSolid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RIM }
        val tex = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        fun capsule(name: String) {
            val b = boneByName[name] ?: return
            val p = solved[name] ?: return
            if (b.width <= 0f) return
            rim.strokeWidth = b.width + 0.020f
            fill.strokeWidth = b.width
            canvas.drawLine(p.joint.x, p.joint.y, p.tip.x, p.tip.y, rim)
            canvas.drawLine(p.joint.x, p.joint.y, p.tip.x, p.tip.y, fill)
        }

        // 1) PIERNAS (debajo de todo)
        capsule("thighL"); capsule("shinL"); capsule("thighR"); capsule("shinR")

        // 2) TORSO + textura del torso pegada al hueso (rígida: no se dobla)
        capsule("torso")
        val sk = skin
        val torsoTex = sk?.torsoBmp
        if (sk != null && torsoTex != null) {
            val p = solved["torso"]
            if (p != null) {
                val m = Matrix()
                m.setTranslate(p.joint.x, p.joint.y)          // pivote: cadera del muñeco
                m.postRotate(p.worldDeg - 180f)               // solo el giro extra (la foto nunca se voltea)
                m.postScale(sk.torsoScale, sk.torsoScale)
                m.postTranslate(-sk.hipCenter.x, -sk.hipCenter.y)
                canvas.drawBitmap(torsoTex, m, tex)
            }
        }

        // 3) CABEZA (círculo) + textura de la cara
        val hp = solved["head"]
        if (hp != null) {
            val r = 0.085f
            canvas.drawCircle(hp.tip.x, hp.tip.y, r + 0.010f, rimSolid)
            canvas.drawCircle(hp.tip.x, hp.tip.y, r, solid)
            val headTex = sk?.headBmp
            if (sk != null && headTex != null) {
                val m = Matrix()
                m.setTranslate(hp.tip.x, hp.tip.y)            // pivote: centro de la cabeza
                m.postRotate(hp.worldDeg - 180f)
                m.postScale(sk.headScale, sk.headScale)
                m.postTranslate(-sk.headCenter.x, -sk.headCenter.y)
                canvas.drawBitmap(headTex, m, tex)
            }
        }

        // 4) BRAZOS (encima del torso)
        capsule("armUL"); capsule("foreArmL"); capsule("armUR"); capsule("foreArmR")
    }
}

// Segmento cápsula para las máscaras (ax,ay)-(bx,by) radio r
private data class Capsule(val ax: Float, val ay: Float, val bx: Float, val by: Float, val r: Float)

// La "piel" del muñeco: cara y torso recortados de la foto del usuario.
// Los bitmaps son recortes pequeños, seguros para 4 GB de RAM.
class PuppetSkin(subject: Bitmap, jts: List<PointF>) {

    val headBmp: Bitmap?      // recorte de la cara (con transparencia)
    val torsoBmp: Bitmap?     // recorte del torso (sin brazos ni piernas)
    val headCenter: PointF    // centro de la cara en coordenadas de la foto
    val hipCenter: PointF     // centro de la cadera en coordenadas de la foto
    val headScale: Float     // unidades de muñeco por píxel de foto
    val torsoScale: Float

    init {
        val sL = jts[11]; val sR = jts[12]
        val hL = jts[23]; val hR = jts[24]
        hipCenter = PointF((hL.x + hR.x) / 2f, (hL.y + hR.y) / 2f)
        val shoulderW = max(1f, hypot(sL.x - sR.x, sL.y - sR.y))
        val torsoLen = max(1f, hypot((sL.x + sR.x) / 2f - hipCenter.x, (sL.y + sR.y) / 2f - hipCenter.y))
        torsoScale = 0.30f / torsoLen

        // --- TORSO: caja hombros -> caderas, borrando brazos y piernas ---
        val padX = 0.20f * shoulderW
        val left = (min(min(sL.x, sR.x), min(hL.x, hR.x)) - padX).toInt().coerceIn(0, subject.width - 2)
        val right = (max(max(sL.x, sR.x), max(hL.x, hR.x)) + padX).toInt().coerceIn(left + 2, subject.width)
        val top = (min(sL.y, sR.y) - 0.40f * shoulderW).toInt().coerceIn(0, subject.height - 2)
        val bottom = (max(hL.y, hR.y) + 0.16f * shoulderW).toInt().coerceIn(top + 2, subject.height)
        torsoBmp = maskedCrop(subject, left, top, right, bottom, limbCapsules(jts))

        // --- CABEZA: caja alrededor de las orejas ---
        val earL = jts[7]; val earR = jts[8]
        var earD = hypot(earL.x - earR.x, earL.y - earR.y)
        if (earD < 0.25f * shoulderW) earD = 0.25f * shoulderW
        headCenter = PointF((earL.x + earR.x) / 2f, (earL.y + earR.y) / 2f - earD * 0.05f)
        val hl = (headCenter.x - 1.05f * earD).toInt().coerceIn(0, subject.width - 2)
        val hr = (headCenter.x + 1.05f * earD).toInt().coerceIn(hl + 2, subject.width)
        val ht = (headCenter.y - 1.35f * earD).toInt().coerceIn(0, subject.height - 2)
        val hb = (headCenter.y + 1.35f * earD).toInt().coerceIn(ht + 2, subject.height)
        headBmp = plainCrop(subject, hl, ht, hr, hb)
        headScale = 0.27f / max(1, hr - hl)
    }

    // Recorte simple (la cabeza no necesita máscara: el fondo ya es transparente)
    private fun plainCrop(src: Bitmap, l: Int, t: Int, r: Int, b: Int): Bitmap? = try {
        val cw = r - l; val ch = b - t
        val px = IntArray(cw * ch)
        src.getPixels(px, 0, cw, l, t, cw, ch)
        Bitmap.createBitmap(px, cw, ch, Bitmap.Config.ARGB_8888)
    } catch (e: Exception) { null }
}

// Cápsulas de brazos y piernas (se borran del torso para que no queden
// brazos "dobles": los de la foto quietos + los del muñeco en movimiento)
private fun limbCapsules(jts: List<PointF>): List<Capsule> {
    fun capsule(a: PointF, b: PointF, f: Float): Capsule {
        val len = max(1f, hypot(b.x - a.x, b.y - a.y))
        return Capsule(a.x, a.y, b.x, b.y, len * f + 6f)
    }
    return listOf(
        capsule(jts[11], jts[13], 0.22f), capsule(jts[13], jts[15], 0.24f),  // brazo izquierdo
        capsule(jts[12], jts[14], 0.22f), capsule(jts[14], jts[16], 0.24f),  // brazo derecho
        capsule(jts[23], jts[25], 0.30f), capsule(jts[25], jts[27], 0.28f), // pierna izquierda
        capsule(jts[24], jts[26], 0.30f), capsule(jts[26], jts[28], 0.28f)  // pierna derecha
    )
}

// Recorta una caja de la foto con transparencia y borra los píxeles que
// caen dentro de las cápsulas.
private fun maskedCrop(src: Bitmap, l: Int, t: Int, r: Int, b: Int, caps: List<Capsule>): Bitmap? = try {
    val cw = r - l; val ch = b - t
    val px = IntArray(cw * ch)
    src.getPixels(px, 0, cw, l, t, cw, ch)
    for (j in 0 until ch) {
        val wy = (t + j).toFloat()
        for (i in 0 until cw) {
            val idx = j * cw + i
            if (px[idx] ushr 24 == 0) continue          // ya es transparente
            val wx = (l + i).toFloat()
            for (cap in caps) {
                val dx = cap.bx - cap.ax
                val dy = cap.by - cap.ay
                val len2 = dx * dx + dy * dy
                val tt = if (len2 <= 0f) 0f else (((wx - cap.ax) * dx + (wy - cap.ay) * dy) / len2).coerceIn(0f, 1f)
                val ex = wx - (cap.ax + tt * dx)
                val ey = wy - (cap.ay + tt * dy)
                if (ex * ex + ey * ey <= cap.r * cap.r) { px[idx] = 0; break }
            }
        }
    }
    Bitmap.createBitmap(px, cw, ch, Bitmap.Config.ARGB_8888)
} catch (e: Exception) { null }
