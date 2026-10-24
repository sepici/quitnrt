package app.thelasthit.nrt.domain.agent

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.dose.sum
import app.thelasthit.nrt.domain.log.DaySummary
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.WeekSummary
import app.thelasthit.nrt.domain.programme.Programme
import app.thelasthit.nrt.domain.programme.ProgrammeStatus
import app.thelasthit.nrt.domain.programme.WeekTarget
import java.time.LocalDate

/** Where the programme is, for an agent: none, measuring a baseline, running, overtime or done. */
enum class PlanStage {
    NONE,
    BASELINE,
    ACTIVE,
    OVERTIME,
    COMPLETE;

    companion object {
        /** An abandoned programme isn't current, so it reads as none. */
        fun of(status: ProgrammeStatus?): PlanStage = when (status) {
            null, ProgrammeStatus.ABANDONED -> NONE
            ProgrammeStatus.BASELINE -> BASELINE
            ProgrammeStatus.ACTIVE -> ACTIVE
            ProgrammeStatus.OVERTIME -> OVERTIME
            ProgrammeStatus.COMPLETE -> COMPLETE
        }
    }
}

/**
 * The week an agent hears about: the programme week today falls in while a plan runs, or else
 * Monday to Sunday, as Today's week columns show it.
 */
data class AgentWeek(
    /** The programme week's number, or null for a calendar week. */
    val number: Int?,
    val start: LocalDate,
    val end: LocalDate,
    /** Everything logged on the week's days so far. */
    val total: Dose,
    /** The week's allowance, or null for a calendar week. Zero for an NRT-free week. */
    val allowance: Dose?,
) {
    val isNrtFree: Boolean get() = allowance == Dose.ZERO

    /** Whether the week is at or under its allowance so far, or null without one. */
    val withinAllowance: Boolean? get() = allowance?.let { total <= it }
}

/** The demon's state, once a plan has started it. */
data class AgentDemon(val name: String?, val hp: Int, val maxHp: Int, val bankedHits: Int)

/**
 * What `getNrtSummary` tells an agent (PLAN §12): today's mg against the daily guide, the week
 * against its allowance, the last dose, and the demon's HP and banked hits.
 *
 * The guide and the allowance are ceilings, so the summary gives each beside the total and says
 * whether the total is within it. It never works out what's "left" of either, which would read
 * as an amount still to take.
 */
data class AgentSummary(
    /** Today's logical day. */
    val today: LocalDate,
    /** Everything logged today, patches included. */
    val todayTotal: Dose,
    val todayDoses: Int,
    /** Today's daily guide while a plan week runs, or null. Zero in an NRT-free week. */
    val dailyGuide: Dose?,
    val week: AgentWeek,
    /** The latest dose that isn't a patch, from today or the days [entries][of] reached back to. */
    val lastDose: Entry?,
    val stage: PlanStage,
    val demon: AgentDemon?,
) {
    /** Whether today is at or under the daily guide so far, or null without one. */
    val withinGuide: Boolean? get() = dailyGuide?.let { todayTotal <= it }

    companion object {
        /** How far back the summary looks for the last dose. */
        const val LAST_DOSE_DAYS = 30

        /** The first logical day the summary needs entries from, for [today]. */
        fun from(today: LocalDate): LocalDate = today.minusDays(LAST_DOSE_DAYS.toLong())

        /**
         * The summary on [today]. [entries] are the live entries from [from] to [today] (more
         * are ignored). [plan] is the running programme's plan, if it has started.
         */
        fun of(
            today: LocalDate,
            entries: List<Entry>,
            stage: PlanStage,
            plan: Programme?,
            demon: AgentDemon?,
        ): AgentSummary {
            val relevant = entries.filter { it.logicalDate in from(today)..today }
            val day = DaySummary.of(today, relevant)
            val planWeek = plan?.weekOn(today)?.takeIf { stage == PlanStage.ACTIVE || stage == PlanStage.OVERTIME }
            val week = if (planWeek != null) {
                AgentWeek(
                    number = planWeek.number,
                    start = planWeek.startDate,
                    end = planWeek.endDate,
                    total = planWeek.totalOf(relevant),
                    allowance = planWeek.target.weeklyAllowance,
                )
            } else {
                val monday = WeekSummary.mondayOf(today)
                val sunday = monday.plusDays(WeekTarget.DAYS - 1L)
                AgentWeek(
                    number = null,
                    start = monday,
                    end = sunday,
                    total = relevant.filter { it.logicalDate in monday..sunday }.map { it.total }.sum(),
                    allowance = null,
                )
            }
            return AgentSummary(
                today = today,
                todayTotal = day.total,
                todayDoses = day.entries.size,
                dailyGuide = planWeek?.target?.dailyGuide,
                week = week,
                lastDose = day.lastDose,
                stage = stage,
                demon = demon,
            )
        }
    }
}
