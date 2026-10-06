package cz.petrkyncl.bazosiphony.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.SuggestionChip
import cz.petrkyncl.bazosiphony.data.Category
import cz.petrkyncl.bazosiphony.data.Money
import cz.petrkyncl.bazosiphony.data.Section

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    sections: List<Section>,
    categories: Map<String, List<Category>>,
    error: String?,
    verified: Boolean,
    onRetry: () -> Unit,
    onSearch: () -> Unit,
    onSection: (Section) -> Unit,
    onCategory: (Section, Category) -> Unit,
    onDeals: () -> Unit,
    onAccount: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = insetsWithoutBottom(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Black)) { append("bazoš") }
                            withStyle(SpanStyle(fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.primary)) { append(" plus") }
                        },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                actions = {
                    CurrencyButton()
                    IconButton(onClick = onAccount) {
                        Icon(if (verified) Icons.Default.Verified else Icons.Default.AccountCircle, "Účet")
                    }
                },
                // klidná neutrální lišta — oranžová jen jako akcent
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(28.dp))
                        .clickable(onClick = onSearch)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text("Hledat na celém Bazoši…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Card(onClick = onDeals, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhoneIphone, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Výkup iPhonů", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "iPhony levněji, než za kolik je vykoupí mp.cz — s odhadem stavu a ziskem.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                    }
                }
            }
            item { Text("Rubriky", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp)) }
            if (sections.isEmpty()) {
                item {
                    if (error != null) CenterNote("Rubriky se nenačetly: $error", action = "Zkusit znovu" to onRetry)
                    else CenterNote("Načítám rubriky…", progress = true)
                }
            }
            items(sections, key = { it.id }) { s -> SectionRow(s, categories[s.id].orEmpty(), onSection, onCategory) }
        }
    }
}

/** Rubrika s ikonou a rychlými odkazy na její kategorie. */
@Composable
private fun SectionRow(s: Section, categories: List<Category>, onSection: (Section) -> Unit, onCategory: (Section, Category) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { onSection(s) }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(sectionIcon(s.id)), null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Text(s.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("Vše", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
        }
        if (categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(categories, key = { it.id }) { c ->
                    SuggestionChip(onClick = { onCategory(s, c) }, label = { Text(c.title, maxLines = 1) })
                }
            }
        }
    }
}

/** Přepínač zobrazované měny (kurz ČNB). */
@Composable
private fun CurrencyButton() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        androidx.compose.material3.TextButton(onClick = { open = true }) {
            Text(if (Money.selected == "CZK") "Kč" else Money.selected, fontWeight = FontWeight.SemiBold)
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                Money.ratesDate?.let { "Kurz ČNB ze dne $it" } ?: "Kurzy ČNB se nenačetly — jen Kč",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Money.choices.forEach { r ->
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Text(
                            if (r.code == "CZK") "CZK · koruna" else "${r.code} · ${r.name} (${czkRate(r.czkPerUnit)} Kč)",
                            fontWeight = if (r.code == Money.selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = { open = false; Money.select(context, r.code) },
                )
            }
        }
    }
}

private fun czkRate(v: Double) = (if (v < 1) "%.3f" else "%.2f").format(java.util.Locale.forLanguageTag("cs-CZ"), v)
