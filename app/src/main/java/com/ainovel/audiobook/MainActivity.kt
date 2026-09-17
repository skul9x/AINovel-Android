package com.ainovel.audiobook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ainovel.audiobook.ui.navigation.AppNavGraph
import com.ainovel.audiobook.ui.theme.AINovelTheme
import com.ainovel.audiobook.ui.viewmodel.AppViewModelProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as AINovelApplication).container
        val factory = AppViewModelProvider.createFactory(container)
        setContent {
            AINovelTheme {
                AppNavGraph(
                    dashboardViewModel = viewModel(factory = factory),
                    generatorViewModel = viewModel(factory = factory),
                    settingsViewModel = viewModel(factory = factory),
                    readerViewModel = viewModel(factory = factory),
                    audioStudioViewModel = viewModel(factory = factory),
                    voicePickerViewModel = viewModel(factory = factory)
                )
            }
        }
    }
}
