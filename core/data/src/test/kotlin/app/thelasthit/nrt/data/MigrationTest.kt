package app.thelasthit.nrt.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.thelasthit.nrt.data.db.AppDatabase
import app.thelasthit.nrt.domain.log.EntryKind
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.product.ProductForm
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every migration runs from version 1 on the exported schemas, and data survives it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    /**
     * The helper works through a driver on the database file. Its older form names the database
     * instead, which androidx.sqlite can't match to a Windows path, so it fails off CI.
     */
    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = context.getDatabasePath(name),
        driver = AndroidSQLiteDriver(),
        databaseClass = AppDatabase::class,
    )

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    private fun SQLiteConnection.count(table: String): Long {
        val statement = prepare("SELECT COUNT(*) FROM `$table`")
        try {
            statement.step()
            return statement.getLong(0)
        } finally {
            statement.close()
        }
    }

    private fun SQLiteConnection.closing(block: (SQLiteConnection) -> Unit) {
        try {
            block(this)
        } finally {
            close()
        }
    }

    /** A version 1 phone: a product, a tagged dose, a deleted dose and the tag. */
    private fun SQLiteConnection.fillVersion1() {
        execSQL(
            """
            INSERT INTO products (id, catalogueId, name, shortName, form, unit, perUnitMicrograms, cartridgeMicrograms,
                puffsPerCartridge, labelMaxPerDay, labelWindowMax, labelWindowMinutes, quickLogPosition, isHidden, createdAt)
            VALUES ('nicorette-gum-uk-2mg', 'nicorette-gum-uk-2mg', 'Nicorette gum 2 mg', 'Nicorette gum', 'GUM', 'PIECE', 2000,
                NULL, NULL, 15, NULL, NULL, 0, 0, 1761200000000)
            """.trimIndent(),
        )
        execSQL("INSERT INTO tags (`key`, `group`, customName, isBuiltIn, isHidden, sortOrder) VALUES ('coffee_tea', 'ROUTINE', NULL, 1, 0, 3)")
        for ((id, deletedAt) in listOf("e1" to "NULL", "e2" to "1761210000000")) {
            execSQL(
                """
                INSERT INTO entries (id, productId, productName, form, unit, perUnitMicrograms, quantity, totalMicrograms,
                    occurredAt, zoneId, logicalEpochDay, kind, cravingStrength, note, source, createdAt, updatedAt, deletedAt)
                VALUES ('$id', 'nicorette-gum-uk-2mg', 'Nicorette gum 2 mg', 'GUM', 'PIECE', 2000, 2, 4000,
                    1761206400000, 'Europe/London', 20384, 'CRAVING', 3, 'After lunch', 'APP', 1761206400000, 1761206400000, $deletedAt)
                """.trimIndent(),
            )
        }
        execSQL("INSERT INTO entry_tags (entryId, tagKey) VALUES ('e1', 'coffee_tea')")
    }

    @Test
    fun version1DataSurvivesAndTheNewTablesStartEmpty() {
        helper.createDatabase(1).closing { it.fillVersion1() }

        helper.runMigrationsAndValidate(2, AppDatabase.MIGRATIONS.toList()).closing { db ->
            assertEquals(1L, db.count("products"))
            assertEquals(2L, db.count("entries"))
            assertEquals(1L, db.count("entry_tags"))
            assertEquals(1L, db.count("tags"))
            val newTables = listOf(
                "programmes", "baseline_units", "ladder_units", "programme_weeks", "programme_week_units",
                "demons", "hit_ledger", "achievements", "gear", "breathing_sessions",
            )
            newTables.forEach { assertEquals("$it starts empty", 0L, db.count(it)) }
        }
    }

    @Test
    fun version2WeeksSurviveAndTheCapsStartEmpty() {
        helper.createDatabase(2).closing { db ->
            db.execSQL(
                """
                INSERT INTO programmes (id, status, createdAt, endedAt, baselineStartedOn, baselineStart, baselineEnd, baselineSource,
                    baselineMeasuredMicrograms, baselineDailyMicrograms, lengthWeeks, nrtFreeWeeks, startDate)
                VALUES ('p1', 'ACTIVE', 1761200000000, NULL, NULL, NULL, NULL, 'MANUAL', NULL, 32000, 12, 2, 20385)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO programme_weeks (programmeId, number, startDate, endDate, kind, ladderStep, dailyGuideMicrograms,
                    weeklyAllowanceMicrograms, isNrtFree, maxUnitsPerDay, minGapMinutes, firstDoseNotBeforeMinuteOfDay, result,
                    resultAt, choice, choiceAt)
                VALUES ('p1', 1, 20385, 20391, 'PLANNED', 2, 24000, 168000, 0, NULL, 90, 540, 'PENDING', NULL, NULL, NULL)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(3, AppDatabase.MIGRATIONS.toList()).closing { db ->
            assertEquals(1L, db.count("programme_weeks"))
            assertEquals(0L, db.count("programme_week_caps"))
            db.execSQL("INSERT INTO programme_week_caps (programmeId, weekNumber, productId, maxUnitsPerDay) VALUES ('p1', 1, 'gum', 5)")
            // Caps go with their week.
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("DELETE FROM programme_weeks")
            assertEquals(0L, db.count("programme_week_caps"))
        }
    }

    @Test
    fun theAppReadsAMigratedDatabase() {
        helper.createDatabase(1).closing { it.fillVersion1() }

        val database = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*AppDatabase.MIGRATIONS).build()
        helper.closeWhenFinished(database)
        runBlocking {
            val product = database.products().allIncludingHidden().single()
            assertEquals(ProductForm.GUM, product.form)
            assertEquals(2000L, product.perUnitMicrograms)
            val entry = database.entries().allLiveWithTags().single()
            assertEquals("e1", entry.entry.id)
            assertEquals(EntryKind.CRAVING, entry.entry.kind)
            assertEquals(listOf("coffee_tea"), entry.tags.map { it.tagKey })
            assertEquals(TagGroup.ROUTINE, database.tags().all().single().group)
            assertNull(database.programmes().current())
            assertNull(database.game().currentDemon())
            assertEquals(0, database.breathing().completedCount())
        }
    }
}
