package com.jedl.videoprogen

// ============================================================
// BIBLIOTECA DE MOVIMIENTOS — RETARGETER (v0.8.0)
// ============================================================
// Traduce los nombres de huesos de la biblioteca de movimientos
// (formato Mixamo/CMU: mixamorig:LeftArm, RightUpLeg, ...) a los
// nombres EXACTOS del PuppetEngine (armUL, thighR, foreArmL, ...).
//
// Mapeo decidido con el dueño:
//   mixamorig:LeftArm      -> armUL      (hombro izquierdo del rig)
//   mixamorig:LeftForeArm  -> foreArmL
//   mixamorig:RightArm     -> armUR
//   mixamorig:RightForeArm -> foreArmR
//   mixamorig:LeftUpLeg    -> thighL
//   mixamorig:LeftLeg      -> shinL
//   mixamorig:RightUpLeg   -> thighR
//   mixamorig:RightLeg     -> shinR
//   Hips/Spine/Spine1/Spine2 -> torso  (aportes SUMADOS)
//   Neck/Head              -> head     (aportes SUMADOS)
//   Manos, pies y dedos    -> se IGNORAN (el rig no los tiene)
//
// CONVENCIÓN JEDL (verificada en el rig): 0° = abajo, 180° = arriba,
// positivo = giro horario en pantalla. Si el archivo viene de una
// biblioteca con positivo ANTI-horario, declara
// "convention": "jedl-inv" y cada ángulo se niega aquí.
//
// REGLA DEL DUEÑO: aquí SOLO se procesan ángulos del archivo; ningún
// eje ni coordenada Y se calcula a mano. Ejes/posición quedan a
// cargo del rig (Skeleton.solve).
// ============================================================

object AnimationRetargeter {

    // Nombres de la biblioteca (normalizados, sin "mixamorig:") -> hueso
    // del PuppetEngine. Varios huesos de la biblioteca aportan al mismo
    // hueso del rig (p.ej. Hips+Spine+Spine1+Spine2 -> torso): sus
    // ángulos SE SUMAN (flexión acumulada de la columna).
    private val BONE_MAP: Map<String, String> = mapOf(
        "hips" to "torso",
        "spine" to "torso",
        "spine1" to "torso",
        "spine2" to "torso",
        "neck" to "head",
        "head" to "head",
        "leftshoulder" to "armUL",
        "leftarm" to "armUL",
        "leftforearm" to "foreArmL",
        "rightshoulder" to "armUR",
        "rightarm" to "armUR",
        "rightforearm" to "foreArmR",
        "leftupleg" to "thighL",
        "leftleg" to "shinL",
        "rightupleg" to "thighR",
        "rightleg" to "shinR"
    )

    // Normaliza un nombre de la biblioteca: quita el prefijo Mixamo,
    // guiones bajos y espacios, y pasa a minúsculas.
    private fun normalize(rawName: String): String {
        return rawName
            .removePrefix("mixamorig:")
            .replace("_", "")
            .replace(" ", "")
            .trim()
            .lowercase()
    }

    /**
     * Convierte UN keyframe de la biblioteca a una Pose del rig.
     *
     * @param raw        ángulos en GRADOS, por nombre de hueso de la
     *                   biblioteca (p.ej. "mixamorig:LeftArm" -> -18.0)
     * @param convention "jedl" (0°=abajo, positivo horario) o
     *                   "jedl-inv" (positivo antihorario: se niega)
     * @param bob        rebote vertical del keyframe (unidades del muñeco,
     *                   + abajo / - arriba), tal como viene del archivo
     * @param sway       balanceo horizontal del keyframe (+ derecha)
     * @return Pose lista para PuppetEngine (huesos desconocidos ignorados)
     */
    fun retarget(
        raw: Map<String, Float>,
        convention: String = "jedl",
        bob: Float = 0f,
        sway: Float = 0f
    ): Pose {
        val sign = if (convention.equals("jedl-inv", ignoreCase = true)) -1f else 1f
        val acc = LinkedHashMap<String, Float>()
        for ((rawName, rawVal) in raw) {
            if (rawVal.isNaN()) continue           // guard: NaN jamás entra al rig
            val target = BONE_MAP[normalize(rawName)] ?: continue  // manos/pies/dedos: IGNORADOS
            acc[target] = (acc[target] ?: 0f) + sign * rawVal
        }
        return Pose(acc, bob, sway)
    }

    /** El hueso del rig al que apunta un nombre de biblioteca (para pruebas). */
    fun targetBone(rawName: String): String? = BONE_MAP[normalize(rawName)]
}
