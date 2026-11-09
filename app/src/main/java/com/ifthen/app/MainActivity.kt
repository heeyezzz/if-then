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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ifthen.app.ui.AppViewModel
import com.ifthen.app.ui.screens.ChainEditScreen
import com.ifthen.app.ui.screens.HomeScreen
import com.ifthen.app.ui.screens.OneOffScreen
import com.ifthen.app.ui.screens.ReviewScreen
import com.ifthen.app.ui.screens.RuleEditScreen
import com.ifthen.app.ui.screens.SettingsScreen
import com.ifthen.app.ui.screens.TaskScreen
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
    NavItem("任务", Icons.Filled.TaskAlt),
    NavItem("复盘", Icons.Filled.Insights),
    NavItem("设置", Icons.Filled.Settings)
)

private const val PAGE_TASK = 0
private const val PAGE_REVIEW = 1
private const val PAGE_SETTINGS = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot() {
    val vm: AppViewModel = viewModel()
    val state by vm.ui.collectAsStateWithLifecycle()
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val notifPerm = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) {}
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            appContext.checkSelfPermission("android.permission.POST_NOTIFICATIONS") !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch("android.permission.POST_NOTIFICATIONS")
        }
        vm.rescheduleAllReminders()
    }
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val onTabs = backStackEntry?.destination?.route == "tabs"
    val pagerState = rememberPagerState(initialPage = PAGE_TASK) { navItems.size }
    var showNewSheet by remember { mutableStateOf(false) }
    var taskOneOff by remember { mutableStateOf(false) }

    // 稳定住这两个回调：否则每次重组都是新实例，三个页面全部无法跳过
    val toast: (String) -> Unit = remember(snackbar, scope) { { msg -> scope.launch { snackbar.showSnackbar(msg) } } }
    var editOpen by remember { mutableStateOf(false) }
    var editPickerOpen by remember { mutableStateOf(false) }
    var editRuleId by remember { mutableStateOf(-1L) }
    val editSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val openEditor: (Long) -> Unit = { id -> editRuleId = id; editOpen = true }

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
            if (onTabs && pagerState.currentPage == PAGE_TASK) {
                ExtendedFloatingActionButton(
                    onClick = { if (taskOneOff) openEditor(-1L) else showNewSheet = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(if (taskOneOff) "一次性" else "新建预案") }
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
                    userScrollEnabled = false, // 只允许点底部标签切页，禁掉左右滑动
                    // 启动时就把各页面预组合：否则跳到某一页会在切页动画中途首次组合，尖峰全砸进动画帧
                    beyondViewportPageCount = navItems.size - 1,
                    key = { it },
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    Box(Modifier.fillMaxSize()) {
                        when (page) {
                            PAGE_TASK -> TaskScreen(
                                vm = vm,
                                state = state,
                                oneOff = taskOneOff,
                                onSelectOneOff = { taskOneOff = it },
                                onEdit = openEditor,
                                onEditChain = { navController.navigate("chain_edit/$it") },
                                onRecord = toast
                            )
                            PAGE_REVIEW -> ReviewScreen(
                                vm, state,
                                onEdit = openEditor
                            )
                            else -> SettingsScreen(vm, onToast = toast)
                        }
                    }
                }
            }
            composable("chain_edit/{headId}") { entry ->
                ChainEditScreen(
                    vm = vm,
                    headId = entry.arguments?.getString("headId")?.toLongOrNull() ?: -1L,
                    defaultFolderId = state.currentFolderId,
                    state = state,
                    onDone = { navController.popBackStack() },
                    onToast = toast
                )
            }
        }

        if (editOpen) {
            ModalBottomSheet(
                onDismissRequest = { editOpen = false; editPickerOpen = false },
                sheetState = editSheetState,
                containerColor = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                dragHandle = {
                    if (!editPickerOpen) {
                        Box(
                            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(width = 42.dp, height = 5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
                            )
                        }
                    }
                }
            ) {
                Box(Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
                    RuleEditScreen(
                        vm = vm,
                        ruleId = editRuleId,
                        defaultFolderId = state.currentFolderId,
                        defaultKind = if (taskOneOff) com.ifthen.app.data.RuleKind.ONE_OFF.name
                            else com.ifthen.app.data.RuleKind.REPEATING.name,
                        state = state,
                        onDone = { editOpen = false; editPickerOpen = false },
                        onToast = toast,
                        onPickerVisibilityChange = { editPickerOpen = it }
                    )
                }
            }
        }

        if (showNewSheet) {
            ModalBottomSheet(onDismissRequest = { showNewSheet = false }) {
                Column(Modifier.padding(bottom = 24.dp)) {
                    Text(
                        "新建什么？",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
                    )
                    SheetOption("建一条预案", "只写一个「如果…那就…」", Icons.Filled.Add) {
                        showNewSheet = false
                        openEditor(-1L)
                    }
                    SheetOption("建一串预案", "一次写下好几步，按顺序自动接成链条", Icons.Filled.Link) {
                        showNewSheet = false
                        navController.navigate("chain_edit/-1")
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetOption(title: String, sub: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}
