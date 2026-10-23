package app.thelasthit.nrt.domain.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

private fun resource(path: String): String =
    requireNotNull(MasterPalette::class.java.getResourceAsStream(path)) { "No resource $path" }
        .bufferedReader().use { it.readText() }

private fun parseColour(hex: String): Int {
    require(Regex("#[0-9a-fA-F]{6}").matches(hex)) { "Colour $hex isn't #rrggbb" }
    return (0xFF000000 or hex.substring(1).toLong(16)).toInt()
}

/**
 * The game layer's master palette, one letter per colour, built to match the app's colours.
 * Written by `art/tools/icons.py` to `icons/palette.json`.
 */
class MasterPalette private constructor(private val colours: Map<Char, Int>) {
    val letters: Set<Char> get() = colours.keys

    val size: Int get() = colours.size

    /** The ARGB colour of [letter], or null if the palette has no such letter. */
    operator fun get(letter: Char): Int? = colours[letter]

    companion object {
        fun bundled(): MasterPalette = parse(resource("/icons/palette.json"))

        fun parse(text: String): MasterPalette {
            val file = json.decodeFromString(PaletteFile.serializer(), text)
            val colours = file.colours.entries.associate { (key, hex) ->
                require(key.length == 1 && key != ".") { "Palette letter '$key' must be one character other than '.'" }
                key[0] to parseColour(hex)
            }
            return MasterPalette(colours)
        }
    }

    @Serializable
    private data class PaletteFile(val colours: Map<String, String>)
}

/**
 * A square pixel icon in the master palette, at most [IconSheet.MAX_COLOURS] colours, drawn with
 * no smoothing like the sprites.
 */
class PixelIcon internal constructor(
    val key: String,
    val size: Int,
    /** Palette letters, row by row, with '.' for transparent. */
    private val letters: CharArray,
    private val palette: MasterPalette,
) {
    /** The palette letters the icon uses. */
    val colours: Set<Char> = letters.filterTo(mutableSetOf()) { it != '.' }

    /** ARGB pixels, row by row, ready to turn into a bitmap. Transparent pixels are 0. */
    fun argbPixels(): IntArray = IntArray(letters.size) { i ->
        letters[i].let { if (it == '.') 0 else palette[it]!! }
    }

    /** The icon's shape in one colour, for a badge that isn't unlocked yet. */
    fun silhouettePixels(argb: Int): IntArray = IntArray(letters.size) { i -> if (letters[i] == '.') 0 else argb }
}

/**
 * A set of icons, the gear or the badges, from `icons/gear.json` or `icons/badges.json`. Each is
 * rows of master palette letters, written by `art/tools/icons.py`.
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
            parse(resource("/icons/${kind.file}.json"), palette)

        fun parse(text: String, palette: MasterPalette): IconSheet {
            val file = json.decodeFromString(IconFile.serializer(), text)
            val size = file.size
            require(size in 8..64) { "Icon size $size is out of range" }
            val icons = file.icons.mapValues { (key, rows) ->
                require(rows.size == size) { "Icon $key has ${rows.size} rows, expected $size" }
                val letters = CharArray(size * size)
                rows.forEachIndexed { y, row ->
                    require(row.length == size) { "Icon $key row $y is ${row.length} wide, expected $size" }
                    row.forEachIndexed { x, ch ->
                        require(ch == '.' || palette[ch] != null) { "Icon $key uses '$ch', which isn't in the palette" }
                        letters[y * size + x] = ch
                    }
                }
                PixelIcon(key, size, letters, palette).also {
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
        val icons: Map<String, List<String>>,
        val groups: Map<String, List<String>> = emptyMap(),
    )
}
