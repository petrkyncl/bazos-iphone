package cz.petrkyncl.bazosiphony.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.petrkyncl.bazosiphony.data.Account
import cz.petrkyncl.bazosiphony.data.Category
import cz.petrkyncl.bazosiphony.data.FavoritesStore
import cz.petrkyncl.bazosiphony.data.Filters
import cz.petrkyncl.bazosiphony.data.Listing
import cz.petrkyncl.bazosiphony.data.Money
import cz.petrkyncl.bazosiphony.data.Repository
import cz.petrkyncl.bazosiphony.data.SavedSearchStore
import cz.petrkyncl.bazosiphony.data.Section
import cz.petrkyncl.bazosiphony.data.WatchWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repository.get(app)
    val favorites = FavoritesStore.get(app)
    val savedSearches = SavedSearchStore.get(app)
    val account = Account.get(app)

    private val _sections = MutableStateFlow<List<Section>>(emptyList())
    val sections = _sections.asStateFlow()
    private val _sectionsError = MutableStateFlow<String?>(null)
    val sectionsError = _sectionsError.asStateFlow()
    // musí být deklarované před init: loadSections() z cache na disku doběhne synchronně
    private val _categories = MutableStateFlow<Map<String, List<Category>>>(emptyMap())
    val categories = _categories.asStateFlow()

    var tab by mutableStateOf(Tab.BROWSE)
        private set

    /** Každá záložka má vlastní historii obrazovek. */
    private val stacks: Map<Tab, SnapshotStateList<Route>> = mapOf(
        Tab.BROWSE to mutableStateListOf(Route.Home),
        Tab.SEARCH to mutableStateListOf(Route.Listings(ListingController(viewModelScope, repo, Filters()), searchMode = true)),
        Tab.DEALS to mutableStateListOf(Route.Deals(DealsController(viewModelScope, repo))),
        Tab.FAVORITES to mutableStateListOf(Route.Favorites),
        Tab.WATCH to mutableStateListOf(Route.Watch),
    )

    val stack: List<Route> get() = stacks.getValue(tab)
    val current: Route get() = stack.last()
    val canGoBack get() = stack.size > 1 || tab != Tab.BROWSE

    init {
        loadSections()
        viewModelScope.launch { Money.init(app) }
        WatchWorker.schedule(app)
    }

    fun loadSections() = viewModelScope.launch {
        _sectionsError.value = null
        runCatching { repo.sections() }
            .onSuccess { secs ->
                _sections.value = secs
                // u rubrik, které už uživatel otevřel, rovnou podle popularity (z cache)
                _categories.value = repo.allCategories(secs)
            }
            .onFailure { _sectionsError.value = it.message }
    }

    fun selectTab(t: Tab) {
        // opakované ťuknutí na aktivní záložku = návrat na její začátek
        if (t == tab) stacks.getValue(t).let { s -> while (s.size > 1) s.removeAt(s.lastIndex) }
        tab = t
        if (t == Tab.WATCH) savedSearches.reload()
    }

    fun back(): Boolean {
        val s = stacks.getValue(tab)
        return when {
            s.size > 1 -> { s.removeAt(s.lastIndex); true }
            tab != Tab.BROWSE -> { tab = Tab.BROWSE; true }
            else -> false
        }
    }

    private fun push(r: Route) { stacks.getValue(tab).add(r) }

    fun openFilters(filters: Filters, savedSearchId: String? = null) =
        push(Route.Listings(ListingController(viewModelScope, repo, filters, savedSearchId)))

    fun openAd(id: String, preview: Listing? = null) =
        push(Route.Detail(DetailController(viewModelScope, repo, account, id, preview)))

    fun openAccount() = push(Route.AccountScreen)

    /** Nové hledání v záložce Hledat (např. z domovské obrazovky). */
    fun search(filters: Filters) {
        tab = Tab.SEARCH
        val s = stacks.getValue(Tab.SEARCH)
        while (s.size > 1) s.removeAt(s.lastIndex)
        (s.first() as Route.Listings).c.setFilters(filters)
    }

    fun openSavedSearch(id: String) {
        val saved = savedSearches.items.value.firstOrNull { it.id == id } ?: return
        tab = Tab.WATCH
        val s = stacks.getValue(Tab.WATCH)
        while (s.size > 1) s.removeAt(s.lastIndex)
        openFilters(saved.filters, saved.id)
    }

    fun saveSearch(filters: Filters, currentMaxId: Long) {
        savedSearches.add(filters, currentMaxId)
        WatchWorker.schedule(getApplication())
    }

    fun removeSearch(id: String) {
        savedSearches.remove(id)
        WatchWorker.schedule(getApplication())
    }

    fun toggleFavorite(listing: Listing) = favorites.toggle(listing)

    val accountController by lazy { AccountController(viewModelScope, account) }
}
