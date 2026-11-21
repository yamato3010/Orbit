@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.yamato3010.vrchattools.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.yamato3010.vrchattools.data.*
import kotlinx.coroutines.CancellationException

private fun openLink(context: Context, uri: Uri): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, uri)); true
} catch (_: ActivityNotFoundException) { false }

@Composable
fun FriendSheet(friend: Friend, model: OrbitViewModel, onDismiss: () -> Unit, onWorld: (World) -> Unit) {
    var inviteAction by remember { mutableStateOf<InviteAction?>(null) }
    val context = LocalContext.current
    var world by remember(friend.location) { mutableStateOf<World?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf(false) }
    LaunchedEffect(friend.id, model.ownProfile.id, model.profileRefresh) { model.enrichFriendProfile(friend.id) }
    val worldId = friend.location.substringBefore(":")
    LaunchedEffect(worldId) {
        try { world = model.resolveWorld(worldId) }
        catch (e: Exception) { if (e is CancellationException) throw e; loadError = true }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Avatar(friend, 92.dp)
            Text(friend.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(listOf(friend.trustRank.label, friend.pronouns).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(shape = CircleShape, color = friend.status.color().copy(alpha = .14f)) {
                Text(friend.status.label, Modifier.padding(12.dp, 6.dp), color = friend.status.color(), style = MaterialTheme.typography.labelLarge)
            }
            if (friend.bio.isNotBlank()) Text(friend.bio, style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(22.dp)).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(world?.name ?: friend.locationLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(if (loadError) "ワールド情報を取得できませんでした" else "${friend.platform} · ${if (friend.location == "private") "非公開のインスタンス" else "フレンドの現在地"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (world != null) IconButton(onClick = { world?.let(onWorld) }) { Icon(Icons.Rounded.ChevronRight, "ワールド詳細") }
            }
            ExpandableDetails("${model.ownProfile.id}/friend/${friend.id}", listOfNotNull(DetailField("ユーザーID", friend.id),
                friend.trustRank.takeUnless { it == TrustRank.Unknown }?.let { DetailField("トラストランク", it.label) }), model.demo,
                load = { force -> model.resolveFriendDetails(friend, force) })
            FilledTonalButton(onClick = { model.toggleFriend(friend.id) }, Modifier.fillMaxWidth().height(52.dp)) {
                Icon(if (friend.id in model.savedFriends) Icons.Rounded.Star else Icons.Rounded.StarOutline, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp)); Text(if (friend.id in model.savedFriends) "お気に入りに追加済み" else "お気に入りに追加")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { inviteAction = InviteAction.Invite }, enabled = !model.socialBusy, modifier = Modifier.weight(1f)) { Text("招待する") }
                OutlinedButton(onClick = { inviteAction = InviteAction.Request }, enabled = !model.socialBusy && friend.status != Presence.Offline, modifier = Modifier.weight(1f)) { Text("招待をリクエスト") }
            }
            if (!model.demo) {
                if (friend.location.startsWith("wrld_") && friend.location.contains(":")) Button(onClick = {
                    val uri = Uri.parse("https://vrchat.com/home/launch").buildUpon().appendQueryParameter("worldId", worldId)
                        .appendQueryParameter("instanceId", friend.location.substringAfter(":")).build()
                    linkError = !openLink(context, uri)
                }, Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Outlined.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("VRChatでインスタンスを開く") }
                TextButton(onClick = { linkError = !openLink(context, Uri.parse("https://vrchat.com/home/user/${friend.id}")) }) { Text("VRChatのプロフィールを見る") }
            } else Text("デモのフレンドです。ログイン後に実際のフレンドが表示されます。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (linkError) Text("リンクを開けるアプリが見つかりませんでした。", color = MaterialTheme.colorScheme.error)
        }
    }
    inviteAction?.let { InviteDialog(model, it, friend.id, friend.name) { inviteAction = null } }
}

@Composable
fun WorldSheet(world: World, model: OrbitViewModel, onDismiss: () -> Unit, onSave: () -> Unit) {
    val context = LocalContext.current
    var linkError by remember { mutableStateOf(false) }
    val platforms by produceState(world.platforms, world, model.ownProfile.id) {
        value = world.platforms
        if (value.isEmpty() && !model.demo) {
            try {
                value = model.resolveWorld(world.id)?.platforms.orEmpty()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Surface(shape = RoundedCornerShape(28.dp)) { WorldArt(world, Modifier.fillMaxWidth().height(210.dp)) }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(world.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("by ${world.author}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SuggestionChip(onClick = {}, label = { Text("${world.occupants} 人が滞在中") }, icon = { Icon(Icons.Outlined.People, null, Modifier.size(17.dp)) })
                SuggestionChip(onClick = {}, label = { Text("定員 ${world.capacity} 人") })
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                WorldPlatformBadges(platforms)
                if (WorldPlatform.Android in platforms && WorldPlatform.Ios !in platforms) {
                    Text("iOSではAndroid版を読み込める場合があります。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(world.description.ifBlank { "このワールドの説明はありません。" }, style = MaterialTheme.typography.bodyLarge)
            ExpandableDetails("${model.ownProfile.id}/world/${world.id}", listOf(DetailField("ワールドID", world.id)), model.demo,
                load = { force -> model.resolveWorldDetails(world, force) })
            FilledTonalButton(onClick = onSave, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(if (model.isWorldSaved(world.id)) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp)); Text(if (model.isWorldSaved(world.id)) "保存先を変更" else "このワールドを保存")
            }
            if (!model.demo) Button(onClick = { linkError = !openLink(context, Uri.parse("https://vrchat.com/home/world/${world.id}")) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("VRChatでワールドを開く"); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.OpenInNew, null, Modifier.size(18.dp))
            } else Text("デモ用の架空のワールドとイラストです。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (linkError) Text("リンクを開けるアプリが見つかりませんでした。", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun LoginSheet(model: OrbitViewModel) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val methods = model.twoFactorMethods
    var method by remember(methods) { mutableStateOf(methods.firstOrNull() ?: "totp") }
    val twoFactor = methods.isNotEmpty()
    val canSubmit = !model.authBusy && if (twoFactor) code.length >= 6 else username.isNotBlank() && password.isNotEmpty()
    val submit: () -> Unit = {
        if (canSubmit) {
            if (twoFactor) { model.verify(code.trim(), method); code = "" }
            else { model.login(username.trim(), password); password = "" }
        }
    }
    ModalBottomSheet(onDismissRequest = model::closeLogin, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !model.authBusy || it != SheetValue.Hidden }), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(if (twoFactor) Icons.Outlined.VerifiedUser else Icons.Rounded.AllInclusive, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
            Text(if (twoFactor) "もうひとつの、確認。" else "おかえりなさい。", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(if (twoFactor) "認証アプリまたはメールに届いたコードを入力してください。" else "VRChatアカウントでログインして、\nフレンドとワールドをもっと身近に。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!twoFactor) {
                OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), enabled = !model.authBusy, label = { Text("ユーザー名（表示名ではありません）") }, singleLine = true, shape = RoundedCornerShape(18.dp), keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next))
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), enabled = !model.authBusy, label = { Text("パスワード") }, singleLine = true, shape = RoundedCornerShape(18.dp),
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "パスワードを隠す" else "パスワードを表示") } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { submit() }))
            } else {
                if (methods.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    methods.forEach { option -> FilterChip(selected = method == option, onClick = { method = option }, enabled = !model.authBusy, label = { Text(when (option) { "emailOtp" -> "メール"; "otp" -> "リカバリー"; else -> "認証アプリ" }) }) }
                }
                OutlinedTextField(code, { code = it.filterNot(Char::isWhitespace).take(32) }, Modifier.fillMaxWidth(), enabled = !model.authBusy, label = { Text("認証コード") }, singleLine = true, shape = RoundedCornerShape(18.dp), keyboardOptions = KeyboardOptions(keyboardType = if (method == "otp") KeyboardType.Text else KeyboardType.Number, imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { submit() }))
            }
            model.authError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            Button(onClick = submit, enabled = canSubmit, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                if (model.authBusy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else { Text(if (twoFactor) "認証して続ける" else "ログイン"); Spacer(Modifier.width(8.dp)); Icon(Icons.Rounded.ArrowForward, null, Modifier.size(18.dp)) }
            }
            Text("VRChatに直接接続します。パスワードは保存されません。Steam / Meta連携のみの方は、VRChatのWebサイトでアカウントを確認してください。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
