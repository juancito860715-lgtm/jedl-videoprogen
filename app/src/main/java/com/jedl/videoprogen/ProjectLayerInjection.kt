package com.jedl.videoprogen

/** Pure project-scene injection for image and background assets. */
object ProjectLayerInjection {
    fun injectAsset(scene: SceneDocument, assetPath: String, type: LayerType): SceneDocument {
        require(type == LayerType.IMAGE || type == LayerType.BACKGROUND) {
            "Solo se pueden inyectar capas IMAGE o BACKGROUND"
        }
        require(ProjectDocumentValidator.isSafeRelativeAssetPath(assetPath)) {
            "Ruta de recurso no segura"
        }
        val layers = scene.layers.toMutableList()
        if (type == LayerType.IMAGE && layers.none { it.type == LayerType.CHARACTER }) {
            val preset = Avatars.presets().first()
            layers += LayerDocument(
                name = "Personaje",
                type = LayerType.CHARACTER,
                characterAppearance = CharacterAppearanceDocument(
                    name = preset.name, skin = preset.skin, hair = preset.hair,
                    hairStyle = preset.hairStyle, shirt = preset.shirt,
                    pants = preset.pants, shoes = preset.shoes, beard = preset.beard,
                    enabled = true
                ),
                zIndex = 0
            )
        }
        val zIndex = when (type) {
            LayerType.BACKGROUND -> (layers.minOfOrNull { it.zIndex } ?: 0) - 1
            LayerType.IMAGE -> (layers.filter { it.type == LayerType.CHARACTER }
                .minOfOrNull { it.zIndex } ?: 0) - 1 -
                layers.count { it.type == LayerType.IMAGE }
            else -> error("Unsupported layer type")
        }
        val layer = LayerDocument(
            name = if (type == LayerType.BACKGROUND) "Fondo" else "Foto del actor",
            type = type,
            assetPath = assetPath,
            zIndex = zIndex
        )
        return scene.copy(layers = layers + layer)
    }
}
