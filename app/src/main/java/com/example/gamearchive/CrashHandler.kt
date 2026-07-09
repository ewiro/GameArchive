package com.example.gamearchive

import android.app.Application
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

/** 全局崩溃捕获 — 崩溃时自动写日志文件，用户可在设置中查看/分享 */
class CrashHandler(private val app: Application) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))

            val log = buildString {
                appendLine("=== Game Archive Crash Report ===")
                appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("Thread: ${thread.name}")
                appendLine()
                appendLine(sw.toString())
            }

            val file = File(app.cacheDir, "crash_log.txt")
            file.writeText(log)
        } catch (_: Exception) {
            // 连日志都写不了，放弃
        } finally {
            defaultHandler?.uncaughtException(thread, throwable)
            Process.killProcess(Process.myPid())
        }
    }
}
