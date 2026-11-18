package com.yamato3010.vrchattools.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*
import org.json.JSONObject

/** パイプラインの更新に含まれるフィールドだけを、保持しているフレンド情報に上書きする。 */
data class FriendUpdate(val id: String, val kind: String, val name: String? = null, val status: String? = null,
    val location: String? = null, val worldName: String? = null, val images: List<String>? = null,
    val bio: String? = null, val platform: String? = null, val rank: TrustRank? = null)
fun applyFriendUpdate(friends: List<Friend>, update: FriendUpdate): List<Friend> {
    if (update.kind == "friend-delete") return friends.filterNot { it.id == update.id }
    val old = friends.firstOrNull { it.id == update.id }
    if (old == null && update.name.isNullOrBlank()) return friends
    val location = when (update.kind) {
        "friend-offline" -> "offline"
        "friend-active" -> update.location?.takeUnless { it == "offline" } ?: "web"
        "friend-online" -> update.location ?: old?.location?.takeUnless { it == "offline" } ?: "private"
        else -> update.location ?: old?.location ?: "private"
    }
    val status = when {
        update.kind == "friend-offline" -> Presence.Offline
        update.status != null -> Presence.from(update.status, location)
        (update.kind in listOf("friend-online", "friend-active") || (update.location != null && update.location != "offline")) && (old == null || old.status == Presence.Offline) -> Presence.Online
        else -> old?.status ?: Presence.Online
    }
    val fresh = Friend(update.id, update.name?.takeIf { it.isNotBlank() } ?: old!!.name, status, location,
        worldName = update.worldName ?: old?.worldName?.takeIf { old.location.substringBefore(':') == location.substringBefore(':') }.orEmpty(),
        image = update.images?.firstOrNull() ?: if (update.images == null) old?.image.orEmpty() else "",
        imageFallbacks = update.images?.drop(1) ?: old?.imageFallbacks.orEmpty(),
        bio = update.bio ?: old?.bio.orEmpty(), platform = update.platform ?: old?.platform ?: "PC", trustRank = update.rank ?: old?.trustRank ?: TrustRank.Unknown)
    return friends.filterNot { it.id == update.id } + fresh
}

sealed interface PipelineEvent {
    data class FriendChanged(val update: FriendUpdate) : PipelineEvent
    data class NotificationChanged(val change: NotificationChange) : PipelineEvent
    data class OwnChanged(val status: SocialStatus?, val description: String?, val name: String?, val location: String?, val images: List<String>? = null) : PipelineEvent
    data object Resync : PipelineEvent
    data object SessionError : PipelineEvent
}
private fun JSONObject.textOrNull(key: String): String? = if (has(key) && !isNull(key)) optString(key) else null
internal fun parsePipelineEvent(text: String): PipelineEvent? = runCatching {
    val frame = JSONObject(text)
    if (frame.has("err")) return@runCatching PipelineEvent.SessionError
    val type = frame.optString("type")
    if (type == "clear-notification") return@runCatching PipelineEvent.NotificationChanged(NotificationChange.Clear)
    if (type == "see-notification" || type == "hide-notification") {
        val id = frame.optString("content").trim('"')
        if (id.isBlank()) return@runCatching null
        return@runCatching PipelineEvent.NotificationChanged(if (type == "see-notification") NotificationChange.Seen(id) else NotificationChange.Remove(id))
    }
    val data = frame.jsonObject("content") ?: return@runCatching null
    when {
        type == "notification" -> data.toSocialNotification()?.let { PipelineEvent.NotificationChanged(NotificationChange.Upsert(it)) }
        type == "response-notification" -> PipelineEvent.Resync
        type.startsWith("friend-") && type in setOf("friend-add", "friend-delete", "friend-online", "friend-offline", "friend-active", "friend-location", "friend-update") -> {
            val user = data.optJSONObject("user") ?: JSONObject()
            val id = data.textOrNull("userId") ?: data.textOrNull("userid") ?: user.textOrNull("id") ?: return@runCatching null
            val imageFields = profileImageFields
            val images = if (imageFields.any(user::has)) imageUrls(*imageFields.map { user.textOrNull(it) }.toTypedArray()) else null
            val tags = user.optJSONArray("tags")
            val platform = data.textOrNull("platform") ?: user.textOrNull("last_platform")
            PipelineEvent.FriendChanged(FriendUpdate(id, type, user.textOrNull("displayName"), user.textOrNull("status"),
                data.textOrNull("location") ?: user.textOrNull("location"), data.optJSONObject("world")?.textOrNull("name"), images,
                user.textOrNull("statusDescription"), platform?.let { when (it) { "android" -> "Android"; "ios" -> "iOS"; "web" -> "Web"; else -> "PC" } },
                tags?.let { TrustRank.fromTags((0 until it.length()).map { i -> it.optString(i) }) }))
        }
        type == "user-update" || type == "user-location" -> {
            val user = data.optJSONObject("user") ?: JSONObject()
            PipelineEvent.OwnChanged(user.textOrNull("status")?.let(SocialStatus::from), user.textOrNull("statusDescription"), user.textOrNull("displayName"),
                data.textOrNull("location") ?: user.textOrNull("location"),
                if (profileImageFields.any(user::has)) user.profileImageUrls() else null)
        }
        else -> null
    }
}.getOrNull()

sealed interface PipelineSignal {
    data object Connected : PipelineSignal
    data class Event(val value: PipelineEvent) : PipelineSignal
    data class Disconnected(val code: Int?) : PipelineSignal
}
internal fun pipelineEvents(client: OkHttpClient, authToken: String, endpoint: HttpUrl = HttpUrl.Builder().scheme("https").host("pipeline.vrchat.cloud").build()): Flow<PipelineSignal> = callbackFlow {
    // パイプラインの認証トークンがクエリ文字列に含まれるため、この URL はログに出力しない。
    val url = endpoint.newBuilder().addQueryParameter("authToken", authToken).build()
    val socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) { trySend(PipelineSignal.Connected) }
        override fun onMessage(webSocket: WebSocket, text: String) { parsePipelineEvent(text)?.let { if (trySend(PipelineSignal.Event(it)).isFailure) { webSocket.cancel(); close() } } }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { trySend(PipelineSignal.Disconnected(null)); close() }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { trySend(PipelineSignal.Disconnected(response?.code)); close() }
    })
    awaitClose { socket.cancel() }
}

enum class RealtimeState(val label: String) {
    Demo("デモモード"), Paused("自動更新は停止中"), Connecting("接続中"), Connected("リアルタイム更新中"), Reconnecting("再接続中"), AuthRequired("再ログインが必要です")
}
