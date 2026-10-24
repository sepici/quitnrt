package app.thelasthit.nrt.link

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.thelasthit.nrt.MainActivity
import app.thelasthit.nrt.domain.log.EntrySource
import app.thelasthit.nrt.domain.matcher.LogLinkParser

/** A `lasthit://` link the app was opened with, and where it came from. */
data class IncomingLink(val link: String, val source: EntrySource)

/**
 * The `lasthit://log` links that open the app, from Routines, Tasker, an NFC sticker or the app's
 * own shortcuts. [LogLinkParser] reads them. Shortcuts open the same links, marked with
 * [EXTRA_SOURCE] so their entries say they came from a shortcut.
 */
object LogLinks {

    const val EXTRA_SOURCE = "app.thelasthit.nrt.extra.SOURCE"
    const val SOURCE_SHORTCUT = "shortcut"

    /** The app's own ways in that open a link, each recorded as its own source. */
    const val SOURCE_TILE = "tile"
    const val SOURCE_WIDGET = "widget"
    const val SOURCE_NOTIFICATION = "notification"

    private val sources = mapOf(
        SOURCE_SHORTCUT to EntrySource.SHORTCUT,
        SOURCE_TILE to EntrySource.TILE,
        SOURCE_WIDGET to EntrySource.WIDGET,
        SOURCE_NOTIFICATION to EntrySource.NOTIFICATION,
    )

    /** `lasthit://log`, which opens the log sheet. */
    val blank: Uri = Uri.Builder().scheme(LogLinkParser.SCHEME).authority(LogLinkParser.HOST).build()

    /** The link that logs one of [productId] now. */
    fun forProduct(productId: String): Uri = blank.buildUpon().appendQueryParameter("product", productId).build()

    /** What a shortcut opens: [link], in this app, marked as a shortcut. */
    fun shortcutIntent(context: Context, link: Uri): Intent = appIntent(context, link, SOURCE_SHORTCUT)

    /** [link] in this app, marked with [source], one of the `SOURCE_` names. */
    fun appIntent(context: Context, link: Uri, source: String): Intent =
        Intent(Intent.ACTION_VIEW, link, context, MainActivity::class.java).putExtra(EXTRA_SOURCE, source)

    /**
     * The link [intent] carries, or null when it carries none. Opening the app from Recents
     * repeats the intent it first came with, whose link was handled then, so that one is skipped.
     */
    fun from(intent: Intent?): IncomingLink? {
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
        val data = intent.data ?: return null
        if (!data.scheme.equals(LogLinkParser.SCHEME, ignoreCase = true)) return null
        val source = sources[intent.getStringExtra(EXTRA_SOURCE)] ?: EntrySource.DEEP_LINK
        return IncomingLink(data.toString(), source)
    }
}
