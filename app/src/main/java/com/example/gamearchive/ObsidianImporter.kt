package com.example.gamearchive

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt

data class ObsidianImportResult(
    val gameCount: Int,
    val animeCount: Int,
    val recordCount: Int,
    val tagCount: Int,
    val skippedNames: List<String>,
    val mediaNoteCount: Int
) {
    val skippedCount: Int get() = skippedNames.size
}

private data class ObsidianMediaNote(
    val fileName: String,
    val kind: ActivityKind,
    val names: List<String>,
    val history: Map<String, Double>,
    val tags: List<String>
)

private data class GameImportCandidate(
    val appId: Int,
    val name: String
)

private data class AnimeImportCandidate(
    val id: Int,
    val name: String,
    val nameCn: String,
    val imageUrl: String,
    val aliases: List<String> = emptyList()
)

object ObsidianImporter {

    suspend fun importTree(context: Context, treeUri: Uri): ObsidianImportResult {
        val notes = readMarkdownFiles(context, treeUri)
            .mapNotNull { (fileName, content) -> parseNote(fileName, content) }
        if (notes.isEmpty()) {
            return ObsidianImportResult(0, 0, 0, 0, emptyList(), 0)
        }

        val gameNotes = notes.filter { it.kind == ActivityKind.GAME }
        val animeNotes = notes.filter { it.kind == ActivityKind.ANIME }
        val games = if (gameNotes.isEmpty()) emptyList() else loadSteamGames(context)
        val animeCandidates = if (animeNotes.isEmpty()) {
            emptyList()
        } else {
            loadBangumiAnime(context)
        }.toMutableList()
        val customGameNames = GameNames.getAllNames(context)
        val gameCandidates = games.map { game ->
            GameImportCandidate(game.appid, game.name)
        }
        val records = mutableListOf<ActivityImportRecord>()
        var matchedGames = 0
        var matchedAnime = 0
        var importedTags = 0
        val skippedNames = mutableListOf<String>()

        gameNotes.forEach { note ->
            val game = findUniqueGame(note.names, gameCandidates, customGameNames)
            if (game == null) {
                skippedNames += note.fileName
                return@forEach
            }
            matchedGames++
            importedTags += mergeGameTags(context, game.appId, note.tags)
            note.history.forEach { (date, hours) ->
                records += ActivityImportRecord(
                    kind = ActivityKind.GAME,
                    id = game.appId,
                    title = game.name,
                    secondaryTitle = "",
                    imageUrl = steamPortraitUrl(game.appId),
                    date = date,
                    gameMinutes = (hours * 60.0).roundToInt()
                )
            }
        }

        for (note in animeNotes) {
            val legacyMatch = findLegacyAnime(note.names, animeCandidates)
            val anime = findUniqueAnime(note.names, animeCandidates)
                ?: searchBangumiAnime(note.names)?.also { animeCandidates += it }
            if (anime == null) {
                skippedNames += note.fileName
                continue
            }
            if (legacyMatch != null && legacyMatch.id != anime.id) {
                ActivityStats.removeImportedRecords(
                    context = context,
                    kind = ActivityKind.ANIME,
                    id = legacyMatch.id,
                    dates = note.history.keys
                )
            }
            matchedAnime++
            note.history.forEach { (date, episodes) ->
                records += ActivityImportRecord(
                    kind = ActivityKind.ANIME,
                    id = anime.id,
                    title = anime.name,
                    secondaryTitle = anime.nameCn,
                    imageUrl = anime.imageUrl,
                    date = date,
                    animeEpisodes = episodes
                )
            }
        }

        val importedRecords = ActivityStats.importRecords(context, records)
        return ObsidianImportResult(
            gameCount = matchedGames,
            animeCount = matchedAnime,
            recordCount = importedRecords,
            tagCount = importedTags,
            skippedNames = skippedNames,
            mediaNoteCount = notes.size
        )
    }

    private suspend fun loadSteamGames(context: Context): List<GameInfo> = coroutineScope {
        UserPrefs.getAllAccounts(context)
            .map { (steamId, apiKey) ->
                async {
                    runCatching {
                        GameArchiveApp.apiService.getOwnedGames(apiKey, steamId).response.games
                    }.getOrDefault(emptyList())
                }
            }
            .awaitAll()
            .flatten()
            .distinctBy { it.appid }
    }

    private suspend fun loadBangumiAnime(context: Context): List<AnimeImportCandidate> {
        val username = UserPrefs.getBangumiUsername(context)
        if (username.isBlank()) return emptyList()
        val cached = BangumiPageCache.load(context, username)
            ?.collections
            .orEmpty()
            .values
            .flatten()
            .mapNotNull { it.subject?.toImportCandidate() }
        val fetched = coroutineScope {
            (1..5).map { type ->
                async {
                    runCatching {
                        val result = mutableListOf<AnimeImportCandidate>()
                        var offset = 0
                        while (true) {
                            val page = GameArchiveApp.bgmService.getUserCollections(
                                username = username,
                                subjectType = 2,
                                collectionType = type,
                                limit = 50,
                                offset = offset
                            )
                            page.data.orEmpty().mapNotNullTo(result) {
                                it.subject?.toImportCandidate()
                            }
                            if (page.data == null || page.total <= offset + 50) break
                            offset += 50
                        }
                        result
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }
        return (cached + fetched).distinctBy { it.id }
    }

    private suspend fun searchBangumiAnime(names: List<String>): AnimeImportCandidate? {
        val sourceNames = names.filter(String::isNotBlank).distinct()
        for (searchTerm in bangumiSearchTerms(sourceNames)) {
            val results = runCatching {
                GameArchiveApp.bgmService.searchSubjects(
                    payload = BangumiSubjectSearchRequest(
                        keyword = searchTerm,
                        filter = BangumiSubjectSearchFilter(type = listOf(2))
                    ),
                    limit = 10
                ).data.orEmpty()
            }.getOrDefault(emptyList())
            val candidates = results.mapNotNull { subject ->
                val id = subject.id ?: return@mapNotNull null
                AnimeImportCandidate(
                    id = id,
                    name = subject.name.orEmpty(),
                    nameCn = subject.name_cn.orEmpty(),
                    imageUrl = subject.images.importImageUrl(),
                    aliases = subject.infobox.importAliases()
                )
            }.distinctBy { it.id }
            findUniqueAnime(sourceNames, candidates)?.let { return it }
        }
        return null
    }

    private fun BangumiSubject.toImportCandidate(): AnimeImportCandidate =
        AnimeImportCandidate(
            id = id,
            name = name,
            nameCn = name_cn.orEmpty(),
            imageUrl = images.importImageUrl()
        )

    private fun BangumiImages?.importImageUrl(): String =
        this?.large ?: this?.common ?: this?.medium ?: ""

    private fun findUniqueGame(
        names: List<String>,
        candidates: List<GameImportCandidate>,
        customNames: Map<Int, String>
    ): GameImportCandidate? =
        findUniqueGame(names, candidates, customNames, ::matchesNameExactly)
            ?: findUniqueGame(
                names,
                candidates,
                customNames,
                ::matchesGameNameIgnoringEditionSuffix
            )

    private fun findUniqueGame(
        names: List<String>,
        candidates: List<GameImportCandidate>,
        customNames: Map<Int, String>,
        matcher: (String, List<String>) -> Boolean
    ): GameImportCandidate? {
        names.forEach { name ->
            val matches = candidates.filter { candidate ->
                matcher(
                    name,
                    listOf(candidate.name, customNames[candidate.appId].orEmpty())
                )
            }.distinctBy { it.appId }
            if (matches.size == 1) return matches.single()
        }
        return null
    }

    private fun findUniqueAnime(
        names: List<String>,
        candidates: List<AnimeImportCandidate>
    ): AnimeImportCandidate? =
        findUniqueAnime(names, candidates, ::matchesNameExactly)
            ?: findUniqueAnime(names, candidates, ::matchesAnimeNameIgnoringMediaPrefix)

    private fun findUniqueAnime(
        names: List<String>,
        candidates: List<AnimeImportCandidate>,
        matcher: (String, List<String>) -> Boolean
    ): AnimeImportCandidate? {
        names.forEach { name ->
            val matches = candidates.filter { candidate ->
                matcher(
                    name,
                    listOf(candidate.name, candidate.nameCn) + candidate.aliases
                )
            }.distinctBy { it.id }
            if (matches.size == 1) return matches.single()
        }
        return null
    }

    /** 复现首版导入器的匹配结果，仅用于迁移曾经写错条目的本地记录。 */
    private fun findLegacyAnime(
        names: List<String>,
        candidates: List<AnimeImportCandidate>
    ): AnimeImportCandidate? {
        val normalizedNames = names.map(::legacyNormalizeName).filter(String::isNotEmpty).toSet()
        return candidates.filter { candidate ->
            listOf(candidate.name, candidate.nameCn).any {
                legacyNormalizeName(it) in normalizedNames
            }
        }.distinctBy { it.id }.singleOrNull()
    }

    private fun matchesNameExactly(source: String, target: List<String>): Boolean {
        val normalizedSource = normalizeName(source)
        return normalizedSource.isNotEmpty() && target.any {
            normalizeName(it) == normalizedSource
        }
    }

    private fun matchesGameNameIgnoringEditionSuffix(
        source: String,
        target: List<String>
    ): Boolean {
        val normalizedSource = normalizeGameName(source)
        return normalizedSource.isNotEmpty() && target.any {
            normalizeGameName(it) == normalizedSource
        }
    }

    private fun matchesAnimeNameIgnoringMediaPrefix(
        source: String,
        target: List<String>
    ): Boolean {
        val normalizedSource = normalizeAnimeName(source)
        return normalizedSource.isNotEmpty() && target.any {
            normalizeAnimeName(it) == normalizedSource
        }
    }

    private fun bangumiSearchTerms(names: List<String>): List<String> {
        val latinPhrases = names.flatMap { name ->
            LATIN_TITLE_REGEX.findAll(name).map { match ->
                match.value.replace(Regex("[_-]+"), " ").trim()
            }.filter { it.length >= 5 }.toList()
        }
        return (names + latinPhrases).distinctBy(::normalizeName)
    }

    private fun List<BangumiInfoboxItem>?.importAliases(): List<String> =
        this.orEmpty()
            .filter { item ->
                item.key.trim().lowercase(Locale.ROOT) in
                    setOf("别名", "別名", "alias", "aliases")
            }
            .flatMap { item -> importAliasValues(item.value) }
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()

    private fun importAliasValues(value: Any?): List<String> = when (value) {
        is String -> listOf(value)
        is Iterable<*> -> value.flatMap(::importAliasValues)
        is Map<*, *> -> value.values.flatMap(::importAliasValues)
        else -> emptyList()
    }

    private fun normalizeName(value: String): String {
        val expanded = Normalizer.normalize(
            value.replace(Regex("[™®©]"), ""),
            Normalizer.Form.NFKC
        )
            .replace(
                Regex("""s[\W_]*a[\W_]*c(?![\p{L}])""", RegexOption.IGNORE_CASE),
                "standalonecomplex"
            )
            .lowercase(Locale.ROOT)
            .filter(Char::isLetterOrDigit)
        return expanded.map { character ->
            when (character) {
                '〇', '零' -> '0'
                '一' -> '1'
                '二' -> '2'
                '三' -> '3'
                '四' -> '4'
                '五' -> '5'
                '六' -> '6'
                '七' -> '7'
                '八' -> '8'
                '九' -> '9'
                else -> character
            }
        }.joinToString("")
    }

    private fun normalizeGameName(value: String): String {
        var normalized = normalizeName(value)
        GAME_EDITION_SUFFIXES.firstOrNull(normalized::endsWith)?.let { suffix ->
            normalized = normalized.removeSuffix(suffix)
        }
        return normalized
    }

    private fun normalizeAnimeName(value: String): String {
        var normalized = normalizeName(value)
        ANIME_MEDIA_PREFIXES.firstOrNull(normalized::startsWith)?.let { prefix ->
            normalized = normalized.removePrefix(prefix)
        }
        return normalized
    }

    private fun legacyNormalizeName(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .filter(Char::isLetterOrDigit)

    private fun mergeGameTags(context: Context, appId: Int, imported: List<String>): Int {
        val tagLibrary = GameTags.getAllTags(context).toMutableList()
        val gameTags = GameTags.getTagsForGame(context, appId).toMutableList()
        var added = 0
        imported.forEach { rawTag ->
            val tag = rawTag.trim()
            if (tag.isBlank() || tag.equals("游戏记录", ignoreCase = true)) return@forEach
            val canonical = tagLibrary.firstOrNull { it.equals(tag, ignoreCase = true) } ?: tag
            if (tagLibrary.none { it.equals(canonical, ignoreCase = true) }) {
                GameTags.addTag(context, canonical)
                tagLibrary += canonical
            }
            if (gameTags.none { it.equals(canonical, ignoreCase = true) }) {
                gameTags += canonical
                added++
            }
        }
        if (added > 0) GameTags.setTagsForGame(context, appId, gameTags)
        return added
    }

    private fun parseNote(fileName: String, content: String): ObsidianMediaNote? {
        val lines = content.lineSequence().toList()
        if (lines.firstOrNull()?.trim() != "---") return null
        val values = mutableMapOf<String, String>()
        val history = linkedMapOf<String, Double>()
        val tags = mutableListOf<String>()
        var section = ""

        for (line in lines.drop(1)) {
            if (line.trim() == "---") break
            if (line.isNotBlank() && !line.first().isWhitespace()) {
                val separator = line.indexOf(':')
                if (separator <= 0) continue
                section = line.substring(0, separator).trim()
                values[section] = unquote(line.substring(separator + 1).trim())
                continue
            }
            when (section) {
                "历史记录" -> {
                    val trimmed = line.trim()
                    val separator = trimmed.indexOf(':')
                    if (separator <= 0) continue
                    val date = trimmed.substring(0, separator).trim()
                    val amount = trimmed.substring(separator + 1).trim().toDoubleOrNull()
                    if (DATE_REGEX.matches(date) && amount != null && amount > 0.0) {
                        history[date] = amount
                    }
                }
                "tags" -> {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("-")) {
                        unquote(trimmed.removePrefix("-").trim())
                            .takeIf(String::isNotBlank)
                            ?.let(tags::add)
                    }
                }
            }
        }

        val kind = when {
            tags.any { it.equals("游戏记录", ignoreCase = true) } -> ActivityKind.GAME
            tags.any { it.equals("动漫记录", ignoreCase = true) } -> ActivityKind.ANIME
            else -> return null
        }
        val names = listOf(
            fileName.removeSuffix(".md"),
            values["中文名"].orEmpty(),
            values["英文名"].orEmpty(),
            values["外文名"].orEmpty()
        ).filter(String::isNotBlank).distinct()
        return ObsidianMediaNote(fileName, kind, names, history, tags)
    }

    private fun unquote(value: String): String {
        if (value.length < 2) return value
        val quoted = value.first() == value.last() && value.first() in setOf('\'', '"')
        return if (quoted) value.substring(1, value.lastIndex) else value
    }

    private fun readMarkdownFiles(context: Context, treeUri: Uri): List<Pair<String, String>> {
        val resolver = context.contentResolver
        val result = mutableListOf<Pair<String, String>>()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)

        fun visit(directoryId: String) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                directoryId
            )
            val children = mutableListOf<Triple<String, String, String>>()
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID
                )
                val nameIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                )
                val mimeIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                )
                while (cursor.moveToNext()) {
                    children += Triple(
                        cursor.getString(idIndex),
                        cursor.getString(nameIndex),
                        cursor.getString(mimeIndex)
                    )
                }
            }
            children.forEach { (documentId, name, mimeType) ->
                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    visit(documentId)
                } else if (name.endsWith(".md", ignoreCase = true)) {
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        documentId
                    )
                    val content = resolver.openInputStream(documentUri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: return@forEach
                    result += name to content
                }
            }
        }

        visit(rootId)
        return result
    }

    private fun steamPortraitUrl(appId: Int): String =
        "https://shared.cloudflare.steamstatic.com/store_item_assets/steam/apps/" +
            "$appId/library_600x900_2x.jpg"

    private val DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")
    private val LATIN_TITLE_REGEX =
        Regex("""[A-Za-z][A-Za-z0-9'’]*(?:[\s_-]+[A-Za-z][A-Za-z0-9'’]*)+""")
    private val GAME_EDITION_SUFFIXES = listOf(
        "gameoftheyearedition",
        "gameoftheyear",
        "gotyedition",
        "goty",
        "年度版"
    )
    private val ANIME_MEDIA_PREFIXES = listOf("劇場版", "剧场版", "映画", "電影", "电影")
}
