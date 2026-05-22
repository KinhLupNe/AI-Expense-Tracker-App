package com.example.aiexpensetrackeropenai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.Color
import com.example.aiexpensetrackeropenai.data.local.ExpenseDao
import com.example.aiexpensetrackeropenai.data.local.ExpenseEntity
import com.example.aiexpensetrackeropenai.data.local.SettingsManager
import com.example.aiexpensetrackeropenai.data.network.ChatRequest
import com.example.aiexpensetrackeropenai.data.network.Message
import com.example.aiexpensetrackeropenai.data.network.OpenAIApi
import com.example.aiexpensetrackeropenai.data.network.ParsedExpense
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import org.json.JSONObject

enum class SyncStatus {
    IDLE, SYNCING, SUCCESS, ERROR
}

data class CategorySummary(
    val category: String,
    val totalAmount: Double,
    val color: Color
)

data class WeekInfo(
    val label: String,
    val startDate: java.time.LocalDate,
    val endDate: java.time.LocalDate,
    val days: List<java.time.LocalDate>
)

data class MonthlyChartData(
    val monthLabel: String,
    val income: Double,
    val expense: Double,
    val expenseCategories: List<CategorySummary> = emptyList()
)

data class WeeklyChartData(
    val weekLabel: String,
    val expense: Double
)

fun getCategoryColor(category: String): androidx.compose.ui.graphics.Color {
    return when (category) {
        "Ăn uống" -> androidx.compose.ui.graphics.Color(0xFF4CAF50) // Xanh lá
        "Hóa đơn" -> androidx.compose.ui.graphics.Color(0xFF00BCD4) // Cyan
        "Di chuyển" -> androidx.compose.ui.graphics.Color(0xFF2196F3) // Xanh dương
        "Giáo án & Trường lớp" -> androidx.compose.ui.graphics.Color(0xFF9C27B0) // Tím
        "Khen thưởng học sinh" -> androidx.compose.ui.graphics.Color(0xFFE91E63) // Hồng
        "Mua sắm" -> androidx.compose.ui.graphics.Color(0xFFFF9800) // Cam
        "Cá nhân" -> androidx.compose.ui.graphics.Color(0xFFFFC107) // Vàng
        "Sức khỏe" -> androidx.compose.ui.graphics.Color(0xFFF44336) // Đỏ
        else -> androidx.compose.ui.graphics.Color(0xFF9E9E9E) // Xám
    }
}

class MainViewModel(
    private val expenseDao: ExpenseDao,
    private val openAIApi: OpenAIApi,
    private val settingsManager: SettingsManager
) : ViewModel() {

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            expenseDao.updateCategoryName("Nhà ở & Hóa đơn", "Hóa đơn")
            expenseDao.updateCategoryName("Mua sắm & Cá nhân", "Mua sắm")
        }
    }

    val apiKey: StateFlow<String?> = settingsManager.apiKeyFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val webhookUrl: StateFlow<String?> = settingsManager.webhookUrlFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val sheetUrl: StateFlow<String?> = settingsManager.sheetUrlFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus = _syncStatus.asStateFlow()
    
    fun saveWebhookUrl(url: String) {
        viewModelScope.launch {
            settingsManager.saveWebhookUrl(url)
        }
    }

    fun saveSheetUrl(url: String) {
        viewModelScope.launch {
            settingsManager.saveSheetUrl(url)
        }
    }

    private fun syncToGoogleSheets(expense: ExpenseEntity) {
        val url = webhookUrl.value
        if (url.isNullOrBlank()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            _syncStatus.value = SyncStatus.SYNCING
            try {
                val json = JSONObject().apply {
                    put("activity", expense.activity)
                    put("amount", expense.amount)
                    put("type", expense.type)
                    put("category", expense.category)
                    val timeStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(expense.timestamp))
                    put("time", timeStr)
                }
                
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .post(body)
                    .build()
                    
                val client = okhttp3.OkHttpClient()
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    expenseDao.updateExpense(expense.copy(isSynced = true))
                    _syncStatus.value = SyncStatus.SUCCESS
                    kotlinx.coroutines.delay(3000)
                    _syncStatus.value = SyncStatus.IDLE
                } else {
                    _syncStatus.value = SyncStatus.ERROR
                    kotlinx.coroutines.delay(5000)
                    _syncStatus.value = SyncStatus.IDLE
                }
                response.close()
            } catch (e: Exception) {
                e.printStackTrace()
                _syncStatus.value = SyncStatus.ERROR
                kotlinx.coroutines.delay(5000)
                _syncStatus.value = SyncStatus.IDLE
            }
        }
    }

    init {
        syncPendingExpenses()
    }

    fun syncPendingExpenses() {
        viewModelScope.launch(Dispatchers.IO) {
            val url = webhookUrl.value
            if (url.isNullOrBlank()) return@launch

            val unsynced = expenseDao.getUnsyncedExpenses()
            if (unsynced.isEmpty()) return@launch

            _syncStatus.value = SyncStatus.SYNCING
            var allSuccess = true
            val client = okhttp3.OkHttpClient()

            for (expense in unsynced) {
                try {
                    val json = JSONObject().apply {
                        put("activity", expense.activity)
                        put("amount", expense.amount)
                        put("type", expense.type)
                        put("category", expense.category)
                        val timeStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(expense.timestamp))
                        put("time", timeStr)
                    }
                    val body = json.toString().toRequestBody("application/json".toMediaType())
                    val request = okhttp3.Request.Builder().url(url).post(body).build()
                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        expenseDao.updateExpense(expense.copy(isSynced = true))
                    } else {
                        allSuccess = false
                    }
                    response.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                    allSuccess = false
                }
            }

            if (allSuccess) {
                _syncStatus.value = SyncStatus.SUCCESS
                kotlinx.coroutines.delay(3000)
                _syncStatus.value = SyncStatus.IDLE
            } else {
                _syncStatus.value = SyncStatus.ERROR
                kotlinx.coroutines.delay(5000)
                _syncStatus.value = SyncStatus.IDLE
            }
        }
    }

    private val _selectedMonthOffset = MutableStateFlow(0)
    val selectedMonthOffset = _selectedMonthOffset.asStateFlow()
    
    private val _selectedWeek = MutableStateFlow(getCurrentWeekIndex())
    val selectedWeek = _selectedWeek.asStateFlow()
    
    private fun getCurrentWeekIndex(): Int {
        val now = java.time.LocalDate.now()
        val firstDayOfMonth = now.withDayOfMonth(1)
        var current = firstDayOfMonth
        while (current.dayOfWeek != java.time.DayOfWeek.MONDAY) {
            current = current.minusDays(1)
        }
        var weekIndex = 1
        while (current <= now) {
            val endDate = current.plusDays(6)
            if (now in current..endDate) {
                return weekIndex
            }
            current = current.plusDays(7)
            weekIndex++
        }
        return 1
    }
    
    private val _selectedDay = MutableStateFlow("")
    val selectedDay = _selectedDay.asStateFlow()

    fun setMonthOffset(offset: Int) {
        _selectedMonthOffset.value = offset
        if (offset == 0) {
            _selectedWeek.value = getCurrentWeekIndex()
        } else {
            _selectedWeek.value = 1
        }
        _selectedDay.value = ""
    }
    
    fun setWeekFilter(week: Int) {
        _selectedWeek.value = week
        _selectedDay.value = ""
    }
    
    // Removed setDayFilter and navigateDay

    val monthWeeks: StateFlow<List<WeekInfo>> = selectedMonthOffset.map { offset ->
        val now = java.time.LocalDate.now()
        var targetMonth = now.plusMonths(offset.toLong())
        val firstDayOfMonth = targetMonth.withDayOfMonth(1)
        val lastDayOfMonth = targetMonth.withDayOfMonth(targetMonth.lengthOfMonth())
        
        var current = firstDayOfMonth
        // Rewind to Monday
        while (current.dayOfWeek != java.time.DayOfWeek.MONDAY) {
            current = current.minusDays(1)
        }
        
        val weeks = mutableListOf<WeekInfo>()
        var weekIndex = 1
        while (current <= lastDayOfMonth) {
            val weekDays = mutableListOf<java.time.LocalDate>()
            val startDate = current
            for (i in 0..6) {
                if (current.monthValue == targetMonth.monthValue) {
                    weekDays.add(current)
                }
                current = current.plusDays(1)
            }
            val endDate = current.minusDays(1)
            weeks.add(WeekInfo("Tuần $weekIndex", startDate, endDate, weekDays))
            weekIndex++
        }
        weeks
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenses: StateFlow<List<ExpenseEntity>> = expenseDao.getAllExpenses().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val monthlyBarChartData: StateFlow<List<MonthlyChartData>> = combine(expenses, selectedMonthOffset) { allExpenses, offset ->
        val results = mutableListOf<MonthlyChartData>()
        val now = java.time.LocalDate.now()
        for (i in -1..1) {
            val targetMonth = now.plusMonths((offset + i).toLong())
            val monthLabel = "${targetMonth.monthValue}/${targetMonth.year}"
            
            val income = allExpenses.filter { 
                val date = java.time.Instant.ofEpochMilli(it.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                date.monthValue == targetMonth.monthValue && date.year == targetMonth.year && it.type == "income"
            }.sumOf { it.amount.toDouble() }
            
            val expensesForMonth = allExpenses.filter { 
                val date = java.time.Instant.ofEpochMilli(it.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                date.monthValue == targetMonth.monthValue && date.year == targetMonth.year && it.type == "expense"
            }
            val expense = expensesForMonth.sumOf { it.amount.toDouble() }
            
            val categories = expensesForMonth.groupBy { it.category }
                .map { (category, items) ->
                    val total = items.sumOf { it.amount.toDouble() }
                    CategorySummary(category, total, getCategoryColor(category))
                }.sortedByDescending { it.totalAmount }
            
            results.add(MonthlyChartData(monthLabel, income, expense, categories))
        }
        results
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weeklyLineChartData: StateFlow<List<WeeklyChartData>> = combine(expenses, monthWeeks) { allExpenses, weeks ->
        weeks.map { weekInfo ->
            val expense = allExpenses.filter { 
                val date = java.time.Instant.ofEpochMilli(it.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                weekInfo.days.contains(date) && it.type == "expense"
            }.sumOf { it.amount.toDouble() }
            WeeklyChartData(weekInfo.label, expense)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    val filteredExpenses: StateFlow<List<ExpenseEntity>> = combine(
        expenses, 
        selectedMonthOffset, 
        selectedWeek, 
        selectedDay
    ) { list, offset, week, day ->
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, offset)
        val targetMonth = calendar.get(Calendar.MONTH)
        val targetYear = calendar.get(Calendar.YEAR)
        
        list.filter { expense ->
            val expCal = java.util.Calendar.getInstance()
            expCal.timeInMillis = expense.timestamp
            val expLocalDate = java.time.Instant.ofEpochMilli(expense.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            
            val isSameMonth = expCal.get(java.util.Calendar.MONTH) == targetMonth && expCal.get(java.util.Calendar.YEAR) == targetYear
            
            if (day.isNotEmpty()) {
                val filterDay = java.time.LocalDate.parse(day)
                return@filter expLocalDate == filterDay
            }
            
            if (week > 0) {
                val weeks = monthWeeks.value
                val weekInfo = weeks.getOrNull(week - 1)
                if (weekInfo != null) {
                    return@filter !expLocalDate.isBefore(weekInfo.startDate) && !expLocalDate.isAfter(weekInfo.endDate)
                }
            }
            
            // If no specific week or day is selected, just check if it's in the month
            isSameMonth
        }.sortedByDescending { it.timestamp }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val groupedExpenses: StateFlow<List<Pair<String, List<ExpenseEntity>>>> = filteredExpenses.map { list ->
        val calendar = Calendar.getInstance()
        val today = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = calendar.timeInMillis

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val todayStr = dateFormat.format(Date(today))
        val yesterdayStr = dateFormat.format(Date(yesterday))

        list.groupBy { expense ->
            val dateStr = dateFormat.format(Date(expense.timestamp))
            when (dateStr) {
                todayStr -> "Hôm nay"
                yesterdayStr -> "Hôm qua"
                else -> "Ngày $dateStr"
            }
        }.toList()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Dữ liệu CHỈ lọc theo tháng - dành riêng cho màn hình Thống kê
    val monthlyExpensesForStats: StateFlow<List<ExpenseEntity>> = combine(
        expenses,
        selectedMonthOffset
    ) { list, offset ->
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, offset)
        val targetMonth = calendar.get(Calendar.MONTH)
        val targetYear = calendar.get(Calendar.YEAR)
        list.filter { expense ->
            val expCal = java.util.Calendar.getInstance()
            expCal.timeInMillis = expense.timestamp
            expCal.get(java.util.Calendar.MONTH) == targetMonth &&
            expCal.get(java.util.Calendar.YEAR) == targetYear
        }.sortedByDescending { it.timestamp }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val categorySummaries: StateFlow<List<CategorySummary>> = monthlyExpensesForStats.map { list ->
        list.filter { it.type == "expense" }
            .groupBy { it.category }
            .map { (category, items) ->
                val total = items.sumOf { it.amount.toDouble() }
                CategorySummary(category, total, getCategoryColor(category))
            }
            .sortedByDescending { it.totalAmount }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<Message>>(emptyList())
    val chatMessages = _chatMessages.asStateFlow()

    private val _currentAudioAmplitude = MutableStateFlow(0.1f)
    val currentAudioAmplitude = _currentAudioAmplitude.asStateFlow()
    
    private var amplitudeJob: kotlinx.coroutines.Job? = null

    fun startAmplitudeTracking(getAmplitude: () -> Int) {
        amplitudeJob?.cancel()
        amplitudeJob = viewModelScope.launch {
            while (isActive) {
                val maxAmp = getAmplitude()
                val normalized = 0.1f + (maxAmp / 32767f) * 0.9f
                _currentAudioAmplitude.value = normalized.coerceIn(0.1f, 1.0f)
                kotlinx.coroutines.delay(50)
            }
        }
    }

    fun stopAmplitudeTracking() {
        amplitudeJob?.cancel()
        _currentAudioAmplitude.value = 0.1f
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            settingsManager.saveApiKey(key)
        }
    }

    fun askAdvisor(question: String) {
        val currentKey = apiKey.value
        if (currentKey.isNullOrBlank()) {
            _errorMessage.value = "API Key is missing."
            return
        }

        val userMsg = Message(role = "user", content = question)
        val newHistory = (_chatMessages.value + userMsg).takeLast(10)
        _chatMessages.value = newHistory

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val currentCal = Calendar.getInstance()
                val currentMonthVal = currentCal.get(Calendar.MONTH) + 1
                val currentYearVal = currentCal.get(Calendar.YEAR)

                val lowercaseQ = question.lowercase()
                val targetMonths = linkedSetOf<Pair<Int, Int>>()

                fun monthsAgo(n: Int): Pair<Int, Int> {
                    val c = Calendar.getInstance()
                    c.add(Calendar.MONTH, -n)
                    return Pair(c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR))
                }

                Regex("(\\d+)\\s*tháng\\s+(gần đây|gần nhất|qua|vừa qua|vừa rồi)").findAll(lowercaseQ).forEach { m ->
                    val n = m.groupValues[1].toIntOrNull() ?: 0
                    if (n in 1..24) for (i in 0 until n) targetMonths.add(monthsAgo(i))
                }

                if (lowercaseQ.contains("tháng trước") || lowercaseQ.contains("tháng rồi") || lowercaseQ.contains("tháng vừa rồi")) {
                    targetMonths.add(monthsAgo(1))
                }

                if (lowercaseQ.contains("tháng này") || lowercaseQ.contains("tháng hiện tại")) {
                    targetMonths.add(Pair(currentMonthVal, currentYearVal))
                }

                Regex("tháng\\s+(\\d{1,2})").findAll(lowercaseQ).forEach { m ->
                    val parsedMonth = m.groupValues[1].toIntOrNull()
                    if (parsedMonth != null && parsedMonth in 1..12) {
                        var y = currentYearVal
                        if (parsedMonth > currentMonthVal && parsedMonth >= 10 && currentMonthVal <= 3) y -= 1
                        targetMonths.add(Pair(parsedMonth, y))
                    }
                }

                if (targetMonths.isEmpty()) targetMonths.add(Pair(currentMonthVal, currentYearVal))

                val sortedTargets = targetMonths.sortedWith(compareBy({ it.second }, { it.first }))
                val targetLabel = sortedTargets.joinToString(", ") { "${it.first}/${it.second}" }

                val allExpenses = expenses.value
                val df = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                val dataContext = sortedTargets.joinToString(separator = "\n\n") { (m, y) ->
                    val filtered = allExpenses.filter {
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = it.timestamp
                        cal.get(Calendar.MONTH) + 1 == m && cal.get(Calendar.YEAR) == y && it.type == "expense"
                    }
                    val header = "== Tháng $m/$y =="
                    if (filtered.isEmpty()) "$header\nKhông có giao dịch nào."
                    else header + "\n" + filtered.joinToString(separator = "\n") {
                        "- ${it.activity}: ${it.amount}đ (${it.category}, ${df.format(java.util.Date(it.timestamp))})"
                    }
                }
                
                val systemPrompt = """
                    Bạn là một trợ lý quản lý tài chính cá nhân. Hãy xưng là 'con' và gọi người dùng là 'mẹ'.
                    Quy tắc NGHIÊM NGẶT:
                    1. Giọng văn: Trưởng thành, điềm đạm, lịch sự. BẮT BUỘC TUYỆT ĐỐI KHÔNG dùng từ "Dạ", "Thưa mẹ" ở đầu câu, KHÔNG lạm dụng từ "ạ" ở cuối câu. Không sến súa, máy móc. Đi thẳng vào số liệu (Ví dụ: "Con gửi mẹ thống kê chi tiêu...").
                    2. Cực kỳ ngắn gọn, tối đa 2 đến 3 câu ngắn.
                    3. KHÔNG SỬ DỤNG ký hiệu Markdown (dấu sao *, gạch -, thăng #). Chỉ dùng văn bản thuần, xuống dòng tự nhiên.
                    4. Mặc định phân tích tháng hiện tại ($currentMonthVal/$currentYearVal) nếu mẹ không chỉ định. Mẹ có thể hỏi nhiều tháng cùng lúc; dữ liệu của mỗi tháng được liệt kê thành các khối "== Tháng X/Y ==" bên dưới — hãy so sánh/tổng hợp khi cần.

                    Dữ liệu chi tiêu các tháng được chọn ($targetLabel):
                    $dataContext
                """.trimIndent()

                val systemMessage = Message(role = "system", content = systemPrompt)
                
                val request = ChatRequest(
                    messages = listOf(systemMessage) + newHistory
                )

                val response = openAIApi.parseExpense(
                    authHeader = "Bearer ${currentKey.trim()}",
                    request = request
                )

                val content = response.choices.firstOrNull()?.message?.content
                if (content != null) {
                    val aiMsg = Message(role = "assistant", content = content)
                    _chatMessages.value = (_chatMessages.value + aiMsg).takeLast(10)
                } else {
                    _errorMessage.value = "Không thể nhận phản hồi từ trợ lý"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Có lỗi xảy ra"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun parseExpense(text: String) {
        val currentKey = apiKey.value
        if (currentKey.isNullOrBlank()) {
            _errorMessage.value = "API Key is missing."
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val formatter = java.time.format.DateTimeFormatter.ofPattern("EEEE, 'ngày' dd/MM/yyyy", java.util.Locale("vi", "VN"))
                val todayStr = java.time.LocalDate.now().format(formatter)
                
                val systemMessage = Message(
                    role = "system",
                    content = """
                        Bạn là một trợ lý phân tích dữ liệu tài chính Việt Nam chuyên nghiệp. Nhiệm vụ của bạn là đọc một câu văn bản tự nhiên về thu chi và trả về một MẢNG CÁC OBJECT JSON (JSON Array), không kèm ký tự markdown (như ```json), không giải thích gì thêm.
                        Ngữ cảnh hiện tại: Hôm nay là $todayStr.
                        Cấu trúc JSON MẢNG bắt buộc phải tuân theo định dạng sau:
                        [
                          {
                            "activity": "Tên hoạt động rút gọn",
                            "amount": Số tiền (Kiểu số nguyên hoặc số thực),
                            "type": "expense" hoặc "income",
                            "category": "Tên danh mục",
                            "date": "yyyy-MM-dd"
                          }
                        ]
                        Quy tắc tính ngày (date):
                        - Nếu người dùng nhắc đến "Hôm qua", trừ đi 1 ngày.
                        - Nếu nhắc đến "Hôm kia", trừ đi 2 ngày.
                        - Nếu nhắc đến "Thứ [X] tuần này", "Ngày [X] tháng này", hãy tự tính toán dựa trên ngày hiện tại (Hôm nay).
                        - Nếu câu KHÔNG nhắc đến mốc thời gian nào, lấy đúng ngày hôm nay.
                        Nếu người dùng liệt kê nhiều khoản chi tiêu (Ví dụ: "mua cá 40k, mua vịt 30k"), bạn phải bóc tách thành nhiều object trong mảng đó.
                        Các danh mục chuẩn bạn ĐƯỢC PHÉP gán: 'Ăn uống', 'Hóa đơn', 'Di chuyển', 'Giáo án & Trường lớp', 'Khen thưởng học sinh', 'Mua sắm', 'Cá nhân', 'Sức khỏe', 'Thu nhập', 'Khác'.
                        Ví dụ: 
                        - 'Hôm qua ăn bát phở 45k' -> date là ngày hôm qua, category: 'Ăn uống'
                        - 'Đổ xăng 50k' -> date là hôm nay, category: 'Di chuyển'
                        - 'Thứ Hai tuần này mua quà cho lớp 1 củ' -> tự suy ra ngày Thứ Hai, activity: 'Mua quà cho lớp', amount: 1000000, type: 'expense', category: 'Khen thưởng học sinh'
                    """.trimIndent()
                )
                val userMessage = Message(role = "user", content = text)
                
                val request = ChatRequest(
                    messages = listOf(systemMessage, userMessage)
                )
                
                val response = openAIApi.parseExpense(
                    authHeader = "Bearer ${currentKey.trim()}",
                    request = request
                )
                
                val content = response.choices.firstOrNull()?.message?.content
                if (content != null) {
                    val listType = object : com.google.gson.reflect.TypeToken<List<ParsedExpense>>() {}.type
                    val parsedList: List<ParsedExpense> = Gson().fromJson(content, listType)
                    
                    val entities = parsedList.map { parsed ->
                        val expenseTimestamp = try {
                            if (!parsed.date.isNullOrEmpty()) {
                                val parsedDate = java.time.LocalDate.parse(parsed.date)
                                val now = java.time.LocalDate.now()
                                if (parsedDate == now) {
                                    System.currentTimeMillis()
                                } else {
                                    val currentTime = java.time.LocalTime.now()
                                    parsedDate.atTime(currentTime).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                }
                            } else {
                                System.currentTimeMillis()
                            }
                        } catch (e: Exception) {
                            System.currentTimeMillis()
                        }
                        
                        ExpenseEntity(
                            activity = parsed.activity,
                            amount = parsed.amount.toInt(),
                            type = parsed.type,
                            category = parsed.category,
                            timestamp = expenseTimestamp
                        )
                    }
                    
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        entities.forEach { entity ->
                            val id = expenseDao.insertExpense(entity)
                            val insertedEntity = entity.copy(id = id.toInt())
                            syncToGoogleSheets(insertedEntity)
                        }
                    }
                } else {
                    _errorMessage.value = "Không thể phân tích dữ liệu"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Có lỗi xảy ra"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun transcribeAudio(file: File, onResult: (String) -> Unit) {
        val currentKey = apiKey.value
        if (currentKey.isNullOrBlank()) {
            _errorMessage.value = "API Key is missing."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val requestFile = file.asRequestBody("audio/m4a".toMediaType())
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                val model = "whisper-1".toRequestBody("text/plain".toMediaType())
                val language = "vi".toRequestBody("text/plain".toMediaType())
                val prompt = "Ứng dụng quản lý chi tiêu tài chính cá nhân bằng tiếng Việt. Ví dụ: mua cá 40k, mua vịt 30k, ăn bát phở 45k.".toRequestBody("text/plain".toMediaType())

                val response = openAIApi.transcribeAudio(
                    authHeader = "Bearer ${currentKey.trim()}",
                    audio = body,
                    model = model,
                    language = language,
                    prompt = prompt
                )
                onResult(response.text)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Có lỗi xảy ra khi gọi Whisper API"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            expenseDao.deleteExpense(expense)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    companion object {
        fun provideFactory(
            expenseDao: ExpenseDao,
            openAIApi: OpenAIApi,
            settingsManager: SettingsManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                    return MainViewModel(expenseDao, openAIApi, settingsManager) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
