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

internal fun bangumiPersonalGradeRes(rating: Int): Int? = when (rating) {
    10 -> R.string.bangumi_grade_legendary
    9 -> R.string.bangumi_grade_masterpiece
    8 -> R.string.bangumi_grade_highly_recommended
    7 -> R.string.bangumi_grade_recommended
    6 -> R.string.bangumi_grade_decent
    5 -> R.string.bangumi_grade_not_recommended
    4 -> R.string.bangumi_grade_poor
    3 -> R.string.bangumi_grade_bad
    2 -> R.string.bangumi_grade_very_bad
    1 -> R.string.bangumi_grade_unbearable
    else -> null
}
