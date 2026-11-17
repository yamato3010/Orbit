package com.yamato3010.vrchattools

import com.yamato3010.vrchattools.data.*
import org.junit.Assert.*
import org.junit.Test

class FriendFilterTest {
    @Test fun onlineFilterExcludesOfflineAndRetainsBusyFriends() {
        val result = filterFriends(DemoData.friends, "", 1, emptySet())
        assertEquals(6, result.size)
        assertTrue(result.none { it.status == Presence.Offline })
        assertTrue(result.any { it.status == Presence.Busy })
    }
    @Test fun joinFilterDoesNotExposePrivateFriendsAsJoinable() {
        assertEquals(setOf("demo-1", "demo-3"), filterFriends(DemoData.friends, "", 2, emptySet()).map { it.id }.toSet())
    }
    @Test fun queryAndFavoritesAreCombinedCaseInsensitively() {
        assertEquals("mikan", filterFriends(DemoData.friends, "MIK", 3, setOf("demo-2")).single().name)
        assertTrue(filterFriends(DemoData.friends, "MIK", 3, setOf("demo-1")).isEmpty())
    }
    @Test fun locationSearchAndOfflineOrderingWork() {
        assertEquals("しろ", filterFriends(DemoData.friends, "こもれび", 0, emptySet()).single().name)
        assertTrue(filterFriends(DemoData.friends.reversed(), "", 0, emptySet()).takeLast(2).all { it.status == Presence.Offline })
    }
    @Test fun privateLocationAndOfflineStateAreRespected() {
        assertEquals(Presence.Offline, Presence.from("join me", "offline"))
        assertEquals("プライベートなワールド", DemoData.friends.first { it.id == "demo-4" }.locationLabel)
    }
}
