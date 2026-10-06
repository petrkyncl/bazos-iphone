package cz.petrkyncl.bazosiphony.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import cz.petrkyncl.bazosiphony.data.Money
import kotlin.math.abs
import kotlin.math.roundToInt

private val CZ = Locale.forLanguageTag("cs-CZ")
private val NUMBER = NumberFormat.getIntegerInstance(CZ)
private val DATE = DateTimeFormatter.ofPattern("d. M. yyyy")

private val DECIMAL = NumberFormat.getNumberInstance(CZ).apply { maximumFractionDigits = 2; minimumFractionDigits = 2 }
private val SYMBOLS = mapOf("EUR" to "€", "USD" to "$", "GBP" to "£", "PLN" to "zł", "JPY" to "¥")

/** Cena v Kč, nebo přepočtená do zvolené měny kurzem ČNB (s „≈"). */
fun czk(n: Int?): String = if (n == null) "—" else money(n, approx = true)

fun number(n: Int): String = NUMBER.format(n)

fun profitText(n: Int): String = (if (n > 0) "+" else if (n < 0) "−" else "") + money(abs(n), approx = false)

private fun money(czk: Int, approx: Boolean): String {
    val perUnit = Money.czkPerUnit ?: return "${NUMBER.format(czk)} Kč" // čte Compose state → změna měny překreslí
    val v = czk / perUnit
    val amount = if (v < 10) DECIMAL.format(v) else NUMBER.format(v.roundToInt())
    return (if (approx) "≈ " else "") + "$amount ${SYMBOLS[Money.selected] ?: Money.selected}"
}

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
