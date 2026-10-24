package app.thelasthit.nrt.ui.game

import androidx.annotation.StringRes
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.game.Achievement
import app.thelasthit.nrt.domain.game.Gear

/** A badge's name, from strings_game.xml. */
@StringRes
fun badgeName(achievement: Achievement): Int = when (achievement) {
    Achievement.FIRST_ENTRY -> R.string.badge_first_entry
    Achievement.LOGGED_3_DAYS -> R.string.badge_logged_days_3
    Achievement.LOGGED_7_DAYS -> R.string.badge_logged_days_7
    Achievement.LOGGED_14_DAYS -> R.string.badge_logged_days_14
    Achievement.LOGGED_28_DAYS -> R.string.badge_logged_days_28
    Achievement.LOGGED_60_DAYS -> R.string.badge_logged_days_60
    Achievement.LOGGED_90_DAYS -> R.string.badge_logged_days_90
    Achievement.STREAK_10_DAYS -> R.string.badge_streak_10
    Achievement.STREAK_21_DAYS -> R.string.badge_streak_21
    Achievement.STREAK_40_DAYS -> R.string.badge_streak_40
    Achievement.BASELINE_DONE -> R.string.badge_baseline_done
    Achievement.HONEST_LEDGER -> R.string.badge_honest_ledger
    Achievement.TAGGED_25 -> R.string.badge_tagged_25
    Achievement.TAGGED_100 -> R.string.badge_tagged_100
    Achievement.FIRST_DAY_IN_GUIDE -> R.string.badge_first_day_in_guide
    Achievement.FIRST_WEEK_MET -> R.string.badge_first_week_met
    Achievement.WEEKS_MET_3 -> R.string.badge_weeks_met_3
    Achievement.WEEKS_MET_6 -> R.string.badge_weeks_met_6
    Achievement.WEEKS_MET_10 -> R.string.badge_weeks_met_10
    Achievement.BACK_IN_STEP -> R.string.badge_back_in_step
    Achievement.HALFWAY -> R.string.badge_halfway
    Achievement.DOWN_A_STRENGTH -> R.string.badge_down_a_strength
    Achievement.FINAL_TAPER_WEEK -> R.string.badge_final_taper_week
    Achievement.WEEK_10_PERCENT_UNDER -> R.string.badge_week_under_by_10
    Achievement.FIVE_DAYS_UNDER -> R.string.badge_five_days_under
    Achievement.THREE_WEEKS_UNDER -> R.string.badge_three_weeks_under
    Achievement.LATER_FIRST_DOSE -> R.string.badge_later_first_dose
    Achievement.LONGER_GAPS -> R.string.badge_longer_gaps
    Achievement.TOP_TRIGGER_DOWN -> R.string.badge_top_trigger_down
    Achievement.BREATHING_1 -> R.string.badge_breathing_1
    Achievement.BREATHING_10 -> R.string.badge_breathing_10
    Achievement.BREATHING_25 -> R.string.badge_breathing_25
    Achievement.DEMON_DEFEATED -> R.string.badge_demon_defeated
}

/** What a badge is for, in a line. */
@StringRes
fun badgeFor(achievement: Achievement): Int = when (achievement) {
    Achievement.FIRST_ENTRY -> R.string.badge_first_entry_for
    Achievement.LOGGED_3_DAYS -> R.string.badge_logged_days_3_for
    Achievement.LOGGED_7_DAYS -> R.string.badge_logged_days_7_for
    Achievement.LOGGED_14_DAYS -> R.string.badge_logged_days_14_for
    Achievement.LOGGED_28_DAYS -> R.string.badge_logged_days_28_for
    Achievement.LOGGED_60_DAYS -> R.string.badge_logged_days_60_for
    Achievement.LOGGED_90_DAYS -> R.string.badge_logged_days_90_for
    Achievement.STREAK_10_DAYS -> R.string.badge_streak_10_for
    Achievement.STREAK_21_DAYS -> R.string.badge_streak_21_for
    Achievement.STREAK_40_DAYS -> R.string.badge_streak_40_for
    Achievement.BASELINE_DONE -> R.string.badge_baseline_done_for
    Achievement.HONEST_LEDGER -> R.string.badge_honest_ledger_for
    Achievement.TAGGED_25 -> R.string.badge_tagged_25_for
    Achievement.TAGGED_100 -> R.string.badge_tagged_100_for
    Achievement.FIRST_DAY_IN_GUIDE -> R.string.badge_first_day_in_guide_for
    Achievement.FIRST_WEEK_MET -> R.string.badge_first_week_met_for
    Achievement.WEEKS_MET_3 -> R.string.badge_weeks_met_3_for
    Achievement.WEEKS_MET_6 -> R.string.badge_weeks_met_6_for
    Achievement.WEEKS_MET_10 -> R.string.badge_weeks_met_10_for
    Achievement.BACK_IN_STEP -> R.string.badge_back_in_step_for
    Achievement.HALFWAY -> R.string.badge_halfway_for
    Achievement.DOWN_A_STRENGTH -> R.string.badge_down_a_strength_for
    Achievement.FINAL_TAPER_WEEK -> R.string.badge_final_taper_week_for
    Achievement.WEEK_10_PERCENT_UNDER -> R.string.badge_week_under_by_10_for
    Achievement.FIVE_DAYS_UNDER -> R.string.badge_five_days_under_for
    Achievement.THREE_WEEKS_UNDER -> R.string.badge_three_weeks_under_for
    Achievement.LATER_FIRST_DOSE -> R.string.badge_later_first_dose_for
    Achievement.LONGER_GAPS -> R.string.badge_longer_gaps_for
    Achievement.TOP_TRIGGER_DOWN -> R.string.badge_top_trigger_down_for
    Achievement.BREATHING_1 -> R.string.badge_breathing_1_for
    Achievement.BREATHING_10 -> R.string.badge_breathing_10_for
    Achievement.BREATHING_25 -> R.string.badge_breathing_25_for
    Achievement.DEMON_DEFEATED -> R.string.badge_demon_defeated_for
}

/** A badge group's heading on the shelf. */
@StringRes
fun badgeGroupName(group: Achievement.Group): Int = when (group) {
    Achievement.Group.LOGGING -> R.string.badge_group_logging
    Achievement.Group.MATCHING -> R.string.badge_group_matching
    Achievement.Group.BEATING -> R.string.badge_group_beating
    Achievement.Group.HABITS -> R.string.badge_group_habits
    Achievement.Group.THE_END -> R.string.badge_group_end
}

/** A piece of gear's name. */
@StringRes
fun gearName(gear: Gear): Int = when (gear) {
    Gear.WOODEN_STICK -> R.string.gear_wooden_stick
    Gear.WOODEN_SWORD -> R.string.gear_wooden_sword
    Gear.IRON_SWORD -> R.string.gear_iron_sword
    Gear.AXE -> R.string.gear_axe
    Gear.WAR_HAMMER -> R.string.gear_war_hammer
    Gear.FIRE_STAFF -> R.string.gear_fire_staff
    Gear.LIGHTNING_BOW -> R.string.gear_lightning_bow
    Gear.LEGENDARY_BLADE -> R.string.gear_legendary_blade
}

/** What brings a piece of gear, in a line. */
@StringRes
fun gearFor(gear: Gear): Int = when (gear) {
    Gear.WOODEN_STICK -> R.string.gear_wooden_stick_for
    Gear.WOODEN_SWORD -> R.string.gear_wooden_sword_for
    Gear.IRON_SWORD -> R.string.gear_iron_sword_for
    Gear.AXE -> R.string.gear_axe_for
    Gear.WAR_HAMMER -> R.string.gear_war_hammer_for
    Gear.FIRE_STAFF -> R.string.gear_fire_staff_for
    Gear.LIGHTNING_BOW -> R.string.gear_lightning_bow_for
    Gear.LEGENDARY_BLADE -> R.string.gear_legendary_blade_for
}
