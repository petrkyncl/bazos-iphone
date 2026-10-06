package cz.petrkyncl.bazosiphony.domain

/**
 * Tržní hodnota iPhonu z aktuálních inzerátů na Bazoši: stejný model a kapacita, bez šumu
 * (kryty, poptávky, prodáno, rozbité kusy, repliky) a bez extrémů. Medián místo průměru —
 * pár inzerátů za 1 Kč nebo přepálených cen ho skoro neovlivní.
 */
data class MarketStats(
    val count: Int, // kolik inzerátů po vyčištění
    val min: Int,
    val p25: Int, // „běžně" = prostřední polovina cen
    val median: Int,
    val p75: Int,
    val cheapestId: String,
    val cheapestTitle: String,
)

data class MarketItem(val id: String, val title: String, val price: Int?)

const val MIN_MARKET_SAMPLE = 3

fun marketStats(target: IphoneKey, items: List<MarketItem>, excludeId: String? = null): MarketStats? {
    val valid = items.filter { a ->
        val price = a.price ?: return@filter false
        if (a.id == excludeId || price < 500) return@filter false
        val k = parseIphone(a.title) ?: return@filter false
        sameModel(k, target) &&
            (target.storageGb == null || k.storageGb == target.storageGb) &&
            listingIssue(a.title, "") == null &&
            !isReplica(a.title) &&
            estimateCondition(a.title, "").grade != Grade.D // rozbité / na díly v nadpisu
    }.distinctBy { it.id }
    if (valid.size < MIN_MARKET_SAMPLE) return null

    // extrémy pryč: mimo 40–250 % hrubého mediánu (chybné ceny, „1 Kč", jiný model v nadpisu)
    val rough = percentile(valid.map { it.price!! }.sorted(), 0.5)
    val clean = valid.filter { it.price!! in (0.4 * rough).toInt()..(2.5 * rough).toInt() }.sortedBy { it.price }
    if (clean.size < MIN_MARKET_SAMPLE) return null
    val prices = clean.map { it.price!! }
    return MarketStats(
        count = clean.size,
        min = prices.first(),
        p25 = percentile(prices, 0.25),
        median = percentile(prices, 0.5),
        p75 = percentile(prices, 0.75),
        cheapestId = clean.first().id,
        cheapestTitle = clean.first().title,
    )
}

/** Percentil s lineární interpolací (seřazený vstup). */
private fun percentile(sorted: List<Int>, p: Double): Int {
    if (sorted.size == 1) return sorted[0]
    val pos = p * (sorted.size - 1)
    val lo = pos.toInt()
    val hi = minOf(lo + 1, sorted.lastIndex)
    return (sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo)).toInt()
}

/** Dotaz pro hledání stejného modelu na Bazoši, např. „iphone 15 pro 128". */
fun marketQuery(k: IphoneKey): String {
    val model = modelLabel(k).lowercase().replace("(", "").replace(")", "")
    val storage = k.storageGb?.let { if (it >= 1024) "${it / 1024}tb" else "$it" }
    return listOfNotNull(model, storage).joinToString(" ")
}
