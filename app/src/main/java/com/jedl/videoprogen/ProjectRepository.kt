package com.jedl.videoprogen

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Persistencia local de proyectos JEDL.
 *
 * Cada proyecto vive en filesDir/JEDLStudio/projects/<id>/project.json.
 * La escritura usa archivo temporal + fsync + rename para no dejar un
 * documento truncado si Android cierra el proceso durante el guardado.
 */
class ProjectRepository(context: Context) {
    private val root = File(context.applicationContext.filesDir, "JEDLStudio/projects")

    data class ProjectSummary(
        val id: String,
        val name: String,
        val modifiedAtMillis: Long,
        val sceneCount: Int
    )

    @Synchronized
    @Throws(IOException::class, IllegalArgumentException::class)
    fun save(project: ProjectDocument): ProjectDocument {
        val validationError = ProjectDocumentValidator.validate(project)
        require(validationError == null) { validationError ?: "Proyecto no válido" }
        if (!root.exists() && !root.mkdirs()) throw IOException("No se pudo crear la carpeta de proyectos")

        val now = System.currentTimeMillis()
        val updated = project.copy(modifiedAtMillis = now)
        val directory = projectDirectory(updated.id)
        if (!directory.exists() && !directory.mkdirs()) throw IOException("No se pudo crear la carpeta del proyecto")

        val target = File(directory, PROJECT_FILE)
        val temporary = File(directory, "$PROJECT_FILE.tmp")
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(encode(updated).toByteArray(Charsets.UTF_8))
                stream.flush()
                stream.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
        return updated
    }

    @Synchronized
    @Throws(IOException::class)
    fun load(projectId: String): ProjectDocument {
        val file = File(projectDirectory(projectId), PROJECT_FILE)
        if (!file.isFile) throw IOException("No existe el proyecto solicitado")
        val project = try {
            decode(file.readText(Charsets.UTF_8))
        } catch (error: Exception) {
            throw IOException("El archivo del proyecto está dañado o no es compatible", error)
        }
        val validationError = ProjectDocumentValidator.validate(project)
        if (validationError != null) throw IOException(validationError)
        if (project.id != projectId) throw IOException("El ID interno no coincide con la carpeta")
        return project
    }

    @Synchronized
    fun list(): List<ProjectSummary> {
        if (!root.isDirectory) return emptyList()
        return root.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && SAFE_ID.matches(it.name) }
            ?.mapNotNull { directory ->
                try {
                    val project = load(directory.name)
                    ProjectSummary(project.id, project.name, project.modifiedAtMillis, project.scenes.size)
                } catch (_: Exception) {
                    null
                }
            }
            ?.sortedByDescending { it.modifiedAtMillis }
            ?.toList()
            ?: emptyList()
    }

    @Synchronized
    @Throws(IOException::class)
    fun delete(projectId: String): Boolean {
        val directory = projectDirectory(projectId)
        if (!directory.exists()) return false
        if (!directory.isDirectory) throw IOException("La ruta del proyecto no es un directorio")
        return directory.deleteRecursively()
    }

    private fun projectDirectory(id: String): File {
        require(SAFE_ID.matches(id)) { "Identificador de proyecto no válido" }
        val directory = File(root, id)
        val canonicalRoot = root.canonicalFile
        val canonicalDirectory = directory.canonicalFile
        require(canonicalDirectory.parentFile == canonicalRoot) { "Ruta de proyecto fuera del almacenamiento permitido" }
        return canonicalDirectory
    }

    private fun encode(project: ProjectDocument): String {
        val scenes = JSONArray()
        project.scenes.forEach { scene ->
            val layers = JSONArray()
            scene.layers.forEach { layer ->
                val t = layer.transform
                layers.put(JSONObject().apply {
                    put("id", layer.id)
                    put("name", layer.name)
                    put("type", layer.type.name)
                    put("visible", layer.visible)
                    put("locked", layer.locked)
                    put("opacity", layer.opacity.toDouble())
                    put("transform", JSONObject().apply {
                        put("x", t.x.toDouble())
                        put("y", t.y.toDouble())
                        put("scaleX", t.scaleX.toDouble())
                        put("scaleY", t.scaleY.toDouble())
                        put("rotationDegrees", t.rotationDegrees.toDouble())
                    })
                    put("assetPath", layer.assetPath ?: JSONObject.NULL)
                    put("text", layer.text)
                    put("startMillis", layer.startMillis)
                    put("durationMillis", layer.durationMillis)
                    put("zIndex", layer.zIndex)
                })
            }
            scenes.put(JSONObject().apply {
                put("id", scene.id)
                put("name", scene.name)
                put("durationMillis", scene.durationMillis)
                put("backgroundColor", scene.backgroundColor)
                put("layers", layers)
            })
        }
        return JSONObject().apply {
            put("schemaVersion", project.schemaVersion)
            put("id", project.id)
            put("name", project.name)
            put("createdAtMillis", project.createdAtMillis)
            put("modifiedAtMillis", project.modifiedAtMillis)
            put("width", project.width)
            put("height", project.height)
            put("frameRate", project.frameRate)
            put("scenes", scenes)
        }.toString(2)
    }

    private fun decode(source: String): ProjectDocument {
        val rootObject = JSONObject(source)
        val scenesJson = rootObject.getJSONArray("scenes")
        val scenes = ArrayList<SceneDocument>(scenesJson.length())
        for (sceneIndex in 0 until scenesJson.length()) {
            val sceneJson = scenesJson.getJSONObject(sceneIndex)
            val layersJson = sceneJson.getJSONArray("layers")
            val layers = ArrayList<LayerDocument>(layersJson.length())
            for (layerIndex in 0 until layersJson.length()) {
                val layerJson = layersJson.getJSONObject(layerIndex)
                val transformJson = layerJson.optJSONObject("transform") ?: JSONObject()
                val rawType = layerJson.optString("type", LayerType.IMAGE.name)
                val type = try { LayerType.valueOf(rawType) } catch (_: Exception) {
                    throw IOException("Tipo de capa desconocido: $rawType")
                }
                val asset = layerJson.opt("assetPath")
                layers.add(LayerDocument(
                    id = layerJson.getString("id"),
                    name = layerJson.getString("name"),
                    type = type,
                    visible = layerJson.optBoolean("visible", true),
                    locked = layerJson.optBoolean("locked", false),
                    opacity = layerJson.optDouble("opacity", 1.0).toFloat(),
                    transform = Transform2D(
                        x = transformJson.optDouble("x", 0.5).toFloat(),
                        y = transformJson.optDouble("y", 0.5).toFloat(),
                        scaleX = transformJson.optDouble("scaleX", 1.0).toFloat(),
                        scaleY = transformJson.optDouble("scaleY", 1.0).toFloat(),
                        rotationDegrees = transformJson.optDouble("rotationDegrees", 0.0).toFloat()
                    ),
                    assetPath = if (asset == null || asset == JSONObject.NULL) null else asset.toString(),
                    text = layerJson.optString("text", ""),
                    startMillis = layerJson.optLong("startMillis", 0L),
                    durationMillis = layerJson.optLong("durationMillis", 5_000L),
                    zIndex = layerJson.optInt("zIndex", 0)
                ))
            }
            scenes.add(SceneDocument(
                id = sceneJson.getString("id"),
                name = sceneJson.getString("name"),
                durationMillis = sceneJson.optLong("durationMillis", 5_000L),
                backgroundColor = sceneJson.optInt("backgroundColor", 0xFF101116.toInt()),
                layers = layers
            ))
        }
        return ProjectDocument(
            id = rootObject.getString("id"),
            name = rootObject.getString("name"),
            schemaVersion = rootObject.getInt("schemaVersion"),
            createdAtMillis = rootObject.getLong("createdAtMillis"),
            modifiedAtMillis = rootObject.getLong("modifiedAtMillis"),
            width = rootObject.getInt("width"),
            height = rootObject.getInt("height"),
            frameRate = rootObject.getInt("frameRate"),
            scenes = scenes
        )
    }

    companion object {
        private const val PROJECT_FILE = "project.json"
        private val SAFE_ID = Regex("^[a-fA-F0-9-]{16,64}$")

        fun newProject(name: String, width: Int = 720, height: Int = 1280, frameRate: Int = 30): ProjectDocument {
            val now = System.currentTimeMillis()
            return ProjectDocument(
                id = UUID.randomUUID().toString(),
                name = name.trim().ifBlank { "Proyecto sin título" },
                createdAtMillis = now,
                modifiedAtMillis = now,
                width = width,
                height = height,
                frameRate = frameRate
            )
        }
    }
}
