package tt.co.jesses.sooner.scan

import kotlin.math.hypot

/**
 * One word that OCR found in the photo, with its box in image pixels.
 * Kept free of ML Kit classes so the picking logic can be unit tested.
 */
data class OcrWord(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val height: Int get() = bottom - top
}

/**
 * What the picker found. [number] is its best guess, or null if the photo has
 * no 5-digit number. [candidates] holds every distinct 5-digit number it saw,
 * best first, so the confirm step can offer the others.
 */
data class StopNumberPick(
    val number: String?,
    val candidates: List<String>,
)

/**
 * Picks the bus stop number out of the words read from a photo of a stop sign.
 *
 * TransLink signs print the number as 5 digits next to or under a "BUS STOP #"
 * label. The same sign often carries other numbers, such as the 33333 text
 * number and phone numbers, so the picker:
 *
 * 1. collects every word that reads as exactly 5 digits (a leading "#" or "No."
 *    is allowed, and common OCR slips such as O for 0 are fixed);
 * 2. drops numbers the stop table doesn't know, when [isKnownStop] is given,
 *    unless that would leave nothing;
 * 3. if it finds a "STOP" or "#" label, takes the candidate nearest it,
 *    preferring ones to its right or below;
 * 4. otherwise takes a lone candidate, or the one printed largest.
 */
object StopNumberPicker {
    const val LENGTH = 5

    /** TransLink's text-for-next-bus number, printed on many signs. Never a stop. */
    private val NOT_STOPS = setOf("33333")

    fun pick(words: List<OcrWord>, isKnownStop: ((String) -> Boolean)? = null): StopNumberPick {
        val found = words.mapNotNull { word -> stopNumberIn(word.text)?.let { Candidate(it, word) } }
            .filter { it.number !in NOT_STOPS }
        if (found.isEmpty()) return StopNumberPick(null, emptyList())

        val known = if (isKnownStop == null) found else found.filter { isKnownStop(it.number) }
        val pool = known.ifEmpty { found }

        val labels = words.filter { isLabel(it.text) }
        val ranked = if (labels.isNotEmpty()) {
            pool.sortedBy { c -> labels.minOf { distanceFromLabel(it, c.word) } }
        } else {
            pool.sortedByDescending { it.word.height }
        }

        // Known stops first, then any others, without repeats.
        val order = (ranked + found.filter { it !in pool }.sortedByDescending { it.word.height })
            .map { it.number }
            .distinct()
        return StopNumberPick(order.first(), order)
    }

    /** The 5-digit number [text] reads as, or null. "#51238", "No.51238" and "5I238" all count. */
    internal fun stopNumberIn(text: String): String? {
        var t = text.trim().trimEnd('.', ',', ':', ';', ')')
        t = t.removePrefix("#").removePrefix("(")
        t = t.replaceFirst(Regex("^(?i)no\\.?"), "")
        if (t.length != LENGTH) return null
        // Only fix letters when the word is mostly digits already, so "STOPS" doesn't become a number.
        if (t.count { it.isDigit() } < LENGTH - 1) return null
        val fixed = buildString {
            for (ch in t) append(OCR_FIXES[ch] ?: ch)
        }
        return fixed.takeIf { s -> s.all { it in '0'..'9' } }
    }

    private val OCR_FIXES = mapOf(
        'O' to '0', 'o' to '0', 'D' to '0', 'Q' to '0',
        'I' to '1', 'l' to '1', 'i' to '1', '|' to '1',
        'S' to '5', 's' to '5',
        'B' to '8',
        'Z' to '2', 'z' to '2',
    )

    /** "STOP", "Stop#", "#" or "No." on their own: the label printed beside the number. */
    internal fun isLabel(text: String): Boolean {
        val t = text.trim().lowercase()
        if (t == "#" || t == "no." || t == "no") return true
        return t.trimEnd('#', ':', '.').let { it == "stop" || it.endsWith("stop") }
    }

    /**
     * Distance from the label to the candidate, in units of the label's height.
     * Numbers above or left of the label count as farther, because the sign
     * reads label first.
     */
    private fun distanceFromLabel(label: OcrWord, word: OcrWord): Float {
        val unit = label.height.coerceAtLeast(1).toFloat()
        val dx = (word.centerX - label.centerX) / unit
        val dy = (word.centerY - label.centerY) / unit
        val behind = (if (word.right < label.left) 3f else 1f) * (if (word.bottom < label.top) 3f else 1f)
        return hypot(dx, dy) * behind
    }

    private data class Candidate(val number: String, val word: OcrWord)
}
