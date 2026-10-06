package cz.petrkyncl.bazosiphony.data

import android.content.Context
import cz.petrkyncl.bazosiphony.domain.Grade
import cz.petrkyncl.bazosiphony.domain.IphoneKey
import cz.petrkyncl.bazosiphony.domain.parseIphone
import cz.petrkyncl.bazosiphony.domain.sameModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * Výkupní ceník iPhonů z mp.cz (neoficiální API průvodce výkupem).
 * Strom: meta-masters (modely) → attr-choices (kapacity) → color-choices (barvy) → qualities (ceny po stavech).
 * Celý Apple ceník ≈ 250 požadavků → stahuje se nejvýš jednou denně a drží v souboru v telefonu.
 */
class MpPricelist(context: Context) {
    private val file = File(context.filesDir, "mp-apple.json")
    private val mutex = Mutex()
    private var memory: Pricelist? = null

    suspend fun get(forceRefresh: Boolean = false): Pricelist = mutex.withLock {
        if (memory == null) memory = readFile()
        val current = memory
        val stale = current == null || System.currentTimeMillis() - current.updatedAtMillis > MAX_AGE_MS
        if (forceRefresh || stale) {
            runCatching { download() }
                .onSuccess { fresh ->
                    // pojistka proti částečnému výpadku API: výrazně menší ceník nepřepíše starý
                    if (current == null || fresh.items.size >= current.items.size / 2) {
                        memory = fresh
                        withContext(Dispatchers.IO) { file.writeText(Http.json.encodeToString(Pricelist.serializer(), fresh)) }
                    }
                }
                .onFailure { if (current == null) throw it } // starý ceník je lepší než žádný
        }
        memory!!
    }

    private suspend fun readFile(): Pricelist? = withContext(Dispatchers.IO) {
        runCatching { Http.json.decodeFromString(Pricelist.serializer(), file.readText()) }.getOrNull()
    }

    private suspend fun mp(path: String) = Http.get("$API/$path", userAgent = "Mozilla/5.0 (compatible; price-check)").first

    private suspend fun download(): Pricelist = coroutineScope {
        val limit = Semaphore(8)
        val models = ((mp("meta-masters/telefony/apple/") as JsonObject)["meta_masters"] as JsonArray).map { it as JsonObject }

        data class Variant(val model: JsonObject, val name: String, val seo: String)
        val variants = models.map { m ->
            async {
                limit.withPermit {
                    runCatching {
                        ((mp("attr-choices/${m.str("seo_name")}/") as JsonObject)["attr_values"] as? JsonArray).orEmpty()
                            .mapNotNull { (it as JsonObject).obj("master_product") }
                            .filter { it.str("seo_name") != null }
                            .map { Variant(m, it.str("name") ?: m.str("name").orEmpty(), it.str("seo_name")!!) }
                    }.getOrDefault(emptyList())
                }
            }
        }.awaitAll().flatten()

        val items = variants.map { v ->
            async {
                limit.withPermit {
                    runCatching {
                        val colors = (mp("color-choices/${v.seo}/") as JsonArray).map { it as JsonObject }
                        val color = colors.firstOrNull() ?: return@runCatching null
                        // ceny jsou pro všechny barvy stejné → stačí první
                        val qualities = (mp("qualities/${color.str("seo_name")}/") as JsonArray).map { it as JsonObject }
                        val buy = mutableMapOf<String, Int>()
                        val sell = mutableMapOf<String, Int>()
                        for (q in qualities) {
                            val grade = q.obj("quality")?.str("short_name")?.let(Grade::of) ?: continue
                            val b = q.str("buying_price")?.toDoubleOrNull()?.toInt() ?: 0
                            val s = q.str("price")?.toDoubleOrNull()?.toInt() ?: 0
                            if (b > NO_BUYOUT) buy[grade.code] = b
                            if (s > 0 && grade != Grade.D) sell[grade.code] = s // prodej D je nespolehlivý
                        }
                        if (buy.isEmpty()) null
                        else PriceVariant(
                            model = v.model.str("name").orEmpty(),
                            variant = v.name,
                            storageGb = parseIphone(v.name)?.storageGb,
                            buy = buy,
                            sell = sell,
                            image = abs(color.str("image") ?: v.model.str("image")),
                            url = abs(color.str("seo_name")),
                        )
                    }.getOrNull()
                }
            }
        }.awaitAll().filterNotNull()

        if (items.isEmpty()) error("mp.cz nevrátilo žádné ceny")
        Pricelist(System.currentTimeMillis(), items)
    }

    companion object {
        private const val MP = "https://www.mp.cz"
        private const val API = "$MP/api/new/buyout/product-selector"
        private const val MAX_AGE_MS = 24 * 3600_000L
        private const val NO_BUYOUT = 10 // výkup 10 Kč = fakticky nevykupují

        private fun abs(path: String?) = when {
            path == null -> null
            path.startsWith("http") -> path
            else -> MP + (if (path.startsWith("/")) "" else "/") + path
        }

        private val keyCache = HashMap<String, IphoneKey?>()

        private fun priceA(i: PriceVariant) = i.buy["A"] ?: i.buy["A+"] ?: i.buy["B"] ?: Int.MAX_VALUE

        /** Najde variantu ceníku: shoda modelu, pak kapacita (přesná → nejbližší → nejlevnější). Vrací (varianta, kapacita odhadnuta). */
        fun match(key: IphoneKey, list: Pricelist): Pair<PriceVariant, Boolean>? {
            val candidates = list.items.filter { item ->
                val k = synchronized(keyCache) { keyCache.getOrPut(item.variant) { parseIphone(item.variant) } }
                k != null && sameModel(k, key)
            }
            if (candidates.isEmpty()) return null
            val storage = key.storageGb ?: return candidates.minBy(::priceA) to true
            candidates.filter { it.storageGb == storage }.minByOrNull(::priceA)?.let { return it to false }
            return candidates.minWith(compareBy<PriceVariant> { kotlin.math.abs((it.storageGb ?: 0) - storage) }.thenBy(::priceA)) to true
        }
    }
}
