package com.example.gamearchive

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsViewModelTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun refreshesAccountStateAtomically() = runBlocking {
        val source = FakeSettingsDataSource(namesChanged = true, nickname = "Tester")
        val viewModel = SettingsViewModel(application, source)

        viewModel.refreshSteamNames()
        val namesState = withTimeout(5_000) {
            viewModel.uiState.first { it.steamNamesRevision == 1 }
        }
        viewModel.refreshBangumiProfile(12)
        val profileState = withTimeout(5_000) {
            viewModel.uiState.first { it.bangumiNickname == "Tester" }
        }

        assertEquals(1, namesState.steamNamesRevision)
        assertEquals("Tester", profileState.bangumiNickname)
    }

    @Test
    fun reportsAvailableVersion() = runBlocking {
        val viewModel = SettingsViewModel(
            application,
            FakeSettingsDataSource(latestVersion = "999.0")
        )

        viewModel.checkForUpdate()
        val state = withTimeout(5_000) {
            viewModel.uiState.first {
                it.updateCheckStatus == UpdateCheckStatus.Available
            }
        }

        assertEquals(UpdateCheckStatus.Available, state.updateCheckStatus)
    }

    private class FakeSettingsDataSource(
        private val namesChanged: Boolean = false,
        private val nickname: String? = null,
        private val latestVersion: String? = null
    ) : SettingsDataSource {
        override suspend fun hydrateMissingSteamNames() = namesChanged
        override suspend fun refreshBangumiNickname(userId: Int) = nickname
        override suspend fun latestVersion() = latestVersion
    }
}
