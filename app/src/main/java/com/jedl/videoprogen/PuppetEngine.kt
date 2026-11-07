package com.jedl.videoprogen

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.PointF
import kotlin.math.max
import kotlin.math.sin

// ============================================================
// MOTOR DE ANIMACIÓN 2D — MUÑECO PRE-RIGGEADO (v0.5.0)
// ============================================================
// La foto del usuario se deforma con una MALLA (SkinnedMesh:
// drawVertices + BitmapShader). Los huesos SON SOLO GUÍAS
// MATEMÁTICAS: no se dibujan ni relleno gris ni borde dorado.
// Todo es Canvas 2D nativo + android.graphics.Matrix, optimizado
// para 4 GB de RAM a 30 FPS.
//
// NOVEDADES v0.5.0 (ingeniería de las apps referentes):
// 1. PLANTILLAS POR KEYFRAMES: una plantilla ya no es solo un
//    nombre fijo (idle/walk/wave): es una LISTA de poses con su
//    instante de tiempo (PoseFrame), interpoladas con easing
//    suave. Es el "motion tweening" de Draw Cartoons 2 y el
//    timeline de LoongBones/DragonBones (estándar MIT).
// 2. MOCAP: el clonador de video produce una MotionTemplate con
//    las poses detectadas por MediaPipe (Etapa C, lo que hacen
//    Modif/Plask/ToonDance pero 100% OFFLINE).
// 3. IK (cinemática inversa): arrastrar la mano o el pie y que
//    codo/hombro (o rodilla/cadera) se ajusten solos. Técnica
//    FABRIK (algoritmo público, tutorial oficial Khronos/Vulkan
//    y SkeletonModification2DFABRIK de Godot), el sello de
//    Chroma Toons.
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

// ---------- KEYFRAMES (v0.5.0) ----------
// Una pose capturada en un instante t (0..1) de la animación.
class PoseFrame(val t: Float, val pose: Pose)

// Plantilla por keyframes: la producen el MOCAP DE VIDEO (Etapa C)
// o el editor de poses del usuario. t normalizado 0..1 sobre el clip.
class MotionTemplate(
    val name: String,
    val frames: List<PoseFrame>,   // SIEMPRE ordenadas por t creciente
    val loop: Boolean = false
)

// Plantilla ACTIVA: builtin ("idle"/"walk"/"wave") o plantilla del usuario.
// data class: la igualdad estructural permite comparar la seleccionada.
data class ActiveTemplate(val builtin: String?, val mocap: MotionTemplate?) {
    companion object {
        fun of(name: String): ActiveTemplate = ActiveTemplate(name, null)
    }
}

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

    // Pose de la plantilla ACTIVA (builtin o de usuario) en el progreso p.
    fun poseForActive(at: ActiveTemplate, p: Float): Pose {
        val mt = at.mocap
        if (mt != null) return poseAtTemplate(mt, p)
        return poseFor(at.builtin ?: "idle", p)
    }

    // Interpolación entre keyframes con easing suave (smoothstep):
    // el "tweening" de Draw Cartoons 2 / timeline de LoongBones.
    fun poseAtTemplate(mt: MotionTemplate, p: Float): Pose {
        val frames = mt.frames
        if (frames.isEmpty()) return poseFor("idle", p)
        if (frames.size == 1) return frames[0].pose
        val t = p.coerceIn(0f, 1f)
        // p hasta el final de la lista (sin loop: la última pose se mantiene)
        val last = frames[frames.size - 1]
        if (t >= last.t) return last.pose
        if (t <= frames[0].t) return frames[0].pose
        // buscar el par de keyframes que rodea a t
        var i = 0
        while (i < frames.size - 1 && frames[i + 1].t < t) i++
        val f0 = frames[i]
        val f1 = frames[i + 1]
        val span = f1.t - f0.t
        var u = if (span <= 0f) 0f else (t - f0.t) / span
        u = u * u * (3f - 2f * u)   // smoothstep: arranque y llegada suaves
        return lerpPose(f0.pose, f1.pose, u)
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

    // ---------- IK: FABRIK DE 2 HUESOS (v0.5.0) ----------
    // El usuario arrastra la MANO (o el PIE); el codo (o la rodilla) se
    // acomoda solo. Algoritmo FABRIK (algoritmo público; referencia:
    // tutorial oficial Khronos/Vulkan y SkeletonModification2DFABRIK de
    // Godot): pasadas hacia adelante y atrás sobre las posiciones de
    // los huesos hasta que la punta alcance el objetivo.
    //
    // upperName/foreName: cadena de 2 huesos (p.ej. "armUR"+"foreArmR").
    // angles: pose actual completa. targetX/targetY: objetivo en
    // unidades del muñeco (coordenadas de pantalla: y crece hacia abajo).
    // Devuelve la pose con SOLO los 2 ángulos de la cadena actualizados.
    fun solveTwoBoneIk(
        upperName: String, foreName: String,
        angles: Map<String, Float>,
        targetX: Float, targetY: Float
    ): Pose {
        val upper = PuppetRig.HUMANOID.bones.firstOrNull { it.name == upperName } ?: return Pose(angles, 0f, 0f)
        val fore = PuppetRig.HUMANOID.bones.firstOrNull { it.name == foreName } ?: return Pose(angles, 0f, 0f)
        val bob = 0f
        val sway = 0f
        // 1) resolver la pose ACTUAL para obtener posiciones de los huesos
        val solved = PuppetRig.HUMANOID.solve(angles, 0f, 0f)
        val up = solved[upperName] ?: return Pose(angles, bob, sway)
        val fo = solved[foreName] ?: return Pose(angles, bob, sway)
        var p0 = PointF(up.joint.x, up.joint.y)          // hombro / cadera
        var p1 = PointF(fo.joint.x, fo.joint.y)          // codo / rodilla
        var p2 = PointF(fo.tip.x, fo.tip.y)              // mano / pie
        val l1 = upper.length
        val l2 = fore.length

        // 2) objetivo alcanzable: limitar al radio máximo de la cadena
        val dx0 = targetX - p0.x
        val dy0 = targetY - p0.y
        val d0 = kotlin.math.sqrt(dx0 * dx0 + dy0 * dy0)
        var tx = targetX
        var ty = targetY
        val maxReach = (l1 + l2) * 0.999f
        if (d0 > maxReach) {
            tx = p0.x + dx0 / d0 * maxReach
            ty = p0.y + dy0 / d0 * maxReach
        }

        // 3) FABRIK: 10 iteraciones (adelante y atrás)
        for (iter in 0 until 10) {
            // BACKWARD: la punta va al objetivo, el codo la sigue
            p2 = PointF(tx, ty)
            var dx = p1.x - p2.x
            var dy = p1.y - p2.y
            var len = kotlin.math.sqrt(dx * dx + dy * dy)
            if (len > 1e-6f) { p1 = PointF(p2.x + dx / len * l2, p2.y + dy / len * l2) }
            // FORWARD: la raíz queda fija, el codo se recoloca
            dx = p1.x - p0.x
            dy = p1.y - p0.y
            len = kotlin.math.sqrt(dx * dx + dy * dy)
            if (len > 1e-6f) { p1 = PointF(p0.x + dx / len * l1, p0.y + dy / len * l1) }
            dx = p2.x - p1.x
            dy = p2.y - p1.y
            len = kotlin.math.sqrt(dx * dx + dy * dy)
            if (len > 1e-6f) { p2 = PointF(p1.x + dx / len * l2, p1.y + dy / len * l2) }
        }

        // 4) posiciones finales -> ángulos del rig (convención: 0=abajo,
        //    180=arriba, positivo horario = atan2(dx, dy))
        val angUpper = angDeg(p0, p1)      // mundo del hueso superior
        val angFore = angDeg(p1, p2)       // mundo del antebrazo/espinilla
        // ángulo de ANIMACIÓN = mundo − mundo del padre − reposo
        val parentPose = upper.parent?.let { solved[it] }
        val parentWorld = parentPose?.worldDeg ?: 0f
        val animUpper = wrapDeg(angUpper - parentWorld - upper.restDeg)
        val animFore = wrapDeg(angFore - angUpper - fore.restDeg)

        val m = LinkedHashMap<String, Float>(angles)
        m[upperName] = animUpper
        m[foreName] = animFore
        return Pose(m, bob, sway)
    }

    // Ángulo de la dirección p0->p1 en la convención del rig (grados).
    private fun angDeg(p0: PointF, p1: PointF): Float {
        val dx = p1.x - p0.x
        val dy = p1.y - p0.y
        return Math.toDegrees(kotlin.math.atan2(dx.toDouble(), dy.toDouble())).toFloat()
    }

    // Normaliza un ángulo a (-180, 180] para interpolar limpio.
    private fun wrapDeg(a: Float): Float {
        var x = a
        while (x > 180f) x -= 360f
        while (x <= -180f) x += 360f
        return x
    }

    // ---------- DIBUJO: SOLO LA MALLA DEFORMADA (la foto), NUNCA los huesos ----------
    // Los huesos son guías matemáticas invisibles. Sin foto no se dibuja nada.
    fun drawPuppet(canvas: Canvas, solved: Map<String, BonePose>, mesh: SkinnedMesh?) {
        val m = mesh ?: return
        m.updateVertices(solved)   // los vértices siguen la matriz de su hueso
        m.draw(canvas)              // drawVertices + BitmapShader (con fallback seguro)
    }
}
