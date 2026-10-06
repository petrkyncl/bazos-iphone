package cz.petrkyncl.bazosiphony.data

import android.content.Context
import cz.petrkyncl.bazosiphony.domain.Grade
import cz.petrkyncl.bazosiphony.domain.cityById
import cz.petrkyncl.bazosiphony.domain.IphoneKey
import cz.petrkyncl.bazosiphony.domain.MarketItem
import cz.petrkyncl.bazosiphony.domain.MarketStats
import cz.petrkyncl.bazosiphony.domain.marketQuery
import cz.petrkyncl.bazosiphony.domain.marketStats
import cz.petrkyncl.bazosiphony.domain.estimateCondition
import cz.petrkyncl.bazosiphony.domain.extractColor
import cz.petrkyncl.bazosiphony.domain.extractWarranty
import cz.petrkyncl.bazosiphony.domain.listingIssue
import cz.petrkyncl.bazosiphony.domain.modelLabel
import cz.petrkyncl.bazosiphony.domain.parseIphone
import cz.petrkyncl.bazosiphony.domain.priceIssue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.builtins.ListSerializer
import androidx.core.content.edit
import java.util.concurrent.ConcurrentHashMap

/** Jediný přístupový bod k datům (Bazoš + ceník mp.cz) s cache v paměti. */
class Repository private constructor(context: Context) {
    private val pricelist = MpPricelist(context)
    private val detailCache = ConcurrentHashMap<String, Pair<Long, JsonObject>>()
    private val sellerCache = ConcurrentHashMap<String, Pair<Long, SellerProfile>>()
    private val categoryCache = ConcurrentHashMap<String, List<Category>>()
    private var sectionsCache: List<Section>? = null
    private val detailLimit = Semaphore(DETAIL_CONCURRENCY)

    // Rubriky a kategorie se mění zřídka → v paměti i na disku (týden), domovská obrazovka je pak hned.
    private val catalogPrefs = context.applicationContext.getSharedPreferences("catalog", Context.MODE_PRIVATE)

    private fun catalogFresh() = System.currentTimeMillis() - catalogPrefs.getLong("at", 0) < CATALOG_MAX_AGE_MS

    suspend fun sections(): List<Section> {
        sectionsCache?.let { return it }
        if (catalogFresh()) {
            catalogPrefs.getString("sections", null)?.let { json ->
                runCatching { Http.json.decodeFromString(ListSerializer(Section.serializer()), json) }.getOrNull()?.let { sectionsCache = it; return it }
            }
        }
        return BazosApi.sections().also {
            sectionsCache = it
            catalogPrefs.edit { putString("sections", Http.json.encodeToString(ListSerializer(Section.serializer()), it)); putLong("at", System.currentTimeMillis()) }
        }
    }

    suspend fun categories(sectionId: String): List<Category> {
        categoryCache[sectionId]?.let { return it }
        if (catalogFresh()) {
            catalogPrefs.getString("cat_$sectionId", null)?.let { json ->
                runCatching { Http.json.decodeFromString(ListSerializer(Category.serializer()), json) }.getOrNull()?.let { categoryCache[sectionId] = it; return it }
            }
        }
        return BazosApi.categories(sectionId).also {
            categoryCache[sectionId] = it
            catalogPrefs.edit { putString("cat_$sectionId", Http.json.encodeToString(ListSerializer(Category.serializer()), it)) }
        }
    }

    /** Kategorie všech rubrik (rychlé odkazy na domovské obrazovce); spočítané podle popularity, jinak pořadí Bazoše. */
    suspend fun allCategories(sections: List<Section>): Map<String, List<Category>> = coroutineScope {
        val limit = Semaphore(4)
        sections.map { s ->
            async { s.id to (cachedPopularity(s.id) ?: limit.withPermit { runCatching { categories(s.id) }.getOrDefault(emptyList()) }) }
        }.awaitAll().toMap()
    }

    /** Stránka výsledků; klíč offsetů/počtů = ID kategorie („" bez kategorie). */
    data class MultiPage(
        val items: List<Listing>,
        val totals: Map<String, Int>,
        val offsets: Map<String, Int>,
        // jen pro řazení bez TOPu: stažené, ale ještě nezařazené inzeráty a hranice (nejnižší ID netopovaného) po kategoriích
        val buffer: List<Listing> = emptyList(),
        val frontiers: Map<String, Long> = emptyMap(),
        val topFallback: Boolean = false, // topovaných je moc (celý Bazoš) → pořadí Bazoše
    ) {
        val total get() = totals.values.sum()
        val hasMore get() = buffer.isNotEmpty() || totals.any { (k, t) -> (offsets[k] ?: 0) < t }
    }

    /**
     * Hledání i ve více kategoriích najednou: API umí jen jednu, takže každou stáhneme zvlášť
     * (paralelně) a výsledky spojíme podle zvoleného řazení. Další stránka pokračuje u každé
     * kategorie od jejího offsetu.
     */
    /**
     * Hledání; s filtrem města se hledá v okruhu, který město pokryje, a výsledky se zúží podle
     * lokality. Když jich na stránce zbude málo, stáhne se rovnou další (max. 4×).
     */
    suspend fun search(filters: Filters, prev: MultiPage? = null): MultiPage {
        val city = cityById(filters.city)
        val models = filters.models.toSet()
        val noiseFilter = filters.hideNoise && (filters.section?.id == BazosApi.SECTION_MOBILES || models.isNotEmpty())
        if (city == null && models.isEmpty() && !noiseFilter) return searchSorted(filters, prev)
        val prices = if (noiseFilter) runCatching { pricelist.get() }.getOrNull() else null
        val f = if (city != null) filters.copy(zip = city.zip, distanceKm = city.radiusKm) else filters
        // dodatečný filtr: lokalita města a přesný model/kapacita z nadpisu (15 Pro ≠ 15 Pro Max)
        fun keep(l: Listing): Boolean {
            if (city != null && !city.matches(l.locality)) return false
            if (noiseFilter && noise(l, prices) != null) return false
            if (models.isEmpty()) return true
            val k = parseIphone(l.title)?.takeIf { it.number != null } ?: return false
            return modelLabel(k) in models && (filters.storages.isEmpty() || k.storageGb in filters.storages) &&
                listingIssue(l.title, "") == null
        }
        var page = searchSorted(f, prev)
        val items = page.items.filter(::keep).toMutableList()
        var rounds = 0
        while (items.size < PAGE_SIZE / 2 && page.hasMore && rounds++ < 4) {
            page = searchSorted(f, page)
            items += page.items.filter(::keep)
        }
        return page.copy(items = items)
    }

    /**
     * Šum ve výpisu mobilů jen z nadpisu a ceny: příslušenství, jiná zařízení (AirPods, Watch, iPad…),
     * poptávky, prodané/rezervované a „iPhone 15 Pro kryt" za 300 Kč (cena hluboko pod výkupem).
     */
    fun noise(l: Listing, prices: Pricelist?): cz.petrkyncl.bazosiphony.domain.Issue? {
        listingIssue(l.title, "")?.let { return it }
        val key = parseIphone(l.title)?.takeIf { it.number != null } ?: return null
        val match = prices?.let { MpPricelist.match(key, it) } ?: return null
        val working = match.first.buy.filterKeys { it != Grade.D.code }.values.minOrNull()
        return priceIssue(l.title, l.price, working)?.takeIf { it.excludes } // podezřelá cena jen varuje, nevyřazuje
    }

    /**
     * Dílčí hledání, ze kterých se výsledky spojují: každá vybraná kategorie × každý vybraný model
     * (API umí jen jednu kategorii a fulltext). Klíč = „kategorie|dotaz".
     */
    private fun streams(f: Filters): List<Pair<String, Filters>> {
        val withModels = f.models.isNotEmpty()
        val cats: List<Category?> = if (withModels) listOf(Category(BazosApi.CATEGORY_APPLE, "Apple")) else f.categories.ifEmpty { listOf(null) }
        val queries: List<String> = if (withModels) f.models.map { (it.lowercase().replace("(", "").replace(")", "") + " " + f.query).trim() } else listOf(f.query)
        return cats.flatMap { c ->
            queries.map { q ->
                "${c?.id.orEmpty()}|$q" to f.copy(
                    categories = listOfNotNull(c), query = q, models = emptyList(), storages = emptyList(),
                    section = if (withModels) Section(BazosApi.SECTION_MOBILES, "Mobily") else f.section,
                )
            }
        }
    }

    private suspend fun searchSorted(filters: Filters, prev: MultiPage?): MultiPage =
        if (filters.sort == Filters.SORT_NEWEST && prev?.topFallback != true) searchIgnoringTop(filters, prev)
        else searchMerged(filters.copy(sort = if (filters.sort == Filters.SORT_NEWEST) "date" else filters.sort), prev)
            .let { if (prev?.topFallback == true) it.copy(topFallback = true) else it }

    /**
     * Nejnovější podle vložení, bez ohledu na topování. Bazoš při řazení podle data dává nejdřív blok
     * všech topovaných (v kategorii stovky), pak ostatní podle data. ID roste s časem vložení, takže
     * stažené inzeráty řadíme podle ID a vydáme jen ty, které už žádný další (starší) netopovaný nepředběhne.
     */
    private suspend fun searchIgnoringTop(filters: Filters, prev: MultiPage?): MultiPage = coroutineScope {
        val streams = streams(filters).toMap()
        val keys = streams.keys.toList()
        val perPage = if (keys.size == 1) 200 else 100
        var totals = prev?.totals ?: emptyMap()
        var offsets = prev?.offsets ?: emptyMap()
        var frontiers = prev?.frontiers ?: emptyMap()
        var buffer = prev?.buffer ?: emptyList()
        val out = mutableListOf<Listing>()
        fun exhausted(k: String) = totals[k]?.let { (offsets[k] ?: 0) >= it } ?: false
        var rounds = 0
        while (out.size < PAGE_SIZE) {
            val pending = keys.filterNot(::exhausted)
            if (pending.isEmpty()) {
                out += buffer.sortedByDescending { it.id.toLongOrNull() ?: 0 }
                buffer = emptyList()
                break
            }
            if (rounds >= MAX_TOP_SKIP_ROUNDS) {
                // obří blok topovaných (celý Bazoš bez hledání) → vzdát to a řadit jako Bazoš
                if (prev == null && out.isEmpty()) return@coroutineScope searchMerged(filters.copy(sort = "date"), null).copy(topFallback = true)
                break
            }
            val pages = pending.map { k ->
                async {
                    k to BazosApi.search(streams.getValue(k).copy(sort = "date"), offsets[k] ?: 0, perPage)
                }
            }.awaitAll()
            rounds++
            for ((k, page) in pages) {
                totals = totals + (k to page.total)
                offsets = offsets + (k to (offsets[k] ?: 0) + perPage)
                page.items.filterNot { it.isTop }.minOfOrNull { it.id.toLongOrNull() ?: 0 }?.let { frontiers = frontiers + (k to it) }
                buffer = (buffer + page.items).distinctBy { it.id }
            }
            // hranice: nejvyšší „nejnižší ID" mezi kategoriemi, které ještě mají co stáhnout
            val threshold = keys.filterNot(::exhausted).maxOfOrNull { frontiers[it] ?: Long.MAX_VALUE } ?: Long.MIN_VALUE
            val (ready, rest) = buffer.partition { (it.id.toLongOrNull() ?: 0) >= threshold }
            out += ready.sortedByDescending { it.id.toLongOrNull() ?: 0 }
            buffer = rest
        }
        MultiPage(out, totals, offsets, buffer, frontiers)
    }

    private suspend fun searchMerged(filters: Filters, prev: MultiPage? = null): MultiPage = coroutineScope {
        val streams = streams(filters).toMap()
        val keys = streams.keys.toList()
        val perPage = if (keys.size == 1) PAGE_SIZE else maxOf(10, PAGE_SIZE / keys.size + 5)
        val pending = keys.filter { k -> prev == null || (prev.offsets[k] ?: 0) < (prev.totals[k] ?: 0) }
        val pages = pending.map { k ->
            async {
                k to BazosApi.search(streams.getValue(k), prev?.offsets?.get(k) ?: 0, perPage)
            }
        }.awaitAll()
        val items = pages.flatMap { it.second.items }.distinctBy { it.id }
        val sorted = when (filters.sort) {
            "price_asc" -> items.sortedWith(compareBy(nullsLast()) { it.price })
            "price_desc" -> items.sortedWith(compareByDescending<Listing> { it.price ?: -1 })
            else -> items.sortedWith(compareByDescending<Listing> { it.isTop }.thenByDescending { it.id.toLongOrNull() ?: 0 })
        }
        MultiPage(
            items = if (keys.size == 1) pages.flatMap { it.second.items } else sorted, // jedna kategorie: pořadí přesně podle Bazoše
            totals = (prev?.totals ?: emptyMap()) + pages.associate { (k, p) -> k to p.total },
            offsets = (prev?.offsets ?: emptyMap()) + pages.associate { (k, p) -> k to (prev?.offsets?.get(k) ?: 0) + perPage },
        )
    }

    // --- Popularita značek telefonů -------------------------------------------------------

    // Popularita jen pro značky telefonů v rubrice Mobily: 9 dotazů (počet inzerátů z hlavičky), cache 30 dní.
    // Web ani API počty u značek nevypisují, jinde se řazení nedělá.
    private fun cachedPopularity(sectionId: String): List<Category>? {
        if (sectionId != BazosApi.SECTION_MOBILES) return null
        if (System.currentTimeMillis() - catalogPrefs.getLong("brands_at_$sectionId", 0) > POPULARITY_MAX_AGE_MS) return null
        val json = catalogPrefs.getString("brands_$sectionId", null) ?: return null
        return runCatching { Http.json.decodeFromString(ListSerializer(Category.serializer()), json) }.getOrNull()
    }

    /** Kategorie rubriky; u Mobilů značky podle počtu inzerátů, „Ostatní značky" a příslušenství za nimi. */
    suspend fun categoriesByPopularity(sectionId: String): List<Category> {
        if (sectionId != BazosApi.SECTION_MOBILES) return categories(sectionId)
        cachedPopularity(sectionId)?.let { return it }
        val all = categories(sectionId)
        // Bazoš řadí nejdřív značky, „Ostatní značky" (id 349) je poslední z nich, pak příslušenství
        val split = all.indexOfFirst { it.id == OTHER_BRANDS }.takeIf { it >= 0 } ?: return all
        val brands = coroutineScope {
            all.take(split).map { c -> async { c.copy(count = runCatching { BazosApi.categoryCount(sectionId, c.id) }.getOrNull()) } }.awaitAll()
        }
        if (brands.any { it.count == null }) return all
        val sorted = brands.sortedByDescending { it.count } + all.drop(split)
        catalogPrefs.edit {
            putString("brands_$sectionId", Http.json.encodeToString(ListSerializer(Category.serializer()), sorted))
            putLong("brands_at_$sectionId", System.currentTimeMillis())
        }
        return sorted
    }

    /** Model pro výběr: výkup mp.cz „od" (nejlevnější kapacita, stav B; jinak A). */
    data class ModelOption(val key: IphoneKey, val label: String, val buyoutFrom: Int?)

    /** Všechny iPhony z ceníku mp.cz, od nejnovější generace (pro výběr modelů). */
    suspend fun iphoneModels(): List<ModelOption> {
        val list = runCatching { pricelist.get() }.getOrNull() ?: return emptyList()
        return list.items.mapNotNull { v -> parseIphone(v.variant)?.takeIf { it.number != null }?.let { it.copy(storageGb = null) to v } }
            .groupBy({ it.first }, { it.second })
            .map { (key, variants) ->
                ModelOption(key, modelLabel(key), variants.mapNotNull { it.buy["B"] ?: it.buy["A"] }.minOrNull())
            }
            .sortedWith(compareBy(iphoneOrder) { it.key })
    }

    // Medián modelu (všechny kapacity) pro výběr modelů — v telefonu na 12 h, ať se 37 modelů nestahuje pořád.
    private val medianPrefs = context.applicationContext.getSharedPreferences("model_medians", Context.MODE_PRIVATE)

    /** Medián ceny modelu na Bazoši (všechny kapacity) a počet inzerátů; null = málo dat. */
    suspend fun modelMedian(key: IphoneKey): Pair<Int, Int>? {
        val k = modelLabel(key)
        medianPrefs.getString(k, null)?.split("|")?.takeIf { it.size == 3 && System.currentTimeMillis() - it[0].toLong() < 12 * 3600_000L }
            ?.let { return if (it[1] == "-") null else it[1].toInt() to it[2].toInt() }
        val m = market(key.copy(storageGb = null))
        medianPrefs.edit { putString(k, "${System.currentTimeMillis()}|${m?.median ?: "-"}|${m?.count ?: 0}") }
        return m?.let { it.median to it.count }
    }

    /** Kapacity, které u vybraných modelů existují (pro výběr kapacity). */
    suspend fun iphoneStorages(models: Collection<String>): List<Int> {
        val list = runCatching { pricelist.get() }.getOrNull() ?: return emptyList()
        return list.items.mapNotNull { parseIphone(it.variant) }
            .filter { models.isEmpty() || modelLabel(it) in models }.mapNotNull { it.storageGb }.distinct().sorted()
    }

    /** Všechny fotky inzerátu (z detailu, sdílí cache s detailem). */
    suspend fun images(id: String): List<String> = BazosApi.images(rawDetail(id))

    suspend fun suggestions(query: String): Suggestions = BazosApi.suggestions(query)

    /** Detail inzerátu; iPhony rovnou vyhodnotí (model, stav, výkup). */
    suspend fun ad(id: String): Ad {
        val prices = runCatching { pricelist.get() }.getOrNull()
        return evaluate(rawDetail(id), prices)
    }

    /** Výkup pro iPhony ve výpisu: jen položky, jejichž nadpis vypadá jako iPhone (detail kvůli popisu a stavu). */
    suspend fun enrichPhones(items: List<Listing>): Map<String, Ad> = coroutineScope {
        val phones = items.filter { parseIphone(it.title)?.number != null }
        if (phones.isEmpty()) return@coroutineScope emptyMap()
        val prices = runCatching { pricelist.get() }.getOrNull() ?: return@coroutineScope emptyMap()
        phones.map { l -> async { runCatching { evaluate(rawDetail(l.id), prices) }.getOrNull() } }
            .awaitAll().filterNotNull().filter { it.plus != null }.associateBy { it.id }
    }

    /** Stránka „Výkup": iPhony z Bazoše vyhodnocené proti výkupu mp.cz. */
    suspend fun phoneDeals(filters: Filters, prev: MultiPage?): Triple<List<Ad>, MultiPage, Long?> = coroutineScope {
        val f = filters.copy(
            section = Section(BazosApi.SECTION_MOBILES, "Mobily"),
            categories = listOf(Category(BazosApi.CATEGORY_APPLE, "Apple")),
            query = if (filters.models.isEmpty()) filters.query.ifBlank { "iphone" } else filters.query,
            hideNoise = false, // Výkup má vlastní „Skrýt prodané a šum" (vyřazené ukazuje ztlumeně)
        )
        val listDeferred = async { search(f, prev) }
        val prices = runCatching { pricelist.get() }.getOrNull()
        val page = listDeferred.await()
        val ads = page.items.map { l -> async { runCatching { evaluate(rawDetail(l.id), prices) }.getOrNull() } }.awaitAll().filterNotNull()
        Triple(ads, page, prices?.updatedAtMillis)
    }

    // --- Tržní hodnota (ceny stejného modelu na Bazoši), cache 1 h na model ---------------
    private val marketCache = ConcurrentHashMap<String, Pair<Long, List<MarketItem>>>()
    private val marketLimit = Semaphore(3)

    /** Tržní hodnota modelu z aktuálních inzerátů (1–2 dotazy na model, pak z cache). */
    suspend fun market(key: IphoneKey, excludeId: String? = null): MarketStats? {
        val query = marketQuery(key)
        val items = marketCache[query]?.takeIf { System.currentTimeMillis() - it.first < MARKET_CACHE_MS }?.second
            ?: marketLimit.withPermit {
                val f = Filters(
                    query = query, sort = "date",
                    section = Section(BazosApi.SECTION_MOBILES, "Mobily"),
                    categories = listOf(Category(BazosApi.CATEGORY_APPLE, "Apple")),
                )
                val first = BazosApi.search(f, 0, 200)
                val rest = if (first.total > 200) BazosApi.search(f, 200, 200).items else emptyList()
                (first.items + rest).map { MarketItem(it.id, it.title, it.price) }
            }.also { marketCache[query] = System.currentTimeMillis() to it }
        return marketStats(key, items, excludeId)
    }

    suspend fun refreshPricelist(): Long = pricelist.get(forceRefresh = true).updatedAtMillis

    suspend fun seller(ad: Ad): SellerProfile {
        val id = ad.seller.id ?: return SellerProfile(emptyList(), emptyList())
        sellerCache[id]?.takeIf { fresh(it.first) }?.let { return it.second.withoutAd(ad.id) }
        val profile = coroutineScope {
            val reviews = async { if (ad.seller.reviewsCount > 0) runCatching { BazosApi.sellerReviews(id) }.getOrDefault(emptyList()) else emptyList() }
            val others = async { if (ad.seller.totalAds > 1) runCatching { BazosApi.sellerAds(id) }.getOrDefault(emptyList()) else emptyList() }
            SellerProfile(reviews.await(), others.await())
        }
        sellerCache[id] = System.currentTimeMillis() to profile
        return profile.withoutAd(ad.id)
    }

    private fun SellerProfile.withoutAd(adId: String) = copy(otherAds = otherAds.filter { it.id != adId })

    private suspend fun rawDetail(id: String): JsonObject {
        detailCache[id]?.takeIf { fresh(it.first) }?.let { return it.second }
        return detailLimit.withPermit { BazosApi.detail(id) }.also { detailCache[id] = System.currentTimeMillis() to it }
    }

    private fun fresh(at: Long) = System.currentTimeMillis() - at < CACHE_MS

    companion object {
        const val PAGE_SIZE = 30
        private const val DETAIL_CONCURRENCY = 6
        private const val CACHE_MS = 10 * 60_000L
        private const val CATALOG_MAX_AGE_MS = 7 * 24 * 3600_000L
        private const val POPULARITY_MAX_AGE_MS = 30 * 24 * 3600_000L
        private const val MARKET_CACHE_MS = 3600_000L
        private const val OTHER_BRANDS = "349" // Mobily → „Ostatní značky"
        private const val MAX_TOP_SKIP_ROUNDS = 5 // kolik stránek topovaných max. přeskočit (5 × 200)

        /** Generace sestupně (18 → X), SE na konci; v rámci generace základ, e, mini, Plus, Air, Pro, Pro Max. */
        val iphoneOrder: Comparator<IphoneKey> = compareByDescending<IphoneKey> { if ((it.number ?: 0) > 2000) 0 else it.number ?: 0 }
            .thenByDescending { it.number ?: 0 }
            .thenBy { k ->
                val v = k.variants
                when {
                    "pro" in v && "max" in v -> 6; "pro" in v -> 5; "air" in v -> 4; "plus" in v -> 3; "mini" in v -> 2; "e" in v -> 1
                    "xs" in v && "max" in v -> 3; "xs" in v -> 2; "xr" in v -> 1; else -> 0
                }
            }

        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository =
            instance ?: synchronized(this) { instance ?: Repository(context.applicationContext).also { instance = it } }

        /** Detail z API Bazoše → inzerát; u iPhonů s „Plus" vyhodnocením. */
        fun evaluate(d: JsonObject, prices: Pricelist?): Ad {
            val title = d.str("title").orEmpty().replace(Regex("\\s+"), " ").trim()
            val description = d.str("description").orEmpty().replace("\r\n", "\n").trim()
            val price = (if (d.str("price_type") == "EXACT") d.int("price") else BazosApi.parsePrice(d.str("price_formatted")))?.takeIf { it > 0 }
            val category = d.obj("category")?.let { c -> c.str("id")?.let { Category(it, c.str("title").orEmpty()) } }
            val section = d.obj("section")?.let { s -> s.str("id")?.let { Section(it, s.str("title").orEmpty(), s.str("image")) } }
            val images = BazosApi.images(d)
            val top = d.bool("topped")

            return Ad(
                id = d.str("id").orEmpty(),
                url = d.str("url").orEmpty(),
                title = title,
                description = description,
                price = price,
                priceText = d.str("price_formatted"),
                // u topovaných inzerátů je „from" čas posledního topování, ne vložení → nezobrazovat
                dateMillis = if (top) null else BazosApi.parseDate(d.str("from")),
                isTop = top,
                views = d.int("views") ?: 0,
                images = images,
                thumb = images.firstOrNull()?.replace("/img/1/", "/img/1m/"), // 350px náhled místo plné fotky
                locality = d.str("locality"),
                zip = d.str("zip_code"),
                lat = d.str("latitude")?.toDoubleOrNull(),
                lng = d.str("longitude")?.toDoubleOrNull(),
                seller = Seller(d.str("phone_id"), d.str("name"), d.int("user_ads_count") ?: 0, d.int("user_reviews_count") ?: 0),
                section = section,
                category = category,
                plus = phonePlus(title, description, price, category, prices),
            )
        }

        /** Vyhodnocení iPhonu; null u inzerátů, které s iPhony nesouvisí. */
        private fun phonePlus(title: String, description: String, price: Int?, category: Category?, prices: Pricelist?): PhonePlus? {
            var key = parseIphone(title)
            val inApple = category?.id == BazosApi.CATEGORY_APPLE
            if (key?.number == null && !inApple) return null
            // model z nadpisu; když v něm není, zkusit i popis
            if (key?.number == null) key = parseIphone("$title $description") ?: key
            val match = if (key?.number != null && prices != null) MpPricelist.match(key, prices) else null
            // podezřelá cena = hluboko pod nejnižším výkupem funkčního kusu (A+–C)
            val working = match?.first?.buy?.filterKeys { it != Grade.D.code }?.values?.minOrNull()
            return PhonePlus(
                key = key?.takeIf { it.number != null },
                model = key?.takeIf { it.number != null }?.let(::modelLabel),
                storageGb = key?.storageGb,
                condition = estimateCondition(title, description),
                buyout = match?.first,
                storageGuessed = match?.second ?: false,
                issue = listingIssue(title, description) ?: priceIssue(title, price, working),
                color = extractColor(title, description),
                warranty = extractWarranty(title, description),
            )
        }
    }
}
