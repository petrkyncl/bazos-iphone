package cz.petrkyncl.bazosiphony.domain

/**
 * Párování iPhonů na výkupní ceník mp.cz: strukturní klíč číslo + varianta + úložiště
 * (port z webové verze src/lib/matching.ts, původně matching.py z bazos-finder).
 */

private val DIACRITICS = mapOf(
    'á' to 'a', 'č' to 'c', 'ď' to 'd', 'é' to 'e', 'ě' to 'e', 'í' to 'i', 'ň' to 'n', 'ó' to 'o',
    'ř' to 'r', 'š' to 's', 'ť' to 't', 'ú' to 'u', 'ů' to 'u', 'ý' to 'y', 'ž' to 'z',
)

fun stripDiacritics(text: String): String =
    buildString(text.length) { for (c in text.lowercase()) append(DIACRITICS[c] ?: c) }

data class IphoneKey(
    val number: Int?, // generace (X/XR/XS = 10, Air = 17, SE = rok vydání)
    val variants: List<String>, // seřazené: air, e, max, mini, plus, pro, x, xr, xs
    val storageGb: Int?,
)

private val STORAGE_VALUES = setOf(16, 32, 64, 128, 256, 512, 1024)
private val VARIANT_WORDS = setOf("pro", "max", "mini", "plus")

fun normalize(text: String): String = stripDiacritics(text)
    .replace("+", " ")
    .replace(Regex("([a-z])(\\d)"), "$1 $2")
    .replace(Regex("(\\d)([a-z])"), "$1 $2")
    .replace(Regex("[^a-z0-9/]+"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()

private fun extractStorage(norm: String): Int? {
    if (Regex("\\b1\\s*tb\\b").containsMatchIn(norm)) return 1024
    if (Regex("\\b2\\s*tb\\b").containsMatchIn(norm)) return 2048
    for (m in Regex("\\b(\\d{2,4})\\s*(gb)?\\b").findAll(norm)) {
        val v = m.groupValues[1].toInt()
        if (v in STORAGE_VALUES && v >= 32) return v
    }
    return null
}

/** Rozparsuje název iPhonu. `null`, když text iPhone neobsahuje. */
fun parseIphone(text: String): IphoneKey? {
    var norm = normalize(text)
        .replace(Regex("\\bi phone\\b"), "iphone") // „I PHONE", „i-phone"
        .replace(Regex("\\b(iphohe|iphnoe|ipone|iphon|ifone|aiphone)\\b"), "iphone") // časté překlepy
    // „Apple 16 Pro", „Phone 17 Pro" (bez „iPhone"); ne „Apple Watch 9", ne „Nothing Phone 2"
    norm = norm.replace(Regex("^(apple )?phone (?=\\d)"), "iphone ").replace(Regex("\\bapple (?=\\d{1,2}\\b)"), "iphone ")
    val tokens = norm.split(Regex("[\\s/]+"))
    val idx = tokens.indexOf("iphone")
    if (idx < 0) return null
    val after = tokens.drop(idx + 1)
    val variants = sortedSetOf<String>()
    var number: Int? = null
    var numberPos = -1
    val first = after.firstOrNull() ?: ""

    when {
        "air" in after.take(3) -> { number = 17; variants += "air" } // „iPhone Air", „iPhone 17 Air"
        first == "x" || first == "xr" || first == "xs" -> { number = 10; variants += first }
        first == "se" -> {
            var year = after.mapNotNull { it.toIntOrNull() }.firstOrNull { it == 2016 || it == 2020 || it == 2022 }
            if (year == null) {
                year = when (after.drop(1).take(2).firstOrNull { it == "1" || it == "2" || it == "3" }) {
                    "1" -> 2016; "2" -> 2020; "3" -> 2022; else -> null
                }
            }
            if (year == null && "5" in after && "g" in after) year = 2022
            number = year // neznámý rok → nespárovat (nehádat mezi 2020 a 2022)
        }
        else -> {
            numberPos = after.indexOfFirst { t -> t.all(Char::isDigit) && t.isNotEmpty() && t.toInt() in 1..30 }
            if (numberPos >= 0) number = after[numberPos].toInt()
        }
    }

    // varianty jen kousek za číslem modelu (ne „max" z „max. stav" v půlce nadpisu)
    after.take(maxOf(numberPos, 0) + 4).filter { it in VARIANT_WORDS }.forEach { variants += it }
    if (numberPos >= 0 && after.getOrNull(numberPos + 1) == "e") variants += "e" // 16e, ne „e sim"
    if ("plus" !in variants && Regex("iphone\\s*\\d{1,2}\\s*\\+", RegexOption.IGNORE_CASE).containsMatchIn(text)) variants += "plus"

    return IphoneKey(number, variants.toList(), extractStorage(norm))
}

fun sameModel(a: IphoneKey, b: IphoneKey): Boolean =
    a.number != null && a.number == b.number && a.variants == b.variants

fun modelLabel(k: IphoneKey): String {
    val v = k.variants
    if ("x" in v || "xr" in v || "xs" in v) {
        return "iPhone " + v.filter { it != "max" }.joinToString("").uppercase() + if ("max" in v) " Max" else ""
    }
    if ("air" in v) return "iPhone Air"
    if ((k.number ?: 0) > 2000) return "iPhone SE (${k.number})"
    val words = listOf("mini", "plus", "pro", "max").filter { it in v }.map { it.replaceFirstChar(Char::uppercase) }
    return "iPhone ${k.number}${if ("e" in v) "e" else ""}" + if (words.isEmpty()) "" else " " + words.joinToString(" ")
}

// ---------------------------------------------------------------------------
// Hledání frází s ohledem na negaci („bez škrábanců", „nejedná se o repliku")
// ---------------------------------------------------------------------------
private val NEG_CUE = Regex("\\b(bez|beze|zadn\\w*|neni|nejsou|nema|nejde|nelze|nejedna\\s+se|na\\s+rozdil|ani)\\b")
private val CONTRAST = Regex("\\b(ale|avsak|vsak|ovsem|bohuzel|jenze|nicmene|krome)\\b")
private val SENTENCE_BOUNDARY = Regex("[.!?\\n\\r|;]")

private fun clauseNegated(text: String, start: Int): Boolean {
    val boundary = SENTENCE_BOUNDARY.findAll(text.substring(0, start)).lastOrNull()?.range?.first ?: -1
    val span = text.substring(boundary + 1, start)
    val lastCue = NEG_CUE.findAll(span).lastOrNull() ?: return false
    return !CONTRAST.containsMatchIn(span.substring(lastCue.range.last + 1)) // „bez vady, ALE displej nefunkční"
}

/** První nepopřený výskyt vzoru (text bez diakritiky), nebo null. */
fun findUnnegated(pattern: Regex, text: String): String? {
    val t = stripDiacritics(text)
    return pattern.findAll(t).firstOrNull { !clauseNegated(t, it.range.first) }?.value
}

private val REPLICA = Regex("\\b(replik\\w*|kopi[eiju]\\w*|napodoben\\w*|fake|falesn\\w+|klon\\w*|1\\s*:\\s*1|cinsk\\w+\\s+verz\\w*|hong\\s*kong)\\b")

fun isReplica(text: String): Boolean = findUnnegated(REPLICA, text) != null
