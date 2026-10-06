package cz.petrkyncl.bazosiphony.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.petrkyncl.bazosiphony.data.Ad
import cz.petrkyncl.bazosiphony.data.BazosApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    state: ListState,
    listState: LazyListState,
    onOpen: (Ad) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onApplyFilters: (BazosApi.Filters) -> Unit,
    onSort: (SortMode) -> Unit,
    onOnlyGood: (Boolean) -> Unit,
    onHideExcluded: (Boolean) -> Unit,
    onRefreshPricelist: () -> Unit,
) {
    var showFilters by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var query by rememberSaveable(state.filters.query) { mutableStateOf(state.filters.query) }
    val focus = LocalFocusManager.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val visible = state.visible

    // nekonečné scrollování: blízko konce načíst další stránku
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.canLoadMore) { if (nearEnd && state.canLoadMore && state.ads.isNotEmpty()) onLoadMore() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("iPhony z Bazoše", fontWeight = FontWeight.SemiBold)
                            Text(
                                buildString {
                                    if (state.total > 0) append("${"%,d".format(state.total).replace(',', ' ')} inzerátů · výhodných ${state.goodCount}")
                                    state.pricelistUpdatedAt?.let { append(" · ceník ${agoMillis(it)}") }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Aktualizovat ceník mp.cz") }, onClick = { showMenu = false; onRefreshPricelist() })
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    placeholder = { Text("Hledat (iphone 15 pro, 13 mini…)") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = ""; onApplyFilters(state.filters.copy(query = "")) }) { Icon(Icons.Default.Clear, "Smazat") }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); onApplyFilters(state.filters.copy(query = query)) }),
                )
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val f = state.filters
                    val active = listOf(f.priceFrom, f.priceTo, f.zip).count { it.isNotBlank() }
                    FilterChip(
                        selected = active > 0, onClick = { showFilters = true },
                        label = { Text(if (active > 0) "Filtry ($active)" else "Filtry") },
                        leadingIcon = { Icon(Icons.Default.FilterList, null, Modifier.size(FilterChipDefaults.IconSize)) },
                    )
                    SortChip(state.sort, onSort)
                    FilterChip(selected = state.onlyGood, onClick = { onOnlyGood(!state.onlyGood) }, label = { Text("Jen výhodné") },
                        leadingIcon = if (state.onlyGood) ({ Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null)
                    FilterChip(selected = state.hideExcluded, onClick = { onHideExcluded(!state.hideExcluded) }, label = { Text("Skrýt prodané a šum") },
                        leadingIcon = if (state.hideExcluded) ({ Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null)
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading && state.ads.isNotEmpty(),
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                state.error?.let { err ->
                    item {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Chyba: $err", color = LocalStatus.current.bad)
                            OutlinedButton(onClick = onRefresh, Modifier.padding(top = 8.dp)) { Text("Zkusit znovu") }
                        }
                    }
                }
                if (state.loading && state.ads.isEmpty()) {
                    item { CenterNote("Načítám inzeráty a výkupní ceny…", progress = true) }
                }
                items(visible, key = { it.id }) { ad -> AdCard(ad, onClick = { onOpen(ad) }) }
                if (!state.loading && state.ads.isNotEmpty() && visible.isEmpty()) {
                    item { CenterNote("V načtených inzerátech nic neodpovídá filtru — sjeď níž pro další.") }
                }
                if (!state.loading && state.error == null && state.ads.isEmpty()) {
                    item { CenterNote("Nic nenalezeno.") }
                }
                if (state.loadingMore) item { CenterNote("Načítám další…", progress = true) }
                else if (state.canLoadMore && state.ads.isNotEmpty()) {
                    item {
                        OutlinedButton(onClick = onLoadMore, Modifier.fillMaxWidth()) {
                            Text("Načíst další (zbývá ${state.total - state.nextOffset})")
                        }
                    }
                }
            }
        }
    }

    if (showFilters) FilterSheet(state.filters.copy(query = query), onDismiss = { showFilters = false }) {
        showFilters = false
        onApplyFilters(it)
    }
}

@Composable
private fun SortChip(sort: SortMode, onSort: (SortMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = sort != SortMode.DATE, onClick = { open = true },
            label = { Text(sort.label) },
            leadingIcon = { Icon(Icons.Default.SwapVert, null, Modifier.size(FilterChipDefaults.IconSize)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label + if (mode == SortMode.PROFIT) " (z načtených)" else "") },
                    leadingIcon = if (mode == sort) ({ Icon(Icons.Default.Check, null) }) else null,
                    onClick = { open = false; onSort(mode) },
                )
            }
        }
    }
}

@Composable
private fun CenterNote(text: String, progress: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (progress) {
            CircularProgressIndicator(Modifier.size(28.dp))
            Spacer(Modifier.height(10.dp))
        }
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(initial: BazosApi.Filters, onDismiss: () -> Unit, onApply: (BazosApi.Filters) -> Unit) {
    var f by remember { mutableStateOf(initial) }
    val distances = listOf(5, 10, 25, 50, 100)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
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
            OutlinedTextField(
                f.zip, { f = f.copy(zip = it.filter { c -> c.isDigit() || c == ' ' }.take(6)) }, Modifier.fillMaxWidth(),
                label = { Text("PSČ") }, placeholder = { Text("prázdné = celá ČR") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            if (f.zip.isNotBlank()) {
                Text("Okruh", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    distances.forEachIndexed { i, km ->
                        SegmentedButton(
                            selected = f.distanceKm == km, onClick = { f = f.copy(distanceKm = km) },
                            shape = SegmentedButtonDefaults.itemShape(i, distances.size),
                        ) { Text("$km km") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { f = BazosApi.Filters(query = f.query, priceFrom = "", sort = f.sort) }, Modifier.weight(1f)) { Text("Vymazat") }
                Button(onClick = { onApply(f) }, Modifier.weight(1f)) { Text("Použít") }
            }
        }
    }
}
