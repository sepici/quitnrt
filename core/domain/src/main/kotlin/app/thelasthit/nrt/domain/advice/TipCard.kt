package app.thelasthit.nrt.domain.advice

import java.time.LocalDate

/** A tip after logging that's on Today: its strategy, what it answers, and the dose that brought it. */
data class ShowingTip(
    /** The strategy's key in the library. */
    val strategy: String,
    /** The dose's tag the tip answers, or null when it's for a slip. */
    val tag: String?,
    val slip: Slip?,
    /** The dose the tip came after, so taking that dose back takes the tip too. */
    val entryId: String,
)

/**
 * The tip after logging on Today (PLAN §9), for one logical day: the tip showing, and what's had a
 * tip that day. A tip sits in the advice line's place until it's closed or the day ends, and each
 * tag gets at most one a day, as does a slip.
 */
data class TipCard(
    val date: LocalDate,
    /** The tags that have had a tip on [date], and [SLIP] once a slip has. */
    val shown: Set<String>,
    /** The tip on Today, or null once it's closed. */
    val showing: ShowingTip?,
) {
    /** The tip on Today on [today]: none on any other day. */
    fun showingOn(today: LocalDate): ShowingTip? = showing?.takeIf { date == today }

    companion object {
        /**
         * The mark for a tip after a slip. Tag keys, the user's own included, are letters, digits
         * and underscores, so it can't be one.
         */
        const val SLIP = "#slip"

        private fun markOf(tag: String?, slip: Slip?): String? = if (slip != null) SLIP else tag

        /**
         * The card after a dose [entryId] is logged on [today], with [tip] the matcher's tip for it.
         * A new day starts empty. A tip whose tag, or a slip, has had one today is left out, and so
         * is no tip at all: then whatever is showing stays. Otherwise the new tip takes the place
         * of the one showing.
         */
        fun afterLog(card: TipCard?, today: LocalDate, tip: LoggedTip?, entryId: String): TipCard? {
            val current = card?.takeIf { it.date == today }
            val mark = tip?.let { markOf(it.tag, it.slip) } ?: return current
            if (current != null && mark in current.shown) return current
            return TipCard(
                date = today,
                shown = current?.shown.orEmpty() + mark,
                showing = ShowingTip(tip.strategy.key, tip.tag, tip.slip, entryId),
            )
        }

        /** The card once the tip showing on [today] is closed. Its tag still counts as having had one. */
        fun close(card: TipCard?, today: LocalDate): TipCard? = card?.takeIf { it.date == today }?.copy(showing = null)

        /**
         * The card once the dose [entryId] is taken back, by Undo: a tip it brought goes, and its
         * tag can have one again, since the dose it answered is gone.
         */
        fun undo(card: TipCard?, entryId: String): TipCard? {
            val showing = card?.showing?.takeIf { it.entryId == entryId } ?: return card
            return card.copy(shown = card.shown - setOfNotNull(markOf(showing.tag, showing.slip)), showing = null)
        }
    }
}
