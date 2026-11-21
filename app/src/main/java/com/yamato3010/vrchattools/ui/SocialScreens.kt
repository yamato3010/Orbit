@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.yamato3010.vrchattools.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.yamato3010.vrchattools.data.*
import kotlinx.coroutines.CancellationException

private data class SocialTarget(val action: InviteAction, val userId: String, val name: String, val notificationId: String? = null)

@Composable
fun NotificationsScreen(model: OrbitViewModel, bottomInset: Dp = 0.dp) {
    var unread by rememberSaveable { mutableStateOf(false) }
    var target by remember { mutableStateOf<SocialTarget?>(null) }
    var accept by remember { mutableStateOf<SocialNotification?>(null) }
    val context = LocalContext.current
    var linkError by remember { mutableStateOf(false) }
    LaunchedEffect(model.demo) { model.refreshNotifications() }
    RefreshableLazyColumn(
        isRefreshing = model.notificationLoading,
        onRefresh = model::refreshNotifications,
        contentPadding = PaddingValues(24.dp, 12.dp, 24.dp, 24.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("あなたへのお知らせ", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("招待・リクエスト・フレンド申請", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = model::refreshNotifications, enabled = !model.notificationLoading) { Icon(Icons.Outlined.Refresh, "通知を更新") }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !unread, onClick = { unread = false }, label = { Text("すべて") })
            FilterChip(selected = unread, onClick = { unread = true }, label = { Text("未読 ${model.unreadCount}") })
        } }
        if (model.demo) item { Text("デモの通知です。操作はVRChatに送信されません。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        if (model.notificationLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        model.notificationError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (linkError) item { Text("リンクを開けるアプリが見つかりませんでした。", color = MaterialTheme.colorScheme.error) }
        val visible = model.notifications.filter { !unread || !it.seen }
        if (visible.isEmpty() && !model.notificationLoading) item { Text("いまは通知がありません。", Modifier.padding(vertical = 32.dp)) }
        items(visible, key = { it.id }) { item ->
            Surface(shape = RoundedCornerShape(26.dp), color = if (item.seen) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${if (!item.seen) "● " else ""}${item.type.label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(item.senderName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (item.worldName.isNotBlank()) Text(item.worldName)
                    if (item.message.isNotBlank()) Text(item.message, style = MaterialTheme.typography.bodyMedium)
                    if (item.createdAt > 0) Text(relativeTime(item.createdAt, System.currentTimeMillis()), style = MaterialTheme.typography.labelSmall)
                    when (item.type) {
                        NotificationType.FriendRequest -> Button(onClick = { accept = item }, enabled = !model.socialBusy) { Text("フレンド申請を承認") }
                        NotificationType.Invite -> {
                            val location = InstanceLocation.parse(item.location)
                            if (location != null) FilledTonalButton(onClick = {
                                if (model.demo) model.notificationAction(item, "see") else {
                                    val uri = Uri.parse("https://vrchat.com/home/launch").buildUpon().appendQueryParameter("worldId", location.worldId).appendQueryParameter("instanceId", location.instanceId).build()
                                    linkError = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.isFailure
                                    if (!linkError) model.notificationAction(item, "see")
                                }
                            }) { Text(if (model.demo) "招待先を確認（デモ）" else "VRChatで招待先を開く") }
                            TextButton(onClick = { target = SocialTarget(InviteAction.Reply, item.senderId, item.senderName, item.id) }, enabled = !model.socialBusy) { Text("招待に返信") }
                        }
                        NotificationType.RequestInvite -> {
                            FilledTonalButton(onClick = { target = SocialTarget(InviteAction.Invite, item.senderId, item.senderName, item.id) }, enabled = !model.socialBusy) { Text("現在地へ招待する") }
                            TextButton(onClick = { target = SocialTarget(InviteAction.RequestReply, item.senderId, item.senderName, item.id) }, enabled = !model.socialBusy) { Text("リクエストに返信") }
                        }
                        else -> Unit
                    }
                    Row {
                        if (!item.seen) TextButton(onClick = { model.notificationAction(item, "see") }, enabled = !model.socialBusy) { Text("既読にする") }
                        TextButton(onClick = { model.notificationAction(item, "hide") }, enabled = !model.socialBusy) { Text("非表示") }
                    }
                }
            }
        }
    }
    target?.let { InviteDialog(model, it.action, it.userId, it.name, it.notificationId) { target = null } }
    accept?.let { item -> AlertDialog(onDismissRequest = { accept = null }, title = { Text("フレンド申請を承認") }, text = { Text("${item.senderName}さんをフレンドに追加しますか？${if (model.demo) "（デモ）" else ""}") },
        confirmButton = { TextButton(onClick = { model.notificationAction(item, "accept"); accept = null }, enabled = !model.socialBusy) { Text("承認する") } }, dismissButton = { TextButton(onClick = { accept = null }) { Text("キャンセル") } }) }
}

@Composable
fun InviteDialog(model: OrbitViewModel, action: InviteAction, userId: String, name: String, notificationId: String? = null, onDismiss: () -> Unit) {
    var messages by remember(action, userId) { mutableStateOf<List<InviteMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Int?>(null) }
    val location = remember { model.ownProfile.location }
    val canInvite = action != InviteAction.Invite || InstanceLocation.parse(location) != null
    LaunchedEffect(action, userId) {
        try { messages = model.loadInviteMessages(action); selected = messages.firstOrNull()?.slot }
        catch (e: Exception) { if (e is CancellationException) throw e; failure = true }
        finally { loading = false }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(action.label) }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("宛先：$name", fontWeight = FontWeight.Bold)
            if (model.demo) Text("デモ操作です。実際には送信しません。")
            if (action == InviteAction.Invite) {
                Text(if (canInvite) "招待先：$location" else "現在地を取得できません。VRChat内でインスタンスに参加してから、画面を開き直してください。")
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failure) Text("定型文を取得できませんでした。画面を開き直してお試しください。", color = MaterialTheme.colorScheme.error)
            Text("VRChatに保存済みの定型文", style = MaterialTheme.typography.labelMedium)
            if (!loading && !failure && messages.isEmpty()) Text("利用できる定型文がありません。VRChat側で設定してください。")
            messages.forEach { message -> FilterChip(selected = selected == message.slot, onClick = { selected = message.slot }, label = { Text("${message.slot + 1}：${message.message.ifBlank { "（空の定型文）" }}") }) }
            Text("上記の内容で送信します。", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { model.sendSocial(action, userId, notificationId, selected, location); onDismiss() }, enabled = !model.socialBusy && !loading && !failure && canInvite && selected != null) { Text("送信する") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } })
}

@Composable
fun StatusSheet(model: OrbitViewModel, onDismiss: () -> Unit) {
    var status by rememberSaveable { mutableStateOf(model.ownProfile.status) }
    var description by rememberSaveable { mutableStateOf(model.ownProfile.description) }
    var name by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = { if (!model.socialBusy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusProfileHeader(model.ownProfile)
            Text("自分のステータス", modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${model.ownProfile.name} · 現在：${model.ownProfile.status.label}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally))
            if (model.demo) Text("デモ操作です。実際のアカウントには反映されません。", style = MaterialTheme.typography.bodySmall)
            SocialStatus.entries.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { value -> FilterChip(selected = status == value, onClick = { status = value }, label = { Text(value.label) }) }
            } }
            OutlinedTextField(description, { if (validStatusDescription(it)) description = it }, label = { Text("ステータス説明") }, supportingText = { Text("${description.length} / $STATUS_DESCRIPTION_LIMIT") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { model.applyStatus(status, description) }, enabled = !model.socialBusy && validStatusDescription(description), modifier = Modifier.fillMaxWidth()) { Text(if (model.socialBusy) "反映中…" else "VRChatに反映する${if (model.demo) "（デモ）" else ""}") }
            HorizontalDivider()
            Text("プリセット", style = MaterialTheme.typography.titleMedium)
            Text("選んだ後に「反映する」を押すと更新されます。", style = MaterialTheme.typography.bodySmall)
            model.presets.forEach { preset -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { status = preset.status; description = preset.description }, modifier = Modifier.weight(1f)) { Column { Text(preset.name); Text(preset.status.label, style = MaterialTheme.typography.labelSmall) } }
                IconButton(onClick = { model.deletePreset(preset.id) }) { Icon(Icons.Outlined.Delete, "${preset.name}を削除") }
            } }
            OutlinedTextField(name, { name = it.take(24) }, label = { Text("プリセット名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { model.savePreset(name, status, description); name = "" }, enabled = name.isNotBlank() && model.presets.size < 12, modifier = Modifier.fillMaxWidth()) { Text("この内容を端末に保存") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Expressive の公式の花形をアバターの枠に使い、プロフィール画像全体が見えるようにする。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusProfileHeader(profile: OwnProfile) {
    val flower = MaterialShapes.Flower.toShape()
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(184.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(174.dp).rotate(-12f).background(colors.tertiaryContainer.copy(alpha = .55f), flower))
            Box(Modifier.size(162.dp).rotate(12f).background(colors.primaryContainer, flower))
            RemoteImage(profile.imageUrls, "自分のアイコン",
                Modifier.size(112.dp).border(4.dp, colors.surface, CircleShape).padding(4.dp)
                    .clip(CircleShape).background(colors.secondaryContainer)) {
                Text(profile.name.take(1).ifBlank { "O" }, style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold, color = colors.onSecondaryContainer)
            }
        }
    }
}
