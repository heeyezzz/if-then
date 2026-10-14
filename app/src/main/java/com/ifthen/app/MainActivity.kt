package com.ifthen.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ifthen.app.ui.AppViewModel
import com.ifthen.app.ui.screens.HomeScreen
import com.ifthen.app.ui.screens.OneOffScreen
import com.ifthen.app.ui.screens.ReviewScreen
import com.ifthen.app.ui.screens.RuleEditScreen
import com.ifthen.app.ui.screens.SettingsScreen
import com.ifthen.app.ui.theme.IfThenTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.ifthen.app.ui.SuccessChime.init(this)
        setContent {
            IfThenTheme { AppRoot() }
        }
    }
}

private data class NavItem(val label: String, val icon: ImageVector)

/** 4 个板块收进一个 pager：空白处左右滑即可切页，卡片上的横向手势仍归卡片自己 */
private val navItems = listOf(
    NavItem("一次性", Icons.Filled.TaskAlt),
    NavItem("长期", Icons.Filled.Repeat),
    NavItem("复盘", Icons.Filled.Insights),
    NavItem("设置", Icons.Filled.Settings)
)

private const val PAGE_ONE_OFF = 0
private const val PAGE_LONG_TERM = 1

@Composable
private fun AppRoot() {
    val vm: AppViewModel = viewModel()
    val state by vm.ui.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val onTabs = backStackEntry?.destination?.route == "tabs"
    val pagerState = rememberPagerState(initialPage = PAGE_LONG_TERM) { navItems.size }

    // 稳定住这两个回调：否则每次重组都是新实例，三个页面全部无法跳过
    val toast: (String) -> Unit = remember(snackbar, scope) { { msg -> scope.launch { snackbar.showSnackbar(msg) } } }
    val onEdit = remember(navController) { { id: Long -> navController.navigate("rule_edit/$id") } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (onTabs) {
                NavigationBar {
                    navItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                if (pagerState.currentPage != index) {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (onTabs && pagerState.currentPage <= PAGE_LONG_TERM) {
                val oneOff = pagerState.currentPage == PAGE_ONE_OFF
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate("rule_edit/-1") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(if (oneOff) "一次性" else "新建预案") }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "tabs",
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                slideInHorizontally(animationSpec = tween(260, easing = LinearOutSlowInEasing)) { it / 8 } +
                    fadeIn(animationSpec = tween(260))
            },
            exitTransition = { fadeOut(animationSpec = tween(160)) },
            popEnterTransition = { fadeIn(animationSpec = tween(220)) },
            popExitTransition = {
                slideOutHorizontally(animationSpec = tween(240, easing = FastOutLinearInEasing)) { it / 8 } +
                    fadeOut(animationSpec = tween(240))
            }
        ) {
            composable("tabs") {
                HorizontalPager(
                    state = pagerState,
                    // 启动时就把四个页面全部预组合：否则跳到某一页会让下一页在切页动画中途首次组合，尖峰全砸进动画帧
                    beyondViewportPageCount = navItems.size - 1,
                    key = { it },
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    Box(Modifier.fillMaxSize()) {
                        when (page) {
                            PAGE_ONE_OFF -> OneOffScreen(
                                vm, state,
                                onEdit = { navController.navigate("rule_edit/$it") },
                                onRecord = toast
                            )
                            PAGE_LONG_TERM -> HomeScreen(
                                vm, state,
                                onEdit = { navController.navigate("rule_edit/$it") },
                                onRecord = toast
                            )
                            2 -> ReviewScreen(
                                vm, state,
                                onEdit = { navController.navigate("rule_edit/$it") }
                            )
                            else -> SettingsScreen(vm, onToast = toast)
                        }
                    }
                }
            }
            composable("rule_edit/{ruleId}") { entry ->
                val ruleId = entry.arguments?.getString("ruleId")?.toLongOrNull() ?: -1L
                RuleEditScreen(
                    vm = vm,
                    ruleId = ruleId,
                    defaultFolderId = state.currentFolderId,
                    defaultKind = if (pagerState.currentPage == PAGE_ONE_OFF) {
                        com.ifthen.app.data.RuleKind.ONE_OFF.name
                    } else {
                        com.ifthen.app.data.RuleKind.REPEATING.name
                    },
                    state = state,
                    onDone = { navController.popBackStack() },
                    onToast = toast
                )
            }
        }
    }
}
