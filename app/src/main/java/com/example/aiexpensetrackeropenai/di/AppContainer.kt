package com.example.aiexpensetrackeropenai.di

import android.content.Context
import com.example.aiexpensetrackeropenai.BuildConfig
import com.example.aiexpensetrackeropenai.data.local.AppDatabase
import com.example.aiexpensetrackeropenai.data.local.SettingsManager
import com.example.aiexpensetrackeropenai.data.network.OpenAIApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

interface AppContainer {
    val database: AppDatabase
    val openAIApi: OpenAIApi
    val settingsManager: SettingsManager
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }

    override val settingsManager: SettingsManager by lazy {
        SettingsManager(context)
    }

    private val retrofit: Retrofit by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create())
            .baseUrl("https://api.openai.com/")
            .client(client)
            .build()
    }

    override val openAIApi: OpenAIApi by lazy {
        retrofit.create(OpenAIApi::class.java)
    }
}
