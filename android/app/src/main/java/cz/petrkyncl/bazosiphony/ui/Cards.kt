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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import cz.petrkyncl.bazosiphony.data.Ad
import cz.petrkyncl.bazosiphony.data.Deal
import cz.petrkyncl.bazosiphony.data.Listing
import cz.petrkyncl.bazosiphony.domain.Confidence
import cz.petrkyncl.bazosiphony.domain.IphoneKey
import cz.petrkyncl.bazosiphony.domain.MarketStats
import cz.petrkyncl.bazosiphony.domain.listingIssue
import cz.petrkyncl.bazosiphony.domain.parseIphone
import cz.petrkyncl.bazosiphony.domain.phoneHeadline

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
        if (isTop) TopBadge(Modifier.padding(4.dp))
    }
}

/** Režim výsledků: true = velké celé fotky se swipováním, false = kompaktní seznam. */
val LocalGalleryMode = staticCompositionLocalOf { true }

/**
 * Rámeček karty inzerátu: v kompaktním režimu čtverec vlevo, v režimu galerie velké celé fotky
 * nahoře (swipe mezi fotkami, ťuknutí otevře detail). Všechny fotky se načtou z detailu až u karty,
 * která je na obrazovce; do té doby první fotka.
 */
@Composable
private fun CardFrame(
    l: Listing,
    favorite: Boolean?,
    onFavorite: () -> Unit,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val gallery = LocalGalleryMode.current
    val heart: @Composable () -> Unit = {
        if (favorite != null) {
            IconButton(onClick = onFavorite, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (favorite) "Odebrat z oblíbených" else "Přidat do oblíbených",
                    tint = if (favorite) MaterialTheme.colorScheme.primary else if (gallery) Color.White else muted,
                )
            }
        }
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        if (gallery) {
            val loadImages = LocalImages.current
            val first = l.thumb?.replace("/img/1m/", "/img/1/") // plná první fotka místo 350px náhledu
            val images by produceState(listOfNotNull(first), l.id) {
                runCatching { loadImages(l.id) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { value = it }
            }
            Box {
                BoxWithConstraints { PhotoPager(images, height = maxWidth * 0.75f, onClick = { onClick() }) }
                if (l.isTop) TopBadge(Modifier.padding(8.dp))
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape)) { heart() }
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp), content = content)
        } else {
            Row(Modifier.padding(10.dp)) {
                Thumb(l.thumb, l.isTop)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp), content = content)
                heart()
            }
        }
    }
}

@Composable
private fun TopBadge(modifier: Modifier = Modifier) {
    Text(
        "TOP",
        modifier.background(BazosOrange, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
        color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
    )
}

/** Karta inzerátu ve výpisu; telefony mají jednotnou strukturovanou kartu. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListingCard(l: Listing, plus: Ad?, favorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit) {
    // telefon → jednotná strukturovaná karta (model, kapacita, stav, baterie… vždy stejně)
    val key = plus?.plus?.key ?: parseIphone(l.title)?.takeIf { it.number != null }
    if (key != null && plus?.plus?.issue?.excludes != true && listingIssue(l.title, "") == null) {
        PhoneCard(l, key, plus, market = null, favorite = favorite, onFavorite = onFavorite, onClick = onClick)
        return
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    CardFrame(l, favorite, onFavorite, onClick) {
        Text(l.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            l.price?.let(::czk) ?: l.priceText ?: "—",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
        )
        Text(
            listOfNotNull(l.locality, ago(l.dateMillis).ifEmpty { null }, "${number(l.views)}×").joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Karta v záložce Výkup: stejná jednotná karta telefonu (i u vyřazených, ztlumeně). */
@Composable
fun DealCard(ad: Ad, market: MarketStats?, onClick: () -> Unit) {
    val key = ad.plus?.key
    if (key == null) {
        ListingCard(ad.toListing(), ad, favorite = false, onFavorite = {}, onClick = onClick)
        return
    }
    Box(Modifier.alpha(if (ad.deal == Deal.EXCLUDED) 0.55f else 1f)) {
        PhoneCard(ad.toListing(), key, ad, market, favorite = null, onFavorite = {}, onClick = onClick)
    }
}

/**
 * Jednotná karta telefonu — první řádek je vždy „iPhone 15 Pro Max · 256 GB", pak vždy ve stejném
 * pořadí stav, baterie, barva, záruka; cena se ziskem a mediánem; původní nadpis až drobně dole.
 * Údaje z popisu (stav, baterie…) se doplní, jakmile dorazí detail.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhoneCard(l: Listing, key: IphoneKey, ad: Ad?, market: MarketStats?, favorite: Boolean?, onFavorite: () -> Unit, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val p = ad?.plus
    CardFrame(l, favorite, onFavorite, onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (ad != null) DealDot(ad.deal, Modifier.padding(end = 6.dp))
            Text(phoneHeadline(key), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // vlastnosti vždy ve stejném pořadí; dokud se nenačte detail, jen to, co je v nadpisu
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (p != null) GradeChip(p.condition.grade, faded = p.condition.confidence == Confidence.LOW)
            p?.condition?.batteryPct?.let { Tag("🔋 $it %") }
            p?.color?.let { Tag(it) }
            p?.warranty?.let { Tag(it) }
            p?.issue?.let { Tag(it.label, if (ad.deal == Deal.EXCLUDED) Tone.BAD else Tone.WARN) }
            p?.condition?.warnings?.firstOrNull()?.let { Tag("⚠ " + it.substringBefore(" (").substringBefore(" — "), Tone.WARN) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            Text(l.price?.let(::czk) ?: l.priceText ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            if (ad != null && ad.deal != Deal.EXCLUDED) ad.profit?.let { ProfitPill(it) }
        }
        val refs = listOfNotNull(
            p?.buyoutFor(p.condition.grade)?.let { "výkup ${czk(it)}" },
            market?.let { "medián Bazoš ${czk(it.median)}" },
        )
        if (refs.isNotEmpty()) Text(refs.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = muted)
        Text("„${l.title}“", style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(l.locality, ago(l.dateMillis).ifEmpty { null }, "${number(l.views)}×").joinToString("  ·  "),
            style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
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
