package com.example.aiexpensetrackeropenai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.aiexpensetrackeropenai.di.DefaultAppContainer
import com.example.aiexpensetrackeropenai.ui.MainScreen
import com.example.aiexpensetrackeropenai.ui.MainViewModel
import com.example.aiexpensetrackeropenai.ui.theme.AIExpenseTrackerOpenAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val appContainer = DefaultAppContainer(this)
        val viewModel: MainViewModel by viewModels {
            MainViewModel.provideFactory(
                expenseDao = appContainer.database.expenseDao(),
                openAIApi = appContainer.openAIApi,
                settingsManager = appContainer.settingsManager
            )
        }

        enableEdgeToEdge()
        setContent {
            AIExpenseTrackerOpenAITheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(modifier = Modifier.padding(innerPadding)) {
                        MainScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}