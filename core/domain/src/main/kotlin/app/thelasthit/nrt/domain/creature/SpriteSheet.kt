package app.thelasthit.nrt.domain.creature

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A creature's pixel art: each frame is rows of palette slot letters, with `.` for transparent.
 * The same frames take any palette variant, the classic 8-bit palette swap.
 */
class SpriteSheet private constructor(
    val creature: String,
    val size: Int,
    private val slotKeys: List<Char>,
    private val variants: Map<String, IntArray>,
    val defaultVariant: String,
    private val frames: Map<SpriteFrame, ByteArray>,
) {
    val variantNames: List<String> get() = variants.keys.toList()

    /** ARGB colours of [variant] in slot order, for swatches. */
    fun palette(variant: String): IntArray = variants[variant] ?: variants.getValue(defaultVariant)

    /**
     * The frame as ARGB pixels, row by row, ready to turn into a bitmap. Transparent pixels are 0.
     * Unknown variants fall back to the default.
     */
    fun argbPixels(frame: SpriteFrame, variant: String = defaultVariant): IntArray {
        val colours = palette(variant)
        val slots = frames.getValue(frame)
        return IntArray(slots.size) { i -> slots[i].toInt().let { if (it < 0) 0 else colours[it] } }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** The sheet for [creature], or null if it hasn't been drawn yet. */
        fun bundled(creature: Creature): SpriteSheet? {
            if (!creature.isDrawn) return null
            val text = SpriteSheet::class.java.getResourceAsStream("/creatures/${creature.key}.json")
                ?.bufferedReader()?.use { it.readText() }
                ?: return null
            return parse(text)
        }

        fun parse(text: String): SpriteSheet {
            val file = json.decodeFromString(SpriteFile.serializer(), text)
            val size = file.size
            require(size in 8..128) { "Sprite size $size is out of range" }
            val slotKeys = file.slots.keys.map { key ->
                require(key.length == 1 && key != ".") { "Slot key '$key' must be one character other than '.'" }
                key[0]
            }
            require(slotKeys.size <= 8) { "At most 8 colours per sprite, found ${slotKeys.size}" }

            val variants = file.variants.mapValues { (name, colours) ->
                IntArray(slotKeys.size) { i ->
                    val hex = requireNotNull(colours[slotKeys[i].toString()]) { "Variant $name has no colour for slot ${slotKeys[i]}" }
                    parseColour(hex)
                }
            }
            require(file.defaultVariant in variants) { "Default variant ${file.defaultVariant} is missing" }

            val frames = SpriteFrame.entries.associateWith { frame ->
                val rows = requireNotNull(file.frames[frame.key]) { "Frame ${frame.key} is missing" }
                require(rows.size == size) { "Frame ${frame.key} has ${rows.size} rows, expected $size" }
                ByteArray(size * size).also { out ->
                    rows.forEachIndexed { y, row ->
                        require(row.length == size) { "Frame ${frame.key} row $y is ${row.length} wide, expected $size" }
                        row.forEachIndexed { x, ch ->
                            out[y * size + x] = if (ch == '.') {
                                -1
                            } else {
                                slotKeys.indexOf(ch).also { require(it >= 0) { "Frame ${frame.key} uses unknown slot '$ch'" } }.toByte()
                            }
                        }
                    }
                }
            }
            return SpriteSheet(file.creature, size, slotKeys, variants, file.defaultVariant, frames)
        }

        private fun parseColour(hex: String): Int {
            require(Regex("#[0-9a-fA-F]{6}").matches(hex)) { "Colour $hex isn't #rrggbb" }
            return (0xFF000000 or hex.substring(1).toLong(16)).toInt()
        }
    }

    @Serializable
    private data class SpriteFile(
        val creature: String,
        val size: Int,
        val slots: Map<String, String>,
        val variants: Map<String, Map<String, String>>,
        val defaultVariant: String,
        val frames: Map<String, List<String>>,
    )
}
