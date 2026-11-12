package com.jedl.videoprogen

import java.util.UUID

/**
 * Modelo de edición no destructiva de la línea de tiempo.
 * Los tiempos están expresados en milisegundos de la escena; los medios
 * importados se refieren siempre a assets privados del proyecto.
 */
enum class TimelineTrackType { VIDEO, AUDIO, VOICE, MUSIC, TEXT, SUBTITLE, EFFECT }

data class TimelineTrackDocument(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Pista",
    val type: TimelineTrackType,
    val muted: Boolean = false,
    val locked: Boolean = false,
    val clips: List<TimelineClipDocument> = emptyList()
)

data class TimelineClipDocument(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Clip",
    val assetPath: String? = null,
    val startMillis: Long = 0L,
    val durationMillis: Long = 1_000L,
    val sourceStartMillis: Long = 0L,
    val sourceEndMillis: Long? = null,
    val volume: Float = 1f,
    val text: String = ""
)

object TimelineDocumentValidator {
    fun validate(tracks: List<TimelineTrackDocument>, sceneDurationMillis: Long): String? {
        if (tracks.size > 128) return "Demasiadas pistas en la línea de tiempo"
        val trackIds = HashSet<String>()
        for (track in tracks) {
            if (!track.id.matches(Regex("^[a-fA-F0-9-]{16,64}$")) || !trackIds.add(track.id)) return "ID de pista no válido o duplicado"
            if (track.name.isBlank() || track.name.length > 120) return "Nombre de pista no válido"
            if (track.clips.size > 10_000) return "Demasiados clips en una pista"
            val clipIds = HashSet<String>()
            for (clip in track.clips) {
                if (!clip.id.matches(Regex("^[a-fA-F0-9-]{16,64}$")) || !clipIds.add(clip.id)) return "ID de clip no válido o duplicado"
                if (clip.name.isBlank() || clip.name.length > 120) return "Nombre de clip no válido"
                if (clip.startMillis < 0L || clip.durationMillis !in 1L..3_600_000L) return "Duración o inicio de clip no válido"
                if (clip.startMillis > sceneDurationMillis || clip.durationMillis > sceneDurationMillis - clip.startMillis) return "El clip excede la duración de la escena"
                if (clip.sourceStartMillis < 0L || (clip.sourceEndMillis != null && clip.sourceEndMillis <= clip.sourceStartMillis)) return "Recorte de origen no válido"
                if (!clip.volume.isFinite() || clip.volume !in 0f..2f) return "Volumen de clip no válido"
                if (clip.assetPath != null && !ProjectDocumentValidator.isSafeRelativeAssetPath(clip.assetPath)) return "Ruta de medio no segura"
                if (track.type in setOf(TimelineTrackType.VIDEO, TimelineTrackType.AUDIO, TimelineTrackType.VOICE, TimelineTrackType.MUSIC) && clip.assetPath == null) return "El clip multimedia necesita un recurso importado"
                if (track.type in setOf(TimelineTrackType.TEXT, TimelineTrackType.SUBTITLE) && clip.text.isBlank()) return "El clip de texto no puede estar vacío"
            }
        }
        return null
    }
}
