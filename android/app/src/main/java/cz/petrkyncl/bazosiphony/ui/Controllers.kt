package cz.petrkyncl.bazosiphony.ui

import androidx.compose.foundation.lazy.LazyListState
import cz.petrkyncl.bazosiphony.data.Account
import cz.petrkyncl.bazosiphony.data.Ad
import cz.petrkyncl.bazosiphony.data.Category
import cz.petrkyncl.bazosiphony.data.Deal
import cz.petrkyncl.bazosiphony.data.Filters
import cz.petrkyncl.bazosiphony.data.Listing
import cz.petrkyncl.bazosiphony.data.Repository
import cz.petrkyncl.bazosiphony.data.SellerProfile
import cz.petrkyncl.bazosiphony.domain.Grade
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Obrazovky v zásobníku jednotlivých záložek. Kontrolery drží stav, dokud je obrazovka v zásobníku. */
sealed interface Route {
    data object Home : Route
    data object Favorites : Route
    data object Watch : Route
    data object AccountScreen : Route
    data class Deals(val c: DealsController) : Route
    data class Listings(val c: ListingController, val searchMode: Boolean = false) : Route
    data class Detail(val c: DetailController) : Route
}

enum class Tab { BROWSE, SEARCH, DEALS, FAVORITES, WATCH }

/** Výpis inzerátů (rubrika, kategorie, výsledky hledání) + dotažení výkupu u iPhonů. */
class ListingController(
    private val scope: CoroutineScope,
    private val repo: Repository,
    initial: Filters,
    val savedSearchId: String? = null,
) {
    data class State(
        val filters: Filters,
        val items: List<Listing> = emptyList(),
        val page: Repository.MultiPage? = null, // offsety a počty po kategoriích
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val categories: List<Category> = emptyList(),
        val plus: Map<String, Ad> = emptyMap(), // iPhony s výkupem, podle ID inzerátu
    ) {
        val total get() = page?.total ?: 0
        val canLoadMore get() = page?.hasMore == true && !loading && !loadingMore
        val maxId get() = items.maxOfOrNull { it.id.toLongOrNull() ?: 0 } ?: 0
    }

    private val _state = MutableStateFlow(State(initial.migrated()))
    val state: StateFlow<State> = _state.asStateFlow()
    val listState = LazyListState()
    private var job: Job? = null

    init {
        loadCategories()
        refresh()
    }

    private fun loadCategories() {
        val section = _state.value.filters.section ?: return _state.update { it.copy(categories = emptyList()) }
        scope.launch {
            // hned v pořadí Bazoše, pak podle popularity (počty inzerátů, cache na týden)
            runCatching { repo.categories(section.id) }.onSuccess { cats -> _state.update { if (it.categories.isEmpty()) it.copy(categories = cats) else it } }
            runCatching { repo.categoriesByPopularity(section.id) }.onSuccess { cats -> _state.update { it.copy(categories = cats) } }
        }
    }

    fun refresh() = load(reset = true)

    fun loadMore() {
        if (_state.value.canLoadMore) load(reset = false)
    }

    fun setFilters(filters: Filters) {
        val sectionChanged = filters.section != _state.value.filters.section
        _state.update { it.copy(filters = filters, categories = if (sectionChanged) emptyList() else it.categories) }
        if (sectionChanged) loadCategories()
        refresh()
    }

    private fun load(reset: Boolean) {
        if (reset) job?.cancel() else if (job?.isActive == true) return
        val s = _state.value
        _state.update { it.copy(loading = reset, loadingMore = !reset, error = null, items = if (reset) emptyList() else it.items, page = if (reset) null else it.page) }
        job = scope.launch {
            try {
                val page = repo.search(s.filters, if (reset) null else s.page)
                _state.update { cur ->
                    cur.copy(
                        items = (if (reset) page.items else cur.items + page.items).distinctBy { it.id },
                        page = page,
                        loading = false,
                        loadingMore = false,
                    )
                }
                if (reset) listState.scrollToItem(0)
                // výkup pro iPhony ve výpisu dotáhnout na pozadí
                val plus = repo.enrichPhones(page.items)
                if (plus.isNotEmpty()) _state.update { it.copy(plus = it.plus + plus) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadingMore = false, error = e.message ?: e.toString()) }
            }
        }
    }
}

/** Záložka „Výkup": iPhony vyhodnocené proti výkupu mp.cz. */
class DealsController(private val scope: CoroutineScope, private val repo: Repository) {
    enum class Sort(val label: String, val api: String) {
        DATE("Nejnovější", "date"), PROFIT("Největší zisk", "date"), PRICE_ASC("Nejlevnější", "price_asc"), PRICE_DESC("Nejdražší", "price_desc")
    }

    data class State(
        val filters: Filters = Filters(priceFrom = "2000"),
        val sort: Sort = Sort.DATE,
        val onlyGood: Boolean = false,
        val hideExcluded: Boolean = true,
        val ads: List<Ad> = emptyList(),
        val total: Int = 0,
        val nextOffset: Int = 0,
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val pricelistUpdatedAt: Long? = null,
    ) {
        val visible: List<Ad>
            get() {
                var list = ads
                if (hideExcluded) list = list.filter { it.deal != Deal.EXCLUDED }
                if (onlyGood) list = list.filter { it.deal == Deal.GOOD }
                if (sort == Sort.PROFIT) list = list.sortedByDescending { it.profit ?: Int.MIN_VALUE }
                return list
            }
        val canLoadMore get() = nextOffset < total && !loading && !loadingMore
        val goodCount get() = ads.count { it.deal == Deal.GOOD }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    val listState = LazyListState()
    private var job: Job? = null

    init { refresh() }

    fun refresh() = load(true)
    fun loadMore() { if (_state.value.canLoadMore) load(false) }

    private fun load(reset: Boolean) {
        if (reset) job?.cancel() else if (job?.isActive == true) return
        val s = _state.value
        val offset = if (reset) 0 else s.nextOffset
        _state.update { it.copy(loading = reset, loadingMore = !reset, error = null) }
        job = scope.launch {
            try {
                val (ads, total, pricelistAt) = repo.phoneDeals(s.filters.copy(sort = s.sort.api), offset)
                _state.update { cur ->
                    cur.copy(
                        ads = (if (reset) ads else cur.ads + ads).distinctBy { it.id },
                        total = total, nextOffset = offset + Repository.PAGE_SIZE,
                        loading = false, loadingMore = false, pricelistUpdatedAt = pricelistAt,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadingMore = false, error = e.message ?: e.toString()) }
            }
        }
    }

    fun setFilters(f: Filters) { _state.update { it.copy(filters = f) }; refresh() }
    fun setSort(sort: Sort) {
        val reload = sort.api != _state.value.sort.api
        _state.update { it.copy(sort = sort) }
        if (reload) refresh()
    }
    fun setOnlyGood(v: Boolean) = _state.update { it.copy(onlyGood = v) }
    fun setHideExcluded(v: Boolean) = _state.update { it.copy(hideExcluded = v) }

    fun refreshPricelist() = scope.launch {
        runCatching { repo.refreshPricelist() }
            .onSuccess { refresh() }
            .onFailure { e -> _state.update { it.copy(error = "Ceník mp.cz: ${e.message}") } }
    }
}

/** Detail inzerátu: data, prodejce, ruční přepnutí stavu, telefon prodejce. */
class DetailController(
    private val scope: CoroutineScope,
    private val repo: Repository,
    private val account: Account,
    val adId: String,
    val preview: Listing?, // z výpisu — ukáže se hned, než dorazí detail
) {
    data class State(
        val ad: Ad? = null,
        val error: String? = null,
        val grade: Grade? = null, // ručně zvolený stav (jinak odhad)
        val seller: SellerProfile? = null,
        val sellerLoading: Boolean = true,
        val phone: String? = null,
        val phoneLoading: Boolean = false,
        val phoneError: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(error = null) }
        scope.launch {
            try {
                val ad = repo.ad(adId)
                // telefon hned: z paměti zdarma, jinak automaticky jen pod denním limitem
                val known = account.cachedPhone(ad.seller.id, adId)
                _state.update { it.copy(ad = ad, phone = known) }
                if (known == null && account.canAutoReveal()) revealPhone()
                val seller = runCatching { repo.seller(ad) }.getOrNull()
                _state.update { it.copy(seller = seller, sellerLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: e.toString(), sellerLoading = false) }
            }
        }
    }

    fun setGrade(g: Grade) = _state.update { it.copy(grade = g) }

    fun revealPhone() {
        _state.update { it.copy(phoneLoading = true, phoneError = null) }
        scope.launch {
            runCatching { account.sellerPhone(adId, _state.value.ad?.seller?.id) }
                .onSuccess { p -> _state.update { it.copy(phone = p, phoneLoading = false) } }
                .onFailure { e -> _state.update { it.copy(phoneError = e.message, phoneLoading = false) } }
        }
    }
}

/** Ověření čísla (dva kroky: SMS → kód). */
class AccountController(private val scope: CoroutineScope, private val account: Account) {
    data class State(val codeSent: Boolean = false, val busy: Boolean = false, val error: String? = null, val info: String? = null)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    val accountState get() = account.state
    val phoneStats get() = account.phoneStats
    fun setAutoReveal(on: Boolean) = account.setAutoReveal(on)

    fun requestCode(phone: String) = run({ account.requestCode(phone) }) { it.copy(codeSent = true, info = "SMS s kódem je na cestě.") }
    fun verify(code: String) = run({ account.verify(code) }) { it.copy(codeSent = false, info = "Číslo je ověřené.") }
    fun logout() { account.logout(); _state.value = State() }
    fun restart() { _state.value = State() }

    private fun run(action: suspend () -> Unit, onOk: (State) -> State) {
        _state.update { it.copy(busy = true, error = null, info = null) }
        scope.launch {
            runCatching { action() }
                .onSuccess { _state.update { onOk(it.copy(busy = false)) } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message ?: e.toString()) } }
        }
    }
}
