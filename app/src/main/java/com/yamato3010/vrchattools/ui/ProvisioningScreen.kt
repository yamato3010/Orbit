package com.yamato3010.vrchattools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ProvisioningScreen(onLogin: () -> Unit, onDemo: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp, 24.dp, 24.dp, 8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Rounded.AllInclusive, null, Modifier.padding(16.dp).size(32.dp))
                }
                Text("Orbitへようこそ", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Orbitは、VRChatのフレンドの居場所を確認したり、行きたいワールドを集めたりできる小さなコンパニオンアプリです。\n\n" +
                        "VRChatアカウントでログインすると、自分のフレンドの状況や招待を確認できます。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("VRChatにログイン")
                }
                OutlinedButton(onClick = onDemo, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("デモで始める")
                }
                Text(
                    "デモではサンプルデータで操作を試せます。あとから設定でログインできます。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
