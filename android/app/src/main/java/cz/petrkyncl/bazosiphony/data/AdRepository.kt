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

class AdRepository(context: Context) {
    private val pricelist = MpPricelist(context)
    private val detailCache = ConcurrentHashMap<String, Pair<Long, JsonObject>>()
    private val sellerCache = ConcurrentHashMap<String, Pair<Long, SellerProfile>>()

    data class Page(val ads: List<Ad>, val total: Int, val pricelistUpdatedAt: Long?)

    /** Stránka inzerátů: výpis + detaily (popis, přesná cena, fotky) + vyhodnocení. */
    suspend fun page(filters: BazosApi.Filters, offset: Int): Page = coroutineScope {
        val listDeferred = async { BazosApi.search(filters, offset, PAGE_SIZE) }
        val prices = runCatching { pricelist.get() }.getOrNull()
        val list = listDeferred.await()
        val limit = Semaphore(DETAIL_CONCURRENCY)
        val ads = list.ids.map { id ->
            async { limit.withPermit { runCatching { evaluate(detail(id), prices) }.getOrNull() } }
        }.awaitAll().filterNotNull()
        Page(ads, list.total, prices?.updatedAtMillis)
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

    private suspend fun detail(id: String): JsonObject {
        detailCache[id]?.takeIf { fresh(it.first) }?.let { return it.second }
        return BazosApi.detail(id).also { detailCache[id] = System.currentTimeMillis() to it }
    }

    private fun fresh(at: Long) = System.currentTimeMillis() - at < CACHE_MS

    companion object {
        const val PAGE_SIZE = 30
        private const val DETAIL_CONCURRENCY = 6
        private const val CACHE_MS = 10 * 60_000L

        /** Detail z API Bazoše → vyhodnocený inzerát. */
        fun evaluate(d: JsonObject, prices: Pricelist?): Ad {
            val title = d.str("title").orEmpty().replace(Regex("\\s+"), " ").trim()
            val description = d.str("description").orEmpty().replace("\r\n", "\n").trim()
            val price = if (d.str("price_type") == "EXACT") d.int("price") else BazosApi.parsePrice(d.str("price_formatted"))

            // model z nadpisu; když v něm není, zkusit i popis
            var key = parseIphone(title)
            if (key?.number == null) key = parseIphone("$title $description") ?: key
            val condition = estimateCondition(title, description)
            val match = if (key?.number != null && prices != null) MpPricelist.match(key, prices) else null

            // podezřelá cena = hluboko pod nejnižším výkupem funkčního kusu (A+–C)
            val working = match?.first?.buy?.filterKeys { it != Grade.D.code }?.values?.minOrNull()
            val issue = listingIssue(title, description) ?: priceIssue(title, price, working)
            val images = BazosApi.images(d)

            return Ad(
                id = d.str("id").orEmpty(),
                url = d.str("url").orEmpty(),
                title = title,
                description = description,
                price = price?.takeIf { it > 0 },
                priceText = d.str("price_formatted"),
                // u topovaných inzerátů je „from" čas posledního topování, ne vložení → nezobrazovat
                date = if (d.bool("topped")) null else BazosApi.parseDate(d.str("from")),
                isTop = d.bool("topped"),
                views = d.int("views") ?: 0,
                images = images,
                thumb = images.firstOrNull()?.replace("/img/1/", "/img/1m/"), // 350px náhled místo plné fotky
                locality = d.str("locality"),
                zip = d.str("zip_code"),
                lat = d.str("latitude")?.toDoubleOrNull(),
                lng = d.str("longitude")?.toDoubleOrNull(),
                seller = Seller(d.str("phone_id"), d.str("name"), d.int("user_ads_count") ?: 0, d.int("user_reviews_count") ?: 0),
                model = key?.takeIf { it.number != null }?.let(::modelLabel),
                storageGb = key?.storageGb,
                condition = condition,
                buyout = match?.first,
                storageGuessed = match?.second ?: false,
                issue = issue,
            )
        }
    }
}
