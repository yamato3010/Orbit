package com.yamato3010.vrchattools

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yamato3010.vrchattools.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResourceDetailsTest {
    @Test fun missingNullAndInvalidValuesNeverBecomeInventedFacts() {
        val fields = JSONObject().put("id", "wrld_test").put("visits", 0).put("favorites", JSONObject.NULL)
            .put("capacity", -1).put("version", "12").put("publicationDate", "none")
            .put("tags", JSONArray(listOf("chill", "chill", JSONObject.NULL, ""))).worldDetailFields()
        assertEquals(listOf(DetailField("ワールドID", "wrld_test"), DetailField("訪問数", "0"), DetailField("タグ", "chill")), fields)
        assertTrue(JSONObject().friendDetailFields().isEmpty())
        assertTrue(JSONObject().instanceDetailFields().isEmpty())
    }
    @Test fun onlySelectedMetadataIsDisplayedIncludingFalseAndZero() {
        val user = JSONObject().put("id", "usr_test").put("bio", "Hello!").put("allowAvatarCopying", false)
            .put("email", "not-for-display").put("authToken", "not-for-display").put("friendKey", "not-for-display")
            .put("travelingToLocation", "wrld_secret:123")
        assertEquals(listOf("ユーザーID", "プロフィール", "アバターのコピーを許可"), user.friendDetailFields().map { it.label })
        assertEquals("いいえ", user.friendDetailFields().last().value)
        val instance = JSONObject().put("active", true).put("full", false).put("secureName", "secret")
            .put("platforms", JSONObject().put("standalonewindows", 0).put("android", 2))
        assertEquals(listOf(DetailField("稼働中", "はい"), DetailField("満員", "いいえ"), DetailField("プラットフォーム別人数", "PC：0 人\nAndroid：2 人")), instance.instanceDetailFields())
    }
    @Test fun dedicatedRequestsFetchFullResourcesAndKeepInstanceMetadata() = runBlocking {
        val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        val client = OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().url(server.url(chain.request().url.encodedPath)).build())
        }.build()
        val repository = VrchatRepository(ApplicationProvider.getApplicationContext<Context>(), client)
        try {
            server.enqueue(MockResponse().setBody("{\"id\":\"usr_test\",\"bio\":\"Profile\"}"))
            assertTrue(repository.friendDetails("usr_test").contains(DetailField("プロフィール", "Profile")))
            assertEquals("/api/1/users/usr_test", server.takeRequest().path)
            server.enqueue(MockResponse().setBody("{\"id\":\"wrld_test\",\"visits\":123}"))
            assertTrue(repository.worldDetails("wrld_test").contains(DetailField("訪問数", "123")))
            assertEquals("/api/1/worlds/wrld_test", server.takeRequest().path)
            server.enqueue(MockResponse().setBody("{\"n_users\":3,\"capacity\":16,\"active\":true}"))
            val instance = repository.instance(InstanceLocation("wrld_test", "123~region(jp)"))!!
            assertEquals(3, instance.occupants)
            assertTrue(instance.extra.contains(DetailField("稼働中", "はい")))
            assertEquals("/api/1/instances/wrld_test:123~region(jp)", server.takeRequest().path)
        } finally { repository.imageLoader.shutdown(); client.dispatcher.executorService.shutdown(); server.shutdown() }
    }
}
