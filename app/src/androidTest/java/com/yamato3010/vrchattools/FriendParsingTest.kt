package com.yamato3010.vrchattools

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yamato3010.vrchattools.data.TrustRank
import com.yamato3010.vrchattools.data.toFriend
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FriendParsingTest {
    @Test fun parsesRankTagsFromApiResponseAndDistinguishesMissingTags() {
        val user = JSONObject().put("id", "usr_test").put("displayName", "Test").put("location", "private").put("status", "active")
        assertEquals(TrustRank.Unknown, user.toFriend().trustRank)
        user.put("tags", org.json.JSONArray())
        assertEquals(TrustRank.Visitor, user.toFriend().trustRank)
        user.put("tags", org.json.JSONArray(listOf("system_trust_known", "system_trust_trusted")))
        assertEquals(TrustRank.Known, user.toFriend().trustRank)
        user.put("tags", org.json.JSONArray(listOf("system_trust_veteran")))
        assertEquals(TrustRank.Trusted, user.toFriend().trustRank)
    }
}
