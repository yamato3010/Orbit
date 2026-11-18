package com.yamato3010.vrchattools

import com.yamato3010.vrchattools.data.*
import org.junit.Assert.*
import org.junit.Test

class SocialReducerTest {
    private val friend = Friend("usr_test", "Test", Presence.JoinMe, "wrld_a:123", "World A", "https://example.com/icon", "hi", "Android", TrustRank.Trusted, listOf("https://example.com/avatar"))
    @Test fun partialUpdatesPreserveImagesRankLocationAndDescription() {
        val next = applyFriendUpdate(listOf(friend), FriendUpdate(friend.id, "friend-update", status = "busy")).single()
        assertEquals(friend.copy(status = Presence.Busy), next)
    }
    @Test fun movementClearsStaleWorldNameButPreservesProfile() {
        val next = applyFriendUpdate(listOf(friend), FriendUpdate(friend.id, "friend-location", location = "private")).single()
        assertEquals("", next.worldName)
        assertEquals("private", next.location)
        assertEquals(friend.imageUrls, next.imageUrls)
        assertEquals(friend.trustRank, next.trustRank)
    }
    @Test fun offlineAndWebTransitionsCannotKeepAVisibleRoom() {
        val off = applyFriendUpdate(listOf(friend), FriendUpdate(friend.id, "friend-offline")).single()
        assertEquals(Presence.Offline, off.status)
        assertEquals("offline", off.location)
        val web = applyFriendUpdate(listOf(off), FriendUpdate(friend.id, "friend-active")).single()
        assertEquals("web", web.location)
        assertEquals(Presence.Online, web.status)
        assertTrue(applyFriendUpdate(listOf(friend), FriendUpdate(friend.id, "friend-delete")).isEmpty())
    }
    @Test fun duplicateNotificationsPreserveReadStateAndCanBeRemoved() {
        val n = SocialNotification("not_1", "usr_1", "Test", NotificationType.Invite, "hello", 1)
        var items = applyNotificationChange(emptyList(), NotificationChange.Upsert(n))
        items = applyNotificationChange(items, NotificationChange.Seen(n.id))
        items = applyNotificationChange(items, NotificationChange.Upsert(n))
        assertEquals(1, items.size); assertTrue(items.single().seen)
        assertTrue(applyNotificationChange(items, NotificationChange.Remove(n.id)).isEmpty())
        assertTrue(applyNotificationChange(items, NotificationChange.Clear).isEmpty())
    }
    @Test fun missingProfileDoesNotInventNewFriendsAndExplicitImageClearWorks() {
        assertTrue(applyFriendUpdate(emptyList(), FriendUpdate("usr_missing", "friend-update", status = "busy")).isEmpty())
        assertTrue(applyFriendUpdate(listOf(friend), FriendUpdate(friend.id, "friend-update", images = emptyList())).single().imageUrls.isEmpty())
    }
    @Test fun invitesRequireTheExactConfirmedVisibleRoom() {
        assertTrue(sameInviteLocation("wrld_a:123~region(jp)~hidden(usr_a)", "wrld_a:123~hidden(usr_a)~region(jp)"))
        assertFalse(sameInviteLocation("wrld_a:123", "wrld_a:456"))
        assertFalse(sameInviteLocation("wrld_a:123", "private"))
        assertFalse(sameInviteLocation("offline", "offline"))
    }
    @Test fun descriptionLimitCountsUtf16AndPresetsAreValid() {
        assertTrue(validStatusDescription("a".repeat(32)))
        assertFalse(validStatusDescription("a".repeat(33)))
        assertFalse(validStatusDescription("😀".repeat(17)))
        assertTrue(defaultStatusPresets.all { validStatusDescription(it.description) })
    }
}
