package com.example.aiexpensetrackeropenai.data.network

import com.google.gson.annotations.SerializedName

data class ChatRequest(
    val model: String = "gpt-4o-mini",
    val messages: List<ApiMessage>,
    @SerializedName("response_format") val responseFormat: ResponseFormat? = null,
    val temperature: Double = 0.0
)

data class ApiMessage(
    val role: String,
    val content: String
)

data class Message(
    val role: String,
    val content: String,
    val id: String = java.util.UUID.randomUUID().toString()
)

data class ImageChatRequest(
    val model: String = "gpt-4o",
    val messages: List<ImageMessage>,
    @SerializedName("response_format") val responseFormat: ResponseFormat? = null,
    val temperature: Double = 0.0
)

data class ImageMessage(
    val role: String,
    val content: List<ContentItem>
)

data class ContentItem(
    val type: String, // "text" or "image_url"
    val text: String? = null,
    @SerializedName("image_url") val imageUrl: ImageUrl? = null
)

data class ImageUrl(
    val url: String // "data:image/jpeg;base64,{base64_string}"
)

data class ResponseFormat(
    val type: String = "json_object"
)

data class ChatResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: Message
)

data class ParsedExpense(
    val activity: String,
    val amount: Double,
    val type: String, // "income" or "expense"
    val category: String,
    val date: String? // "yyyy-MM-dd"
)

data class ParsedExpenseList(
    val expenses: List<ParsedExpense>
)

data class TranscriptionResponse(
    val text: String
)

data class SpeechRequest(
    val model: String = "tts-1",
    val input: String,
    val voice: String = "onyx",
    @SerializedName("response_format") val responseFormat: String = "mp3"
)
