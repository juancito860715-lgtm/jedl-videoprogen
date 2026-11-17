package com.jedl.videoprogen

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class MotionLibraryTest {
    private fun motionFiles(): Map<String, String> {
        val candidates = listOf(File("src/main/assets/motions"), File("app/src/main/assets/motions"))
        val dir = candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate app/src/main/assets/motions")
        return dir.listFiles().orEmpty()
            .filter { it.isFile && (it.extension.equals("json", true) || it.extension.equals("csv", true)) }
            .associate { it.name to it.readText(Charsets.UTF_8) }
    }

    @Test fun allTwentyTwoMotionAssetsMeetTheRuntimeContract() {
        val files = motionFiles()
        assertEquals(MotionLibraryValidator.EXPECTED_FILE_COUNT, files.size)
        assertTrue(MotionLibraryValidator.validate(files).joinToString("\n"), MotionLibraryValidator.validate(files).isEmpty())
    }

    @Test fun bowKickStretchAndStumbleContainRealPoseChanges() {
        val files = motionFiles()
        listOf("bow.json", "kick.json", "stretch.json", "stumble.json").forEach { name ->
            assertTrue("$name still has no meaningful movement",
                MotionLibraryValidator.movementAmplitude(name, files.getValue(name)) >= 5f)
        }
    }
}
