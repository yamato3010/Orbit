@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.yamato3010.vrchattools.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yamato3010.vrchattools.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private data class InstanceData(val world: World?, val details: InstanceDetails?, val loading: Boolean, val errors: List<String>, val retry: () -> Unit)

@Composable
private fun rememberInstanceData(group: FriendInstance, model: OrbitViewModel, focused: Boolean = false): InstanceData {
    val identity = "${model.ownProfile.id}/${group.address.key}"
    DisposableEffect(identity) {
        model.watchInstance(group.address.key)
        onDispose { model.unwatchInstance(group.address.key) }
    }
    var tick by remember(identity) { mutableIntStateOf(0) }
    var handledRetry by remember(identity) { mutableIntStateOf(0) }
    var retry by remember(identity) { mutableIntStateOf(0) }
    var world by remember(group.address.worldId, model.ownProfile.id, model.demo) { mutableStateOf(model.worlds.firstOrNull { it.id == group.address.worldId }) }
    var details by remember(identity, model.demo) { mutableStateOf<InstanceDetails?>(null) }
    var loading by remember(identity) { mutableStateOf(true) }
    var worldError by remember(identity) { mutableStateOf<String?>(null) }
    var instanceError by remember(identity) { mutableStateOf<String?>(null) }
    val interval = if (focused) 30_000L else 120_000L
    LaunchedEffect(identity, model.appForeground, model.demo, interval) {
        if (model.demo || !model.appForeground) return@LaunchedEffect
        while (true) { delay(interval); tick++ }
    }
    LaunchedEffect(identity, model.demo, model.appForeground, model.instanceVersions[group.address.key], tick, retry) {
        if (!model.appForeground && !model.demo) return@LaunchedEffect
        val force = retry != handledRetry
        handledRetry = retry
        loading = true; worldError = null; instanceError = null
        try {
            try {
                world = model.resolveWorld(group.address.worldId, force)
                if (world == null) worldError = instanceLoadError(InstanceResource.World, null)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                worldError = instanceLoadError(InstanceResource.World, e)
            }
            try {
                details = model.resolveInstance(group.address, force, interval)
                if (details == null) instanceError = instanceLoadError(InstanceResource.Instance, null)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                instanceError = instanceLoadError(InstanceResource.Instance, e)
            }
        } finally { loading = false }
    }
    return InstanceData(world, model.displayedInstances[group.address.key] ?: details, loading, listOfNotNull(worldError, instanceError)) { retry++ }
}

@Composable
private fun InstanceImage(world: World?, modifier: Modifier = Modifier) {
    if (world != null && (world.imageUrls.isNotEmpty() || world.id.startsWith("demo-"))) WorldArt(world, modifier)
    else Box(modifier.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Public, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun InstanceMetadata(group: FriendInstance, data: InstanceData) {
    val details = data.details
    data.world?.let { WorldPlatformBadges(it.platforms) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
            Text(details?.accessLabel ?: group.address.accessLabel, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text("#${group.address.number}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        (details?.region ?: group.address.region)?.let { region ->
            Text(region.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.People, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
        Text("フレンド ${group.friends.size} 人", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        if (details?.occupants != null) Text(
            "· 滞在 ${details.occupants} 人" + (details.capacity?.let { " / 定員 $it 人" } ?: ""),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun InstanceCard(group: FriendInstance, model: OrbitViewModel, onFriend: (Friend) -> Unit, onInstance: (FriendInstance) -> Unit) {
    val data = rememberInstanceData(group, model)
    val title = data.world?.name ?: group.friends.firstNotNullOfOrNull { it.worldName.takeIf(String::isNotBlank) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.clickable { onInstance(group) }) {
            InstanceImage(data.world, Modifier.fillMaxWidth().height(142.dp))
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title ?: if (data.loading) "ワールド情報を取得中…" else "ワールド情報を取得できません", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Rounded.ChevronRight, "インスタンス詳細", tint = MaterialTheme.colorScheme.primary)
                }
                InstanceMetadata(group, data)
            }
        }
        LazyRow(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(group.friends.take(6), key = { it.id }) { friend ->
                Column(Modifier.width(62.dp).clickable { onFriend(friend) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Avatar(friend, 52.dp)
                    Text(friend.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (group.friends.size > 6) item {
                TextButton(onClick = { onInstance(group) }) { Text("ほか ${group.friends.size - 6} 人") }
            }
        }
    }
}

/** 居場所が不明なユーザーは専用のセクションにまとめ、同じインスタンスにいるとはみなさない。 */
fun LazyListScope.instanceItems(overview: SocialOverview, model: OrbitViewModel, onFriend: (Friend) -> Unit, onInstance: (FriendInstance) -> Unit, includeOffline: Boolean = false) {
    items(overview.instances, key = { "instance-${it.address.key}" }) { group -> InstanceCard(group, model, onFriend, onInstance) }
    fun peopleSection(key: String, title: String, note: String, people: List<Friend>) {
        if (people.isEmpty()) return
        item(key = "section-$key") {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("$title · ${people.size} 人", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(people, key = { "$key-${it.id}" }) { friend -> FriendRow(friend, friend.id in model.savedFriends, { onFriend(friend) }, { model.toggleFriend(friend.id) }) }
    }
    peopleSection("private", "プライベート", "現在地は非公開です。同じインスタンスかは分かりません。", overview.privateFriends)
    peopleSection("traveling", "ワールドを移動中", "移動先が分かるとインスタンスごとに表示されます。", overview.travelingFriends)
    peopleSection("web", "Web / アプリ", "ワールドには滞在していません。", overview.webFriends)
    peopleSection("unknown", "現在地不明", "インスタンスを確認できないフレンドです。", overview.unknownFriends)
    if (includeOffline) peopleSection("offline", "オフライン", "また会えるのを楽しみに。", overview.offlineFriends)
}

@Composable
fun InstanceSheet(group: FriendInstance, model: OrbitViewModel, onDismiss: () -> Unit, onFriend: (Friend) -> Unit, onWorld: (World) -> Unit) {
    val data = rememberInstanceData(group, model, focused = true)
    val context = LocalContext.current
    var linkFailed by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Box(Modifier.clip(RoundedCornerShape(24.dp))) { InstanceImage(data.world, Modifier.fillMaxWidth().height(180.dp)) } }
            item {
                Text(data.world?.name ?: group.friends.firstOrNull()?.worldName?.takeIf(String::isNotBlank) ?: "ワールド", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { InstanceMetadata(group, data) }
            }
            if (data.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (data.errors.isNotEmpty() && !data.loading) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        data.errors.forEach { message -> Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    TextButton(onClick = data.retry) { Text("再試行") }
                }
            }
            if (!model.demo) item {
                Button(onClick = {
                    val uri = Uri.parse("https://vrchat.com/home/launch").buildUpon()
                        .appendQueryParameter("worldId", group.address.worldId).appendQueryParameter("instanceId", group.address.instanceId).build()
                    linkFailed = try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)); false } catch (_: ActivityNotFoundException) { true }
                }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Outlined.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("VRChatでインスタンスを開く")
                }
                Text("参加できるかどうかは、VRChat側の公開範囲・権限・満員状況によって決まります。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (model.demo) item { Text("サンプルのインスタンスです。人数はデモデータです。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (linkFailed) item { Text("リンクを開けるアプリが見つかりませんでした。", color = MaterialTheme.colorScheme.error) }
            data.world?.let { world -> item { OutlinedButton(onClick = { onWorld(world) }, Modifier.fillMaxWidth()) { Text("ワールドの詳細") } } }
            item {
                ExpandableDetails("${model.ownProfile.id}/instance/${group.address.key}",
                    listOf(DetailField("インスタンスID", group.address.instanceId), DetailField("ワールドID", group.address.worldId)) + data.details?.extra.orEmpty(), model.demo)
            }
            item { Text("ここにいるフレンド", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(group.friends, key = { it.id }) { friend -> FriendRow(friend, friend.id in model.savedFriends, { onFriend(friend) }, { model.toggleFriend(friend.id) }) }
        }
    }
}
