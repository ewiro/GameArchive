package com.example.gamearchive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONObject

enum class UpdateCheckStatus {
    Idle,
    Checking,
    Latest,
    Available,
    Failed
}

data class SettingsUiState(
    val steamNamesRevision: Int = 0,
    val bangumiNickname: String = "",
    val updateCheckStatus: UpdateCheckStatus = UpdateCheckStatus.Idle
)

internal interface SettingsDataSource {
    suspend fun hydrateMissingSteamNames(): Boolean
    suspend fun refreshBangumiNickname(userId: Int): String?
    suspend fun latestVersion(): String?
}

private class DefaultSettingsDataSource(
    private val application: Application
) : SettingsDataSource {
    override suspend fun hydrateMissingSteamNames(): Boolean {
        var changed = false
        UserPrefs.getAllAccounts(application).forEach { (steamId, apiKey) ->
            if (UserPrefs.getStoredSteamNickname(application, steamId).isBlank()) {
                val nickname = runCatchingCancellable {
                    GameArchiveApp.apiService
                        .getPlayerSummaries(apiKey, steamId)
                        .response.players
                        .firstOrNull()
                        ?.personaname
                        .orEmpty()
                }.getOrDefault("")
                if (nickname.isNotBlank()) {
                    UserPrefs.saveSteamNickname(application, steamId, nickname)
                    changed = true
                }
            }
        }
        return changed
    }

    override suspend fun refreshBangumiNickname(userId: Int): String? {
        if (userId <= 0 || UserPrefs.getBangumiAccessToken(application).isBlank()) return null
        val currentUser = BangumiAuthSession.execute(application) { it.getCurrentUser() }
        UserPrefs.setBangumiUsername(application, currentUser.username)
        return currentUser.nickname.ifBlank { currentUser.username }.also {
            UserPrefs.setBangumiNickname(application, it)
        }
    }

    override suspend fun latestVersion(): String? {
        val redirectRequest = Request.Builder()
            .url(RELEASES_LATEST_URL)
            .header("User-Agent", "GameArchive")
            .build()
        val redirectTag = runCatchingCancellable {
            GameArchiveApp.okHttpClient.newCall(redirectRequest).awaitResponse().use { response ->
                response.takeIf { it.isSuccessful }
                    ?.request?.url?.pathSegments?.lastOrNull()
                    ?.takeIf { it.startsWith("v") }
                    ?.removePrefix("v")
            }
        }.getOrNull()
        if (!redirectTag.isNullOrBlank()) return redirectTag

        val apiRequest = Request.Builder()
            .url(RELEASES_API_URL)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "GameArchive")
            .build()
        return runCatchingCancellable {
            GameArchiveApp.okHttpClient.newCall(apiRequest).awaitResponse().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()?.takeIf { it.isNotBlank() }
                    ?.let(::JSONObject)
                    ?.optString("tag_name", "")
                    ?.removePrefix("v")
                    ?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    private companion object {
        const val RELEASES_LATEST_URL = "https://github.com/ewiro/GameArchive/releases/latest"
        const val RELEASES_API_URL =
            "https://api.github.com/repos/ewiro/GameArchive/releases/latest"
    }
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private var dataSource: SettingsDataSource = DefaultSettingsDataSource(application)
    private val _uiState = MutableStateFlow(
        SettingsUiState(bangumiNickname = UserPrefs.getBangumiNickname(application))
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var steamNamesJob: Job? = null
    private var bangumiProfileJob: Job? = null
    private var updateJob: Job? = null

    internal constructor(application: Application, dataSource: SettingsDataSource) : this(application) {
        this.dataSource = dataSource
    }

    fun refreshSteamNames() {
        steamNamesJob?.cancel()
        steamNamesJob = viewModelScope.launch {
            if (dataSource.hydrateMissingSteamNames()) {
                _uiState.value = _uiState.value.copy(
                    steamNamesRevision = _uiState.value.steamNamesRevision + 1
                )
            }
        }
    }

    fun refreshBangumiProfile(userId: Int) {
        bangumiProfileJob?.cancel()
        if (userId <= 0) {
            clearBangumiProfile()
            return
        }
        bangumiProfileJob = viewModelScope.launch {
            val nickname = runCatchingCancellable {
                dataSource.refreshBangumiNickname(userId)
            }.getOrNull() ?: return@launch
            _uiState.value = _uiState.value.copy(bangumiNickname = nickname)
        }
    }

    fun clearBangumiProfile() {
        bangumiProfileJob?.cancel()
        _uiState.value = _uiState.value.copy(bangumiNickname = "")
    }

    fun checkForUpdate() {
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(updateCheckStatus = UpdateCheckStatus.Checking)
            val latestVersion = runCatchingCancellable { dataSource.latestVersion() }.getOrNull()
            val status = when {
                latestVersion == null -> UpdateCheckStatus.Failed
                latestVersion == BuildConfig.VERSION_NAME -> UpdateCheckStatus.Latest
                else -> UpdateCheckStatus.Available
            }
            _uiState.value = _uiState.value.copy(updateCheckStatus = status)
        }
    }

    fun consumeUpdateCheckResult() {
        if (_uiState.value.updateCheckStatus != UpdateCheckStatus.Checking) {
            _uiState.value = _uiState.value.copy(updateCheckStatus = UpdateCheckStatus.Idle)
        }
    }
}
