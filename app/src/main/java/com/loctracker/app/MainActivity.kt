package com.loctracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.loctracker.app.ui.Copy
import com.loctracker.app.ui.MainViewModel
import com.loctracker.app.ui.screens.Bullets
import com.loctracker.app.ui.screens.DashboardScreen
import com.loctracker.app.ui.screens.EditorScreen
import com.loctracker.app.ui.screens.HistoryScreen
import com.loctracker.app.ui.screens.InfoScreen
import com.loctracker.app.ui.screens.RecipientsScreen
import com.loctracker.app.ui.theme.LocTrackerTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LocTrackerTheme { AppRoot(vm) } }
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Map("map", "Map", Icons.Filled.Map),
    Recipients("recipients", "People", Icons.Filled.People),
    History("history", "History", Icons.Filled.History),
    Info("info", "About", Icons.Filled.Info),
}

@Composable
private fun AppRoot(vm: MainViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    var onboarding by remember { mutableStateOf(!vm.isOnboarded()) }

    Scaffold(
        bottomBar = {
            if (route != null && route.startsWith("edit").not()) {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(nav, startDestination = Tab.Map.route) {
            composable(Tab.Map.route) {
                DashboardScreen(vm, onAddRecipient = { nav.navigate("edit/0") }, bottomPadding = inner.calculateBottomPadding())
            }
            composable(Tab.Recipients.route) { RecipientsScreen(vm, onEdit = { nav.navigate("edit/$it") }, padding = inner) }
            composable(Tab.History.route) { HistoryScreen(vm, inner) }
            composable(Tab.Info.route) { InfoScreen(vm, inner) }
            composable("edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                EditorScreen(vm, it.arguments?.getLong("id") ?: 0L, onDone = { nav.popBackStack() }, padding = PaddingValues())
            }
        }
    }

    if (onboarding) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(Copy.LIMIT_TITLE) },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    Text(Copy.LIMIT_SUMMARY)
                    Bullets(Copy.LIMIT_POINTS.take(3))
                }
            },
            confirmButton = { TextButton(onClick = { vm.setOnboarded(); onboarding = false }) { Text("I understand") } },
        )
    }
}
