package com.example.gamearchive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class DetailUiState(
    val details: StoreGameData? = null,
    val reviews: ReviewResponse? = null,
    val isLoading: Boolean = true
)

internal interface DetailDataSource {
    suspend fun details(appId: Int, language: String): StoreGameData?
    suspend fun reviews(appId: Int, language: String): ReviewResponse?
}

private class NetworkDetailDataSource : DetailDataSource {
    override suspend fun details(appId: Int, language: String): StoreGameData? {
        return GameArchiveApp.apiService.getGameDetails(appId, l = language)
            .get(appId.toString())
            ?.data
    }

    override suspend fun reviews(appId: Int, language: String): ReviewResponse {
        return GameArchiveApp.apiService.getGameReviews(appId, l = language, count = 50)
    }
}

class DetailViewModel internal constructor(
    private val dataSource: DetailDataSource = NetworkDetailDataSource()
) : ViewModel() {
    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()
    private var loadedKey: Pair<Int, String>? = null

    fun load(appId: Int, language: String) {
        val key = appId to language
        if (loadedKey == key) return
        loadedKey = key
        if (appId == 0) {
            _uiState.value = DetailUiState(isLoading = false)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            supervisorScope {
                val details = async {
                    runCatchingCancellable { dataSource.details(appId, language) }.getOrNull()
                }
                val reviews = async {
                    runCatchingCancellable { dataSource.reviews(appId, language) }.getOrNull()
                }
                _uiState.value = DetailUiState(
                    details = details.await(),
                    reviews = reviews.await(),
                    isLoading = false
                )
            }
        }
    }
}
