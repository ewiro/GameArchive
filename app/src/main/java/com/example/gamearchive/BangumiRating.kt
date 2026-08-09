package com.example.gamearchive

import androidx.compose.ui.graphics.Color

internal fun normalizeBangumiScore(value: Any?): Double? {
    val score = when (value) {
        is Number -> value.toDouble()
        is Map<*, *> -> normalizeBangumiScore(value["score"])
        else -> null
    }
    return score?.takeIf { it.isFinite() && it in 0.0..10.0 }
}

internal fun bangumiGrade(score: Double): String = when {
    score >= 9.0 -> "超神作"
    score >= 8.0 -> "神作"
    score >= 7.0 -> "力荐"
    score >= 6.0 -> "推荐"
    score >= 5.0 -> "还行"
    score >= 4.0 -> "较差"
    else -> "差"
}

internal fun bangumiScoreColor(score: Double): Color = when {
    score >= 8.0 -> Color(0xFFE53935)
    score >= 7.0 -> Color(0xFFFF9800)
    score >= 5.0 -> Color(0xFF42A5F5)
    else -> Color(0xFF9E9E9E)
}
