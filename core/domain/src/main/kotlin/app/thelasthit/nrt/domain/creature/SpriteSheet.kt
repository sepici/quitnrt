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
    /** The slot labelled "eye" in the sprite file, or -1. */
    private val eyeSlot: Int,
    /** The slot labelled "outline" in the sprite file, or -1. */
    private val outlineSlot: Int,
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

    /**
     * The defeat sequence in [variant], one frame after another: knocked out, then breaking apart
     * into pixels until only a few are left. Unknown variants fall back to the default.
     */
    fun defeatPixels(variant: String = defaultVariant): List<IntArray> =
        SpriteFrame.DEFEAT.map { argbPixels(it, variant) }

    /**
     * The frame's outline filled with one colour, for a creature lurking before the plan starts.
     * With [eyes], the eye pixels keep a colour of their own, so it watches from the dark.
     */
    fun silhouettePixels(frame: SpriteFrame, argb: Int, eyes: Int? = null): IntArray {
        val slots = frames.getValue(frame)
        return IntArray(slots.size) { i ->
            when {
                slots[i] < 0 -> 0
                eyes != null && slots[i].toInt() == eyeSlot -> eyes
                else -> argb
            }
        }
    }

    /**
     * The hit flash, made from the frame itself: every pixel in [argb] but the outline, which
     * keeps its colour, so the creature still has its shape on a light sky as it flashes.
     */
    fun flashPixels(frame: SpriteFrame, argb: Int, variant: String = defaultVariant): IntArray {
        val outline = palette(variant).getOrNull(outlineSlot)
        val slots = frames.getValue(frame)
        return IntArray(slots.size) { i ->
            when {
                slots[i] < 0 -> 0
                outline != null && slots[i].toInt() == outlineSlot -> outline
                else -> argb
            }
        }
    }

    /**
     * The frame's rim in [argb], one sprite pixel wide, for the dark-mode sky: (size + 2) pixels a
     * side, to draw one sprite pixel up and to the left of the frame (see [PixelRim]).
     */
    fun rimPixels(frame: SpriteFrame, argb: Int): IntArray = PixelRim.around(argbPixels(frame), size, size, argb)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** The sheet for [creature], or null if it hasn't been drawn yet. */
        fun bundled(creature: Creature): SpriteSheet? = if (creature.isDrawn) inResources(creature) else null

        /** The sheet in the resources, drawn or still waiting for review, or null if there's no file. */
        internal fun inResources(creature: Creature): SpriteSheet? {
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
            val eyeSlot = slotKeys.indexOfFirst { file.slots[it.toString()] == "eye" }
            val outlineSlot = slotKeys.indexOfFirst { file.slots[it.toString()] == "outline" }
            return SpriteSheet(file.creature, size, slotKeys, eyeSlot, outlineSlot, variants, file.defaultVariant, frames)
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
