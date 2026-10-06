package cz.petrkyncl.bazosiphony.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

/** Seznam uložený jako JSON v SharedPreferences, vystavený jako StateFlow. */
abstract class JsonListStore<T>(context: Context, private val key: String, private val serializer: KSerializer<T>) {
    private val prefs = context.applicationContext.getSharedPreferences("store", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<T>> = _items.asStateFlow()

    private fun load(): List<T> = runCatching {
        Http.json.decodeFromString(ListSerializer(serializer), prefs.getString(key, null) ?: "[]")
    }.getOrDefault(emptyList())

    /** Znovu načíst z disku (zapisuje i worker na pozadí). */
    fun reload() { _items.value = load() }

    protected fun update(fn: (List<T>) -> List<T>) {
        _items.update(fn)
        prefs.edit { putString(key, Http.json.encodeToString(ListSerializer(serializer), _items.value)) }
    }
}

class FavoritesStore private constructor(context: Context) : JsonListStore<Listing>(context, "favorites", Listing.serializer()) {
    fun isFavorite(id: String) = items.value.any { it.id == id }

    fun toggle(listing: Listing) = update { list ->
        if (list.any { it.id == listing.id }) list.filterNot { it.id == listing.id } else listOf(listing) + list
    }

    companion object {
        @Volatile private var instance: FavoritesStore? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: FavoritesStore(context).also { instance = it } }
    }
}

class SavedSearchStore private constructor(context: Context) : JsonListStore<SavedSearch>(context, "saved_searches", SavedSearch.serializer()) {
    init {
        // starý formát (jedna kategorie) → seznam kategorií
        if (items.value.any { it.filters != it.filters.migrated() }) update { list -> list.map { it.copy(filters = it.filters.migrated()) } }
    }

    fun find(filters: Filters) = items.value.firstOrNull { it.filters.watchKey() == filters.watchKey() }

    fun add(filters: Filters, currentMaxId: Long) = update { list ->
        val f = filters.watchKey() // hlídá se vždy podle nejnovějších
        if (list.any { it.filters.watchKey() == f }) list else list + SavedSearch(UUID.randomUUID().toString(), f, lastSeenMaxId = currentMaxId)
    }

    fun remove(id: String) = update { list -> list.filterNot { it.id == id } }

    fun setNotify(id: String, notify: Boolean) = update { list -> list.map { if (it.id == id) it.copy(notify = notify) else it } }

    /** Uživatel hledání otevřel → nové inzeráty už viděl. */
    fun markSeen(id: String, maxId: Long) = update { list ->
        list.map { if (it.id == id) it.copy(lastSeenMaxId = maxOf(it.lastSeenMaxId, maxId), newCount = 0) else it }
    }

    fun setNewCount(id: String, count: Int) = update { list -> list.map { if (it.id == id) it.copy(newCount = count) else it } }

    companion object {
        @Volatile private var instance: SavedSearchStore? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: SavedSearchStore(context).also { instance = it } }
    }
}
