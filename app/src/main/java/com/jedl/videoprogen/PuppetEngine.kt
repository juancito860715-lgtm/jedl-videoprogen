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
//
// NOVEDADES v0.6.0 (FASE B): 5 PLANTILLAS NUEVAS (percusión,
// correr, saltar, bailar, celebrar — 8 en total) y las
// DIRECCIONES del caminar/correr (perfil derecha/espalda/frente/
// 3/4; el perfil izquierdo es ESPEJO en el renderer).
// Convención VERIFICADA contra walk/wave existentes: 0=abajo,
// 180=arriba, positivo=horario; los límites articulares de
// clampJoint() se respetan en TODAS las fórmulas.
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
            // v0.6.0 (FASE B): 5 PLANTILLAS NUEVAS. Signos VERIFICADOS contra
            // walk/wave: brazo izquierdo arriba = ángulo POSITIVO (espejo del
            // derecho), rodillas solo negativas, codos dentro de clampJoint.
            "drum" -> {  // PERCUSIÓN: manos golpeando alternadas al ritmo
                val beat = t * 4f                         // 4 tiempos por clip
                val bL = max(0f, sin(beat))
                val bR = max(0f, -sin(beat))
                a["armUL"] = 42f + bL * 10f               // brazos abiertos al frente
                a["armUR"] = -42f - bR * 10f
                a["foreArmL"] = -95f + bL * 60f           // el codo dobla: la mano golpea
                a["foreArmR"] = 95f - bR * 60f
                a["head"] = 3f + sin(beat * 2f) * 2f      // cabeza marcando el ritmo
                a["torso"] = sin(beat) * 2f
                a["thighL"] = 5f                          // postura firme, piernas abiertas
                a["thighR"] = -5f
                bob = -max(bL, bR) * 0.008f               // rebote con el golpe
                sway = sin(beat) * 0.004f
            }
            "run" -> {  // CORRER: zancada amplia, brazos doblados en contrafase
                val c = t * 3f                            // 3 zancadas por clip
                val stepL = max(0f, sin(c))
                val stepR = max(0f, -sin(c))
                a["thighL"] = 22f * stepL + 6f
                a["thighR"] = 22f * stepR + 6f
                a["shinL"] = -55f * stepL
                a["shinR"] = -55f * stepR
                a["armUL"] = -18f - 20f * sin(c)          // bombeo de brazos
                a["foreArmL"] = -70f                      // codos doblados a 70 grados
                a["armUR"] = -18f + 20f * sin(c)
                a["foreArmR"] = 70f
                a["torso"] = sin(c) * 3f
                a["head"] = -sin(c) * 2f
                bob = -0.012f * max(stepL, stepR)         // vuelo: el cuerpo sube
                sway = 0.014f * sin(c)
            }
            "jump" -> {  // SALTAR: agacharse -> impulso -> vuelo -> aterrizar (2 saltos)
                val c = t * 2f
                val air = max(0f, sin(c))                 // 0 en el suelo, 1 en el punto más alto
                val squat = max(0f, -sin(c))             // agachado antes de saltar
                a["thighL"] = 12f * air - 25f * squat
                a["thighR"] = 12f * air - 25f * squat
                a["shinL"] = -10f * air - 85f * squat     // rodillas muy dobladas en la agachada
                a["shinR"] = -10f * air - 85f * squat
                a["armUL"] = 20f - squat * 30f + air * 130f   // brazos arriba en el vuelo
                a["armUR"] = -20f + squat * 30f - air * 130f
                a["foreArmL"] = -10f - squat * 20f
                a["foreArmR"] = 10f + squat * 20f
                a["torso"] = -squat * 5f + air * 2f
                a["head"] = -squat * 3f
                bob = -0.045f * air                       // el cuerpo sube en el vuelo
                sway = 0f
            }
            "dance" -> {  // BAILAR: brazos arriba alternando, cadera marcada
                val c = t * 2f
                val s = sin(c)
                a["armUL"] = 100f + 45f * s               // un brazo arriba...
                a["foreArmL"] = -25f + 15f * s
                a["armUR"] = -100f + 45f * s              // ...y el otro en contrafase
                a["foreArmR"] = 25f - 15f * s
                a["thighL"] = 12f * max(0f, s)            // pies marcando el suelo
                a["shinL"] = -30f * max(0f, s)
                a["thighR"] = 12f * max(0f, -s)
                a["shinR"] = -30f * max(0f, -s)
                a["torso"] = s * 5f
                a["head"] = -s * 4f
                bob = 0.006f * kotlin.math.abs(s)
                sway = 0.018f * s                        // cadera marcada
            }
            "cheer" -> {  // CELEBRAR: ambos brazos arriba, saltitos
                val c = t * 2f
                val s = sin(c)
                val hop = max(0f, s)
                a["armUL"] = 135f + s * 10f
                a["armUR"] = -135f - s * 10f
                a["foreArmL"] = -20f + s * 8f
                a["foreArmR"] = 20f - s * 8f
                a["thighL"] = 8f * hop
                a["thighR"] = 8f * hop
                a["shinL"] = -20f * hop
                a["shinR"] = -20f * hop
                a["head"] = -4f                           // mirando arriba
                a["torso"] = s * 1.5f
                bob = -0.02f * hop
                sway = s * 0.005f
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

    // v0.6.0 (FASE B): DIRECCIÓN del caminar/correr (pedido del dueño:
    // ver TODAS las direcciones en pantalla). Se aplica SOBRE la pose ya
    // interpolada (la dirección es la VISTA, no el movimiento):
    //   "der"/"izq"  perfil (la vista izquierda es ESPEJO en el renderer)
    //   "frente"     piernas levantan EN EL SITIO, codos algo abiertos
    //   "espalda"    mismos ángulos que frente (el renderer tapa la cara)
    //   "3/4"        torso y cabeza girados unos grados (diagonal aprox.)
    // Las demás plantillas (percusión, bailar...) y el MOCAP DE VIDEO no se
    // tocan: el espejo "izq" también les funciona (flip del renderer).
    fun applyDirection(pose: Pose, builtin: String, direction: String): Pose {
        if (direction == "der" || direction == "izq") return pose
        if (builtin != "walk" && builtin != "run") return pose
        val m = LinkedHashMap<String, Float>(pose.angles)
        when (direction) {
            "frente", "espalda" -> {
                m["thighL"] = (m["thighL"] ?: 0f) * 0.45f   // sin zancada: levanta en el sitio
                m["thighR"] = (m["thighR"] ?: 0f) * 0.45f
                m["shinL"] = (m["shinL"] ?: 0f) * 0.75f
                m["shinR"] = (m["shinR"] ?: 0f) * 0.75f
                m["armUL"] = (m["armUL"] ?: 0f) * 0.6f + 8f  // brazos pegados, balanceo corto
                m["armUR"] = (m["armUR"] ?: 0f) * 0.6f - 8f
                m["foreArmL"] = ((m["foreArmL"] ?: 0f) - 18f).coerceIn(-150f, 5f)
                m["foreArmR"] = ((m["foreArmR"] ?: 0f) + 18f).coerceIn(-5f, 150f)
            }
            "3/4" -> {
                m["torso"] = (m["torso"] ?: 0f) + 4f         // giro de diagonal aproximado
                m["head"] = (m["head"] ?: 0f) - 3f
            }
        }
        return Pose(m, pose.bob, pose.sway)
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
    // Godot): pasadas hacia adelante y atrás sobre las posiciones de los
    // huesos hasta que la punta alcance el objetivo.
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
        // v0.5.1 (FASE A): LÍMITES ARTICULARES — el codo dobla solo hacia el
        // cuerpo y la rodilla solo hacia atrás (sentidos VERIFICADOS en las
        // plantillas walk/wave del propio rig: foreArmR=+20..58 en wave,
        // shin=-32 en walk). Sin esto el IK doblaba codos/rodillas hacia
        // delante: poses imposibles = la "desfiguración" del editor de poses.
        val animUpper = clampJoint(upperName, wrapDeg(angUpper - parentWorld - upper.restDeg))
        val animFore = clampJoint(foreName, wrapDeg(angFore - angUpper - fore.restDeg))

        val m = LinkedHashMap<String, Float>(angles)
        m[upperName] = animUpper
        m[foreName] = animFore
        return Pose(m, bob, sway)
    }

    // Rango fisiológico por hueso (grados de animación). El resto sin límite.
    private fun clampJoint(bone: String, a: Float): Float {
        return when (bone) {
            "foreArmL" -> a.coerceIn(-150f, 5f)
            "foreArmR" -> a.coerceIn(-5f, 150f)
            "shinL", "shinR" -> a.coerceIn(-140f, 5f)
            "thighL", "thighR" -> a.coerceIn(-60f, 60f)
            else -> a
        }
    }

    // v0.5.1 (FASE A): gira UN hueso (codo o rodilla en el editor de puntos)
    // para que apunte al objetivo (ux, uy en unidades del muñeco) desde su
    // articulación actual. Respeta los límites articulares. Es el "punto
    // intermedio arrastrable" del editor: el dedo orienta el antebrazo o la
    // espinilla sin mover el resto del cuerpo.
    fun pointBoneTo(boneName: String, angles: Map<String, Float>, targetX: Float, targetY: Float): Pose {
        val bone = PuppetRig.HUMANOID.bones.firstOrNull { it.name == boneName } ?: return Pose(angles, 0f, 0f)
        val solved = PuppetRig.HUMANOID.solve(angles, 0f, 0f)
        val bp = solved[boneName] ?: return Pose(angles, 0f, 0f)
        val jx = bp.joint.x
        val jy = bp.joint.y
        if (kotlin.math.abs(targetX - jx) < 1e-4f && kotlin.math.abs(targetY - jy) < 1e-4f) return Pose(angles, 0f, 0f)
        val worldTarget = angDeg(PointF(jx, jy), PointF(targetX, targetY))
        val parentWorld = bone.parent?.let { solved[it]?.worldDeg } ?: 0f
        val anim = clampJoint(boneName, wrapDeg(worldTarget - parentWorld - bone.restDeg))
        val m = LinkedHashMap<String, Float>(angles)
        m[boneName] = anim
        return Pose(m, 0f, 0f)
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
