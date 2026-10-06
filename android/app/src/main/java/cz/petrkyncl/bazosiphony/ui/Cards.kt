package cz.petrkyncl.bazosiphony.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import cz.petrkyncl.bazosiphony.data.Ad
import cz.petrkyncl.bazosiphony.data.Deal
import cz.petrkyncl.bazosiphony.data.Listing
import cz.petrkyncl.bazosiphony.domain.Confidence

@Composable
private fun Thumb(url: String?, isTop: Boolean, size: Int = 104) {
    Box {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        if (isTop) {
            Text(
                "TOP",
                Modifier
                    .padding(4.dp)
                    .background(BazosOrange, RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp),
                color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Karta inzerátu ve výpisu; u iPhonů s výkupem navíc zisk proti mp.cz. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListingCard(l: Listing, plus: Ad?, favorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(10.dp)) {
            Thumb(l.thumb, l.isTop)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(l.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    l.price?.let(::czk) ?: l.priceText ?: "—",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                )
                if (plus != null && plus.deal != Deal.EXCLUDED) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        plus.profit?.let { ProfitPill(it) }
                        plus.plus?.let { p ->
                            p.buyoutFor(p.condition.grade)?.let { Text("výkup ${czk(it)}", style = MaterialTheme.typography.labelSmall, color = muted) }
                            GradeChip(p.condition.grade, faded = p.condition.confidence == Confidence.LOW)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    listOfNotNull(l.locality, ago(l.dateMillis).ifEmpty { null }, "${number(l.views)}×").joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onFavorite, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (favorite) "Odebrat z oblíbených" else "Přidat do oblíbených",
                    tint = if (favorite) MaterialTheme.colorScheme.primary else muted,
                )
            }
        }
    }
}

/** Karta v záložce Výkup: iPhone s modelem, stavem, výkupem a ziskem. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DealCard(ad: Ad, market: cz.petrkyncl.bazosiphony.domain.MarketStats?, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val p = ad.plus
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().alpha(if (ad.deal == Deal.EXCLUDED) 0.55f else 1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(10.dp)) {
            Thumb(ad.thumb, ad.isTop)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    DealDot(ad.deal, Modifier.padding(top = 6.dp, end = 6.dp))
                    Text(ad.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Text(ad.price?.let(::czk) ?: ad.priceText ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (ad.deal != Deal.EXCLUDED) ad.profit?.let { ProfitPill(it) }
                }
                Text(
                    listOfNotNull(
                        p?.buyoutFor(p.condition.grade)?.let { "výkup ${czk(it)}" },
                        market?.let { "medián Bazoš ${czk(it.median)}" },
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall, color = muted,
                )
                if (p != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        p.model?.let { Tag(it + (p.storageGb?.let { gb -> " · " + storageText(gb) } ?: "")) }
                        GradeChip(p.condition.grade, faded = p.condition.confidence == Confidence.LOW)
                        p.condition.batteryPct?.let { Tag("🔋 $it %") }
                        p.issue?.let { Tag(it.label, if (ad.deal == Deal.EXCLUDED) Tone.BAD else Tone.WARN) }
                        p.condition.warnings.firstOrNull()?.let { Tag("⚠ " + it.substringBefore(" (").substringBefore(" — "), Tone.WARN) }
                    }
                }
                Text(
                    listOfNotNull(
                        ad.locality,
                        ago(ad.dateMillis).ifEmpty { null },
                        "👤 ${ad.seller.name ?: "?"} · ${ad.seller.totalAds} inz." + if (ad.seller.reviewsCount > 0) " · ★ ${ad.seller.reviewsCount}" else "",
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun CenterNote(text: String, progress: Boolean = false, action: Pair<String, () -> Unit>? = null) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (progress) {
            CircularProgressIndicator(Modifier.size(28.dp))
            Spacer(Modifier.height(10.dp))
        }
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        action?.let { (label, onClick) -> OutlinedButton(onClick = onClick, Modifier.padding(top = 8.dp)) { Text(label) } }
    }
}
