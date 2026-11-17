@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.yamato3010.vrchattools.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.yamato3010.vrchattools.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Destination(val label: String, val icon: ImageVector) {
    Home("ホーム", Icons.Outlined.Home), Friends("フレンド", Icons.Outlined.People),
    Worlds("ワールド", Icons.Outlined.Public), Activity("ログ", Icons.Outlined.History)
}

@Composable
fun OrbitApp(model: OrbitViewModel) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var friendId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedWorld by remember { mutableStateOf<World?>(null) }
    var selectedInstance by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(model.error) { model.error?.let { snackbar.showSnackbar(it); model.dismissError() } }
    BackHandler(settings || page != 0) { if (settings) settings = false else page = 0 }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AllInclusive, null, Modifier.size(31.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Orbit", Modifier.padding(start = 9.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                Spacer(Modifier.weight(1f))
                if (model.demo) Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                    Text("DEMO", Modifier.padding(horizontal = 11.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { settings = !settings }, modifier = Modifier.background(if (settings) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, CircleShape)) {
                    Icon(if (settings) Icons.Rounded.Close else Icons.Outlined.Tune, if (settings) "設定を閉じる" else "設定")
                }
            }
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 10.dp, top = 6.dp), contentAlignment = Alignment.Center) {
                NavigationBar(Modifier.widthIn(max = 600.dp).clipShape(), containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0)) {
                    Destination.entries.forEachIndexed { index, destination ->
                        NavigationBarItem(selected = page == index && !settings, onClick = { page = index; settings = false },
                            icon = { Icon(destination.icon, null) }, label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer))
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            AnimatedContent(targetState = if (settings) 4 else page, modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                transitionSpec = { (fadeIn() + slideInHorizontally { it / 18 }) togetherWith fadeOut() }, label = "navigation") { current ->
                when (current) {
                    0 -> HomeScreen(model, onPage = { page = it }, onFriend = { friendId = it.id }, onWorld = { selectedWorld = it }, onInstance = { selectedInstance = it.address.key })
                    1 -> FriendsScreen(model, onFriend = { friendId = it.id }, onInstance = { selectedInstance = it.address.key })
                    2 -> WorldsScreen(model, onWorld = { selectedWorld = it })
                    3 -> ActivityScreen(model, onFriend = { friendId = it })
                    else -> SettingsScreen(model)
                }
            }
        }
    }
    model.friends.firstOrNull { it.id == friendId }?.let { friend ->
        FriendSheet(friend, model, onDismiss = { friendId = null }, onWorld = { friendId = null; selectedWorld = it })
    }
    selectedWorld?.let { world -> WorldSheet(world, model, onDismiss = { selectedWorld = null }) }
    groupFriendsByInstance(model.socialFriends).instances.firstOrNull { it.address.key == selectedInstance }?.let { instance ->
        InstanceSheet(instance, model, onDismiss = { selectedInstance = null },
            onFriend = { selectedInstance = null; friendId = it.id },
            onWorld = { selectedInstance = null; selectedWorld = it })
    }
    if (model.loginOpen) LoginSheet(model)
}

private fun Modifier.clipShape() = clip(RoundedCornerShape(30.dp))

@Composable
private fun HomeScreen(model: OrbitViewModel, onPage: (Int) -> Unit, onFriend: (Friend) -> Unit, onWorld: (World) -> Unit, onInstance: (FriendInstance) -> Unit) {
    val overview = groupFriendsByInstance(model.socialFriends)
    val online = model.friends.filter { it.status != Presence.Offline }
    LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("YOUR LITTLE VRCHAT COMPANION", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.7.sp)
                Text("さあ、会いにいこう。", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp)
                Text("いつもの友達と、まだ知らない世界へ。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.fillMaxWidth().padding(22.dp)) {
                    OrbitFlower(Modifier.align(Alignment.TopEnd).padding(top = 2.dp).size(112.dp))
                    Column(Modifier.fillMaxWidth(.68f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                            Text(if (model.demo) "フレンドのようす · サンプル" else "フレンドのようす", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${online.size}", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(" 人がオンライン", Modifier.padding(bottom = 10.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text(if (online.isEmpty()) "次の出会いを、楽しみに。" else "今日も、誰かが待っている。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { onPage(1) }, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp)) {
                            Text("フレンドを見る", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.ArrowForward, null, Modifier.size(17.dp))
                        }
                    }
                }
            }
        }
        if (model.demo) item {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(20.dp)).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("デモで雰囲気を体験中", Modifier.weight(1f).padding(start = 9.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                TextButton(onClick = model::openLogin) { Text("ログイン", color = MaterialTheme.colorScheme.onSecondaryContainer) }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("フレンドのいる場所", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    RefreshAction(model)
                }
                Text("${overview.instances.size} インスタンス · フレンドの多い順", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        instanceItems(overview, model, onFriend, onInstance)
        if (online.isEmpty()) item { EmptyState("みんなの場所を、ここに。", "フレンドがオンラインになると、インスタンスごとに表示されます。", Icons.Outlined.People) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("次は、どこへ？", "ワールド", { onPage(2) })
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(model.worlds.take(6), key = { it.id }) { world ->
                        WorldCard(world, world.id in model.savedWorlds, { onWorld(world) }, { model.toggleWorld(world) }, Modifier.width(228.dp))
                    }
                }
                if (model.worlds.isEmpty()) Text("ワールドは更新後に表示されます。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { SectionTitle("お気に入りのフレンド", "すべて", { onPage(1) }) }
        val favorites = model.friends.filter { it.id in model.savedFriends }.take(4)
        items(favorites, key = { "fav-${it.id}" }) { friend -> FriendRow(friend, true, { onFriend(friend) }, { model.toggleFriend(friend.id) }) }
        if (favorites.isEmpty()) item { Text("フレンドの ☆ を押すと、ここに表示されます。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun PageHeading(eyebrow: String, title: String, subtitle: String, action: @Composable () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.8.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            action()
        }
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchBox(query: String, onQuery: (String) -> Unit, hint: String, onSearch: (() -> Unit)? = null) {
    OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(), singleLine = true,
        shape = CircleShape, placeholder = { Text(hint, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "検索をクリア") } },
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer, focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }))
}

@Composable
private fun FilterChips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(labels.size) { index ->
            FilterChip(selected = selected == index, onClick = { onSelect(index) }, label = { Text(labels[index]) }, shape = CircleShape,
                leadingIcon = if (selected == index) ({ Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }) else null)
        }
    }
}

@Composable
private fun RefreshAction(model: OrbitViewModel) {
    IconButton(onClick = model::refresh, enabled = !model.busy) {
        if (model.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        else Icon(Icons.Rounded.Refresh, "最新の情報に更新")
    }
}

@Composable
private fun FriendsScreen(model: OrbitViewModel, onFriend: (Friend) -> Unit, onInstance: (FriendInstance) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var instanceView by rememberSaveable { mutableStateOf(false) }
    val filtered = filterFriends(model.socialFriends, query, filter, model.savedFriends)
    val overview = groupFriendsByInstance(filtered)
    LazyColumn(contentPadding = PaddingValues(24.dp, 12.dp, 24.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PageHeading("GOOD COMPANY", "フレンド", "つながりを、もう少し近くに。") { RefreshAction(model) } }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !instanceView, onClick = { instanceView = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("個人表示") }
                SegmentedButton(selected = instanceView, onClick = { instanceView = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("インスタンス別") }
            }
        }
        item { Spacer(Modifier.height(8.dp)); SearchBox(query, { query = it }, "名前・ワールドで検索") }
        item { FilterChips(listOf("すべて", "オンライン", "参加歓迎", "お気に入り"), filter) { filter = it } }
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${filtered.size} 人のフレンド", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (model.demo) Text("サンプルデータ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (instanceView) {
            item { Text("${overview.instances.size} インスタンス", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            instanceItems(overview, model, onFriend, onInstance, includeOffline = true)
        } else {
            items(filtered, key = { it.id }) { friend -> FriendRow(friend, friend.id in model.savedFriends, { onFriend(friend) }, { model.toggleFriend(friend.id) }) }
        }
        if (filtered.isEmpty()) item { EmptyState("フレンドが見つかりません", "検索やフィルターを変えてみてください。") }
    }
}

@Composable
private fun WorldsScreen(model: OrbitViewModel, onWorld: (World) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(query, model.demo) { delay(550); if (!model.demo && filter == 0) model.searchWorlds(query) }
    val source = if (filter == 1) model.bookmarkedWorlds() else model.worlds
    val worlds = source.filter { (!model.demo && filter == 0) || it.name.contains(query, true) || it.author.contains(query, true) }
    LazyColumn(contentPadding = PaddingValues(24.dp, 12.dp, 24.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("A WORLD OF POSSIBILITIES", "ワールド", "お気に入りの場所が、きっと見つかる。") }
        item { SearchBox(query, { query = it }, "ワールドを探す", { model.searchWorlds(query) }) }
        item { FilterChips(listOf("見つける", "保存したワールド"), filter) { filter = it; if (it == 0 && !model.demo) model.searchWorlds(query) } }
        item { Text(if (model.demo) "探索のヒント · 架空のサンプルワールド" else if (filter == 1) "端末に保存したワールド" else "VRChatのワールド", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(worlds.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { world -> WorldCard(world, world.id in model.savedWorlds, { onWorld(world) }, { model.toggleWorld(world) }, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (model.worldLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (worlds.isEmpty() && !model.worldLoading) item { EmptyState("ワールドが見つかりません", if (filter == 1) "気になるワールドの保存ボタンを押してみましょう。" else "キーワードを変えて探してみましょう。", Icons.Outlined.Public) }
    }
}

@Composable
private fun ActivityScreen(model: OrbitViewModel, onFriend: (String) -> Unit) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    val events = model.activities.filter { filter == 0 || if (filter == 1) it.kind in listOf("online", "offline") else it.kind == "world" }
    LazyColumn(contentPadding = PaddingValues(24.dp, 12.dp, 24.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeading("LITTLE MOMENTS", "アクティビティ", "みんなの動きを、そっと振り返る。") { RefreshAction(model) } }
        item {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                    Text(if (model.demo) "これはサンプルのログです。ログインすると、更新時に検出した変化を端末に記録します。" else "更新時に検出したフレンドの変化を記録します。アプリを閉じている間の履歴は取得しません。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        item { FilterChips(listOf("すべて", "オンライン状況", "ワールド移動"), filter) { filter = it } }
        items(events, key = { it.id }) { event ->
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(22.dp)).clickable(enabled = model.friends.any { it.id == event.friendId }) { onFriend(event.friendId) }.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(42.dp).background(if (event.kind == "world") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                    Icon(if (event.kind == "world") Icons.Outlined.Public else if (event.kind == "offline") Icons.Outlined.Bedtime else Icons.Outlined.WavingHand, null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(event.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(event.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(relativeTime(event.time, now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (events.isEmpty()) item { EmptyState("まだログはありません", "フレンド情報を更新すると、前回からの変化がここに表示されます。", Icons.Outlined.History) }
    }
}

@Composable
private fun SettingsScreen(model: OrbitViewModel) {
    var confirmLogout by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp, 12.dp, 24.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        PageHeading("MAKE IT YOURS", "あなたのOrbit", "好きな色で、心地よいつながりを。")
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.AccountCircle, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                Text(if (model.demo) "まだ見ぬ出会いへ。" else model.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (model.demo) "VRChatアカウントをつないで、\nいつものフレンドをここに。" else "VRChatと接続しています", style = MaterialTheme.typography.bodyMedium)
                if (model.demo) Button(onClick = model::openLogin, enabled = !model.authBusy) { Text(if (model.authBusy) "接続を確認中…" else "VRChatにログイン"); Spacer(Modifier.width(8.dp)); Icon(Icons.Rounded.ArrowForward, null, Modifier.size(18.dp)) }
                else OutlinedButton(onClick = { confirmLogout = true }, enabled = !model.authBusy) { Text("ログアウト") }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("見た目", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Palette, null); Text("テーマ", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleSmall) }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("システム", "ライト", "ダーク").forEachIndexed { index, title ->
                            SegmentedButton(selected = model.themeMode == index, onClick = { model.setTheme(index) }, shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(title, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                    if (android.os.Build.VERSION.SDK_INT >= 31) Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("壁紙の色を使う", style = MaterialTheme.typography.titleSmall); Text("Androidのダイナミックカラー", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Switch(model.dynamicColor, model::setDynamic)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("データとプライバシー", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("パスワードは保存しません。認証用CookieはAndroid Keystoreで暗号化して端末に保存します。お気に入りとログはこの端末内だけに保存されます。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("お気に入りはOrbit専用です。VRChat側のお気に入りとは同期されません。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Orbit  /  0.1.0\nVRChatのための、小さなコンパニオン。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("VRChatとは独立した非公式アプリです。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false }, title = { Text("ログアウトしますか？") }, text = { Text("認証情報を端末から削除し、デモモードに戻ります。保存したお気に入りとログはこの端末に残ります。") }, confirmButton = { TextButton(onClick = { confirmLogout = false; model.logout() }) { Text("ログアウト") } }, dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("キャンセル") } })
}
