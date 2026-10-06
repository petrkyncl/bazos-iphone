package cz.petrkyncl.bazosiphony.data

import android.content.Context
import cz.petrkyncl.bazosiphony.domain.Grade
import cz.petrkyncl.bazosiphony.domain.estimateCondition
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
import java.util.concurrent.ConcurrentHashMap

/** Jediný přístupový bod k datům (Bazoš + ceník mp.cz) s cache v paměti. */
class Repository private constructor(context: Context) {
    private val pricelist = MpPricelist(context)
    private val detailCache = ConcurrentHashMap<String, Pair<Long, JsonObject>>()
    private val sellerCache = ConcurrentHashMap<String, Pair<Long, SellerProfile>>()
    private val categoryCache = ConcurrentHashMap<String, List<Category>>()
    private var sectionsCache: List<Section>? = null
    private val detailLimit = Semaphore(DETAIL_CONCURRENCY)

    suspend fun sections(): List<Section> = sectionsCache ?: BazosApi.sections().also { sectionsCache = it }

    suspend fun categories(sectionId: String): List<Category> =
        categoryCache[sectionId] ?: BazosApi.categories(sectionId).also { categoryCache[sectionId] = it }

    suspend fun search(filters: Filters, offset: Int): BazosApi.Page = BazosApi.search(filters, offset, PAGE_SIZE)

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
            .awaitAll().filterNotNull().filter { it.plus?.buyout != null }.associateBy { it.id }
    }

    /** Stránka „Výkup": iPhony z Bazoše vyhodnocené proti výkupu mp.cz. */
    suspend fun phoneDeals(filters: Filters, offset: Int): Triple<List<Ad>, Int, Long?> = coroutineScope {
        val f = filters.copy(
            section = Section(BazosApi.SECTION_MOBILES, "Mobily"),
            category = Category(BazosApi.CATEGORY_APPLE, "Apple"),
            query = filters.query.ifBlank { "iphone" },
        )
        val listDeferred = async { search(f, offset) }
        val prices = runCatching { pricelist.get() }.getOrNull()
        val page = listDeferred.await()
        val ads = page.items.map { l -> async { runCatching { evaluate(rawDetail(l.id), prices) }.getOrNull() } }.awaitAll().filterNotNull()
        Triple(ads, page.total, prices?.updatedAtMillis)
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
                model = key?.takeIf { it.number != null }?.let(::modelLabel),
                storageGb = key?.storageGb,
                condition = estimateCondition(title, description),
                buyout = match?.first,
                storageGuessed = match?.second ?: false,
                issue = listingIssue(title, description) ?: priceIssue(title, price, working),
            )
        }
    }
}
