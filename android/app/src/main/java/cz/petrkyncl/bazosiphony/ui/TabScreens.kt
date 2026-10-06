package cz.petrkyncl.bazosiphony.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.petrkyncl.bazosiphony.data.Ad
import cz.petrkyncl.bazosiphony.data.Listing
import cz.petrkyncl.bazosiphony.data.SavedSearch

// ---------------------------------------------------------------------------
// Výkup iPhonů
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealsScreen(c: DealsController, repo: cz.petrkyncl.bazosiphony.data.Repository, onOpen: (Ad) -> Unit) {
    val s by c.state.collectAsState()
    var showFilters by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var query by remember(s.filters.query) { mutableStateOf(s.filters.query) }
    val focus = LocalFocusManager.current
    val visible = s.visible

    val nearEnd by remember {
        derivedStateOf {
            val info = c.listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, s.canLoadMore) { if (nearEnd && s.canLoadMore && s.ads.isNotEmpty()) c.loadMore() }

    Scaffold(
        contentWindowInsets = insetsWithoutBottom(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Výkup iPhonů")
                            Text(
                                buildString {
                                    val city = cz.petrkyncl.bazosiphony.domain.cityById(s.filters.city)
                                    if (city != null) append("jen ${city.name} · výhodných ${s.goodCount}")
                                    else if (s.total > 0) append("${number(s.total)} inzerátů · výhodných ${s.goodCount}")
                                    s.pricelistUpdatedAt?.let { append(" · ceník mp.cz ${ago(it)}") }
                                },
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        ViewModeButton()
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Aktualizovat ceník mp.cz") }, onClick = { showMenu = false; c.refreshPricelist() })
                        }
                    },
                )
                TextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    placeholder = { Text("Model (iphone 15 pro, 13 mini…)") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true, shape = MaterialTheme.shapes.extraLarge,
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); c.setFilters(s.filters.copy(query = query)) }),
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = s.filters.activeCount > 0, onClick = { showFilters = true },
                        label = { Text(if (s.filters.activeCount > 0) "Filtry (${s.filters.activeCount})" else "Filtry") },
                        leadingIcon = { Icon(Icons.Default.FilterList, null, Modifier.size(FilterChipDefaults.IconSize)) },
                    )
                    ModelsChip(s.filters, repo) { c.setFilters(it) }
                    SortChip(s.sort.name, DealsController.Sort.entries.map { it.name to it.label + if (it == DealsController.Sort.PROFIT) " (z načtených)" else "" }) {
                        c.setSort(DealsController.Sort.valueOf(it))
                    }
                    CheckChip("Jen výhodné", s.onlyGood) { c.setOnlyGood(!s.onlyGood) }
                    CheckChip("Skrýt prodané a šum", s.hideExcluded) { c.setHideExcluded(!s.hideExcluded) }
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = s.loading && s.ads.isNotEmpty(), onRefresh = c::refresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                state = c.listState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                s.error?.let { item { CenterNote("Chyba: $it", action = "Zkusit znovu" to c::refresh) } }
                if (s.loading && s.ads.isEmpty()) item { CenterNote("Načítám iPhony a výkupní ceny…", progress = true) }
                items(visible, key = { it.id }) { ad -> DealCard(ad, s.marketFor(ad)) { onOpen(ad) } }
                if (!s.loading && s.ads.isNotEmpty() && visible.isEmpty()) item { CenterNote("V načtených nic neodpovídá filtru — sjeď níž pro další.") }
                if (s.loadingMore) item { CenterNote("Načítám další…", progress = true) }
            }
        }
    }
    if (showFilters) FilterSheet(s.filters, onDismiss = { showFilters = false }) { showFilters = false; c.setFilters(it) }
}

@Composable
private fun CheckChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) },
        leadingIcon = if (selected) ({ Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
    )
}

// ---------------------------------------------------------------------------
// Oblíbené
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(items: List<Listing>, onOpen: (Listing) -> Unit, onFavorite: (Listing) -> Unit) {
    Scaffold(contentWindowInsets = insetsWithoutBottom(), topBar = { TopAppBar(title = { Text("Oblíbené") }, actions = { ViewModeButton() }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (items.isEmpty()) item { CenterNote("Zatím nic. Inzeráty si ulož srdíčkem.") }
            items(items, key = { it.id }) { l -> ListingCard(l, null, true, onFavorite = { onFavorite(l) }, onClick = { onOpen(l) }) }
        }
    }
}

// ---------------------------------------------------------------------------
// Hlídání (uložená hledání)
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(
    items: List<SavedSearch>,
    notificationsAllowed: Boolean,
    onAllowNotifications: () -> Unit,
    onOpen: (SavedSearch) -> Unit,
    onNotify: (SavedSearch, Boolean) -> Unit,
    onRemove: (SavedSearch) -> Unit,
) {
    Scaffold(contentWindowInsets = insetsWithoutBottom(), topBar = { TopAppBar(title = { Text("Hlídání") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!notificationsAllowed && items.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Povol notifikace, ať ti dáme vědět o nových inzerátech.", style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = onAllowNotifications, Modifier.padding(top = 8.dp)) { Text("Povolit notifikace") }
                        }
                    }
                }
            }
            if (items.isEmpty()) {
                item {
                    CenterNote("Zatím nic nehlídáš. V libovolném výpisu nebo hledání ťukni na zvonek s plusem a appka tě upozorní na nové inzeráty (kontrola každých 30 min).")
                }
            }
            items(items, key = { it.id }) { s ->
                Card(
                    onClick = { onOpen(s) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.filters.label(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                                if (s.newCount > 0) {
                                    Spacer(Modifier.width(8.dp))
                                    Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("${s.newCount} nových") }
                                }
                            }
                            Text(
                                if (s.notify) "Upozorňuji na nové inzeráty" else "Bez upozornění",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = s.notify, onCheckedChange = { onNotify(s, it) })
                        IconButton(onClick = { onRemove(s) }) { Icon(Icons.Default.Delete, "Smazat") }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Účet — ověření telefonního čísla
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(c: AccountController, onBack: () -> Unit) {
    val s by c.state.collectAsState()
    val acc by c.accountState.collectAsState()
    var phone by remember { mutableStateOf(acc.phone.orEmpty()) }
    var code by remember { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = insetsWithoutBottom(),
        topBar = {
            TopAppBar(
                title = { Text("Účet") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (acc.verified) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Verified, null, tint = LocalStatus.current.good, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Ověřené číslo", style = MaterialTheme.typography.titleMedium)
                        Text(formatPhone(acc.phone.orEmpty()), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
                val stats by c.phoneStats.collectAsState()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Telefon prodejce hned po otevření", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Dnes odkryto ${stats.todayCount} z ${stats.dailyLimit} automatických. Už jednou zobrazená čísla si appka pamatuje a znovu se na ně neptá.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = stats.autoReveal, onCheckedChange = c::setAutoReveal)
                }
                Text(
                    "Každé nové číslo je dotaz na Bazoš zapsaný k tvému ověřenému číslu — hromadné odkrývání vede k zablokování. " +
                        "Proto se čísla načítají jen u inzerátů, které otevřeš, a automaticky nejvýš ${stats.dailyLimit} denně.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = c::logout) { Text("Odhlásit") }
                return@Column
            }

            Text("Ověření telefonu", style = MaterialTheme.typography.titleLarge)
            Text(
                "Bazoš ukazuje telefony prodejců jen ověřeným uživatelům. Zadej své číslo, Bazoš ti pošle SMS s kódem. " +
                    "Číslo i přihlašovací token zůstávají jen v tomto telefonu.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                phone, { phone = it }, Modifier.fillMaxWidth(),
                label = { Text("Telefon") }, placeholder = { Text("731 123 456") }, prefix = { Text("+420 ") },
                singleLine = true, enabled = !s.codeSent && !s.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            if (!s.codeSent) {
                Button(onClick = { c.requestCode(phone) }, enabled = !s.busy && phone.any(Char::isDigit), modifier = Modifier.fillMaxWidth()) {
                    Text(if (s.busy) "Posílám…" else "Poslat SMS s kódem")
                }
            } else {
                OutlinedTextField(
                    code, { code = it.filter(Char::isLetterOrDigit).take(8) }, Modifier.fillMaxWidth(),
                    label = { Text("Kód z SMS") }, singleLine = true, enabled = !s.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (code.isNotBlank()) c.verify(code) }),
                )
                Button(onClick = { c.verify(code) }, enabled = !s.busy && code.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(if (s.busy) "Ověřuji…" else "Ověřit")
                }
                TextButton(onClick = c::restart) { Text("Změnit číslo / poslat znovu") }
            }
            s.info?.let { Text(it, color = LocalStatus.current.good) }
            s.error?.let { Text(it, color = LocalStatus.current.bad) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun formatPhone(p: String) = if (p.length == 9) "+420 ${p.chunked(3).joinToString(" ")}" else p
