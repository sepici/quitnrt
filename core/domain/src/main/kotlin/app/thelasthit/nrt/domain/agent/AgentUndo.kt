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
 */
object AgentUndo {
    val WINDOW: Duration = Duration.ofHours(24)

    /** The earliest an entry can have been written, at [now], to be undone. */
    fun since(now: Instant): Instant = now.minus(WINDOW)

    fun canUndo(writtenAt: Instant, now: Instant): Boolean = !writtenAt.isBefore(since(now))
}
