package cz.petrkyncl.bazosiphony.data

import cz.petrkyncl.bazosiphony.domain.ConditionEstimate
import cz.petrkyncl.bazosiphony.domain.Grade
import cz.petrkyncl.bazosiphony.domain.Issue
import kotlinx.serialization.Serializable

/** Rubrika Bazoše (MO = Mobily, AU = Auto…). */
@Serializable
data class Section(val id: String, val title: String, val image: String? = null)

@Serializable
data class Category(val id: String, val title: String, val count: Int? = null) {
    // počet inzerátů se mění → do porovnání (uložená hledání, výběr) nepatří
    override fun equals(other: Any?) = other is Category && other.id == id
    override fun hashCode() = id.hashCode()
}

/** Parametry hledání — stejné jako v oficiální appce (ads.php). */
@Serializable
data class Filters(
    val query: String = "",
    val section: Section? = null,
    val categories: List<Category> = emptyList(), // víc značek najednou → appka spojí výsledky
    @Deprecated("jen kvůli starým uloženým hledáním — použij categories")
    val category: Category? = null,
    val priceFrom: String = "",
    val priceTo: String = "",
    val zip: String = "",
    val distanceKm: Int = 25,
    val city: String? = null, // jen dané město (CITIES); přebíjí zip + okruh
    val sort: String = SORT_NEWEST, // newest (podle vložení, bez TOPu) | date (jako Bazoš, TOP první) | price_asc | price_desc
) {
    companion object {
        const val SORT_NEWEST = "newest"
    }

    val activeCount get() = listOf(priceFrom, priceTo, zip).count { it.isNotBlank() } + (if (city != null) 1 else 0)

    /** Klíč pro porovnání uložených hledání (pořadí kategorií ani řazení nehraje roli). */
    fun watchKey(): Filters = copy(sort = SORT_NEWEST, categories = categories.sortedBy { it.id })

    /** Převod starého formátu (jedna kategorie) na nový. */
    @Suppress("DEPRECATION")
    fun migrated(): Filters = if (category == null) this else copy(categories = (categories + category).distinct(), category = null)

    val categoriesLabel: String?
        get() = when (categories.size) {
            0 -> null
            1, 2, 3 -> categories.joinToString(" + ") { it.title }
            else -> "${categories.size} kategorií"
        }

    /** Krátký popis pro uložená hledání a nadpisy. */
    fun label(): String = listOfNotNull(
        query.trim().takeIf { it.isNotEmpty() }?.let { "„$it“" },
        categoriesLabel ?: section?.title,
        if (priceFrom.isNotBlank() || priceTo.isNotBlank()) "${priceFrom.ifBlank { "0" }}–${priceTo.ifBlank { "∞" }} Kč" else null,
        cz.petrkyncl.bazosiphony.domain.cityById(city)?.name ?: zip.takeIf { it.isNotBlank() }?.let { "$it +$distanceKm km" },
    ).joinToString(" · ").ifEmpty { "Všechny inzeráty" }
}

/** Položka výpisu (bez popisu a fotek — ty jsou až v detailu). */
@Serializable
data class Listing(
    val id: String,
    val title: String,
    val priceText: String?,
    val price: Int?,
    val thumb: String?,
    val locality: String?,
    val isTop: Boolean,
    val views: Int,
    val url: String,
    val dateMillis: Long?, // u topovaných null (API vrací čas topování, ne vložení)
)

enum class Deal { GOOD, BAD, SUSPICIOUS, UNKNOWN, EXCLUDED }

data class Seller(val id: String?, val name: String?, val totalAds: Int, val reviewsCount: Int)

/** „Plus" vrstva pro iPhony: model, odhad stavu, výkup mp.cz. */
data class PhonePlus(
    val key: cz.petrkyncl.bazosiphony.domain.IphoneKey?, // strukturní klíč modelu (pro tržní hodnotu)
    val model: String?, // rozpoznaný model, např. „iPhone 15 Pro"
    val storageGb: Int?,
    val condition: ConditionEstimate,
    val buyout: PriceVariant?, // spárovaná varianta ceníku mp.cz
    val storageGuessed: Boolean, // kapacita v inzerátu chybí/nesedí → nejbližší/nejlevnější varianta
    val issue: Issue?,
    val color: String? = null,
    val warranty: String? = null, // „záruka do 01/2028" / „záruka"
) {
    val isReplica get() = condition.warnings.any { it.startsWith("Replika") }
    fun buyoutFor(grade: Grade): Int? = buyout?.buy?.get(grade.code)
}

/** Detail inzerátu. */
data class Ad(
    val id: String,
    val url: String,
    val title: String,
    val description: String,
    val price: Int?,
    val priceText: String?,
    val dateMillis: Long?,
    val isTop: Boolean,
    val views: Int,
    val images: List<String>,
    val thumb: String?,
    val locality: String?,
    val zip: String?,
    val lat: Double?,
    val lng: Double?,
    val seller: Seller,
    val section: Section?,
    val category: Category?,
    val plus: PhonePlus?, // null = není iPhone
) {
    fun profitFor(grade: Grade): Int? = plus?.buyoutFor(grade)?.let { b -> price?.let { b - it } }
    val profit: Int? get() = plus?.let { profitFor(it.condition.grade) }

    val deal: Deal
        get() = when {
            plus == null -> Deal.UNKNOWN
            plus.issue?.excludes == true || plus.isReplica -> Deal.EXCLUDED
            profit == null -> Deal.UNKNOWN
            plus.issue == Issue.SUSPICIOUS_PRICE -> Deal.SUSPICIOUS
            profit!! > 0 -> Deal.GOOD
            else -> Deal.BAD
        }

    fun toListing() = Listing(id, title, priceText, price, thumb, locality, isTop, views, url, dateMillis)
}

data class Review(val text: String, val positive: Boolean, val author: String?, val dateMillis: Long?)

data class SellerAd(val id: String, val title: String, val price: Int?, val url: String, val image: String?, val isPhone: Boolean)

data class SellerProfile(val reviews: List<Review>, val otherAds: List<SellerAd>) {
    val positive get() = reviews.count { it.positive }
    val negative get() = reviews.count { !it.positive }
    val phoneAds get() = 1 + otherAds.count { it.isPhone } // včetně tohoto inzerátu
    val likelyDealer get() = phoneAds >= DEALER_PHONE_ADS

    companion object {
        const val DEALER_PHONE_ADS = 4 // od kolika telefonů v inzerátech je prodejce nejspíš překupník
    }
}

data class Suggestions(val keywords: List<String>, val sections: List<Section>, val categories: List<Pair<Section, Category>>)

/** Uložené hledání s hlídáním nových inzerátů. */
@Serializable
data class SavedSearch(
    val id: String,
    val filters: Filters,
    val notify: Boolean = true,
    val lastSeenMaxId: Long = 0, // nejvyšší ID inzerátu, který už uživatel viděl
    val newCount: Int = 0,
)

/** Varianta výkupního ceníku mp.cz (ukládá se do telefonu jako JSON). */
@Serializable
data class PriceVariant(
    val model: String, // „Apple iPhone 15 Pro"
    val variant: String, // „Apple iPhone 15 Pro, 128GB"
    val storageGb: Int?,
    val buy: Map<String, Int>, // stav („A+", „A"…) → kolik mp.cz zaplatí
    val sell: Map<String, Int>, // stav → za kolik mp.cz prodává (jen A+–C)
    val image: String?,
    val url: String?,
)

@Serializable
data class Pricelist(val updatedAtMillis: Long, val items: List<PriceVariant>)
