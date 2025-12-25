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
# 1. 阻止对你的包名下所有内容的修改
-keep class com.example.steamtracker.** { *; }
-keep interface com.example.steamtracker.** { *; }
-keepclassmembers class com.example.steamtracker.** { *; }

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

# 6. 保护 Coil
-keep class coil.** { *; }