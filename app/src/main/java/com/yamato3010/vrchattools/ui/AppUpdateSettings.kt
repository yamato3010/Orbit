package com.yamato3010.vrchattools.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.yamato3010.vrchattools.BuildConfig
import com.yamato3010.vrchattools.data.AppUpdate
import com.yamato3010.vrchattools.data.AppUpdateException
import com.yamato3010.vrchattools.data.AppUpdates
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun AppUpdateSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installed = remember(context) { AppUpdates.installedPackage(context) }
    var update by remember { mutableStateOf<AppUpdate?>(null) }
    var apk by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("アプリの更新", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("現在のバージョン ${installed.versionName}", style = MaterialTheme.typography.bodyMedium)
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (BuildConfig.DEBUG) {
            Text("アプリ内更新を利用するには、GitHub Releasesの配布版をインストールしてください。", style = MaterialTheme.typography.bodySmall)
            return@Column
        }

        OutlinedButton(enabled = !busy, onClick = {
            scope.launch {
                busy = true
                apk = null
                update = null
                message = "更新を確認中…"
                try {
                    update = AppUpdates.check(PackageInfoCompat.getLongVersionCode(installed))
                    message = update?.let { "バージョン ${it.versionName} をインストールできます。" }
                        ?: "最新版を使用しています。"
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: AppUpdateException) {
                    message = failure.message
                } catch (_: Exception) {
                    message = "更新を確認できませんでした。"
                } finally {
                    busy = false
                }
            }
        }) { Text("更新を確認") }

        update?.let { available ->
            OutlinedButton(enabled = !busy, onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !AppUpdates.canInstall(context)) {
                    try {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                        message = "Orbitからのインストールを許可し、この画面に戻ってもう一度押してください。"
                    } catch (_: Exception) {
                        message = "インストールの許可を設定できませんでした。"
                    }
                    return@OutlinedButton
                }

                scope.launch {
                    busy = true
                    try {
                        val downloaded = apk?.takeIf { it.isFile } ?: run {
                            message = "バージョン ${available.versionName} をダウンロード中…"
                            AppUpdates.download(context.applicationContext, available).also { apk = it }
                        }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", downloaded)
                        val installer = Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(installer)
                        message = "Androidの画面でインストールを確認してください。"
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: AppUpdateException) {
                        message = failure.message
                    } catch (_: Exception) {
                        message = "更新を準備できませんでした。もう一度お試しください。"
                    } finally {
                        busy = false
                    }
                }
            }) { Text(if (apk == null) "ダウンロードしてインストール" else "インストール") }
        }
    }
}
