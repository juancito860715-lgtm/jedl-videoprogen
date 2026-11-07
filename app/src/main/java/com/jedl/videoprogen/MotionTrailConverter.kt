package com.jedl.videoprogen

import android.graphics.PointF
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.sqrt

// ============================================================
// CONVERTIDOR RASTRO -> PLANTILLA (ETAPA C, v0.5.0)
// ============================================================
// Convierte el rastro de 33 juntas de MediaPipe Pose (una lista de
// puntos NORMALIZADOS 0..1 por cada fotograma del video) en una
// PLANTILLA DE KEYFRAMES (MotionTemplate) aplicable al muñeco.
//
// Es la versión OFFLINE de lo que hacen Modif (Plask) y ToonDance
// en la nube: ellos suben el video a servidores con GPU; aquí la
// IA corre EN el teléfono (MediaPipe, Apache 2.0).
//
// MATEMÁTICA (rotoscopia estándar):
// 1. Ángulo de cada hueso del video: atan2(dx, dy) — misma
//    convención del rig (0 = abajo, 180 = arriba, horario +).
// 2. NORMALIZACIÓN: cada ángulo se hace RELATIVO al torso
//    (hombros -> caderas) para que la persona del video no arrastre
//    la inclinación global al muñeco.
// 3. SUAVIZADO EMA (alpha = 0.35): elimina el ruido del detector
//    sin retrasar el movimiento.
// 4. KEYFRAMES: se descartan los cuadros donde TODO cambió menos
//    de 3 grados (el interpolador los reconstruye solos).
//
// LIMITACIONES documentadas (regla del proyecto, nada inventado):
// - se clona el movimiento de UNA persona (la más visible);
// - el cuerpo entero debe verse en el video;
// - el modelo lite no da la inclinación de la cabeza con la
//   confianza necesaria: el hueso "head" queda en reposo.
// ============================================================
object MotionTrailConverter {

    private const val EMA_ALPHA = 0.35f
    private const val MIN_DELTA_DEG = 3f
    private const val MIN_VALID_FRAMES = 8

    // Pares (hueso superior, hueso inferior, juntas del video)
    private val ARM_L = intArrayOf(11, 13, 15)   // hombro, codo, muñeca izq.
    private val ARM_R = intArrayOf(12, 14, 16)
    private val LEG_L = intArrayOf(23, 25, 27)   // cadera, rodilla, tobillo izq.
    private val LEG_R = intArrayOf(24, 26, 28)
    private val SHOULDER_MID = intArrayOf(11, 12)
    private val HIP_MID = intArrayOf(23, 24)

    // trail: lista de fotogramas; cada fotograma = lista de 33 PointF(x,y) en 0..1
    // (o lista VACÍA si MediaPipe no detectó persona en ese cuadro).
    // Devuelve null si el video no sirve (poca detección).
    fun toTemplate(name: String, trail: List<List<PointF>>): MotionTemplate? {
        val valid = ArrayList<List<PointF>>(trail.size)
        for (f in trail) if (f.size >= 33) valid.add(f)
        if (valid.size < MIN_VALID_FRAMES) return null

        // 1) ángulos crudos por fotograma (relativos al torso) + bob/sway
        val raw = ArrayList<FloatArray>(valid.size)   // [angUL, angFL, angUR, angFR, angTL, angSL, angTR, angSR]
        val bobs = FloatArray(valid.size)
        val sways = FloatArray(valid.size)
        var hipScale = 0f
        for (f in valid) {
            val torsoDeg = angDeg(f[SHOULDER_MID[0]], f[SHOULDER_MID[1]], f[HIP_MID[0]], f[HIP_MID[1]])
            hipScale = maxOf(hipScale, dist(f[SHOULDER_MID[0]], f[SHOULDER_MID[1]], f[HIP_MID[0]], f[HIP_MID[1]]))
            raw.add(floatArrayOf(
                rel(f, ARM_L, torsoDeg), rel2(f, ARM_L, torsoDeg),
                rel(f, ARM_R, torsoDeg), rel2(f, ARM_R, torsoDeg),
                rel(f, LEG_L, torsoDeg), rel2(f, LEG_L, torsoDeg),
                rel(f, LEG_R, torsoDeg), rel2(f, LEG_R, torsoDeg)
            ))
            bobs[raw.size - 1] = f[HIP_MID[0]].y
            sways[raw.size - 1] = f[HIP_MID[0]].x
        }
        if (hipScale < 1e-4f) return null

        // 2) bob/sway normalizados a unidades de muñeco (torso del rig = 0.28)
        var bobAvg = 0f; var swayAvg = 0f
        for (i in bobs.indices) { bobAvg += bobs[i]; swayAvg += sways[i] }
        bobAvg /= bobs.size; swayAvg /= sways.size

        // 3) suavizado EMA
        val sm = ArrayList<FloatArray>(raw.size)
        var prev = raw[0].copyOf()
        sm.add(prev.copyOf())
        for (i in 1 until raw.size) {
            val cur = FloatArray(8)
            for (k in 0 until 8) cur[k] = prev[k] + EMA_ALPHA * (raw[i][k] - prev[k])
            sm.add(cur)
            prev = cur
        }

        // 4) keyframes: primer cuadro, cambios > umbral y último cuadro
        val frames = ArrayList<PoseFrame>()
        var lastStored: FloatArray? = null
        for (i in sm.indices) {
            val t = if (sm.size == 1) 0f else i.toFloat() / (sm.size - 1).toFloat()
            val a = sm[i]
            val ls = lastStored
            val bigChange = (ls == null) || (i == sm.size - 1) || run {
                var diff = 0f
                for (k in 0 until 8) diff = maxOf(diff, kotlin.math.abs(a[k] - ls[k]))
                diff >= MIN_DELTA_DEG
            }
            if (bigChange) {
                frames.add(PoseFrame(t, anglesToPose(a, bobs[i], sways[i], bobAvg, swayAvg, hipScale)))
                lastStored = a
            }
        }

        // guardado de seguridad: sin keyframes no hay plantilla
        if (frames.isEmpty()) return null
        return MotionTemplate(name, frames, loop = false)
    }

    // Mapa de ángulos del rig a partir de los 8 ángulos convertidos.
    // La foto del usuario es el cuerpo: los ángulos de brazos/piernas
    // mueven la MALLA (con los pesos LBS nuevos).
    private fun anglesToPose(
        a: FloatArray, bobRaw: Float, swayRaw: Float,
        bobAvg: Float, swayAvg: Float, hipScale: Float
    ): Pose {
        val m = LinkedHashMap<String, Float>()
        // El rig usa: restDeg=180 en brazo/pierna superior (cuelgan) y 0 en
        // inferior. El ángulo relativo YA compensa el torso: solo hay que
        // restar el reposo (los ángulos del video son absolutos respecto
        // al torso, y el torso del rig está en reposo vertical).
        m["armUL"] = a[0] - 180f
        m["foreArmL"] = a[1]
        m["armUR"] = a[2] - 180f
        m["foreArmR"] = a[3]
        m["thighL"] = a[4] - 180f
        m["shinL"] = a[5]
        m["thighR"] = a[6] - 180f
        m["shinR"] = a[7]
        m["torso"] = 0f
        m["head"] = 0f
        // bob/sway: desplazamiento de la cadera en unidades de muñeco
        val bob = ((bobAvg - bobRaw) / hipScale * 0.28f).coerceIn(-0.06f, 0.06f)
        val sway = ((swayRaw - swayAvg) / hipScale * 0.28f).coerceIn(-0.06f, 0.06f)
        return Pose(m, bob, sway)
    }

    // Ángulo del hueso superior (hombro->codo) relativo al torso.
    private fun rel(f: List<PointF>, joints: IntArray, torsoDeg: Float): Float {
        val ang = angDeg(f[joints[0]], f[joints[1]])
        return wrap(ang - torsoDeg)
    }

    // Ángulo del hueso inferior (codo->muñeca) relativo al superior.
    private fun rel2(f: List<PointF>, joints: IntArray, torsoDeg: Float): Float {
        val upper = angDeg(f[joints[0]], f[joints[1]])
        val lower = angDeg(f[joints[1]], f[joints[2]])
        return wrap(lower - upper)
    }

    // Ángulo de la dirección p0->p1 en la convención del rig (grados):
    // 0 = abajo, 180 = arriba, positivo = horario en pantalla.
    private fun angDeg(p0: PointF, p1: PointF): Float {
        val dx = p1.x - p0.x
        val dy = p1.y - p0.y
        return Math.toDegrees(atan2(dx, dy).toDouble()).toFloat()
    }

    private fun angDeg(a: PointF, b: PointF, c: PointF, d: PointF): Float {
        return angDeg(
            PointF((a.x + b.x) / 2f, (a.y + b.y) / 2f),
            PointF((c.x + d.x) / 2f, (c.y + d.y) / 2f)
        )
    }

    private fun wrap(x: Float): Float {
        var a = x
        while (a > 180f) a -= 360f
        while (a <= -180f) a += 360f
        return a
    }

    private fun dist(a: PointF, b: PointF, c: PointF, d: PointF): Float {
        val mx = (a.x + b.x) / 2f - (c.x + d.x) / 2f
        val my = (a.y + b.y) / 2f - (c.y + d.y) / 2f
        return sqrt(mx * mx + my * my)
    }
}
