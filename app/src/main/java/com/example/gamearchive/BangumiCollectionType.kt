package com.example.gamearchive

internal fun bangumiCollectionTypeToUi(apiType: Int): Int = when (apiType) {
    2 -> 3
    3 -> 2
    else -> apiType
}

internal fun bangumiCollectionTypeToApi(uiType: Int): Int = when (uiType) {
    2 -> 3
    3 -> 2
    else -> uiType
}
