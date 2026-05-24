# Preserve crash line numbers
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-keep class retrofit2.KotlinExtensions { *; }
-keep class retrofit2.KotlinExtensions$* { *; }
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Gson
-keepattributes *Annotation*
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Gson TypeToken — preserve generic info on anonymous subclasses so
# `object : TypeToken<List<...>>() {}.type` resolves to ParameterizedType
# (fixes: java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType)
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Keep @SerializedName fields when obfuscation strips them
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Network DTOs (Gson reflection — keep field names)
-keep class com.example.aiexpensetrackeropenai.data.network.** { *; }
-keep interface com.example.aiexpensetrackeropenai.data.network.OpenAIApi { *; }
# Room entities
-keep class com.example.aiexpensetrackeropenai.data.local.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep interface kotlin.coroutines.Continuation { *; }
-keep class kotlin.coroutines.Continuation { *; }
-keep class kotlin.coroutines.jvm.internal.** { *; }

# Strip verbose/debug logs in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# ========================
# R8 full-mode hardening
# ========================

# Kotlin metadata — cần cho reflection của Kotlin (data class, sealed, etc.)
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# ViewModel — Compose viewModels() inflate qua reflection / factory
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.lifecycle.ViewModelProvider$Factory { *; }

# Toàn bộ ViewModel + UI state holders trong app
-keep class com.example.aiexpensetrackeropenai.ui.MainViewModel { *; }
-keep class com.example.aiexpensetrackeropenai.ui.MainViewModel$* { *; }
-keep class com.example.aiexpensetrackeropenai.ui.MainViewModel$Companion { *; }

# Data classes / enums dùng trong UI state — không obfuscate field name
-keep class com.example.aiexpensetrackeropenai.ui.SyncStatus { *; }
-keep class com.example.aiexpensetrackeropenai.ui.AnalyzeState { *; }
-keep class com.example.aiexpensetrackeropenai.ui.CategorySummary { *; }
-keep class com.example.aiexpensetrackeropenai.ui.WeekInfo { *; }

# Giữ enum values()/valueOf — R8 fullMode hay xoá
-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Coroutines — hardening cho R8 full-mode
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-keep class kotlinx.coroutines.flow.** { *; }
-keep class kotlinx.coroutines.android.** { *; }

# Vosk đã gỡ — KHÔNG cần keep nữa
-dontwarn org.vosk.**

# Bỏ warning của JNA (vốn cho Vosk, hiện không dùng) — phòng case còn tham chiếu
-dontwarn com.sun.jna.**

# Update manager — dùng reflection để parse JSON manifest
-keep class com.example.aiexpensetrackeropenai.util.UpdateManager { *; }
-keep class com.example.aiexpensetrackeropenai.util.UpdateManager$* { *; }
