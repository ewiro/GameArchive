package com.example.gamearchive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BangumiRatingTest {
    @Test
    fun collectionTypesSwapDoingAndDoneInBothDirections() {
        assertEquals(3, bangumiCollectionTypeToUi(2))
        assertEquals(2, bangumiCollectionTypeToUi(3))
        assertEquals(3, bangumiCollectionTypeToApi(2))
        assertEquals(2, bangumiCollectionTypeToApi(3))
        assertEquals(1, bangumiCollectionTypeToUi(1))
    }

    @Test
    fun normalizesLegacyObjectAndNumericValues() {
        assertEquals(8.4, requireNotNull(normalizeBangumiScore(mapOf("score" to 8.4))), 0.0)
        assertEquals(7.0, requireNotNull(normalizeBangumiScore(7)), 0.0)
    }

    @Test
    fun rejectsMissingInvalidAndOutOfRangeValues() {
        assertNull(normalizeBangumiScore(emptyMap<String, Any>()))
        assertNull(normalizeBangumiScore(Double.NaN))
        assertNull(normalizeBangumiScore(11.0))
    }

    @Test
    fun mapsPersonalRatingsToTheirExactGrade() {
        assertEquals(R.string.bangumi_grade_unbearable, bangumiPersonalGradeRes(1))
        assertEquals(R.string.bangumi_grade_recommended, bangumiPersonalGradeRes(7))
        assertEquals(R.string.bangumi_grade_legendary, bangumiPersonalGradeRes(10))
        assertNull(bangumiPersonalGradeRes(0))
    }
}
