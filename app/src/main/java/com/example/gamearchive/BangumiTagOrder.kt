package com.example.gamearchive

import android.content.Context
import org.json.JSONArray
import java.text.Normalizer
import java.util.Locale

/**
 * Bangumi 服务端会按标签名称重新排序后再保存。
 * 这里仅记录用户最后一次输入的顺序，回读时仍以服务端返回的标签集合为准。
 */
object BangumiTagOrder {
    const val PREF_NAME = "bangumi_tag_order"

    fun save(context: Context, username: String, subjectId: Int, tags: List<String>) {
        if (username.isBlank()) return
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key(username, subjectId), JSONArray(tags).toString())
            .apply()
    }

    fun get(context: Context, username: String, subjectId: Int): List<String>? {
        if (username.isBlank()) return null
        val json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(key(username, subjectId), null)
            ?: return null
        return parse(json)
    }

    fun snapshot(context: Context, username: String): Map<Int, List<String>> {
        if (username.isBlank()) return emptyMap()
        val prefix = keyPrefix(username)
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .all
            .mapNotNull { (key, value) ->
                val subjectId = key.removePrefix(prefix)
                    .takeIf { key.startsWith(prefix) }
                    ?.toIntOrNull()
                    ?: return@mapNotNull null
                val tags = (value as? String)?.let(::parse) ?: return@mapNotNull null
                subjectId to tags
            }
            .toMap()
    }

    fun restore(serverTags: List<String>?, preferredOrder: List<String>?): List<String>? {
        if (serverTags == null || preferredOrder == null || serverTags.size < 2) {
            return serverTags
        }

        val remaining = LinkedHashMap<String, String>(serverTags.size)
        serverTags.forEach { tag -> remaining.putIfAbsent(normalizedKey(tag), tag) }

        return buildList(serverTags.size) {
            preferredOrder.forEach { tag ->
                remaining.remove(normalizedKey(tag))?.let(::add)
            }
            addAll(remaining.values)
        }
    }

    fun hasSameOrder(serverTags: List<String>?, preferredOrder: List<String>): Boolean =
        serverTags.orEmpty().map(::normalizedKey) == preferredOrder.map(::normalizedKey)

    private fun keyPrefix(username: String) = "$username:"

    private fun key(username: String, subjectId: Int) = "${keyPrefix(username)}$subjectId"

    private fun parse(json: String): List<String>? = runCatching {
        val array = JSONArray(json)
        List(array.length()) { index -> array.getString(index) }
    }.getOrNull()

    private fun normalizedKey(tag: String): String =
        Normalizer.normalize(tag.trim(), Normalizer.Form.NFKC).lowercase(Locale.ROOT)
}
