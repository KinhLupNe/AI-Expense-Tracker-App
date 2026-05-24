package com.example.aiexpensetrackeropenai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CameraAlt
import java.util.Calendar
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.example.aiexpensetrackeropenai.data.local.ExpenseEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.text.NumberFormat

private val vnNumberFormat: NumberFormat = NumberFormat.getNumberInstance(vnLocale)

fun formatVnCurrency(amount: Number): String {
    // NumberFormat không thread-safe nhưng UI chỉ gọi từ main thread → OK
    return vnNumberFormat.format(amount.toLong())
}

fun formatShortCurrency(amount: Double): String {
    return when {
        amount >= 1_000_000_000 -> String.format(java.util.Locale.US, "%.1ftỷ", amount / 1_000_000_000).replace(".0tỷ", "tỷ")
        amount >= 1_000_000 -> String.format(java.util.Locale.US, "%.1ftr", amount / 1_000_000).replace(".0tr", "tr")
        amount >= 1_000 -> String.format(java.util.Locale.US, "%.0fk", amount / 1_000)
        else -> String.format(java.util.Locale.US, "%.0f", amount)
    }
}

// Phát file âm thanh trong res/raw — auto-release khi xong, không lưu giữ MediaPlayer.
// onDone() được gọi khi audio phát xong (hoặc lỗi) để có thể chain phát file tiếp.
private fun playRawSound(
    context: android.content.Context,
    rawResId: Int,
    volume: Float = 0.6f,
    onDone: () -> Unit = {}
) {
    try {
        val mp = android.media.MediaPlayer.create(context, rawResId)
        if (mp == null) {
            onDone()
            return
        }
        mp.setVolume(volume, volume)
        mp.setOnCompletionListener { player ->
            kotlin.runCatching { player.release() }
            onDone()
        }
        mp.setOnErrorListener { player, _, _ ->
            kotlin.runCatching { player.release() }
            onDone()
            true
        }
        mp.start()
    } catch (e: Exception) {
        e.printStackTrace()
        onDone()
    }
}

fun playSuccessTing(context: android.content.Context) {
    playRawSound(context, com.example.aiexpensetrackeropenai.R.raw.sound_success, 0.7f)
}

// Beep trước, beep xong thì phát giọng "Fail.mp3".
fun playFailureTone(context: android.content.Context) {
    playRawSound(context, com.example.aiexpensetrackeropenai.R.raw.sound_failure, 0.7f) {
        playRawSound(context, com.example.aiexpensetrackeropenai.R.raw.sound_fail_voice, 0.85f)
    }
}

private data class QuickReply(val shortText: String, val fullQuestion: String)

fun isWhisperJunk(text: String): Boolean {
    val cleaned = text.trim()
    val lower = cleaned.lowercase()
    if (cleaned.isEmpty() || cleaned.length < 3) return true
    if (cleaned.all { !it.isLetterOrDigit() }) return true
    val knownHallucinations = listOf(
        "cảm ơn", "thank you", "thanks for watching", "subscribe",
        "phụ đề", "subtitle", "hẹn gặp lại", "video tiếp theo",
        "các bạn đã xem", "các bạn đã theo dõi", "xem video", "đã xem",
        "tiếp theo nhé", "tiếng việt"
    )
    if (knownHallucinations.any { lower.contains(it) }) return true
    return lower in setOf("bạn", "xin chào", "ok", "okay", "yeah", "uh", "um", "you", "hi", "hello", ".")
}

fun encodeImageUriToBase64(context: android.content.Context, uri: android.net.Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes()
        inputStream?.close()
        bytes?.let {
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)
            val maxDim = 1024
            val scale = kotlin.math.min(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
            val resized = if (scale < 1) {
                android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else {
                bitmap
            }
            val outputStream = java.io.ByteArrayOutputStream()
            resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
            android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
        }
    } catch (e: Exception) {
        null
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val apiKey by viewModel.apiKey.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val groupedExpenses by viewModel.groupedExpenses.collectAsState()
    val categorySummaries by viewModel.categorySummaries.collectAsState()
    val savingsSummary by viewModel.savingsSummary.collectAsState()
    val debtSummary by viewModel.debtSummary.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val amplitude by viewModel.currentAudioAmplitude.collectAsState()

    val monthlyExpensesForStats by viewModel.monthlyExpensesForStats.collectAsState()
    val webhookUrl by viewModel.webhookUrl.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val pendingReceiptExpenses by viewModel.pendingReceiptExpenses.collectAsState()
    val lastAddedExpense by viewModel.lastAddedExpense.collectAsState()
    val lastAddedCount by viewModel.lastAddedCount.collectAsState()
    val addExpenseFailure by viewModel.addExpenseFailure.collectAsState()


    var inputText by remember { mutableStateOf("") }
    var aiInputText by remember { mutableStateOf("") }

    var transcriptionTarget by remember {
        mutableStateOf<(String) -> Unit>({ text ->
            inputText = if (inputText.isBlank()) text else inputText + " " + text
        })
    }

    val selectedMonthOffset by viewModel.selectedMonthOffset.collectAsState()
    val monthWeeks by viewModel.monthWeeks.collectAsState()
    val selectedWeek by viewModel.selectedWeek.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Thống kê", "Sổ thu chi", "Sổ sách", "Hỏi con")
    var showSettingsDialog by remember { mutableStateOf(false) }
    val sheetUrl by viewModel.sheetUrl.collectAsState()



    val context = androidx.compose.ui.platform.LocalContext.current


    val isTranscribing by viewModel.isTranscribing.collectAsState()

    var isRecording by remember { mutableStateOf(false) }
    var recordTimeRemaining by remember { mutableStateOf(20) }

    fun beginRecording() {
        if (viewModel.startRecording(context)) {
            viewModel.startAmplitudeTracking { viewModel.getRecordingAmplitude() }
            isRecording = true
            recordTimeRemaining = 20
        }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            beginRecording()
        } else {
            android.widget.Toast.makeText(context, "Mẹ cho con quyền ghi âm nhé", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (recordTimeRemaining > 0 && isRecording) {
                kotlinx.coroutines.delay(1000)
                recordTimeRemaining -= 1
            }
            if (isRecording) {
                viewModel.stopAmplitudeTracking()
                isRecording = false
                val target = transcriptionTarget
                viewModel.stopRecordingAndTranscribe { text ->
                    if (!isWhisperJunk(text)) {
                        target(text)
                    } else {
                        android.widget.Toast.makeText(context, "Con chưa nghe rõ, mẹ thử lại nhé!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopAmplitudeTracking()
        }
    }

    val isImeVisible = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    var editingExpense by remember { mutableStateOf<com.example.aiexpensetrackeropenai.data.local.ExpenseEntity?>(null) }
    var showImageOptions by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    
    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            val base64 = encodeImageUriToBase64(context, it)
            if (base64 != null) {
                viewModel.parseReceiptImage(base64)
            }
        }
    }
    
    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            tempCameraUri?.let {
                val base64 = encodeImageUriToBase64(context, it)
                if (base64 != null) {
                    viewModel.parseReceiptImage(base64)
                }
            }
        }
    }

    fun createTempImageUri(): android.net.Uri {
        val imagePath = java.io.File(context.cacheDir, "images")
        imagePath.mkdirs()
        val file = java.io.File.createTempFile("receipt_", ".jpg", imagePath)
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                actionLabel = "Đã hiểu",
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Success: phát ting bell
    LaunchedEffect(lastAddedExpense?.id) {
        if (lastAddedExpense != null) playSuccessTing(context)
    }

    // Failure: phát beep + auto-clear sau 5s
    LaunchedEffect(addExpenseFailure) {
        if (addExpenseFailure == null) return@LaunchedEffect
        playFailureTone(context)
        kotlinx.coroutines.delay(5000)
        viewModel.clearAddExpenseFailure()
    }

    // Auto-dismiss success sau 5s
    LaunchedEffect(lastAddedExpense, selectedTabIndex) {
        if (lastAddedExpense == null) return@LaunchedEffect
        kotlinx.coroutines.delay(5000L)
        viewModel.clearLastAddedExpense()
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Color.White,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    actionColor = MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        },
        bottomBar = {
            if (!isImeVisible) {
                NavigationBar(
                    modifier = Modifier.height(64.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            label = { Text(title) },
                            icon = { 
                                Icon(
                                    imageVector = when(index) {
                                        0 -> Icons.Default.PieChart
                                        1 -> Icons.AutoMirrored.Filled.List
                                        2 -> Icons.Default.Folder
                                        else -> Icons.AutoMirrored.Filled.Chat
                                    }, 
                                    contentDescription = title
                                )
                            }
                        )
                    }
                }
            }
        },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val todayStrFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM")
                val currentTodayStr = java.time.LocalDate.now().format(todayStrFormatter)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Button(
                            onClick = {
                                selectedTabIndex = 1
                                viewModel.setMonthOffset(0)
                                if (groupedExpenses.isNotEmpty()) {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(0)
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Text(
                                text = "Thu chi của mẹ",
                                style = androidx.compose.ui.text.TextStyle(
                                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFFE91E63),
                                            Color(0xFF9C27B0),
                                            Color(0xFF3F51B5)
                                        )
                                    ),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = currentTodayStr,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.3.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (syncStatus) {
                            SyncStatus.SYNCING -> Icon(Icons.Default.CloudUpload, contentDescription = "Syncing", tint = Color.Gray, modifier = Modifier.size(20.dp))
                            SyncStatus.SUCCESS -> Icon(Icons.Default.CloudDone, contentDescription = "Synced", tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                            SyncStatus.ERROR -> {
                                IconButton(onClick = { viewModel.syncPendingExpenses() }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.CloudOff, contentDescription = "Sync Error", tint = Color.Red, modifier = Modifier.size(20.dp))
                                }
                            }
                            SyncStatus.IDLE -> {}
                        }
                        
                        if (!sheetUrl.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(onClick = {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(sheetUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = "Mở Google Sheets", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = { showSettingsDialog = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = "Cài đặt", tint = Color.Gray)
                        }
                    }
                }

                // Settings Dialog
                if (showSettingsDialog) {
                        var apiKeyInput by remember { mutableStateOf(apiKey ?: "") }
                        val autoReadTts by viewModel.autoReadTts.collectAsState()
                        val ttsVoice by viewModel.ttsVoice.collectAsState()
                        
                        AlertDialog(
                            onDismissRequest = { showSettingsDialog = false },
                            title = { Text("Cài đặt", fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("OpenAI API Key (AI Hỏi đáp)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                        OutlinedTextField(
                                            value = apiKeyInput,
                                            onValueChange = { apiKeyInput = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            placeholder = { Text("sk-proj-...", color = Color.Gray) },
                                            singleLine = true
                                        )
                                    }
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("AI Tự động đọc", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                            Text("Bật để con tự đọc to câu trả lời.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        }
                                        androidx.compose.material3.Switch(
                                            checked = autoReadTts,
                                            onCheckedChange = { viewModel.setAutoReadTts(it) }
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Giọng đọc", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                        Text("Chọn giọng cho con đọc to.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val voices = listOf(
                                                "male" to "Nam (Lê Minh)",
                                                "female" to "Nữ (Ban Mai)"
                                            )
                                            voices.forEach { (value, label) ->
                                                val selected = ttsVoice == value
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable { viewModel.setTtsVoice(value) },
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                    border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                                                ) {
                                                    Text(
                                                        text = label,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 10.dp),
                                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        textAlign = TextAlign.Center,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(onClick = {
                                    viewModel.saveApiKey(apiKeyInput.trim())
                                    showSettingsDialog = false
                                }) {
                                    Text("Lưu")
                                }
                            },
                        dismissButton = {
                            TextButton(onClick = { showSettingsDialog = false }) {
                                Text("Huỷ")
                            }
                        }
                    )
                }
                
                if (selectedTabIndex == 0 || selectedTabIndex == 1) {
                    val calendar = java.util.Calendar.getInstance()
                    calendar.add(java.util.Calendar.MONTH, selectedMonthOffset)
                    val monthStr = sdfMonthYear.format(calendar.time)
                    val monthNumber = sdfMonthNum.format(calendar.time)
                    val totalMonthExpense = monthlyExpensesForStats.filter { it.type == "expense" && it.category != "Tiết kiệm" && it.category != "Cho vay" }.sumOf { it.amount }
                    val totalMonthIncome = monthlyExpensesForStats.filter { it.type == "income" && it.category != "Tiết kiệm" && it.category != "Cho vay" }.sumOf { it.amount }
                    val netMonthChange = totalMonthIncome - totalMonthExpense
                    
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.wrapContentWidth().align(Alignment.Center)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                IconButton(onClick = { viewModel.setMonthOffset(selectedMonthOffset - 1) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Tháng trước", modifier = Modifier.size(20.dp))
                                }
                                Text(
                                    "Tháng $monthStr", 
                                    style = MaterialTheme.typography.labelLarge, 
                                    fontWeight = FontWeight.Bold, 
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(onClick = { viewModel.setMonthOffset(selectedMonthOffset + 1) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Tháng sau", modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                    if (selectedTabIndex == 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(0.95f)
                                .align(Alignment.CenterHorizontally),
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFFF0F0F0)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left column: Chi (top) / Thu (bottom)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(20.dp))
                                        Text(
                                            "${formatVnCurrency(totalMonthExpense)}đ",
                                            color = Color(0xFFE53935),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ArrowDropUp, contentDescription = null, tint = Color(0xFF009688), modifier = Modifier.size(20.dp))
                                        Text(
                                            "${formatVnCurrency(totalMonthIncome)}đ",
                                            color = Color(0xFF009688),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1
                                        )
                                    }
                                }

                                // Right: Net total
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("= ", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                                    Text(
                                        "${formatVnCurrency(netMonthChange)}đ",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 8.dp)
        ) {
        
        // Removed stand-alone Today text

        if (isRecording || isTranscribing) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { /* handle by OK button */ }) {
                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (isTranscribing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                PulsingDot()
                            }
                            Text(
                                text = if (isTranscribing) "Con đang nghĩ..." else "Con đang nghe mẹ...",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = if (isTranscribing) "Đang chuyển giọng nói thành chữ..." else "${recordTimeRemaining}s còn lại",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        AudioWaveform(
                            amplitude = if (isTranscribing) 0.3f else amplitude,
                            modifier = Modifier.fillMaxWidth().height(90.dp)
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        if (!isTranscribing) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.stopAmplitudeTracking()
                                        isRecording = false
                                        viewModel.cancelRecording()
                                        focusManager.clearFocus()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(vertical = 14.dp)
                                ) {
                                    Text("Huỷ", fontSize = 16.sp, color = Color.Gray)
                                }
                                Button(
                                    onClick = {
                                        viewModel.stopAmplitudeTracking()
                                        isRecording = false
                                        val target = transcriptionTarget
                                        viewModel.stopRecordingAndTranscribe { text ->
                                            if (!isWhisperJunk(text)) {
                                                target(text)
                                            } else {
                                                android.widget.Toast.makeText(context, "Con chưa nghe rõ mẹ ạ", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        focusManager.clearFocus()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                                    contentPadding = PaddingValues(vertical = 14.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Xong rồi", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedContent(
            targetState = selectedTabIndex,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally(
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                ) { fullWidth -> direction * (fullWidth / 4) } + fadeIn(
                    animationSpec = tween(durationMillis = 260)
                )) togetherWith (slideOutHorizontally(
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                ) { fullWidth -> -direction * (fullWidth / 4) } + fadeOut(
                    animationSpec = tween(durationMillis = 200)
                ))
            },
            label = "tabTransition",
            modifier = Modifier.fillMaxSize()
        ) { tabIndex ->
        when (tabIndex) {
            1 -> {
                // Sổ Thu Chi
                Column(modifier = Modifier.fillMaxSize()) {
                    
                    LaunchedEffect(listState, groupedExpenses, monthWeeks) {
                        androidx.compose.runtime.snapshotFlow {
                            val visibleItems = listState.layoutInfo.visibleItemsInfo
                            val isAtBottom = !listState.canScrollForward && visibleItems.isNotEmpty()
                            if (isAtBottom) {
                                visibleItems.lastOrNull { it.key is String && (it.key as String).startsWith("header_") }?.key
                            } else {
                                visibleItems.firstOrNull { it.key is String && (it.key as String).startsWith("header_") }?.key
                            } ?: visibleItems.firstOrNull()?.key
                        }.collect { activeItemKey ->
                            if (activeItemKey != null) {
                                if (activeItemKey is Int) {
                                    val expenseId = activeItemKey
                                    val expense = groupedExpenses.flatMap { it.second }.find { it.id == expenseId }
                                    if (expense != null) {
                                        val date = java.time.Instant.ofEpochMilli(expense.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                                        val weekIndex = monthWeeks.indexOfFirst { weekInfo ->
                                            !date.isBefore(weekInfo.startDate) && !date.isAfter(weekInfo.endDate)
                                        }
                                        if (weekIndex != -1 && selectedWeek != weekIndex + 1) {
                                            viewModel.setWeekFilter(weekIndex + 1)
                                        }
                                    }
                                } else if (activeItemKey is String && activeItemKey.startsWith("header_")) {
                                    val dateStr = activeItemKey.removePrefix("header_")
                                    val group = groupedExpenses.find { it.first == dateStr }
                                    val firstExpense = group?.second?.firstOrNull()
                                    if (firstExpense != null) {
                                        val date = java.time.Instant.ofEpochMilli(firstExpense.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                                        val weekIndex = monthWeeks.indexOfFirst { weekInfo ->
                                            !date.isBefore(weekInfo.startDate) && !date.isAfter(weekInfo.endDate)
                                        }
                                        if (weekIndex != -1 && selectedWeek != weekIndex + 1) {
                                            viewModel.setWeekFilter(weekIndex + 1)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Đã xoá thanh điều hướng Tuần
                
                    ExpenseTable(
                        groupedExpenses = groupedExpenses,
                        onDelete = { viewModel.deleteExpense(it) },
                        onEdit = { editingExpense = it },
                        highlightExpenseId = lastAddedExpense?.id,
                        modifier = Modifier.weight(1f),
                        listState = listState
                    )
                    
                    // AI Input Bar
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxWidth().imePadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Mẹ mua gì vậy?", color = Color.Gray) },
                                maxLines = 3,
                                shape = RoundedCornerShape(24.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                            leadingIcon = {
                                Row(
                                    modifier = Modifier.padding(start = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                                        .clickable(enabled = !isLoading && !isTranscribing) {
                                            transcriptionTarget = { text ->
                                                inputText = if (inputText.isBlank()) text else inputText + " " + text
                                            }
                                            val isGranted = ContextCompat.checkSelfPermission(
                                                context, Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (isGranted) {
                                                beginRecording()
                                            } else {
                                                recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = "Giọng nói",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                                        .clickable(enabled = !isLoading) {
                                            showImageOptions = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        contentDescription = "Chụp ảnh",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            },
                            trailingIcon = {
                                val canSend = inputText.isNotBlank() && !isLoading
                                Box(
                                            modifier = Modifier
                                        .padding(end = 6.dp)
                                        .size(40.dp)
                                        .background(
                                            if (canSend) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                            CircleShape
                                        )
                                        .clickable(enabled = canSend) {
                                            viewModel.parseExpense(inputText)
                                            inputText = ""
                                            focusManager.clearFocus()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Gửi",
                                            tint = if (canSend) MaterialTheme.colorScheme.onPrimary else Color.Gray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }
                    }
                }
            }
            0 -> {
                // Thống Kê - dùng dữ liệu cả tháng
                var selectedCategoryForDetails by remember { mutableStateOf<String?>(null) }
                val analyzeState by viewModel.analyzeState.collectAsState()
                val analysisText by viewModel.analysisText.collectAsState()
                
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Thẻ Thu Chi (Thay đổi ròng)
                        val totalIncomeStats = monthlyExpensesForStats.filter { it.type == "income" && it.category != "Tiết kiệm" && it.category != "Cho vay" }.sumOf { it.amount }
                        val totalExpenseStats = monthlyExpensesForStats.filter { it.type == "expense" && it.category != "Tiết kiệm" && it.category != "Cho vay" }.sumOf { it.amount }
                        val netChangeStats = totalIncomeStats - totalExpenseStats
                        
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F7FA)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Thu Chi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.Black)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("${formatVnCurrency(netChangeStats)}đ", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.Black)
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // Chi phí box
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("Chi phí", color = Color(0xFFE53935), fontSize = 14.sp)
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFFE53935))
                                                Text("${formatVnCurrency(totalExpenseStats)}đ", color = Color(0xFFE53935), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            }
                                        }
                                    }
                                    
                                    // Thu nhập box
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("Thu nhập", color = Color(0xFF009688), fontSize = 14.sp)
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                                Icon(Icons.Default.ArrowDropUp, contentDescription = null, tint = Color(0xFF009688))
                                                Text("${formatVnCurrency(totalIncomeStats)}đ", color = Color(0xFF009688), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column {
                                ExpensePieChart(
                                    categorySummaries = categorySummaries
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Chi tiết Danh mục",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )

                                val totalPieExpense = categorySummaries.sumOf { it.totalAmount }
                                categorySummaries.forEach { summary ->
                                    val percentage = if (totalPieExpense > 0) ((summary.totalAmount / totalPieExpense) * 100).toInt() else 0
                                    CategoryDetailItem(
                                        summary = summary, 
                                        percentage = percentage,
                                        onClick = { selectedCategoryForDetails = summary.category }
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                    

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .clickable { selectedCategoryForDetails = "Tiết kiệm" },
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Tiết kiệm tích luỹ trong năm", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = formatVnCurrency(savingsSummary.totalAmount),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Khoản này không tính vào tổng chi tiêu", style = MaterialTheme.typography.bodySmall, color = Color(0xFF388E3C))
                                }
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Xem chi tiết", tint = Color(0xFF2E7D32), modifier = Modifier.size(32.dp))
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .clickable { selectedCategoryForDetails = "Cho vay" },
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE7F6))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Cho vay trong năm", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF512DA8))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = formatVnCurrency(debtSummary.totalAmount),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF512DA8)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Khoản này không tính vào tổng chi tiêu", style = MaterialTheme.typography.bodySmall, color = Color(0xFF673AB7))
                                }
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Xem chi tiết", tint = Color(0xFF512DA8), modifier = Modifier.size(32.dp))
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp)) // Add space for FAB
                    }
                } // End of LazyColumn
                
                    // AI Input Bar (Copied to Thống Kê)
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .imePadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Mẹ mua gì vậy?", color = Color.Gray) },
                                maxLines = 3,
                                shape = RoundedCornerShape(24.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                            leadingIcon = {
                                Row(
                                    modifier = Modifier.padding(start = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                                        .clickable(enabled = !isLoading && !isTranscribing) {
                                            transcriptionTarget = { text ->
                                                inputText = if (inputText.isBlank()) text else inputText + " " + text
                                            }
                                            val isGranted = ContextCompat.checkSelfPermission(
                                                context, Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (isGranted) {
                                                beginRecording()
                                            } else {
                                                recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = "Giọng nói",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                                        .clickable(enabled = !isLoading) {
                                            showImageOptions = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        contentDescription = "Chụp ảnh",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            },
                            trailingIcon = {
                                val canSend = inputText.isNotBlank() && !isLoading
                                Box(
                                            modifier = Modifier
                                        .padding(end = 6.dp)
                                        .size(40.dp)
                                        .background(
                                            if (canSend) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                            CircleShape
                                        )
                                        .clickable(enabled = canSend) {
                                            viewModel.parseExpense(inputText)
                                            inputText = ""
                                            focusManager.clearFocus()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Gửi",
                                            tint = if (canSend) MaterialTheme.colorScheme.onPrimary else Color.Gray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }
                    }



                if (selectedCategoryForDetails != null) {
                    val isYearlyCategory = selectedCategoryForDetails == "Tiết kiệm" || selectedCategoryForDetails == "Cho vay"
                    val categoryExpenses = if (isYearlyCategory) {
                        val targetYear = java.time.LocalDate.now().plusMonths(selectedMonthOffset.toLong()).year
                        expenses.filter { 
                            val expDate = java.time.Instant.ofEpochMilli(it.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                            expDate.year == targetYear && it.category == selectedCategoryForDetails
                        }.sortedByDescending { it.timestamp }
                    } else {
                        monthlyExpensesForStats.filter { it.category == selectedCategoryForDetails }.sortedByDescending { it.timestamp }
                    }
                    
                    val groupedByMonth = categoryExpenses.groupBy { sdfMonthYear.format(Date(it.timestamp)) }

                    AlertDialog(
                        onDismissRequest = { selectedCategoryForDetails = null },
                        title = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Chi tiết: $selectedCategoryForDetails", 
                                    fontWeight = FontWeight.Bold, 
                                    style = MaterialTheme.typography.titleMedium, 
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { selectedCategoryForDetails = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "Đóng")
                                }
                            }
                        },
                        text = {
                            if (categoryExpenses.isEmpty()) {
                                Text("Mẹ chưa có dữ liệu mục này ạ.")
                            } else {
                                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                                    val groupedExpensesList = groupedByMonth.entries.map { it.key to it.value }
                                    ExpenseTable(
                                        groupedExpenses = groupedExpensesList,
                                        onDelete = { viewModel.deleteExpense(it) },
                                        onEdit = { editingExpense = it }
                                    )
                                }
                            }
                        },
                        confirmButton = { }
                    )
                }
            } // End of Box
        } // End of 1 -> block
        2 -> {
                // Sổ sách
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val ledgers = listOf(
                        "Sổ tiết kiệm" to Icons.Default.ArrowUpward,
                        "Sổ vay nợ" to Icons.Default.ArrowDownward,
                        "Sổ quà cáp đáp lễ" to Icons.Default.Folder
                    )
                    
                    ledgers.forEach { (name, icon) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        3 -> {
                AdvisorTabContent(
                    viewModel = viewModel,
                    inputText = aiInputText,
                    onInputTextChange = { aiInputText = it },
                    onMicClick = {
                        transcriptionTarget = { text ->
                            aiInputText = if (aiInputText.isBlank()) text else aiInputText + " " + text
                        }
                        val isGranted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (isGranted) {
                            beginRecording()
                        } else {
                            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
            }
        }
        }
        }

        }
    }

    // Banner overlay — đặt ngoài Scaffold để slide xuống sát trên cùng (đè cả topBar).
    // statusBarsPadding() để né status bar hệ thống.
    val cachedBannerExpense = remember { mutableStateOf<ExpenseEntity?>(null) }
    LaunchedEffect(lastAddedExpense) {
        if (lastAddedExpense != null) cachedBannerExpense.value = lastAddedExpense
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = lastAddedExpense != null,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        enter = slideInVertically(
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        ) { fullHeight -> -fullHeight - 80 } + fadeIn(animationSpec = tween(durationMillis = 300)),
        exit = slideOutVertically(
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
        ) { fullHeight -> -fullHeight - 80 } + fadeOut(animationSpec = tween(durationMillis = 220))
    ) {
        cachedBannerExpense.value?.let { exp ->
            NotificationBannerCard(
                expense = exp,
                onDismiss = { viewModel.clearLastAddedExpense() }
            )
        }
    }

    val cachedFailureMsg = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(addExpenseFailure) {
        if (addExpenseFailure != null) cachedFailureMsg.value = addExpenseFailure
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = addExpenseFailure != null,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        enter = slideInVertically(
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        ) { fullHeight -> -fullHeight - 80 } + fadeIn(animationSpec = tween(durationMillis = 300)),
        exit = slideOutVertically(
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
        ) { fullHeight -> -fullHeight - 80 } + fadeOut(animationSpec = tween(durationMillis = 220))
    ) {
        cachedFailureMsg.value?.let { msg ->
            FailureBannerCard(
                message = msg,
                onDismiss = { viewModel.clearAddExpenseFailure() }
            )
        }
    }
    } // end of outer Box wrapping Scaffold + banner overlays

    if (showImageOptions) {
        AlertDialog(
            onDismissRequest = { showImageOptions = false },
            title = { Text("Chọn hóa đơn") },
            text = { Text("Mẹ muốn chụp ảnh mới hay chọn từ thư viện ạ?") },
            confirmButton = {
                TextButton(onClick = {
                    showImageOptions = false
                    tempCameraUri = createTempImageUri()
                    cameraLauncher.launch(tempCameraUri)
                }) {
                    Text("Chụp ảnh")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImageOptions = false
                    galleryLauncher.launch("image/*")
                }) {
                    Text("Thư viện")
                }
            }
        )
    }

    editingExpense?.let { exp ->
        EditExpenseDialog(
            expense = exp,
            onDismiss = { editingExpense = null },
            onSave = { updatedExp ->
                viewModel.updateExpense(updatedExp)
                editingExpense = null
            }
        )
    }

    if (!pendingReceiptExpenses.isNullOrEmpty()) {
        ReceiptConfirmationDialog(
            expenses = pendingReceiptExpenses!!,
            onConfirm = { 
                viewModel.confirmReceiptExpenses(it) 
            },
            onCancel = { 
                viewModel.cancelReceiptExpenses() 
            }
        )
    }
}

@Composable
fun AdvisorTabContent(
    viewModel: MainViewModel,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onMicClick: () -> Unit
) {
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val autoReadTts by viewModel.autoReadTts.collectAsState()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val context = androidx.compose.ui.platform.LocalContext.current




    val quickReplies = listOf(
        QuickReply("Khoản tiêu nhiều nhất?", "Tháng này mẹ đã tiêu vào khoản nào nhiều nhất?"),
        QuickReply("Chi tiêu bất thường?", "Mẹ có khoản chi nào bất thường trong tuần này không?"),
        QuickReply("So với tháng trước?", "So với tháng trước thì tháng này mẹ chi tiêu thế nào?"),
        QuickReply("Mẹo tiết kiệm tiền?", "Gợi ý cho mẹ cách tiết kiệm tiền đi!")
    )

    val hasMessages = chatMessages.isNotEmpty()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(chatMessages, isLoading) {
        val total = chatMessages.size + (if (isLoading && hasMessages) 1 else 0)
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
        
        if (!isLoading && hasMessages) {
            val lastMsg = chatMessages.last()
            if (lastMsg.role == "assistant" && autoReadTts && lastMsg.content != viewModel.lastReadMsgContent) {
                viewModel.lastReadMsgContent = lastMsg.content
                // Bỏ qua markdown table khi đọc
                val textToRead = lastMsg.content.replace(Regex("\\[TABLE\\](.*?)\\[/TABLE\\]", RegexOption.DOT_MATCHES_ALL), "Dưới đây là bảng chi tiết mẹ nhé.")
                viewModel.playTextToSpeech(textToRead, context)
            }
        }
    }

    var showClearChatDialog by remember { mutableStateOf(false) }
    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            title = { Text("Xóa đoạn chat?", fontWeight = FontWeight.Bold) },
            text = { Text("Mẹ có chắc muốn xóa toàn bộ tin nhắn với con không?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearChat()
                        showClearChatDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("Xóa", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatDialog = false }) {
                    Text("Huỷ")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        if (hasMessages) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { showClearChatDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Xóa đoạn chat", color = Color.Gray, fontSize = 13.sp)
                }
            }
        }
        if (hasMessages) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(chatMessages, key = { it.id }) { msg ->
                    ChatMessageRow(
                        msg,
                        modifier = Modifier
                    )
                }
                if (isLoading) {
                    item(key = "typing-indicator") {
                        TypingBubble(
                            modifier = Modifier
                        )
                    }
                }
            }
        } else {
            // Khi chưa có tin nhắn: hiển thị greeting + quick replies (scrollable để không che input khi keyboard mở)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChatAvatar(isBot = true)
                Text(
                    text = "Con đây mẹ!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Con có thể giúp gì cho mẹ?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                quickReplies.forEach { reply ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (!isLoading) {
                                viewModel.askAdvisor(reply.fullQuestion, context)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = reply.fullQuestion,
                            modifier = Modifier.padding(12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        if (hasMessages) {
            // Khi đã có tin nhắn: thu gọn thành LazyRow ngang
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickReplies) { reply ->
                    Surface(
                        modifier = Modifier.clickable {
                            if (!isLoading) {
                                viewModel.askAdvisor(reply.fullQuestion, context)
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            text = reply.shortText,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val canSend = inputText.isNotBlank() && !isLoading

            TextField(
                value = inputText,
                onValueChange = onInputTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Mẹ cần con giúp gì?", color = Color.Gray) },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )

            val buttonColor by androidx.compose.animation.animateColorAsState(
                targetValue = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                animationSpec = androidx.compose.animation.core.tween(300)
            )

            Box(
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .size(48.dp)
                    .background(buttonColor, CircleShape)
                    .clickable(enabled = !isLoading) {
                        if (canSend) {
                            viewModel.askAdvisor(inputText, context)
                            onInputTextChange("")
                            focusManager.clearFocus()
                        } else {
                            onMicClick()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    androidx.compose.animation.Crossfade(targetState = canSend, animationSpec = androidx.compose.animation.core.tween(300)) { send ->
                        if (send) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Gửi",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp).padding(start = 2.dp)
                            )
                        } else {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Giọng nói",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTable(
    groupedExpenses: List<Pair<String, List<ExpenseEntity>>>,
    onDelete: (ExpenseEntity) -> Unit,
    onEdit: (ExpenseEntity) -> Unit = {},
    highlightExpenseId: Int? = null,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
) {
    if (groupedExpenses.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Inbox,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = Color.LightGray
                )
                Text("Mẹ chưa có giao dịch nào", color = Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Mẹ ghi chữ hoặc bấm mic để con thêm nhé",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        }
        return
    }

    val firstId = groupedExpenses.firstOrNull()?.second?.firstOrNull()?.id ?: -1
    LaunchedEffect(firstId) {
        if (firstId != -1) listState.animateScrollToItem(0)
    }

    val activeHeaderKey by remember {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val isAtBottom = !listState.canScrollForward && visibleItems.isNotEmpty()
            if (isAtBottom) {
                visibleItems.lastOrNull { it.key is String && (it.key as String).startsWith("header_") }?.key as? String
            } else {
                visibleItems.firstOrNull { it.key is String && (it.key as String).startsWith("header_") }?.key as? String
            }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val viewportHeight = this.maxHeight
        val lastGroup = groupedExpenses.lastOrNull()
        val lastGroupItemCount = lastGroup?.second?.size ?: 0
        val estimatedLastGroupHeight = 40.dp + (lastGroupItemCount * 56).dp
        
        var selectedIds by remember { mutableStateOf(setOf<Int>()) }
        val isMultiSelectMode = selectedIds.isNotEmpty()
        var swipedItemId by remember { mutableStateOf<Int?>(null) }
        var expandedItemId by remember { mutableStateOf<Int?>(null) }

        val dynamicBottomPadding = if (estimatedLastGroupHeight < viewportHeight) {
            viewportHeight - estimatedLastGroupHeight
        } else {
            0.dp
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(expandedItemId, swipedItemId, isMultiSelectMode) {
                    if (!isMultiSelectMode && (expandedItemId != null || swipedItemId != null)) {
                        detectTapGestures(onTap = {
                            expandedItemId = null
                            swipedItemId = null
                        })
                    }
                }
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = dynamicBottomPadding)
        ) {
        groupedExpenses.forEach { (dateStr, items) ->
            stickyHeader(key = "header_$dateStr") {
                val income = items.filter { it.type == "income" && it.category != "Tiết kiệm" && it.category != "Cho vay" }.sumOf { it.amount }
                val expense = items.filter { it.type == "expense" && it.category != "Tiết kiệm" && it.category != "Cho vay" }.sumOf { it.amount }
                val total = income - expense

                val dayIds = items.map { it.id }.toSet()
                val allDaySelected = dayIds.isNotEmpty() && dayIds.all { selectedIds.contains(it) }
                val someDaySelected = dayIds.any { selectedIds.contains(it) }

                val headerBg = when {
                    isMultiSelectMode && allDaySelected -> Color(0xFFFFEBEE)
                    isMultiSelectMode && someDaySelected -> Color(0xFFFFF8E1)
                    else -> MaterialTheme.colorScheme.background
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBg)
                        .then(
                            if (isMultiSelectMode) Modifier.clickable {
                                selectedIds = if (allDaySelected) {
                                    selectedIds - dayIds
                                } else {
                                    selectedIds + dayIds
                                }
                            } else Modifier
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isMultiSelectMode) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(
                                        if (allDaySelected) Color(0xFFE53935) else Color.Transparent,
                                        CircleShape
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = if (allDaySelected) Color(0xFFE53935) else Color.Gray,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (allDaySelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                } else if (someDaySelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(Color(0xFFFFB300), CircleShape)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = dateStr,
                            fontWeight = FontWeight.Medium,
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        text = "${if (total > 0) "+" else ""}${formatVnCurrency(total)}đ",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }

            items(items, key = { it.id }) { expense ->
                val coroutineScope = rememberCoroutineScope()
                val density = androidx.compose.ui.platform.LocalDensity.current
                val maxSwipePx = remember(density) { with(density) { -120.dp.toPx() } }
                val offsetX = remember { androidx.compose.animation.core.Animatable(0f) }

                val isSwiped = swipedItemId == expense.id
                val isExpanded = expandedItemId == expense.id

                // Đóng vuốt nếu có row khác được vuốt
                LaunchedEffect(swipedItemId) {
                    if (swipedItemId != expense.id && offsetX.value != 0f) {
                        offsetX.animateTo(0f, tween(200))
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(
                            fadeInSpec = tween(280),
                            fadeOutSpec = null,
                            placementSpec = tween(220, easing = FastOutSlowInEasing)
                        )
                ) {
                    // Menu Buttons (Background)
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(vertical = 4.dp, horizontal = 8.dp) // Match card's padding
                            .clip(RoundedCornerShape(12.dp)), // Match card's radius
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Row(
                            modifier = Modifier.width(120.dp).fillMaxHeight(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Nút Sửa
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(Color(0xFF1976D2))
                                    .clickable {
                                        onEdit(expense)
                                        swipedItemId = null
                                        coroutineScope.launch { offsetX.animateTo(0f, tween(200)) }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Sửa", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                            // Nút Xóa
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(Color(0xFFE53935))
                                    .clickable {
                                        onDelete(expense)
                                        swipedItemId = null
                                        coroutineScope.launch { offsetX.snapTo(0f) }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Xóa", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }

                    // Foreground
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { androidx.compose.ui.unit.IntOffset(offsetX.value.roundToInt(), 0) }
                            .pointerInput(isMultiSelectMode) {
                                if (!isMultiSelectMode) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            coroutineScope.launch {
                                                if (offsetX.value < maxSwipePx / 2) {
                                                    offsetX.animateTo(maxSwipePx, tween(200))
                                                    swipedItemId = expense.id
                                                } else {
                                                    offsetX.animateTo(0f, tween(200))
                                                    if (swipedItemId == expense.id) swipedItemId = null
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            coroutineScope.launch { offsetX.animateTo(0f, tween(200)) }
                                            if (swipedItemId == expense.id) swipedItemId = null
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            if (dragAmount < 0 || offsetX.value < 0f) { // Only swipe left or drag back
                                                change.consume()
                                                coroutineScope.launch {
                                                    val newOffset = (offsetX.value + dragAmount).coerceIn(maxSwipePx, 0f)
                                                    offsetX.snapTo(newOffset)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                            .pointerInput(isMultiSelectMode, swipedItemId) {
                                detectTapGestures(
                                    onLongPress = {
                                        if (!isMultiSelectMode) {
                                            selectedIds = selectedIds + expense.id
                                        }
                                    },
                                    onTap = {
                                        if (isMultiSelectMode) {
                                            if (selectedIds.contains(expense.id)) {
                                                selectedIds = selectedIds - expense.id
                                            } else {
                                                selectedIds = selectedIds + expense.id
                                            }
                                        } else if (swipedItemId != null && swipedItemId != expense.id) {
                                            // Có row khác đang vuốt → tap để đóng nó, row hiện tại trở lại bình thường
                                            swipedItemId = null
                                        } else if (isSwiped) {
                                            // Đang vuốt chính nó → tap để đóng
                                            swipedItemId = null
                                        } else {
                                            // Bình thường → toggle mở rộng để xem đầy đủ
                                            expandedItemId = if (isExpanded) null else expense.id
                                        }
                                    }
                                )
                            }
                    ) {
                        ExpenseItemCard(
                            expense = expense,
                            showCheckbox = isMultiSelectMode,
                            isChecked = selectedIds.contains(expense.id),
                            expanded = isExpanded,
                            isHighlighted = highlightExpenseId == expense.id
                        )
                    }
                }
            }
        }
        }
        }

        if (isMultiSelectMode) {
            Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Đã chọn ${selectedIds.size} mục", fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { selectedIds = emptySet() }) {
                                Text("Hủy", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val itemsToDelete = groupedExpenses.flatMap { it.second }.filter { selectedIds.contains(it.id) }
                                    itemsToDelete.forEach { onDelete(it) }
                                    selectedIds = emptySet()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                            ) {
                                Text("Xóa")
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getCategoryIcon(category: String): String {
    val lower = category.lowercase()
    return when {
        lower.contains("mua sắm") -> "🛍️"
        lower.contains("thực phẩm") || lower.contains("ăn") -> "🍔"
        lower.contains("nhà") || lower.contains("hóa đơn") -> "🏠"
        lower.contains("thăm hỏi") || lower.contains("hiếu") -> "🎁"
        lower.contains("tiết kiệm") -> "💰"
        lower.contains("công việc") -> "💼"
        lower.contains("thu nhập") || lower.contains("lương") -> "💵"
        lower.contains("cho vay") || lower.contains("nợ") -> "💳"
        lower.contains("sức khỏe") || lower.contains("y tế") -> "💊"
        lower.contains("di chuyển") || lower.contains("đi lại") -> "🚗"
        else -> "❓"
    }
}

@Composable
fun ExpenseItemCard(
    expense: ExpenseEntity,
    showCheckbox: Boolean = false,
    isChecked: Boolean = false,
    expanded: Boolean = false,
    isHighlighted: Boolean = false
) {
    val isIncome = expense.type == "income"
    val sideColor = if (isIncome) Color(0xFF4CAF50) else Color(0xFFE53935)
    val amountStr = "${formatVnCurrency(expense.amount)}đ"

    val targetBg = when {
        isChecked -> Color(0xFFFFEBEE)
        isHighlighted -> Color(0xFFA5D6A7) // green 200 — nổi bật rõ
        expanded -> Color(0xFFF3F8FF)
        else -> Color.White
    }
    val targetBorder = when {
        isChecked -> Color(0xFFE53935)
        isHighlighted -> Color(0xFF2E7D32) // green 800
        expanded -> Color(0xFF1976D2)
        else -> Color(0xFFEEEEEE)
    }
    val backgroundColor by androidx.compose.animation.animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(durationMillis = 700),
        label = "highlightBg"
    )
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = targetBorder,
        animationSpec = tween(durationMillis = 700),
        label = "highlightBorder"
    )
    val borderWidth = if (isChecked || expanded || isHighlighted) 1.5.dp else 1.dp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(borderWidth, borderColor),
        shadowElevation = if (isHighlighted) 6.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val catIcon = getCategoryIcon(expense.category)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFFF5F5F5), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = catIcon,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.category,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = expense.activity,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis
                )
                if (expanded) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val timeStr = remember(expense.timestamp) {
                        java.text.SimpleDateFormat("HH:mm · dd/MM/yyyy", vnLocale)
                            .format(java.util.Date(expense.timestamp))
                    }
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF1976D2),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isIncome) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = sideColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (expanded) amountStr else amountStr.replace("đ", ""),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (expanded) sideColor else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun NotificationBannerCard(
    expense: ExpenseEntity,
    onDismiss: () -> Unit
) {
    val isIncome = expense.type == "income"
    val sideColor = if (isIncome) Color(0xFF4CAF50) else Color(0xFFE53935)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDismiss),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            // Tiêu đề
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(Color(0xFF4CAF50), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Đã thêm cho mẹ rồi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF2E7D32)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Bấm để đóng",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row giao dịch — chỉ hiển thị, không cho tương tác (tap/swipe/longpress)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFF5F5F5), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = getCategoryIcon(expense.category), fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = expense.category,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = expense.activity,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isIncome) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = sideColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${formatVnCurrency(expense.amount)}đ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = sideColor
                    )
                }
            }
        }
    }
}

@Composable
fun FailureBannerCard(
    message: String,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDismiss),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, Color(0xFFFFCDD2))
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(Color(0xFFE53935), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Thêm thất bại",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFFC62828)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Bấm để đóng",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CategoryTag(category: String) {
    val backgroundColor = when (category) {
        "Mua sắm" -> Color(0xFFFFF3E0) // Orange light
        "Thực phẩm" -> Color(0xFFE8F5E9) // Green light
        "Hóa đơn" -> Color(0xFFE0F7FA) // Cyan light
        "Thăm hỏi, hiếu hỉ" -> Color(0xFFFCE4EC) // Pink light
        "Tiết kiệm" -> Color(0xFFF3E5F5) // Purple light
        "Công việc" -> Color(0xFFE3F2FD) // Blue light
        "Thu nhập" -> Color(0xFFE0F7FA) // Cyan light
        "Khác" -> Color(0xFFF5F5F5) // Grey light
        // Giữ màu cũ cho tương thích
        "Ăn uống" -> Color(0xFFFFF3E0)
        "Di chuyển" -> Color(0xFFE3F2FD)
        "Giáo án & Trường lớp" -> Color(0xFFF3E5F5)
        "Khen thưởng học sinh" -> Color(0xFFFCE4EC)
        "Cá nhân" -> Color(0xFFFFF8E1)
        "Sức khỏe" -> Color(0xFFFFEBEE)
        else -> Color(0xFFF5F5F5) // Grey light
    }

    val textColor = when (category) {
        "Mua sắm" -> Color(0xFFE65100)
        "Thực phẩm" -> Color(0xFF2E7D32)
        "Hóa đơn" -> Color(0xFF006064)
        "Thăm hỏi, hiếu hỉ" -> Color(0xFFC2185B)
        "Tiết kiệm" -> Color(0xFF6A1B9A)
        "Công việc" -> Color(0xFF1565C0)
        "Thu nhập" -> Color(0xFF006064)
        "Khác" -> Color(0xFF424242)
        // Giữ màu cũ cho tương thích
        "Ăn uống" -> Color(0xFFE65100)
        "Di chuyển" -> Color(0xFF1565C0)
        "Giáo án & Trường lớp" -> Color(0xFF6A1B9A)
        "Khen thưởng học sinh" -> Color(0xFFAD1457)
        "Cá nhân" -> Color(0xFFF57F17)
        "Sức khỏe" -> Color(0xFFC62828)
        else -> Color(0xFF424242)
    }

    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = category,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ExpensePieChart(
    categorySummaries: List<CategorySummary>
) {
    if (categorySummaries.isEmpty()) {
        Text(
            text = "Chưa có dữ liệu chi tiêu để vẽ biểu đồ",
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            textAlign = TextAlign.Center,
            color = Color.Gray,
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }

    val totalAmount = categorySummaries.sumOf { it.totalAmount }
    var selectedSummary by remember { mutableStateOf<CategorySummary?>(null) }
    var centerOffset by remember { mutableStateOf(Offset.Zero) }

    // Tính toán tỷ lệ radius (1f là pop-up, 0.85f là bình thường) cho từng slice
    val animatedOuterRadii = categorySummaries.associateWith { summary ->
        val isSelected = selectedSummary == summary
        val targetScale = if (isSelected) 1f else 0.85f
        androidx.compose.animation.core.animateFloatAsState(
            targetValue = targetScale, 
            animationSpec = androidx.compose.animation.core.tween(200)
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxChartSize = 280.dp
        val availableWidth = maxWidth - 64.dp // chừa khoảng trống 32.dp mỗi bên cho icon to
        val chartSize = if (availableWidth < maxChartSize) availableWidth else maxChartSize

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartSize + 48.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(chartSize)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { tapOffset ->
                            val dx = tapOffset.x - centerOffset.x
                            val dy = tapOffset.y - centerOffset.y
                            val distance = Math.sqrt((dx * dx + dy * dy).toDouble())
                            
                            val maxOuterRadius = kotlin.math.min(size.width, size.height) / 2f
                            val innerRadius = maxOuterRadius * 0.35f
                            
                            if (distance in innerRadius.toDouble()..maxOuterRadius.toDouble()) {
                                val angle = (Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())) + 360) % 360
                                val touchAngle = (angle + 90) % 360
                                
                                var currentStartAngle = 0f
                                for (summary in categorySummaries) {
                                    val sweepAngle = ((summary.totalAmount / totalAmount) * 360f).toFloat()
                                    if (touchAngle >= currentStartAngle && touchAngle < currentStartAngle + sweepAngle) {
                                        selectedSummary = summary
                                        break
                                    }
                                    currentStartAngle += sweepAngle
                                }
                            }
                            
                            // Đợi đến khi nhả ngón tay ra
                            val success = tryAwaitRelease()
                            selectedSummary = null
                        }
                    )
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            
            val maxOuterRadius = size.minDimension / 2
            val baseOuterRadius = maxOuterRadius * 0.85f
            val innerRadius = maxOuterRadius * 0.35f
            
            centerOffset = Offset(canvasWidth / 2, canvasHeight / 2)

            var startAngle = -90f
            
            categorySummaries.forEach { summary ->
                val sweepAngle = ((summary.totalAmount / totalAmount) * 360f).toFloat()
                
                val scale = animatedOuterRadii[summary]?.value ?: 0.85f
                val outerRadius = maxOuterRadius * scale
                
                val strokeCenter = (innerRadius + outerRadius) / 2
                val strokeWidth = outerRadius - innerRadius

                drawArc(
                    color = summary.color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(centerOffset.x - strokeCenter, centerOffset.y - strokeCenter),
                    size = Size(strokeCenter * 2, strokeCenter * 2),
                    style = Stroke(width = strokeWidth)
                )

                startAngle += sweepAngle
            }
        }
        
        // Center text removed per request

        // Draw icons and text on slices
        var currentStartAngle = -90f
        categorySummaries.forEach { summary ->
            val sweepAngle = ((summary.totalAmount / totalAmount) * 360f).toFloat()
            val percentage = if (totalAmount > 0) ((summary.totalAmount / totalAmount) * 100).toInt() else 0
            
            if (percentage > 5) {
                val angleInRadians = (currentStartAngle + sweepAngle / 2) * (Math.PI / 180.0)
                val isSelected = selectedSummary == summary
                
                // Position for Icon (sát mép ngoài, 1.0f)
                val iconRadius = (chartSize.value / 2) * 1.0f
                val offsetX = (iconRadius * kotlin.math.cos(angleInRadians)).toFloat()
                val offsetY = (iconRadius * kotlin.math.sin(angleInRadians)).toFloat()
                
                val catIcon = getCategoryIcon(summary.category)
                
                Box(
                    modifier = Modifier
                        .offset(x = offsetX.dp, y = offsetY.dp)
                        .size(36.dp)
                        .background(Color.White, CircleShape)
                        .border(1.dp, summary.color, CircleShape)
                        .clickable { selectedSummary = summary },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = catIcon,
                        fontSize = 18.sp
                    )
                }
                
                // Position for Text if selected
                if (isSelected) {
                    // Place text in the middle of the colored band
                    val textRadius = (chartSize.value / 2) * 0.6f
                    val textOffsetX = (textRadius * kotlin.math.cos(angleInRadians)).toFloat()
                    val textOffsetY = (textRadius * kotlin.math.sin(angleInRadians)).toFloat()
                    
                    Box(
                        modifier = Modifier
                            .offset(x = textOffsetX.dp, y = textOffsetY.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = summary.category,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatVnCurrency(summary.totalAmount),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
            currentStartAngle += sweepAngle
        }
    }
    }
}

@Composable
fun CategoryDetailItem(summary: CategorySummary, percentage: Int, onClick: () -> Unit = {}) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFEEEEEE))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val catIcon = getCategoryIcon(summary.category)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFFF5F5F5), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = catIcon,
                    fontSize = 16.sp
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = summary.category,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formatVnCurrency(summary.totalAmount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().height(24.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val barHeight = 4.dp
                    val thumbWidth = 40.dp
                    val thumbHeight = 24.dp
                    
                    // Track (Background)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barHeight)
                            .background(Color(0xFFEEEEEE), RoundedCornerShape(2.dp))
                            .align(Alignment.Center)
                    )
                    
                    val fraction = percentage / 100f
                    
                    // Progress
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(barHeight)
                            .background(summary.color, RoundedCornerShape(2.dp))
                            .align(Alignment.CenterStart)
                    )
                    
                    // Thumb (Percentage pill)
                    val thumbOffset = maxWidth * fraction - (thumbWidth / 2)
                    val coercedOffset = thumbOffset.coerceIn(0.dp, maxWidth - thumbWidth)
                    
                    Box(
                        modifier = Modifier
                            .offset(x = coercedOffset)
                            .size(width = thumbWidth, height = thumbHeight)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF90A4AE), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$percentage%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.DarkGray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageRow(msg: com.example.aiexpensetrackeropenai.data.network.Message, modifier: Modifier = Modifier) {
    val isUser = msg.role == "user"
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Khôi", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                ChatAvatar(isBot = true)
            }
            Spacer(Modifier.width(8.dp))
        }
        
        // Chat Bubble
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp, 
                topEnd = 16.dp, 
                bottomStart = if (isUser) 16.dp else 4.dp, 
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.weight(1f, fill = false).padding(top = 14.dp)
        ) {
            val content = msg.content
            if (content.contains("[TABLE]") && content.contains("[/TABLE]")) {
                // Table Rendering
                val beforeTable = content.substringBefore("[TABLE]").trim()
                val tableString = content.substringAfter("[TABLE]").substringBefore("[/TABLE]").trim()
                val afterTable = content.substringAfter("[/TABLE]").trim()
                
                Column(modifier = Modifier.padding(12.dp)) {
                    if (beforeTable.isNotEmpty()) {
                        Text(beforeTable, fontSize = 14.sp, color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                    }
                    
                    // The table
                    val lines = tableString.lines().filter { it.isNotBlank() }
                    if (lines.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                lines.forEachIndexed { index, line ->
                                    val columns = line.split("|").map { it.trim() }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (index == 0) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        columns.forEachIndexed { colIndex, text ->
                                            Text(
                                                text = text,
                                                fontSize = 12.sp,
                                                fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.weight(if (colIndex == 1) 1.5f else 1f),
                                                maxLines = if (index == 0) 1 else 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (index < lines.size - 1) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                                    }
                                }
                            }
                        }
                    }
                    
                    if (afterTable.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(afterTable, fontSize = 14.sp, color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Text(
                    text = content,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 14.sp,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Mẹ", 
                    fontSize = 11.sp, 
                    color = Color.Gray, 
                    fontWeight = FontWeight.SemiBold, 
                    maxLines = 1, 
                    softWrap = false
                )
                Spacer(Modifier.height(2.dp))
                ChatAvatar(isBot = false)
            }
        }
    }
}

@Composable
fun ChatAvatar(isBot: Boolean) {
    if (isBot) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = com.example.aiexpensetrackeropenai.R.drawable.app_icon),
            contentDescription = "Con",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
        )
    } else {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = com.example.aiexpensetrackeropenai.R.drawable.avatar_me),
            contentDescription = "Mẹ",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
        )
    }
}

@Composable
fun TypingBubble(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        ChatAvatar(isBot = true)
        Spacer(Modifier.width(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TypingDot(0)
                TypingDot(150)
                TypingDot(300)
            }
        }
    }
}

@Composable
fun PulsingDot() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    Box(
        modifier = Modifier
            .size(14.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(Color(0xFFE53935), CircleShape)
    )
}

@Composable
fun AudioWaveform(amplitude: Float, modifier: Modifier = Modifier) {
    val barCount = 15
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier) {
        val barWidth = 6.dp.toPx()
        val totalSpacing = size.width - barCount * barWidth
        val spacing = (totalSpacing / (barCount - 1).coerceAtLeast(1)).coerceAtLeast(2f)
        val maxH = size.height
        val centerY = maxH / 2f
        val minH = 8.dp.toPx()

        for (i in 0 until barCount) {
            val barOffset = (i.toFloat() / barCount) * 2 * Math.PI.toFloat() * 2
            val wave = (kotlin.math.sin(phase + barOffset) * 0.5f + 0.5f).toFloat()
            val centerDist = kotlin.math.abs(i - (barCount - 1) / 2f) / ((barCount - 1) / 2f)
            val centerBias = 1f - centerDist * 0.35f

            val barHeight = (maxH * amplitude * (0.35f + wave * 0.65f) * centerBias).coerceAtLeast(minH)

            val barColor = androidx.compose.ui.graphics.lerp(primary, tertiary, centerDist)

            drawRoundRect(
                color = barColor,
                topLeft = androidx.compose.ui.geometry.Offset(
                    x = i * (barWidth + spacing),
                    y = centerY - barHeight / 2f
                ),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
            )
        }
    }
}

@Composable
fun TypingDot(delayMillis: Int) {
    val transition = rememberInfiniteTransition(label = "typing")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, delayMillis = delayMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), CircleShape)
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseDialog(
    expense: com.example.aiexpensetrackeropenai.data.local.ExpenseEntity,
    onDismiss: () -> Unit,
    onSave: (com.example.aiexpensetrackeropenai.data.local.ExpenseEntity) -> Unit
) {
    var activity by remember { mutableStateOf(expense.activity) }
    var amount by remember { mutableStateOf(expense.amount.toString()) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Sửa giao dịch", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(
                    value = activity,
                    onValueChange = { activity = it },
                    label = { Text("Nội dung") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Số tiền") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = {
                    val amt = amount.toDoubleOrNull()?.toInt() ?: expense.amount
                    onSave(expense.copy(activity = activity, amount = amt))
                },
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Lưu", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Hủy", color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh ?: MaterialTheme.colorScheme.surface
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReceiptConfirmationDialog(
    expenses: List<com.example.aiexpensetrackeropenai.data.local.ExpenseEntity>,
    onConfirm: (List<com.example.aiexpensetrackeropenai.data.local.ExpenseEntity>) -> Unit,
    onCancel: () -> Unit
) {
    val editableExpenses = remember { androidx.compose.runtime.mutableStateListOf(*expenses.toTypedArray()) }
    var editingExpense by remember { mutableStateOf<com.example.aiexpensetrackeropenai.data.local.ExpenseEntity?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text("Xác nhận hóa đơn", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            if (editableExpenses.isEmpty()) {
                Text("Không có dữ liệu hợp lệ.")
            } else {
                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    val grouped = listOf("Danh sách giao dịch" to editableExpenses.toList())
                    ExpenseTable(
                        groupedExpenses = grouped,
                        onDelete = { editableExpenses.remove(it) },
                        onEdit = { editingExpense = it }
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = { onConfirm(editableExpenses.toList()) },
                shape = RoundedCornerShape(16.dp),
                enabled = editableExpenses.isNotEmpty()
            ) {
                Text("Lưu tất cả", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(
                onClick = onCancel,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Hủy bỏ", color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh ?: MaterialTheme.colorScheme.surface
    )

    if (editingExpense != null) {
        EditExpenseDialog(
            expense = editingExpense!!,
            onDismiss = { editingExpense = null },
            onSave = { updated ->
                val index = editableExpenses.indexOf(editingExpense)
                if (index != -1) {
                    editableExpenses[index] = updated
                }
                editingExpense = null
            }
        )
    }
}
