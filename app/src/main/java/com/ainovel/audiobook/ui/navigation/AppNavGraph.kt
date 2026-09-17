package com.ainovel.audiobook.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ainovel.audiobook.R
import com.ainovel.audiobook.ui.screens.AudioStudioScreen
import com.ainovel.audiobook.ui.screens.LiveConsoleScreen
import com.ainovel.audiobook.ui.screens.OutlineEditorScreen
import com.ainovel.audiobook.ui.screens.ReaderScreen
import com.ainovel.audiobook.ui.screens.RotationSettingsScreen
import com.ainovel.audiobook.ui.screens.StudioDashboardScreen
import com.ainovel.audiobook.ui.theme.DarkBackground
import com.ainovel.audiobook.ui.theme.DarkSurface
import com.ainovel.audiobook.ui.theme.DarkSurfaceBorder
import com.ainovel.audiobook.ui.theme.NeonCyan
import com.ainovel.audiobook.ui.theme.NeonLavender
import com.ainovel.audiobook.ui.theme.NeonPurple
import com.ainovel.audiobook.ui.theme.TextPrimary
import com.ainovel.audiobook.ui.theme.TextSecondary
import com.ainovel.audiobook.ui.theme.TextTertiary
import com.ainovel.audiobook.ui.viewmodel.AudioStudioViewModel
import com.ainovel.audiobook.ui.viewmodel.DashboardViewModel
import com.ainovel.audiobook.ui.viewmodel.GeneratorViewModel
import com.ainovel.audiobook.ui.viewmodel.ReaderViewModel
import com.ainovel.audiobook.ui.viewmodel.SettingsViewModel
import com.ainovel.audiobook.ui.viewmodel.VoicePickerViewModel

sealed class Screen(val route: String, val titleRes: Int, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", R.string.nav_dashboard, Icons.Default.Home)
    object Outline : Screen("outline", R.string.nav_outline, Icons.Default.EditNote)
    object LiveConsole : Screen("live_console", R.string.nav_console, Icons.Default.Terminal)
    object Reader : Screen("reader/{novelId}", R.string.nav_reader, Icons.Default.Book) {
        fun createRoute(novelId: String) = "reader/$novelId"
    }
    object AudioStudio : Screen("audio_studio/{novelId}", R.string.nav_audio_studio, Icons.Default.Headphones) {
        fun createRoute(novelId: String) = "audio_studio/$novelId"
    }
    object Settings : Screen("settings", R.string.nav_settings, Icons.Default.Settings)
}

val BottomNavScreens = listOf(
    Screen.Dashboard,
    Screen.Outline,
    Screen.Reader,
    Screen.Settings
)

@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    dashboardViewModel: DashboardViewModel = viewModel(),
    generatorViewModel: GeneratorViewModel = viewModel(),
    readerViewModel: ReaderViewModel = viewModel(),
    audioStudioViewModel: AudioStudioViewModel = viewModel(),
    voicePickerViewModel: VoicePickerViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        bottomBar = {
            val showBottomBar = BottomNavScreens.any { screen ->
                currentDestination?.route?.startsWith(screen.route.substringBefore('/')) == true
            } || currentDestination?.route == Screen.LiveConsole.route || currentDestination?.route?.startsWith("audio_studio") == true

            if (showBottomBar) {
                NavigationBar(
                    containerColor = DarkSurface,
                    modifier = Modifier
                        .wrapContentHeight()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                ) {
                    BottomNavScreens.forEach { screen ->
                        val isSelected = currentDestination?.route?.startsWith(screen.route.substringBefore('/')) == true

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                val targetRoute = if (screen == Screen.Reader) {
                                    Screen.Reader.createRoute("current")
                                } else {
                                    screen.route
                                }
                                navController.navigate(targetRoute) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = stringResource(screen.titleRes)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(screen.titleRes),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = NeonCyan,
                                indicatorColor = NeonPurple,
                                unselectedIconColor = TextTertiary,
                                unselectedTextColor = TextTertiary
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                StudioDashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToCreate = { navController.navigate(Screen.Outline.route) },
                    onNavigateToLiveConsole = { navController.navigate(Screen.LiveConsole.route) },
                    onNavigateToReader = { novelId -> navController.navigate(Screen.Reader.createRoute(novelId)) },
                    onNavigateToAudioStudio = { novelId -> navController.navigate(Screen.AudioStudio.createRoute(novelId)) }
                )
            }

            composable(Screen.Outline.route) {
                OutlineEditorScreen(
                    viewModel = generatorViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onStartWriting = { navController.navigate(Screen.LiveConsole.route) }
                )
            }

            composable(Screen.LiveConsole.route) {
                LiveConsoleScreen(
                    viewModel = generatorViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToReader = { navController.navigate(Screen.Reader.createRoute("generated")) }
                )
            }

            composable(
                route = Screen.Reader.route,
                arguments = listOf(navArgument("novelId") { type = NavType.StringType; defaultValue = "current" })
            ) {
                ReaderScreen(
                    viewModel = readerViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAudioStudio = { navController.navigate(Screen.AudioStudio.createRoute("current")) }
                )
            }

            composable(
                route = Screen.AudioStudio.route,
                arguments = listOf(navArgument("novelId") { type = NavType.StringType; defaultValue = "current" })
            ) {
                AudioStudioScreen(
                    studioViewModel = audioStudioViewModel,
                    voicePickerViewModel = voicePickerViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                RotationSettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
