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

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.window.DialogProperties
import com.example.aiexpensetrackeropenai.util.UpdateManager
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp

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
                val context = LocalContext.current
                val coroutineScope = rememberCoroutineScope()
                val updateInfo by UpdateManager.updateInfo.collectAsState()
                val downloadProgress by UpdateManager.downloadProgress.collectAsState()

                LaunchedEffect(Unit) {
                    UpdateManager.checkUpdate(BuildConfig.VERSION_NAME)
                }

                if (updateInfo != null) {
                    AlertDialog(
                        onDismissRequest = { },
                        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                        title = { Text("Bản Cập Nhật Mới") },
                        text = {
                            Column {
                                Text("Phiên bản ${updateInfo!!.version} đã sẵn sàng. Bạn cần cập nhật để tiếp tục sử dụng ứng dụng.")
                                if (updateInfo!!.releaseNotes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Chi tiết:\n${updateInfo!!.releaseNotes}")
                                }
                                if (downloadProgress != null) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Đang tải... ${(downloadProgress!! * 100).toInt()}%")
                                    LinearProgressIndicator(
                                        progress = downloadProgress!!,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        UpdateManager.downloadAndInstall(context, updateInfo!!.downloadUrl)
                                    }
                                },
                                enabled = downloadProgress == null
                            ) {
                                Text(if (downloadProgress == null) "Cập Nhật Ngay" else "Đang Tải...")
                            }
                        }
                    )
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(modifier = Modifier.padding(innerPadding)) {
                        MainScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}