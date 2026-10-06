package cz.petrkyncl.bazosiphony

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.petrkyncl.bazosiphony.ui.AppTheme
import cz.petrkyncl.bazosiphony.ui.DetailScreen
import cz.petrkyncl.bazosiphony.ui.ListScreen
import cz.petrkyncl.bazosiphony.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                val list by vm.list.collectAsStateWithLifecycle()
                val detail by vm.detail.collectAsStateWithLifecycle()
                val listState = rememberLazyListState() // přežije otevření detailu → návrat na stejné místo

                AnimatedContent(
                    targetState = detail,
                    contentKey = { it?.ad?.id },
                    transitionSpec = {
                        if (targetState != null) (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut()
                        else fadeIn() togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                    },
                    label = "screen",
                ) { d ->
                    if (d == null) {
                        ListScreen(
                            state = list,
                            listState = listState,
                            onOpen = vm::open,
                            onRefresh = vm::refresh,
                            onLoadMore = vm::loadMore,
                            onApplyFilters = vm::applyFilters,
                            onSort = vm::setSort,
                            onOnlyGood = vm::setOnlyGood,
                            onHideExcluded = vm::setHideExcluded,
                            onRefreshPricelist = { vm.refreshPricelist() },
                        )
                    } else {
                        BackHandler(onBack = vm::close)
                        DetailScreen(d, onBack = vm::close, onGrade = { vm.setGrade(it) })
                    }
                }
            }
        }
    }
}
