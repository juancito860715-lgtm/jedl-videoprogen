package com.jedl.videoprogen

import org.json.JSONObject
import kotlin.math.abs

/** Strict, pure validation of the motion files shipped in assets/motions. */
object MotionLibraryValidator {
    const val EXPECTED_FILE_COUNT = 22
    private const val EPSILON = 0.05f

    fun validate(files: Map<String, String>): List<String> {
        val errors = mutableListOf<String>()
        if (files.size != EXPECTED_FILE_COUNT) {
            errors += "Expected $EXPECTED_FILE_COUNT motion files, found ${files.size}"
        }
        val ids = mutableSetOf<String>()
        for ((fileName, content) in files.toSortedMap()) {
            val entry = MotionLibraryParser.parseFile(fileName, content)
            if (entry == null) {
                errors += "$fileName: parser rejected file"
                continue
            }
            if (!ids.add(entry.id)) errors += "$fileName: duplicate motion id ${entry.id}"
            val times = extractTimes(fileName, content)
            if (times.size < 2) errors += "$fileName: at least two keyframes required"
            if (times.any { !it.isFinite() || it !in 0f..1f }) {
                errors += "$fileName: keyframe time outside [0,1]"
            }
            if (times.zipWithNext().any { (a,b) -> b <= a }) {
                errors += "$fileName: keyframe times must be strictly increasing"
            }
            val names = extractBoneNames(fileName, content)
            if (names.isEmpty()) errors += "$fileName: no bone channels"
            names.filter { AnimationRetargeter.targetBone(it) == null }
                .forEach { errors += "$fileName: unknown bone $it" }

            for ((frameIndex, frame) in entry.template.frames.withIndex()) {
                for ((bone, angle) in frame.pose.angles) {
                    if (!angle.isFinite()) {
                        errors += "$fileName frame $frameIndex: non-finite angle for $bone"
                    } else if (abs(PuppetEngine.clampJoint(bone, angle) - angle) > EPSILON) {
                        errors += "$fileName frame $frameIndex: $bone angle $angle outside clampJoint"
                    }
                }
                if (!frame.pose.bob.isFinite() || !frame.pose.sway.isFinite()) {
                    errors += "$fileName frame $frameIndex: non-finite root motion"
                }
            }
            if (entry.template.loop && entry.template.frames.size >= 2) {
                val first = entry.template.frames.first().pose
                val last = entry.template.frames.last().pose
                val keys = first.angles.keys + last.angles.keys
                if (keys.any { abs((first.angles[it] ?: 0f) - (last.angles[it] ?: 0f)) > EPSILON } ||
                    abs(first.bob - last.bob) > EPSILON || abs(first.sway - last.sway) > EPSILON) {
                    errors += "$fileName: loop endpoints are discontinuous"
                }
            }
        }
        return errors.distinct()
    }

    fun movementAmplitude(fileName: String, content: String): Float {
        val entry = MotionLibraryParser.parseFile(fileName, content) ?: return 0f
        return entry.template.frames.maxOfOrNull { frame ->
            maxOf(
                frame.pose.angles.values.maxOfOrNull { abs(it) } ?: 0f,
                abs(frame.pose.bob), abs(frame.pose.sway)
            )
        } ?: 0f
    }

    private fun extractTimes(fileName: String, content: String): List<Float> {
        return if (fileName.endsWith(".json", true)) {
            val array = JSONObject(content).optJSONArray("keyframes") ?: return emptyList()
            (0 until array.length()).map { array.getJSONObject(it).optDouble("t", Double.NaN).toFloat() }
        } else {
            val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            val headerIndex = lines.indexOfFirst { it.startsWith("t,") }
            if (headerIndex < 0) return emptyList()
            lines.drop(headerIndex + 1).mapNotNull { it.substringBefore(',').toFloatOrNull() }
        }
    }

    private fun extractBoneNames(fileName: String, content: String): Set<String> {
        return if (fileName.endsWith(".json", true)) {
            val array = JSONObject(content).optJSONArray("keyframes") ?: return emptySet()
            buildSet {
                for (i in 0 until array.length()) {
                    val bones = array.getJSONObject(i).optJSONObject("bones") ?: continue
                    val iterator = bones.keys()
                    while (iterator.hasNext()) add(iterator.next())
                }
            }
        } else {
            val lines = content.lines().map { it.trim() }
            val header = lines.firstOrNull { it.startsWith("t,") } ?: return emptySet()
            header.split(',').drop(1).dropLast(2).toSet()
        }
    }
}
