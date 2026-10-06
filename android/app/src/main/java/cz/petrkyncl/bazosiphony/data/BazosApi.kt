package cz.petrkyncl.bazosiphony.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Mobilní API Bazoše (zjištěno dekompilací Android appky 2.26.1, viz docs/bazos-api.md v repu).
 * Čtecí endpointy nepotřebují přihlášení. API je neoficiální → cache a málo souběžných požadavků.
 */
object BazosApi {
    private const val BASE = "https://www.bazos.cz/api/v2/"
    private const val UA = "ktor-client" // stejně jako oficiální appka
    const val SECTION_MOBILES = "MO"
    const val CATEGORY_APPLE = "439"

    data class Page(val items: List<Listing>, val total: Int)

    suspend fun sections(): List<Section> {
        val (json, _) = Http.get(BASE + "sections.php", userAgent = UA)
        return (json as JsonArray).mapNotNull { it as? JsonObject }.mapNotNull { s ->
            Section(s.str("id") ?: return@mapNotNull null, s.str("title").orEmpty(), s.str("image"))
        }
    }

    suspend fun categories(sectionId: String): List<Category> {
        val (json, _) = Http.get(BASE + "categories.php", mapOf("section" to sectionId), UA)
        return (json as JsonArray).mapNotNull { it as? JsonObject }.mapNotNull { c ->
            Category(c.str("id") ?: return@mapNotNull null, c.str("title").orEmpty())
        }
    }

    /** Hledání v jedné kategorii (API víc najednou neumí — čárka vrací 0, opakovaný parametr bere poslední). */
    suspend fun search(f: Filters, offset: Int, limit: Int): Page {
        require(f.categories.size <= 1) { "víc kategorií spojuje Repository.search" }
        val zip = f.zip.filter(Char::isDigit)
        val (json, headers) = Http.get(
            BASE + "ads.php",
            mapOf(
                "section" to f.section?.id,
                "category" to f.categories.firstOrNull()?.id,
                "sort" to if (f.sort == Filters.SORT_NEWEST) "date" else f.sort, // „bez TOPu" řadí appka sama
                "query" to f.query.trim(),
                "price_from" to f.priceFrom,
                "price_to" to f.priceTo,
                "zip_code" to zip,
                "distance" to if (zip.isNotEmpty()) f.distanceKm else null,
                "offset" to offset,
                "limit" to limit,
            ),
            UA,
        )
        val items = (json as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::listing) }
        return Page(items, headers["x-total-count"]?.toIntOrNull() ?: items.size)
    }

    private fun listing(o: JsonObject): Listing? {
        val top = o.bool("topped")
        return Listing(
            id = o.str("id") ?: return null,
            title = o.str("title").orEmpty().replace(Regex("\\s+"), " ").trim(),
            priceText = o.str("price_formatted"),
            price = parsePrice(o.str("price_formatted")),
            thumb = o.str("image_thumbnail"),
            locality = o.str("locality"),
            isTop = top,
            views = o.int("views") ?: 0,
            url = o.str("url").orEmpty(),
            dateMillis = if (top) null else parseDate(o.str("from")),
        )
    }

    suspend fun detail(adId: String): JsonObject =
        Http.get(BASE + "ad-detail-2.php", mapOf("ad_id" to adId), UA).first as JsonObject

    suspend fun suggestions(query: String): Suggestions {
        val o = Http.get(BASE + "suggestionsnew.php", mapOf("query" to query), UA).first as JsonObject
        fun arr(k: String) = (o[k] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        return Suggestions(
            keywords = arr("keywords").mapNotNull { it.str("keyword") },
            sections = arr("sections").mapNotNull { s -> s.str("section")?.let { Section(it, s.str("title").orEmpty()) } },
            categories = arr("categories").mapNotNull { c ->
                val sec = Section(c.str("parentSection") ?: return@mapNotNull null, c.str("parentTitle").orEmpty())
                sec to Category(c.str("category") ?: return@mapNotNull null, c.str("title").orEmpty())
            },
        )
    }

    /** Recenze prodejce. Telefony a e-maily recenzentů záměrně zahazujeme. */
    suspend fun sellerReviews(phoneId: String): List<Review> {
        val (json, _) = Http.get(BASE + "ratings.php", mapOf("phone_id" to phoneId, "offset" to 0, "limit" to 50), UA)
        return (json as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map {
            Review(
                text = it.str("message").orEmpty().trim(),
                positive = it.str("type") == "positive",
                author = it.str("name"),
                dateMillis = parseDate(it.str("from")),
            )
        }
    }

    /** Všechny inzeráty prodejce (napříč rubrikami), stejně jako „Všechny inzeráty uživatele". */
    suspend fun sellerAds(phoneId: String): List<SellerAd> {
        val (json, _) = Http.get(BASE + "ads.php", mapOf("phone_id" to phoneId, "offset" to 0, "limit" to 40), UA)
        return (json as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map {
            val url = it.str("url").orEmpty()
            SellerAd(
                id = it.str("id").orEmpty(),
                title = it.str("title").orEmpty(),
                price = parsePrice(it.str("price_formatted")),
                url = url,
                image = it.str("image_thumbnail"),
                isPhone = "mobil.bazos." in url,
            )
        }
    }

    fun parsePrice(text: String?): Int? {
        if (text == null || Regex("dohodou|v textu|zdarma|nabídněte", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
        return text.filter(Char::isDigit).toIntOrNull()
    }

    private val PLAIN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val PRAGUE = ZoneId.of("Europe/Prague")

    /** API posílá RFC 1123 („Tue, 06 Oct 2026 10:05:04 +0200") i „2026-10-06 10:03:02". */
    fun parseDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH)) }
            .recoverCatching { LocalDateTime.parse(value, PLAIN).atZone(PRAGUE) }
            .getOrNull()?.toInstant()?.toEpochMilli()
    }

    fun images(d: JsonObject): List<String> =
        (d["images"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
}

/** Počet inzerátů v kategorii (hlavička x-total-count, stačí limit=1). */
suspend fun BazosApi.categoryCount(sectionId: String, categoryId: String): Int? =
    search(Filters(section = Section(sectionId, ""), categories = listOf(Category(categoryId, ""))), 0, 1).total
