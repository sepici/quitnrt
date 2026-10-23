package app.thelasthit.nrt.ui.format

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.BuiltInTags
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatsTest {

    @Test
    fun `every built-in tag has text`() {
        assertEquals(BuiltInTags.all.map { it.key }.toSet(), builtInTagKeysWithText)
    }

    @Test
    fun `doses show to one decimal place at most`() {
        assertEquals("8", Dose.mg(8).mgText())
        assertEquals("0", Dose.ZERO.mgText())
        assertEquals("1.5", Dose.mg(1.5).mgText())
        assertEquals("0.5", Dose.mg(0.5).mgText())
        // An inhalator session is 1.875 mg on the label.
        assertEquals("1.9", Dose(1875).mgText())
        assertEquals("3.8", Dose(3750).mgText())
        assertEquals("15", Dose(14_960).mgText())
    }
}
