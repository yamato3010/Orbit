package com.yamato3010.vrchattools

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yamato3010.vrchattools.data.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class ApiCachingTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: VrchatRepository
    private lateinit var client: OkHttpClient
    private val now = AtomicLong(0)
    @Before fun setup() {
        val cert = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val tls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        server = MockWebServer().apply { useHttps(tls.sslSocketFactory(), false); start() }
        client = OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).addInterceptor { chain ->
            val original = chain.request().url
            chain.proceed(chain.request().newBuilder().url(server.url(original.encodedPath + (original.encodedQuery?.let { "?$it" } ?: ""))).build())
        }.build()
        repository = VrchatRepository(ApplicationProvider.getApplicationContext<Context>(), client, now::get)
    }
    @After fun teardown() { repository.close(); client.dispatcher.executorService.shutdown(); server.shutdown() }
    private fun json(body: String) = MockResponse().setBody(body)

    @Test fun profileAndExpandedDetailsShareOneHttpResponseEvenConcurrently() = runBlocking {
        server.enqueue(json("""{"id":"usr_test","pronouns":"they/them","bio":"Hello","tags":["system_trust_veteran"]}""").setBodyDelay(50, TimeUnit.MILLISECONDS))
        val profile = async(start = CoroutineStart.UNDISPATCHED) { repository.friendProfile("usr_test") }
        val details = async(start = CoroutineStart.UNDISPATCHED) { repository.friendDetails("usr_test") }
        assertEquals("they/them", profile.await().pronouns)
        assertTrue(details.await().contains(DetailField("プロフィール", "Hello")))
        assertEquals(1, server.requestCount)
        server.enqueue(json("""{"id":"usr_test","pronouns":"she/her","tags":[]}"""))
        repository.friendDetails("usr_test", force = true)
        assertEquals("she/her", repository.friendProfile("usr_test").pronouns)
        assertEquals(2, server.requestCount)
    }
    @Test fun worldMetadataAndSearchesAreReusedUntilExpiration() = runBlocking {
        server.enqueue(json("""{"id":"wrld_test","name":"World","visits":123}"""))
        assertEquals("World", repository.world("wrld_test").name)
        assertTrue(repository.worldDetails("wrld_test").contains(DetailField("訪問数", "123")))
        assertEquals(1, server.requestCount)
        server.enqueue(json("[]")); repository.worlds(" cafe "); repository.worlds("cafe")
        assertEquals(2, server.requestCount)
        now.set(CachePolicy.SEARCH + 1)
        server.enqueue(json("[]")); repository.worlds("cafe")
        assertEquals(3, server.requestCount)
        repository.world("wrld_test")
        assertEquals(3, server.requestCount)
        now.set(CachePolicy.WORLD + 1)
        server.enqueue(json("""{"id":"wrld_test","name":"New"}"""))
        assertEquals("New", repository.world("wrld_test").name)
        assertEquals(4, server.requestCount)
    }
    @Test fun focusedRoomsUseShorterFreshnessAndMovementInvalidatesOnlyThatRoom() = runBlocking {
        val a = InstanceLocation("wrld_a", "123"); val b = InstanceLocation("wrld_b", "456")
        server.enqueue(json("""{"n_users":1}""")); server.enqueue(json("""{"n_users":9}"""))
        repository.instance(a); repository.instance(b)
        now.set(31_000)
        assertEquals(1, repository.instance(a)!!.occupants)
        assertEquals(2, server.requestCount)
        server.enqueue(json("""{"n_users":2}"""))
        assertEquals(2, repository.instance(a, maxAgeMs = CachePolicy.INSTANCE_FOCUSED)!!.occupants)
        repository.invalidateInstance(a.key)
        server.enqueue(json("""{"n_users":3}"""))
        assertEquals(3, repository.instance(a)!!.occupants)
        assertEquals(9, repository.instance(b)!!.occupants)
        assertEquals(4, server.requestCount)
    }
    @Test fun missingResourcesAreTemporarilyCachedAndClearedWithTheAccount() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        repeat(2) { try { repository.world("wrld_missing") } catch (e: ApiException) { assertEquals(404, e.code) } }
        assertEquals(1, server.requestCount)
        server.enqueue(json("""{"id":"wrld_missing","name":"Restored"}"""))
        assertEquals("Restored", repository.world("wrld_missing", force = true).name)
        repository.clearResourceCache()
        server.enqueue(json("""{"id":"wrld_missing","name":"Other account"}"""))
        assertEquals("Other account", repository.world("wrld_missing").name)
        assertEquals(3, server.requestCount)
    }
    @Test fun rateLimitedReadsWaitAndCurrentLocationNeverComesFromTheMetadataCache() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120"))
        try { repository.world("wrld_test") } catch (e: ApiException) { assertEquals(429, e.code) }
        try { repository.currentUser() } catch (e: ApiException) { assertEquals(429, e.code) }
        try { repository.world("wrld_test", force = true) } catch (e: ApiException) { assertEquals(429, e.code) }
        assertEquals(1, server.requestCount)
        now.set(120_001)
        server.enqueue(json("""{"id":"usr_test","location":"wrld_one:123"}"""))
        server.enqueue(json("""{"id":"usr_test","location":"wrld_two:456"}"""))
        assertEquals("wrld_one:123", repository.currentUser().getString("location"))
        assertEquals("wrld_two:456", repository.currentUser().getString("location"))
        assertEquals(3, server.requestCount)
    }
}
