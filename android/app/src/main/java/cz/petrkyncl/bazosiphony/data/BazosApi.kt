package cz.petrkyncl.bazosiphony.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDateTime
import java.time.OffsetDateTime
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

    data class Filters(
        val query: String = "",
        val priceFrom: String = "2000",
        val priceTo: String = "",
        val zip: String = "",
        val distanceKm: Int = 25,
        val sort: String = "date", // date | price_asc | price_desc
    )

    data class Page(val ids: List<String>, val total: Int)

    suspend fun search(f: Filters, offset: Int, limit: Int): Page {
        val zip = f.zip.filter(Char::isDigit)
        val (json, headers) = Http.get(
            BASE + "ads.php",
            mapOf(
                "section" to SECTION_MOBILES,
                "category" to CATEGORY_APPLE,
                "sort" to f.sort,
                "query" to f.query.trim().ifEmpty { "iphone" },
                "price_from" to f.priceFrom,
                "price_to" to f.priceTo,
                "zip_code" to zip,
                "distance" to if (zip.isNotEmpty()) f.distanceKm else null,
                "offset" to offset,
                "limit" to limit,
            ),
            UA,
        )
        val ids = (json as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("id") }
        return Page(ids, headers["x-total-count"]?.toIntOrNull() ?: ids.size)
    }

    suspend fun detail(adId: String): JsonObject =
        Http.get(BASE + "ad-detail-2.php", mapOf("ad_id" to adId), UA).first as JsonObject

    /** Recenze prodejce. Telefony a e-maily recenzentů záměrně zahazujeme. */
    suspend fun sellerReviews(phoneId: String): List<Review> {
        val (json, _) = Http.get(BASE + "ratings.php", mapOf("phone_id" to phoneId, "offset" to 0, "limit" to 50), UA)
        return (json as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map {
            Review(
                text = it.str("message").orEmpty().trim(),
                positive = it.str("type") == "positive",
                author = it.str("name"),
                date = parseDate(it.str("from")),
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
        if (text == null || Regex("dohodou|v textu|zdarma", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
        return text.filter(Char::isDigit).toIntOrNull()
    }

    private val PLAIN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val PRAGUE = ZoneId.of("Europe/Prague")

    /** API posílá RFC 1123 („Tue, 06 Oct 2026 10:05:04 +0200") i „2026-10-06 10:03:02". */
    fun parseDate(value: String?): ZonedDateTime? {
        if (value.isNullOrBlank()) return null
        return runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH)) }
            .recoverCatching { LocalDateTime.parse(value, PLAIN).atZone(PRAGUE) }
            .recoverCatching { OffsetDateTime.parse(value).toZonedDateTime() }
            .getOrNull()
    }

    fun images(d: JsonObject): List<String> =
        (d["images"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
}
