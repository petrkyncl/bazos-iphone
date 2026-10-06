package cz.petrkyncl.bazosiphony.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val CZ = Locale.forLanguageTag("cs-CZ")
private val NUMBER = NumberFormat.getIntegerInstance(CZ)
private val DATE = DateTimeFormatter.ofPattern("d. M. yyyy")

fun czk(n: Int?): String = if (n == null) "—" else "${NUMBER.format(n)} Kč"

fun number(n: Int): String = NUMBER.format(n)

fun profitText(n: Int): String = (if (n > 0) "+" else if (n < 0) "−" else "") + czk(abs(n))

fun storageText(gb: Int): String = if (gb >= 1024) "${gb / 1024} TB" else "$gb GB"

fun dateText(millis: Long?): String =
    millis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DATE) }.orEmpty()

fun ago(millis: Long?): String {
    if (millis == null) return ""
    val mins = (System.currentTimeMillis() - millis) / 60_000
    return when {
        mins < 1 -> "právě teď"
        mins < 60 -> "před $mins min"
        mins < 24 * 60 -> "před ${mins / 60} h"
        mins < 7 * 24 * 60 -> (mins / 1440).let { "před $it ${if (it == 1L) "dnem" else "dny"}" }
        else -> dateText(millis)
    }
}
