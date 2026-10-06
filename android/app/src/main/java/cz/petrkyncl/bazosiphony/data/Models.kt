package cz.petrkyncl.bazosiphony.data

import cz.petrkyncl.bazosiphony.domain.ConditionEstimate
import cz.petrkyncl.bazosiphony.domain.Grade
import cz.petrkyncl.bazosiphony.domain.Issue
import kotlinx.serialization.Serializable
import java.time.ZonedDateTime

enum class Deal { GOOD, BAD, SUSPICIOUS, UNKNOWN, EXCLUDED }

data class Seller(val id: String?, val name: String?, val totalAds: Int, val reviewsCount: Int)

/** Inzerát z Bazoše po vyhodnocení: model, stav, výkup, zisk. */
data class Ad(
    val id: String,
    val url: String,
    val title: String,
    val description: String,
    val price: Int?,
    val priceText: String?,
    val date: ZonedDateTime?,
    val isTop: Boolean,
    val views: Int,
    val images: List<String>,
    val thumb: String?,
    val locality: String?,
    val zip: String?,
    val lat: Double?,
    val lng: Double?,
    val seller: Seller,
    val model: String?, // rozpoznaný model, např. „iPhone 15 Pro"
    val storageGb: Int?,
    val condition: ConditionEstimate,
    val buyout: PriceVariant?, // spárovaná varianta ceníku mp.cz
    val storageGuessed: Boolean, // kapacita v inzerátu chybí/nesedí → nejbližší/nejlevnější varianta
    val issue: Issue?,
) {
    val isReplica get() = condition.warnings.any { it.startsWith("Replika") }

    fun buyoutFor(grade: Grade): Int? = buyout?.buy?.get(grade.code)
    fun profitFor(grade: Grade): Int? = buyoutFor(grade)?.let { b -> price?.let { b - it } }

    val profit: Int? get() = profitFor(condition.grade)

    val deal: Deal
        get() = when {
            issue?.excludes == true || isReplica -> Deal.EXCLUDED
            profit == null -> Deal.UNKNOWN
            issue == Issue.SUSPICIOUS_PRICE -> Deal.SUSPICIOUS
            profit!! > 0 -> Deal.GOOD
            else -> Deal.BAD
        }
}

data class Review(val text: String, val positive: Boolean, val author: String?, val date: ZonedDateTime?)

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
