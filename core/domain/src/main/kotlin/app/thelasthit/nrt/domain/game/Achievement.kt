package app.thelasthit.nrt.domain.game

/**
 * The achievements of PLAN 8.1, in its order. Each unlocks a badge, and none deals damage. The
 * [key] names the badge in `icons/badges.json` and is what gets stored. [AchievementRules] has
 * the rule behind each.
 *
 * Some thresholds were tuned so badges come at a steady pace (ROADMAP P4-4, `UnlockPaceTest`).
 * The streaks are 10, 21 and 40 days rather than PLAN's 7, 14 and 30. For anyone who logs every
 * day, those matched the logged-days tiers and landed on the same days. The last weeks-met tier
 * is 10 rather than 9, so a badge still comes in the NRT-free weeks.
 */
enum class Achievement(val key: String, val group: Group) {
    FIRST_ENTRY("first_entry", Group.LOGGING),
    LOGGED_3_DAYS("logged_days_3", Group.LOGGING),
    LOGGED_7_DAYS("logged_days_7", Group.LOGGING),
    LOGGED_14_DAYS("logged_days_14", Group.LOGGING),
    LOGGED_30_DAYS("logged_days_30", Group.LOGGING),
    LOGGED_60_DAYS("logged_days_60", Group.LOGGING),
    LOGGED_90_DAYS("logged_days_90", Group.LOGGING),
    STREAK_10_DAYS("streak_10", Group.LOGGING),
    STREAK_21_DAYS("streak_21", Group.LOGGING),
    STREAK_40_DAYS("streak_40", Group.LOGGING),
    BASELINE_DONE("baseline_done", Group.LOGGING),

    /** Backdated a forgotten entry. */
    HONEST_LEDGER("honest_ledger", Group.LOGGING),
    TAGGED_25("tagged_25", Group.LOGGING),
    TAGGED_100("tagged_100", Group.LOGGING),

    FIRST_DAY_IN_GUIDE("first_day_in_guide", Group.MATCHING),
    FIRST_WEEK_MET("first_week_met", Group.MATCHING),
    WEEKS_MET_3("weeks_met_3", Group.MATCHING),
    WEEKS_MET_6("weeks_met_6", Group.MATCHING),
    WEEKS_MET_10("weeks_met_10", Group.MATCHING),

    /** Caught up after a missed week. */
    BACK_IN_STEP("back_in_step", Group.MATCHING),

    /** The daily guide at half the baseline or less. */
    HALFWAY("halfway", Group.MATCHING),
    DOWN_A_STRENGTH("down_a_strength", Group.MATCHING),
    FINAL_TAPER_WEEK("final_taper_week", Group.MATCHING),

    /** A week at least 10% under its allowance. */
    WEEK_10_PERCENT_UNDER("week_under_by_10", Group.BEATING),

    /** 5 days under the daily guide in one week. */
    FIVE_DAYS_UNDER("five_days_under", Group.BEATING),

    /** 3 weeks in a row under the allowance. */
    THREE_WEEKS_UNDER("three_weeks_under", Group.BEATING),

    /** The first dose 30 minutes later than the baseline's, across a whole week. */
    LATER_FIRST_DOSE("later_first_dose", Group.HABITS),

    /** The average gap between doses 30 minutes longer than the baseline's. */
    LONGER_GAPS("longer_gaps", Group.HABITS),

    /** The top trigger's share down by a third against the baseline. */
    TOP_TRIGGER_DOWN("top_trigger_down", Group.HABITS),
    BREATHING_1("breathing_1", Group.HABITS),
    BREATHING_10("breathing_10", Group.HABITS),
    BREATHING_25("breathing_25", Group.HABITS),

    DEMON_DEFEATED("demon_defeated", Group.THE_END);

    /** The groups of PLAN 8.1. Each group's badges share a frame, so they read as a set. */
    enum class Group(val key: String) {
        LOGGING("logging"),
        MATCHING("matching"),
        BEATING("beating"),
        HABITS("habits"),
        THE_END("end"),
    }

    companion object {
        fun fromKey(key: String?): Achievement? = entries.firstOrNull { it.key == key }
    }
}
