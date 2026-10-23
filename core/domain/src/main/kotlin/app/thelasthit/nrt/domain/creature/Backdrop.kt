package app.thelasthit.nrt.domain.creature

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A pixel backdrop for the game layer, in a light and a dark version. Like the sprites, it's rows
 * of palette letters, written by `art/tools/backdrops.py`. The creature stands at [spriteX],
 * [spriteY] in backdrop pixels, at the same scale. Text drawn on the sky stays above [horizon].
 */
class Backdrop private constructor(
    val scene: Scene,
    val width: Int,
    val height: Int,
    val spriteX: Int,
    val spriteY: Int,
    /** The top row of the land: hills, ground and rocks. */
    val horizon: Int,
    private val light: IntArray,
    private val dark: IntArray,
    /** A 1 px rim around the sprite, so a dark creature stays visible on a dark sky. */
    private val rims: Map<Boolean, Int>,
) {
    enum class Scene(val key: String) { BAND("band"), STAGE("stage") }

    /** ARGB pixels, row by row. */
    fun argbPixels(dark: Boolean): IntArray = if (dark) this.dark else light

    fun rim(dark: Boolean): Int? = rims[dark]

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun bundled(scene: Scene): Backdrop {
            val text = requireNotNull(Backdrop::class.java.getResourceAsStream("/backdrops/${scene.key}.json")) {
                "No backdrop for ${scene.key}"
            }.bufferedReader().use { it.readText() }
            return parse(scene, text)
        }

        fun parse(scene: Scene, text: String): Backdrop {
            val file = json.decodeFromString(BackdropFile.serializer(), text)
            fun pixels(mode: String): IntArray {
                val rows = requireNotNull(file.rows[mode]) { "No $mode rows" }
                val palette = requireNotNull(file.palettes[mode]) { "No $mode palette" }.mapValues { parseColour(it.value) }
                require(rows.size == file.height) { "$mode has ${rows.size} rows, expected ${file.height}" }
                return IntArray(file.width * file.height).also { out ->
                    rows.forEachIndexed { y, row ->
                        require(row.length == file.width) { "$mode row $y is ${row.length} wide, expected ${file.width}" }
                        row.forEachIndexed { x, ch ->
                            out[y * file.width + x] = requireNotNull(palette[ch.toString()]) { "$mode uses unknown letter '$ch'" }
                        }
                    }
                }
            }
            return Backdrop(
                scene = scene,
                width = file.width,
                height = file.height,
                spriteX = file.sprite.x,
                spriteY = file.sprite.y,
                horizon = file.horizon ?: file.height,
                light = pixels("light"),
                dark = pixels("dark"),
                rims = buildMap {
                    file.rim["light"]?.let { put(false, parseColour(it)) }
                    file.rim["dark"]?.let { put(true, parseColour(it)) }
                },
            )
        }

        private fun parseColour(hex: String): Int {
            require(Regex("#[0-9a-fA-F]{6}").matches(hex)) { "Colour $hex isn't #rrggbb" }
            return (0xFF000000 or hex.substring(1).toLong(16)).toInt()
        }
    }

    @Serializable
    private data class BackdropFile(
        val width: Int,
        val height: Int,
        val sprite: Point,
        val horizon: Int? = null,
        val rim: Map<String, String> = emptyMap(),
        val palettes: Map<String, Map<String, String>>,
        val rows: Map<String, List<String>>,
    )

    @Serializable
    private data class Point(val x: Int, val y: Int)
}
