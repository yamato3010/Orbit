package com.yamato3010.vrchattools.data

/** API のタグ名とランク表示にはずれがあり、system_trust_trusted は Known User に対応する。 */
enum class TrustRank(val label: String) {
    Trusted("Trusted"), Known("Known"), User("User"), NewUser("New User"), Visitor("Visitor"), Unknown("ランク不明");

    companion object {
        fun fromTags(tags: Collection<String>?): TrustRank {
            if (tags == null) return Unknown
            return when {
                "system_trust_veteran" in tags -> Trusted
                "system_trust_trusted" in tags -> Known
                "system_trust_known" in tags -> User
                "system_trust_basic" in tags -> NewUser
                tags.any { it.startsWith("system_trust_") } -> Unknown
                else -> Visitor
            }
        }
    }
}
