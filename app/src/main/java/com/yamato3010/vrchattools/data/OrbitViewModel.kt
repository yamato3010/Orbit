package com.yamato3010.vrchattools.data

import android.app.Application
import android.os.Build
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class OrbitViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("orbit_preferences", 0)
    private val api = VrchatRepository(application)
    val imageLoader get() = api.imageLoader
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
    var dynamicColor by mutableStateOf(prefs.getBoolean("dynamic", Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)); private set
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
    private val friendProfileMutex = Mutex()
    private val friendProfiles = mutableStateMapOf<String, Pair<FriendProfile, Long>>()
    var profileRefresh by mutableIntStateOf(0); private set
    val socialFriends: List<Friend> get() = friends.map { friend ->
        val world = worldCache[friend.location.substringBefore(':')]
        val profile = friendProfiles[friend.id]?.first
        friend.copy(worldName = world?.name ?: friend.worldName, trustRank = profile?.rank ?: friend.trustRank,
            pronouns = profile?.pronouns ?: friend.pronouns)
    }
    suspend fun enrichFriendProfile(id: String) = friendProfileMutex.withLock {
        if (demo || friends.none { it.id == id }) return@withLock
        val cached = friendProfiles[id]
        if (cached != null && System.currentTimeMillis() - cached.second < 300_000) return@withLock
        val epoch = generation
        try {
            val profile = api.friendProfile(id)
            if (epoch == generation && friends.any { it.id == id }) friendProfiles[id] = profile to System.currentTimeMillis()
        } catch (e: Exception) { if (e is CancellationException) throw e }
        delay(1000) // スクロール中にリクエストが集中しないよう、追加のプロフィール情報は間隔を空けて順番に取得する。
    }
    var ownProfile by mutableStateOf(demoProfile); private set
    var notifications by mutableStateOf(demoNotifications()); private set
    var notificationLoading by mutableStateOf(false); private set
    var notificationError by mutableStateOf<String?>(null); private set
    var socialBusy by mutableStateOf(false); private set
    var socialMessage by mutableStateOf<String?>(null); private set
    var presets by mutableStateOf(defaultStatusPresets); private set
    var realtimeEnabled by mutableStateOf(prefs.getBoolean("realtime", true)); private set
    var realtimeState by mutableStateOf(RealtimeState.Demo); private set
    private var profileRevision = 0
    private var foreground = false
    private var pipelineJob: Job? = null
    private var reconcileJob: Job? = null
    private var notificationJob: Job? = null
    private var actionJob: Job? = null
    private val friendJournal = mutableListOf<FriendUpdate>()
    private val notificationJournal = mutableListOf<NotificationChange>()
    val unreadCount get() = notifications.count { !it.seen }
    fun dismissSocialMessage() { socialMessage = null }
    fun setForeground(value: Boolean) {
        if (foreground == value) return
        foreground = value
        restartPipeline()
        if (value && !demo) { refresh(); refreshNotifications(); refreshProfile() }
    }
    fun setRealtime(value: Boolean) { realtimeEnabled = value; prefs.edit().putBoolean("realtime", value).apply(); restartPipeline() }
    private fun restartPipeline() {
        pipelineJob?.cancel(); reconcileJob?.cancel()
        realtimeState = if (demo) RealtimeState.Demo else RealtimeState.Paused
        if (demo || !foreground || !realtimeEnabled) return
        val epoch = generation
        pipelineJob = viewModelScope.launch {
            var retry = 1000L
            while (epoch == generation) {
                realtimeState = if (retry == 1000L) RealtimeState.Connecting else RealtimeState.Reconnecting
                var rejected = false
                try {
                    api.pipeline().collect { signal ->
                        if (epoch != generation) return@collect
                        when (signal) {
                            PipelineSignal.Connected -> { realtimeState = RealtimeState.Connected; retry = 1000; refresh(); refreshNotifications(); refreshProfile() }
                            is PipelineSignal.Disconnected -> if (signal.code == 401 || signal.code == 403) rejected = true
                            is PipelineSignal.Event -> when (val event = signal.value) {
                                is PipelineEvent.FriendChanged -> {
                                    if (event.update.kind == "friend-delete") friendProfiles.remove(event.update.id)
                                    else friendProfiles[event.update.id]?.let { (profile, time) ->
                                        friendProfiles[event.update.id] = profile.copy(rank = event.update.rank ?: profile.rank,
                                            pronouns = event.update.pronouns ?: profile.pronouns) to time
                                    }
                                    if (busy) friendJournal.add(event.update)
                                    val fresh = applyFriendUpdate(friends, event.update)
                                    recordChanges(fresh); friends = fresh; instanceCache.clear(); lastSync = System.currentTimeMillis()
                                }
                                is PipelineEvent.NotificationChanged -> changeNotification(event.change)
                                is PipelineEvent.OwnChanged -> {
                                    profileRevision++
                                    ownProfile = ownProfile.copy(status = event.status ?: ownProfile.status, description = event.description ?: ownProfile.description,
                                        name = event.name ?: ownProfile.name, location = event.location ?: ownProfile.location, imageUrls = event.images ?: ownProfile.imageUrls)
                                    displayName = ownProfile.name
                                }
                                PipelineEvent.Resync -> { refresh(); refreshNotifications() }
                                PipelineEvent.SessionError -> rejected = true
                            }
                        }
                        if (rejected) throw PipelineAuthException()
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (e is PipelineAuthException) rejected = true
                }
                if (rejected) { realtimeState = RealtimeState.AuthRequired; reconcileJob?.cancel(); break }
                realtimeState = RealtimeState.Reconnecting
                delay(retry); retry = (retry * 2).coerceAtMost(60000)
            }
        }
        reconcileJob = viewModelScope.launch { while (true) { delay(120000); refresh(); refreshNotifications(); refreshProfile() } }
    }
    private class PipelineAuthException : IOException()
    private fun refreshProfile() {
        val epoch = generation
        val revision = profileRevision
        viewModelScope.launch { try { val value = api.currentUser().toOwnProfile(); if (epoch == generation && revision == profileRevision) { ownProfile = value; displayName = value.name } }
            catch (e: Exception) { if (e is CancellationException) throw e } }
    }
    fun refreshNotifications() {
        if (demo || notificationLoading) return
        val epoch = generation
        notificationLoading = true; notificationError = null; notificationJournal.clear()
        notificationJob = viewModelScope.launch {
            try {
                val result = api.notifications()
                if (epoch == generation) notifications = notificationJournal.fold(result, ::applyNotificationChange)
            } catch (e: Exception) { if (e is CancellationException) throw e; if (epoch == generation) notificationError = friendlyError(e) }
            finally { if (epoch == generation) { notificationLoading = false; notificationJournal.clear() } }
        }
    }
    private fun changeNotification(change: NotificationChange) {
        if (notificationLoading) notificationJournal.add(change)
        notifications = applyNotificationChange(notifications, change)
    }
    private fun socialAction(success: String, block: suspend () -> Unit) {
        if (socialBusy || authBusy) return
        val epoch = generation
        socialBusy = true; socialMessage = null
        actionJob = viewModelScope.launch {
            try { block(); if (epoch == generation) socialMessage = (if (demo) "デモ：" else "") + success }
            catch (e: Exception) { if (e is CancellationException) throw e; if (epoch == generation) socialMessage = if (e is SocialActionException) e.message else friendlyError(e) }
            finally { if (epoch == generation) socialBusy = false }
        }
    }
    fun notificationAction(item: SocialNotification, action: String) {
        require(action in setOf("see", "hide", "accept"))
        if (action == "accept" && item.type != NotificationType.FriendRequest) return
        socialAction(when(action) { "accept" -> "フレンド申請を承認しました"; "hide" -> "通知を非表示にしました"; else -> "既読にしました" }) {
            if (!demo) api.notificationAction(item.id, action)
            changeNotification(if (action == "see") NotificationChange.Seen(item.id) else NotificationChange.Remove(item.id))
            if (action == "accept") refresh()
        }
    }
    suspend fun loadInviteMessages(action: InviteAction): List<InviteMessage> =
        if (demo) listOf(InviteMessage(0, "よかったら一緒に遊びませんか？"), InviteMessage(1, "ありがとう！あとで合流します。")) else api.inviteMessages(accountId, action)
    fun sendSocial(action: InviteAction, userId: String, notificationId: String?, slot: Int?, confirmedLocation: String) {
        socialAction("${action.label}：送信しました") {
            if (!demo) {
                when (action) {
                    InviteAction.Invite -> {
                        val revision = profileRevision
                        if (!sameInviteLocation(confirmedLocation, ownProfile.location))
                            throw SocialActionException("現在地が変わりました。画面を開き直して、招待先を確認してください。")
                        val current = api.currentUser().toOwnProfile()
                        if (revision != profileRevision || !sameInviteLocation(confirmedLocation, current.location))
                            throw SocialActionException("現在地が変わりました。画面を開き直して、招待先を確認してください。")
                        profileRevision++
                        ownProfile = current
                        api.sendInvite(userId, current.location, slot)
                    }
                    InviteAction.Request -> api.requestInvite(userId, slot)
                    InviteAction.Reply, InviteAction.RequestReply -> api.respondInvite(requireNotNull(notificationId), requireNotNull(slot))
                }
            }
        }
    }
    private class SocialActionException(message: String) : IOException(message)
    fun applyStatus(status: SocialStatus, description: String) {
        if (!validStatusDescription(description)) return
        socialAction("ステータスを更新しました") {
            profileRevision++
            val updated = if (demo) ownProfile.copy(status = status, description = description) else api.updateStatus(accountId, status, description)
            profileRevision++
            ownProfile = ownProfile.copy(status = updated.status, description = updated.description)
        }
    }
    fun savePreset(name: String, status: SocialStatus, description: String) {
        if (name.isBlank() || !validStatusDescription(description) || presets.size >= 12) return
        presets = presets + StatusPreset(java.util.UUID.randomUUID().toString(), name.take(24), status, description)
        persistPresets()
    }
    fun deletePreset(id: String) { presets = presets.filterNot { it.id == id }; persistPresets() }
    private fun persistPresets() { prefs.edit().putString("presets_$accountId", JSONArray(presets.map {
        JSONObject().put("id", it.id).put("name", it.name).put("status", it.status.apiValue).put("description", it.description)
    }).toString()).apply() }
    private fun loadPresets() {
        presets = runCatching { val array = JSONArray(prefs.getString("presets_$accountId", null) ?: return@runCatching defaultStatusPresets)
            (0 until array.length()).map { array.getJSONObject(it).let { p -> StatusPreset(p.getString("id"), p.getString("name"), SocialStatus.from(p.getString("status")), p.getString("description")) } }
        }.getOrDefault(defaultStatusPresets)
    }
    init {
        loadBookmarks(); loadPresets()
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
                .put("capacity", w.capacity).put("thumbnailImageUrl", w.image)
                .put("imageUrl", w.imageFallbacks.firstOrNull().orEmpty()).put("description", w.description)
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
        generation++; refreshJob?.cancel(); worldJob?.cancel(); notificationJob?.cancel(); actionJob?.cancel(); socialBusy = false; notificationLoading = false
        accountId = user.getString("id"); demo = false; displayName = user.optString("displayName")
        friends = emptyList(); worlds = emptyList(); activities = readHistory(); worldCache.clear(); instanceCache.clear(); friendProfiles.clear()
        runCatching {
            val cached = JSONArray(prefs.getString("world_cache_$accountId", "[]"))
            for (i in 0 until cached.length()) { val w = cached.getJSONObject(i).toWorld(); worldCache[w.id] = w }
        }
        busy = false; loadBookmarks(); loginOpen = false; twoFactorMethods = emptyList(); lastSync = null
        ownProfile = user.toOwnProfile(); notifications = emptyList(); loadPresets(); restartPipeline(); refreshNotifications()
        refresh()
    }
    fun refresh() {
        if (busy) return
        if (demo) { lastSync = System.currentTimeMillis(); return }
        val epoch = generation
        refreshJob = viewModelScope.launch {
            busy = true; error = null; friendJournal.clear()
            try {
                val snapshot = api.friends()
                if (epoch != generation) return@launch
                val fresh = friendJournal.fold(snapshot, ::applyFriendUpdate)
                instanceCache.clear(); recordChanges(fresh); friends = fresh; lastSync = System.currentTimeMillis(); profileRefresh++
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
    suspend fun resolveFriendDetails(friend: Friend): List<DetailField> {
        if (demo) return demoFriendDetails(friend.id)
        val epoch = generation
        val result = api.friendDetails(friend.id)
        if (epoch != generation) throw CancellationException("Account changed")
        return result
    }
    suspend fun resolveWorldDetails(world: World): List<DetailField> {
        if (demo) return demoWorldDetails(world)
        val epoch = generation
        val result = api.worldDetails(world.id)
        if (epoch != generation) throw CancellationException("Account changed")
        return result
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
        if (demo) return@withLock DemoData.instanceDetails(address)?.let { details -> details.copy(extra = listOf(
            DetailField("稼働中", "はい"), DetailField("満員", "いいえ"), DetailField("プラットフォーム別人数", "PC：${details.occupants} 人\nAndroid：0 人"))) }
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
            authBusy = true; generation++; refreshJob?.cancel(); worldJob?.cancel(); notificationJob?.cancel(); actionJob?.cancel(); socialBusy = false; notificationLoading = false
            pipelineJob?.cancel(); reconcileJob?.cancel()
            try { api.logout() } catch (e: Exception) { if (e is CancellationException) throw e }
            finally {
                demo = true; accountId = "demo"; displayName = "旅するあなた"; friends = DemoData.friends
                worlds = DemoData.worlds; activities = DemoData.activities(); worldCache.clear(); instanceCache.clear(); friendProfiles.clear(); loadBookmarks()
                ownProfile = demoProfile; notifications = demoNotifications(); notificationError = null; socialMessage = null; loadPresets(); restartPipeline()
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
            ActivityEntry(java.util.UUID.randomUUID().toString(), f.name, text, now, f.id, kind)
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
    override fun onCleared() {
        api.imageLoader.shutdown()
        super.onCleared()
    }
    private fun friendlyError(e: Exception) = if (e is ApiException) e.message!! else "通信できませんでした。ネットワーク接続を確認して、もう一度お試しください。"
}
