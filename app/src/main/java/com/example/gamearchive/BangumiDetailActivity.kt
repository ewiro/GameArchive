package com.example.gamearchive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.painter.Painter

internal const val EXTRA_BANGUMI_COVER_TRANSITION = "BANGUMI_COVER_TRANSITION"

internal object BangumiCoverTransitionStore {
    private data class Entry(
        val subjectId: Int,
        val painter: Painter
    )

    private var entry: Entry? = null

    @Synchronized
    fun put(subjectId: Int, painter: Painter) {
        entry = Entry(subjectId, painter)
    }

    @Synchronized
    fun take(subjectId: Int): Painter? {
        val current = entry
        entry = null
        return current?.takeIf { it.subjectId == subjectId }?.painter
    }
}

class BangumiDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (intent.getBooleanExtra(EXTRA_BANGUMI_COVER_TRANSITION, false)) {
            setTheme(R.style.Theme_SteamTracker_BangumiTransition)
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val subjectId = intent.getIntExtra("SUBJECT_ID", 0)
        val subjectName = intent.getStringExtra("SUBJECT_NAME") ?: ""
        val subjectNameCn = intent.getStringExtra("SUBJECT_NAME_CN") ?: ""
        val subjectImage = intent.getStringExtra("SUBJECT_IMAGE") ?: ""
        val transitionCoverPainter = BangumiCoverTransitionStore.take(subjectId)

        setContent {
            MiuixThemeForApp {
                BangumiDetailScreen(
                    subjectId = subjectId,
                    subjectName = subjectName,
                    subjectNameCn = subjectNameCn,
                    subjectImage = subjectImage,
                    initialCoverPainter = transitionCoverPainter,
                    onBack = { finish() }
                )
            }
        }
    }
}
