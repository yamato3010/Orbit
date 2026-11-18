package com.yamato3010.vrchattools

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yamato3010.vrchattools.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SocialProtocolTest {
    private val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
    private fun server() = MockWebServer().apply { useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory(), false) }
    private fun OkHttpClient.Builder.trustTestCertificate(): OkHttpClient.Builder {
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        return sslSocketFactory(trust.sslSocketFactory(), trust.trustManager)
    }

    @Test fun parsesDoubleEncodedPartialUpdateWithoutRevealingTravelDestination() {
        val payload = JSONObject().put("userid", "usr_test").put("location", "traveling")
            .put("travelingToLocation", "wrld_secret:123").put("user", JSONObject().put("status", "busy"))
        val event = parsePipelineEvent(JSONObject().put("type", "friend-location").put("content", payload.toString()).toString()) as PipelineEvent.FriendChanged
        assertEquals("usr_test", event.update.id)
        assertEquals("traveling", event.update.location)
        assertNull(event.update.images); assertNull(event.update.rank)
        assertNull(parsePipelineEvent("malformed"))
        assertNull(parsePipelineEvent("{\"type\":\"unknown\"}"))
    }
    @Test fun notificationDetailsMayBeStringOrObjectAndSeenEventsUsePlainIds() {
        val details = JSONObject().put("worldId", "wrld_test:123").put("inviteMessage", "hello")
        val n = JSONObject().put("id", "not_test").put("type", "invite").put("senderUsername", JSONObject.NULL)
            .put("created_at", "2026-11-19T12:34:56.000Z").put("details", details.toString()).toSocialNotification()!!
        assertEquals("hello", n.message); assertEquals("ユーザー", n.senderName); assertTrue(n.createdAt > 0)
        assertEquals(n, JSONObject().put("id", "not_test").put("type", "invite").put("created_at", "2026-11-19T12:34:56Z").put("details", details).toSocialNotification())
        assertEquals(PipelineEvent.NotificationChanged(NotificationChange.Seen("not_test")), parsePipelineEvent("{\"type\":\"see-notification\",\"content\":\"not_test\"}"))
    }
    @Test fun ownProfilePatchDoesNotInventMissingLocationOrDescription() {
        val event = parsePipelineEvent(JSONObject().put("type", "user-update").put("content", JSONObject().put("user", JSONObject().put("status", "ask me"))).toString()) as PipelineEvent.OwnChanged
        assertEquals(SocialStatus.AskMe, event.status); assertNull(event.description); assertNull(event.location)
        assertNull(event.images)
        val user = JSONObject().put("id", "usr_self").put("userIcon", "https://api.vrchat.cloud/icon")
            .put("currentAvatarThumbnailImageUrl", "https://api.vrchat.cloud/avatar")
        assertEquals(listOf("https://api.vrchat.cloud/icon", "https://api.vrchat.cloud/avatar"), user.toOwnProfile().imageUrls)
        val iconEvent = parsePipelineEvent(JSONObject().put("type", "user-update").put("content", JSONObject().put("user", user)).toString()) as PipelineEvent.OwnChanged
        assertEquals(user.toOwnProfile().imageUrls, iconEvent.images)

    }
    @Test fun pipelineEncodesTokenUsesUserAgentAndClosesOnCancellation() = runBlocking {
        val server = server()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send("{\"type\":\"clear-notification\"}") }
        }))
        server.start()
        val client = vrchatClient(object : CookieJar {
            override fun loadForRequest(url: HttpUrl) = emptyList<Cookie>()
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
        }).newBuilder().trustTestCertificate().build()
        try {
            val received = withTimeout(10000) { pipelineEvents(client, "test+&token", server.url("/")).take(2).toList() }
            assertEquals(PipelineSignal.Connected, received[0])
            assertEquals(PipelineSignal.Event(PipelineEvent.NotificationChanged(NotificationChange.Clear)), received[1])
            val request = server.takeRequest(3, TimeUnit.SECONDS)!!
            assertEquals("test+&token", request.requestUrl!!.queryParameter("authToken"))
            assertEquals(ORBIT_USER_AGENT, request.getHeader("User-Agent"))
            assertNull(request.getHeader("Cookie"))
        } finally { client.dispatcher.executorService.shutdown(); server.shutdown() }
    }
    @Test fun socialRestRequestsUseCorrectRoutesAndMinimalBodies() = runBlocking {
        val server = server(); server.start()
        // すべてのリクエストを捕捉し、テスト中の通信や認証情報が VRChat に届かないようにする。
        val client = OkHttpClient.Builder().trustTestCertificate().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().url(server.url(chain.request().url.encodedPath + (chain.request().url.encodedQuery?.let { "?$it" } ?: ""))).build())
        }.build()
        val repository = VrchatRepository(ApplicationProvider.getApplicationContext(), client)
        try {
            server.enqueue(MockResponse().setBody("{\"id\":\"usr_test\",\"status\":\"busy\",\"statusDescription\":\"working\"}"))
            repository.updateStatus("usr_test", SocialStatus.Busy, "working")
            var request = server.takeRequest()!!
            assertEquals("PUT", request.method); assertEquals("/api/1/users/usr_test", request.path)
            val status = JSONObject(request.body.readUtf8()); assertEquals(2, status.length()); assertEquals("busy", status.getString("status"))
            server.enqueue(MockResponse().setBody("{}")); repository.sendInvite("usr_friend", "wrld_test:123~region(jp)", 2)
            request = server.takeRequest()!!; assertEquals("/api/1/invite/usr_friend", request.path)
            assertEquals("wrld_test:123~region(jp)", JSONObject(request.body.readUtf8()).getString("instanceId"))
            server.enqueue(MockResponse().setBody("{}")); repository.requestInvite("usr_friend", 1)
            request = server.takeRequest()!!; assertEquals("/api/1/requestInvite/usr_friend", request.path)
            assertEquals(1, JSONObject(request.body.readUtf8()).getInt("requestSlot"))
            server.enqueue(MockResponse().setBody("{}")); repository.respondInvite("not_test", 3)
            request = server.takeRequest()!!; assertEquals("/api/1/invite/not_test/response", request.path)
            assertEquals(3, JSONObject(request.body.readUtf8()).getInt("responseSlot"))
            server.enqueue(MockResponse().setBody("{}")); repository.notificationAction("frq_test", "accept")
            request = server.takeRequest()!!; assertEquals("PUT", request.method); assertEquals("/api/1/auth/user/notifications/frq_test/accept", request.path)
            server.enqueue(MockResponse().setBody("[{\"id\":\"not_test\",\"type\":\"requestInvite\"}]"))
            assertEquals(NotificationType.RequestInvite, repository.notifications().single().type)
            assertEquals("/api/1/auth/user/notifications?n=100&offset=0", server.takeRequest()!!.path)
        } finally { repository.imageLoader.shutdown(); client.dispatcher.executorService.shutdown(); server.shutdown() }
    }
}
