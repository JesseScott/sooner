package tt.co.jesses.sooner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StopNumberPickerTest {
    /** A word at (x, y) with the given text height; width is rough, from the text length. */
    private fun w(text: String, x: Int, y: Int, h: Int = 20) =
        OcrWord(text, x, y, x + text.length * h / 2, y + h)

    @Test
    fun picksNumberRightOfLabelOverOtherNumbers() {
        val words = listOf(
            w("BUS", 0, 0), w("STOP", 50, 0), w("#", 110, 0), w("51238", 130, 0),
            w("Text", 0, 200), w("33333", 60, 200), w("and", 130, 200),
            w("12345", 0, 400, h = 40),
        )
        val pick = StopNumberPicker.pick(words)
        assertEquals("51238", pick.number)
        assertEquals(listOf("51238", "12345"), pick.candidates)
    }

    @Test
    fun picksNumberUnderLabel() {
        val words = listOf(
            w("BUS", 0, 0), w("STOP", 60, 0),
            w("60980", 0, 30, h = 50),
            w("54321", 400, 600),
        )
        assertEquals("60980", StopNumberPicker.pick(words).number)
    }

    @Test
    fun prefersNumberAfterLabelOverOneBeforeIt() {
        // 11111 sits just above-left; 22222 is a bit farther but to the right.
        val words = listOf(
            w("11111", 0, 0), w("STOP", 100, 40), w("22222", 200, 40),
        )
        assertEquals("22222", StopNumberPicker.pick(words).number)
    }

    @Test
    fun hashJoinedToNumberCounts() {
        val words = listOf(w("Stop", 0, 0), w("#51238", 60, 0))
        assertEquals("51238", StopNumberPicker.pick(words).number)
    }

    @Test
    fun fallsBackToLoneNumberWithoutLabel() {
        val words = listOf(w("Commercial", 0, 0), w("Dr", 120, 0), w("51238", 0, 40), w("604-953-3333", 0, 80))
        assertEquals("51238", StopNumberPicker.pick(words).number)
    }

    @Test
    fun withoutLabelPicksLargestNumber() {
        val words = listOf(w("50001", 0, 0, h = 15), w("51238", 0, 40, h = 60), w("50002", 0, 120, h = 15))
        val pick = StopNumberPicker.pick(words)
        assertEquals("51238", pick.number)
        assertEquals(3, pick.candidates.size)
    }

    @Test
    fun knownStopsBeatUnknownOnes() {
        val words = listOf(w("STOP", 0, 0), w("99999", 60, 0), w("51238", 0, 200))
        val pick = StopNumberPicker.pick(words) { it == "51238" }
        assertEquals("51238", pick.number)
        assertEquals(listOf("51238", "99999"), pick.candidates)
    }

    @Test
    fun keepsUnknownNumbersWhenNoneAreKnown() {
        val words = listOf(w("STOP", 0, 0), w("99999", 60, 0))
        assertEquals("99999", StopNumberPicker.pick(words) { false }.number)
    }

    @Test
    fun ignoresTextForNextBusNumber() {
        val words = listOf(w("Text", 0, 0), w("33333", 60, 0))
        val pick = StopNumberPicker.pick(words)
        assertNull(pick.number)
        assertTrue(pick.candidates.isEmpty())
    }

    @Test
    fun nothingFoundWithoutFiveDigits() {
        val words = listOf(w("BUS", 0, 0), w("STOP", 50, 0), w("1234", 0, 30), w("123456", 0, 60))
        assertNull(StopNumberPicker.pick(words).number)
        assertNull(StopNumberPicker.pick(emptyList()).number)
    }

    @Test
    fun readsFiveDigitWordsAndFixesOcrSlips() {
        assertEquals("51238", StopNumberPicker.stopNumberIn("51238"))
        assertEquals("51238", StopNumberPicker.stopNumberIn("#51238"))
        assertEquals("51238", StopNumberPicker.stopNumberIn("No.51238"))
        assertEquals("51238", StopNumberPicker.stopNumberIn("5I238"))
        assertEquals("60980", StopNumberPicker.stopNumberIn("6098O"))
        assertEquals("51238", StopNumberPicker.stopNumberIn("51238."))
        assertNull(StopNumberPicker.stopNumberIn("STOPS"))
        assertNull(StopNumberPicker.stopNumberIn("5IZ38")) // too many letters to trust
        assertNull(StopNumberPicker.stopNumberIn("1234"))
        assertNull(StopNumberPicker.stopNumberIn("123456"))
        assertNull(StopNumberPicker.stopNumberIn("604-953-3333"))
    }

    @Test
    fun recognisesLabels() {
        assertTrue(StopNumberPicker.isLabel("STOP"))
        assertTrue(StopNumberPicker.isLabel("Stop#"))
        assertTrue(StopNumberPicker.isLabel("#"))
        assertTrue(StopNumberPicker.isLabel("BUSSTOP"))
        assertFalse(StopNumberPicker.isLabel("STOPS"))
        assertFalse(StopNumberPicker.isLabel("Bus"))
    }
}
