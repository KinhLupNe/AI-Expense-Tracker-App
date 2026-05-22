package com.example.aiexpensetrackeropenai.data.network

import com.google.gson.annotations.SerializedName

data class ChatRequest(
    val model: String = "gpt-4o-mini",
    val messages: List<Message>,
    @SerializedName("response_format") val responseFormat: ResponseFormat? = null,
    val temperature: Double = 0.0
)

data class Message(
    val role: String,
    val content: String
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

data class TranscriptionResponse(
    val text: String
)
