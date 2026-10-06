package cz.petrkyncl.bazosiphony.ui

import java.text.NumberFormat
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val CZ = Locale.forLanguageTag("cs-CZ")
private val NUMBER = NumberFormat.getIntegerInstance(CZ)

fun czk(n: Int?): String = if (n == null) "—" else "${NUMBER.format(n)} Kč"

fun profitText(n: Int): String = (if (n > 0) "+" else if (n < 0) "−" else "") + czk(abs(n))

fun storageText(gb: Int): String = if (gb >= 1024) "${gb / 1024} TB" else "$gb GB"

fun ago(date: ZonedDateTime?): String {
    if (date == null) return ""
    val mins = Duration.between(date, ZonedDateTime.now()).toMinutes()
    return when {
        mins < 1 -> "právě teď"
        mins < 60 -> "před $mins min"
        mins < 24 * 60 -> "před ${mins / 60} h"
        mins < 7 * 24 * 60 -> (mins / 1440).let { "před $it ${if (it == 1L) "dnem" else "dny"}" }
        else -> date.format(DateTimeFormatter.ofPattern("d. M. yyyy"))
    }
}

fun agoMillis(millis: Long): String =
    ago(ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault()))
