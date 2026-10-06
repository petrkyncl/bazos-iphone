package cz.petrkyncl.bazosiphony.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import cz.petrkyncl.bazosiphony.data.Section

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    sections: List<Section>,
    error: String?,
    verified: Boolean,
    onRetry: () -> Unit,
    onSearch: () -> Unit,
    onSection: (Section) -> Unit,
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
                            withStyle(SpanStyle(fontWeight = FontWeight.Light)) { append(" plus") }
                        },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                actions = {
                    IconButton(onClick = onAccount) {
                        Icon(if (verified) Icons.Default.Verified else Icons.Default.AccountCircle, "Účet")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BazosOrange, titleContentColor = Color.White, actionIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
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
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    onClick = onDeals,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
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
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Rubriky", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
            }
            if (sections.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (error != null) CenterNote("Rubriky se nenačetly: $error", action = "Zkusit znovu" to onRetry)
                    else CenterNote("Načítám rubriky…", progress = true)
                }
            }
            items(sections, key = { it.id }) { s ->
                Card(
                    onClick = { onSection(s) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        AsyncImage(s.image, null, Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(s.title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
        }
    }
}
