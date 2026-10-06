package cz.petrkyncl.bazosiphony

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import cz.petrkyncl.bazosiphony.data.WatchWorker
import cz.petrkyncl.bazosiphony.ui.AccountScreen
import cz.petrkyncl.bazosiphony.ui.AppTheme
import cz.petrkyncl.bazosiphony.ui.DealsScreen
import cz.petrkyncl.bazosiphony.ui.DetailScreen
import cz.petrkyncl.bazosiphony.ui.FavoritesScreen
import cz.petrkyncl.bazosiphony.ui.HomeScreen
import cz.petrkyncl.bazosiphony.ui.ListingScreen
import cz.petrkyncl.bazosiphony.ui.MainViewModel
import cz.petrkyncl.bazosiphony.ui.Route
import cz.petrkyncl.bazosiphony.ui.Tab
import cz.petrkyncl.bazosiphony.ui.WatchScreen
import cz.petrkyncl.bazosiphony.data.Filters

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent { AppTheme { App(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Ťuknutí na notifikaci hlídání → otevřít dané hledání. */
    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_SAVED_SEARCH)?.let {
            vm.savedSearches.reload()
            vm.openSavedSearch(it)
            intent.removeExtra(EXTRA_SAVED_SEARCH)
        }
    }

    companion object {
        const val EXTRA_SAVED_SEARCH = "saved_search_id"
    }
}

private data class TabItem(val tab: Tab, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabItem(Tab.BROWSE, "Procházet", Icons.Default.GridView),
    TabItem(Tab.SEARCH, "Hledat", Icons.Default.Search),
    TabItem(Tab.DEALS, "Výkup", Icons.Default.PhoneIphone),
    TabItem(Tab.FAVORITES, "Oblíbené", Icons.Default.Favorite),
    TabItem(Tab.WATCH, "Hlídání", Icons.Default.Notifications),
)

@Composable
private fun App(vm: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sections by vm.sections.collectAsState()
    val sectionsError by vm.sectionsError.collectAsState()
    val categories by vm.categories.collectAsState()
    val favorites by vm.favorites.items.collectAsState()
    val saved by vm.savedSearches.items.collectAsState()
    val account by vm.account.state.collectAsState()
    val phoneStats by vm.account.phoneStats.collectAsState()
    val favoriteIds = remember(favorites) { favorites.map { it.id }.toSet() }
    val newTotal = saved.sumOf { it.newCount }

    fun notificationsGranted() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var notificationsAllowed by remember { mutableStateOf(notificationsGranted()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAllowed = it }
    fun askNotifications() { if (!notificationsGranted() && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) }

    BackHandler(enabled = vm.canGoBack) { vm.back() }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEach { item ->
                    NavigationBarItem(
                        selected = vm.tab == item.tab,
                        onClick = { vm.selectTab(item.tab) },
                        icon = {
                            if (item.tab == Tab.WATCH && newTotal > 0) BadgedBox(badge = { Badge { Text("$newTotal") } }) { Icon(item.icon, null) }
                            else Icon(item.icon, null)
                        },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        // spotřebovat jen spodní lištu — horní inset (stavový řádek) si řeší TopAppBar jednotlivých obrazovek
        val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
        Box(Modifier.fillMaxSize().padding(bottom).consumeWindowInsets(bottom)) {
          androidx.compose.runtime.CompositionLocalProvider(
              cz.petrkyncl.bazosiphony.ui.LocalGalleryMode provides cz.petrkyncl.bazosiphony.ui.ViewMode.gallery,
              cz.petrkyncl.bazosiphony.ui.LocalImages provides vm.repo::images,
          ) {
            val route = vm.current
            AnimatedContent(
                targetState = route,
                transitionSpec = { (fadeIn() + slideInHorizontally { it / 8 }) togetherWith (fadeOut() + slideOutHorizontally { -it / 8 }) },
                label = "route",
            ) { r ->
                when (r) {
                    Route.Home -> HomeScreen(
                        sections = sections, categories = categories, error = sectionsError, verified = account.verified,
                        onRetry = { vm.loadSections() },
                        onSearch = { vm.selectTab(Tab.SEARCH) },
                        onSection = { vm.openFilters(Filters(section = it)) },
                        onCategory = { sec, cat -> vm.openFilters(Filters(section = sec, categories = listOf(cat))) },
                        onDeals = { vm.selectTab(Tab.DEALS) },
                        onAccount = vm::openAccount,
                    )
                    is Route.Listings -> {
                        val ls by r.c.state.collectAsState()
                        // otevřené uložené hledání = nové inzeráty viděny
                        r.c.savedSearchId?.let { MarkSeen(vm, it, ls.maxId) }
                        ListingScreen(
                            c = r.c, searchMode = r.searchMode, canGoBack = vm.stack.size > 1, repo = vm.repo,
                            favoriteIds = favoriteIds,
                            watched = saved.any { it.filters.watchKey() == ls.filters.watchKey() },
                            onBack = { vm.back() },
                            onOpen = { vm.openAd(it.id, it) },
                            onFavorite = vm::toggleFavorite,
                            onWatch = { f, maxId -> vm.saveSearch(f, maxId); askNotifications() },
                        )
                    }
                    is Route.Deals -> DealsScreen(r.c, onOpen = { vm.openAd(it.id, it.toListing()) })
                    Route.Favorites -> FavoritesScreen(favorites, onOpen = { vm.openAd(it.id, it) }, onFavorite = vm::toggleFavorite)
                    Route.Watch -> WatchScreen(
                        items = saved, notificationsAllowed = notificationsAllowed,
                        onAllowNotifications = ::askNotifications,
                        onOpen = { vm.openFilters(it.filters, it.id) },
                        onNotify = { s, on -> vm.savedSearches.setNotify(s.id, on); if (on) askNotifications() },
                        onRemove = { vm.removeSearch(it.id) },
                    )
                    Route.AccountScreen -> AccountScreen(vm.accountController, onBack = { vm.back() })
                    is Route.Detail -> DetailScreen(
                        c = r.c,
                        favorite = r.c.adId in favoriteIds,
                        verified = account.verified,
                        autoLimitReached = phoneStats.autoReveal && phoneStats.todayCount >= phoneStats.dailyLimit,
                        onBack = { vm.back() },
                        onFavorite = { vm.toggleFavorite(it.toListing()) },
                        onOpenAd = { vm.openAd(it) },
                        onAccount = vm::openAccount,
                    )
                }
            }
          }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { WatchWorker.ensureChannel(context) }
}

@Composable
private fun MarkSeen(vm: MainViewModel, id: String, maxId: Long) {
    androidx.compose.runtime.LaunchedEffect(id, maxId) { if (maxId > 0) vm.savedSearches.markSeen(id, maxId) }
}
