package com.example.gamearchive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class BangumiDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val subjectId = intent.getIntExtra("SUBJECT_ID", 0)
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
                    onBack = { finish() }
                )
            }
        }
    }
}
