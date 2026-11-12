package com.jedl.videoprogen

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Almacén privado de recursos de un proyecto.
 *
 * Los documentos solo guardan rutas relativas (assets/<uuid>.<ext>).
 * Los bytes se copian al almacenamiento privado para que el proyecto no
 * dependa de permisos temporales sobre un Uri de la galería.
 */
class ProjectAssetStore private constructor(private val projectsRoot: File) {
    constructor(context: Context) : this(File(context.applicationContext.filesDir, "JEDLStudio/projects"))

    data class ImportedAsset(val relativePath: String, val byteCount: Long)

    @Throws(IOException::class, IllegalArgumentException::class)
    fun importAsset(projectId: String, source: InputStream, originalName: String, maxBytes: Long = DEFAULT_MAX_BYTES): ImportedAsset {
        require(SAFE_ID.matches(projectId)) { "Identificador de proyecto no válido" }
        require(maxBytes in 1L..MAX_ALLOWED_BYTES) { "Límite de importación no válido" }
        val projectDir = projectDirectory(projectId)
        val assetsDir = File(projectDir, "assets")
        if (!assetsDir.exists() && !assetsDir.mkdirs()) throw IOException("No se pudo crear la carpeta de recursos")

        val extension = originalName.substringAfterLast('.', "").lowercase()
            .filter { it.isLetterOrDigit() }.take(8).ifBlank { "bin" }
        val fileName = UUID.randomUUID().toString() + "." + extension
        val relativePath = "assets/$fileName"
        val target = resolve(projectId, relativePath)
        val temporary = File(assetsDir, "$fileName.tmp")
        var total = 0L
        try {
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) throw IOException("El recurso supera el límite de importación")
                    output.write(buffer, 0, count)
                }
                output.flush()
                output.fd.sync()
            }
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath())
            }
            return ImportedAsset(relativePath, total)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    @Throws(IOException::class, IllegalArgumentException::class)
    fun openAsset(projectId: String, relativePath: String) = resolve(projectId, relativePath).inputStream()

    fun deleteProjectAssets(projectId: String): Boolean {
        require(SAFE_ID.matches(projectId)) { "Identificador de proyecto no válido" }
        val assets = File(projectDirectory(projectId), "assets")
        return !assets.exists() || assets.deleteRecursively()
    }

    private fun resolve(projectId: String, relativePath: String): File {
        require(SAFE_ID.matches(projectId)) { "Identificador de proyecto no válido" }
        require(ProjectDocumentValidator.isSafeRelativeAssetPath(relativePath)) { "Ruta de recurso no segura" }
        val projectDir = projectDirectory(projectId)
        val file = File(projectDir, relativePath).canonicalFile
        require(file.path.startsWith(projectDir.canonicalPath + File.separator)) { "Recurso fuera de la carpeta del proyecto" }
        return file
    }

    private fun projectDirectory(projectId: String): File {
        val root = File(projectsRoot, projectId).canonicalFile
        require(root.parentFile == projectsRoot.canonicalFile) { "Ruta de proyecto fuera del almacenamiento permitido" }
        return root
    }

    companion object {
        private const val DEFAULT_MAX_BYTES = 32L * 1024L * 1024L
        private const val MAX_ALLOWED_BYTES = 128L * 1024L * 1024L
        private val SAFE_ID = Regex("^[a-fA-F0-9-]{16,64}$")

        internal fun forTests(directory: File) =
            ProjectAssetStore(File(directory, "JEDLStudio/projects"))
    }
}
