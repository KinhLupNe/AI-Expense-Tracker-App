package com.example.aiexpensetrackeropenai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Delete
import java.util.Calendar
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.example.aiexpensetrackeropenai.data.local.ExpenseEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.text.NumberFormat

fun formatVnCurrency(amount: Number): String {
    return NumberFormat.getNumberInstance(Locale("vi", "VN")).format(amount.toLong()) + "đ"
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val apiKey by viewModel.apiKey.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val groupedExpenses by viewModel.groupedExpenses.collectAsState()
    val categorySummaries by viewModel.categorySummaries.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val amplitude by viewModel.currentAudioAmplitude.collectAsState()
    val monthlyBarChartData by viewModel.monthlyBarChartData.collectAsState()
    val weeklyLineChartData by viewModel.weeklyLineChartData.collectAsState()

    val monthlyExpensesForStats by viewModel.monthlyExpensesForStats.collectAsState()
    val webhookUrl by viewModel.webhookUrl.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()


    var inputText by remember { mutableStateOf("") }
    
    val selectedMonthOffset by viewModel.selectedMonthOffset.collectAsState()
    val monthWeeks by viewModel.monthWeeks.collectAsState()
    val selectedWeek by viewModel.selectedWeek.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Sổ Thu Chi", "Thống Kê", "Trợ lý AI")
    var showSettingsDialog by remember { mutableStateOf(false) }
    val sheetUrl by viewModel.sheetUrl.collectAsState()



    val context = androidx.compose.ui.platform.LocalContext.current


    val audioRecorder = remember { AudioRecorderHelper(context) }
    var isRecording by remember { mutableStateOf(false) }
    var recordTimeRemaining by remember { mutableStateOf(10) }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            audioRecorder.startRecording()
            viewModel.startAmplitudeTracking { audioRecorder.getMaxAmplitude() }
            isRecording = true
            recordTimeRemaining = 10
        } else {
            android.widget.Toast.makeText(context, "Cần cấp quyền ghi âm", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (recordTimeRemaining > 0) {
                kotlinx.coroutines.delay(1000)
                recordTimeRemaining -= 1
            }
            if (isRecording) {
                audioRecorder.stopRecording()
                viewModel.stopAmplitudeTracking()
                isRecording = false
                audioRecorder.audioFile?.let { file ->
                    viewModel.transcribeAudio(file) { text ->
                        val cleaned = text.trim()
                        val lower = cleaned.lowercase()
                        val isJunk = cleaned.isEmpty() ||
                                cleaned.all { !it.isLetterOrDigit() } ||
                                lower.contains("cảm ơn") ||
                                lower.contains("thank you") ||
                                lower == "bạn" || lower == "xin chào"

                        if (isJunk) {
                            android.widget.Toast.makeText(context, "Không nhận diện được giọng nói, vui lòng thử lại!", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            inputText = if (inputText.isBlank()) cleaned else inputText + " " + cleaned
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioRecorder.destroy()
            viewModel.stopAmplitudeTracking()
        }
    }

    val isImeVisible = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    Scaffold(
        bottomBar = {
            if (!isImeVisible) {
                NavigationBar {
                    tabs.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            label = { Text(title) },
                            icon = { 
                                Icon(
                                    imageVector = when(index) {
                                        0 -> Icons.Default.List
                                        1 -> Icons.Default.PieChart
                                        else -> Icons.Default.Chat
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
                val todayStrFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
                val currentTodayStr = java.time.LocalDate.now().format(todayStrFormatter)
                
                Text(
                    text = "Thu chi của mẹ", 
                    style = MaterialTheme.typography.headlineMedium, 
                    fontWeight = FontWeight.Black
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hôm nay: $currentTodayStr",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    when (syncStatus) {
                        SyncStatus.SYNCING -> Icon(Icons.Default.CloudUpload, contentDescription = "Syncing", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        SyncStatus.SUCCESS -> Icon(Icons.Default.CloudDone, contentDescription = "Synced", tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                        SyncStatus.ERROR -> {
                            IconButton(onClick = { viewModel.syncPendingExpenses() }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.CloudOff, contentDescription = "Sync Error", tint = Color.Red, modifier = Modifier.size(16.dp))
                            }
                        }
                        SyncStatus.IDLE -> {}
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    if (!sheetUrl.isNullOrBlank()) {
                        IconButton(onClick = {
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(sheetUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "Mở Google Sheets", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                
                if (selectedTabIndex == 0 || selectedTabIndex == 1) {
                    val calendar = java.util.Calendar.getInstance()
                    calendar.add(java.util.Calendar.MONTH, selectedMonthOffset)
                    val monthStr = java.text.SimpleDateFormat("MM/yyyy", java.util.Locale.getDefault()).format(calendar.time)
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF0F0F0),
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            IconButton(onClick = { viewModel.setMonthOffset(selectedMonthOffset - 1) }, modifier = Modifier.size(32.dp)) {
                                Text("<", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Text(monthStr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                            IconButton(onClick = { viewModel.setMonthOffset(selectedMonthOffset + 1) }, modifier = Modifier.size(32.dp)) {
                                Text(">", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }
                    val totalMonthExpense = monthlyExpensesForStats.filter { it.type == "expense" }.sumOf { it.amount }
                    if (totalMonthExpense > 0) {
                        Text(
                            text = "Tổng chi: ${formatVnCurrency(totalMonthExpense)}",
                            color = Color(0xFFD32F2F),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 8.dp)
        ) {
        
        // Removed stand-alone Today text

        if (isRecording) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { /* handle by OK button */ }) {
                androidx.compose.material3.Card(
                    modifier = Modifier.padding(16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Đang lắng nghe... ${recordTimeRemaining}s",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Canvas(modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                        ) {
                            val barWidth = 8.dp.toPx()
                            val spacing = 8.dp.toPx()
                            val center = size.width / 2f
                            val maxH = size.height
                            
                            val multipliers = listOf(0.4f, 0.7f, 1.0f, 0.7f, 0.4f)
                            val startX = center - (2 * barWidth + 2 * spacing) - barWidth / 2f
                            
                            for (i in 0..4) {
                                val h = maxH * amplitude * multipliers[i]
                                drawRoundRect(
                                    color = Color(0xFF4CAF50),
                                    topLeft = androidx.compose.ui.geometry.Offset(
                                        x = startX + i * (barWidth + spacing),
                                        y = (maxH - h) / 2f
                                    ),
                                    size = androidx.compose.ui.geometry.Size(barWidth, h),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                audioRecorder.stopRecording()
                                viewModel.stopAmplitudeTracking()
                                isRecording = false
                                audioRecorder.audioFile?.let { file ->
                                    viewModel.transcribeAudio(file) { text ->
                                        val cleaned = text.trim()
                                        val lower = cleaned.lowercase()
                                        val isJunk = cleaned.isEmpty() ||
                                                cleaned.all { !it.isLetterOrDigit() } ||
                                                lower.contains("cảm ơn") || lower.contains("thank you") ||
                                                lower == "bạn" || lower == "xin chào"

                                        if (isJunk) {
                                            android.widget.Toast.makeText(context, "Không nhận diện được giọng nói", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            inputText = if (inputText.isBlank()) cleaned else inputText + " " + cleaned
                                        }
                                    }
                                }
                                focusManager.clearFocus()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("OK", color = Color.White)
                        }
                    }
                }
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
            Button(onClick = { viewModel.clearError() }) {
                Text("Xóa lỗi")
            }
        }


        when (selectedTabIndex) {
            0 -> {
                // Sổ Thu Chi
                Column(modifier = Modifier.fillMaxSize()) {
                    // Week and Day Filters
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                    // Removed Cả tháng button
                    itemsIndexed(monthWeeks) { index, weekInfo ->
                        androidx.compose.material3.FilterChip(
                            selected = selectedWeek == (index + 1),
                            onClick = { viewModel.setWeekFilter(index + 1) },
                            label = { Text(weekInfo.label) }
                        )
                    }
                }
                
                // Removed Day Navigator
                    ExpenseTable(
                        groupedExpenses = groupedExpenses,
                        onDelete = { viewModel.deleteExpense(it) },
                        modifier = Modifier.weight(1f)
                    )
                    
                    // AI Input Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Nhập chi tiêu / thu nhập") },
                            maxLines = 3,
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (isLoading) return@IconButton
                                        val isGranted = ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        
                                        if (isGranted) {
                                            audioRecorder.startRecording()
                                            viewModel.startAmplitudeTracking { audioRecorder.getMaxAmplitude() }
                                            isRecording = true
                                            recordTimeRemaining = 10
                                        } else {
                                            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    enabled = !isLoading
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Mic, contentDescription = "Giọng nói")
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank() && !isLoading) {
                                    viewModel.parseExpense(inputText)
                                    inputText = ""
                                    focusManager.clearFocus()
                                }
                            },
                            enabled = !isLoading && inputText.isNotBlank()
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                            } else {
                                Icon(Icons.Default.Send, contentDescription = "Gửi", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            1 -> {
                // Thống Kê - dùng dữ liệu cả tháng
                var selectedCategoryForDetails by remember { mutableStateOf<String?>(null) }
                
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            ExpensePieChart(categorySummaries = categorySummaries)
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Chi tiết Danh mục",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    val totalPieExpense = categorySummaries.sumOf { it.totalAmount }
                    items(categorySummaries) { summary ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .clickable { selectedCategoryForDetails = summary.category },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val percentage = if (totalPieExpense > 0) ((summary.totalAmount / totalPieExpense) * 100).toInt() else 0
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Canvas(modifier = Modifier.size(16.dp)) {
                                            drawCircle(color = summary.color)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = summary.category, 
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "($percentage%)",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray
                                    )
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formatVnCurrency(summary.totalAmount), 
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE53935),
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }
                    


                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            WeeklyLineChart(data = weeklyLineChartData)
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            MonthlyPieCharts(data = monthlyBarChartData)
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                } // End of LazyColumn
                
                if (selectedCategoryForDetails != null) {
                    val categoryExpenses = monthlyExpensesForStats
                        .filter { it.type == "expense" && it.category == selectedCategoryForDetails }
                        .sortedBy { it.timestamp }
                    
                    val df = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())

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
                                Text("Không có dữ liệu.")
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                                    items(categoryExpenses) { expense ->
                                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(text = df.format(java.util.Date(expense.timestamp)), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                                Text(text = formatVnCurrency(expense.amount.toDouble()), fontWeight = FontWeight.Bold, color = Color(0xFFE53935), fontSize = 14.sp)
                                            }
                                            Text(text = expense.activity, fontSize = 14.sp, color = Color.Gray, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                            Divider(modifier = Modifier.padding(top = 4.dp))
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = { }
                    )
                }
            } // End of Box
        } // End of 1 -> block
        2 -> {
                AdvisorTabContent(viewModel)
            }
        }
    }
}
}

@Composable
fun AdvisorTabContent(viewModel: MainViewModel) {
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    
    val quickReplies = listOf(
        "Tháng này mẹ đã tiêu vào khoản nào nhiều nhất?",
        "Mẹ có khoản chi nào bất thường trong tuần này không?",
        "So với tháng trước thì tháng này mẹ chi tiêu thế nào?",
        "Gợi ý cho mẹ cách tiết kiệm tiền đi!"
    )

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(chatMessages) { msg ->
                val isUser = msg.role == "user"
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Text(
                            text = msg.content,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 16.sp,
                            color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickReplies) { reply ->
                SuggestionChip(
                    onClick = {
                        if (!isLoading) {
                            viewModel.askAdvisor(reply)
                        }
                    },
                    label = { Text(reply) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Hỏi trợ lý...") },
                maxLines = 3
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (inputText.isNotBlank() && !isLoading) {
                        viewModel.askAdvisor(inputText)
                        inputText = ""
                        focusManager.clearFocus()
                    }
                },
                enabled = !isLoading && inputText.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.Send, contentDescription = "Gửi")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpenseTable(
    groupedExpenses: List<Pair<String, List<ExpenseEntity>>>,
    onDelete: (ExpenseEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Hoạt động", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold)
            Text("Số tiền & Loại", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
        }
        HorizontalDivider(thickness = 1.dp, color = Color.LightGray)

        LazyColumn {
            groupedExpenses.forEach { (dateStr, items) ->
                stickyHeader {
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    val total = items.sumOf { if (it.type == "income") it.amount else -it.amount }
                    val totalStr = if (total >= 0) "+${formatVnCurrency(total)}" else formatVnCurrency(total)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFEEEEEE))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(dateStr, fontWeight = FontWeight.SemiBold, color = Color.DarkGray, fontSize = 15.sp)
                        Text(
                            totalStr,
                            fontWeight = FontWeight.SemiBold,
                            color = if (total >= 0) Color(0xFF4CAF50) else Color(0xFFE53935),
                            fontSize = 15.sp
                        )
                    }
                }

                items(items) { expense ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(0.5f)) {
                            Text(expense.activity, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
                            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(expense.timestamp))
                            Text(timeStr, style = MaterialTheme.typography.bodySmall, color = Color.Gray, fontSize = 14.sp)
                        }
                        
                        Column(
                            modifier = Modifier.weight(0.4f),
                            horizontalAlignment = Alignment.End
                        ) {
                            val amountStr = if (expense.type == "income") "+${formatVnCurrency(expense.amount)}" 
                                            else "-${formatVnCurrency(expense.amount)}"
                            Text(
                                amountStr,
                                color = if (expense.type == "income") Color(0xFF4CAF50) else Color(0xFFE53935),
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            CategoryTag(expense.category)
                        }
                        
                        IconButton(
                            onClick = { onDelete(expense) },
                            modifier = Modifier.weight(0.1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = Color.Red)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun CategoryTag(category: String) {
    val backgroundColor = when (category) {
        "Ăn uống" -> Color(0xFFFFF3E0) // Orange light
        "Di chuyển" -> Color(0xFFE3F2FD) // Blue light
        "Mua sắm" -> Color(0xFFFCE4EC) // Pink light
        "Hóa đơn" -> Color(0xFFE8F5E9) // Green light
        "Thu nhập" -> Color(0xFFE0F7FA) // Cyan light
        "Giải trí" -> Color(0xFFF3E5F5) // Purple light
        "Sức khỏe" -> Color(0xFFFFEBEE) // Red light
        else -> Color(0xFFF5F5F5) // Grey light
    }
    
    val textColor = when (category) {
        "Ăn uống" -> Color(0xFFE65100)
        "Di chuyển" -> Color(0xFF1565C0)
        "Mua sắm" -> Color(0xFFC2185B)
        "Hóa đơn" -> Color(0xFF2E7D32)
        "Thu nhập" -> Color(0xFF006064)
        "Giải trí" -> Color(0xFF6A1B9A)
        "Sức khỏe" -> Color(0xFFC62828)
        else -> Color(0xFF424242)
    }

    Box(
        modifier = Modifier
            .wrapContentWidth()
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = category,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


@Composable
fun ExpensePieChart(categorySummaries: List<CategorySummary>) {
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categorySummaries.forEach { summary ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(summary.color, RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = summary.category,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))

        Canvas(modifier = Modifier.size(120.dp)) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val radius = size.minDimension / 2
            val center = Offset(canvasWidth / 2, canvasHeight / 2)

            var startAngle = -90f
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 36f
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
            }

            categorySummaries.forEach { summary ->
                val sweepAngle = ((summary.totalAmount / totalAmount) * 360f).toFloat()
                val percentage = if (totalAmount > 0) ((summary.totalAmount / totalAmount) * 100).toInt() else 0

                drawArc(
                    color = summary.color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2)
                )

                if (percentage > 5) {
                    val angleInRadians = (startAngle + sweepAngle / 2) * (Math.PI / 180.0)
                    val textRadius = radius * 0.65f
                    val textX = center.x + (textRadius * kotlin.math.cos(angleInRadians)).toFloat()
                    val textY = center.y + (textRadius * kotlin.math.sin(angleInRadians)).toFloat()

                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText("$percentage%", textX, textY + 12f, textPaint)
                    }
                }
                startAngle += sweepAngle
            }
        }
    }
}

@Composable
fun MonthlyPieCharts(data: List<MonthlyChartData>) {
    if (data.isEmpty() || data.all { it.expense == 0.0 }) {
        Text(
            text = "Chưa có dữ liệu thống kê tháng",
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            textAlign = TextAlign.Center,
            color = Color.Gray,
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Chi Tiêu 3 Tháng Gần Nhất", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            data.forEach { monthData ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val totalExpense = monthData.expense
                    if (totalExpense > 0) {
                        Canvas(modifier = Modifier.size(80.dp)) {
                            val canvasWidth = size.width
                            val canvasHeight = size.height
                            val radius = size.minDimension / 2
                            val center = Offset(canvasWidth / 2, canvasHeight / 2)
                            
                            var startAngle = -90f
                            val textPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = 20f
                                textAlign = android.graphics.Paint.Align.CENTER
                                isFakeBoldText = true
                            }
                            
                            monthData.expenseCategories.forEach { summary ->
                                val sweepAngle = ((summary.totalAmount / totalExpense) * 360f).toFloat()
                                val percentage = ((summary.totalAmount / totalExpense) * 100).toInt()
                                
                                drawArc(
                                    color = summary.color,
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle,
                                    useCenter = true,
                                    topLeft = Offset(center.x - radius, center.y - radius),
                                    size = Size(radius * 2, radius * 2)
                                )
                                
                                if (percentage > 10) {
                                    val angleInRadians = (startAngle + sweepAngle / 2) * (Math.PI / 180.0)
                                    val textRadius = radius * 0.65f
                                    val textX = center.x + (textRadius * kotlin.math.cos(angleInRadians)).toFloat()
                                    val textY = center.y + (textRadius * kotlin.math.sin(angleInRadians)).toFloat()
                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.drawText("$percentage%", textX, textY + 6f, textPaint)
                                    }
                                }
                                startAngle += sweepAngle
                            }
                        }
                    } else {
                        Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                            Text("-", color = Color.Gray)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(monthData.monthLabel, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text(formatVnCurrency(totalExpense), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                }
            }
        }
    }
}

@Composable
fun WeeklyLineChart(data: List<WeeklyChartData>) {
    if (data.isEmpty() || data.all { it.expense == 0.0 }) {
        Text(
            text = "Chưa có dữ liệu chi tiêu tuần",
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            textAlign = TextAlign.Center,
            color = Color.Gray,
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }

    val maxVal = data.maxOf { it.expense }
    
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Biểu Đồ Chi Tiêu Các Tuần", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val pointSpacing = canvasWidth / (data.size.coerceAtLeast(2) - 1)
                
                // Draw horizontal grid lines
                val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                for (i in 0..4) {
                    val y = canvasHeight * i / 4
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.5f),
                        start = Offset(0f, y),
                        end = Offset(canvasWidth, y),
                        strokeWidth = 1f,
                        pathEffect = pathEffect
                    )
                }

                // Create path for line chart
                val path = Path()
                val points = mutableListOf<Offset>()
                
                data.forEachIndexed { index, weekData ->
                    val x = index * pointSpacing
                    val y = canvasHeight - if (maxVal > 0) (weekData.expense / maxVal * canvasHeight).toFloat() else 0f
                    val point = Offset(x, y)
                    points.add(point)
                    
                    if (index == 0) {
                        path.moveTo(point.x, point.y)
                    } else {
                        path.lineTo(point.x, point.y)
                    }
                }
                
                // Draw Line
                drawPath(
                    path = path,
                    color = Color(0xFF2196F3),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
                
                // Draw Dots
                points.forEach { point ->
                    drawCircle(
                        color = Color.White,
                        radius = 6.dp.toPx(),
                        center = point
                    )
                    drawCircle(
                        color = Color(0xFF2196F3),
                        radius = 4.dp.toPx(),
                        center = point
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            data.forEach {
                Text(it.weekLabel, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}
