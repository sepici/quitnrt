package app.thelasthit.nrt.link

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.product.PillLabel
import app.thelasthit.nrt.domain.product.UserProduct
import app.thelasthit.nrt.ui.format.icon
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.format.pillLabelText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The launcher shortcuts for the first [MAX_PRODUCTS] products on the quick log, kept in step
 * with it. Each opens the product's `lasthit://log` link, so it logs through the same path as any
 * link. "Log a dose" is a static shortcut in `res/xml/shortcuts.xml`, so it's there before the
 * app has run.
 */
class QuickLogShortcuts(private val context: Context) {

    /** What a shortcut shows and opens. Compared to skip republishing when nothing changed. */
    data class Spec(val productId: String, val shortLabel: String, val longLabel: String, @DrawableRes val icon: Int)

    /**
     * Publishes the shortcuts again whenever [quickLog] changes them. Runs until cancelled. Each
     * publish is on the main thread, so the shortcuts only change between the main thread's tasks,
     * which a test can wait on.
     */
    suspend fun follow(quickLog: Flow<List<UserProduct>>) {
        quickLog.map(::specsFor).distinctUntilChanged().collect { specs ->
            withContext(Dispatchers.Main) { publish(specs) }
        }
    }

    /**
     * Short labels are the quick-log pills' ("Gum 2 mg"), worked out over the whole quick log so
     * two products tell apart the same way they do on Today. Long labels say what a tap does, as
     * the pills do to a screen reader: "Log Nicorette gum 2 mg".
     */
    fun specsFor(quickLog: List<UserProduct>): List<Spec> {
        val labels = PillLabel.of(quickLog)
        val resources = context.resources
        return quickLog.take(MAX_PRODUCTS).map { product ->
            val named = resources.getString(R.string.dose_named, product.shortName, product.perUnit.mgText())
            Spec(
                productId = product.id,
                shortLabel = resources.pillLabelText(labels.getValue(product.id)),
                longLabel = resources.getString(R.string.today_quick_log_description, named),
                icon = product.form.icon,
            )
        }
    }

    private fun publish(specs: List<Spec>) {
        val shortcuts = specs.mapIndexed { rank, spec ->
            ShortcutInfoCompat.Builder(context, ID_PREFIX + spec.productId)
                .setShortLabel(spec.shortLabel)
                .setLongLabel(spec.longLabel)
                .setIcon(IconCompat.createWithAdaptiveBitmap(iconBitmap(spec.icon)))
                .setIntent(LogLinks.shortcutIntent(context, LogLinks.forProduct(spec.productId)))
                .setRank(rank)
                .build()
        }
        try {
            // Also updates a pinned shortcut with the same ID, such as after a product is renamed.
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        } catch (e: IllegalStateException) {
            // The phone is locked after a restart, so shortcuts can't change yet. The next change publishes them.
        }
    }

    /**
     * A shortcut's adaptive icon: the glyph on the stone tile that dose rows use, so a dose never
     * sits on green. The launcher masks it to its own shape.
     */
    fun iconBitmap(@DrawableRes glyph: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (ICON_DP * density).roundToInt()
        val glyphSize = (GLYPH_DP * density).roundToInt()
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        canvas.drawColor(ContextCompat.getColor(context, R.color.shortcut_tile))
        val drawable = checkNotNull(ContextCompat.getDrawable(context, glyph)).mutate()
        drawable.setTint(ContextCompat.getColor(context, R.color.shortcut_glyph))
        val inset = (size - glyphSize) / 2
        drawable.setBounds(inset, inset, inset + glyphSize, inset + glyphSize)
        drawable.draw(canvas)
        return bitmap
    }

    companion object {
        const val MAX_PRODUCTS = 3
        const val ID_PREFIX = "log-"

        /** An adaptive icon's full size, of which the middle 72 dp shows. */
        private const val ICON_DP = 108

        /** The glyph's size, as in res/drawable/ic_shortcut_log_glyph.xml. */
        private const val GLYPH_DP = 36
    }
}
