package com.yamato3010.vrchattools.data

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class OrbitViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("orbit_preferences", 0)
    private val api = VrchatRepository(application)
    var demo by mutableStateOf(true); private set
    var displayName by mutableStateOf("旅するあなた"); private set
    var friends by mutableStateOf(DemoData.friends); private set
    var worlds by mutableStateOf(DemoData.worlds); private set
    var activities by mutableStateOf(DemoData.activities()); private set
    var savedFriends by mutableStateOf(setOf("demo-1", "demo-3")); private set
    var savedWorlds by mutableStateOf(setOf("demo-world-1", "demo-world-3")); private set
    var busy by mutableStateOf(false); private set
    var authBusy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var authError by mutableStateOf<String?>(null); private set
    var twoFactorMethods by mutableStateOf<List<String>>(emptyList()); private set
    var loginOpen by mutableStateOf(false); private set
    var lastSync by mutableStateOf<Long?>(null); private set
    var themeMode by mutableIntStateOf(prefs.getInt("theme", 0)); private set
    var dynamicColor by mutableStateOf(prefs.getBoolean("dynamic", false)); private set
    var worldLoading by mutableStateOf(false); private set
    var worldQuery by mutableStateOf(""); private set
    private var accountId = "demo"
    private var refreshJob: Job? = null
    private var worldJob: Job? = null
    private var generation = 0
    private val worldMutex = Mutex()
    private val instanceMutex = Mutex()
    private val instanceCache = mutableMapOf<String, InstanceDetails>()
    private val worldCache = mutableStateMapOf<String, World>()
    val socialFriends: List<Friend> get() = friends.map { friend ->
        worldCache[friend.location.substringBefore(':')]?.let { friend.copy(worldName = it.name) } ?: friend
    }
    init {
        loadBookmarks()
        if (api.hasSession()) viewModelScope.launch {
            authBusy = true
            try { acceptUser(api.currentUser()) }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                error = "セッションを復元できませんでした。設定から再ログインできます。"
            } finally { authBusy = false }
        }
    }
    private fun loadBookmarks() {
        savedFriends = prefs.getStringSet("friends_$accountId", if (demo) setOf("demo-1", "demo-3") else emptySet())!!.toSet()
        savedWorlds = prefs.getStringSet("worlds_$accountId", if (demo) setOf("demo-world-1", "demo-world-3") else emptySet())!!.toSet()
    }
    fun toggleFriend(id: String) {
        savedFriends = if (id in savedFriends) savedFriends - id else savedFriends + id
        prefs.edit().putStringSet("friends_$accountId", savedFriends).apply()
    }
    fun toggleWorld(world: World) {
        worldCache[world.id] = world
        savedWorlds = if (world.id in savedWorlds) savedWorlds - world.id else savedWorlds + world.id
        prefs.edit().putStringSet("worlds_$accountId", savedWorlds).apply()
        prefs.edit().putString("world_cache_$accountId", JSONArray(worldCache.values.map { w ->
            JSONObject().put("id", w.id).put("name", w.name).put("authorName", w.author).put("occupants", w.occupants)
                .put("capacity", w.capacity).put("thumbnailImageUrl", w.image).put("description", w.description)
        }).toString()).apply()
    }
    fun bookmarkedWorlds() = (worlds + worldCache.values).distinctBy { it.id }.filter { it.id in savedWorlds }
    fun setTheme(mode: Int) { themeMode = mode; prefs.edit().putInt("theme", mode).apply() }
    fun setDynamic(value: Boolean) { dynamicColor = value; prefs.edit().putBoolean("dynamic", value).apply() }
    fun dismissError() { error = null }
    fun openLogin() { authError = null; twoFactorMethods = emptyList(); loginOpen = true }
    fun closeLogin() { if (!authBusy) { loginOpen = false; twoFactorMethods = emptyList(); api.clearSession() } }
    fun login(username: String, password: String) = authenticate { api.login(username, password) }
    fun verify(code: String, method: String) = authenticate { api.verify(code, method) }
    private fun authenticate(block: suspend () -> JSONObject) {
        if (authBusy) return
        viewModelScope.launch {
            authBusy = true; authError = null
            try {
                val user = block()
                val methods = user.optJSONArray("requiresTwoFactorAuth")
                if (methods != null) {
                    twoFactorMethods = (0 until methods.length()).map { methods.getString(it) }.filter { it in listOf("totp", "emailOtp", "otp") }
                    if (twoFactorMethods.isEmpty()) throw IOException("この認証方式にはまだ対応していません。")
                } else acceptUser(user)
            } catch (e: Exception) { if (e is CancellationException) throw e; authError = friendlyError(e) }
            finally { authBusy = false }
        }
    }
    private fun acceptUser(user: JSONObject) {
        if (!user.optString("id").startsWith("usr_")) throw IOException("ユーザー情報を取得できませんでした。再ログインしてください。")
        generation++; refreshJob?.cancel(); worldJob?.cancel()
        accountId = user.getString("id"); demo = false; displayName = user.optString("displayName")
        friends = emptyList(); worlds = emptyList(); activities = readHistory(); worldCache.clear(); instanceCache.clear()
        runCatching {
            val cached = JSONArray(prefs.getString("world_cache_$accountId", "[]"))
            for (i in 0 until cached.length()) { val w = cached.getJSONObject(i).toWorld(); worldCache[w.id] = w }
        }
        busy = false; loadBookmarks(); loginOpen = false; twoFactorMethods = emptyList(); lastSync = null
        refresh()
    }
    fun refresh() {
        if (busy) return
        if (demo) { lastSync = System.currentTimeMillis(); return }
        val epoch = generation
        refreshJob = viewModelScope.launch {
            busy = true; error = null
            try {
                val fresh = api.friends()
                if (epoch != generation) return@launch
                instanceCache.clear(); recordChanges(fresh); friends = fresh; lastSync = System.currentTimeMillis()
                if (worlds.isEmpty()) searchWorlds(worldQuery)
            } catch (e: Exception) { if (e is CancellationException) throw e; error = friendlyError(e) }
            finally { if (epoch == generation) busy = false }
        }
    }
    fun searchWorlds(query: String) {
        worldQuery = query
        if (demo) return
        worldJob?.cancel()
        val epoch = generation
        worldJob = viewModelScope.launch {
            worldLoading = true
            try {
                val result = api.worlds(query)
                if (epoch == generation) { worlds = result; result.forEach { worldCache[it.id] = it } }
            } catch (e: Exception) { if (e is CancellationException) throw e; error = friendlyError(e) }
            finally { if (epoch == generation) worldLoading = false }
        }
    }
    suspend fun resolveWorld(id: String): World? = worldMutex.withLock {
        worldCache[id]?.let { return@withLock it }
        DemoData.worlds.firstOrNull { demo && it.id == id }?.let { return@withLock it }
        if (demo || !id.startsWith("wrld_")) return@withLock null
        val epoch = generation
        val world = api.world(id)
        if (epoch != generation) return@withLock null
        worldCache[id] = world
        world
    }
    suspend fun resolveInstance(address: InstanceLocation): InstanceDetails? = instanceMutex.withLock {
        if (demo) return@withLock DemoData.instanceDetails(address)
        instanceCache[address.key]?.let { return@withLock it }
        val epoch = generation
        val details = api.instance(address)
        if (epoch != generation) return@withLock null
        if (details != null) instanceCache[address.key] = details
        details
    }
    fun logout() {
        if (authBusy) return
        viewModelScope.launch {
            authBusy = true; generation++; refreshJob?.cancel(); worldJob?.cancel()
            try { api.logout() } catch (e: Exception) { if (e is CancellationException) throw e }
            finally {
                demo = true; accountId = "demo"; displayName = "旅するあなた"; friends = DemoData.friends
                worlds = DemoData.worlds; activities = DemoData.activities(); worldCache.clear(); instanceCache.clear(); loadBookmarks()
                busy = false; worldLoading = false; authBusy = false; lastSync = null; worldQuery = ""
            }
        }
    }
    private fun recordChanges(fresh: List<Friend>) {
        val old = runCatching { JSONObject(prefs.getString("snapshot_$accountId", "{}")!!) }.getOrDefault(JSONObject())
        val now = System.currentTimeMillis()
        val added = fresh.mapNotNull { f ->
            if (!old.has(f.id)) return@mapNotNull null
            val previous = old.getJSONObject(f.id)
            val previousStatus = previous.optString("status")
            val text: String
            val kind: String
            when {
                previousStatus == Presence.Offline.name && f.status != Presence.Offline -> { text = "がオンラインになりました"; kind = "online" }
                previousStatus != Presence.Offline.name && f.status == Presence.Offline -> { text = "がオフラインになりました"; kind = "offline" }
                previous.optString("location") != f.location && f.status != Presence.Offline -> { text = "が移動しました：${f.locationLabel}"; kind = "world" }
                previousStatus != f.status.name -> { text = "が「${f.status.label}」になりました"; kind = "status" }
                else -> return@mapNotNull null
            }
            ActivityEntry("${f.id}-$now", f.name, text, now, f.id, kind)
        }
        activities = (added + activities).take(100)
        val snapshot = JSONObject()
        fresh.forEach { snapshot.put(it.id, JSONObject().put("status", it.status.name).put("location", it.location)) }
        prefs.edit().putString("snapshot_$accountId", snapshot.toString()).putString("history_$accountId", JSONArray(activities.map {
            JSONObject().put("id", it.id).put("name", it.name).put("text", it.text).put("time", it.time).put("friendId", it.friendId).put("kind", it.kind)
        }).toString()).apply()
    }
    private fun readHistory(): List<ActivityEntry> = runCatching {
        val array = JSONArray(prefs.getString("history_$accountId", "[]"))
        (0 until array.length()).map { i -> array.getJSONObject(i).let {
            ActivityEntry(it.getString("id"), it.getString("name"), it.getString("text"), it.getLong("time"), it.optString("friendId"), it.optString("kind"))
        } }
    }.getOrDefault(emptyList())
    private fun friendlyError(e: Exception) = if (e is ApiException) e.message!! else "通信できませんでした。ネットワーク接続を確認して、もう一度お試しください。"
}
