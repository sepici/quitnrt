package app.thelasthit.nrt.domain.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

internal fun resourceText(path: String): String =
    requireNotNull(MasterPalette::class.java.getResourceAsStream(path)) { "No resource $path" }
        .bufferedReader().use { it.readText() }

/** `#rrggbb` as an opaque ARGB colour. */
internal fun parseColour(hex: String): Int {
    require(Regex("#[0-9a-fA-F]{6}").matches(hex)) { "Colour $hex isn't #rrggbb" }
    return (0xFF000000 or hex.substring(1).toLong(16)).toInt()
}

/**
 * The one master palette for the game layer: creatures, gear and badges all draw their colours
 * from it, so they sit together. It's `palette.json` at the resource root, as named ramps of
 * `#rrggbb` that run dark to light.
 */
class MasterPalette private constructor(
    /** Each ramp's colours as ARGB, dark to light. */
    val ramps: Map<String, List<Int>>,
) {
    /** Every colour in the palette, as ARGB. */
    val colours: Set<Int> = ramps.values.flatten().toSet()

    val size: Int get() = colours.size

    operator fun contains(argb: Int): Boolean = argb in colours

    companion object {
        fun bundled(): MasterPalette = parse(resourceText("/palette.json"))

        fun parse(text: String): MasterPalette {
            val file = json.decodeFromString(PaletteFile.serializer(), text)
            require(file.ramps.isNotEmpty()) { "The palette has no ramps" }
            val ramps = file.ramps.mapValues { (name, hexes) ->
                require(hexes.isNotEmpty()) { "Ramp $name is empty" }
                hexes.map(::parseColour)
            }
            return MasterPalette(ramps)
        }
    }

    @Serializable
    private data class PaletteFile(val ramps: Map<String, List<String>>)
}
