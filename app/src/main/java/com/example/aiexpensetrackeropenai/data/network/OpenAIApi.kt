package com.example.aiexpensetrackeropenai.data.network

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenAIApi {
    @POST("v1/chat/completions")
    suspend fun parseExpense(
        @Header("Authorization") authHeader: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: ChatRequest
    ): ChatResponse

    @retrofit2.http.Multipart
    @POST("v1/audio/transcriptions")
    suspend fun transcribeAudio(
        @Header("Authorization") authHeader: String,
        @retrofit2.http.Part audio: okhttp3.MultipartBody.Part,
        @retrofit2.http.Part("model") model: okhttp3.RequestBody,
        @retrofit2.http.Part("language") language: okhttp3.RequestBody,
        @retrofit2.http.Part("prompt") prompt: okhttp3.RequestBody
    ): TranscriptionResponse
}
