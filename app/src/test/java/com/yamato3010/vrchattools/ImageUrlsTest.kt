package com.yamato3010.vrchattools

import com.yamato3010.vrchattools.data.imageUrls
import org.junit.Assert.*
import org.junit.Test

class ImageUrlsTest {
    @Test fun ignoresNullBlankAndNonImageValuesWithoutHidingValidFallbacks() {
        assertEquals(listOf("https://api.vrchat.cloud/api/1/image/file_a/1/256"),
            imageUrls(null, "", "null", "  ", "undefined", "https://api.vrchat.cloud/api/1/image/file_a/1/256"))
    }
    @Test fun retainsPreferenceOrderAndDeduplicatesEquivalentUrls() {
        assertEquals(listOf("https://example.com/icon", "https://example.com/avatar"),
            imageUrls(" https://example.com/icon ", "https://example.com/icon#fragment", "https://example.com/avatar"))
    }
    @Test fun resolvesApiRelativeUrlsButRejectsInsecureOrCredentialBearingUrls() {
        assertEquals(listOf("https://api.vrchat.cloud/api/1/file/file_a/1/file"),
            imageUrls("http://example.com/image", "file:///image", "https://user:secret@example.com/image", "/api/1/file/file_a/1/file"))
    }
}
