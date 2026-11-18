package com.yamato3010.vrchattools

import com.yamato3010.vrchattools.data.ApiCookieJar
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class ApiCookieJarTest {
    private val auth = Cookie.Builder().name("auth").value("test-session").domain("vrchat.cloud").secure().build()
    private var saved = 0
    private val delegate = object : CookieJar {
        override fun loadForRequest(url: HttpUrl) = listOf(auth)
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { saved++ }
    }
    @Test fun protectedImagesReceiveApiSessionButRedirectDestinationsDoNot() {
        val jar = ApiCookieJar(delegate)
        assertEquals(listOf(auth), jar.loadForRequest("https://api.vrchat.cloud/api/1/file/a/1/file".toHttpUrl()))
        assertTrue(jar.loadForRequest("https://cdn.vrchat.cloud/image".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://api.vrchat.cloud.example.com/image".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://example.com/image".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("http://api.vrchat.cloud/image".toHttpUrl()).isEmpty())
    }
    @Test fun onlyApiResponsesCanUpdateTheSession() {
        val jar = ApiCookieJar(delegate)
        jar.saveFromResponse("https://cdn.vrchat.cloud/image".toHttpUrl(), listOf(auth))
        assertEquals(0, saved)
        jar.saveFromResponse("https://api.vrchat.cloud/api/1/auth/user".toHttpUrl(), listOf(auth))
        assertEquals(1, saved)
    }
}
