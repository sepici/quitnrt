package app.thelasthit.nrt.domain.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

/**
 * A square pixel icon, at most [IconSheet.MAX_COLOURS] colours from the master palette, drawn with
 * no smoothing like the sprites.
 */
class PixelIcon internal constructor(
    val key: String,
    val size: Int,
    /** Colour key letters, row by row, with '.' for transparent. */
    private val letters: CharArray,
    /** The sheet's colour key: letter to ARGB. */
    private val colourKey: Map<Char, Int>,
) {
    /** The colour key letters the icon uses. */
    val colours: Set<Char> = letters.filterTo(mutableSetOf()) { it != '.' }

    /** ARGB pixels, row by row, ready to turn into a bitmap. Transparent pixels are 0. */
    fun argbPixels(): IntArray = IntArray(letters.size) { i ->
        letters[i].let { if (it == '.') 0 else colourKey.getValue(it) }
    }
}

/**
 * A set of icons, the gear or the badges, from `icons/gear.json` or `icons/badges.json`, written
 * by `art/tools/icons.py`. Each file has a colour key, one letter per colour, and every colour in
 * it must be in the [MasterPalette]. Icons are rows of those letters.
 */
class IconSheet private constructor(
    val size: Int,
    private val icons: Map<String, PixelIcon>,
    /** For badges: each group's keys, in order. Empty for gear. */
    val groups: Map<String, List<String>>,
) {
    enum class Kind(val file: String) { GEAR("gear"), BADGES("badges") }

    val keys: Set<String> get() = icons.keys

    operator fun get(key: String): PixelIcon? = icons[key]

    fun icon(gear: Gear): PixelIcon = requireNotNull(icons[gear.key]) { "No icon for ${gear.key}" }

    fun icon(achievement: Achievement): PixelIcon =
        requireNotNull(icons[achievement.key]) { "No badge for ${achievement.key}" }

    companion object {
        const val MAX_COLOURS = 8

        fun bundled(kind: Kind, palette: MasterPalette = MasterPalette.bundled()): IconSheet =
            parse(resourceText("/icons/${kind.file}.json"), palette)

        fun parse(text: String, palette: MasterPalette): IconSheet {
            val file = json.decodeFromString(IconFile.serializer(), text)
            val size = file.size
            require(size in 8..64) { "Icon size $size is out of range" }
            val colourKey = file.colours.entries.associate { (letter, hex) ->
                require(letter.length == 1 && letter != ".") { "Colour key '$letter' must be one character other than '.'" }
                val argb = parseColour(hex)
                require(argb in palette) { "Colour $hex for '$letter' isn't in the master palette" }
                letter[0] to argb
            }
            val icons = file.icons.mapValues { (key, rows) ->
                require(rows.size == size) { "Icon $key has ${rows.size} rows, expected $size" }
                val letters = CharArray(size * size)
                rows.forEachIndexed { y, row ->
                    require(row.length == size) { "Icon $key row $y is ${row.length} wide, expected $size" }
                    row.forEachIndexed { x, ch ->
                        require(ch == '.' || ch in colourKey) { "Icon $key uses '$ch', which isn't in the colour key" }
                        letters[y * size + x] = ch
                    }
                }
                PixelIcon(key, size, letters, colourKey).also {
                    require(it.colours.size <= MAX_COLOURS) { "Icon $key uses ${it.colours.size} colours, at most $MAX_COLOURS" }
                }
            }
            for ((group, keys) in file.groups) {
                for (key in keys) require(key in icons) { "Group $group lists $key, which has no icon" }
            }
            return IconSheet(size, icons, file.groups)
        }
    }

    @Serializable
    private data class IconFile(
        val size: Int,
        val colours: Map<String, String>,
        val icons: Map<String, List<String>>,
        val groups: Map<String, List<String>> = emptyMap(),
    )
}
