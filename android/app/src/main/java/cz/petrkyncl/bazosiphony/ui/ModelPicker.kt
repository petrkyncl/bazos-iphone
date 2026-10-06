package cz.petrkyncl.bazosiphony.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.petrkyncl.bazosiphony.data.Filters
import cz.petrkyncl.bazosiphony.data.Repository
import cz.petrkyncl.bazosiphony.domain.IphoneKey
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Štítek „Modely" do řádku filtrů; otevře výběr konkrétních iPhonů. */
@Composable
fun ModelsChip(filters: Filters, repo: Repository, onApply: (Filters) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val n = filters.models.size
    FilterChip(
        selected = n > 0, onClick = { open = true },
        label = { Text(if (n == 0) "Modely" else if (n == 1) filters.models.first().removePrefix("iPhone ") else "Modely ($n)") },
        leadingIcon = { Icon(Icons.Default.PhoneIphone, null, Modifier.size(FilterChipDefaults.IconSize)) },
    )
    if (open) ModelSheet(filters, repo, onDismiss = { open = false }) { open = false; onApply(it) }
}

private fun IphoneKey.isPro() = "pro" in variants
private fun IphoneKey.isProMax() = "pro" in variants && "max" in variants
private fun IphoneKey.generation(): Int = number?.takeIf { it < 2000 } ?: 0 // SE mimo řady

/** Řada pro seskupení: „iPhone 15", „iPhone X/XR/XS", „iPhone SE". */
private fun IphoneKey.seriesName(): String = when {
    (number ?: 0) > 2000 -> "iPhone SE"
    number == 10 -> "iPhone X / XR / XS"
    "air" in variants -> "iPhone 17"
    else -> "iPhone $number"
}

/**
 * Výběr konkrétních iPhonů: řady i jednotlivé modely jako zaškrtávátka, rychlé volby
 * (Pro a výš, od řady…), u každého modelu medián ceny na Bazoši a výkup mp.cz.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ModelSheet(initial: Filters, repo: Repository, onDismiss: () -> Unit, onApply: (Filters) -> Unit) {
    var selected by remember { mutableStateOf(initial.models.toSet()) }
    var storages by remember { mutableStateOf(initial.storages.toSet()) }
    var options by remember { mutableStateOf<List<Repository.ModelOption>>(emptyList()) }
    var availableStorages by remember { mutableStateOf<List<Int>>(emptyList()) }
    val medians = remember { mutableStateMapOf<String, Pair<Int, Int>?>() }

    LaunchedEffect(Unit) {
        options = repo.iphoneModels()
        // mediány postupně (repo omezuje souběh na 3, výsledky drží 12 h)
        coroutineScope {
            options.map { o -> async { medians[o.label] = runCatching { repo.modelMedian(o.key) }.getOrNull() } }.awaitAll()
        }
    }
    LaunchedEffect(selected) { availableStorages = repo.iphoneStorages(selected) }

    val series = options.groupBy { it.key.seriesName() }
    fun select(pred: (IphoneKey) -> Boolean) { selected = options.filter { pred(it.key) }.map { it.label }.toSet() }
    fun narrow(pred: (IphoneKey) -> Boolean) {
        // kombinovatelné: zúží stávající výběr (prázdný výběr = všechny modely)
        val base = if (selected.isEmpty()) options else options.filter { it.label in selected }
        selected = base.filter { pred(it.key) }.map { it.label }.toSet()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Modely iPhonu", style = MaterialTheme.typography.titleLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { narrow { it.isPro() } }, label = { Text("Pro a výš") })
                    AssistChip(onClick = { narrow { it.isProMax() } }, label = { Text("Jen Pro Max") })
                    AssistChip(onClick = { select { it.generation() >= 15 } }, label = { Text("Od 15 výš") })
                    AssistChip(onClick = { select { it.generation() >= 13 } }, label = { Text("Od 13 výš") })
                    AssistChip(onClick = { select { true } }, label = { Text("Všechny") })
                }
                Row(Modifier.fillMaxWidth()) {
                    Text("model", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(start = 48.dp))
                    Text("medián Bazoš · výkup od", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn(Modifier.weight(1f, fill = false).padding(horizontal = 8.dp)) {
                if (options.isEmpty()) item { CenterNote("Načítám seznam modelů…", progress = true) }
                series.forEach { (name, models) ->
                    item(key = "s_$name") {
                        val n = models.count { it.label in selected }
                        val state = when (n) { 0 -> ToggleableState.Off; models.size -> ToggleableState.On; else -> ToggleableState.Indeterminate }
                        val toggle = {
                            selected = if (state == ToggleableState.On) selected - models.map { it.label }.toSet() else selected + models.map { it.label }
                        }
                        Row(Modifier.fillMaxWidth().clickable(onClick = toggle), verticalAlignment = Alignment.CenterVertically) {
                            TriStateCheckbox(state = state, onClick = toggle)
                            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (n > 0) Text("$n / ${models.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
                        }
                    }
                    models.forEach { o ->
                        item(key = o.label) {
                            val on = o.label in selected
                            val toggle = { selected = if (on) selected - o.label else selected + o.label }
                            Row(Modifier.fillMaxWidth().clickable(onClick = toggle).padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = on, onCheckedChange = { toggle() })
                                Text(o.label.removePrefix("iPhone "), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Column(horizontalAlignment = Alignment.End) {
                                    val m = medians[o.label]
                                    Text(
                                        when {
                                            o.label !in medians -> "…"
                                            m == null -> "málo inzerátů"
                                            else -> "${czk(m.first)} (${m.second}×)"
                                        },
                                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        o.buyoutFrom?.let { "výkup od ${czk(it)}" } ?: "nevykupují",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    item(key = "d_$name") { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                }
            }
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selected.isNotEmpty() && availableStorages.isNotEmpty()) {
                    Text("Kapacita (nepovinné)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        availableStorages.forEach { gb ->
                            val on = gb in storages
                            FilterChip(
                                selected = on, onClick = { storages = if (on) storages - gb else storages + gb },
                                label = { Text(if (gb >= 1024) "${gb / 1024} TB" else "$gb GB") },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { selected = emptySet(); storages = emptySet() }, Modifier.weight(1f)) { Text("Vymazat") }
                    Button(
                        onClick = { onApply(initial.copy(models = selected.toList(), storages = if (selected.isEmpty()) emptyList() else storages.toList())) },
                        Modifier.weight(1f),
                    ) { Text(if (selected.isEmpty()) "Všechny modely" else "Použít (${selected.size})") }
                }
            }
        }
    }
}
