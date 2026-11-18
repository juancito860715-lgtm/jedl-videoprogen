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

    @Test fun retargeterClampsImportedAnglesToRigJointLimits() {
        val pose = AnimationRetargeter.retarget(
            mapOf(
                "mixamorig:RightForeArm" to -95f,
                "mixamorig:LeftForeArm" to 40f,
                "mixamorig:LeftLeg" to 25f,
                "mixamorig:RightUpLeg" to 90f,
                "mixamorig:Spine" to 210f
            )
        )
        assertEquals(-5f, pose.angles.getValue("foreArmR"), 0.001f)
        assertEquals(5f, pose.angles.getValue("foreArmL"), 0.001f)
        assertEquals(5f, pose.angles.getValue("shinL"), 0.001f)
        assertEquals(60f, pose.angles.getValue("thighR"), 0.001f)
        assertEquals(180f, pose.angles.getValue("torso"), 0.001f)
    }

    @Test fun bowKickStretchAndStumbleContainRealPoseChanges() {
        val files = motionFiles()
        listOf("bow.json", "kick.json", "stretch.json", "stumble.json").forEach { name ->
            assertTrue("$name still has no meaningful movement",
                MotionLibraryValidator.movementAmplitude(name, files.getValue(name)) >= 5f)
        }
    }
}
