package com.example.gamearchive

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.math.BigDecimal
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

enum class ActivityKind { GAME, ANIME }

data class ActivityEntry(
    val kind: ActivityKind,
    val id: Int,
    val title: String,
    val secondaryTitle: String,
    val imageUrl: String,
    val gameMinutes: Int,
    val animeEpisodes: Double,
    val lastRecordedAt: Long
)

data class DailyActivity(
    val date: String,
    val gameMinutes: Int,
    val animeEpisodes: Double,
    val entries: List<ActivityEntry>
) {
    val score: Double get() = gameMinutes / 60.0 + animeEpisodes
}

data class ItemActivityRecord(
    val date: String,
    val amount: Double
)

data class ActivityImportRecord(
    val kind: ActivityKind,
    val id: Int,
    val title: String,
    val secondaryTitle: String,
    val imageUrl: String,
    val date: String,
    val gameMinutes: Int = 0,
    val animeEpisodes: Double = 0.0
)

fun formatEpisodeAmount(amount: Double): String {
    val rounded = amount.toLong()
    return if (abs(amount - rounded) < 0.000001) {
        rounded.toString()
    } else {
        BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString()
    }
}

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
            copies.first().copy(
                playtime_forever = copies.sumOf { it.playtime_forever },
                rtime_last_played = copies.maxOf { it.rtime_last_played }
            )
        }

        aggregated.values.forEach { game ->
            val key = game.appid.toString()
            val previous = if (baselines.has(key)) baselines.optInt(key) else null
            val current = game.playtime_forever.coerceAtLeast(0)
            val playedAt = steamPlayedAt(game, observedAt)
            if (playedAt != null) {
                moveLegacyDetectedActivity(days, game, observedAt, playedAt)
            }
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
                    animeEpisodes = 0.0,
                    recordedAt = playedAt ?: observedAt
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
        watchedEpisodeCollections: Map<Int, List<BangumiUserEpisodeCollection>>,
        observedAt: Long = System.currentTimeMillis()
    ) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val baselines = parseObject(prefs.getString(KEY_ANIME_BASELINES, null))
        val days = parseObject(prefs.getString(KEY_DAILY_ACTIVITY, null))

        collections.distinctBy { it.subject_id }.forEach { item ->
            val watchedEpisodes = watchedEpisodeCollections[item.subject_id]
                ?.filter { it.type == 2 }
                ?: return@forEach
            val currentEpisodes = watchedEpisodes.size
            val currentEpisodeIds = watchedEpisodes.mapTo(linkedSetOf()) { it.episode.id }
            val key = item.subject_id.toString()
            val previous = baselines.optJSONObject(key)
            val previousEpisodeIds = previous?.let(::previousEpisodeIds)
            val currentApiType = uiCollectionTypeToApi(item.type)
            if (previous != null) {
                val previousEpisodes = previous.optInt("episodes")
                val previousApiType = previous.optInt("type")
                val delta = currentEpisodes - previousEpisodes
                if (delta > 0 && (previousApiType == 3 || currentApiType == 3)) {
                    val subject = item.subject
                    val candidates = watchedEpisodes
                        .filter { previousEpisodeIds == null || it.episode.id !in previousEpisodeIds }
                        .sortedByDescending {
                            bangumiEpisodeUpdatedAt(it.updated_at, observedAt)
                        }
                        .take(delta)
                    candidates.forEach { episode ->
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
                            animeEpisodes = 1.0,
                            recordedAt = bangumiEpisodeUpdatedAt(
                                episode.updated_at,
                                observedAt
                            )
                        )
                    }
                    repeat(delta - candidates.size) {
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
                            animeEpisodes = 1.0,
                            recordedAt = observedAt
                        )
                    }
                }
            }
            if (previous != null && previousEpisodeIds == null) {
                reconcileLegacyAnimeDates(
                    days = days,
                    item = item,
                    watchedEpisodes = watchedEpisodes,
                    observedAt = observedAt
                )
            }
            baselines.put(
                key,
                animeBaseline(
                    episodes = currentEpisodes,
                    apiType = currentApiType,
                    episodeIds = currentEpisodeIds
                )
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
        currentEpisodeIds: Collection<Int>,
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
                animeEpisodes = delta.toDouble(),
                recordedAt = recordedAt
            )
        }
        baselines.put(
            subjectId.toString(),
            animeBaseline(
                episodes = currentEpisodes,
                apiType = currentApiType,
                episodeIds = currentEpisodeIds
            )
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
    fun updateAnimeRecord(
        context: Context,
        subjectId: Int,
        date: String,
        episodes: Double
    ): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val days = parseObject(prefs.getString(KEY_DAILY_ACTIVITY, null))
        val day = days.optJSONObject(date) ?: return false
        val entries = day.optJSONObject("entries") ?: return false
        val entryKey = "${ActivityKind.ANIME.name.lowercase(Locale.US)}:$subjectId"
        val entry = entries.optJSONObject(entryKey) ?: return false
        val previousEpisodes = entry.optDouble("anime_episodes", 0.0)
        val updatedEpisodes = episodes.coerceAtLeast(0.0)
        if (previousEpisodes == updatedEpisodes) return false

        if (updatedEpisodes == 0.0) {
            entries.remove(entryKey)
        } else {
            entry.put("anime_episodes", updatedEpisodes)
            entries.put(entryKey, entry)
        }

        var animeTotal = 0.0
        val entryKeys = entries.keys()
        while (entryKeys.hasNext()) {
            animeTotal += entries.optJSONObject(entryKeys.next())
                ?.optDouble("anime_episodes", 0.0)
                ?: 0.0
        }
        day
            .put("anime_episodes", animeTotal.coerceAtLeast(0.0))
            .put("entries", entries)
        if (
            day.optInt("game_minutes") == 0 &&
            day.optDouble("anime_episodes", 0.0) == 0.0 &&
            entries.length() == 0
        ) {
            days.remove(date)
        } else {
            days.put(date, day)
        }
        prefs.edit().putString(KEY_DAILY_ACTIVITY, days.toString()).apply()
        notifyChanged()
        return true
    }

    /**
     * 导入本地历史记录。已有的同条目同日期记录保持不变，确保重复导入不会叠加，
     * 也不会覆盖由 Steam 或 Bangumi 同步得到的本地记录。
     */
    @Synchronized
    fun importRecords(context: Context, records: List<ActivityImportRecord>): Int {
        if (records.isEmpty()) return 0
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val days = parseObject(prefs.getString(KEY_DAILY_ACTIVITY, null))
        var importedCount = 0

        records.forEach { record ->
            val recordedAt = dateTimestamp(record.date) ?: return@forEach
            val day = days.optJSONObject(record.date) ?: JSONObject()
            val entries = day.optJSONObject("entries") ?: JSONObject()
            val entryKey = "${record.kind.name.lowercase(Locale.US)}:${record.id}"
            if (entries.optJSONObject(entryKey) != null) return@forEach
            if (record.gameMinutes <= 0 && record.animeEpisodes <= 0.0) return@forEach

            val entry = JSONObject()
                .put("kind", record.kind.name)
                .put("id", record.id)
                .put("title", record.title)
                .put("secondary_title", record.secondaryTitle)
                .put("image_url", record.imageUrl)
                .put("game_minutes", record.gameMinutes.coerceAtLeast(0))
                .put("anime_episodes", record.animeEpisodes.coerceAtLeast(0.0))
                .put("last_recorded_at", recordedAt)
                .put("source", "obsidian")
            entries.put(entryKey, entry)
            day
                .put("game_minutes", day.optInt("game_minutes") + record.gameMinutes)
                .put(
                    "anime_episodes",
                    day.optDouble("anime_episodes", 0.0) + record.animeEpisodes
                )
                .put("entries", entries)
            days.put(record.date, day)
            importedCount++
        }

        if (importedCount > 0) {
            prefs.edit().putString(KEY_DAILY_ACTIVITY, days.toString()).apply()
            notifyChanged()
        }
        return importedCount
    }

    /** 移除旧版导入器误匹配到其他条目的 Obsidian 记录。 */
    @Synchronized
    fun removeImportedRecords(
        context: Context,
        kind: ActivityKind,
        id: Int,
        dates: Set<String>
    ): Int {
        if (dates.isEmpty()) return 0
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val days = parseObject(prefs.getString(KEY_DAILY_ACTIVITY, null))
        val entryKey = "${kind.name.lowercase(Locale.US)}:$id"
        var removedCount = 0

        dates.forEach { date ->
            val day = days.optJSONObject(date) ?: return@forEach
            val entries = day.optJSONObject("entries") ?: return@forEach
            val entry = entries.optJSONObject(entryKey) ?: return@forEach
            if (entry.optString("source") != "obsidian") return@forEach
            entries.remove(entryKey)
            day
                .put(
                    "game_minutes",
                    (day.optInt("game_minutes") - entry.optInt("game_minutes"))
                        .coerceAtLeast(0)
                )
                .put(
                    "anime_episodes",
                    (
                        day.optDouble("anime_episodes", 0.0) -
                            entry.optDouble("anime_episodes", 0.0)
                        ).coerceAtLeast(0.0)
                )
                .put("entries", entries)
            if (
                day.optInt("game_minutes") == 0 &&
                day.optDouble("anime_episodes", 0.0) == 0.0 &&
                entries.length() == 0
            ) {
                days.remove(date)
            } else {
                days.put(date, day)
            }
            removedCount++
        }

        if (removedCount > 0) {
            prefs.edit().putString(KEY_DAILY_ACTIVITY, days.toString()).apply()
            notifyChanged()
        }
        return removedCount
    }

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
    fun getYearSnapshot(
        context: Context,
        year: Int,
        includeAnime: Boolean = true
    ): ActivityYearSnapshot {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val days = parseObject(prefs.getString(KEY_DAILY_ACTIVITY, null))
        val prefix = "$year-"
        val stats = linkedMapOf<String, DailyActivity>()
        val history = linkedMapOf<String, DailyActivity>()
        val years = mutableSetOf<Int>()
        val keys = days.keys()
        while (keys.hasNext()) {
            val date = keys.next()
            val day = decodeDay(date, days.optJSONObject(date))
            val visibleDay = if (includeAnime) {
                day
            } else {
                day.copy(
                    animeEpisodes = 0.0,
                    entries = day.entries.filter { it.kind == ActivityKind.GAME }
                )
            }
            if (includeAnime || visibleDay.gameMinutes > 0 || visibleDay.entries.isNotEmpty()) {
                date.take(4).toIntOrNull()?.let(years::add)
                history[date] = visibleDay
                if (date.startsWith(prefix)) stats[date] = visibleDay
            }
        }
        val hasGameBaseline =
            parseObject(prefs.getString(KEY_GAME_BASELINES, null)).length() > 0
        val hasAnimeBaseline =
            parseObject(prefs.getString(KEY_ANIME_BASELINES, null)).length() > 0
        val hasBaseline = hasGameBaseline || includeAnime && hasAnimeBaseline
        val gameObservations =
            prefs.getString(KEY_GAME_OBSERVATIONS, "0")?.toIntOrNull() ?: 0
        val animeObservations =
            prefs.getString(KEY_ANIME_OBSERVATIONS, "0")?.toIntOrNull() ?: 0
        return ActivityYearSnapshot(
            stats = stats,
            history = history,
            availableYears = years,
            baselineOnly = hasBaseline &&
                gameObservations < 2 &&
                (!includeAnime || animeObservations < 2)
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
            val amount = entry.optDouble(amountKey, 0.0)
            if (amount > 0.0) records += ItemActivityRecord(date, amount)
        }
        return records.sortedBy { it.date }
    }

    private fun decodeDay(date: String, source: JSONObject?): DailyActivity {
        if (source == null) return DailyActivity(date, 0, 0.0, emptyList())
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
                animeEpisodes = entry.optDouble("anime_episodes", 0.0),
                lastRecordedAt = entry.optLong("last_recorded_at")
            )
        }
        return DailyActivity(
            date = date,
            gameMinutes = source.optInt("game_minutes"),
            animeEpisodes = source.optDouble("anime_episodes", 0.0),
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
        animeEpisodes: Double,
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
            .put(
                "anime_episodes",
                existing.optDouble("anime_episodes", 0.0) + animeEpisodes
            )
            .put("last_recorded_at", recordedAt)
        entries.put(entryKey, existing)
        day
            .put("game_minutes", day.optInt("game_minutes") + gameMinutes)
            .put(
                "anime_episodes",
                day.optDouble("anime_episodes", 0.0) + animeEpisodes
            )
            .put("entries", entries)
        days.put(date, day)
    }

    private fun animeBaseline(
        episodes: Int,
        apiType: Int,
        episodeIds: Collection<Int>
    ): JSONObject {
        val ids = JSONArray()
        episodeIds.distinct().sorted().forEach(ids::put)
        return JSONObject()
            .put("episodes", episodes.coerceAtLeast(0))
            .put("type", apiType)
            .put("episode_ids", ids)
    }

    private fun previousEpisodeIds(source: JSONObject): Set<Int>? {
        if (!source.has("episode_ids")) return null
        val ids = source.optJSONArray("episode_ids") ?: return emptySet()
        return buildSet {
            for (index in 0 until ids.length()) {
                val id = ids.optInt(index)
                if (id > 0) add(id)
            }
        }
    }

    private fun reconcileLegacyAnimeDates(
        days: JSONObject,
        item: BangumiCollection,
        watchedEpisodes: List<BangumiUserEpisodeCollection>,
        observedAt: Long
    ) {
        if (hasObsidianAnimeRecords(days, item.subject_id)) return
        val existingAmounts = animeRecordAmounts(days, item.subject_id)
        val recordedTotal = existingAmounts.values.sum()
        val recordedTotalInt = recordedTotal.toInt()
        if (
            recordedTotal <= 0.0 ||
            abs(recordedTotal - recordedTotalInt) > 0.000001 ||
            recordedTotalInt > watchedEpisodes.size
        ) return

        val timestampedEpisodes = watchedEpisodes.mapNotNull { episode ->
            bangumiEpisodeUpdatedAtOrNull(episode.updated_at, observedAt)?.let {
                episode to it
            }
        }.sortedByDescending { it.second }
        if (timestampedEpisodes.size < recordedTotalInt) return

        val recordedEpisodes = timestampedEpisodes.take(recordedTotalInt)
        val expectedAmounts = recordedEpisodes
            .groupingBy { localDate(it.second) }
            .eachCount()
            .mapValues { (_, amount) -> amount.toDouble() }
        if (expectedAmounts == existingAmounts) return

        removeAnimeRecords(days, item.subject_id)
        val subject = item.subject
        recordedEpisodes.sortedBy { it.second }.forEach { (_, recordedAt) ->
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
                animeEpisodes = 1.0,
                recordedAt = recordedAt
            )
        }
    }

    private fun animeRecordAmounts(days: JSONObject, subjectId: Int): Map<String, Double> {
        val entryKey = "${ActivityKind.ANIME.name.lowercase(Locale.US)}:$subjectId"
        return buildMap {
            val dates = days.keys()
            while (dates.hasNext()) {
                val date = dates.next()
                val amount = days.optJSONObject(date)
                    ?.optJSONObject("entries")
                    ?.optJSONObject(entryKey)
                    ?.optDouble("anime_episodes", 0.0)
                    ?: 0.0
                if (amount > 0.0) put(date, amount)
            }
        }
    }

    private fun hasObsidianAnimeRecords(days: JSONObject, subjectId: Int): Boolean {
        val entryKey = "${ActivityKind.ANIME.name.lowercase(Locale.US)}:$subjectId"
        val dates = days.keys()
        while (dates.hasNext()) {
            val entry = days.optJSONObject(dates.next())
                ?.optJSONObject("entries")
                ?.optJSONObject(entryKey)
                ?: continue
            if (entry.optString("source") == "obsidian") return true
        }
        return false
    }

    private fun removeAnimeRecords(days: JSONObject, subjectId: Int) {
        val entryKey = "${ActivityKind.ANIME.name.lowercase(Locale.US)}:$subjectId"
        val dates = buildList {
            val keys = days.keys()
            while (keys.hasNext()) add(keys.next())
        }
        dates.forEach { date ->
            val day = days.optJSONObject(date) ?: return@forEach
            val entries = day.optJSONObject("entries") ?: return@forEach
            val entry = entries.optJSONObject(entryKey) ?: return@forEach
            val episodes = entry.optDouble("anime_episodes", 0.0)
            entries.remove(entryKey)
            day
                .put(
                    "anime_episodes",
                    (day.optDouble("anime_episodes", 0.0) - episodes).coerceAtLeast(0.0)
                )
                .put("entries", entries)
            if (
                day.optInt("game_minutes") == 0 &&
                day.optDouble("anime_episodes", 0.0) == 0.0 &&
                entries.length() == 0
            ) {
                days.remove(date)
            } else {
                days.put(date, day)
            }
        }
    }

    private fun bangumiEpisodeUpdatedAt(updatedAt: Long?, observedAt: Long): Long {
        return bangumiEpisodeUpdatedAtOrNull(updatedAt, observedAt) ?: observedAt
    }

    private fun bangumiEpisodeUpdatedAtOrNull(
        updatedAt: Long?,
        observedAt: Long
    ): Long? {
        if (updatedAt == null || updatedAt <= 0L) return null
        val timestamp = if (updatedAt < 10_000_000_000L) {
            updatedAt * 1_000L
        } else {
            updatedAt
        }
        return timestamp.takeIf {
            it <= observedAt + 5 * 60 * 1_000L
        }
    }

    private fun parseObject(value: String?): JSONObject =
        runCatching { JSONObject(value ?: "{}") }.getOrElse { JSONObject() }

    private fun steamPlayedAt(game: GameInfo, observedAt: Long): Long? {
        if (game.rtime_last_played <= 0L) return null
        val timestamp = game.rtime_last_played * 1_000L
        return timestamp.takeIf { it <= observedAt + 5 * 60 * 1_000L }
    }

    /**
     * v2.0 及更早版本把 Steam 延迟返回的增量记在检测当天。
     * 仅当当天条目时间确实也是检测当天时，将它移到 Steam 最后游玩日期。
     */
    private fun moveLegacyDetectedActivity(
        days: JSONObject,
        game: GameInfo,
        observedAt: Long,
        playedAt: Long
    ) {
        val detectedDate = localDate(observedAt)
        val playedDate = localDate(playedAt)
        if (detectedDate == playedDate) return

        val sourceDay = days.optJSONObject(detectedDate) ?: return
        val sourceEntries = sourceDay.optJSONObject("entries") ?: return
        val entryKey = "${ActivityKind.GAME.name.lowercase(Locale.US)}:${game.appid}"
        val sourceEntry = sourceEntries.optJSONObject(entryKey) ?: return
        val recordedAt = sourceEntry.optLong("last_recorded_at")
        if (recordedAt <= 0L || localDate(recordedAt) != detectedDate) return

        val minutes = sourceEntry.optInt("game_minutes")
        if (minutes <= 0) return
        val title = sourceEntry.optString("title").ifBlank { game.name }
        val imageUrl = sourceEntry.optString("image_url").ifBlank {
            steamPortraitUrl(game.appid)
        }

        sourceEntries.remove(entryKey)
        sourceDay
            .put("game_minutes", (sourceDay.optInt("game_minutes") - minutes).coerceAtLeast(0))
            .put("entries", sourceEntries)
        if (
            sourceEntries.length() == 0 &&
            sourceDay.optInt("game_minutes") == 0 &&
            sourceDay.optDouble("anime_episodes", 0.0) == 0.0
        ) {
            days.remove(detectedDate)
        } else {
            days.put(detectedDate, sourceDay)
        }

        addActivity(
            days = days,
            kind = ActivityKind.GAME,
            id = game.appid,
            title = title,
            secondaryTitle = "",
            imageUrl = imageUrl,
            gameMinutes = minutes,
            animeEpisodes = 0.0,
            recordedAt = playedAt
        )
    }

    private fun localDate(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))

    private fun dateTimestamp(date: String): Long? {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
        }
        val parsed = runCatching { formatter.parse(date) }.getOrNull() ?: return null
        return parsed.time.takeIf { formatter.format(parsed) == date }
    }

    private fun uiCollectionTypeToApi(type: Int): Int = when (type) {
        2 -> 3
        3 -> 2
        else -> type
    }

    private fun steamPortraitUrl(appId: Int): String =
        "https://shared.cloudflare.steamstatic.com/store_item_assets/steam/apps/$appId/library_600x900_2x.jpg"
}
