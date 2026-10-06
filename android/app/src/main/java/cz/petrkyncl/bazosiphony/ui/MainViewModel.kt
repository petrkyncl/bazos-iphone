package cz.petrkyncl.bazosiphony.ui

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.petrkyncl.bazosiphony.data.Ad
import cz.petrkyncl.bazosiphony.data.AdRepository
import cz.petrkyncl.bazosiphony.data.BazosApi
import cz.petrkyncl.bazosiphony.data.Deal
import cz.petrkyncl.bazosiphony.data.SellerProfile
import cz.petrkyncl.bazosiphony.domain.Grade
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortMode(val label: String, val api: String) {
    DATE("Nejnovější", "date"),
    PROFIT("Největší zisk", "date"), // řadí se lokálně z načtených
    PRICE_ASC("Nejlevnější", "price_asc"),
    PRICE_DESC("Nejdražší", "price_desc"),
}

data class ListState(
    val filters: BazosApi.Filters = BazosApi.Filters(),
    val sort: SortMode = SortMode.DATE,
    val onlyGood: Boolean = false,
    val hideExcluded: Boolean = true,
    val ads: List<Ad> = emptyList(),
    val total: Int = 0,
    val nextOffset: Int = 0,
    val loading: Boolean = false, // první stránka / obnovení
    val loadingMore: Boolean = false,
    val error: String? = null,
    val pricelistUpdatedAt: Long? = null,
) {
    val visible: List<Ad>
        get() {
            var list = ads
            if (hideExcluded) list = list.filter { it.deal != Deal.EXCLUDED }
            if (onlyGood) list = list.filter { it.deal == Deal.GOOD }
            if (sort == SortMode.PROFIT) list = list.sortedByDescending { it.profit ?: Int.MIN_VALUE }
            return list
        }
    val canLoadMore get() = nextOffset < total && !loading && !loadingMore
    val goodCount get() = ads.count { it.deal == Deal.GOOD }
}

data class DetailState(
    val ad: Ad,
    val grade: Grade = ad.condition.grade, // uživatel může stav přepnout podle fotek
    val seller: SellerProfile? = null,
    val sellerLoading: Boolean = true,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AdRepository(app)
    private val prefs = app.getSharedPreferences("filters", Context.MODE_PRIVATE)

    private val _list = MutableStateFlow(loadPrefs())
    val list: StateFlow<ListState> = _list.asStateFlow()

    private val _detail = MutableStateFlow<DetailState?>(null)
    val detail: StateFlow<DetailState?> = _detail.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() = load(reset = true)

    fun loadMore() {
        if (_list.value.canLoadMore) load(reset = false)
    }

    private fun load(reset: Boolean) {
        if (reset) loadJob?.cancel() else if (loadJob?.isActive == true) return
        val s = _list.value
        val offset = if (reset) 0 else s.nextOffset
        _list.update { it.copy(loading = reset, loadingMore = !reset, error = null) }
        loadJob = viewModelScope.launch {
            runCatching { repo.page(s.filters.copy(sort = s.sort.api), offset) }
                .onSuccess { page ->
                    _list.update { cur ->
                        val merged = if (reset) page.ads else cur.ads + page.ads
                        cur.copy(
                            ads = merged.distinctBy { it.id },
                            total = page.total,
                            nextOffset = offset + AdRepository.PAGE_SIZE,
                            loading = false,
                            loadingMore = false,
                            pricelistUpdatedAt = page.pricelistUpdatedAt,
                        )
                    }
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _list.update { it.copy(loading = false, loadingMore = false, error = e.message ?: e.toString()) }
                }
        }
    }

    fun applyFilters(filters: BazosApi.Filters) {
        _list.update { it.copy(filters = filters) }
        savePrefs()
        refresh()
    }

    fun setSort(sort: SortMode) {
        val needsReload = sort.api != _list.value.sort.api
        _list.update { it.copy(sort = sort) }
        savePrefs()
        if (needsReload) refresh()
    }

    fun setOnlyGood(v: Boolean) { _list.update { it.copy(onlyGood = v) }; savePrefs() }
    fun setHideExcluded(v: Boolean) { _list.update { it.copy(hideExcluded = v) }; savePrefs() }

    fun refreshPricelist() = viewModelScope.launch {
        runCatching { repo.refreshPricelist() }
            .onSuccess { refresh() }
            .onFailure { e -> _list.update { it.copy(error = "Ceník mp.cz: ${e.message}") } }
    }

    fun open(ad: Ad) {
        _detail.value = DetailState(ad)
        viewModelScope.launch {
            val seller = runCatching { repo.seller(ad) }.getOrNull()
            _detail.update { if (it?.ad?.id == ad.id) it.copy(seller = seller, sellerLoading = false) else it }
        }
    }

    fun close() { _detail.value = null }

    fun setGrade(grade: Grade) = _detail.update { it?.copy(grade = grade) }

    private fun loadPrefs(): ListState = ListState(
        filters = BazosApi.Filters(
            query = prefs.getString("query", "") ?: "",
            priceFrom = prefs.getString("priceFrom", "2000") ?: "",
            priceTo = prefs.getString("priceTo", "") ?: "",
            zip = prefs.getString("zip", "") ?: "",
            distanceKm = prefs.getInt("distance", 25),
        ),
        sort = runCatching { SortMode.valueOf(prefs.getString("sort", "DATE")!!) }.getOrDefault(SortMode.DATE),
        onlyGood = prefs.getBoolean("onlyGood", false),
        hideExcluded = prefs.getBoolean("hideExcluded", true),
    )

    private fun savePrefs() {
        val s = _list.value
        prefs.edit {
            putString("query", s.filters.query)
            putString("priceFrom", s.filters.priceFrom)
            putString("priceTo", s.filters.priceTo)
            putString("zip", s.filters.zip)
            putInt("distance", s.filters.distanceKm)
            putString("sort", s.sort.name)
            putBoolean("onlyGood", s.onlyGood)
            putBoolean("hideExcluded", s.hideExcluded)
        }
    }
}
