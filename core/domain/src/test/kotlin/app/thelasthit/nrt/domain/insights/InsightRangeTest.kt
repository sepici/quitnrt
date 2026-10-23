package app.thelasthit.nrt.domain.insights

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.insights.InsightRange.DAYS_30
import app.thelasthit.nrt.domain.insights.InsightRange.DAYS_60
import app.thelasthit.nrt.domain.insights.InsightRange.DAYS_7
import app.thelasthit.nrt.domain.log.entry
import app.thelasthit.nrt.domain.product.FormWord
import app.thelasthit.nrt.domain.product.ProductForm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class InsightRangeTest {

    private val today = LocalDate.parse("2026-10-23")

    @Test
    fun `7 days is always there and each longer range unlocks after more history than the one below`() {
        fun unlocked(historyDays: Int) = InsightRange.entries.filter { it.isUnlocked(historyDays) }
        assertEquals(listOf(DAYS_7), unlocked(0))
        assertEquals(listOf(DAYS_7), unlocked(7))
        assertEquals(listOf(DAYS_7, DAYS_30), unlocked(8))
        assertEquals(listOf(DAYS_7, DAYS_30), unlocked(30))
        assertEquals(listOf(DAYS_7, DAYS_30, DAYS_60), unlocked(31))
        assertEquals(InsightRange.entries, unlocked(61))
    }

    @Test
    fun `history counts both the first day and today`() {
        assertEquals(0, InsightRange.historyDays(today, null))
        assertEquals(1, InsightRange.historyDays(today, today))
        assertEquals(5, InsightRange.historyDays(today, LocalDate.parse("2026-10-19")))
    }

    @Test
    fun `a window never starts before the first logged day`() {
        assertNull(DAYS_7.window(today, null))
        assertEquals(InsightWindow(LocalDate.parse("2026-10-17"), today), DAYS_7.window(today, LocalDate.parse("2026-09-01")))
        val clipped = DAYS_30.window(today, LocalDate.parse("2026-10-20"))
        assertEquals(InsightWindow(LocalDate.parse("2026-10-20"), today), clipped)
        assertEquals(4, clipped?.days)
    }

    @Test
    fun `the previous period only counts once it's all logged`() {
        val window = DAYS_7.window(today, LocalDate.parse("2026-10-01"))!!
        assertEquals(InsightWindow(LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-16")), window.previous)
        assertTrue(window.hasFullPrevious(LocalDate.parse("2026-10-10")))
        assertFalse(window.hasFullPrevious(LocalDate.parse("2026-10-11")))
    }

    @Test
    fun `product types run most mg first, and a filter keeps only its type`() {
        val entries = listOf(
            entry("2026-10-20T07:30", form = ProductForm.PATCH_16H, perUnit = Dose.mg(15)),
            entry("2026-10-20T08:00", form = ProductForm.PATCH_24H, perUnit = Dose.mg(14)),
            entry("2026-10-20T09:00"),
        )
        assertEquals(listOf(ProductType(FormWord.PATCH), ProductType(FormWord.GUM)), ProductType.inUse(entries))
        assertEquals(2, entries.ofType(ProductType(FormWord.PATCH)).size)
        assertEquals(3, entries.ofType(null).size)
    }
}
