package com.jedl.videoprogen

/**
 * Canonical back-to-front Z order for every avatar rendering path.
 *
 * Keep this contract platform-independent so JVM unit tests can validate it
 * without constructing Android Canvas, Bitmap, or Paint instances.
 */
object AvatarRenderOrder {
    val backToFront: List<String> = listOf(
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
    )
}
