package cz.petrkyncl.bazosiphony.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import cz.petrkyncl.bazosiphony.data.Ad
import cz.petrkyncl.bazosiphony.data.SellerProfile
import cz.petrkyncl.bazosiphony.domain.Grade
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(state: DetailState, onBack: () -> Unit, onGrade: (Grade) -> Unit) {
    val ad = state.ad
    val context = LocalContext.current
    var viewer by remember { mutableStateOf<Int?>(null) }
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ad.model ?: "Detail", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } },
                actions = {
                    IconButton(onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${ad.title}\n${ad.url}"), null))
                    }) { Icon(Icons.Default.Share, "Sdílet") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ad.images.isNotEmpty()) item { Gallery(ad.images) { viewer = it } }
            item { Header(ad, ::open) }
            if (ad.issue != null || ad.condition.warnings.isNotEmpty()) item { Warnings(ad) }
            item { ConditionCard(ad) }
            item { BuyoutCard(ad, state.grade, onGrade, ::open) }
            item { SellerCard(ad, state.seller, state.sellerLoading, ::open) }
            item {
                Section("Popis") {
                    Text(ad.description.ifBlank { "Bez popisu." }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    viewer?.let { start -> ImageViewer(ad.images, start) { viewer = null } }
}

@Composable
private fun Gallery(images: List<String>, onClick: (Int) -> Unit) {
    val pager = rememberPagerState { images.size }
    Box {
        HorizontalPager(pager, Modifier.fillMaxWidth().aspectRatio(4f / 3f)) { page ->
            AsyncImage(
                images[page], null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable { onClick(page) },
            )
        }
        if (images.size > 1) {
            Text(
                "${pager.currentPage + 1} / ${images.size}",
                Modifier.align(Alignment.BottomEnd).padding(10.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
                color = Color.White, style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(ad: Ad, open: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(ad.title, style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(ad.price?.let(::czk) ?: ad.priceText ?: "—", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            ad.profit?.let { ProfitPill(it, large = true) }
        }
        Text(
            listOfNotNull(ad.locality, ago(ad.date).ifEmpty { null }, "${ad.views}× zobrazeno").joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { open(ad.url) }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Otevřít na Bazoši")
            }
            if (ad.lat != null && ad.lng != null) {
                OutlinedButton(onClick = { open("geo:${ad.lat},${ad.lng}?q=${ad.lat},${ad.lng}(${ad.locality ?: "Inzerát"})") }) {
                    Icon(Icons.Default.Map, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Mapa")
                }
            }
        }
    }
}

@Composable
private fun Warnings(ad: Ad) {
    val s = LocalStatus.current
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(s.warnBg, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ad.issue?.let { Text(it.label, color = s.warn, fontWeight = FontWeight.SemiBold) }
        ad.condition.warnings.forEach { Text("⚠ $it", color = s.warn, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun Section(title: String, trailing: @Composable (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionCard(ad: Ad) {
    val c = ad.condition
    Section("Odhad stavu z popisu") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            GradeChip(c.grade)
            Text(c.confidence.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            c.batteryPct?.let { Tag("🔋 baterie $it %") }
        }
        if (c.signals.isEmpty()) {
            Text("Prodejce stav nepopsal — počítám konzervativně se stavem B.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                c.signals.forEach { Tag("${it.grade.code} · ${it.label} „${it.match}“") }
            }
        }
    }
}

@Composable
private fun BuyoutCard(ad: Ad, selected: Grade, onGrade: (Grade) -> Unit, open: (String) -> Unit) {
    val variant = ad.buyout
    Section(
        "Výkup mp.cz" + (variant?.let { " · " + it.variant.removePrefix("Apple ") } ?: ""),
        trailing = variant?.url?.let { url -> { Text("mp.cz ↗", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable { open(url) }) } },
    ) {
        if (variant == null) {
            Text(
                if (ad.model != null) "Model ${ad.model} není ve výkupním ceníku." else "Model se nepodařilo rozpoznat.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Section
        }
        if (ad.storageGuessed) {
            Text(
                "Kapacita v inzerátu chybí nebo nesedí — použita ${if (ad.storageGb != null) "nejbližší" else "nejlevnější"} varianta.",
                style = MaterialTheme.typography.bodySmall, color = LocalStatus.current.warn,
            )
        }
        Text("Ťukni na stav, pokud podle fotek odhaduješ jiný.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Grade.entries.forEach { g ->
            val buy = ad.buyoutFor(g)
            val active = g == selected
            Card(
                onClick = { onGrade(g) },
                enabled = buy != null,
                colors = CardDefaults.cardColors(
                    containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                ),
                border = if (active) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${g.code}  ${g.title}" + if (g == ad.condition.grade) "  · odhad" else "",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        variant.sell[g.code]?.let {
                            Text("mp.cz prodává za ${czk(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(buy?.let(::czk) ?: "nevykupují", fontWeight = FontWeight.SemiBold)
                        ad.profitFor(g)?.let { ProfitPill(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SellerCard(ad: Ad, seller: SellerProfile?, loading: Boolean, open: (String) -> Unit) {
    val s = LocalStatus.current
    Section("Prodejce · ${ad.seller.name ?: "—"}") {
        if (seller?.likelyDealer == true) Tag("Pravděpodobně překupník (${seller.phoneAds} telefonů v inzerátech)", Tone.WARN)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("inzerátů", ad.seller.totalAds.toString(), null, Modifier.weight(1f))
            Stat("kladných", seller?.positive?.toString() ?: if (ad.seller.reviewsCount > 0) "…" else "0", s.good, Modifier.weight(1f))
            Stat("záporných", seller?.negative?.toString() ?: if (ad.seller.reviewsCount > 0) "…" else "0", s.bad, Modifier.weight(1f))
        }
        if (loading && (ad.seller.reviewsCount > 0 || ad.seller.totalAds > 1)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Načítám recenze a další inzeráty…", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (seller != null && seller.reviews.isNotEmpty()) {
            Text("Recenze", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            seller.reviews.forEach { r ->
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp)).padding(10.dp)) {
                    Text(
                        (if (r.positive) "👍 kladná" else "👎 záporná") + " · ${r.author ?: ""}" + (r.date?.let { " · " + it.format(DateTimeFormatter.ofPattern("d. M. yyyy")) } ?: ""),
                        style = MaterialTheme.typography.labelMedium, color = if (r.positive) s.good else s.bad,
                    )
                    if (r.text.isNotBlank()) Text(r.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (seller != null && seller.otherAds.isNotEmpty()) {
            Text("Další inzeráty prodejce", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            seller.otherAds.forEachIndexed { i, o ->
                if (i > 0) HorizontalDivider()
                Row(Modifier.fillMaxWidth().clickable { open(o.url) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(o.image, null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                    Spacer(Modifier.width(10.dp))
                    Text((if (o.isPhone) "📱 " else "") + o.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(czk(o.price), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color?, modifier: Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp)).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color ?: MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ImageViewer(images: List<String>, start: Int, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pager = rememberPagerState(initialPage = start) { images.size }
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                AsyncImage(images[page], null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            Text(
                "${pager.currentPage + 1} / ${images.size}",
                Modifier.align(Alignment.BottomCenter).padding(24.dp),
                color = Color.White,
            )
        }
    }
}
