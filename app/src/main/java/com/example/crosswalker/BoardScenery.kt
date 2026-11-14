package com.example.crosswalker

internal data class SceneryPixel(
    val x: Float, val y: Float, val width: Float, val height: Float, val color: String
)

internal data class ScenerySprite(
    val id: String, val x: Float, val y: Float, val width: Float, val height: Float
)

internal data class ScenerySign(val text: String, val x: Float, val y: Float, val width: Float)

internal data class BoardScenery(
    val pixels: List<SceneryPixel>,
    val sprites: List<ScenerySprite>,
    val accentLights: List<SceneryPixel>,
    val signs: List<ScenerySign>
)

/** Decorative layers for the far pavement and the outer edges of the near pavement. */
internal fun boardScenery(world: Int, levelId: Int): BoardScenery {
    val pixels = mutableListOf<SceneryPixel>()
    val sprites = mutableListOf<ScenerySprite>()
    val lights = mutableListOf<SceneryPixel>()
    val signs = mutableListOf<ScenerySign>()
    val mirrored = levelId % 2 == 0
    val variant = (levelId - 1) % 4

    fun xPosition(x: Int, width: Int): Float = (if (mirrored) 360 - x - width else x).toFloat()
    fun pixel(x: Int, y: Int, width: Int, height: Int, color: String) {
        pixels += SceneryPixel(xPosition(x, width), y.toFloat(), width.toFloat(), height.toFloat(), color)
    }
    fun prop(id: String, x: Int, y: Int, width: Int, height: Int) {
        sprites += ScenerySprite(id, xPosition(x, width), y.toFloat(), width.toFloat(), height.toFloat())
    }
    fun planter(x: Int, y: Int, width: Int, bloom: String) {
        pixel(x + 2, y + 8, width, 5, "#227E78")
        pixel(x, y, width, 10, "#76536B")
        pixel(x + 1, y + 6, width - 2, 3, "#A97B7E")
        pixel(x + 2, y + 1, width - 4, 5, "#245F55")
        for (leaf in 3 until width - 3 step 5) {
            pixel(x + leaf, y + 2, 3, 3, "#50AD7B")
            pixel(x + leaf + 1, y, 2, 2, bloom)
        }
    }
    fun bench(x: Int, y: Int) {
        pixel(x + 2, y + 8, 24, 3, "#227E78")
        pixel(x + 2, y, 2, 10, "#4E4A63")
        pixel(x + 22, y, 2, 10, "#4E4A63")
        pixel(x, y, 26, 3, "#B67857")
        pixel(x, y + 5, 26, 3, "#E0A476")
    }
    fun paving(x: Int, y: Int, width: Int, height: Int) {
        pixel(x, y, width, height, "#77748C")
        pixel(x + 1, y + 1, width - 2, height - 2, "#9D9AB0")
        for (joint in 4 until height step 6) {
            pixel(x + 1, y + joint, width - 2, 1, "#827E98")
        }
    }

    fun sign(text: String, x: Int, y: Int, width: Int) {
        signs += ScenerySign(text, xPosition(x, width), y.toFloat(), width.toFloat())
    }

    fun facade(x: Int, width: Int, wall: String, roof: String, title: String, windowsLit: Boolean = false) {
        pixel(x + 4, 12, width, 40, "#5A556A")
        pixel(x, 10, width, 38, "#40394F")
        pixel(x + 2, 12, width - 4, 34, wall)
        pixel(x - 2, 5, width + 4, 9, roof)
        pixel(x + 2, 5, width - 4, 2, "#E0A476")
        pixel(x + 3, 43, width - 6, 3, roof)
        val door = x + width / 2 - 7
        pixel(door, 30, 14, 18, "#40394F")
        pixel(door + 2, 32, 10, 14, "#769EB1")
        pixel(door + 7, 32, 1, 14, "#40394F")
        pixel(door - 2, 48, 18, 2, "#C5B7A2")
        for (window in listOf(x + 8, x + width - 22)) {
            pixel(window, 25, 14, 13, "#40394F")
            pixel(window + 2, 27, 10, 9, if (windowsLit) "#E4AA68" else "#9BC5D1")
            pixel(window + 6, 27, 2, 9, "#655B72")
            pixel(window + 1, 38, 12, 2, "#D9B295")
            if (windowsLit) {
                lights += SceneryPixel(xPosition(window + 2, 4), 27f, 4f, 6f, "#FFE0A0")
                lights += SceneryPixel(xPosition(window + 8, 4), 27f, 4f, 6f, "#FFE0A0")
            }
        }
        val signWidth = if (title.length > 7) 66 else 54
        if (title.isNotEmpty()) sign(title, x + (width - signWidth) / 2, 15, signWidth)
    }

    fun schoolBus(x: Int, y: Int) {
        pixel(x + 1, y + 24, 78, 3, "#444052")
        pixel(x + 2, y + 3, 75, 21, "#2B2540")
        pixel(x + 5, y, 62, 5, "#2B2540")
        pixel(x + 7, y + 2, 57, 4, "#FFE07A")
        pixel(x + 4, y + 6, 71, 16, "#FFC23D")
        pixel(x + 4, y + 19, 71, 3, "#D98A1C")
        pixel(x + 67, y + 5, 8, 10, "#4E4A63")
        pixel(x + 69, y + 6, 4, 7, "#B9DAEC")
        for (window in 9..54 step 11) {
            pixel(x + window, y + 6, 8, 8, "#4E4A63")
            pixel(x + window + 1, y + 7, 6, 2, "#B9DAEC")
        }
        pixel(x + 5, y + 16, 69, 2, "#4E4A63")
        pixel(x + 61, y + 8, 1, 14, "#9B6529")
        for (wheel in listOf(12, 60)) {
            pixel(x + wheel, y + 20, 10, 8, "#2B2540")
            pixel(x + wheel + 3, y + 23, 4, 3, "#9C99B5")
        }
        pixel(x + 73, y + 18, 4, 3, "#FFF8E6")
        pixel(x + 2, y + 17, 3, 3, "#D9483F")
    }

    fun playCourt(x: Int, y: Int) {
        val line = "#E8E3CF"
        val surface = if (variant == 3) "#9D665D" else "#36796F"
        pixel(x, y, 92, 44, "#514E69")
        pixel(x + 2, y + 2, 88, 40, surface)
        pixel(x + 7, y + 6, 78, 1, line)
        pixel(x + 7, y + 36, 78, 1, line)
        pixel(x + 7, y + 7, 1, 29, line)
        pixel(x + 84, y + 7, 1, 29, line)
        pixel(x + 45, y + 7, 1, 29, line)
        pixel(x + 41, y + 17, 9, 9, line)
        pixel(x + 43, y + 19, 5, 5, surface)
        for (goal in listOf(3, 83)) {
            pixel(x + goal, y + 15, 6, 13, line)
            pixel(x + goal + 1, y + 16, 4, 11, "#879DAC")
            for (net in 18..25 step 3) pixel(x + goal + 1, y + net, 4, 1, line)
        }
        pixel(x + 54, y + 28, 4, 4, "#2B2540")
        pixel(x + 55, y + 28, 2, 2, "#FFF8E6")
    }

    fun hopscotch(x: Int, y: Int) {
        for (row in 0..4) {
            val paired = row == 1 || row == 3
            val offset = if (paired) 0 else 4
            val width = if (paired) 16 else 8
            pixel(x + offset, y + row * 5, width, 5, "#FFF8E6")
            pixel(x + offset + 1, y + row * 5 + 1, width - 2, 3, "#6B90AD")
            if (paired) pixel(x + 7, y + row * 5 + 1, 2, 3, "#FFF8E6")
        }
    }

    fun busShelter(x: Int, y: Int) {
        pixel(x, y + 24, 48, 5, "#67637E")
        pixel(x + 3, y + 5, 38, 17, "#73909F")
        pixel(x + 7, y + 7, 29, 2, "#B9DAEC")
        pixel(x + 10, y + 15, 25, 4, "#C58C68")
        pixel(x + 3, y + 4, 3, 22, "#40394F")
        pixel(x + 38, y + 4, 3, 22, "#40394F")
        pixel(x, y, 44, 6, "#40394F")
        pixel(x + 2, y + 1, 40, 2, "#A6ABC5")
        pixel(x + 47, y + 2, 2, 24, "#40394F")
        pixel(x + 44, y + 2, 8, 7, "#7CC6F5")
        pixel(x + 46, y + 4, 4, 3, "#2B2540")
    }

    when (world) {
        1 -> {
            paving(5, 4, 350, 52)
            playCourt(12, 6)
            hopscotch(109, 23)
            facade(132, 96, "#DCA783", "#596C90", "ESCUELA")
            pixel(246, 10, 101, 42, "#67637E")
            pixel(248, 12, 97, 38, "#514E69")
            for (edge in listOf(252, 340)) pixel(edge, 15, 2, 31, "#D3C9B2")
            schoolBus(256, 15 + variant)
            prop("tree_autumn", 5, 354, 32, 49)
            bench(9, 416)
            paving(322, 378, 34, 61)
            if (variant == 1 || variant == 3) hopscotch(330, 403)
            else prop("decor_bikerack_campus", 324, 379, 28, 24)
            planter(323, 431, 27, "#FFE07A")
        }
        2 -> {
            paving(5, 35, 350, 21)
            val titles = listOf("CAFÉ" to "PAN", "TIENDA" to "TALLER", "LIBROS" to "CAFÉ", "MERCADO" to "PAN")
            val (first, second) = titles[variant]
            facade(13, 105, "#996A77", "#56546F", first, windowsLit = true)
            facade(237, 110, "#798399", "#514D70", second, windowsLit = true)
            busShelter(155, 22)
            paving(4, 361, 42, 73)
            prop("tree_autumn", 9, 359, 30, 40)
            bench(10, 407)
            paving(316, 390, 40, 49)
            planter(322, 418, 29, "#7CC6F5")
        }
        else -> {
            val walls = listOf("#D7A98B", "#C28A83", "#ADB58A", "#D5A171")
            paving(42, 44, 30, 12)
            paving(269, 44, 30, 12)
            facade(15, 84, walls[variant], "#95605B", "")
            facade(241, 86, walls[(variant + 2) % 4], "#62778B", "")
            prop("decor_fence_barrio", 104, 23, 44, 24)
            prop("tree", 153, 1, 34, 47)
            prop("tree_autumn", 201, 4, 29, 41)
            prop("decor_mailbox_barrio", 99, 29, 25, 24)
            planter(137, 42, 28, "#F27A6B")
            planter(198, 42, 31, "#FFE07A")
            prop("tree", 5, 354, 32, 55)
            bench(10, 418)
            prop("decor_fence_barrio", 317, 369, 39, 26)
            planter(320, 408, 32, "#F27A6B")
            planter(323, 434, 25, "#FFE07A")
        }
    }
    return BoardScenery(pixels, sprites, lights, signs)
}
