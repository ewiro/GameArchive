# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# ==============================================
# 1. 保护数据模型类（Gson 反射解析依赖字段名，混淆会导致解析失败）
#    数据类已加 @Keep 注解作为主保护，这里按正确包名再加一层双保险
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class com.example.gamearchive.** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 2. 核心：保留所有泛型和反射信息（解决 ParameterizedType 报错的关键）
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keepattributes SourceFile, LineNumberTable

# 3. 保护 Retrofit / OkHttp / Gson
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }

# 4. 保护 Kotlin 元数据 (R8 经常会在这里出错)
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 5. 阻止对 Service 接口方法的混淆 (防止网络请求返回 null)
-keepclassmembers interface * {
    @retrofit2.http.* <methods>;
}

# 5.1 Retrofit 官方 R8 规则：保留泛型类型信息
# 修复 suspend 函数 + R8 混淆导致的 "Class cannot be cast to ParameterizedType" 崩溃
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep class kotlin.coroutines.Continuation

# 5.2 完整保留本项目的网络接口及其方法签名 (Retrofit 靠它解析返回类型)
-keep interface com.example.gamearchive.SteamApiService { *; }

# 6. 保护 Coil
-keep class coil.** { *; }

# 7. MIUI X 主题库 (KMP 库，不自带消费者混淆规则，R8 可能破坏主题/颜色系统)
-keep class top.yukonga.miuix.kmp.** { *; }
-keep interface top.yukonga.miuix.kmp.** { *; }

# 8. Compose Runtime — 防止 R8 优化模式破坏 Compose lambda/状态机/重组
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.ui.** { *; }

# 9. Lifecycle ViewModel — 防止 R8 移除 ViewModel 构造器导致无法实例化
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# 10. Kotlinx Coroutines — 防止 ServiceLoader 发现不到 MainDispatcher
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}