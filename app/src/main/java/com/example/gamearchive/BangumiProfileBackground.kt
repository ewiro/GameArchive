package com.example.gamearchive

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Stores the optional Bangumi profile background as an app-private local file.
 * It intentionally does not use SharedPreferences so DataBackup never exports it.
 */
object BangumiProfileBackground {
    private const val DIRECTORY_NAME = "bangumi_profile"
    private const val FILE_NAME = "background"
    private const val TEMP_FILE_NAME = "background.tmp"

    fun file(context: Context): File =
        File(File(context.filesDir, DIRECTORY_NAME), FILE_NAME)

    fun exists(context: Context): Boolean = file(context).isFile

    fun save(context: Context, uri: Uri): Boolean {
        val directory = File(context.filesDir, DIRECTORY_NAME)
        if (!directory.exists() && !directory.mkdirs()) return false

        val target = File(directory, FILE_NAME)
        val temporary = File(directory, TEMP_FILE_NAME)
        temporary.delete()

        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            } ?: return false
            temporary.length() > 0L
        }.getOrDefault(false)

        if (!copied) {
            temporary.delete()
            return false
        }

        if (target.exists() && !target.delete()) {
            temporary.delete()
            return false
        }
        return temporary.renameTo(target).also { renamed ->
            if (!renamed) temporary.delete()
        }
    }

    fun clear(context: Context): Boolean {
        val target = file(context)
        return !target.exists() || target.delete()
    }
}
