package com.yamato3010.vrchattools

import com.yamato3010.vrchattools.data.TrustRank
import org.junit.Assert.assertEquals
import org.junit.Test

class TrustRankTest {
    @Test fun mapsApiTagNamesToTheirActualDisplayedRanks() {
        assertEquals(TrustRank.Trusted, TrustRank.fromTags(listOf("system_trust_veteran")))
        assertEquals(TrustRank.Known, TrustRank.fromTags(listOf("system_trust_trusted")))
        assertEquals(TrustRank.User, TrustRank.fromTags(listOf("system_trust_known")))
        assertEquals(TrustRank.NewUser, TrustRank.fromTags(listOf("system_trust_basic")))
    }
    @Test fun highestRankWinsRegardlessOfTagOrder() {
        val tags = listOf("system_trust_basic", "system_trust_veteran", "system_trust_known", "system_trust_trusted")
        assertEquals(TrustRank.Trusted, TrustRank.fromTags(tags))
        assertEquals(TrustRank.Trusted, TrustRank.fromTags(tags.reversed()))
        assertEquals(TrustRank.Known, TrustRank.fromTags(tags.filterNot { it == "system_trust_veteran" }))
    }
    @Test fun anExplicitEmptyTagListMeansVisitor() {
        assertEquals(TrustRank.Visitor, TrustRank.fromTags(emptyList()))
        assertEquals(TrustRank.Visitor, TrustRank.fromTags(listOf("system_supporter", "language_jpn")))
    }
    @Test fun missingOrUnrecognizedRankInformationIsNotGuessedAsVisitor() {
        assertEquals(TrustRank.Unknown, TrustRank.fromTags(null))
        assertEquals(TrustRank.Unknown, TrustRank.fromTags(listOf("system_trust_future_rank")))
    }
}
