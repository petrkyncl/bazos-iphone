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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import cz.petrkyncl.bazosiphony.domain.Confidence

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdCard(ad: Ad, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (ad.deal == Deal.EXCLUDED) 0.55f else 1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(10.dp)) {
            Box {
                AsyncImage(
                    model = ad.thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(104.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                if (ad.isTop) {
                    Text(
                        "TOP",
                        Modifier
                            .padding(4.dp)
                            .background(Color(0xFFF59E0B), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp),
                        color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
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
                ad.buyoutFor(ad.condition.grade)?.let {
                    Text("výkup ${czk(it)}", style = MaterialTheme.typography.bodySmall, color = muted)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ad.model?.let { Tag(it + (ad.storageGb?.let { gb -> " · " + storageText(gb) } ?: "")) }
                    GradeChip(ad.condition.grade, faded = ad.condition.confidence == Confidence.LOW)
                    ad.condition.batteryPct?.let { Tag("🔋 $it %") }
                    ad.issue?.let { Tag(it.label, if (ad.deal == Deal.EXCLUDED) Tone.BAD else Tone.WARN) }
                    ad.condition.warnings.firstOrNull()?.let { Tag("⚠ " + it.substringBefore(" (").substringBefore(" — "), Tone.WARN) }
                }
                Text(
                    listOfNotNull(
                        ad.locality,
                        ago(ad.date).ifEmpty { null },
                        "👤 ${ad.seller.name ?: "?"} · ${ad.seller.totalAds} inz." + if (ad.seller.reviewsCount > 0) " · ★ ${ad.seller.reviewsCount}" else "",
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
