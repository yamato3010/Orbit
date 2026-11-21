package com.yamato3010.vrchattools.data

data class WorldFavoriteFolder(val id: String, val name: String, val worldIds: Set<String>)

internal fun defaultWorldFolder(worldIds: Set<String> = emptySet()) =
    WorldFavoriteFolder("default", "未分類", worldIds)
