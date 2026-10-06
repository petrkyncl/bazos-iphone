package cz.petrkyncl.bazosiphony.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Zobrazovaná měna. Ceny na Bazoši jsou v Kč; přepočet kurzem ČNB (denní kurzovní lístek,
 * zdarma a bez klíče). Stav je Compose state → změna měny hned překreslí všechny ceny.
 */
object Money {
    data class Rate(val code: String, val name: String, val czkPerUnit: Double)

    var selected by mutableStateOf("CZK")
        private set
    var rates by mutableStateOf<Map<String, Rate>>(emptyMap())
        private set
    var ratesDate by mutableStateOf<String?>(null) // „05.10.2026"
        private set

    /** Kč za 1 jednotku zvolené měny; null = zobrazovat v Kč. */
    val czkPerUnit: Double? get() = if (selected == "CZK") null else rates[selected]?.czkPerUnit

    /** Nejpoužívanější měny nahoře, ostatní podle kódu. */
    val choices: List<Rate>
        get() = listOf(Rate("CZK", "koruna", 1.0)) +
            COMMON.mapNotNull { rates[it] } +
            rates.values.filter { it.code !in COMMON }.sortedBy { it.code }

    private val COMMON = listOf("EUR", "USD", "GBP", "PLN", "CHF")
    private const val URL = "https://www.cnb.cz/cs/financni-trhy/devizovy-trh/kurzy-devizoveho-trhu/kurzy-devizoveho-trhu/denni_kurz.txt"
    private const val MAX_AGE_MS = 6 * 3600_000L

    fun select(context: Context, code: String) {
        selected = code
        prefs(context).edit { putString("currency", code) }
    }

    /** Načíst uloženou volbu a kurzy (z cache, případně stáhnout čerstvé). */
    suspend fun init(context: Context) {
        val p = prefs(context)
        selected = p.getString("currency", "CZK") ?: "CZK"
        p.getString("rates", null)?.let { parse(it) }
        if (rates.isEmpty() || System.currentTimeMillis() - p.getLong("rates_at", 0) > MAX_AGE_MS) {
            runCatching { download() }.onSuccess { text ->
                parse(text)
                p.edit { putString("rates", text); putLong("rates_at", System.currentTimeMillis()) }
            }
        }
    }

    private suspend fun download(): String = withContext(Dispatchers.IO) {
        Http.client.newCall(Request.Builder().url(URL).build()).execute().use { it.body.string() }
    }

    /** Formát ČNB: 1. řádek datum, 2. hlavička, pak „země|měna|množství|kód|kurz" (desetinná čárka). */
    private fun parse(text: String) {
        val lines = text.lines().filter { it.isNotBlank() }
        ratesDate = lines.firstOrNull()?.substringBefore(" ")
        rates = lines.drop(2).mapNotNull { line ->
            val p = line.split("|")
            if (p.size < 5) return@mapNotNull null
            val amount = p[2].toDoubleOrNull() ?: return@mapNotNull null
            val rate = p[4].replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
            Rate(p[3], p[1], rate / amount)
        }.associateBy { it.code }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("currency", Context.MODE_PRIVATE)
}
