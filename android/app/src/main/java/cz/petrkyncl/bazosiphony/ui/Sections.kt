package cz.petrkyncl.bazosiphony.ui

import androidx.annotation.DrawableRes
import cz.petrkyncl.bazosiphony.R
import cz.petrkyncl.bazosiphony.data.Section

/** Vlastní ikony rubrik (Phosphor duotone, MIT) místo obrázků Bazoše. */
@DrawableRes
fun sectionIcon(id: String): Int = when (id) {
    "MO" -> R.drawable.ph_device_mobile
    "MT" -> R.drawable.ph_motorcycle
    "AU" -> R.drawable.ph_car_profile
    "PC" -> R.drawable.ph_desktop
    "EL" -> R.drawable.ph_television_simple
    "FO" -> R.drawable.ph_camera
    "HU" -> R.drawable.ph_guitar
    "SP" -> R.drawable.ph_bicycle
    "ST" -> R.drawable.ph_wrench
    "DU" -> R.drawable.ph_house_line
    "NA" -> R.drawable.ph_couch
    "OB" -> R.drawable.ph_t_shirt
    "KN" -> R.drawable.ph_books
    "VS" -> R.drawable.ph_ticket
    "SL" -> R.drawable.ph_handshake
    "PR" -> R.drawable.ph_briefcase
    "RE" -> R.drawable.ph_buildings
    "DE" -> R.drawable.ph_baby
    "ZV" -> R.drawable.ph_paw_print
    else -> R.drawable.ph_package
}

/**
 * Pořadí rubrik podle toho, co uživatel na Bazoši opravdu kupuje: mobily, motorky, auta nahoře,
 * reality / děti / zvířata úplně dole. Neznámé (nové) rubriky se zařadí před ty spodní.
 */
private val PREFERRED = listOf(
    "MO", "MT", "AU", "PC", "EL", "FO", "HU", "SP", "ST", "DU", "NA", "OB", "KN", "VS", "SL", "PR", "OS",
    "RE", "DE", "ZV",
)

fun List<Section>.inPreferredOrder(): List<Section> {
    val unknownRank = PREFERRED.indexOf("RE") - 0.5
    return sortedBy { s -> PREFERRED.indexOf(s.id).takeIf { it >= 0 }?.toDouble() ?: unknownRank }
}
