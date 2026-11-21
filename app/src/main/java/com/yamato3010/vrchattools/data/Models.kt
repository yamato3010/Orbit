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
    val worldName: String = "", val image: String = "", val bio: String = "", val platform: String = "PC", val trustRank: TrustRank = TrustRank.Unknown, val imageFallbacks: List<String> = emptyList(), val pronouns: String = "") {
    val imageUrls: List<String> get() = imageUrls(image, *imageFallbacks.toTypedArray())
    val locationLabel: String get() = when {
        status == Presence.Offline -> "また会えるのを楽しみに"
        location == "private" -> "プライベートなワールド"
        location == "traveling" -> "ワールドを移動中"
        worldName.isNotBlank() -> worldName
        location.startsWith("wrld_") -> "ワールドに滞在中"
        location.isBlank() || location == "web" -> "Web / アプリでオンライン"
        else -> "現在地を確認できません"
    }
}
enum class WorldPlatform(val apiName: String, val label: String) {
    Windows("standalonewindows", "Windows"),
    Android("android", "Android"),
    Ios("ios", "iOS")
}

data class World(val id: String, val name: String, val author: String, val occupants: Int,
    val capacity: Int = 32, val image: String = "", val description: String = "", val palette: Int = 0,
    val imageFallbacks: List<String> = emptyList(), val platforms: List<WorldPlatform> = emptyList()) {
    val imageUrls: List<String> get() = imageUrls(image, *imageFallbacks.toTypedArray())
}
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

internal fun JSONObject.toFriend(limited: Boolean = false): Friend {
    val location = optString("location", "offline")
    val tags = optJSONArray("tags")?.let { array ->
        (0 until array.length()).mapNotNull { array.opt(it) as? String }
    }
    val images = profileImageUrls()
    return Friend(optString("id"), optString("displayName"), Presence.from(optString("status"), location),
        location, image = images.firstOrNull().orEmpty(), imageFallbacks = images.drop(1),
        bio = optString("statusDescription"),
        platform = if (optString("last_platform").contains("android")) "Android" else "PC",
        trustRank = if (limited && tags.isNullOrEmpty()) TrustRank.Unknown else TrustRank.fromTags(tags), pronouns = profilePronouns())
}
internal fun JSONObject.toWorld(): World {
    val images = imageUrls(optString("thumbnailImageUrl"), optString("imageUrl"))
    val packages = optJSONArray("unityPackages")
    val platformNames = (0 until (packages?.length() ?: 0)).mapNotNull { index ->
        packages?.optJSONObject(index)?.optString("platform")?.trim()?.lowercase()
    }.toSet()
    val platforms = WorldPlatform.entries.filter { it.apiName in platformNames }
    return World(optString("id"), optString("name"), optString("authorName"),
        optInt("occupants"), optInt("capacity", 32), images.firstOrNull().orEmpty(),
        optString("description"), imageFallbacks = images.drop(1), platforms = platforms)
}

internal val profileImageFields = listOf("userIcon", "iconUrl", "profilePicOverride", "currentAvatarThumbnailImageUrl", "currentAvatarImageUrl")
internal fun JSONObject.profileImageUrls(): List<String> = imageUrls(*profileImageFields.map { optString(it) }.toTypedArray())

internal fun JSONObject.profilePronouns(): String = (opt("pronouns") as? String)?.trim()?.takeUnless { it == "null" }.orEmpty()

data class FriendProfile(val rank: TrustRank, val pronouns: String)
