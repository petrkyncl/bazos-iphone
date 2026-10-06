package cz.petrkyncl.bazosiphony.domain

/**
 * Hledání jen v konkrétním městě. Bazoš umí jen PSČ + okruh, takže se hledá v okruhu, který město
 * celé pokryje, a výsledky se pak vyfiltrují podle lokality inzerátu. Lokality v API (ověřeno):
 * Praha → „Praha 1" … „Praha 10" (okolí je „Praha - západ/východ", Kladno…), ostatní města
 * přesný název („Brno" vs. „Brno venkov", „Plzeň" vs. „Plzeň-sever").
 */
data class City(val id: String, val name: String, val zip: String, val radiusKm: Int) {
    private val district = Regex("^${Regex.escape(name)} \\d+$")
    fun matches(locality: String?): Boolean = locality != null && (locality == name || district.matches(locality))
}

val CITIES = listOf(
    City("praha", "Praha", "11000", 25),
    City("brno", "Brno", "60200", 15),
    City("ostrava", "Ostrava", "70200", 15),
    City("plzen", "Plzeň", "30100", 12),
    City("olomouc", "Olomouc", "77900", 10),
    City("liberec", "Liberec", "46001", 10),
    City("budejovice", "České Budějovice", "37001", 10),
    City("hradec", "Hradec Králové", "50002", 10),
)

fun cityById(id: String?): City? = CITIES.firstOrNull { it.id == id }
