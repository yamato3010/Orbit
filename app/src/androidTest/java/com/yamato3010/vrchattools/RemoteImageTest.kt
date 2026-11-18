package com.yamato3010.vrchattools

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import com.yamato3010.vrchattools.data.*
import com.yamato3010.vrchattools.ui.Avatar
import com.yamato3010.vrchattools.ui.LocalOrbitImageLoader
import com.yamato3010.vrchattools.ui.WorldArt
import com.yamato3010.vrchattools.ui.theme.VrchattoolsTheme
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class RemoteImageTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var api: MockWebServer
    private lateinit var cdn: MockWebServer
    private lateinit var loader: ImageLoader
    private lateinit var png: ByteArray

    @Before fun setUp() {
        val cert = HeldCertificate.Builder().commonName("Orbit test images")
            .addSubjectAlternativeName("api.vrchat.cloud").addSubjectAlternativeName("cdn.vrchat.cloud").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        api = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        cdn = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        val auth = Cookie.Builder().name("auth").value("test-image-session").domain("vrchat.cloud").secure().build()
        val session = object : CookieJar {
            override fun loadForRequest(url: HttpUrl) = listOf(auth)
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
        }
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        val client = vrchatClient(session).newBuilder()
            .sslSocketFactory(trust.sslSocketFactory(), trust.trustManager)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> = listOf(InetAddress.getByName("127.0.0.1"))
            }).build()
        loader = ImageLoader.Builder(InstrumentationRegistry.getInstrumentation().targetContext)
            .okHttpClient(client).memoryCache(null).diskCache(null).crossfade(false).build()
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.MAGENTA) }
        png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
    }

    @After fun tearDown() { loader.shutdown(); api.shutdown(); cdn.shutdown() }
    private fun apiUrl(path: String) = api.url(path).newBuilder().host("api.vrchat.cloud").build().toString()
    private fun image() = MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(png))

    @Test fun loadsProtectedImagesFallsBackAndDoesNotSendAuthToCdn() {
        val cdnUrl = cdn.url("/avatar.png").newBuilder().host("cdn.vrchat.cloud").build().toString()
        api.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/missing-icon" -> MockResponse().setResponseCode(404)
                "/avatar" -> MockResponse().setResponseCode(302).setHeader("Location", cdnUrl)
                "/world-thumb" -> image()
                else -> MockResponse().setResponseCode(404)
            }
        }
        cdn.enqueue(image())
        val friend = Friend("test", "Test", Presence.Online, "private", image = apiUrl("/missing-icon"), imageFallbacks = listOf(apiUrl("/avatar")))
        val world = World("wrld_test", "Test World", "Test", 0, image = apiUrl("/world-thumb"))
        compose.setContent {
            CompositionLocalProvider(LocalOrbitImageLoader provides loader) {
                VrchattoolsTheme(dynamicColor = false) {
                    Column { Avatar(friend, 64.dp); WorldArt(world, Modifier.width(240.dp).height(120.dp)) }
                }
            }
        }
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription("Testのアイコン").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithContentDescription("Test Worldのサムネイル").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Testのアイコン").assertIsDisplayed()
        compose.onNodeWithContentDescription("Test Worldのサムネイル").assertIsDisplayed()
        val requests = (1..3).map { api.takeRequest(10, TimeUnit.SECONDS)!! }
        assertEquals(setOf("/missing-icon", "/avatar", "/world-thumb"), requests.map { it.path }.toSet())
        requests.forEach {
            assertEquals("auth=test-image-session", it.getHeader("Cookie"))
            assertEquals(ORBIT_USER_AGENT, it.getHeader("User-Agent"))
        }
        assertNull(cdn.takeRequest(10, TimeUnit.SECONDS)!!.getHeader("Cookie"))
    }

    @Test fun failedImageKeepsPlaceholderAndCanBeRetried() {
        val calls = AtomicInteger()
        api.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest) = if (calls.getAndIncrement() == 0) MockResponse().setResponseCode(503) else image()
        }
        val friend = Friend("retry", "Retry", Presence.Online, "private", image = apiUrl("/retry"))
        compose.setContent {
            CompositionLocalProvider(LocalOrbitImageLoader provides loader) {
                VrchattoolsTheme(dynamicColor = false) { Avatar(friend, 64.dp) }
            }
        }
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("Retryのアイコン を再読み込み").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("R").assertIsDisplayed()
        compose.onNodeWithContentDescription("Retryのアイコン を再読み込み").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("Retryのアイコン").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Retryのアイコン").assertIsDisplayed()
        assertEquals(2, calls.get())
    }
}
