package com.yamato3010.vrchattools

import com.yamato3010.vrchattools.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class InstanceRequestTest {
    @Test fun publicInstanceUsesTheExpectedApiRoute() {
        assertEquals("/api/1/instances/wrld_test:123", instanceApiUrl(InstanceLocation("wrld_test", "123")).encodedPath)
    }
    @Test fun preservesRoomDelimitersInsteadOfFormEncodingThem() {
        val room = "123~hidden(usr_owner)~region(jp)~nonce(room_nonce)"
        val url = instanceApiUrl(InstanceLocation("wrld_test", room))
        assertEquals("/api/1/instances/wrld_test:$room", url.encodedPath)
        assertFalse(url.toString().contains("%7E"))
        assertFalse(url.toString().contains("%28"))
        assertFalse(url.toString().contains("%29"))
    }
    @Test fun preservesGroupAndInviteTagsButCannotInjectOtherUrlParts() {
        val group = "123~group(grp_owner)~groupAccessType(public)~region(us)"
        assertTrue(instanceApiUrl(InstanceLocation("wrld_test", group)).encodedPath.endsWith(group))
        val invite = "123~private(usr_owner)~canRequestInvite~nonce(room)"
        assertTrue(instanceApiUrl(InstanceLocation("wrld_test", invite)).encodedPath.endsWith(invite))
        val malicious = InstanceLocation("wrld_test", "123/other?query=wrong#fragment")
        val url = instanceApiUrl(malicious)
        assertNull(url.query)
        assertNull(url.fragment)
        assertEquals(malicious.location, url.pathSegments.last())
    }
    @Test fun showsWhichResourceFailedAndDistinguishesStatuses() {
        assertTrue(instanceLoadError(InstanceResource.Instance, ApiException(400, "ignored")).contains("インスタンス情報を取得できませんでした（HTTP 400）"))
        assertTrue(instanceLoadError(InstanceResource.World, ApiException(403, "ignored")).contains("ワールド情報へのアクセス"))
        assertTrue(instanceLoadError(InstanceResource.Instance, ApiException(401, "ignored")).contains("ログインし直して"))
        assertTrue(instanceLoadError(InstanceResource.Instance, ApiException(429, "ignored")).contains("少し待って"))
    }
    @Test fun unavailableRoomAndConnectivityErrorsAreNotTheSame() {
        assertEquals(instanceLoadError(InstanceResource.Instance, null), instanceLoadError(InstanceResource.Instance, ApiException(404, "ignored")))
        assertTrue(instanceLoadError(InstanceResource.Instance, null).contains("見つかりません"))
        assertTrue(instanceLoadError(InstanceResource.Instance, IOException()).contains("接続を確認"))
    }
}
