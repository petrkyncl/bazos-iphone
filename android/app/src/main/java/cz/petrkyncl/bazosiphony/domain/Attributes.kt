package cz.petrkyncl.bazosiphony.domain

/**
 * Doplňkové údaje telefonu pro normalizovanou kartu: barva a záruka.
 * Barva se bere z nadpisu, z popisu jen u „barva: …" (jinak by se chytaly barvy krytů apod.).
 */
private val COLORS = listOf(
    // titanové varianty dřív než obecné barvy („natural titanium", „black titanium")
    """(natural|prirodni)\s+titan\w*|titan\w*\s+prirodni""" to "přírodní titan",
    """(desert|pouštní|pouštni|pousti|poustni)\s+titan\w*""" to "pouštní titan",
    """(black|cern\w*)\s+titan\w*""" to "černý titan",
    """(white|bil\w*)\s+titan\w*""" to "bílý titan",
    """(blue|modr\w*)\s+titan\w*""" to "modrý titan",
    """space\s+(black|gr[ae]y)|vesmirne?\s+sed\w*""" to "vesmírně šedá",
    """cern\w*|black|midnight|graphite|grafit\w*""" to "černá",
    """bil\w*|white|starlight""" to "bílá",
    """stribrn\w*|silver""" to "stříbrná",
    """zlat\w*|gold""" to "zlatá",
    """modr\w*|blue|ultramarine|teal|pacific""" to "modrá",
    """cerven\w*|red\b|product\s*red""" to "červená",
    """zelen\w*|green|alpine""" to "zelená",
    """fialov\w*|purple|lavender|levandulov\w*""" to "fialová",
    """ruzov\w*|pink""" to "růžová",
    """zlut\w*|yellow""" to "žlutá",
    """oranzov\w*|orange""" to "oranžová",
    """titan\w*""" to "titan",
    """sed\w*|gr[ae]y""" to "šedá",
).map { (re, name) -> Regex("""\b($re)""") to name }

fun extractColor(title: String, description: String): String? {
    val t = stripDiacritics(title)
    COLORS.firstOrNull { (re, _) -> re.containsMatchIn(t) }?.let { return it.second }
    // z popisu jen výslovně uvedená barva
    val d = stripDiacritics(description)
    val m = Regex("""\bbarv\w*\s*[:\-]?\s*([a-z ]{3,30})""").find(d) ?: return null
    return COLORS.firstOrNull { (re, _) -> re.containsMatchIn(m.groupValues[1]) }?.second
}

private val WARRANTY = Regex("""\bzaru[kc]\w*""") // záruka, záruky, v záruce…
private val WARRANTY_UNTIL = Regex("""\bzaru[kc]\w*[^.\n]{0,40}?\bdo\s+(\d{1,2})\s*[./]\s*(\d{4}|\d{2})\b""")

/** „záruka do 01/2028", „záruka", nebo null. */
fun extractWarranty(title: String, description: String): String? {
    val text = "$title\n$description"
    if (findUnnegated(WARRANTY, text) == null) return null // „bez záruky" se nepočítá
    val m = WARRANTY_UNTIL.find(stripDiacritics(text)) ?: return "záruka"
    val month = m.groupValues[1].padStart(2, '0')
    val year = m.groupValues[2].let { if (it.length == 2) "20$it" else it }
    return if (month.toInt() in 1..12) "záruka do $month/$year" else "záruka"
}

/** Rychlý náhled z nadpisu (než dorazí detail): „iPhone 15 Pro Max · 256 GB". */
fun phoneHeadline(key: IphoneKey): String =
    modelLabel(key) + (key.storageGb?.let { " · " + if (it >= 1024) "${it / 1024} TB" else "$it GB" } ?: "")
