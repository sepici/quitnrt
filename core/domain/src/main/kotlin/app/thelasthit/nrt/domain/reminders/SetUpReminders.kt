package app.thelasthit.nrt.domain.reminders

/**
 * The reminders step in set-up (PLAN §15): the reminders that are on by default, each with a
 * switch, so a fresh install asks for notifications before anything is due. "Forgot to log" stays
 * in Settings, off, since it goes by a usual first dose that a new install doesn't have yet.
 */
object SetUpReminders {

    /** The reminders set-up offers, in the order it lists them, which is Settings' order too. */
    val offered: List<Reminder> = listOf(Reminder.ALLOWANCE, Reminder.CHECK_IN, Reminder.WEEKLY_RESULT, Reminder.ACHIEVEMENTS)

    /** What set-up starts with: each reminder it offers as it is by default. */
    val initial: Set<Reminder> = offered.filter { it.onByDefault }.toSet()

    /** [settings] with each reminder set-up offers switched as [on] says. The rest stay as they are. */
    fun applied(settings: ReminderSettings, on: Set<Reminder>): ReminderSettings =
        offered.fold(settings) { applied, reminder -> applied.with(reminder, reminder in on) }

    /** Whether [on] needs notifications allowed: at least one reminder set-up offers is on. */
    fun needsNotifications(on: Set<Reminder>): Boolean = offered.any { it in on }
}
