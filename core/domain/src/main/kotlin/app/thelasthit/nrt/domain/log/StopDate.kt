package app.thelasthit.nrt.domain.log

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** About the day someone stopped smoking or vaping, which onboarding asks for. */
object StopDate {

    /** Most NRT courses run 8 to 12 weeks. */
    const val USUAL_COURSE_WEEKS = 8

    /** Under 8 weeks in, onboarding mentions the usual course length. It never stops anyone. */
    fun isEarlyInCourse(stoppedOn: LocalDate, today: LocalDate): Boolean =
        ChronoUnit.DAYS.between(stoppedOn, today) < USUAL_COURSE_WEEKS * 7

    fun weeksSince(stoppedOn: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(stoppedOn, today) / 7

    fun daysSince(stoppedOn: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(stoppedOn, today)
}
