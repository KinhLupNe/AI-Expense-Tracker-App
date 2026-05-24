package com.example.aiexpensetrackeropenai.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object UpdateManager {
    private val client = OkHttpClient()
    private const val REPO_URL = "https://api.github.com/repos/KinhLupNe/AI-Expense-Tracker-App/releases/latest"
    private const val TAG = "UpdateManager"

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo

    data class UpdateInfo(val version: String, val downloadUrl: String, val releaseNotes: String)

    suspend fun checkUpdate(currentVersionName: String) {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(REPO_URL).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = response.body?.string()
                    if (json != null) {
                        val jsonObject = JSONObject(json)
                        val tagName = jsonObject.getString("tag_name")
                        // Usually tags are "v1.0", strip "v" if present
                        val latestVersion = tagName.removePrefix("v")
                        val currentVersion = currentVersionName.removePrefix("v")
                        
                        if (latestVersion != currentVersion) {
                            val body = jsonObject.optString("body", "New update available.")
                            val assets = jsonObject.getJSONArray("assets")
                            var apkUrl: String? = null
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                if (asset.getString("name").endsWith(".apk")) {
                                    apkUrl = asset.getString("browser_download_url")
                                    break
                                }
                            }
                            
                            if (apkUrl != null) {
                                _updateInfo.value = UpdateInfo(latestVersion, apkUrl, body)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates", e)
            }
        }
    }

    suspend fun downloadAndInstall(context: Context, url: String) {
        withContext(Dispatchers.IO) {
            try {
                _downloadProgress.value = 0f
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    _downloadProgress.value = null
                    return@withContext
                }

                val body = response.body ?: return@withContext
                val contentLength = body.contentLength()
                val inputStream = body.byteStream()

                val updateDir = File(context.externalCacheDir ?: context.cacheDir, "updates")
                if (!updateDir.exists()) updateDir.mkdirs()
                
                val apkFile = File(updateDir, "update.apk")
                if (apkFile.exists()) apkFile.delete()

                val outputStream = FileOutputStream(apkFile)
                val buffer = ByteArray(8192)
                var totalBytesRead = 0L
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (contentLength > 0) {
                        val progress = totalBytesRead.toFloat() / contentLength.toFloat()
                        _downloadProgress.value = progress
                    }
                }
                
                outputStream.flush()
                outputStream.close()
                inputStream.close()

                _downloadProgress.value = 1f
                
                installApk(context, apkFile)
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading APK", e)
                _downloadProgress.value = null
            }
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK", e)
        }
    }
}
