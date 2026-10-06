package cz.petrkyncl.bazosiphony.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage

/** Načtení všech fotek inzerátu (z detailu, s cache) — poskytuje MainActivity. */
val LocalImages = staticCompositionLocalOf<suspend (String) -> List<String>> { { emptyList() } }

/**
 * Fotky inzerátu vždy celé (ContentScale.Fit, žádný ořez) s listováním do stran.
 * Výška je pevná, aby se seznam při načítání neposouval; na šířku i na výšku focené fotky se vejdou.
 */
@Composable
fun PhotoPager(images: List<String>, height: Dp, modifier: Modifier = Modifier, onClick: (Int) -> Unit) {
    if (images.isEmpty()) {
        Box(modifier.fillMaxWidth().height(height).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        return
    }
    val pager = rememberPagerState { images.size }
    Box(modifier.fillMaxWidth().height(height).background(Color(0xFF0B0B0C))) {
        // jeden swipe = jedna fotka (i při rychlém švihnutí)
        HorizontalPager(
            pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1,
            flingBehavior = PagerDefaults.flingBehavior(pager, pagerSnapDistance = PagerSnapDistance.atMost(1)),
        ) { page ->
            AsyncImage(
                images[page], null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().pointerInput(page) { detectTapGestures(onTap = { onClick(page) }) },
            )
        }
        if (images.size > 1) {
            Text(
                "${pager.currentPage + 1} / ${images.size}",
                Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 2.dp),
                color = Color.White, style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** Fotky přes celou obrazovku: listování, přiblížení dvěma prsty i dvojitým ťuknutím, posun přiblížené fotky. */
@Composable
fun PhotoViewer(images: List<String>, start: Int, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val pager = rememberPagerState(initialPage = start) { images.size }
        var zoomed by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // při přiblížení se nelistuje (tah posouvá fotku)
            HorizontalPager(
                pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomed,
                flingBehavior = PagerDefaults.flingBehavior(pager, pagerSnapDistance = PagerSnapDistance.atMost(1)),
            ) { page ->
                ZoomableImage(images[page], active = page == pager.currentPage, onZoomChange = { if (page == pager.currentPage) zoomed = it })
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
                Icon(Icons.Default.Close, "Zavřít", tint = Color.White)
            }
            if (images.size > 1) {
                Text(
                    "${pager.currentPage + 1} / ${images.size}",
                    Modifier.align(Alignment.BottomCenter).padding(28.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp),
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(url: String, active: Boolean, onZoomChange: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val shownScale by animateFloatAsState(scale, label = "zoom")

    // jiná fotka v pageru → zpět na celou
    LaunchedEffect(active) { if (!active) { scale = 1f; offset = Offset.Zero } }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = size.width * (s - 1) / 2f
        val maxY = size.height * (s - 1) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    AsyncImage(
        url, null, contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { tap ->
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else {
                        scale = 2.5f
                        // přiblížit k místu ťuknutí
                        val center = Offset(size.width / 2f, size.height / 2f)
                        offset = clamp((center - tap) * (scale - 1), scale)
                    }
                    onZoomChange(scale > 1f)
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        // dva prsty = zoom; jeden prst posouvá jen přiblíženou fotku (jinak listuje pager)
                        if (event.changes.size > 1 || scale > 1f) {
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale == 1f) Offset.Zero else clamp(offset + event.calculatePan(), scale)
                            onZoomChange(scale > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = shownScale
                scaleY = shownScale
                translationX = offset.x
                translationY = offset.y
            },
    )
}
