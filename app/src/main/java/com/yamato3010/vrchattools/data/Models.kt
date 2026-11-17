package com.yamato3010.vrchattools.data

import org.json.JSONObject

enum class Presence(val label: String) {
    JoinMe("参加歓迎"), Online("オンライン"), AskMe("要リクエスト"), Busy("取り込み中"), Offline("オフライン");
    companion object {
        fun from(status: String, location: String) = when {
            location == "offline" -> Offline
            status == "join me" -> JoinMe
            status == "ask me" -> AskMe
            status == "busy" -> Busy
            status == "offline" -> Offline
            else -> Online
        }
    }
}
data class Friend(val id: String, val name: String, val status: Presence, val location: String,
    val worldName: String = "", val image: String = "", val bio: String = "", val platform: String = "PC") {
    val locationLabel: String get() = when {
        status == Presence.Offline -> "また会えるのを楽しみに"
        location == "private" -> "プライベートなワールド"
        location == "traveling" -> "ワールドを移動中"
        worldName.isNotBlank() -> worldName
        location.startsWith("wrld_") -> "ワールドに滞在中"
        else -> "Web / アプリでオンライン"
    }
}
data class World(val id: String, val name: String, val author: String, val occupants: Int,
    val capacity: Int = 32, val image: String = "", val description: String = "", val palette: Int = 0)
data class ActivityEntry(val id: String, val name: String, val text: String, val time: Long,
    val friendId: String = "", val kind: String = "online")

fun filterFriends(friends: List<Friend>, query: String, filter: Int, saved: Set<String>): List<Friend> =
    friends.filter { friend ->
        (friend.name.contains(query, true) || friend.locationLabel.contains(query, true)) && when (filter) {
            1 -> friend.status != Presence.Offline
            2 -> friend.status == Presence.JoinMe
            3 -> friend.id in saved
            else -> true
        }
    }.sortedBy { it.status == Presence.Offline }

internal fun JSONObject.toFriend(): Friend {
    val location = optString("location", "offline")
    return Friend(optString("id"), optString("displayName"), Presence.from(optString("status"), location),
        location, image = optString("userIcon").ifBlank { optString("profilePicOverride") }
            .ifBlank { optString("currentAvatarThumbnailImageUrl") }.ifBlank { optString("currentAvatarImageUrl") },
        bio = optString("statusDescription"),
        platform = if (optString("last_platform").contains("android")) "Android" else "PC")
}
internal fun JSONObject.toWorld() = World(optString("id"), optString("name"), optString("authorName"),
    optInt("occupants"), optInt("capacity", 32), optString("thumbnailImageUrl").ifBlank { optString("imageUrl") },
    optString("description"))
