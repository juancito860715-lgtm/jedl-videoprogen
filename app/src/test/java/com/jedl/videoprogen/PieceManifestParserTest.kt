package com.jedl.videoprogen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class PieceManifestParserTest {
    @Test fun generatedBankContractContainsExactlyRigBones() {
        val bones = AvatarRenderOrder.backToFront
        val pieces = JSONArray()
        bones.forEach { bone ->
            pieces.put(JSONObject().apply {
                put("bone", bone); put("file", "$bone.png")
                put("pivotX", 128); put("pivotY", 20)
                put("widthUnits", 0.06); put("tint", "skin")
            })
        }
        val parsed = PieceManifestParser.parse(JSONObject().put("view", "frente").put("pieces", pieces).toString(), "ritmo", "pieces/ritmo")
        assertNotNull(parsed)
        assertEquals(10, parsed!!.pieces.size)
        assertEquals(bones.toSet(), parsed.pieces.map { it.bone }.toSet())
    }

    @Test fun malformedManifestIsRejected() {
        assertNull(PieceManifestParser.parse("""{"pieces":[{"bone":"torso"}]}""", "ritmo", "pieces/ritmo"))
    }
}
