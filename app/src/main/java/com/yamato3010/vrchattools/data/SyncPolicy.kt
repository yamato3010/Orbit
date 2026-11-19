package com.yamato3010.vrchattools.data

internal enum class SnapshotResource { Friends, Notifications, OwnProfile }

/** 失敗した試行も記録し、一時的なエラーの後にタイマーが動くたびにリクエストを送るのを防ぐ。 */
internal class SnapshotSchedule(private val clock: () -> Long) {
    private val attempts = mutableMapOf<SnapshotResource, Long>()
    fun due(resource: SnapshotResource, connected: Boolean): Boolean = attempts[resource]?.let {
        clock() - it >= if (connected) CachePolicy.CONNECTED_SYNC else CachePolicy.DISCONNECTED_SYNC
    } ?: true
    fun started(resource: SnapshotResource) { attempts[resource] = clock() }
    fun clear() = attempts.clear()
}

/** ステータス、名前、画像、タグの変更はルームの人数に影響しない。非公開のルームは不明のまま扱う。 */
internal fun affectedInstances(before: Friend?, after: Friend?): Set<String> {
    fun address(friend: Friend?) = friend?.takeUnless { it.status == Presence.Offline }?.location?.let(InstanceLocation::parse)?.key
    val old = address(before)
    val new = address(after)
    return if (old == new) emptySet() else setOfNotNull(old, new)
}
