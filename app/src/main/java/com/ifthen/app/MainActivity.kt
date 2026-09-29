package com.ifthen.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
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

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem("home", "首页", Icons.Filled.Home),
    NavItem("review", "复盘", Icons.Filled.Insights),
    NavItem("settings", "设置", Icons.Filled.Settings)
)

@Composable
private fun AppRoot() {
    val vm: AppViewModel = viewModel()
    val state by vm.ui.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val toast: (String) -> Unit = { msg ->
        scope.launch { snackbar.showSnackbar(msg) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (currentRoute?.startsWith("rule_edit") != true) {
                NavigationBar {
                    navItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
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
            if (currentRoute == "home") {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate("rule_edit/-1") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("新建预案") }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(vm, state, onEdit = { navController.navigate("rule_edit/$it") }, onRecord = toast)
            }
            composable("review") {
                ReviewScreen(vm, state, onEdit = { navController.navigate("rule_edit/$it") })
            }
            composable("settings") {
                SettingsScreen(vm, onToast = toast)
            }
            composable("rule_edit/{ruleId}") { entry ->
                val ruleId = entry.arguments?.getString("ruleId")?.toLongOrNull() ?: -1L
                RuleEditScreen(
                    vm = vm,
                    ruleId = ruleId,
                    defaultFolderId = state.currentFolderId,
                    state = state,
                    onDone = { navController.popBackStack() },
                    onToast = toast
                )
            }
        }
    }
}
