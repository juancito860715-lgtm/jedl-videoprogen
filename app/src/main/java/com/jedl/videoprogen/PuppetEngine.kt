package com.jedl.videoprogen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import kotlin.math.max
import kotlin.math.sin

// ============================================================
// MOTOR DE ANIMACIÓN 2D — MUÑECO PRE-RIGGEADO (v0.4.1)
// ============================================================
// Intercambio de textura: la foto del usuario se pega RÍGIDA al hueso
// del torso y se mueve con el muñeco. Los huesos SON SOLO GUÍAS
// MATEMÁTICAS: no se dibujan ni relleno gris ni borde dorado.
// Todo es Canvas 2D nativo + android.graphics.Matrix, optimizado
// para 4 GB de RAM a 30 FPS.
// ============================================================

// Un hueso del muñeco: va de su articulación (origen local) a su punta.
class Bone(
    val name: String,
    val parent: String?,        // null = raíz (el torso, anclado a la cadera)
    val length: Float,          // largo en unidades del muñeco
    val width: Float,           // grosor (solo referencia matemática, NO se dibuja)
    val restDeg: Float,         // ángulo de reposo (grados, horario en pantalla)
    val attachX: Float,         // anclaje en el frame del padre (unidades)
    val attachY: Float
)

// Estado resuelto de un hueso en un cuadro concreto.
class BonePose(
    val joint: PointF,          // posición de la articulación (unidades)
    val tip: PointF,            // posición de la punta (unidades)
    val worldDeg: Float,        // rotación total acumulada (para la textura)
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

    // ---------- DIBUJO: SOLO LA TEXTURA (la foto), NUNCA los huesos ----------
    // Los huesos son guías matemáticas invisibles: el relleno gris y el
    // borde dorado fueron ELIMINADOS. Sin foto no se dibuja nada.
    fun drawPuppet(canvas: Canvas, solved: Map<String, BonePose>, skin: PuppetSkin?) {
        val sk = skin ?: return                        // sin foto: nada que dibujar
        val bmp = sk.torsoBmp ?: return
        val p = solved["torso"] ?: return
        val tex = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        // La foto se pega RÍGIDA al hueso del torso con android.graphics.Matrix:
        // sigue la posición y la rotación del hueso, pero nunca se dobla.
        val m = Matrix()
        m.setTranslate(p.joint.x, p.joint.y + sk.anchorPuppetY)   // pivote en el pecho del muñeco
        m.postRotate(p.worldDeg - 180f)              // solo el giro extra: la foto nunca se voltea
        m.postScale(sk.torsoScale, sk.torsoScale)    // escala foto -> unidades del muñeco
        m.postTranslate(-sk.anchorPhoto.x, -sk.anchorPhoto.y)
        canvas.drawBitmap(bmp, m, tex)
    }
}

// La "piel" del muñeco: la FOTO COMPLETA del usuario pegada al hueso
// del torso (intercambio de textura v1 — la foto entera por ahora).
class PuppetSkin private constructor(
    val torsoBmp: Bitmap?,       // la textura que sigue al hueso del torso
    val anchorPhoto: PointF,     // punto de la foto que se clava al hueso (coords de foto)
    val anchorPuppetY: Float,    // altura del hueso donde se clava (unidades del muñeco)
    val torsoScale: Float         // unidades de muñeco por píxel de foto
) {
    companion object {
        // Fallback (y v1): la foto completa, centrada sobre el pecho del
        // muñeco. Garantiza que la foto SIEMPRE se vea y se mueva con él.
        fun fullPhoto(photo: Bitmap): PuppetSkin = PuppetSkin(
            torsoBmp = photo,
            anchorPhoto = PointF(photo.width / 2f, photo.height / 2f),
            anchorPuppetY = -0.14f,                    // centro de la foto en el pecho
            torsoScale = 0.85f / photo.height          // la foto ocupa ~0.85 de alto del muñeco
        )
    }
}
