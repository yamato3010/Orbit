package com.yamato3010.vrchattools.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** 未設定（null を含む）の値や無効な URL を除外してから、優先する画像を選ぶ。 */
internal fun imageUrls(vararg values: String?): List<String> = values.mapNotNull { raw ->
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
    val absolute = if (value.startsWith("/api/1/")) "https://api.vrchat.cloud$value" else value
    val url = absolute.toHttpUrlOrNull() ?: return@mapNotNull null
    if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) null
    else url.newBuilder().fragment(null).build().toString()
}.distinct()
