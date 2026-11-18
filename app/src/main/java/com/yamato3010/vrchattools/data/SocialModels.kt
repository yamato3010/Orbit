package com.yamato3010.vrchattools.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

enum class SocialStatus(val apiValue: String, val label: String) {
    JoinMe("join me", "参加歓迎"), Active("active", "オンライン"), AskMe("ask me", "要リクエスト"), Busy("busy", "取り込み中");
    companion object { fun from(value: String) = entries.firstOrNull { it.apiValue == value } ?: Active }
}
data class StatusPreset(val id: String, val name: String, val status: SocialStatus, val description: String)
val defaultStatusPresets = listOf(
    StatusPreset("join", "遊びにきてね", SocialStatus.JoinMe, "気軽に遊びにきてね！"),
    StatusPreset("work", "作業中", SocialStatus.AskMe, "作業中です。お誘いはリクエストで。"),
    StatusPreset("quiet", "集中したい", SocialStatus.Busy, "いまは集中しています。あとで会おう。")
)
const val STATUS_DESCRIPTION_LIMIT = 32
fun validStatusDescription(text: String) = text.length <= STATUS_DESCRIPTION_LIMIT

data class OwnProfile(val id: String, val name: String, val status: SocialStatus, val description: String, val location: String)
internal fun JSONObject.toOwnProfile() = OwnProfile(optString("id"), optString("displayName"),
    SocialStatus.from(optString("status")), optString("statusDescription"), optString("location", "offline"))

enum class NotificationType(val apiValue: String, val label: String) {
    Invite("invite", "招待"), RequestInvite("requestInvite", "招待リクエスト"), FriendRequest("friendRequest", "フレンド申請"),
    InviteResponse("inviteResponse", "招待への返信"), RequestResponse("requestInviteResponse", "リクエストへの返信"), Other("", "お知らせ");
    companion object { fun from(value: String) = entries.firstOrNull { it.apiValue == value } ?: Other }
}
data class SocialNotification(val id: String, val senderId: String, val senderName: String, val type: NotificationType,
    val message: String, val createdAt: Long, val seen: Boolean = false, val location: String = "", val worldName: String = "")
internal fun JSONObject.jsonObject(key: String): JSONObject? = when (val value = opt(key)) {
    is JSONObject -> value
    is String -> runCatching { JSONObject(value) }.getOrNull()
    else -> null
}
internal fun JSONObject.toSocialNotification(): SocialNotification? {
    val id = optString("id").takeIf { it.isNotBlank() && it != "null" } ?: return null
    val details = jsonObject("details") ?: JSONObject()
    val text = details.optString("inviteMessage").ifBlank { details.optString("requestMessage") }
        .ifBlank { details.optString("responseMessage") }.ifBlank { optString("message") }
    val created = optString("created_at")
    val date = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX").firstNotNullOfOrNull { pattern ->
        runCatching { SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(created)?.time }.getOrNull()
    } ?: 0L
    return SocialNotification(id, optString("senderUserId"), optString("senderUsername").takeUnless { it == "null" || it.isBlank() } ?: "ユーザー",
        NotificationType.from(optString("type")), text, date, optBoolean("seen"), details.optString("worldId"), details.optString("worldName"))
}

data class InviteMessage(val slot: Int, val message: String)
enum class InviteAction(val label: String, val messageType: String) {
    Invite("招待を送る", "message"), Request("招待をリクエスト", "request"), Reply("定型文で返信", "response"), RequestReply("定型文で返信", "requestResponse")
}
sealed interface NotificationChange {
    data class Upsert(val item: SocialNotification) : NotificationChange
    data class Seen(val id: String) : NotificationChange
    data class Remove(val id: String) : NotificationChange
    data object Clear : NotificationChange
}
fun applyNotificationChange(items: List<SocialNotification>, change: NotificationChange): List<SocialNotification> = when (change) {
    is NotificationChange.Upsert -> {
        val old = items.firstOrNull { it.id == change.item.id }
        (items.filterNot { it.id == change.item.id } + change.item.copy(seen = change.item.seen || old?.seen == true)).sortedByDescending { it.createdAt }
    }
    is NotificationChange.Seen -> items.map { if (it.id == change.id) it.copy(seen = true) else it }
    is NotificationChange.Remove -> items.filterNot { it.id == change.id }
    NotificationChange.Clear -> emptyList()
}

val demoProfile = OwnProfile("demo", "旅するあなた", SocialStatus.Active, "今日も、いい出会いを。", "demo-world-3:789")
fun demoNotifications() = listOf(
    SocialNotification("demo-invite", "demo-1", "こはる", NotificationType.Invite, "よかったら遊びにきてね！", System.currentTimeMillis(), location = "demo-world-3:789", worldName = "こもれびの庭"),
    SocialNotification("demo-request", "demo-3", "しろ", NotificationType.RequestInvite, "一緒に遊びませんか？", System.currentTimeMillis() - 60000),
    SocialNotification("demo-friend", "demo-new", "はる", NotificationType.FriendRequest, "", System.currentTimeMillis() - 120000)
)

/** 居場所が非公開、移動中、または不明の場合は、招待先として使えない。 */
fun sameInviteLocation(confirmed: String, current: String): Boolean {
    val address = InstanceLocation.parse(confirmed) ?: return false
    return address.key == InstanceLocation.parse(current)?.key
}
