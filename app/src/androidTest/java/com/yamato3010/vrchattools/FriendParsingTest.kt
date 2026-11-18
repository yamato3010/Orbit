package com.yamato3010.vrchattools

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yamato3010.vrchattools.data.TrustRank
import com.yamato3010.vrchattools.data.toFriend
import com.yamato3010.vrchattools.data.toWorld
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FriendParsingTest {
    @Test fun prefersProfileIconsAndRetainsAvatarFallbacks() {
        val user = JSONObject().put("iconUrl", "https://api.vrchat.cloud/icon")
            .put("userIcon", JSONObject.NULL).put("profilePicOverride", "null")
            .put("currentAvatarThumbnailImageUrl", "https://api.vrchat.cloud/thumb")
            .put("currentAvatarImageUrl", "https://api.vrchat.cloud/avatar")
        assertEquals("https://api.vrchat.cloud/icon", user.toFriend().image)
        assertEquals(listOf("https://api.vrchat.cloud/icon", "https://api.vrchat.cloud/thumb", "https://api.vrchat.cloud/avatar"), user.toFriend().imageUrls)
        user.put("userIcon", "https://api.vrchat.cloud/custom")
        assertEquals("https://api.vrchat.cloud/custom", user.toFriend().image)
    }
    @Test fun invalidOrMissingThumbnailsFallBackToTheWorldImage() {
        val world = JSONObject().put("thumbnailImageUrl", JSONObject.NULL).put("imageUrl", "https://api.vrchat.cloud/world-image")
        assertEquals("https://api.vrchat.cloud/world-image", world.toWorld().image)
        world.put("thumbnailImageUrl", "https://api.vrchat.cloud/world-thumb")
        assertEquals(listOf("https://api.vrchat.cloud/world-thumb", "https://api.vrchat.cloud/world-image"), world.toWorld().imageUrls)
        world.put("thumbnailImageUrl", "http://invalid/image")
        assertEquals("https://api.vrchat.cloud/world-image", world.toWorld().image)
    }

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
