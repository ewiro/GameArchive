package com.example.gamearchive

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.util.Locale

internal data class BangumiInfoboxValue(
    val label: String? = null,
    val text: String
)

internal fun flattenBangumiInfoboxValue(value: Any?): List<BangumiInfoboxValue> = when (value) {
    null -> emptyList()
    is String -> value.trim().takeIf { it.isNotEmpty() }
        ?.let { listOf(BangumiInfoboxValue(text = it)) }
        .orEmpty()
    is Number -> listOf(BangumiInfoboxValue(text = formatInfoboxNumber(value)))
    is Iterable<*> -> value.flatMap(::flattenBangumiInfoboxEntry)
    is Array<*> -> value.flatMap(::flattenBangumiInfoboxEntry)
    is Map<*, *> -> flattenBangumiInfoboxEntry(value)
    else -> value.toString().trim().takeIf { it.isNotEmpty() }
        ?.let { listOf(BangumiInfoboxValue(text = it)) }
        .orEmpty()
}

private fun flattenBangumiInfoboxEntry(value: Any?): List<BangumiInfoboxValue> {
    if (value !is Map<*, *>) return flattenBangumiInfoboxValue(value)
    val label = value["k"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    val nestedValue = value["v"] ?: return emptyList()
    return flattenBangumiInfoboxValue(nestedValue).map { item ->
        if (item.label == null) item.copy(label = label) else item
    }
}

private fun formatInfoboxNumber(value: Number): String {
    val number = value.toDouble()
    return if (number % 1.0 == 0.0) number.toLong().toString() else number.toString()
}

internal fun isWebsiteInfoboxKey(key: String): Boolean {
    val normalized = key.trim().lowercase(Locale.ROOT).replace(" ", "")
    return normalized in setOf(
        "官方网站", "官方網站", "官网", "官網", "website", "officialwebsite"
    )
}

internal fun isAnimationProductionKey(key: String): Boolean {
    val normalized = key.trim().lowercase(Locale.ROOT).replace(" ", "")
    return normalized in setOf(
        "动画制作", "動畫製作", "アニメーション制作", "animationproduction"
    )
}

internal fun isDirectorInfoboxKey(key: String): Boolean {
    val normalized = key.trim().lowercase(Locale.ROOT).replace(" ", "")
    return normalized in setOf(
        "导演", "導演", "监督", "監督", "director"
    )
}

internal fun openExternalWebLink(context: Context, rawUrl: String) {
    val uri = normalizeHttpUri(rawUrl)
    if (uri == null) {
        Toast.makeText(context, R.string.general_open_link_failed, Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.general_open_link_failed, Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, R.string.general_open_link_failed, Toast.LENGTH_SHORT).show()
    }
}

private fun normalizeHttpUri(rawUrl: String): Uri? {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) return null
    val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https")) return null
    if (uri.host.isNullOrBlank()) return null
    return uri
}
