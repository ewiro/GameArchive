package com.example.gamearchive

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class ActivityKind { GAME, ANIME }

data class ActivityEntry(
    val kind: ActivityKind,
    val id: Int,
    val title: String,
    val secondaryTitle: String,
    val imageUrl: String,
    val gameMinutes: Int,
    val animeEpisodes: Int,
    val lastRecordedAt: Long
)

data class DailyActivity(
    val date: String,
    val gameMinutes: Int,
    val animeEpisodes: Int,
    val entries: List<ActivityEntry>
) {
    val score: Double get() = gameMinutes / 60.0 + animeEpisodes
}

data class ItemActivityRecord(
    val date: String,
    val amount: Int
)

data class ActivityYearSnapshot(
    val stats: Map<String, DailyActivity>,
    val history: Map<String, DailyActivity>,
    val availableYears: Set<Int>,
    val baselineOnly: Boolean
)

object ActivityStats {
    const val PREF_NAME = "activity_stats"

    private const val KEY_GAME_BASELINES = "game_baselines"
    private const val KEY_ANIME_BASELINES = "anime_baselines"
    private const val KEY_DAILY_ACTIVITY = "daily_activity"
    private const val KEY_GAME_OBSERVATIONS = "game_observations"
    private const val KEY_ANIME_OBSERVATIONS = "anime_observations"

    private val _revision = MutableLiveData(0L)
    val revision: LiveData<Long> = _revision
    private val revisionCounter = AtomicLong(0L)

    @Synchronized
    fun syncSteam(context: Context, games: List<GameInfo>, observedAt: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val baselines = parseObject(prefs.getString(KEY_GAME_BASELINES, null))
        val days = parseObject(prefs.getString(KEY_DAILY_ACTIVITY, null))
        val aggregated = games.groupBy { it.appid }.mapValues { (_, copies) ->
            copies.first().copy(playtime_forever = copies.sumOf { it.playtime_forever })
        }

        aggregated.values.forEach { game ->
            val key = game.appid.toString()
            val previous = if (baselines.has(key)) baselines.optInt(key) else null
            val current = game.playtime_forever.coerceAtLeast(0)
            if (
                previous != null &&
                current > previous &&
                GameMarks.getMark(context, game.appid) == R.string.mark_playing
            ) {
                addActivity(
                    days = days,
                    kind = ActivityKind.GAME,
                    id = game.appid,
                    title = game.name,
                    secondaryTitle = "",
                    imageUrl = steamPortraitUrl(game.appid),
                    gameMinutes = current - previous,
                    animeEpisodes = 0,
                    recordedAt = observedAt
                )
            }
            baselines.put(key, current)
        }

        prefs.edit()
            .putString(KEY_GAME_BASELINES, baselines.toString())
            .putString(KEY_DAILY_ACTIVITY, days.toString())
            .putString(
                KEY_GAME_OBSERVATIONS,
                (prefs.getString(KEY_GAME_OBSERVATIONS, "0")?.toIntOrNull() ?: 0)
                    .plus(1)
                    .toString()
            )
            .apply()
        notifyChanged()
    }

    @Synchronized
    fun syncBangumi(
        context: Context,
        collections: List<BangumiCollection>,
        watchedEpisodeCounts: Map<Int, Int>,
        observedAt: Long = System.currentTimeMillis()
    ) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val baselines = parseObject(prefs.getString(KEY_ANIME_BASELINES, null))
        val days = parseObject(prefs.getString(KEY_DAILY_ACTIVITY, null))

        collections.distinctBy { it.subject_id }.forEach { item ->
            val currentEpisodes = watchedEpisodeCounts[item.subject_id] ?: return@forEach
            val key = item.subject_id.toString()
            val previous = baselines.optJSONObject(key)
            val currentApiType = uiCollectionTypeToApi(item.type)
            if (previous != null) {
                val previousEpisodes = previous.optInt("episodes")
                val previousApiType = previous.optInt("type")
                val delta = currentEpisodes - previousEpisodes
                if (delta > 0 && (previousApiType == 3 || currentApiType == 3)) {
                    val subject = item.subject
                    addActivity(
                        days = days,
                        kind = ActivityKind.ANIME,
                        id = item.subject_id,
                        title = subject?.name.orEmpty(),
                        secondaryTitle = subject?.name_cn.orEmpty(),
                        imageUrl = subject?.images?.large
                            ?: subject?.images?.common
                            ?: subject?.images?.medium
                            ?: "",
                        gameMinutes = 0,
                        animeEpisodes = delta,
                        recordedAt = observedAt
                    )
                }
            }
            baselines.put(
                key,
                JSONObject()
                    .put("episodes", currentEpisodes.coerceAtLeast(0))
                    .put("type", currentApiType)
            )
        }

        prefs.edit()
            .putString(KEY_ANIME_BASELINES, baselines.toString())
            .putString(KEY_DAILY_ACTIVITY, days.toString())
            .putString(
                KEY_ANIME_OBSERVATIONS,
                (prefs.getString(KEY_ANIME_OBSERVATIONS, "0")?.toIntOrNull() ?: 0)
                    .plus(1)
                    .toString()
            )
            .apply()
        notifyChanged()
    }

    @Synchronized
    fun recordBangumiSave(
        context: Context,
        subjectId: Int,
        title: String,
        secondaryTitle: String,
        imageUrl: String,
        previousApiType: Int,
        currentApiType: Int,
        previousEpisodes: Int,
        currentEpisodes: Int,
        recordedAt: Long = System.currentTimeMillis()
    ) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val baselines = parseObject(prefs.getString(KEY_ANIME_BASELINES, null))
        val days = parseObject(prefs.getString(KEY_DAILY_ACTIVITY, null))
        val delta = currentEpisodes - previousEpisodes
        if (delta > 0 && (previousApiType == 3 || currentApiType == 3)) {
            addActivity(
                days = days,
                kind = ActivityKind.ANIME,
                id = subjectId,
                title = title,
                secondaryTitle = secondaryTitle,
                imageUrl = imageUrl,
                gameMinutes = 0,
                animeEpisodes = delta,
                recordedAt = recordedAt
            )
        }
        baselines.put(
            subjectId.toString(),
            JSONObject()
                .put("episodes", currentEpisodes.coerceAtLeast(0))
                .put("type", currentApiType)
        )
        prefs.edit()
            .putString(KEY_ANIME_BASELINES, baselines.toString())
            .putString(KEY_DAILY_ACTIVITY, days.toString())
            .apply()
        notifyChanged()
    }

    @Synchronized
    fun getYearStats(context: Context, year: Int): Map<String, DailyActivity> {
        val days = loadDays(context)
        val prefix = "$year-"
        val result = linkedMapOf<String, DailyActivity>()
        val keys = days.keys()
        while (keys.hasNext()) {
            val date = keys.next()
            if (date.startsWith(prefix)) result[date] = decodeDay(date, days.optJSONObject(date))
        }
        return result
    }

    @Synchronized
    fun getDayActivities(context: Context, date: String): DailyActivity {
        val days = loadDays(context)
        return decodeDay(date, days.optJSONObject(date))
    }

    @Synchronized
    fun getGameRecords(context: Context, appId: Int): List<ItemActivityRecord> =
        getItemRecords(context, ActivityKind.GAME, appId, "game_minutes")

    @Synchronized
    fun getAnimeRecords(context: Context, subjectId: Int): List<ItemActivityRecord> =
        getItemRecords(context, ActivityKind.ANIME, subjectId, "anime_episodes")

    @Synchronized
    fun getAvailableYears(context: Context): Set<Int> {
        val days = loadDays(context)
        val years = mutableSetOf<Int>()
        val keys = days.keys()
        while (keys.hasNext()) keys.next().take(4).toIntOrNull()?.let(years::add)
        return years
    }

    /** 一次解析统计 JSON，同时返回记录页需要的全部年度状态。 */
    @Synchronized
    fun getYearSnapshot(context: Context, year: Int): ActivityYearSnapshot {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val days = parseObject(prefs.getString(KEY_DAILY_ACTIVITY, null))
        val prefix = "$year-"
        val stats = linkedMapOf<String, DailyActivity>()
        val history = linkedMapOf<String, DailyActivity>()
        val years = mutableSetOf<Int>()
        val keys = days.keys()
        while (keys.hasNext()) {
            val date = keys.next()
            date.take(4).toIntOrNull()?.let(years::add)
            val day = decodeDay(date, days.optJSONObject(date))
            history[date] = day
            if (date.startsWith(prefix)) stats[date] = day
        }
        val hasBaseline =
            parseObject(prefs.getString(KEY_GAME_BASELINES, null)).length() > 0 ||
                parseObject(prefs.getString(KEY_ANIME_BASELINES, null)).length() > 0
        val gameObservations =
            prefs.getString(KEY_GAME_OBSERVATIONS, "0")?.toIntOrNull() ?: 0
        val animeObservations =
            prefs.getString(KEY_ANIME_OBSERVATIONS, "0")?.toIntOrNull() ?: 0
        return ActivityYearSnapshot(
            stats = stats,
            history = history,
            availableYears = years,
            baselineOnly = hasBaseline && gameObservations < 2 && animeObservations < 2
        )
    }

    fun hasBaseline(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return parseObject(prefs.getString(KEY_GAME_BASELINES, null)).length() > 0 ||
            parseObject(prefs.getString(KEY_ANIME_BASELINES, null)).length() > 0
    }

    fun isBaselineOnly(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val gameObservations =
            prefs.getString(KEY_GAME_OBSERVATIONS, "0")?.toIntOrNull() ?: 0
        val animeObservations =
            prefs.getString(KEY_ANIME_OBSERVATIONS, "0")?.toIntOrNull() ?: 0
        return hasBaseline(context) && gameObservations < 2 && animeObservations < 2
    }

    fun notifyChanged() {
        _revision.postValue(revisionCounter.incrementAndGet())
    }

    private fun loadDays(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return parseObject(prefs.getString(KEY_DAILY_ACTIVITY, null))
    }

    private fun getItemRecords(
        context: Context,
        kind: ActivityKind,
        id: Int,
        amountKey: String
    ): List<ItemActivityRecord> {
        val days = loadDays(context)
        val entryKey = "${kind.name.lowercase(Locale.US)}:$id"
        val records = mutableListOf<ItemActivityRecord>()
        val dates = days.keys()
        while (dates.hasNext()) {
            val date = dates.next()
            val entry = days.optJSONObject(date)
                ?.optJSONObject("entries")
                ?.optJSONObject(entryKey)
                ?: continue
            val amount = entry.optInt(amountKey)
            if (amount > 0) records += ItemActivityRecord(date, amount)
        }
        return records.sortedByDescending { it.date }
    }

    private fun decodeDay(date: String, source: JSONObject?): DailyActivity {
        if (source == null) return DailyActivity(date, 0, 0, emptyList())
        val entriesObject = source.optJSONObject("entries") ?: JSONObject()
        val entries = mutableListOf<ActivityEntry>()
        val keys = entriesObject.keys()
        while (keys.hasNext()) {
            val entry = entriesObject.optJSONObject(keys.next()) ?: continue
            val kind = runCatching {
                ActivityKind.valueOf(entry.optString("kind"))
            }.getOrNull() ?: continue
            entries += ActivityEntry(
                kind = kind,
                id = entry.optInt("id"),
                title = entry.optString("title"),
                secondaryTitle = entry.optString("secondary_title"),
                imageUrl = entry.optString("image_url"),
                gameMinutes = entry.optInt("game_minutes"),
                animeEpisodes = entry.optInt("anime_episodes"),
                lastRecordedAt = entry.optLong("last_recorded_at")
            )
        }
        return DailyActivity(
            date = date,
            gameMinutes = source.optInt("game_minutes"),
            animeEpisodes = source.optInt("anime_episodes"),
            entries = entries.sortedByDescending { it.lastRecordedAt }
        )
    }

    private fun addActivity(
        days: JSONObject,
        kind: ActivityKind,
        id: Int,
        title: String,
        secondaryTitle: String,
        imageUrl: String,
        gameMinutes: Int,
        animeEpisodes: Int,
        recordedAt: Long
    ) {
        val date = localDate(recordedAt)
        val day = days.optJSONObject(date) ?: JSONObject()
        val entries = day.optJSONObject("entries") ?: JSONObject()
        val entryKey = "${kind.name.lowercase(Locale.US)}:$id"
        val existing = entries.optJSONObject(entryKey) ?: JSONObject()
        existing
            .put("kind", kind.name)
            .put("id", id)
            .put("title", title)
            .put("secondary_title", secondaryTitle)
            .put("image_url", imageUrl)
            .put("game_minutes", existing.optInt("game_minutes") + gameMinutes)
            .put("anime_episodes", existing.optInt("anime_episodes") + animeEpisodes)
            .put("last_recorded_at", recordedAt)
        entries.put(entryKey, existing)
        day
            .put("game_minutes", day.optInt("game_minutes") + gameMinutes)
            .put("anime_episodes", day.optInt("anime_episodes") + animeEpisodes)
            .put("entries", entries)
        days.put(date, day)
    }

    private fun parseObject(value: String?): JSONObject =
        runCatching { JSONObject(value ?: "{}") }.getOrElse { JSONObject() }

    private fun localDate(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))

    private fun uiCollectionTypeToApi(type: Int): Int = when (type) {
        2 -> 3
        3 -> 2
        else -> type
    }

    private fun steamPortraitUrl(appId: Int): String =
        "https://shared.cloudflare.steamstatic.com/store_item_assets/steam/apps/$appId/library_600x900_2x.jpg"
}
