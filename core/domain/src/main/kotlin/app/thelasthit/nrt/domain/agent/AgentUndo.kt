package app.thelasthit.nrt.domain.agent

import java.time.Duration
import java.time.Instant

/**
 * What `undoLastNrtEntry` takes back: the entry written most recently, from anywhere, as long as
 * it was written in the last [WINDOW]. It goes by when an entry was written, not when the dose was
 * taken, so "log a gum at 3 pm yesterday" then "undo that" takes back the backdated one.
 *
 * The window keeps a misheard "undo" from deleting a dose from days ago without anyone seeing,
 * since nothing on screen offers it back. Older entries are deleted from History.
 *
 * A restore from backup brings entries in with no time of writing, so none of them looks just
 * written. Nothing written at or before the last restore is undone either: the restore is where
 * undo stops, and a dose logged after it can still be undone.
 */
object AgentUndo {
    val WINDOW: Duration = Duration.ofHours(24)

    /**
     * The earliest an entry can have been written, at [now], to be undone: a day back, or just
     * after [restoredAt], the last restore from backup, if that's later.
     */
    fun since(now: Instant, restoredAt: Instant? = null): Instant {
        val dayBack = now.minus(WINDOW)
        val afterRestore = restoredAt?.plusMillis(1) ?: return dayBack
        return if (afterRestore.isAfter(dayBack)) afterRestore else dayBack
    }

    fun canUndo(writtenAt: Instant, now: Instant, restoredAt: Instant? = null): Boolean =
        !writtenAt.isBefore(since(now, restoredAt))
}
