package com.yamato3010.vrchattools.data

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal const val ORBIT_USER_AGENT = "OrbitVRChatTools/0.1 (Android; personal companion)"

/** 署名付きの画像 URL は CDN にリダイレクトされることがある。セッション Cookie は API ホストにだけ送る。 */
internal class ApiCookieJar(private val session: CookieJar) : CookieJar {
    private fun trusted(url: HttpUrl) = url.isHttps && url.host == "api.vrchat.cloud"
    override fun loadForRequest(url: HttpUrl): List<Cookie> = if (trusted(url)) session.loadForRequest(url) else emptyList()
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (trusted(url)) session.saveFromResponse(url, cookies)
    }
}

internal fun vrchatClient(session: CookieJar): OkHttpClient = OkHttpClient.Builder()
    .cookieJar(ApiCookieJar(session))
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .addInterceptor { chain ->
        chain.proceed(chain.request().newBuilder().header("User-Agent", ORBIT_USER_AGENT).build())
    }
    .build()

/** これらの区切り文字は VRChat のインスタンス ID の構文に含まれる。フォームエンコードすると WAF にブロックされる。 */
internal fun instanceApiUrl(address: InstanceLocation): HttpUrl = HttpUrl.Builder()
    .scheme("https")
    .host("api.vrchat.cloud")
    .addPathSegments("api/1/instances")
    .addPathSegment(address.location)
    .build()
