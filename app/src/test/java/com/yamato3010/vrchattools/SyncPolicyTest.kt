package com.yamato3010.vrchattools

import com.yamato3010.vrchattools.data.*
import org.junit.Assert.*
import org.junit.Test

class SyncPolicyTest {
    @Test fun connectedSnapshotsAreLessFrequentButDisconnectedFallbackRemainsFast() {
        var now = 0L
        val schedule = SnapshotSchedule { now }
        assertTrue(schedule.due(SnapshotResource.Friends, true))
        schedule.started(SnapshotResource.Friends)
        now = 120_000
        assertFalse(schedule.due(SnapshotResource.Friends, true))
        assertTrue(schedule.due(SnapshotResource.Friends, false))
        now = 600_000; assertTrue(schedule.due(SnapshotResource.Friends, true))
        schedule.started(SnapshotResource.Friends)
        assertFalse(schedule.due(SnapshotResource.Friends, false))
        assertTrue(schedule.due(SnapshotResource.Notifications, true))
        schedule.clear(); assertTrue(schedule.due(SnapshotResource.Friends, true))
    }
    @Test fun statusAndProfileChangesDoNotInvalidateAnyInstances() {
        val f = Friend("usr_1", "name", Presence.Online, "wrld_one:123")
        assertTrue(affectedInstances(f, f.copy(status = Presence.Busy, name = "new", pronouns = "they/them")).isEmpty())
        assertTrue(affectedInstances(f.copy(location = "private"), f.copy(location = "private")).isEmpty())
        assertEquals(setOf("wrld_one:123", "wrld_one:456"), affectedInstances(f, f.copy(location = "wrld_one:456")))
        assertEquals(setOf("wrld_one:123"), affectedInstances(f, f.copy(status = Presence.Offline, location = "offline")))
        assertEquals(setOf("wrld_one:123"), affectedInstances(null, f))
    }
}
