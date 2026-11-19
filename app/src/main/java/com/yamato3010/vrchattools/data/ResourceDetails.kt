package com.yamato3010.vrchattools.data

import org.json.JSONArray
import org.json.JSONObject

/** 表示する項目を明示的に選び、認証情報や非公開の移動先は UI に渡さない。 */
data class DetailField(val label: String, val value: String)
private class DetailFields(private val json: JSONObject) {
    val items = mutableListOf<DetailField>()
    fun text(key: String, label: String) {
        val value = json.opt(key) as? String ?: return
        if (value.isNotBlank() && value !in setOf("null", "none")) items.add(DetailField(label, value))
    }
    fun number(key: String, label: String, suffix: String = "") {
        val value = json.opt(key) as? Number ?: return
        if (value.toDouble() >= 0) items.add(DetailField(label, "$value$suffix"))
    }
    fun flag(key: String, label: String) {
        val value = json.opt(key) as? Boolean ?: return
        items.add(DetailField(label, if (value) "はい" else "いいえ"))
    }
    fun strings(key: String, label: String) {
        val values = json.optJSONArray(key)?.let { array -> (0 until array.length()).mapNotNull { array.opt(it) as? String } }
            ?.filter { it.isNotBlank() && it != "null" }?.distinct().orEmpty()
        if (values.isNotEmpty()) items.add(DetailField(label, values.joinToString("\n")))
    }
}
internal fun JSONObject.friendDetailFields(): List<DetailField> = DetailFields(this).apply {
    text("id", "ユーザーID"); text("bio", "プロフィール"); text("pronouns", "代名詞")
    text("date_joined", "登録日"); text("last_login", "最終ログイン（API）"); text("last_activity", "最終アクティビティ（API）")
    text("last_platform", "最終プラットフォーム"); text("state", "接続状態（API）")
    strings("bioLinks", "プロフィールリンク"); strings("tags", "タグ")
    flag("allowAvatarCopying", "アバターのコピーを許可")
}.items
internal fun JSONObject.worldDetailFields(): List<DetailField> = DetailFields(this).apply {
    text("id", "ワールドID"); text("authorId", "作者ID"); text("description", "ワールドの説明"); text("releaseStatus", "公開状態")
    number("version", "バージョン"); number("recommendedCapacity", "推奨人数", " 人"); number("capacity", "定員", " 人")
    number("visits", "訪問数"); number("favorites", "お気に入り数")
    number("publicOccupants", "公開インスタンスの人数", " 人"); number("privateOccupants", "非公開インスタンスの人数", " 人")
    text("created_at", "作成日時"); text("updated_at", "更新日時"); text("publicationDate", "公開日時")
    strings("supportedPlatforms", "対応プラットフォーム"); strings("tags", "タグ")
}.items
internal fun JSONObject.instanceDetailFields(): List<DetailField> = DetailFields(this).apply {
    text("id", "インスタンスID"); text("worldId", "ワールドID"); text("displayName", "表示名"); text("description", "説明")
    text("ownerId", "オーナーID"); text("creatorId", "作成者ID"); text("groupAccessType", "グループの公開範囲")
    flag("active", "稼働中"); flag("full", "満員"); flag("hasCapacityForYou", "自分の参加枠あり")
    flag("canRequestInvite", "招待リクエスト可"); flag("queueEnabled", "待機列あり")
    flag("ageGate", "年齢制限あり"); flag("permanent", "常設")
    flag("playerPersistenceEnabled", "プレイヤーデータの永続化")
    text("photonRegion", "サーバーリージョン"); text("minimumAvatarPerformance", "アバターの最低パフォーマンス")
    text("closedAt", "閉鎖日時"); strings("languages", "使用言語"); strings("creationLanguages", "作成時の言語")
    this@instanceDetailFields.optJSONObject("platforms")?.let { platforms ->
        val counts = DetailFields(platforms).apply {
            number("standalonewindows", "PC", " 人"); number("android", "Android", " 人"); number("ios", "iOS", " 人")
        }.items
        if (counts.isNotEmpty()) items.add(DetailField("プラットフォーム別人数", counts.joinToString("\n") { "${it.label}：${it.value}" }))
    }
}.items

internal fun demoFriendDetails(id: String) = JSONObject().put("id", id).put("bio", "ワールド巡りと写真が好きです。気軽にお話ししましょう！")
    .put("date_joined", "2022-04-01").put("pronouns", "they/them").put("bioLinks", JSONArray(listOf("https://vrchat.com/"))).friendDetailFields()
internal fun demoWorldDetails(world: World) = JSONObject().put("id", world.id).put("authorId", "usr_demo_creator")
    .put("releaseStatus", "public").put("version", 12).put("recommendedCapacity", 16).put("capacity", world.capacity)
    .put("visits", 12840).put("favorites", 256).put("created_at", "2024-04-01T12:00:00Z").put("updated_at", "2026-11-01T09:00:00Z")
    .put("supportedPlatforms", JSONArray(listOf("standalonewindows", "android"))).put("tags", JSONArray(listOf("author_tag_chill", "author_tag_photography"))).worldDetailFields()
