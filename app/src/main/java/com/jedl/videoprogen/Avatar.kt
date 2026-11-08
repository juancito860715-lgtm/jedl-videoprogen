package com.jedl.videoprogen

// ============================================================
// FASE B — PERSONAJES POR PIEZAS (v0.6.0)
// ============================================================
// Personaje 100% DIBUJADO EN CÓDIGO: cero assets, cero RAM extra.
// Estilo flat (inspirado en Open Peeps, CC0, y el arte del dueño
// tocando el tambor): cápsulas de color plano. Una variante es un
// conjunto de PARÁMETROS (colores/peinado/barba), así el editor
// grande da combinaciones infinitas sin descargar nada.
//
// El avatar se ancla al rig EXISTENTE (PuppetRig.HUMANOID): las
// plantillas, el IK del editor de poses y el MOCAP DE VIDEO lo
// animan igual que a la foto, pero SIN distorsión (no hay píxeles
// arrastrados: cada pieza se dibuja nueva en cada cuadro).
// ============================================================

// Definición de un personaje: TODO son parámetros visuales.
data class AvatarSpec(
    val name: String,
    val skin: Int,        // color de piel (ARGB)
    val hair: Int,        // color de cabello (ARGB)
    val hairStyle: Int,   // 0 corto, 1 afro, 2 gorra, 3 calvo, 4 largo
    val shirt: Int,       // camiseta (y gorra, a juego)
    val pants: Int,       // pantalón
    val shoes: Int,       // zapatos
    val beard: Boolean    // barba/bigote dibujado
)

object Avatars {

    // Paletas planas con buen contraste sobre cualquier fondo
    val SKINS = intArrayOf(
        0xFFF2C29B.toInt(), 0xFFE8A87C.toInt(), 0xFFC68B59.toInt(),
        0xFF9C6B3F.toInt(), 0xFF6B4226.toInt()
    )
    val HAIRS = intArrayOf(
        0xFF212121.toInt(), 0xFF3E2A1C.toInt(), 0xFF6D4C2F.toInt(),
        0xFFA3572B.toInt(), 0xFFB03A48.toInt()
    )
    val SHIRTS = intArrayOf(
        0xFFE4572E.toInt(), 0xFFF5B841.toInt(), 0xFF2E86AB.toInt(),
        0xFF3AB54A.toInt(), 0xFF8E44AD.toInt(), 0xFFE63B60.toInt(),
        0xFF1B1D24.toInt(), 0xFFF7F7F7.toInt()
    )
    val PANTS = intArrayOf(
        0xFF2A2D35.toInt(), 0xFF3E5C76.toInt(), 0xFF4F6D9A.toInt(),
        0xFF5D4037.toInt(), 0xFF37474F.toInt(), 0xFF6D4C2F.toInt()
    )
    val SHOES = intArrayOf(
        0xFFF7F7F7.toInt(), 0xFF212121.toInt(),
        0xFFE4572E.toInt(), 0xFFF5B841.toInt()
    )

    val HAIR_STYLE_NAMES = listOf("Corto", "Afro", "Gorra", "Calvo", "Largo")

    // 3 PERSONAJES PROTOTIPO (pedido del dueño: tres HOMBRES distintos)
    fun presets(): List<AvatarSpec> = listOf(
        // Ritmo: percusionista con gorra y colores de la marca (oro/rojo)
        AvatarSpec("Ritmo", SKINS[2], HAIRS[0], 2, SHIRTS[1], PANTS[0], SHOES[2], false),
        // Clásico: pelo corto, barba, camiseta azul
        AvatarSpec("Clasico", SKINS[0], HAIRS[1], 0, SHIRTS[2], PANTS[2], SHOES[0], true),
        // Fiesta: afro, camiseta verde, pantalón claro
        AvatarSpec("Fiesta", SKINS[1], HAIRS[3], 1, SHIRTS[3], PANTS[1], SHOES[3], false)
    )
}
