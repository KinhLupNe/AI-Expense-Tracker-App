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
import com.example.aiexpensetrackeropenai.data.network.ImageMessage
import com.example.aiexpensetrackeropenai.data.network.ContentItem
import com.example.aiexpensetrackeropenai.data.network.ImageUrl
import com.example.aiexpensetrackeropenai.data.network.ImageChatRequest
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

// Singletons dùng chung trong toàn ViewModel — tránh tạo lại
internal val vnLocale: Locale = Locale.forLanguageTag("vi-VN")

internal val sharedHttpClient: okhttp3.OkHttpClient by lazy {
    okhttp3.OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
}

// SimpleDateFormat KHÔNG thread-safe — chỉ dùng từ UI thread / 1 coroutine tại 1 thời điểm
internal val sdfDateSlashYear: SimpleDateFormat = SimpleDateFormat("dd/MM/yyyy", vnLocale)
internal val sdfDateTimeSlash: SimpleDateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", vnLocale)
internal val sdfDateShort: SimpleDateFormat = SimpleDateFormat("dd/MM", vnLocale)
internal val sdfMonthYear: SimpleDateFormat = SimpleDateFormat("MM/yyyy", vnLocale)
internal val sdfMonthNum: SimpleDateFormat = SimpleDateFormat("M", vnLocale)
internal val sdfTime: SimpleDateFormat = SimpleDateFormat("HH:mm", vnLocale)

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

enum class AnalyzeState {
    IDLE,
    ANALYZING,
    PLAYING
}

fun getCategoryColor(category: String): androidx.compose.ui.graphics.Color {
    return when (category) {
        "Mua sắm" -> androidx.compose.ui.graphics.Color(0xFFFF5252) // Vibrant Red
        "Thực phẩm" -> androidx.compose.ui.graphics.Color(0xFF00E676) // Vibrant Green
        "Hóa đơn" -> androidx.compose.ui.graphics.Color(0xFF00B0FF) // Vibrant Light Blue
        "Thăm hỏi, hiếu hỉ" -> androidx.compose.ui.graphics.Color(0xFFFFC400) // Vibrant Yellow
        "Tiết kiệm" -> androidx.compose.ui.graphics.Color(0xFFD500F9) // Vibrant Purple
        "Công việc" -> androidx.compose.ui.graphics.Color(0xFF2979FF) // Vibrant Blue
        "Thu nhập" -> androidx.compose.ui.graphics.Color(0xFF00E676) // Vibrant Green
        "Khác" -> androidx.compose.ui.graphics.Color(0xFF78909C) // Blue Grey
        // Backward compatibility
        "Ăn uống" -> androidx.compose.ui.graphics.Color(0xFF00E676)
        "Di chuyển" -> androidx.compose.ui.graphics.Color(0xFF00BFA5) // Vibrant Teal
        "Giáo án & Trường lớp" -> androidx.compose.ui.graphics.Color(0xFF651FFF) // Deep Purple
        "Khen thưởng học sinh" -> androidx.compose.ui.graphics.Color(0xFFF50057) // Vibrant Pink
        "Cá nhân" -> androidx.compose.ui.graphics.Color(0xFFFF6D00) // Vibrant Orange
        "Sức khỏe" -> androidx.compose.ui.graphics.Color(0xFFD50000) // Deep Red
        else -> androidx.compose.ui.graphics.Color(0xFF78909C)
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
            expenseDao.updateCategoryName("Nợ/Cho vay", "Cho vay")
        }
        viewModelScope.launch {
            settingsManager.ttsVoiceFlow.collect { voice ->
                _ttsVoice.value = voice
            }
        }
    }

    var lastReadMsgContent: String = ""

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

    val autoReadTts: StateFlow<Boolean> = settingsManager.autoReadTtsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    fun setAutoReadTts(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveAutoReadTts(enabled)
        }
    }

    private val _analyzeState = MutableStateFlow(AnalyzeState.IDLE)
    val analyzeState: StateFlow<AnalyzeState> = _analyzeState.asStateFlow()

    private val _pendingReceiptExpenses = MutableStateFlow<List<ExpenseEntity>?>(null)
    val pendingReceiptExpenses: StateFlow<List<ExpenseEntity>?> = _pendingReceiptExpenses.asStateFlow()

    // Báo cho UI biết vừa có expense mới được thêm — UI tự xử lý highlight/notification
    private val _lastAddedExpense = MutableStateFlow<ExpenseEntity?>(null)
    val lastAddedExpense: StateFlow<ExpenseEntity?> = _lastAddedExpense.asStateFlow()

    // Số mục vừa thêm (kèm cùng _lastAddedExpense) — để TTS đọc "Đã thêm N mục"
    private val _lastAddedCount = MutableStateFlow(0)
    val lastAddedCount: StateFlow<Int> = _lastAddedCount.asStateFlow()

    fun clearLastAddedExpense() {
        _lastAddedExpense.value = null
        _lastAddedCount.value = 0
    }

    // Báo cho UI biết vừa thêm thất bại — UI hiện banner đỏ + đọc "Thêm thất bại"
    private val _addExpenseFailure = MutableStateFlow<String?>(null)
    val addExpenseFailure: StateFlow<String?> = _addExpenseFailure.asStateFlow()

    fun clearAddExpenseFailure() {
        _addExpenseFailure.value = null
    }

    // Android built-in TTS — dùng cho thông báo ngắn ("đã thêm N mục", "thêm thất bại").
    // Khác với playTextToSpeech qua network — Android TTS instant, không tốn API key.
    private var instantTts: android.speech.tts.TextToSpeech? = null
    @Volatile private var instantTtsReady = false

    fun initInstantTts(context: android.content.Context) {
        if (instantTts != null) return
        val appCtx = context.applicationContext
        instantTts = android.speech.tts.TextToSpeech(appCtx) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                val result = instantTts?.setLanguage(vnLocale)
                instantTtsReady = result != null &&
                    result != android.speech.tts.TextToSpeech.LANG_MISSING_DATA &&
                    result != android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED
                instantTts?.setSpeechRate(1.05f)
                instantTts?.setPitch(1.0f)
            }
        }
    }

    fun speakInstant(text: String) {
        if (!instantTtsReady) return
        kotlin.runCatching {
            instantTts?.stop()
            instantTts?.speak(
                text,
                android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                null,
                "instant_${System.currentTimeMillis()}"
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        kotlin.runCatching {
            instantTts?.stop()
            instantTts?.shutdown()
        }
        instantTts = null
        instantTtsReady = false
    }

    fun confirmReceiptExpenses(expenses: List<ExpenseEntity>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var lastInserted: ExpenseEntity? = null
            expenses.forEach { entity ->
                val dbEntity = entity.copy(id = 0)
                val id = expenseDao.insertExpense(dbEntity)
                val insertedEntity = dbEntity.copy(id = id.toInt())
                lastInserted = insertedEntity
                syncToGoogleSheets(insertedEntity)
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                _pendingReceiptExpenses.value = null

                expenses.firstOrNull()?.let { firstEntity ->
                    val expDate = java.time.Instant.ofEpochMilli(firstEntity.timestamp)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                    val now = java.time.LocalDate.now()
                    val monthsDiff = (expDate.year - now.year) * 12 + (expDate.monthValue - now.monthValue)

                    _selectedMonthOffset.value = monthsDiff
                    _selectedWeek.value = getWeekIndexForDate(expDate)
                    _selectedDay.value = ""
                }
                lastInserted?.let {
                    _lastAddedCount.value = expenses.size
                    _lastAddedExpense.value = it
                }
            }
        }
    }

    fun cancelReceiptExpenses() {
        _pendingReceiptExpenses.value = null
    }

    private val _analysisText = MutableStateFlow<String?>(null)
    val analysisText: StateFlow<String?> = _analysisText.asStateFlow()

    fun dismissAnalysis() {
        mediaPlayer?.release()
        mediaPlayer = null
        _analysisText.value = null
        _analyzeState.value = AnalyzeState.IDLE
    }

    fun stopAnalysisAudio() {
        if (_analyzeState.value == AnalyzeState.PLAYING) {
            mediaPlayer?.release()
            mediaPlayer = null
            _analyzeState.value = AnalyzeState.IDLE
        }
    }

    fun analyzeCurrentMonth(monthExpenses: List<ExpenseEntity>, monthLabel: String, context: android.content.Context) {
        val currentKey = apiKey.value
        if (currentKey.isNullOrBlank()) {
            _errorMessage.value = "Mẹ ơi, chưa có OpenAI API Key. Vào Cài đặt giúp con nhé."
            return
        }
        
        _analyzeState.value = AnalyzeState.ANALYZING
        viewModelScope.launch {
            try {
                // Giả định lương mẹ là 12tr (chỉ dùng nội bộ, không được nhắc trong output)
                val income = 12000000.0
                // BỎ HOÀN TOÀN tiết kiệm và cho vay/nợ ra khỏi phân tích — coi như khoản ngoài luồng
                val expenseList = monthExpenses.filter { it.type == "expense" && it.category != "Tiết kiệm" && it.category != "Cho vay" }
                val totalExpense = expenseList.sumOf { it.amount.toDouble() }

                val categoriesStr = expenseList.groupBy { it.category }
                    .map { (cat, items) ->
                        val sum = items.sumOf { it.amount.toDouble() }
                        val pct = if (totalExpense > 0) (sum / totalExpense * 100).toInt() else 0
                        "$cat: ${sum.toLong()}đ ($pct% tổng chi, ${items.size} giao dịch)"
                    }
                    .sortedByDescending {
                        Regex("(\\d+)đ").find(it)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    }
                    .joinToString("\n  ")

                val transactionsStr = expenseList
                    .sortedByDescending { it.timestamp }
                    .joinToString("\n") {
                        "  ${sdfDateShort.format(Date(it.timestamp))} | ${it.category} | ${it.activity}: ${it.amount}đ"
                    }

                val prompt = """
                    Bạn đóng vai là một người con thông minh và có hiểu biết sâu về tài chính cá nhân, đang phân tích chi tiêu sinh hoạt tháng $monthLabel cho mẹ.

                    === DỮ LIỆU NỘI BỘ (CHỈ dùng để phân tích, KHÔNG nhắc trong câu trả lời) ===
                    Thu nhập tham chiếu (ẩn): ${income.toLong()}đ
                    TUYỆT ĐỐI KHÔNG được nói "lương mẹ", "thu nhập của mẹ", "12 triệu", hay bất kỳ con số thu nhập nào trong câu trả lời.

                    === PHẠM VI PHÂN TÍCH ===
                    Chỉ phân tích các khoản CHI TIÊU SINH HOẠT bên dưới. TUYỆT ĐỐI KHÔNG nhắc, không phân tích, không bình luận về:
                    - Khoản TIẾT KIỆM (đã loại khỏi dữ liệu, coi như khoản ngoài luồng).
                    - Khoản NỢ / CHO VAY (đã loại khỏi dữ liệu, coi như khoản ngoài luồng).
                    Nếu trong câu trả lời lỡ nhắc đến "tiết kiệm" hay "cho vay" / "nợ" -> SAI YÊU CẦU.

                    === TỔNG QUAN CHI TIÊU SINH HOẠT ===
                    Tổng chi: ${totalExpense.toLong()}đ

                    === CHI TIẾT THEO DANH MỤC (đã sắp xếp giảm dần) ===
                      $categoriesStr

                    === TOÀN BỘ GIAO DỊCH SINH HOẠT TRONG THÁNG ===
                    $transactionsStr

                    === YÊU CẦU PHÂN TÍCH ===
                    Trả lời khoảng 150 chữ. KHÔNG markdown, KHÔNG động viên, KHÔNG khen ngợi, KHÔNG câu kết kiểu "mẹ cố gắng nhé". Phân tích phải CỤ THỂ, DỰA TRÊN dữ liệu thực, KHÔNG chung chung.

                    Bố cục bắt buộc:
                    1. Đánh giá tổng quan ngắn về mức chi tiêu sinh hoạt tháng này (cao/vừa/thấp so với thu nhập ẩn — nhưng KHÔNG nói số thu nhập).
                    2. Phân tích 2-3 danh mục chi lớn nhất: tên danh mục, số tiền, chiếm bao nhiêu phần trăm tổng chi, có hợp lý hay bất thường không. Trích dẫn các giao dịch cụ thể nếu thấy đáng chú ý (ví dụ: "khoản X ngày Y hơi cao", "có nhiều lần mua Z trong tháng").
                    3. Chỉ ra 1-2 khoản có thể tiết giảm + gợi ý cụ thể (ví dụ: "thay vì đi ăn ngoài 5 lần/tháng có thể giảm xuống 2 lần").

                    === ĐỊNH DẠNG VĂN NÓI (CỰC KỲ QUAN TRỌNG) ===
                    Đoạn này sẽ được TTS đọc to lên, nên BẮT BUỘC viết theo lối văn nói tiếng Việt tự nhiên. TUYỆT ĐỐI KHÔNG dùng chữ số Ả Rập (0-9) trong câu trả lời. KHÔNG dùng dấu chấm/phẩy ngăn cách hàng nghìn. KHÔNG dùng ký hiệu "đ", "%", "k", "tr".

                    Quy tắc viết số bằng chữ:
                    - Số tiền viết bằng chữ tiếng Việt, ưu tiên cách đọc gọn tự nhiên:
                      • 35000 -> "ba mươi lăm nghìn"
                      • 50000 -> "năm chục nghìn" hoặc "năm mươi nghìn"
                      • 500000 -> "năm trăm nghìn"
                      • 1500000 -> "một triệu rưỡi" (ưu tiên) hoặc "một triệu năm trăm nghìn"
                      • 2050000 -> "hai triệu năm mươi nghìn" (BỎ "không trăm")
                      • 2500000 -> "hai triệu rưỡi"
                      • 12000000 -> "mười hai triệu"
                    - Phần trăm: "25%" -> "hai mươi lăm phần trăm". KHÔNG viết "25%".
                    - Ngày tháng: "5/3" -> "ngày năm tháng ba" (KHÔNG bao giờ viết "05" hay "0X" có số 0 đứng đầu).
                    - Số lần / số giao dịch: "5 lần" -> "năm lần", "12 giao dịch" -> "mười hai giao dịch".
                    - Số thứ tự: viết bằng chữ ("thứ nhất", "thứ hai") thay vì "1.", "2.".

                    Câu trả lời ra phải đọc trơn tru như một người con đang nói chuyện với mẹ, không có bất kỳ chữ số nào.
                """.trimIndent()

                val systemMessage = Message(role = "system", content = prompt)
                val request = ChatRequest(messages = listOf(com.example.aiexpensetrackeropenai.data.network.ApiMessage(systemMessage.role, systemMessage.content)))
                val response = openAIApi.parseExpense(authHeader = "Bearer ${currentKey.trim()}", request = request)
                val content = response.choices.firstOrNull()?.message?.content
                
                if (content != null) {
                    // Hiển thị text ngay khi chat xong, không chờ TTS download
                    _analysisText.value = content
                    // TTS chạy song song ở background, không chặn UI
                    playTextToSpeech(content, context, onReady = {
                        _analyzeState.value = AnalyzeState.PLAYING
                    }, onDone = {
                        if (_analysisText.value != null) {
                            _analyzeState.value = AnalyzeState.IDLE
                        }
                    })
                } else {
                    _errorMessage.value = "Con không phân tích được mẹ ạ."
                    _analyzeState.value = AnalyzeState.IDLE
                }
            } catch (e: Exception) {
                _errorMessage.value = "Lỗi khi phân tích: ${e.message}"
                _analyzeState.value = AnalyzeState.IDLE
            }
        }
    }

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
                    put("action", "add")
                    put("activity", expense.activity)
                    put("amount", expense.amount)
                    put("type", expense.type)
                    put("category", expense.category)
                    put("time", sdfDateTimeSlash.format(Date(expense.timestamp)))
                }

                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                val response = sharedHttpClient.newCall(request).execute()

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

            for (expense in unsynced) {
                try {
                    val json = JSONObject().apply {
                        put("action", "add")
                        put("id", expense.id)
                        put("activity", expense.activity)
                        put("amount", expense.amount)
                        put("type", expense.type)
                        put("category", expense.category)
                        put("time", sdfDateTimeSlash.format(Date(expense.timestamp)))
                    }
                    val body = json.toString().toRequestBody("application/json".toMediaType())
                    val request = okhttp3.Request.Builder().url(url).post(body).build()
                    val response = sharedHttpClient.newCall(request).execute()

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

    private var mediaPlayer: android.media.MediaPlayer? = null

    private fun normalizeTextForSpeech(text: String): String {
        val tableRegex = Regex("\\[TABLE\\](.*?)\\[/TABLE\\]", RegexOption.DOT_MATCHES_ALL)
        var speech = tableRegex.replace(text) { "" }
        speech = speech.replace(Regex("(\\d+(?:\\.\\d+)?)\\s*k\\b", RegexOption.IGNORE_CASE), "$1 nghìn")
        speech = speech.replace(Regex("(\\d+(?:\\.\\d+)?)\\s*tr\\b", RegexOption.IGNORE_CASE), "$1 triệu")
        // Bỏ ký tự xuống dòng thừa, khoảng trắng thừa
        speech = speech.replace(Regex("\\n+"), ". ").replace(Regex("\\s+"), " ")
        return speech.trim()
    }

    // FPT.AI API Keys (danh sách dự phòng)
    private val fptApiKeys = listOf(
        "KZSwFAdXAqoEj3G7Q3iV6iX184HC6LuO", // Key chính
        "IfZEI9MftblxvzneQ5HnHgBKVDOm1tlH"  // Key dự phòng do người dùng cung cấp
    )
    private var currentFptKeyIndex = 0

    fun playTextToSpeech(text: String, context: android.content.Context, onReady: () -> Unit = {}, onDone: () -> Unit = {}) {
        val openAiKey = apiKey.value
        val fptVoice = "banmai"
        val openAiVoice = "nova"

        val speechText = normalizeTextForSpeech(text)
        if (speechText.isBlank()) {
            onReady()
            onDone()
            return
        }
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var tempFile: java.io.File? = null
            try {
                // PRIMARY: FPT.AI TTS qua danh sách keys
                for (attempt in fptApiKeys.indices) {
                    val key = fptApiKeys[currentFptKeyIndex]
                    var success = false
                    try {
                        val url = "https://api.fpt.ai/hmi/tts/v5"
                        val requestBody = speechText.toRequestBody("text/plain; charset=utf-8".toMediaType())
                        val request = okhttp3.Request.Builder()
                            .url(url)
                            .post(requestBody)
                            .addHeader("api-key", key)
                            .addHeader("voice", fptVoice)
                            .addHeader("speed", "")
                            .addHeader("format", "mp3")
                            .build()

                        val response = sharedHttpClient.newCall(request).execute()

                        if (response.isSuccessful) {
                            val respStr = response.body?.string()
                            if (respStr != null) {
                                val json = JSONObject(respStr)
                                if (json.optInt("error", -1) == 0) {
                                    val asyncUrl = json.optString("async")
                                    if (asyncUrl.isNotEmpty()) {
                                        kotlinx.coroutines.delay(400)
                                        var downloadResponse: okhttp3.Response? = null
                                        for (i in 1..15) {
                                            val downloadReq = okhttp3.Request.Builder().url(asyncUrl).build()
                                            downloadResponse = sharedHttpClient.newCall(downloadReq).execute()
                                            if (downloadResponse.isSuccessful) {
                                                val audioStream = downloadResponse.body?.byteStream()
                                                if (audioStream != null) {
                                                    val file = java.io.File.createTempFile("tts_", ".mp3", context.cacheDir)
                                                    val outputStream = java.io.FileOutputStream(file)
                                                    audioStream.copyTo(outputStream)
                                                    outputStream.close()
                                                    audioStream.close()
                                                    tempFile = file
                                                }
                                                break
                                            } else {
                                                downloadResponse.close()
                                                kotlinx.coroutines.delay(300)
                                            }
                                        }
                                        if (tempFile != null) {
                                            success = true
                                        }
                                    }
                                }
                            }
                        }
                        response.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    if (success) {
                        break
                    } else {
                        currentFptKeyIndex = (currentFptKeyIndex + 1) % fptApiKeys.size
                    }
                }

                // FALLBACK: OpenAI TTS
                if (tempFile == null && !openAiKey.isNullOrBlank()) {
                    val request = com.example.aiexpensetrackeropenai.data.network.SpeechRequest(
                        input = speechText,
                        voice = openAiVoice
                    )
                    val response = openAIApi.generateSpeech("Bearer ${openAiKey.trim()}", request = request)
                    val audioStream = response.byteStream()
                    val file = java.io.File.createTempFile("tts_", ".mp3", context.cacheDir)
                    val outputStream = java.io.FileOutputStream(file)
                    audioStream.copyTo(outputStream)
                    outputStream.close()
                    audioStream.close()
                    tempFile = file
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (tempFile != null) {
                        mediaPlayer?.release()
                        mediaPlayer = android.media.MediaPlayer().apply {
                            setDataSource(tempFile!!.absolutePath)
                            prepare()
                            start()
                            onReady()
                            setOnCompletionListener {
                                it.release()
                                mediaPlayer = null
                                tempFile!!.delete()
                                onDone()
                            }
                        }
                    } else {
                        onReady()
                        onDone()
                    }
                }
            }
        }
    }

    private val _selectedMonthOffset = MutableStateFlow(0)
    val selectedMonthOffset = _selectedMonthOffset.asStateFlow()
    
    private val _selectedWeek = MutableStateFlow(getCurrentWeekIndex())
    val selectedWeek = _selectedWeek.asStateFlow()
    
    private fun getCurrentWeekIndex(): Int {
        return getWeekIndexForDate(java.time.LocalDate.now())
    }

    private fun getWeekIndexForDate(date: java.time.LocalDate): Int {
        val targetMonth = date.withDayOfMonth(1)
        val lastDayOfMonth = targetMonth.withDayOfMonth(targetMonth.lengthOfMonth())
        var current = targetMonth
        while (current.dayOfWeek != java.time.DayOfWeek.MONDAY) {
            current = current.minusDays(1)
        }
        var weekIndex = 1
        while (current <= lastDayOfMonth) {
            val endDate = current.plusDays(6)
            if (date in current..endDate) {
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
            
            // Lướt dọc toàn tháng: không lọc theo week nữa
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

        val todayStr = sdfDateSlashYear.format(Date(today))
        val yesterdayStr = sdfDateSlashYear.format(Date(yesterday))

        list.groupBy { expense ->
            val dateStr = sdfDateSlashYear.format(Date(expense.timestamp))
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
        list.filter { it.type == "expense" && it.category != "Tiết kiệm" && it.category != "Cho vay" }
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

    val savingsSummary: StateFlow<CategorySummary> = combine(expenses, selectedMonthOffset) { list, offset ->
        val targetYear = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, offset) }.get(java.util.Calendar.YEAR)
        val savingsItems = list.filter { 
            val expYear = java.time.Instant.ofEpochMilli(it.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate().year
            it.category == "Tiết kiệm" && expYear == targetYear 
        }
        val total = if (savingsItems.isNotEmpty()) savingsItems.sumOf { it.amount.toDouble() } else 0.0
        CategorySummary("Tiết kiệm", total, getCategoryColor("Tiết kiệm"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CategorySummary("Tiết kiệm", 0.0, Color(0xFF4CAF50))
    )

    val debtSummary: StateFlow<CategorySummary> = combine(expenses, selectedMonthOffset) { list, offset ->
        val targetYear = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, offset) }.get(java.util.Calendar.YEAR)
        val debtItems = list.filter { 
            val expYear = java.time.Instant.ofEpochMilli(it.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate().year
            it.category == "Cho vay" && expYear == targetYear
        }
        val total = if (debtItems.isNotEmpty()) debtItems.sumOf { it.amount.toDouble() } else 0.0
        CategorySummary("Cho vay", total, Color(0xFF673AB7)) // Purple
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CategorySummary("Cho vay", 0.0, Color(0xFF673AB7))
    )

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing = _isTranscribing.asStateFlow()

    private var audioRecorder: AudioRecorderHelper? = null

    private val _chatMessages = MutableStateFlow<List<Message>>(emptyList())
    val chatMessages = _chatMessages.asStateFlow()

    private val _currentAudioAmplitude = MutableStateFlow(0f)
    val currentAudioAmplitude: StateFlow<Float> = _currentAudioAmplitude.asStateFlow()

    private val _ttsVoice = MutableStateFlow("alloy")
    val ttsVoice: StateFlow<String> = _ttsVoice.asStateFlow()

    fun setTtsVoice(voice: String) {
        _ttsVoice.value = voice
        settingsManager.saveTtsVoice(voice)
    }

    private var amplitudeJob: kotlinx.coroutines.Job? = null

    fun startAmplitudeTracking(getAmplitude: () -> Int) {
        amplitudeJob?.cancel()
        amplitudeJob = viewModelScope.launch {
            while (isActive) {
                val maxAmp = getAmplitude()
                val raw = (maxAmp / 8000f).coerceIn(0f, 1f)
                val normalized = kotlin.math.sqrt(raw)
                _currentAudioAmplitude.value = normalized.coerceIn(0.1f, 1.0f)
                kotlinx.coroutines.delay(40)
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

    fun askAdvisor(question: String, context: android.content.Context) {
        val currentKey = apiKey.value
        if (currentKey.isNullOrBlank()) {
            _errorMessage.value = "Mẹ ơi, chưa có API Key. Vào Cài đặt giúp con nhé."
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
                val dataContext = sortedTargets.joinToString(separator = "\n\n") { (m, y) ->
                    val filtered = allExpenses.filter {
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = it.timestamp
                        cal.get(Calendar.MONTH) + 1 == m && cal.get(Calendar.YEAR) == y
                    }
                    val header = "== Tháng $m/$y =="
                    if (filtered.isEmpty()) "$header\nKhông có giao dịch nào."
                    else header + "\n" + filtered.joinToString(separator = "\n") {
                        val sign = if (it.type == "expense") "-" else "+"
                        "- ${it.activity}: $sign${it.amount}đ (${it.category}, ${sdfDateSlashYear.format(Date(it.timestamp))})"
                    }
                }
                
                val systemPrompt = """
                    Bạn là một trợ lý quản lý tài chính cá nhân. Hãy xưng là 'con' và gọi người dùng là 'mẹ'.
                    Quy tắc NGHIÊM NGẶT:
                    1. XƯNG HÔ MẸ - CON BẮT BUỘC: TUYỆT ĐỐI mỗi câu trong câu trả lời PHẢI chứa đầy đủ CẢ HAI từ "mẹ" và "con" (không phân biệt vị trí đầu/giữa/cuối câu). Không được có bất kỳ câu nào thiếu một trong hai từ này. Ví dụ ĐÚNG: "Tháng này mẹ chi nhiều nhất cho Thực phẩm, con thấy khoảng 3tr.", "Con gợi ý mẹ giảm khoản ăn ngoài xuống 1tr nhé.". Ví dụ SAI: "Tháng này chi nhiều nhất cho Thực phẩm." (thiếu cả mẹ và con), "Con thấy ổn." (thiếu mẹ), "Mẹ ăn nhiều quá." (thiếu con). Giọng văn trưởng thành, điềm đạm, lịch sự. KHÔNG dùng "Dạ", "Thưa mẹ" ở đầu câu, KHÔNG lạm dụng "ạ" ở cuối câu. Không sến súa, máy móc. Đi thẳng vào số liệu.
                    2. TRẢ LỜI NGẮN GỌN (Dưới 50 chữ): Nếu mẹ hỏi câu hỏi chung chung, trả lời một cách cực kỳ ngắn gọn và xúc tích.
                    3. KHI CẦN LIỆT KÊ CHI TIẾT HOẶC BÁO CÁO (khi mẹ yêu cầu cụ thể như "chi tiết", "bảng", "liệt kê"): BẮT BUỘC trả về dữ liệu dưới dạng BẢNG bằng cú pháp sau:
                    [TABLE]
                    Tiêu đề 1 | Tiêu đề 2 | Tiêu đề 3
                    Dữ liệu 1 | Dữ liệu 2 | Dữ liệu 3
                    [/TABLE]
                    Bên ngoài bảng [TABLE][/TABLE], bạn chỉ được viết thêm 1-2 câu cực ngắn để chào hoặc tóm tắt.
                    4. KHÔNG SỬ DỤNG ký hiệu Markdown (dấu sao *, gạch -, thăng #) trong văn bản thường. 
                    5. LUÔN LUÔN mặc định phân tích và lấy số liệu của tháng hiện tại ($currentMonthVal/$currentYearVal) nếu mẹ không chỉ định rõ ràng tháng nào khác.
                    6. ĐỊNH DẠNG SỐ TIỀN: Tuyệt đối không viết số dài (như 35000, 35000000). Luôn viết tắt số tiền. Ví dụ: 35000 thành 35k, 35000000 thành 35tr, 1500000 thành 1.5tr.
                    7. TỰ ĐỘNG SỬA LỖI NHẬN DẠNG GIỌNG NÓI (Whisper STT hay nghe nhầm): Mẹ là phụ nữ trung niên Việt Nam, vừa là nội trợ vừa là GIÁO VIÊN — chi tiêu xoay quanh chợ búa, bếp núc, hoá đơn gia đình, và công việc trường lớp (in giáo án, đồ dùng dạy học, quỹ trường, quà học sinh). Khi gặp từ vô nghĩa đi kèm tiền, BẮT BUỘC suy luận theo bối cảnh này và sửa. Ví dụ: "mua lợi" -> "mua thịt lợn" (KHÔNG phải lợi nhuận), "in dán" -> "in giáo án", "rau muốn" -> "rau muống", "đổ sang" -> "đổ xăng", "tiền điên" -> "tiền điện", "vợ cho con" -> "vở cho con", "đám cuối" -> "đám cưới", "ba lăm" = 35, "lăm chục" = 50, "trăm" có thể bị nghe thành "tram"/"chăm", "triệu" thành "chiếu". KHÔNG BAO GIỜ giữ nguyên từ vô nghĩa.

                    Dữ liệu chi tiêu các tháng được chọn ($targetLabel):
                    $dataContext
                """.trimIndent()

                val systemMessage = Message(role = "system", content = systemPrompt)
                
                val request = ChatRequest(
                    messages = (listOf(systemMessage) + newHistory).map { com.example.aiexpensetrackeropenai.data.network.ApiMessage(it.role, it.content) }
                )

                val response = openAIApi.parseExpense(
                    authHeader = "Bearer ${currentKey.trim()}",
                    request = request
                )

                val content = response.choices.firstOrNull()?.message?.content
                if (content != null) {
                    val aiMsg = Message(role = "assistant", content = content)
                    lastReadMsgContent = content
                    
                    if (autoReadTts.value) {
                        playTextToSpeech(content, context, onReady = {
                            _chatMessages.value = (_chatMessages.value + aiMsg).takeLast(10)
                            _isLoading.value = false
                        })
                    } else {
                        _chatMessages.value = (_chatMessages.value + aiMsg).takeLast(10)
                        _isLoading.value = false
                    }
                } else {
                    _errorMessage.value = "Con chưa nhận được phản hồi mẹ ạ."
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Có gì đó không ổn mẹ ạ, thử lại nhé."
                _isLoading.value = false
            }
        }
    }

    fun parseExpense(text: String) {
        val currentKey = apiKey.value
        if (currentKey.isNullOrBlank()) {
            _addExpenseFailure.value = "Mẹ ơi, chưa có API Key. Vào Cài đặt giúp con nhé."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val formatter = java.time.format.DateTimeFormatter.ofPattern("EEEE, 'ngày' dd/MM/yyyy", vnLocale)
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
                            "amount": Số tiền (Kiểu số nguyên, ví dụ: 50k -> 50000),
                            "type": "expense" hoặc "income",
                            "category": "Tên danh mục",
                            "date": "yyyy-MM-dd"
                          }
                        ]
                        Quy tắc tính ngày (date):
                        - Nếu nhắc đến "Hôm qua", trừ đi 1 ngày. "Hôm kia" trừ 2 ngày. "Thứ [X] tuần này", tự tính dựa trên Hôm nay. Không nhắc đến thì lấy Hôm nay.
                        Quy tắc nhận diện và số tiền:
                        - Tự động hiểu các đơn vị lóng/viết tắt: "k" = nghìn, "củ" = triệu, "lít" = trăm nghìn, "tr" = triệu.
                        - BẤT KỲ CỤM TỪ NÀO (danh từ hoặc động từ) đi kèm số tiền ĐỀU LÀ khoản thu/chi hợp lệ. Không cần thiết phải có động từ. (Ví dụ: "phở 40k" -> tự hiểu là "Ăn phở", "tiền điện 500k" -> "Đóng tiền điện", "in giáo án 50k" -> "In giáo án").
                        - BỐI CẢNH NGƯỜI DÙNG (CỰC KỲ QUAN TRỌNG để suy luận): Người dùng là MẸ — phụ nữ trung niên Việt Nam, vừa là nội trợ trong gia đình vừa là GIÁO VIÊN. Vì thế chi tiêu thường xoay quanh: chợ búa (thịt, cá, rau, gạo, gia vị, hoa quả), nấu nướng cho cả nhà, hoá đơn điện nước gas internet, xăng xe, thuốc men, đồ dùng nhà cửa, quà cáp thăm hỏi, đám cưới đám ma, mua sắm cho con cái, VÀ các khoản liên quan đến công việc giáo viên: in giáo án, mua sách vở, đồ dùng dạy học, văn phòng phẩm, quà cho học sinh, nộp quỹ trường, công đoàn, sinh nhật đồng nghiệp. Khi suy luận từ bị nghe nhầm, LUÔN ưu tiên giả thuyết phù hợp với bối cảnh này.

                        - TỰ ĐỘNG SỬA LỖI WHISPER (STT) — BẮT BUỘC SỬA, KHÔNG ĐƯỢC GIỮ NGUYÊN từ vô nghĩa. Whisper hay nghe nhầm phụ âm cuối, dấu thanh, từ ít gặp. Danh sách lỗi phổ biến cần tự sửa:

                          * THỰC PHẨM / CHỢ BÚA:
                            - "mua lợi" / "thịt lợi" -> "mua lợn" / "thịt lợn"
                            - "thịt hẹo" / "thịt heo nái" lạ -> "thịt heo"
                            - "rau muốn" -> "rau muống"
                            - "rau cẩy" / "rau cài" -> "rau cải"
                            - "mua lá" / "ăn lá" (không có ngữ cảnh lá thuốc) -> "mua cá" / "ăn cá"
                            - "lưới" (đi chợ) -> "lươn"
                            - "đậu hủ" -> "đậu hũ" / "đậu phụ"
                            - "bí ngồi" -> "bí ngô" (tuỳ ngữ cảnh)
                            - "trứng kịt" -> "trứng vịt"
                            - "gà chiên" trong bối cảnh chợ có thể là "gà ta" / "gà tre"
                            - "tôm khô" vs "tôm khu" -> "tôm khô"
                            - "bún chỗ" / "bún cho" -> "bún chả" / "bún bò"
                            - "phỡ" / "phơ" -> "phở"
                            - "súp" có thể là "sữa", "sườn" (tuỳ ngữ cảnh giá tiền)

                          * GIÁO VIÊN / TRƯỜNG LỚP:
                            - "in dáng" / "in dán" / "in dạ" -> "in giáo án"
                            - "soạn giảng" / "soạn dán" -> "soạn giáo án"
                            - "vợ" (mua cho con/học sinh) -> "vở" (sổ vở)
                            - "thước kê" -> "thước kẻ"
                            - "phấn bản" -> "phấn bảng"
                            - "quỹ trường" / "quy trường" -> "quỹ trường"
                            - "công doan" -> "công đoàn"
                            - "quà 20-11" / "hai mươi mốt mười một" -> "quà 20/11" (Ngày Nhà giáo)

                          * HOÁ ĐƠN / TIỆN ÍCH:
                            - "đổ sang" / "đổ săng" / "đổ xang" -> "đổ xăng"
                            - "tiền điên" -> "tiền điện"
                            - "tiền nuốt" / "tiền nóc" -> "tiền nước"
                            - "tiền ga" / "tiền gát" -> "tiền gas"
                            - "in tơ nét" / "internet" / "mạng" -> "internet" / "tiền mạng"

                          * SINH HOẠT / ĂN UỐNG:
                            - "an sáng" / "ăn xáng" -> "ăn sáng"
                            - "cà fê" / "cà phế" -> "cà phê"
                            - "bia hồi" -> "bia hơi"

                          * HIẾU HỈ / THĂM HỎI:
                            - "đám cuối" / "đám cuôi" -> "đám cưới"
                            - "đám mà" / "đam ma" -> "đám ma"
                            - "thăm hồi" -> "thăm hỏi"
                            - "mừng thọ", "mừng tuổi", "lì xì" — giữ nguyên

                          * SỐ TIỀN — Whisper rất hay nghe nhầm số:
                            - "lăm" và "năm" lẫn lộn: "ba lăm" = 35, "ba mươi lăm" = 35. "năm chục" = 50.
                            - "trăm" có thể bị nghe thành "tram", "trầm", "chăm" -> sửa thành "trăm".
                            - "nghìn" có thể bị nghe thành "ngàn", "nghin" -> coi tương đương.
                            - "triệu" có thể bị nghe thành "chiếu", "triệu" -> sửa thành "triệu".
                            - "k", "ka" sau số -> nghìn. "tr" sau số -> triệu. "củ" -> triệu. "lít" sau số (vd: 5 lít = 500k) -> trăm nghìn.

                        - QUY TRÌNH SUY LUẬN: Khi gặp 1 từ lạ/vô nghĩa đi kèm số tiền, KHÔNG bao giờ giữ nguyên từ sai. Hãy hỏi nội bộ: "Trong bối cảnh mẹ là phụ nữ gia đình + giáo viên, từ nghe gần giống nhất với từ vừa nhận có nghĩa hợp lý là gì?" rồi dùng từ đó. Ví dụ "mua lợi 100k" -> bối cảnh nội trợ + đi chợ -> "Mua thịt lợn" (KHÔNG phải "lợi nhuận", "lợi ích"). Ví dụ "in dán 50k" -> bối cảnh giáo viên -> "In giáo án".
                        - Nếu người dùng viết trống không như "50k ăn sáng", "xăng 50", bạn hãy tự động điền activity hợp lý và lưu lại, tuyệt đối KHÔNG ĐƯỢC từ chối.
                        - Hãy thông minh và tha thứ cho lỗi chính tả, viết tắt. Cố gắng trích xuất bằng được dữ liệu nếu thấy có nhắc đến tiền.
                        - Chỉ trả về mảng rỗng [] nếu câu nói hoàn toàn không có ý nghĩa tài chính (ví dụ: "chào bạn", "hôm nay trời đẹp").
                        Các danh mục chuẩn bạn ĐƯỢC PHÉP gán: 'Mua sắm', 'Thực phẩm', 'Hóa đơn', 'Thăm hỏi, hiếu hỉ', 'Tiết kiệm', 'Công việc', 'Thu nhập', 'Cho vay', 'Khác'.
                        Phân biệt rõ:
                        - 'Mua sắm': đồ cá nhân, cả gia đình, sức khỏe thuốc thang.
                        - 'Thực phẩm': đồ sống, rau củ, gạo, hoa quả, cá, thịt.
                        - 'Hóa đơn': điện nước, xăng xe.
                        - 'Thăm hỏi, hiếu hỉ': BẮT BUỘC ghi rõ thăm hỏi ai, tên gì vào trường activity (ví dụ: 'Thăm hỏi ốm đau bác Tư', 'Đi đám cưới bạn B'), tuyệt đối không ghi cộc lốc là 'Thăm hỏi' hay 'Hiếu hỉ'.
                        - 'Tiết kiệm': các khoản mua vàng để tích trữ, gửi tiết kiệm (không phải chi tiêu).
                        - 'Công việc': nộp quỹ trường, chi phí công việc.
                        - 'Cho vay': các khoản cho người khác vay, hoặc thu nợ. (Ví dụ: 'Cho cậu Sơn vay', 'Thu nợ chị Hằng').
                        - 'Khác': các khoản không thuộc danh mục nào ở trên.
                        Ví dụ:
                        - 'mua cá thịt 100k' -> activity: 'Mua cá thịt', amount: 100000, type: 'expense', category: 'Thực phẩm'
                        - 'xăng 50' -> activity: 'Đổ xăng', amount: 50000, type: 'expense', category: 'Hóa đơn'
                        - 'đi đám cưới Tuấn 1 triệu' -> activity: 'Đi đám cưới Tuấn', amount: 1000000, type: 'expense', category: 'Thăm hỏi, hiếu hỉ'
                        - 'mua 1 chỉ vàng tiết kiệm 8 củ' -> activity: 'Mua 1 chỉ vàng', amount: 8000000, type: 'expense', category: 'Tiết kiệm'
                        - 'nộp quỹ trường 200k' -> activity: 'Nộp quỹ trường', amount: 200000, type: 'expense', category: 'Công việc'
                    """.trimIndent()
                )
                val userMessage = Message(role = "user", content = text)
                
                val request = ChatRequest(
                    messages = listOf(
                        com.example.aiexpensetrackeropenai.data.network.ApiMessage(systemMessage.role, systemMessage.content),
                        com.example.aiexpensetrackeropenai.data.network.ApiMessage(userMessage.role, userMessage.content)
                    )
                )
                
                val response = openAIApi.parseExpense(
                    authHeader = "Bearer ${currentKey.trim()}",
                    request = request
                )
                
                val content = response.choices.firstOrNull()?.message?.content
                if (content.isNullOrBlank()) {
                    _addExpenseFailure.value = "Con chưa nhận được phản hồi. Mẹ thử lại nhé."
                    return@launch
                }

                val parsedList: List<ParsedExpense> = try {
                    Gson().fromJson(content, Array<ParsedExpense>::class.java)?.toList() ?: emptyList()
                } catch (e: Exception) {
                    _addExpenseFailure.value = "Con chưa hiểu. Mẹ ghi rõ hoạt động và số tiền giúp con nhé."
                    return@launch
                }

                val validParsed = parsedList.filter {
                    it.activity.isNotBlank() && it.amount > 0 && (it.type == "income" || it.type == "expense")
                }

                if (validParsed.isEmpty()) {
                    _addExpenseFailure.value = "Con không thấy khoản nào. Mẹ ghi rõ hoạt động + số tiền nhé (ví dụ: 'mua cà phê 30k')."
                    return@launch
                }

                val entities = validParsed.map { parsed ->
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
                    
                var lastInserted: ExpenseEntity? = null
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    entities.forEach { entity ->
                        val id = expenseDao.insertExpense(entity)
                        val insertedEntity = entity.copy(id = id.toInt())
                        lastInserted = insertedEntity
                        syncToGoogleSheets(insertedEntity)
                    }
                }

                // Tự động nhảy UI về thời điểm của khoản chi đầu tiên
                entities.firstOrNull()?.let { firstEntity ->
                    val expDate = java.time.Instant.ofEpochMilli(firstEntity.timestamp)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                    val now = java.time.LocalDate.now()
                    val monthsDiff = (expDate.year - now.year) * 12 + (expDate.monthValue - now.monthValue)

                    _selectedMonthOffset.value = monthsDiff
                    _selectedWeek.value = getWeekIndexForDate(expDate)
                    _selectedDay.value = ""
                }
                lastInserted?.let {
                    _lastAddedCount.value = entities.size
                    _lastAddedExpense.value = it
                }
            } catch (e: Exception) {
                _addExpenseFailure.value = e.message ?: "Có gì đó không ổn mẹ ạ, thử lại nhé."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun parseReceiptImage(base64Image: String) {
        val currentKey = apiKey.value
        if (currentKey.isNullOrBlank()) {
            _addExpenseFailure.value = "Mẹ ơi, chưa có API Key. Vào Cài đặt giúp con nhé."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val formatter = java.time.format.DateTimeFormatter.ofPattern("EEEE, 'ngày' dd/MM/yyyy", vnLocale)
                val todayStr = java.time.LocalDate.now().format(formatter)

                val systemPromptText = """
                    Bạn là một trợ lý phân tích dữ liệu tài chính Việt Nam chuyên nghiệp. Nhiệm vụ của bạn là đọc hình ảnh hóa đơn hoặc chữ viết tay về thu chi và trả về một MẢNG CÁC OBJECT JSON (JSON Array), không kèm ký tự markdown (như ```json), không giải thích gì thêm.
                    Ngữ cảnh hiện tại: Hôm nay là $todayStr.
                    Cấu trúc JSON MẢNG bắt buộc phải tuân theo định dạng sau:
                    [
                      {
                        "activity": "Tên hoạt động rút gọn",
                        "amount": Số tiền (Kiểu số nguyên, ví dụ: 50k -> 50000),
                        "type": "expense" hoặc "income",
                        "category": "Tên danh mục",
                        "date": "yyyy-MM-dd"
                      }
                    ]
                    Quy tắc:
                    - Nếu hóa đơn là mua sắm siêu thị, tạp hóa, nhà hàng: Phân tách chi tiết từng mặt hàng và gộp chúng vào danh mục phù hợp.
                    - Hoặc có thể lấy tổng số tiền nếu hóa đơn quá dài và chung một danh mục.
                    - Các danh mục chuẩn bạn ĐƯỢC PHÉP gán: 'Mua sắm', 'Thực phẩm', 'Hóa đơn', 'Thăm hỏi, hiếu hỉ', 'Tiết kiệm', 'Công việc', 'Thu nhập', 'Cho vay', 'Khác'.
                    - Phân biệt rõ:
                      - 'Mua sắm': đồ cá nhân, cả gia đình, sức khỏe thuốc thang.
                      - 'Thực phẩm': đồ sống, rau củ, gạo, hoa quả, cá, thịt.
                      - 'Hóa đơn': điện nước, xăng xe, nhà hàng, quán ăn.
                      - 'Thăm hỏi, hiếu hỉ': BẮT BUỘC ghi rõ thăm hỏi ai, tên gì vào trường activity.
                      - 'Tiết kiệm': gửi tiết kiệm, mua vàng.
                      - 'Công việc': nộp quỹ trường, chi phí công việc.
                      - 'Cho vay': các khoản cho người khác vay, hoặc thu nợ.
                      - 'Khác': các khoản không thuộc danh mục nào ở trên.
                """.trimIndent()

                val systemMessage = ImageMessage(
                    role = "system",
                    content = listOf(ContentItem(type = "text", text = systemPromptText))
                )
                
                val userMessage = ImageMessage(
                    role = "user",
                    content = listOf(
                        ContentItem(type = "text", text = "Xin hãy phân tích hóa đơn hoặc chữ viết tay này."),
                        ContentItem(type = "image_url", imageUrl = ImageUrl(url = "data:image/jpeg;base64,$base64Image"))
                    )
                )

                val request = ImageChatRequest(
                    messages = listOf(systemMessage, userMessage)
                )

                val response = openAIApi.parseReceiptImage(
                    authHeader = "Bearer ${currentKey.trim()}",
                    request = request
                )

                val content = response.choices.firstOrNull()?.message?.content
                if (content.isNullOrBlank()) {
                    _addExpenseFailure.value = "Con chưa nhận được kết quả từ hình ảnh. Mẹ thử lại nhé."
                    return@launch
                }

                val parsedList: List<ParsedExpense> = try {
                    Gson().fromJson(content, Array<ParsedExpense>::class.java)?.toList() ?: emptyList()
                } catch (e: Exception) {
                    _addExpenseFailure.value = "Con chưa đọc được hóa đơn này. Mẹ chụp lại rõ hơn nhé."
                    return@launch
                }

                val validParsed = parsedList.filter {
                    it.activity.isNotBlank() && it.amount > 0 && (it.type == "income" || it.type == "expense")
                }

                if (validParsed.isEmpty()) {
                    _addExpenseFailure.value = "Con không tìm thấy khoản nào trong ảnh. Mẹ chụp lại giúp con nhé."
                    return@launch
                }

                val entities = validParsed.map { parsed ->
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
                        id = kotlin.random.Random.nextInt(Int.MIN_VALUE, -1),
                        activity = parsed.activity,
                        amount = parsed.amount.toInt(),
                        type = parsed.type,
                        category = parsed.category,
                        timestamp = expenseTimestamp
                    )
                }
                
                _pendingReceiptExpenses.value = entities
            } catch (e: Exception) {
                _addExpenseFailure.value = e.message ?: "Có lỗi xảy ra khi đọc ảnh mẹ ạ."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun startRecording(context: android.content.Context): Boolean {
        return try {
            audioRecorder = AudioRecorderHelper(context).apply { startRecording() }
            true
        } catch (e: Exception) {
            _errorMessage.value = "Lỗi khởi tạo thu âm: ${e.message}"
            audioRecorder = null
            false
        }
    }

    fun cancelRecording() {
        try {
            audioRecorder?.stopRecording()
        } catch (_: Exception) {}
        audioRecorder?.audioFile?.delete()
        audioRecorder = null
    }

    fun getRecordingAmplitude(): Int = audioRecorder?.getMaxAmplitude() ?: 0

    fun stopRecordingAndTranscribe(onResult: (String) -> Unit) {
        val recorder = audioRecorder
        if (recorder == null) {
            _errorMessage.value = "Chưa có bản thu âm nào."
            return
        }
        try {
            recorder.stopRecording()
        } catch (_: Exception) {}
        val file = recorder.audioFile
        audioRecorder = null

        if (file == null || !file.exists() || file.length() < 1024) {
            file?.delete()
            _errorMessage.value = "Bản thu quá ngắn, mẹ thử lại nhé."
            return
        }

        val currentKey = apiKey.value
        if (currentKey.isNullOrBlank()) {
            file.delete()
            _errorMessage.value = "Mẹ ơi, chưa có OpenAI API Key. Vào Cài đặt giúp con nhé."
            return
        }

        viewModelScope.launch {
            _isTranscribing.value = true
            try {
                val audioPart = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val body = file.asRequestBody("audio/ogg".toMediaType())
                    MultipartBody.Part.createFormData("file", file.name, body)
                }
                val modelPart = "whisper-1".toRequestBody("text/plain".toMediaType())
                val languagePart = "vi".toRequestBody("text/plain".toMediaType())
                val promptPart = "Đây là tiếng Việt, ghi chú thu chi gia đình: tiền k là nghìn, tr là triệu, củ là triệu, đồng. Ví dụ: mua rau 30k, đổ xăng 50, đám cưới 1tr."
                    .toRequestBody("text/plain".toMediaType())

                val response = openAIApi.transcribeAudio(
                    authHeader = "Bearer ${currentKey.trim()}",
                    audio = audioPart,
                    model = modelPart,
                    language = languagePart,
                    prompt = promptPart
                )
                val text = response.text.trim()
                if (text.isNotBlank()) {
                    onResult(text)
                } else {
                    _errorMessage.value = "Con chưa nghe rõ, mẹ thử lại nhé."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Lỗi nhận diện giọng nói: ${e.message}"
            } finally {
                file.delete()
                _isTranscribing.value = false
            }
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            expenseDao.deleteExpense(expense)
            deleteFromGoogleSheets(expense)
        }
    }

    private fun deleteFromGoogleSheets(expense: ExpenseEntity) {
        val url = webhookUrl.value
        if (url.isNullOrBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            _syncStatus.value = SyncStatus.SYNCING
            try {
                val json = JSONObject().apply {
                    put("action", "delete")
                    put("id", expense.id)
                    put("activity", expense.activity)
                    put("amount", expense.amount)
                    put("type", expense.type)
                    put("category", expense.category)
                    put("time", sdfDateTimeSlash.format(Date(expense.timestamp)))
                }

                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                val response = sharedHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
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

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearChat() {
        _chatMessages.value = emptyList()
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

    fun updateExpense(expense: ExpenseEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            expenseDao.updateExpense(expense)
            updateInGoogleSheets(expense)
        }
    }

    private fun updateInGoogleSheets(expense: ExpenseEntity) {
        val url = webhookUrl.value
        if (url.isNullOrBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            _syncStatus.value = SyncStatus.SYNCING
            try {
                val json = JSONObject().apply {
                    put("action", "update")
                    put("id", expense.id)
                    put("activity", expense.activity)
                    put("amount", expense.amount)
                    put("type", expense.type)
                    put("category", expense.category)
                    put("time", sdfDateTimeSlash.format(Date(expense.timestamp)))
                }

                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                val response = sharedHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
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
}
