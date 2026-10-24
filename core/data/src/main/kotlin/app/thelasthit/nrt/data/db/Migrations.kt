package app.thelasthit.nrt.data.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.execSQL

/** The database's migrations. Each one's SQL is the exported schema's, and MigrationTest checks it. */
object Migrations {

    /**
     * Version 2: the programme, the game and the breathing timer, all new tables that start
     * empty. Nothing in version 1 changes.
     */
    val FROM_1_TO_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            FROM_1_TO_2_SQL.forEach(db::execSQL)
        }

        override fun migrate(connection: SQLiteConnection) {
            FROM_1_TO_2_SQL.forEach(connection::execSQL)
        }
    }

    /**
     * Version 3: the cap on units a day became one for each product (PLAN §6.3), in a table of
     * its own that starts empty. The weeks' old single cap was never set, and stays null.
     */
    val FROM_2_TO_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            FROM_2_TO_3_SQL.forEach(db::execSQL)
        }

        override fun migrate(connection: SQLiteConnection) {
            FROM_2_TO_3_SQL.forEach(connection::execSQL)
        }
    }

    /**
     * Version 4: each week keeps the total its result was judged on, written once when the result
     * comes in. Results already in have no total kept, so the column starts null for every week.
     */
    val FROM_3_TO_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            FROM_3_TO_4_SQL.forEach(db::execSQL)
        }

        override fun migrate(connection: SQLiteConnection) {
            FROM_3_TO_4_SQL.forEach(connection::execSQL)
        }
    }

    internal val FROM_3_TO_4_SQL = listOf(
        "ALTER TABLE `programme_weeks` ADD COLUMN `resultTotalMicrograms` INTEGER",
    )

    internal val FROM_2_TO_3_SQL = listOf(
        "CREATE TABLE IF NOT EXISTS `programme_week_caps` (`programmeId` TEXT NOT NULL, `weekNumber` INTEGER NOT NULL, `productId` TEXT NOT NULL, `maxUnitsPerDay` INTEGER NOT NULL, PRIMARY KEY(`programmeId`, `weekNumber`, `productId`), FOREIGN KEY(`programmeId`, `weekNumber`) REFERENCES `programme_weeks`(`programmeId`, `number`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    )

    internal val FROM_1_TO_2_SQL = listOf(
        "CREATE TABLE IF NOT EXISTS `programmes` (`id` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `endedAt` INTEGER, `baselineStartedOn` INTEGER, `baselineStart` INTEGER, `baselineEnd` INTEGER, `baselineSource` TEXT, `baselineMeasuredMicrograms` INTEGER, `baselineDailyMicrograms` INTEGER, `lengthWeeks` INTEGER, `nrtFreeWeeks` INTEGER, `startDate` INTEGER, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_programmes_endedAt` ON `programmes` (`endedAt`)",
        "CREATE TABLE IF NOT EXISTS `baseline_units` (`programmeId` TEXT NOT NULL, `productId` TEXT NOT NULL, `productName` TEXT NOT NULL, `form` TEXT NOT NULL, `unit` TEXT NOT NULL, `perUnitMicrograms` INTEGER NOT NULL, `unitsPerDay` INTEGER NOT NULL, PRIMARY KEY(`programmeId`, `productId`), FOREIGN KEY(`programmeId`) REFERENCES `programmes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE TABLE IF NOT EXISTS `ladder_units` (`programmeId` TEXT NOT NULL, `step` INTEGER NOT NULL, `productId` TEXT NOT NULL, `productName` TEXT NOT NULL, `form` TEXT NOT NULL, `unit` TEXT NOT NULL, `perUnitMicrograms` INTEGER NOT NULL, `unitsPerDay` INTEGER NOT NULL, PRIMARY KEY(`programmeId`, `step`, `productId`), FOREIGN KEY(`programmeId`) REFERENCES `programmes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE TABLE IF NOT EXISTS `programme_weeks` (`programmeId` TEXT NOT NULL, `number` INTEGER NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER NOT NULL, `kind` TEXT NOT NULL, `ladderStep` INTEGER, `dailyGuideMicrograms` INTEGER NOT NULL, `weeklyAllowanceMicrograms` INTEGER NOT NULL, `isNrtFree` INTEGER NOT NULL, `maxUnitsPerDay` INTEGER, `minGapMinutes` INTEGER, `firstDoseNotBeforeMinuteOfDay` INTEGER, `result` TEXT NOT NULL, `resultAt` INTEGER, `choice` TEXT, `choiceAt` INTEGER, PRIMARY KEY(`programmeId`, `number`), FOREIGN KEY(`programmeId`) REFERENCES `programmes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE TABLE IF NOT EXISTS `programme_week_units` (`programmeId` TEXT NOT NULL, `weekNumber` INTEGER NOT NULL, `productId` TEXT NOT NULL, `productName` TEXT NOT NULL, `form` TEXT NOT NULL, `unit` TEXT NOT NULL, `perUnitMicrograms` INTEGER NOT NULL, `unitsPerDay` INTEGER NOT NULL, PRIMARY KEY(`programmeId`, `weekNumber`, `productId`), FOREIGN KEY(`programmeId`, `weekNumber`) REFERENCES `programme_weeks`(`programmeId`, `number`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE TABLE IF NOT EXISTS `demons` (`id` TEXT NOT NULL, `programmeId` TEXT NOT NULL, `name` TEXT, `creature` TEXT NOT NULL, `variant` TEXT, `maxHp` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `defeatedAt` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`programmeId`) REFERENCES `programmes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_demons_programmeId` ON `demons` (`programmeId`)",
        "CREATE TABLE IF NOT EXISTS `hit_ledger` (`id` TEXT NOT NULL, `programmeId` TEXT NOT NULL, `kind` TEXT NOT NULL, `hits` INTEGER NOT NULL, `week` INTEGER, `earnedBy` INTEGER, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`programmeId`) REFERENCES `programmes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_hit_ledger_programmeId_week` ON `hit_ledger` (`programmeId`, `week`)",
        "CREATE TABLE IF NOT EXISTS `achievements` (`key` TEXT NOT NULL, `unlockedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))",
        "CREATE TABLE IF NOT EXISTS `gear` (`key` TEXT NOT NULL, `unlockedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))",
        "CREATE TABLE IF NOT EXISTS `breathing_sessions` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `durationSeconds` INTEGER NOT NULL, `roundsCompleted` INTEGER NOT NULL, `ranToEnd` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    )
}
