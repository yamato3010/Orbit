package com.yamato3010.vrchattools.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import coil.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** セッション Cookie だけを永続化し、パスワードや認証コードは保存しない。 */
internal class SessionCookies(context: Context) : CookieJar {
    private val prefs = context.getSharedPreferences("orbit_session", Context.MODE_PRIVATE)
    private val cookies = mutableListOf<Cookie>()
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("orbit-session", null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("orbit-session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    init {
        runCatching {
            val parts = prefs.getString("cookies", null)?.split(":")
            if (parts != null && parts.size == 2) {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
                val data = String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
                data.lines().mapNotNull { Cookie.parse(BASE_URL, it) }.forEach { cookies.add(it) }
            }
        }.onFailure { prefs.edit().clear().apply() }
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> =
        cookies.filter { it.expiresAt > System.currentTimeMillis() && it.matches(url) }
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.filter { it.name == "auth" || it.name == "twoFactorAuth" }.forEach { cookie ->
            this.cookies.removeAll { it.name == cookie.name }
            this.cookies.add(cookie)
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(this.cookies.joinToString("\n").toByteArray(Charsets.UTF_8))
        prefs.edit().putString("cookies", Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }
    @Synchronized fun clear() { cookies.clear(); prefs.edit().clear().apply() }
    @Synchronized fun hasSession() = cookies.any { it.name == "auth" && it.expiresAt > System.currentTimeMillis() }
    companion object { val BASE_URL = HttpUrl.Builder().scheme("https").host("api.vrchat.cloud").addPathSegments("api/1/").build() }
}

class ApiException(val code: Int, message: String) : IOException(message)
class VrchatRepository internal constructor(context: Context, clientOverride: okhttp3.OkHttpClient? = null) {
    private val jar = SessionCookies(context)
    private val client = clientOverride ?: vrchatClient(jar)
    val imageLoader: ImageLoader = ImageLoader.Builder(context.applicationContext)
        .okHttpClient(client)
        .crossfade(180)
        .build()

    fun hasSession() = jar.hasSession()
    private suspend fun request(path: String, method: String = "GET", json: JSONObject? = null, authorization: String? = null): String =
        request("https://api.vrchat.cloud/api/1/$path".toHttpUrl(), method, json, authorization)

    private suspend fun request(url: HttpUrl, method: String = "GET", json: JSONObject? = null, authorization: String? = null): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
            .header("Accept", "application/json")
        if (authorization != null) builder.header("Authorization", authorization)
        if (method != "GET") builder.method(method, (json?.toString() ?: "{}").toRequestBody("application/json".toMediaType()))
        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(response.code, when (response.code) {
                401 -> "認証できませんでした。ログイン情報または認証コードを確認してください。"
                403 -> "アクセスできません。VRChat側のアカウント状態を確認してください。"
                429 -> "リクエストが多すぎます。少し待ってからお試しください。"
                else -> "VRChatに接続できませんでした（${response.code}）。"
            })
            body
        }
    }
    suspend fun login(username: String, password: String): JSONObject {
        jar.clear()
        return JSONObject(request("auth/user", authorization = Credentials.basic(
            URLEncoder.encode(username, "UTF-8").replace("+", "%20"), URLEncoder.encode(password, "UTF-8").replace("+", "%20"))))
    }
    suspend fun verify(code: String, method: String): JSONObject {
        require(method in listOf("totp", "emailOtp", "otp"))
        val verified = JSONObject(request("auth/twofactorauth/${method.lowercase()}/verify", "POST", JSONObject().put("code", code)))
        if (!verified.optBoolean("verified")) throw IOException("認証コードが正しくありません。")
        return currentUser()
    }
    suspend fun currentUser() = JSONObject(request("auth/user"))
    suspend fun friends(): List<Friend> {
        val result = mutableListOf<Friend>()
        for (offline in listOf(false, true)) {
            var offset = 0
            do {
                val page = JSONArray(request("auth/user/friends?offline=$offline&n=100&offset=$offset"))
                for (i in 0 until page.length()) result.add(page.getJSONObject(i).toFriend())
                offset += page.length()
            } while (page.length() == 100)
        }
        return result.distinctBy { it.id }
    }
    suspend fun worlds(query: String = ""): List<World> {
        val search = if (query.isBlank()) "sort=heat&order=descending" else "search=${URLEncoder.encode(query, "UTF-8")}&sort=relevance"
        val array = JSONArray(request("worlds?n=24&$search"))
        return (0 until array.length()).map { array.getJSONObject(it).toWorld() }
    }
    suspend fun world(id: String) = JSONObject(request("worlds/${URLEncoder.encode(id, "UTF-8")}")).toWorld()
    suspend fun instance(address: InstanceLocation): InstanceDetails? {
        val body = request(instanceApiUrl(address))
        if (body.trim() == "null") return null
        val data = JSONObject(body)
        val access = when (data.optString("type")) {
            "hidden" -> "Friends+"
            "friends" -> "Friends"
            "private" -> if (data.optBoolean("canRequestInvite")) "Invite+" else "Invite"
            "group" -> when (data.optString("groupAccessType")) {
                "public" -> "Group Public"; "plus" -> "Group+"; "members" -> "Group"
                else -> address.accessLabel
            }
            "public" -> "Public"
            else -> address.accessLabel
        }
        fun count(key: String): Int? = if (data.has(key) && !data.isNull(key)) data.optInt(key, -1).takeIf { it >= 0 } else null
        return InstanceDetails(count("n_users") ?: count("userCount"), count("capacity"), access,
            data.optString("region").takeIf { it.isNotBlank() && it != "null" } ?: address.region)
    }
    fun pipeline(): kotlinx.coroutines.flow.Flow<PipelineSignal> {
        val token = jar.loadForRequest(SessionCookies.BASE_URL).firstOrNull { it.name == "auth" }?.value
            ?: return kotlinx.coroutines.flow.flowOf(PipelineSignal.Disconnected(401))
        return pipelineEvents(client.newBuilder().readTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS).build(), token)
    }
    suspend fun notifications(): List<SocialNotification> {
        val result = mutableListOf<SocialNotification>()
        var offset = 0
        do {
            val page = JSONArray(request("auth/user/notifications?n=100&offset=$offset"))
            for (i in 0 until page.length()) page.optJSONObject(i)?.toSocialNotification()?.let(result::add)
            offset += page.length()
        } while (page.length() == 100)
        return result.distinctBy { it.id }.sortedByDescending { it.createdAt }
    }
    private fun segment(value: String) = URLEncoder.encode(value, "UTF-8")
    suspend fun notificationAction(id: String, action: String) {
        require(action in setOf("see", "hide", "accept"))
        request("auth/user/notifications/${segment(id)}/$action", "PUT")
    }
    suspend fun inviteMessages(userId: String, action: InviteAction): List<InviteMessage> {
        val data = JSONArray(request("message/${segment(userId)}/${action.messageType}"))
        return (0 until data.length()).map { data.getJSONObject(it) }.mapNotNull {
            val slot = it.optInt("slot", -1)
            if (slot in 0..11) InviteMessage(slot, it.optString("message")) else null
        }.sortedBy { it.slot }
    }
    suspend fun sendInvite(userId: String, location: String, slot: Int?) {
        require(InstanceLocation.parse(location) != null)
        request("invite/${segment(userId)}", "POST", JSONObject().put("instanceId", location).apply { slot?.let { require(it in 0..11); put("messageSlot", it) } })
    }
    suspend fun requestInvite(userId: String, slot: Int?) {
        request("requestInvite/${segment(userId)}", "POST", JSONObject().apply { slot?.let { require(it in 0..11); put("requestSlot", it) } })
    }
    suspend fun respondInvite(notificationId: String, slot: Int) {
        require(slot in 0..11)
        request("invite/${segment(notificationId)}/response", "POST", JSONObject().put("responseSlot", slot))
    }
    suspend fun updateStatus(userId: String, status: SocialStatus, description: String): OwnProfile {
        require(validStatusDescription(description))
        return JSONObject(request("users/${segment(userId)}", "PUT", JSONObject()
            .put("status", status.apiValue).put("statusDescription", description))).toOwnProfile()
    }
    suspend fun logout() { try { request("logout", "PUT") } finally { jar.clear() } }
    fun clearSession() = jar.clear()
}
