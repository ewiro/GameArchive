package com.example.gamearchive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap

internal data class BangumiCoverTransitionData(
    val imageBitmap: ImageBitmap,
    val sourceRect: Rect
)

internal object BangumiCoverTransitionStore {
    private data class Entry(
        val subjectId: Int,
        val imageBitmap: ImageBitmap,
        val sourceRect: Rect
    )

    private var entry: Entry? = null

    @Synchronized
    fun begin(subjectId: Int, imageBitmap: ImageBitmap, sourceRect: Rect) {
        entry = Entry(subjectId, imageBitmap, sourceRect)
    }

    @Synchronized
    fun take(subjectId: Int): BangumiCoverTransitionData? {
        val current = entry?.takeIf { it.subjectId == subjectId } ?: return null
        entry = null
        return BangumiCoverTransitionData(current.imageBitmap, current.sourceRect)
    }

    @Synchronized
    fun clear(subjectId: Int) {
        if (entry?.subjectId == subjectId) entry = null
    }
}

open class BangumiDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val subjectId = intent.getIntExtra("SUBJECT_ID", 0)
        val coverTransition = if (this is BangumiCoverTransitionActivity) {
            BangumiCoverTransitionStore.take(subjectId)
        } else null
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val subjectName = intent.getStringExtra("SUBJECT_NAME") ?: ""
        val subjectNameCn = intent.getStringExtra("SUBJECT_NAME_CN") ?: ""
        val subjectImage = intent.getStringExtra("SUBJECT_IMAGE") ?: ""

        setContent {
            MiuixThemeForApp {
                BangumiDetailScreen(
                    subjectId = subjectId,
                    subjectName = subjectName,
                    subjectNameCn = subjectNameCn,
                    subjectImage = subjectImage,
                    initialCoverImage = coverTransition?.imageBitmap,
                    transitionSourceRect = coverTransition?.sourceRect,
                    onBack = { finish() }
                )
            }
        }
    }
}

class BangumiCoverTransitionActivity : BangumiDetailActivity()
