package com.yamato3010.vrchattools.data

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.yamato3010.vrchattools.BuildConfig
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

internal data class AppUpdate(
    val versionName: String,
    val versionCode: Long,
    val url: String,
    val size: Long,
    val sha256: String,
)

internal class AppUpdateException(message: String) : IOException(message)

internal object AppUpdates {
    private val client = OkHttpClient.Builder()
        .followSslRedirects(false)
        .callTimeout(5, TimeUnit.MINUTES)
        .build()
    private val apkName = Regex("""Orbit-([1-9][0-9]{0,9})\.apk""")
    private val sha256 = Regex("sha256:[0-9a-f]{64}")
    private val releaseTag = Regex("""v[0-9]+\.[0-9]+\.[0-9]+""")

    suspend fun check(installedVersion: Long): AppUpdate? = runInterruptible(Dispatchers.IO) {
        val repository = BuildConfig.RELEASE_REPOSITORY
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repository/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 404) throw AppUpdateException("公開済みのリリースが見つかりません。")
            if (!response.isSuccessful) throw AppUpdateException("GitHubに接続できませんでした（HTTP ${response.code}）。")
            val release = JSONObject(response.body?.string() ?: throw IOException())
            val tag = release.getString("tag_name")
            if (!releaseTag.matches(tag)) throw AppUpdateException("リリースのバージョン形式が無効です。")
            val assets = release.getJSONArray("assets")
            val candidates = (0 until assets.length()).map { assets.getJSONObject(it) }
                .mapNotNull { asset ->
                    if (asset.optString("state") != "uploaded") return@mapNotNull null
                    val match = apkName.matchEntire(asset.optString("name")) ?: return@mapNotNull null
                    asset to match.groupValues[1].toLong()
                }
            val (asset, code) = candidates.singleOrNull() ?: throw AppUpdateException("更新用APKが見つかりません。")
            if (code <= installedVersion) return@use null

            val size = asset.optLong("size")
            val digest = asset.optString("digest")
            val url = asset.optString("browser_download_url").toHttpUrlOrNull()
            val expectedPath = "/$repository/releases/download/$tag/${asset.getString("name")}"
            if (size <= 0 || !sha256.matches(digest) || url == null || !url.isHttps ||
                url.host != "github.com" || url.encodedPath != expectedPath
            ) {
                throw AppUpdateException("更新用APKの情報が無効です。")
            }
            AppUpdate(tag.removePrefix("v"), code, url.toString(), size, digest.removePrefix("sha256:"))
        }
    }

    suspend fun download(context: Context, update: AppUpdate): File = runInterruptible(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates")
        directory.mkdirs()
        val temporary = File.createTempFile("orbit-", ".apk", directory)
        try {
            val request = Request.Builder().url(update.url).build()
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            client.newCall(request).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) throw IOException()
                body.byteStream().use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            received += count
                            if (received > update.size) throw IOException()
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                    }
                }
            }
            val actualDigest = digest.digest().joinToString("") { "%02x".format(it) }
            if (received != update.size || actualDigest != update.sha256) {
                throw AppUpdateException("APKのダウンロード内容を確認できませんでした。")
            }

            val archive = context.packageManager.getPackageArchiveInfo(temporary.path, 0)
            if (archive == null || archive.packageName != context.packageName ||
                PackageInfoCompat.getLongVersionCode(archive) != update.versionCode ||
                archive.versionName != update.versionName ||
                PackageInfoCompat.getLongVersionCode(installedPackage(context)) >= update.versionCode
            ) {
                throw AppUpdateException("APKのアプリIDまたはバージョンが一致しません。")
            }

            File(directory, "Orbit.apk").also { apk ->
                if (!temporary.renameTo(apk)) throw IOException()
            }
        } finally {
            temporary.delete()
        }
    }

    @Suppress("DEPRECATION")
    fun installedPackage(context: Context) =
        context.packageManager.getPackageInfo(context.packageName, 0)

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()
}
