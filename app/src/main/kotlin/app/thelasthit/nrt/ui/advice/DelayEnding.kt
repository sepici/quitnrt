package app.thelasthit.nrt.ui.advice

import app.thelasthit.nrt.domain.advice.DelayRun
import java.time.Instant

/**
 * The delay timer's end outside its page: a notice booked as the timer starts, so the end is told
 * of even when Android has closed the app ([app.thelasthit.nrt.reminders.DelayNotices]).
 */
interface DelayEnding {
    /** Books the notice for [run]'s end, in place of any other, and clears an old one from the shade. */
    fun book(run: DelayRun)

    /** Takes back the booking and any notice in the shade: the timer was stopped, or its end is on screen. */
    fun clear()

    /**
     * The app saw [run] end at [now]. True when the app buzzes itself: it's on screen, or the
     * notice can't go up, as when notifications are off. Otherwise the notice goes up straight
     * away, without waiting for its booking. Either way an end is told of once, by whichever sees
     * it first, and only close to the end ([DelayRun.buzzesAt]).
     */
    fun ended(run: DelayRun, now: Instant): Boolean

    companion object {
        /** No notices: the app buzzes at the end itself, as long as it's close to the end. */
        val None: DelayEnding = object : DelayEnding {
            override fun book(run: DelayRun) = Unit

            override fun clear() = Unit

            override fun ended(run: DelayRun, now: Instant): Boolean = run.buzzesAt(now)
        }
    }
}
