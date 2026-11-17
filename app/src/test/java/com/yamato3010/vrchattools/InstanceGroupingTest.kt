package com.yamato3010.vrchattools

import com.yamato3010.vrchattools.data.*
import org.junit.Assert.*
import org.junit.Test

class InstanceGroupingTest {
    private fun friend(id: String, location: String, status: Presence = Presence.Online) = Friend(id, id, status, location)

    @Test fun groupsSameInstanceButSeparatesRoomsOfSameWorld() {
        val overview = groupFriendsByInstance(listOf(friend("a", "wrld_one:123"), friend("b", "wrld_one:123"), friend("c", "wrld_one:456")))
        assertEquals(listOf(2, 1), overview.instances.map { it.friends.size })
        assertEquals(listOf("123", "456"), overview.instances.map { it.address.number })
    }
    @Test fun sameRoomNumberInDifferentWorldsOrOwnersDoesNotMerge() {
        val overview = groupFriendsByInstance(listOf(friend("a", "wrld_one:123~hidden(usr_a)"), friend("b", "wrld_one:123~hidden(usr_b)"), friend("c", "wrld_two:123~hidden(usr_a)")))
        assertEquals(3, overview.instances.size)
    }
    @Test fun reorderedTagsReferToTheSameInstanceButDifferentNoncesDoNot() {
        val overview = groupFriendsByInstance(listOf(
            friend("a", "wrld_one:1~hidden(usr_a)~region(jp)~nonce(first)"),
            friend("b", "wrld_one:1~nonce(first)~region(jp)~hidden(usr_a)"),
            friend("c", "wrld_one:1~hidden(usr_a)~region(jp)~nonce(second)")))
        assertEquals(listOf(2, 1), overview.instances.map { it.friends.size })
    }
    @Test fun hiddenLocationsAreNeverPresentedAsOneSharedInstance() {
        val overview = groupFriendsByInstance(listOf(friend("a", "private"), friend("b", "private"), friend("c", "traveling"), friend("d", "web"), friend("e", "unknown"), friend("f", "wrld_one:123", Presence.Offline)))
        assertTrue(overview.instances.isEmpty())
        assertEquals(2, overview.privateFriends.size)
        assertEquals(1, overview.travelingFriends.size)
        assertEquals(1, overview.webFriends.size)
        assertEquals(1, overview.unknownFriends.size)
        assertEquals(1, overview.offlineFriends.size)
    }
    @Test fun accessAndRegionAreParsedWithoutDroppingJoinParameters() {
        val address = InstanceLocation.parse("wrld_one:123~private(usr_a)~canRequestInvite~region(eu)")!!
        assertEquals("Invite+", address.accessLabel)
        assertEquals("eu", address.region)
        assertTrue(address.location.contains("canRequestInvite"))
        assertEquals("Group Public", InstanceLocation.parse("wrld_one:1~group(grp_a)~groupAccessType(public)")!!.accessLabel)
        assertNull(InstanceLocation.parse("wrld_one:"))
        assertNull(InstanceLocation.parse("private"))
        assertNull(InstanceLocation.parse("wrld_one:123/invalid"))
    }
    @Test fun duplicateFriendsAreNotDoubleCountedAndDemoShowsBothGroupingCases() {
        val one = friend("a", "wrld_one:123")
        assertEquals(1, groupFriendsByInstance(listOf(one, one)).instances.single().friends.size)
        val demo = groupFriendsByInstance(DemoData.friends)
        assertEquals(listOf(2, 1, 1), demo.instances.map { it.friends.size })
        assertEquals(2, demo.instances.count { it.address.worldId == "demo-world-1" })
    }
}
