package com.yamato3010.vrchattools.data

/** 公開されている居場所からは、ワールド内のルームまで特定できる。非公開の居場所はグループ化できない。 */
data class InstanceLocation(val worldId: String, val instanceId: String) {
    val location: String get() = "$worldId:$instanceId"
    val number: String get() = instanceId.substringBefore('~')
    private val tags: List<String> get() = instanceId.split('~').drop(1)
    // タグの順序はインスタンスの識別に影響しないが、owner・nonce・アクセス制限のタグは識別に使う。
    val key: String get() = "$worldId:$number" + tags.sorted().joinToString("") { "~$it" }
    val accessLabel: String get() = when {
        tags.any { it.startsWith("group(") } -> when {
            "groupAccessType(public)" in tags -> "Group Public"
            "groupAccessType(plus)" in tags -> "Group+"
            "groupAccessType(members)" in tags -> "Group"
            else -> "グループ（公開範囲不明）"
        }
        tags.any { it.startsWith("private(") } -> if ("canRequestInvite" in tags) "Invite+" else "Invite"
        tags.any { it.startsWith("friends(") } -> "Friends"
        tags.any { it.startsWith("hidden(") } -> "Friends+"
        else -> "Public"
    }
    val region: String? get() = tags.firstOrNull { it.startsWith("region(") }?.substringAfter('(')?.substringBefore(')')

    companion object {
        private val pattern = Regex("((?:wrld_[A-Za-z0-9_-]+|demo-world-[0-9]+)):([A-Za-z0-9_-]+(?:~[A-Za-z0-9_-]+(?:\\([A-Za-z0-9_-]+\\))?)*)")
        fun parse(value: String): InstanceLocation? = pattern.matchEntire(value)?.let {
            InstanceLocation(it.groupValues[1], it.groupValues[2])
        }
    }
}

data class FriendInstance(val address: InstanceLocation, val friends: List<Friend>)
data class SocialOverview(
    val instances: List<FriendInstance>,
    val privateFriends: List<Friend>,
    val travelingFriends: List<Friend>,
    val webFriends: List<Friend>,
    val unknownFriends: List<Friend>,
    val offlineFriends: List<Friend>
)

fun groupFriendsByInstance(friends: List<Friend>): SocialOverview {
    val visible = linkedMapOf<String, Pair<InstanceLocation, MutableList<Friend>>>()
    val private = mutableListOf<Friend>()
    val traveling = mutableListOf<Friend>()
    val web = mutableListOf<Friend>()
    val unknown = mutableListOf<Friend>()
    val offline = mutableListOf<Friend>()
    friends.distinctBy { it.id }.forEach { friend ->
        val address = InstanceLocation.parse(friend.location)
        when {
            friend.status == Presence.Offline || friend.location == "offline" -> offline.add(friend)
            address != null -> visible.getOrPut(address.key) { address to mutableListOf() }.second.add(friend)
            friend.location == "private" -> private.add(friend)
            friend.location == "traveling" -> traveling.add(friend)
            friend.location.isBlank() || friend.location == "web" -> web.add(friend)
            else -> unknown.add(friend)
        }
    }
    return SocialOverview(
        visible.values.map { (address, members) -> FriendInstance(address, members.sortedBy { it.name.lowercase() }) }
            .sortedWith(compareByDescending<FriendInstance> { it.friends.size }.thenBy { it.address.key }),
        private, traveling, web, unknown, offline
    )
}

/** このインスタンスの人数を集計する。ワールド全体の人数を表す World.occupants は使わない。 */
data class InstanceDetails(val occupants: Int?, val capacity: Int?, val accessLabel: String, val region: String?, val extra: List<DetailField> = emptyList())
