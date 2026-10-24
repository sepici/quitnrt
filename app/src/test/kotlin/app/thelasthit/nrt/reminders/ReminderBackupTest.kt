package app.thelasthit.nrt.reminders

import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import app.thelasthit.nrt.R
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.data.settings.ReminderStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

/**
 * The reminder settings and their notes belong to this phone, so backups and transfers leave
 * them out. Restored on a new phone, "already asked" would skip the notification question there.
 */
@RunWith(RobolectricTestRunner::class)
class ReminderBackupTest {

    private val app: TestApp = ApplicationProvider.getApplicationContext()

    @Test
    fun theRulesNameTheStoresOwnFile() {
        assertEquals(ReminderStore.FILE, ReminderStore.fileIn(app).relativeTo(app.filesDir).invariantSeparatorsPath)
    }

    @Test
    fun autoBackupLeavesTheRemindersOut() {
        assertEquals(listOf("full-backup-content"), excludedIn(R.xml.backup_rules))
    }

    @Test
    fun cloudBackupAndDeviceTransferLeaveTheRemindersOut() {
        val sections = excludedIn(R.xml.data_extraction_rules)
        assertTrue("cloud-backup" in sections)
        assertTrue("device-transfer" in sections)
    }

    /** The sections of [rules] that exclude the reminders' file. */
    private fun excludedIn(rules: Int): List<String> {
        val parser: XmlResourceParser = app.resources.getXml(rules)
        val sections = mutableListOf<String>()
        var section = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "exclude" -> {
                    val domain = parser.getAttributeValue(null, "domain")
                    val path = parser.getAttributeValue(null, "path")
                    if (domain == "file" && path == ReminderStore.FILE) sections += section
                }
                else -> section = parser.name
            }
        }
        return sections
    }
}
