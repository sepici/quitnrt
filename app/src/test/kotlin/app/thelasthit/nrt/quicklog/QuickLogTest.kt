package app.thelasthit.nrt.quicklog

import android.app.Notification
import android.os.Looper
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.EntrySource
import app.thelasthit.nrt.link.LogLinks
import app.thelasthit.nrt.reminders.Notice
import app.thelasthit.nrt.reminders.NoticeActionReceiver
import app.thelasthit.nrt.reminders.actionLabels
import app.thelasthit.nrt.reminders.allowNotifications
import app.thelasthit.nrt.reminders.awaitNotification
import app.thelasthit.nrt.reminders.notification
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.tile.LogTileService
import app.thelasthit.nrt.widget.DiscardWidgetLog
import app.thelasthit.nrt.widget.KeepBothWidgetLogs
import app.thelasthit.nrt.widget.LogFavourite
import app.thelasthit.nrt.widget.UndoWidgetLog
import app.thelasthit.nrt.widget.WidgetKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.time.LocalDate

/** The widget's taps and the Quick Settings tile, each through the same logging path as the app. */
@RunWith(RobolectricTestRunner::class)
class QuickLogTest {

    private val app: TestApp = ApplicationProvider.getApplicationContext()
    private val glanceId = object : GlanceId {}
    private val today = LocalDate.parse("2026-10-23")

    @Before
    fun setUp() {
        app.setUp()
        app.allowNotifications()
    }

    private fun entriesToday(): List<Entry> = runBlocking { app.graph.entries.observeDay(today).first().entries }

    private fun tap(callback: ActionCallback, vararg pairs: ActionParameters.Pair<out Any>) =
        runBlocking { callback.onAction(app, glanceId, actionParametersOf(*pairs)) }

    // The widget.

    @Test
    fun aFavouriteLogsAtOnceFromTheWidget() {
        tap(LogFavourite(), WidgetKeys.PRODUCT to GUM_2)
        val entry = entriesToday().single()
        assertEquals(GUM_2, entry.productId)
        assertEquals(EntrySource.WIDGET, entry.source)
        val moment = app.quickLog.moment.value as WidgetMoment.Logged
        assertEquals(entry.id, moment.entry.id)
        assertEquals("Nicorette gum", moment.name)
    }

    @Test
    fun undoOnTheWidgetTakesTheDoseBack() {
        tap(LogFavourite(), WidgetKeys.PRODUCT to GUM_2)
        val entry = entriesToday().single()
        tap(UndoWidgetLog(), WidgetKeys.ENTRY to entry.id)
        assertEquals(emptyList<Entry>(), entriesToday())
        assertNull(app.quickLog.moment.value)
    }

    @Test
    fun aTapThatLooksLikeADoseLoggedElsewhereAsksFirst() {
        runBlocking { app.graph.entries.log(LogRequest(GUM_2)) }
        tap(LogFavourite(), WidgetKeys.PRODUCT to GUM_2)
        assertEquals(1, entriesToday().size)
        assertTrue(app.quickLog.moment.value is WidgetMoment.Duplicate)

        tap(KeepBothWidgetLogs())
        assertEquals(listOf(EntrySource.APP, EntrySource.WIDGET), entriesToday().map { it.source }.sorted())
        assertTrue(app.quickLog.moment.value is WidgetMoment.Logged)
    }

    @Test
    fun discardDropsTheTap() {
        runBlocking { app.graph.entries.log(LogRequest(GUM_2)) }
        tap(LogFavourite(), WidgetKeys.PRODUCT to GUM_2)
        tap(DiscardWidgetLog())
        assertEquals(listOf(EntrySource.APP), entriesToday().map { it.source })
        assertNull(app.quickLog.moment.value)
    }

    @Test
    fun theLoggedMomentEndsOnItsOwn() {
        var redrawn = 0
        val quick = QuickLog(app, app.graph, CoroutineScope(SupervisorJob() + Dispatchers.Default), onChange = { redrawn++ }, hold = Duration.ofMillis(100))
        runBlocking { quick.logFromWidget(GUM_2) }
        assertTrue(quick.moment.value is WidgetMoment.Logged)
        val end = System.currentTimeMillis() + 5_000
        while (quick.moment.value != null && System.currentTimeMillis() < end) Thread.sleep(20)
        assertNull(quick.moment.value)
        assertEquals(2, redrawn)
    }

    // The tile.

    /** The tile, with the app it would open kept for the test to look at. */
    class RecordingTile : LogTileService() {
        var opened: Intent? = null

        override fun open(intent: Intent) {
            opened = intent
        }
    }

    private fun clickTile(): RecordingTile {
        val tile = Robolectric.setupService(RecordingTile::class.java)
        tile.onClick()
        return tile
    }

    private fun awaitOpened(tile: RecordingTile): Intent? {
        val end = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            tile.opened?.let { return it }
            Thread.sleep(20)
        }
        return null
    }

    @Test
    fun theTileLogsTheFirstQuickLogProductWithUndo() {
        clickTile()
        val notice = present(app.awaitNotification(Notice.ID_LOGGED))
        val entry = entriesToday().single()
        assertEquals(GUM_2, entry.productId)
        assertEquals(EntrySource.TILE, entry.source)
        assertEquals("Logged 2${Char(0xA0)}mg of Nicorette gum", notice.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(listOf("Undo"), notice.actionLabels)
        assertEquals(Duration.ofSeconds(10).toMillis(), notice.timeoutAfter)
    }

    @Test
    fun undoOnTheTilesNoteTakesTheDoseBack() {
        clickTile()
        app.awaitNotification(Notice.ID_LOGGED)
        val undo = shadowOf(app.notification(Notice.ID_LOGGED)!!.actions.single().actionIntent).savedIntent
        NoticeActionReceiver().onReceive(app, undo)
        val end = System.currentTimeMillis() + 5_000
        while (entriesToday().isNotEmpty() && System.currentTimeMillis() < end) Thread.sleep(20)
        assertEquals(emptyList<Entry>(), entriesToday())
        assertNull(app.notification(Notice.ID_LOGGED))
    }

    @Test
    fun theTileCanOpenTheLogSheetInstead() {
        runBlocking { app.graph.reminders.setTileOpensSheet(true) }
        val opened = present(awaitOpened(clickTile()))
        assertEquals(LogLinks.blank, opened.data)
        assertEquals(EntrySource.TILE, LogLinks.from(opened)?.source)
        assertEquals(emptyList<Entry>(), entriesToday())
    }

    @Test
    fun aTileTapThatLooksLikeADoseLoggedElsewhereOpensTheAppToAsk() {
        runBlocking { app.graph.entries.log(LogRequest(GUM_2, source = EntrySource.WIDGET)) }
        val opened = present(awaitOpened(clickTile()))
        assertEquals(LogLinks.forProduct(GUM_2), opened.data)
        assertEquals(EntrySource.TILE, LogLinks.from(opened)?.source)
        assertEquals(1, entriesToday().size)
    }

    @Test
    fun theTileNamesWhatItLogs() {
        val tile = Robolectric.setupService(RecordingTile::class.java)
        tile.onStartListening()
        val end = System.currentTimeMillis() + 5_000
        while (tile.qsTile.subtitle == null && System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertEquals("Log a dose", tile.qsTile.label)
        assertEquals("Gum 2${Char(0xA0)}mg", tile.qsTile.subtitle)
        assertEquals("Log Nicorette gum 2${Char(0xA0)}mg", tile.qsTile.contentDescription)
    }

    @Test
    fun everyWayInHasItsOwnSource() {
        fun sourceOf(name: String) = LogLinks.from(LogLinks.appIntent(app, LogLinks.blank, name))?.source
        assertEquals(EntrySource.TILE, sourceOf(LogLinks.SOURCE_TILE))
        assertEquals(EntrySource.WIDGET, sourceOf(LogLinks.SOURCE_WIDGET))
        assertEquals(EntrySource.NOTIFICATION, sourceOf(LogLinks.SOURCE_NOTIFICATION))
        assertEquals(EntrySource.SHORTCUT, sourceOf(LogLinks.SOURCE_SHORTCUT))
        assertEquals(EntrySource.DEEP_LINK, LogLinks.from(Intent(Intent.ACTION_VIEW, LogLinks.blank))?.source)
    }

    private fun <T> present(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
