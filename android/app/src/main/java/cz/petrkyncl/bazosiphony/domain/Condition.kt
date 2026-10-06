package cz.petrkyncl.bazosiphony.domain

/**
 * Odhad stavu telefonu podle textu inzerátu — pravidla, žádné AI (zdarma, okamžitě).
 * Stupně odpovídají výkupu mp.cz:
 *   A+ zánovní (nový, nepoužitý) · A jako nový (bez škrábanců) · B použitý (lehké oděrky)
 *   C opotřebovaný (rýhy, víc oděrek) · D poškozený (prasklý displej/záda, jakákoli funkční vada)
 */
enum class Grade(val code: String, val title: String) {
    APLUS("A+", "Zánovní"), A("A", "Jako nový"), B("B", "Použitý"), C("C", "Opotřebovaný"), D("D", "Poškozený");

    companion object {
        fun of(code: String): Grade? = entries.firstOrNull { it.code == code }
    }
}

enum class Confidence(val label: String) { HIGH("vysoká jistota"), MEDIUM("střední jistota"), LOW("stav nepopsán") }

data class Signal(val grade: Grade, val label: String, val match: String)

data class ConditionEstimate(
    val grade: Grade,
    val confidence: Confidence,
    val signals: List<Signal>,
    val batteryPct: Int?,
    val warnings: List<String>,
)

private class Rule(val grade: Grade, val label: String, pattern: String) {
    val re = Regex(pattern)
}

// Vzory pracují s textem bez diakritiky a malými písmeny.
private val RULES = listOf(
    // --- D: poškozený / vada ---
    Rule(Grade.D, "prasklina", """\b(prask\w*|popraskan\w*|roztrisk\w*|rozbit\w*|naprasl\w*)\b"""),
    Rule(Grade.D, "nefunkční", """\b(nefunkc\w+|nezapin\w+|(nejde|nelze)\s+zapnout|nenabiji|nereaguje|zamrz\w*)\b"""),
    Rule(Grade.D, "na díly / opravu", """\b(na\s+(nahradni\s+)?dily|na\s+opravu|k\s+oprave|nahradni\s+dily)\b"""),
    Rule(Grade.D, "Face ID nefunguje", """\b(face\s*id|touch\s*id)\s+(nefung\w*|nejde|nefunkc\w*|nepracuje)|\b(nefung\w*|nejde)\s+(face\s*id|touch\s*id)\b"""),
    Rule(Grade.D, "vada displeje", """\b(vypalen\w*|burn\s*-?\s*in|mrtv\w+\s+pixel\w*|zelen\w+\s+(car\w*|pruh\w*)|flek\w*\s+na\s+displej\w*|skvrn\w*\s+na\s+displej\w*)\b"""),
    Rule(Grade.D, "iCloud blokace", """\b(icloud|apple\s*id)\s+(zamcen\w*|blokovan\w*|lock\w*|zablokovan\w*)|\b(zamcen\w*|blokovan\w*)\s+(na\s+)?(icloud|apple\s*id)\b"""),
    Rule(Grade.D, "vada", """\b(nefunguje|nefunguji|vadn\w+|zavad\w+|poskozen\w+|promacklin\w*|ohnut\w*|tekl\w*\s+voda|polit\w*|utopen\w*)\b"""),

    // --- C: výrazné opotřebení ---
    Rule(Grade.C, "opotřebovaný", """\b(opotrebovan\w*|(silne|vyrazne|vetsi|hodne)\s+opotreben\w*|ojet\w*|hodne\s+pouzivan\w*)\b"""),
    Rule(Grade.C, "výrazné škrábance", """\b(hlubsi\w*|hlubok\w+|vyrazn\w+|vetsi\w*|viditeln\w+|vic\w*|vice|hodne|spousta)\s+(skrab\w*|rh\w*|oder\w*|odren\w*|tukan\w*)"""),
    Rule(Grade.C, "rýhy / ťukance", """\b(ryh\w*|tukan\w*|tuknut\w*|odlupl\w*|odlupnut\w*)\b"""),

    // --- B: lehké známky používání ---
    Rule(Grade.B, "známky používání", """\b(znamk\w*\s+(pouzivan\w*|pouziti|opotreben\w*)|(bezn\w*|drobn\w*|minimaln\w*|mal\w*|lehk\w*)\s+opotreben\w*)\b"""),
    Rule(Grade.B, "škrábance / oděrky", """\b(skrab\w*|mikroskrab\w*|oder\w*|odren\w*|otluk\w*|otlac\w*)\b"""),
    Rule(Grade.B, "použitý", """\b(pouzivan\w*\s+stav|bezny\s+stav|dobry\s+stav|zachovaly\s+stav)\b"""),

    // --- A: jako nový ---
    Rule(Grade.A, "jako nový", """\b(jako\s+nov\w*|stav\s+nov\w*|perfektni\w*|bezvadn\w*|vyborn\w+\s+stav\w*|top\s+stav\w*|10\s*/\s*10|100\s*%\s*stav\w*|zanovn\w*|nulov\w+\s+znamk\w*)\b"""),
    Rule(Grade.A, "bez škrábanců", """\bbez\s+(jakychkoliv\s+|jedineho\s+|zadnych\s+|viditelnych\s+)?(skrab\w*|oder\w*|znamek|poskozeni|vad)\b"""),

    // --- A+: nový, nepoužitý ---
    Rule(Grade.APLUS, "nový nepoužitý", """\b(nerozbal\w*|zapecet\w*|neotevren\w*|nepouzit\w*|nerozdel\w*|v\s+foli\w*|sealed|novy\s+kus|zcela\s+novy|uplne\s+novy|brand\s+new)\b"""),
)

// Fráze, které obsahují slovo z negativních pravidel, ale telefon nepoškozují (ochranné sklo, kryt…).
private val POSITIVE_MASKS = Regex(
    """\b(odoln\w*\s+proti\s+(skrab\w*|oder\w*)|ochran\w*\s+proti\s+(skrab\w*|oder\w*|prask\w*)|prask\w*\s+(ochrann\w*|tvrzen\w*)\s+skl\w*|(ochrann\w*|tvrzen\w*)\s+skl\w*\s+(je\s+|bylo\s+)?prask\w*|prask\w*\s+sklick\w*|sklick\w*\s+(je\s+|bylo\s+)?prask\w*|prask\w*\s+(kryt\w*|obal\w*|pouzdr\w*)|(kryt\w*|obal\w*|pouzdr\w*)\s+(je\s+)?prask\w*)""",
)

private val BATTERY = listOf(
    Regex("""(?:bater\w*|kondic\w*|zdravi|health|bh|kapacit\w*)\D{0,25}?(\d{2,3})\s*%"""),
    Regex("""(\d{2,3})\s*%\s*(?:bater\w*|kondic\w*|zdravi|health|bh|kapacit\w*)"""),
)
private val BATTERY_THRESHOLD_BEFORE = Regex("""\b(pod|nad|do|az|alespon|min\w*)\s*$""")

private val WARNINGS = listOf(
    Regex("""\b(neoriginal\w*|kompatibil\w*|aftermarket)\s+(displej\w*|display|baterie|baterii|dil\w*)|\b(vymen\w*|menen\w*|nov\w*)\s+(displej\w*|display)\b""") to "Vyměněný / neoriginální díl (mp.cz může snížit stav)",
    Regex("""\bservisni\s+(zprav\w*|hlasen\w*)|neznam\w*\s+(dil|displej|baterie)""") to "Hlášení o neznámém dílu",
    Regex("""\b(us|usa|americk\w*)\s+(verz\w*|model\w*)|\bjen\s+esim\b|\besim\s+only\b""") to "US verze (jen eSIM)",
    Regex("""\b(blokovan\w*|zamcen\w*)\s+na\s+(operator\w*|sit\w*)|\bsim\s*lock\w*""") to "Blokace na operátora",
    Regex("""\b(icloud|apple\s*id)\b""") to "Zmínka o iCloud / Apple ID — ověř odhlášení",
)

const val REPLICA_WARNING = "Replika / napodobenina"

fun estimateCondition(title: String, description: String): ConditionEstimate {
    val raw = "$title\n$description"
    val masked = POSITIVE_MASKS.replace(stripDiacritics(raw), " ")
    val signals = RULES.mapNotNull { rule ->
        // negace („bez škrábanců") ruší jen vady; „bez poškození, jako nový" je pořád „jako nový"
        val hit = if (rule.grade == Grade.A || rule.grade == Grade.APLUS) rule.re.find(masked)?.value else findUnnegated(rule.re, masked)
        hit?.let { Signal(rule.grade, rule.label, it) }
    }

    var batteryPct: Int? = null
    run outer@{
        for (re in BATTERY) for (m in re.findAll(masked)) {
            val v = m.groupValues[1].toInt()
            // „pod 80 %" je hranice (např. výměna baterie zdarma), ne stav baterie
            val before = masked.substring(0, m.groups[1]!!.range.first)
            if (v in 50..100 && !BATTERY_THRESHOLD_BEFORE.containsMatchIn(before)) {
                batteryPct = v
                return@outer
            }
        }
    }

    val warnings = WARNINGS.filter { (re, _) -> re.containsMatchIn(masked) }.map { it.second }.toMutableList()
    if (isReplica(raw)) warnings.add(0, REPLICA_WARNING)

    fun has(g: Grade) = signals.any { it.grade == g }
    val (grade, confidence) = when {
        has(Grade.D) -> Grade.D to Confidence.HIGH
        has(Grade.C) -> Grade.C to Confidence.HIGH
        // „jako nový, jen mikroškrábanec" = podle mp.cz už B
        has(Grade.B) -> Grade.B to if (has(Grade.A) || has(Grade.APLUS)) Confidence.MEDIUM else Confidence.HIGH
        has(Grade.APLUS) && !Regex("""\bjako\s+nov""").containsMatchIn(masked) -> Grade.APLUS to Confidence.MEDIUM
        has(Grade.A) || has(Grade.APLUS) -> Grade.A to Confidence.MEDIUM
        else -> Grade.B to Confidence.LOW // stav nepopsaný → konzervativně „použitý"
    }
    batteryPct?.let { if (it < 80) warnings += "Baterie $it % — pod 80 % může výkup snížit cenu" }

    return ConditionEstimate(grade, confidence, signals, batteryPct, warnings)
}

// ---------------------------------------------------------------------------
// Šum v inzerátech (port listing_rules.py z bazos-finder)
// ---------------------------------------------------------------------------
enum class Issue(val label: String, val excludes: Boolean) {
    SOLD("Prodáno", true),
    RESERVED("Rezervováno", true),
    RESERVATION_SALE("Prodej rezervace, ne telefonu", true),
    BUY_REQUEST("Poptávka (Koupím…)", true),
    NOT_PHONE("Není telefon", true),
    SUSPICIOUS_PRICE("Podezřele nízká cena", false),
}

private val SOLD = Regex("""prodano|\bprodan[ay]?\b|\bsold\b""")
private val RESERVED = Regex("""\bre[sz]ervovan\w*|\bre[sz]erv\b""")
private val RESERVATION_TITLE = Regex("""\bre[sz]ervac\w*""")
private val RESERVATION_SALE = Regex("""\b(prenecham|prodam|predam|nabizim|postoupim)\s+(?:\w+\s+){0,2}(?:re[sz]ervac|objednavk|predobjednavk)\w*""")
private val BUY_REQUEST = Regex("""^\W*(koupim|kupim|shanim|hledam|vykoupim|poptavk\w*|poptavam|vymenim|vymena)\b""")
private val OTHER_DEVICE = Regex("""\b(ipad|macbook|imac|mac\s*mini|mac\s*studio|apple\s*watch\w*|watch\w*|airpods?|air\s*pods?|airtag\w*|homepod\w*|apple\s*tv|ipod\w*|sluchatk\w*|pencil|stylus|tablet\w*|notebook\w*|hodink\w*)\b""")
private val ACCESSORY = Regex("""\b(kryt\w*|pouzdr\w*|obal\w*|navlek\w*|sklo|skla|sklicko|folie|case|krabic\w*|nabijec\w*|kabel\w*|drzak\w*|powerbank\w*|adapter\w*|protector\w*|reminek\w*|reminky|pasek|pasky|nahradni\s+dil\w*|magsafe\s+(penezenk\w*|nabijec\w*))\b""")
private val SPARE_PART = Regex("""\b(displej\w*|display|baterie|nahradni\s+d\w*|leather|sleeve|silikon\w*|magsafe)\b""")
private val FILLER = setOf("prodam", "prodej", "nabizim", "novy", "nova", "nove", "pouzity", "pouzita", "pouzite", "top", "super", "apple")

/** Problém inzerátu podle textu (bez ceny), nebo null. */
fun listingIssue(title: String, description: String): Issue? {
    val t = stripDiacritics(title)
    val d = stripDiacritics(description)
    if (SOLD.containsMatchIn(t)) return Issue.SOLD
    if (RESERVED.containsMatchIn(t)) return Issue.RESERVED
    if (RESERVATION_SALE.containsMatchIn(d) || RESERVATION_SALE.containsMatchIn(t) || RESERVATION_TITLE.containsMatchIn(t)) return Issue.RESERVATION_SALE
    if (BUY_REQUEST.containsMatchIn(t)) return Issue.BUY_REQUEST
    val first = t.split(Regex("[^a-z0-9]+")).firstOrNull { it.isNotEmpty() && it !in FILLER } ?: ""
    if (ACCESSORY.containsMatchIn(t) && first != "iphone" && !(first.isNotEmpty() && first.all(Char::isDigit))) return Issue.NOT_PHONE
    if (OTHER_DEVICE.containsMatchIn(t) && !Regex("""\bi\s?phone\b""").containsMatchIn(t)) return Issue.NOT_PHONE
    return null
}

/** Cena hluboko pod výkupem funkčního kusu: příslušenství/díl, nebo podezřelá cena (chyba, podvod, cena v popisu). */
fun priceIssue(title: String, price: Int?, lowestBuyout: Int?): Issue? {
    if (price == null || price == 0 || lowestBuyout == null || price >= 0.25 * lowestBuyout) return null
    val t = stripDiacritics(title)
    return if (ACCESSORY.containsMatchIn(t) || SPARE_PART.containsMatchIn(t)) Issue.NOT_PHONE else Issue.SUSPICIOUS_PRICE
}
