package cz.petrkyncl.bazosiphony.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cz.petrkyncl.bazosiphony.data.Filters
import cz.petrkyncl.bazosiphony.data.Repository
import cz.petrkyncl.bazosiphony.domain.IphoneKey
import cz.petrkyncl.bazosiphony.domain.modelLabel

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

/** Výběr konkrétních iPhonů (víc najednou) a kapacit, seřazené podle generace. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ModelSheet(initial: Filters, repo: Repository, onDismiss: () -> Unit, onApply: (Filters) -> Unit) {
    var models by remember { mutableStateOf(initial.models.toSet()) }
    var storages by remember { mutableStateOf(initial.storages.toSet()) }
    var all by remember { mutableStateOf<List<IphoneKey>>(emptyList()) }
    var availableStorages by remember { mutableStateOf<List<Int>>(emptyList()) }
    LaunchedEffect(Unit) { all = repo.iphoneModels() }
    LaunchedEffect(models) { availableStorages = repo.iphoneStorages(models) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Modely iPhonu", style = MaterialTheme.typography.titleLarge)
            Text(
                "Vyber jeden nebo víc. Výsledky obsahují jen inzeráty, kde model v nadpisu přesně sedí (15 Pro ≠ 15 Pro Max).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (all.isEmpty()) CenterNote("Načítám seznam modelů…", progress = true)
                // po generacích: 18 · 17 · 16 …, SE a X/XR/XS zvlášť
                all.groupBy { k -> if ((k.number ?: 0) > 2000) "SE" else if (k.number == 10) "X" else k.number.toString() }.forEach { (gen, keys) ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        keys.forEach { k ->
                            val label = modelLabel(k)
                            val on = label in models
                            FilterChip(
                                selected = on,
                                onClick = { models = if (on) models - label else models + label },
                                label = { Text(label.removePrefix("iPhone ")) },
                            )
                        }
                    }
                }
                if (models.isNotEmpty() && availableStorages.isNotEmpty()) {
                    Text("Kapacita (nepovinné)", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        availableStorages.forEach { gb ->
                            val on = gb in storages
                            FilterChip(
                                selected = on, onClick = { storages = if (on) storages - gb else storages + gb },
                                label = { Text(if (gb >= 1024) "${gb / 1024} TB" else "$gb GB") },
                            )
                        }
                    }
                    if (storages.isNotEmpty()) {
                        Text(
                            "Inzeráty bez uvedené kapacity v nadpisu se při výběru kapacity nezobrazí.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { models = emptySet(); storages = emptySet() }, Modifier.weight(1f)) { Text("Vymazat") }
                Button(
                    onClick = {
                        onApply(initial.copy(models = models.toList(), storages = if (models.isEmpty()) emptyList() else storages.toList()))
                    },
                    Modifier.weight(1f),
                ) { Text(if (models.isEmpty()) "Všechny modely" else "Použít (${models.size})") }
            }
        }
    }
}
