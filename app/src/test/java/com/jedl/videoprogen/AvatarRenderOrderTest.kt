package com.jedl.videoprogen

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarRenderOrderTest {

    @Test
    fun avatarPiecesAreDrawnInBackToFrontZOrder() {
        assertEquals(
            listOf(
                "armUR",
                "foreArmR",
                "thighR",
                "shinR",
                "torso",
                "thighL",
                "shinL",
                "head",
                "armUL",
                "foreArmL"
            ),
            AvatarRenderOrder.backToFront
        )
    }
}
