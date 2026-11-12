package com.jedl.videoprogen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import java.io.File

// El export corre en un SERVICIO EN PRIMER PLANO con wakelock (anti-MIUI).
// v0.5.0: la plantilla pendiente puede ser builtin, MOCAP de video o una
// animación propia del usuario (ActiveTemplate) — el MP4 queda EXACTO
// a lo que se ve en pantalla.
//
// v0.6.0 (FASE B): el export también soporta el MODO PERSONAJE (avatar por
// piezas + cara recortada de la foto + dirección). La foto (mesh) pasa a
// ser OPCIONAL: se puede exportar un video solo con el personaje.
class ExportService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Exportar video", NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(NOTIF_ID, buildNotification("Preparando…", 0))
        Thread { doExport() }.start()
        return START_NOT_STICKY
    }

    private fun buildNotification(text: String, pct: Int): Notification {
        val openApp = packageManager.getLaunchIntentForPackage(packageName)
        val pi = if (openApp != null) PendingIntent.getActivity(this, 0, openApp, PendingIntent.FLAG_IMMUTABLE) else null
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("JEDL Studio · Exportando")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, pct, false)
            .setContentIntent(pi)
            .build()
    }

    private fun doExport() {
        val photo = pendingPhoto
        val text = pendingText
        if (photo == null && !(pendingAvatarMode && pendingAvatarSpec != null)) { stopSelf(); return }

        val nm = getSystemService(NotificationManager::class.java)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JEDL:export").apply { setReferenceCounted(false) }
        wakeLock.acquire(10 * 60 * 1000L)

        try {
            val tempFile = File(cacheDir, "temp_export.mp4")
            val sceneDuration = (pendingProjectScene?.durationMillis ?: 5_000L).coerceAtLeast(1L)
            val exporter = Mp4Exporter(pendingWidth, pendingHeight, 30, sceneDuration / 1_000f)
            val projectId = pendingProjectId
            val assetStore = projectId?.let { ProjectAssetStore(this) }
            val imageCache = object : LruCache<String, Bitmap>(24 * 1024) {
                override fun sizeOf(key: String, value: Bitmap): Int = maxOf(1, value.byteCount / 1024)
            }
            fun resolveExportBitmap(relativePath: String): Bitmap? {
                val id = projectId ?: return null
                val store = assetStore ?: return null
                imageCache.get(relativePath)?.let { if (!it.isRecycled) return it }
                return try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    store.openAsset(id, relativePath).use { BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1
                    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
                    val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
                    store.openAsset(id, relativePath).use { input ->
                        BitmapFactory.decodeStream(input, null, options)?.also { imageCache.put(relativePath, it) }
                    }
                } catch (_: Exception) { null }
            }
            val ok = exporter.export(tempFile.absolutePath, { pct ->
                nm.notify(NOTIF_ID, buildNotification("Exportando… $pct%", pct))
                onStatus?.invoke("Exportando… $pct%")
            }) { progress ->
                SceneRenderer.renderSceneToBitmap(
                    pendingBackground, pendingMesh, text, progress, pendingWidth, pendingHeight, pendingTemplate,
                    drawSkeleton = false,
                    textX = pendingTextX, textY = pendingTextY, textSize = pendingTextSize,
                    actorX = pendingActorX, actorY = pendingActorY,
                    avatarMode = pendingAvatarMode, avatarSpec = pendingAvatarSpec,
                    avatarFace = pendingAvatarFace, direction = pendingDirection,
                    projectScene = pendingProjectScene,
                    assetResolver = ::resolveExportBitmap
                )
            }

            if (ok) {
                saveToGallery(tempFile)
                onStatus?.invoke("✓ ¡Video guardado en tu Galería!")
            } else {
                onStatus?.invoke("Error al exportar")
            }
        } catch (e: Exception) {
            onStatus?.invoke("Error: " + e.message)
        } finally {
            try { wakeLock.release() } catch (_: Exception) {}
            nm.cancel(NOTIF_ID)
            pendingPhoto = null
            pendingBackground = null
            pendingMesh = null
            pendingText = ""
            // v0.6.0: los datos del avatar SOLO se desreferencian (la cara
            // pertenece a la MainActivity, no se recicla aquí)
            pendingAvatarSpec = null
            pendingAvatarFace = null
            pendingProjectScene = null
            pendingProjectId = null
            stopSelf()
        }
    }

    private fun saveToGallery(file: File) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "JEDL_Video_" + System.currentTimeMillis() + ".mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/JEDL")
            }
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return
        contentResolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { input -> input.copyTo(out) }
        }
    }

    companion object {
        private const val CHANNEL_ID = "jedl_export"
        private const val NOTIF_ID = 1

        @Volatile var pendingPhoto: Bitmap? = null
        @Volatile var pendingBackground: Bitmap? = null
        @Volatile var pendingMesh: SkinnedMesh? = null
        @Volatile var pendingText: String = ""
        // v0.5.0: plantilla ACTIVA (builtin, mocap de video o animación propia)
        @Volatile var pendingTemplate: ActiveTemplate = ActiveTemplate.of("idle")
        @Volatile var pendingWidth: Int = 720
        @Volatile var pendingHeight: Int = 1280
        @Volatile var pendingTextX: Float = 0.5f
        @Volatile var pendingTextY: Float = 0.9f
        @Volatile var pendingTextSize: Float = 60f
        // v0.5.1 (FASE A): posición libre del actor en la escena
        @Volatile var pendingActorX: Float = 0.5f
        @Volatile var pendingActorY: Float = 0.55f
        // v0.6.0 (FASE B): modo personaje (avatar por piezas + cara + dirección)
        @Volatile var pendingAvatarMode: Boolean = false
        @Volatile var pendingAvatarSpec: AvatarSpec? = null
        @Volatile var pendingAvatarFace: Bitmap? = null
        @Volatile var pendingDirection: String = "der"
        @Volatile var pendingProjectScene: SceneDocument? = null
        @Volatile var pendingProjectId: String? = null
        @Volatile var onStatus: ((String) -> Unit)? = null

        fun start(
            context: Context, photo: Bitmap?, background: Bitmap?,
            mesh: SkinnedMesh?, text: String, template: ActiveTemplate,
            width: Int, height: Int,
            textX: Float, textY: Float, textSize: Float,
            actorX: Float = 0.5f, actorY: Float = 0.55f,
            avatarMode: Boolean = false, avatarSpec: AvatarSpec? = null,
            avatarFace: Bitmap? = null, direction: String = "der",
            projectScene: SceneDocument? = null,
            projectId: String? = null
        ) {
            pendingPhoto = photo
            pendingBackground = background
            pendingMesh = mesh
            pendingText = text
            pendingTemplate = template
            pendingWidth = width
            pendingHeight = height
            pendingTextX = textX
            pendingTextY = textY
            pendingTextSize = textSize
            pendingActorX = actorX
            pendingActorY = actorY
            pendingAvatarMode = avatarMode
            pendingAvatarSpec = avatarSpec
            pendingAvatarFace = avatarFace
            pendingDirection = direction
            pendingProjectScene = projectScene
            pendingProjectId = projectId
            val intent = Intent(context, ExportService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}
