package cz.petrkyncl.bazosiphony.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.NotificationAdd
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.petrkyncl.bazosiphony.data.Filters
import cz.petrkyncl.bazosiphony.domain.CITIES
import cz.petrkyncl.bazosiphony.domain.cityById
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import cz.petrkyncl.bazosiphony.data.Listing
import cz.petrkyncl.bazosiphony.data.Repository
import cz.petrkyncl.bazosiphony.data.Section
import cz.petrkyncl.bazosiphony.data.Suggestions
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingScreen(
    c: ListingController,
    searchMode: Boolean,
    canGoBack: Boolean,
    repo: Repository,
    favoriteIds: Set<String>,
    watched: Boolean,
    onBack: () -> Unit,
    onOpen: (Listing) -> Unit,
    onFavorite: (Listing) -> Unit,
    onWatch: (Filters, Long) -> Unit,
) {
    val state by c.state.collectAsState()
    val f = state.filters
    var showFilters by remember { mutableStateOf(false) }
    var query by remember(f.query) { mutableStateOf(f.query) }
    var focused by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<Suggestions?>(null) }
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    // našeptávač s malým zpožděním, ať se neptáme na každé písmeno
    LaunchedEffect(query, focused) {
        suggestions = null
        if (!focused || query.trim().length < 2) return@LaunchedEffect
        delay(250)
        suggestions = runCatching { repo.suggestions(query.trim()) }.getOrNull()
    }

    // nekonečné scrollování
    val nearEnd by remember {
        derivedStateOf {
            val info = c.listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.canLoadMore) { if (nearEnd && state.canLoadMore && state.items.isNotEmpty()) c.loadMore() }

    fun submit(newFilters: Filters) {
        focus.clearFocus()
        c.setFilters(newFilters)
    }

    val title = when {
        searchMode -> "Hledat na Bazoši"
        f.categoriesLabel != null -> f.categoriesLabel!!
        f.section != null -> f.section.title
        else -> "Inzeráty"
    }

    Scaffold(
        contentWindowInsets = insetsWithoutBottom(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, maxLines = 1)
                            if (state.total > 0) {
                                Text(
                                    (cityById(f.city)?.let { "jen ${it.name}" } ?: "${number(state.total)} inzerátů") +
                                        if (f.categories.isNotEmpty() && f.section != null) " · ${f.section.title}" else "",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = { if (canGoBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } },
                    actions = {
                        ViewModeButton()
                        IconButton(onClick = { onWatch(f, state.maxId) }, enabled = !watched) {
                            Icon(
                                if (watched) Icons.Default.NotificationsActive else Icons.Default.NotificationAdd,
                                if (watched) "Hlídá se" else "Hlídat nové inzeráty",
                                tint = if (watched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused },
                    placeholder = { Text(if (f.section != null) "Hledat v rubrice ${f.section.title}" else "Co hledáš?") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = ""; submit(f.copy(query = "")) }) { Icon(Icons.Default.Clear, "Smazat") }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit(f.copy(query = query)) }),
                )
                if (state.categories.isNotEmpty() && !focused) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        item {
                            FilterChip(selected = f.categories.isEmpty(), onClick = { submit(f.copy(categories = emptyList())) }, label = { Text("Vše") })
                        }
                        // víc kategorií najednou: ťuknutí přidá/odebere
                        items(state.categories, key = { it.id }) { cat ->
                            val on = cat in f.categories
                            FilterChip(
                                selected = on,
                                onClick = { submit(f.copy(categories = if (on) f.categories - cat else f.categories + cat)) },
                                label = { Text(cat.title + (cat.count?.let { " · ${compactCount(it)}" } ?: "")) },
                                leadingIcon = if (on) ({ Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                            )
                        }
                    }
                }
                if (!focused) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = f.activeCount > 0, onClick = { showFilters = true },
                            label = { Text(if (f.activeCount > 0) "Filtry (${f.activeCount})" else "Filtry") },
                            leadingIcon = { Icon(Icons.Default.FilterList, null, Modifier.size(FilterChipDefaults.IconSize)) },
                        )
                        SortChip(f.sort, SORTS) { submit(f.copy(sort = it)) }
                        if (f.section?.id == "MO" || f.models.isNotEmpty()) {
                            FilterChip(
                                selected = f.hideNoise, onClick = { submit(f.copy(hideNoise = !f.hideNoise)) },
                                label = { Text("Bez šumu") },
                                leadingIcon = if (f.hideNoise) ({ Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                            )
                        }
                        // konkrétní modely: v Mobilech (Apple / bez značky) nebo když už jsou vybrané
                        if (f.models.isNotEmpty() || (f.section?.id == "MO" && (f.categories.isEmpty() || f.categories.any { it.id == "439" }))) {
                            ModelsChip(f, repo) { submit(it) }
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (focused && (suggestions != null || query.isNotBlank())) {
            SuggestionList(
                suggestions, query, Modifier.padding(padding),
                onKeyword = { kw -> query = kw; submit(f.copy(query = kw)) },
                onCategory = { sec, cat -> submit(f.copy(query = query, section = sec, categories = listOf(cat))) },
                onSection = { sec -> submit(f.copy(query = query, section = sec, categories = emptyList())) },
                onSearch = { submit(f.copy(query = query)) },
            )
            return@Scaffold
        }
        PullToRefreshBox(
            isRefreshing = state.loading && state.items.isNotEmpty(),
            onRefresh = c::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                state = c.listState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                state.error?.let { item { CenterNote("Chyba: $it", action = "Zkusit znovu" to c::refresh) } }
                if (state.page?.topFallback == true) {
                    item {
                        Text(
                            "Tady je topovaných inzerátů tisíce, proto jsou nahoře jako na Bazoši. Bez TOPu to řadí v rubrice nebo s hledaným výrazem.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                }
                if (state.loading && state.items.isEmpty()) item { CenterNote("Načítám inzeráty…", progress = true) }
                items(state.items, key = { it.id }) { l ->
                    ListingCard(l, null, l.id in favoriteIds, onFavorite = { onFavorite(l) }, onClick = { onOpen(l) })
                }
                if (!state.loading && state.error == null && state.items.isEmpty()) item { CenterNote("Nic nenalezeno.") }
                if (state.loadingMore) item { CenterNote("Načítám další…", progress = true) }
            }
        }
    }

    if (showFilters) FilterSheet(f, onDismiss = { showFilters = false }) { showFilters = false; submit(it) }
}

val SORTS = listOf(
    Filters.SORT_NEWEST to "Nejnovější vložené",
    "date" to "Nejnovější vč. TOP",
    "price_asc" to "Nejlevnější",
    "price_desc" to "Nejdražší",
)

@Composable
fun SortChip(current: String, options: List<Pair<String, String>>, onSort: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        FilterChip(
            selected = current != options.first().first, onClick = { open = true },
            label = { Text(options.firstOrNull { it.first == current }?.second ?: options.first().second) },
            leadingIcon = { Icon(Icons.Default.SwapVert, null, Modifier.size(FilterChipDefaults.IconSize)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = if (key == current) ({ Icon(Icons.Default.Check, null) }) else null,
                    onClick = { open = false; onSort(key) },
                )
            }
        }
    }
}

@Composable
private fun SuggestionList(
    s: Suggestions?, query: String, modifier: Modifier,
    onKeyword: (String) -> Unit, onCategory: (Section, cz.petrkyncl.bazosiphony.data.Category) -> Unit,
    onSection: (Section) -> Unit, onSearch: () -> Unit,
) {
    LazyColumn(modifier.fillMaxSize()) {
        if (query.isNotBlank()) item { SuggestionRow(Icons.Default.Search, "Hledat „$query“", null, onSearch) }
        s?.categories?.let { cats ->
            items(cats) { (sec, cat) -> SuggestionRow(Icons.Default.Category, "„$query“ v ${cat.title}", sec.title) { onCategory(sec, cat) } }
        }
        s?.sections?.let { secs -> items(secs) { sec -> SuggestionRow(Icons.Default.Category, "„$query“ v rubrice ${sec.title}", null) { onSection(sec) } } }
        s?.keywords?.let { kws -> items(kws) { kw -> SuggestionRow(Icons.Default.Search, kw, null) { onKeyword(kw) } } }
    }
}

@Composable
private fun SuggestionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, sub: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.padding(start = 16.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(initial: Filters, onDismiss: () -> Unit, onApply: (Filters) -> Unit) {
    var f by remember { mutableStateOf(initial) }
    val distances = listOf(5, 10, 25, 50, 100)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Filtry", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    f.priceFrom, { f = f.copy(priceFrom = it.filter(Char::isDigit)) }, Modifier.weight(1f),
                    label = { Text("Cena od") }, suffix = { Text("Kč") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    f.priceTo, { f = f.copy(priceTo = it.filter(Char::isDigit)) }, Modifier.weight(1f),
                    label = { Text("Cena do") }, suffix = { Text("Kč") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            // lokalita: celá ČR / jen město / vlastní PSČ + okruh
            var customZip by remember { mutableStateOf(f.city == null && f.zip.isNotBlank()) }
            Text("Lokalita", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FilterChip(
                    selected = f.city == null && !customZip, label = { Text("Celá ČR") },
                    onClick = { customZip = false; f = f.copy(city = null, zip = "") },
                )
                CITIES.forEach { city ->
                    FilterChip(
                        selected = f.city == city.id, label = { Text(city.name) },
                        onClick = { customZip = false; f = f.copy(city = city.id, zip = "") },
                    )
                }
                FilterChip(selected = customZip, label = { Text("Vlastní PSČ") }, onClick = { customZip = true; f = f.copy(city = null) })
            }
            if (f.city != null) {
                Text(
                    "Jen inzeráty z města ${cityById(f.city)?.name} — bez okolí.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (customZip) OutlinedTextField(
                f.zip, { f = f.copy(zip = it.filter { ch -> ch.isDigit() || ch == ' ' }.take(6)) }, Modifier.fillMaxWidth(),
                label = { Text("PSČ") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            if (customZip && f.zip.isNotBlank()) {
                Text("Okruh", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    distances.forEachIndexed { i, km ->
                        SegmentedButton(
                            selected = f.distanceKm == km, onClick = { f = f.copy(distanceKm = km) },
                            shape = SegmentedButtonDefaults.itemShape(i, distances.size),
                        ) { Text("$km") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { f = f.copy(priceFrom = "", priceTo = "", zip = "", city = null) }, Modifier.weight(1f)) { Text("Vymazat") }
                Button(onClick = { onApply(f) }, Modifier.weight(1f)) { Text("Použít") }
            }
        }
    }
}

/** 6223 → „6,2 tis.", 845 → „845". */
fun compactCount(n: Int): String = when {
    n >= 10_000 -> "${n / 1000} tis."
    n >= 1_000 -> "%.1f tis.".format(java.util.Locale.forLanguageTag("cs-CZ"), n / 1000.0)
    else -> n.toString()
}
