package com.yamato3010.vrchattools.data

object DemoData {
    val worlds = listOf(
        World("demo-world-1", "雲の上の喫茶店", "sorairo", 24, 40, description = "雲の海に浮かぶ、小さな喫茶店。あたたかいコーヒーと、ゆっくり流れる時間を。", palette = 0),
        World("demo-world-2", "Midnight Rooftop", "tsuki", 18, 32, description = "街の灯りを見下ろしながら、夜更かしを。静かな音楽が流れる屋上ラウンジ。", palette = 1),
        World("demo-world-3", "こもれびの庭", "komorebi", 36, 60, description = "木漏れ日が揺れる、緑いっぱいの庭。お散歩にも、友達とのおしゃべりにも。", palette = 2),
        World("demo-world-4", "Sakura no Oto", "haru", 12, 32, description = "春の音を聴きにいこう。満開の桜と、水辺の小さな駅。", palette = 3),
        World("demo-world-5", "Blue Hour", "aoi", 8, 24, description = "夜が始まる、その少し前。波の音と青い空に包まれる海辺。", palette = 4)
    )
    val friends = listOf(
        Friend("demo-1", "こはる", Presence.JoinMe, "demo-world-1:123", "雲の上の喫茶店", bio = "まったりお話ししよ〜", platform = "PC", trustRank = TrustRank.Trusted),
        Friend("demo-2", "mikan", Presence.Online, "demo-world-1:456~hidden(usr_demo)~region(jp)", "雲の上の喫茶店", bio = "今日もどこかで。", trustRank = TrustRank.Known),
        Friend("demo-3", "しろ", Presence.JoinMe, "demo-world-3:789", "こもれびの庭", bio = "写真撮りにいきたい！", platform = "Android", trustRank = TrustRank.NewUser),
        Friend("demo-4", "Aoi", Presence.AskMe, "private", bio = "作業したり、お話ししたり", trustRank = TrustRank.User),
        Friend("demo-5", "ねむ", Presence.Busy, "private", bio = "ワールド制作中…", trustRank = TrustRank.Trusted),
        Friend("demo-6", "sora", Presence.Online, "demo-world-3:789", "こもれびの庭", bio = "庭でのんびり。", trustRank = TrustRank.Visitor),
        Friend("demo-7", "ゆき", Presence.Offline, "offline", bio = "週末に会いましょう", trustRank = TrustRank.Known),
        Friend("demo-8", "Luna", Presence.Offline, "offline", bio = "See you soon ♡", trustRank = TrustRank.Unknown)
    )
    fun instanceDetails(address: InstanceLocation) = when (address.worldId to address.number) {
        "demo-world-3" to "789" -> InstanceDetails(7, 32, address.accessLabel, "jp")
        "demo-world-1" to "123" -> InstanceDetails(5, 20, address.accessLabel, "jp")
        "demo-world-1" to "456" -> InstanceDetails(3, 20, address.accessLabel, "jp")
        else -> null
    }
    fun activities(): List<ActivityEntry> {
        val now = System.currentTimeMillis()
        return listOf(
            ActivityEntry("d1", "こはる", "がオンラインになりました", now - 120_000, "demo-1"),
            ActivityEntry("d2", "しろ", "が「こもれびの庭」に移動しました", now - 480_000, "demo-3", "world"),
            ActivityEntry("d3", "mikan", "がオンラインになりました", now - 900_000, "demo-2"),
            ActivityEntry("d4", "sora", "が「こもれびの庭」に移動しました", now - 2_100_000, "demo-6", "world"),
            ActivityEntry("d5", "ゆき", "がオフラインになりました", now - 4_200_000, "demo-7", "offline")
        )
    }
}
