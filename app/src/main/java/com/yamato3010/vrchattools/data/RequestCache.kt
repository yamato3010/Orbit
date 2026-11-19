package com.yamato3010.vrchattools.data

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object CachePolicy {
    const val PROFILE = 10 * 60_000L
    const val WORLD = 10 * 60_000L
    const val SEARCH = 2 * 60_000L
    const val INSTANCE = 120_000L
    const val INSTANCE_FOCUSED = 30_000L
    const val FAILURE = 30_000L
    const val CONNECTED_SYNC = 10 * 60_000L
    const val DISCONNECTED_SYNC = 2 * 60_000L
}

/** 取得結果と処理中のリクエストを共有する。画面側でキャンセルしても、同じ HTTP リクエストは重複して送信しない。 */
internal class RequestCache<T>(
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val maxEntries: Int = 128,
    private val failureTtl: (Throwable) -> Long = { 0 }
) {
    private data class Entry<T>(val result: Result<T>, val time: Long, val failureTtl: Long)
    private data class Flight<T>(val revision: Long, val result: Deferred<T>)
    private val lock = Mutex()
    private val entries = LinkedHashMap<String, Entry<T>>(16, .75f, true)
    private val flights = mutableMapOf<String, Flight<T>>()
    private var revision = 0L

    suspend fun get(key: String, ttl: Long, force: Boolean = false, load: suspend () -> T): T {
        val work = lock.withLock {
            val old = entries[key]
            if (!force && old != null && clock() - old.time < if (old.result.isFailure) old.failureTtl else ttl)
                return old.result.getOrThrow()
            flights[key]?.let { return@withLock it.result }
            val version = revision
            lateinit var task: Deferred<T>
            task = scope.async(start = CoroutineStart.LAZY) {
                val result = try { Result.success(load()) } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { Result.failure(e) }
                lock.withLock {
                    // 無効化した時点で処理中だったリクエストを切り離し、完了しても新しい結果を上書きしないようにする。
                    if (version == revision && flights[key]?.result === task) {
                        val errorTtl = result.exceptionOrNull()?.let(failureTtl) ?: 0L
                        if (result.isSuccess || errorTtl > 0) {
                            entries[key] = Entry(result, clock(), errorTtl)
                            while (entries.size > maxEntries) entries.remove(entries.keys.first())
                        }
                        flights.remove(key)
                    }
                }
                result.getOrThrow()
            }
            flights[key] = Flight(version, task)
            task.start()
            task
        }
        return work.await()
    }
    suspend fun invalidate(key: String) = lock.withLock {
        entries.remove(key)
        // すでに待機中の呼び出しは結果を受け取れる。新しい呼び出しには無効化前の処理を共有しない。
        flights.remove(key)
        Unit
    }
    suspend fun clear() = lock.withLock {
        revision++
        entries.clear()
        flights.values.forEach { it.result.cancel() }
        flights.clear()
    }
}

/** サーバーから待機を求められている間は、取得が成功した扱いにしない。 */
internal class ReadBackoff(private val clock: () -> Long) {
    private var until = 0L
    private var error: ApiException? = null
    @Synchronized fun check() { if (clock() < until) throw requireNotNull(error) }
    @Synchronized fun record(failure: ApiException, delayMs: Long) {
        if (failure.code == 429) { until = maxOf(until, clock() + delayMs.coerceIn(1000, 86_400_000)); error = failure }
    }
    @Synchronized fun clear() { until = 0; error = null }
}
