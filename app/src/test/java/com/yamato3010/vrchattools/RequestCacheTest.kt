package com.yamato3010.vrchattools

import com.yamato3010.vrchattools.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class RequestCacheTest {
    @Test fun concurrentReadersShareWorkEvenWhenTheFirstScreenCloses() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val cache = RequestCache<String>(scope)
            val started = CompletableDeferred<Unit>(); val result = CompletableDeferred<String>()
            val calls = AtomicInteger()
            val first = async(start = CoroutineStart.UNDISPATCHED) { cache.get("user", 1000) { calls.incrementAndGet(); started.complete(Unit); result.await() } }
            started.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) { cache.get("user", 1000) { calls.incrementAndGet(); "duplicate" } }
            first.cancelAndJoin(); result.complete("profile")
            assertEquals("profile", second.await()); assertEquals(1, calls.get())
            assertEquals("profile", cache.get("user", 1000) { error("unexpected request") })
        } finally { scope.cancel() }
    }
    @Test fun callerFreshnessForceAndLruLimitAreHonored() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            var now = 0L; var calls = 0
            val cache = RequestCache<Int>(scope, { now }, maxEntries = 2)
            suspend fun get(key: String, ttl: Long = 1000, force: Boolean = false) = cache.get(key, ttl, force) { ++calls }
            assertEquals(1, get("room")); now = 500
            assertEquals(1, get("room"))
            assertEquals(2, get("room", ttl = 300)) // 詳細を表示している画面では、より新しいスナップショットを使う。
            assertEquals(3, get("room", force = true))
            get("world"); get("user") // 最も古いルームのキャッシュ項目を削除する。
            assertEquals(6, get("room"))
        } finally { scope.cancel() }
    }
    @Test fun invalidationPreventsOldWorkFromOverwritingTheNewSnapshot() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val cache = RequestCache<String>(scope)
            val start = CompletableDeferred<Unit>(); val old = CompletableDeferred<String>()
            val first = async { cache.get("room", 1000) { start.complete(Unit); old.await() } }
            start.await(); cache.invalidate("room")
            assertEquals("new", cache.get("room", 1000) { "new" })
            old.complete("old"); assertEquals("old", first.await())
            assertEquals("new", cache.get("room", 1000) { error("old result replaced the cache") })
            cache.clear()
            assertEquals("other account", cache.get("room", 1000) { "other account" })
        } finally { scope.cancel() }
    }
    @Test fun accountChangeCancelsPendingReadsAndDropsEveryOldValue() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val cache = RequestCache<String>(scope)
            val started = CompletableDeferred<Unit>(); val old = CompletableDeferred<String>()
            val waiting = async { cache.get("user", 1000) { started.complete(Unit); old.await() } }
            started.await(); cache.clear()
            try { waiting.await(); fail("old account read survived") } catch (_: CancellationException) { }
            assertEquals("new account", cache.get("user", 1000) { "new account" })
            old.complete("old account")
            assertEquals("new account", cache.get("user", 1000) { error("cache was polluted") })
        } finally { scope.cancel() }
    }
    @Test fun onlySelectedFailuresAreCachedAndForceCanRetry() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            var now = 0L; var calls = 0
            val cache = RequestCache<String>(scope, { now }, failureTtl = { if (it is ApiException && it.code == 404) 50 else 0 })
            repeat(2) { try { cache.get("gone", 1000) { calls++; throw ApiException(404, "gone") } } catch (_: ApiException) {} }
            assertEquals(1, calls)
            assertEquals("retried", cache.get("gone", 1000, force = true) { calls++; "retried" })
            repeat(2) { try { cache.get("network", 1000) { calls++; throw IOException("offline") } } catch (_: IOException) {} }
            assertEquals(4, calls)
            now = 2000
            assertEquals("expired", cache.get("gone", 1000) { "expired" })
        } finally { scope.cancel() }
    }
    @Test fun rateLimitStopsReadsUntilTheServerWaitExpires() {
        var now = 0L
        val gate = ReadBackoff { now }
        gate.check(); gate.record(ApiException(429, "wait"), 600_000)
        now = 300_000
        assertEquals(429, runCatching { gate.check() }.exceptionOrNull().let { it as ApiException }.code)
        now = 600_000; gate.check()
        gate.record(ApiException(429, "wait"), 60_000); gate.clear(); gate.check()
    }
}
