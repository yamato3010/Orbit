@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.yamato3010.vrchattools.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.yamato3010.vrchattools.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Destination(val label: String, val icon: ImageVector) {
    Home("ホーム", Icons.Rounded.Home), Friends("フレンド", Icons.Rounded.People),
    Worlds("ワールド", Icons.Rounded.Public), Activity("ログ", Icons.Rounded.History)
}

@Composable
fun OrbitApp(model: OrbitViewModel, appState: OrbitAppState) {
    var notifications by appState::notifications
    var statusEditor by appState::statusEditor
    var page by appState::page
    var settings by appState::settings
    var friendId by appState::friendId
    var selectedWorld by appState::selectedWorld
    var selectedInstance by appState::selectedInstance
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(model.error) { model.error?.let { snackbar.showSnackbar(it); model.dismissError() } }
    LaunchedEffect(model.socialMessage) { model.socialMessage?.let { snackbar.showSnackbar(it); model.dismissSocialMessage() } }
    BackHandler(settings || notifications || page != 0) { if (notifications) notifications = false else if (settings) settings = false else page = 0 }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Rounded.AllInclusive, null, Modifier.padding(8.dp).size(24.dp))
                }
                Text("Orbit", Modifier.padding(start = 9.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { statusEditor = true }) { Icon(Icons.Outlined.AccountCircle, "自分のステータス") }
                IconButton(onClick = { notifications = !notifications; settings = false }) {
                    BadgedBox(badge = { if (model.unreadCount > 0) Badge { Text(model.unreadCount.toString()) } }) { Icon(Icons.Outlined.Notifications, "通知") }
                }
                IconButton(onClick = { settings = !settings; notifications = false }, modifier = Modifier.background(if (settings) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, CircleShape)) {
                    Icon(if (settings) Icons.Rounded.Close else Icons.Outlined.Tune, if (settings) "設定を閉じる" else "設定")
                }
            }
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 10.dp, top = 6.dp), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(36.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0)) {
                        Destination.entries.forEachIndexed { index, destination ->
                            NavigationBarItem(selected = page == index && !settings && !notifications, onClick = { page = index; settings = false; notifications = false },
                                icon = { Icon(destination.icon, null) },
                                label = { Text(destination.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        val direction = LocalLayoutDirection.current
        val bottomInset = padding.calculateBottomPadding()
        // ナビゲーションバーは表示領域に重ね、下部の余白はスクロールするコンテンツにだけ入れる。
        Box(Modifier.fillMaxSize().padding(start = padding.calculateStartPadding(direction),
            top = padding.calculateTopPadding(), end = padding.calculateEndPadding(direction))
            .consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            AnimatedContent(targetState = if (notifications) 5 else if (settings) 4 else page, modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                transitionSpec = { (fadeIn() + slideInHorizontally { it / 18 }) togetherWith fadeOut() }, label = "navigation") { current ->
                when (current) {
                    0 -> HomeScreen(model, onPage = { page = it }, onFriend = { friendId = it.id }, onWorld = { selectedWorld = it }, onInstance = { selectedInstance = it.address.key }, bottomInset = bottomInset)
                    1 -> FriendsScreen(model, onFriend = { friendId = it.id }, onInstance = { selectedInstance = it.address.key }, bottomInset = bottomInset)
                    2 -> WorldsScreen(model, onWorld = { selectedWorld = it }, bottomInset = bottomInset)
                    3 -> ActivityScreen(model, onFriend = { friendId = it }, bottomInset = bottomInset)
                    5 -> NotificationsScreen(model, bottomInset)
                    else -> SettingsScreen(model, appState.settingsScroll, bottomInset)
                }
            }
        }
    }
    model.socialFriends.firstOrNull { it.id == friendId }?.let { friend ->
        FriendSheet(friend, model, onDismiss = { friendId = null }, onWorld = { friendId = null; selectedWorld = it })
    }
    selectedWorld?.let { world -> WorldSheet(world, model, onDismiss = { selectedWorld = null }) }
    groupFriendsByInstance(model.socialFriends).instances.firstOrNull { it.address.key == selectedInstance }?.let { instance ->
        InstanceSheet(instance, model, onDismiss = { selectedInstance = null },
            onFriend = { selectedInstance = null; friendId = it.id },
            onWorld = { selectedInstance = null; selectedWorld = it })
    }
    if (statusEditor) StatusSheet(model) { statusEditor = false }
    if (model.loginOpen) LoginSheet(model)
}

@Composable
private fun HomeScreen(model: OrbitViewModel, onPage: (Int) -> Unit, onFriend: (Friend) -> Unit, onWorld: (World) -> Unit, onInstance: (FriendInstance) -> Unit, bottomInset: Dp) {
    val overview = groupFriendsByInstance(model.socialFriends)
    val online = model.friends.filter { it.status != Presence.Offline }
    RefreshableLazyColumn(
        isRefreshing = model.busy,
        onRefresh = model::refresh,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 20.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Text("ダッシュボード", style = MaterialTheme.typography.headlineLarge)
        }
        item {
            FriendOverview(
                onlineCount = online.size,
                largestGathering = overview.instances.firstOrNull(),
                onFriends = { onPage(1) },
                onInstance = onInstance
            )
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
                Text("${overview.instances.size} インスタンス · ${model.realtimeState.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun FriendOverview(
    onlineCount: Int,
    largestGathering: FriendInstance?,
    onFriends: () -> Unit,
    onInstance: (FriendInstance) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val gatheringCount = largestGathering?.friends?.size ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("フレンドのようす", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(28.dp, 12.dp, 12.dp, 28.dp),
                color = colors.primaryContainer
            ) {
                Column(Modifier.padding(12.dp)) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.CenterStart) {
                        Icon(Icons.Rounded.Person, null)
                    }
                    Text(onlineCount.toString(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold)
                    Text("人がオンライン", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = onFriends,
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.onPrimaryContainer),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text("フレンドを見る")
                    }
                }
            }
            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(12.dp, 28.dp, 28.dp, 12.dp),
                color = colors.tertiaryContainer
            ) {
                Column(Modifier.padding(12.dp)) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.CenterStart) {
                        Icon(Icons.Rounded.People, null)
                    }
                    if (largestGathering == null) {
                        Text("集まっている場所はまだありません", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(gatheringCount.toString(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold)
                        Text(
                            if (gatheringCount == 1) "人のフレンドがいます" else "人のフレンドが集まっています！",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { onInstance(largestGathering) },
                            colors = ButtonDefaults.textButtonColors(contentColor = colors.onTertiaryContainer),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text("場所を見る")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageHeading(title: String, action: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge)
        action()
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
            val isSelected = selected == index
            val corner by animateDpAsState(
                targetValue = if (isSelected) 12.dp else 24.dp,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "filter corner"
            )
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(index) },
                label = { Text(labels[index]) },
                shape = RoundedCornerShape(corner),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                leadingIcon = if (isSelected) ({ Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }) else null
            )
        }
    }
}

@Composable
private fun RefreshAction(model: OrbitViewModel) {
    IconButton(onClick = model::refresh, enabled = !model.busy) {
        if (model.busy) LoadingIndicator(Modifier.size(28.dp))
        else Icon(Icons.Rounded.Refresh, "最新の情報に更新")
    }
}

@Composable
private fun FriendsScreen(model: OrbitViewModel, onFriend: (Friend) -> Unit, onInstance: (FriendInstance) -> Unit, bottomInset: Dp) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var instanceView by rememberSaveable { mutableStateOf(false) }
    val filtered = filterFriends(model.socialFriends, query, filter, model.savedFriends)
    val overview = groupFriendsByInstance(filtered)
    RefreshableLazyColumn(
        isRefreshing = model.busy,
        onRefresh = model::refresh,
        contentPadding = PaddingValues(24.dp, 12.dp, 24.dp, 24.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { PageHeading("フレンド") { RefreshAction(model) } }
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
            items(filtered, key = { it.id }) { friend ->
                LaunchedEffect(friend.id, model.ownProfile.id, model.profileRefresh) { model.enrichFriendProfile(friend.id) }
                FriendRow(friend, friend.id in model.savedFriends, { onFriend(friend) }, { model.toggleFriend(friend.id) }, showTrustRank = true)
            }
        }
        if (filtered.isEmpty()) item { EmptyState("フレンドが見つかりません", "検索やフィルターを変えてみてください。") }
    }
}

private enum class WorldsTab(val label: String) {
    Discover("見つける"),
    Saved("保存したワールド");

    fun usesRemoteSearch(demo: Boolean) = this == Discover && !demo
}

@Composable
private fun WorldsScreen(model: OrbitViewModel, onWorld: (World) -> Unit, bottomInset: Dp) {
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(WorldsTab.Discover) }
    val useRemoteSearch = tab.usesRemoteSearch(model.demo)

    fun searchRemote() {
        if (tab.usesRemoteSearch(model.demo)) model.searchWorlds(query)
    }

    LaunchedEffect(query, model.demo) {
        delay(550)
        searchRemote()
    }
    val source = if (tab == WorldsTab.Saved) model.bookmarkedWorlds() else model.worlds
    val worlds = if (useRemoteSearch) source else source.filter {
        it.name.contains(query, ignoreCase = true) || it.author.contains(query, ignoreCase = true)
    }
    LazyColumn(contentPadding = PaddingValues(24.dp, 12.dp, 24.dp, 24.dp + bottomInset), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("ワールド") }
        item { SearchBox(query, { query = it }, "ワールドを探す", ::searchRemote) }
        item {
            FilterChips(WorldsTab.entries.map { it.label }, tab.ordinal) { index ->
                tab = WorldsTab.entries[index]
                searchRemote()
            }
        }
        item { Text(if (model.demo) "探索のヒント · 架空のサンプルワールド" else if (tab == WorldsTab.Saved) "端末に保存したワールド" else "VRChatのワールド", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(worlds.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { world -> WorldCard(world, world.id in model.savedWorlds, { onWorld(world) }, { model.toggleWorld(world) }, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (model.worldLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (worlds.isEmpty() && !model.worldLoading) item { EmptyState("ワールドが見つかりません", if (tab == WorldsTab.Saved) "気になるワールドの保存ボタンを押してみましょう。" else "キーワードを変えて探してみましょう。", Icons.Outlined.Public) }
    }
}

@Composable
private fun ActivityScreen(model: OrbitViewModel, onFriend: (String) -> Unit, bottomInset: Dp) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    val events = model.activities.filter { filter == 0 || if (filter == 1) it.kind in listOf("online", "offline") else it.kind == "world" }
    RefreshableLazyColumn(
        isRefreshing = model.busy,
        onRefresh = model::refresh,
        contentPadding = PaddingValues(24.dp, 12.dp, 24.dp, 24.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PageHeading("アクティビティ") { RefreshAction(model) } }
        item {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                    Text(if (model.demo) "これはサンプルのログです。ログインすると、更新時に検出した変化を端末に記録します。" else "更新時に検出したフレンドの変化を記録します。${model.realtimeState.label}。アプリを閉じている間の履歴は取得しません。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
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
        if (events.isEmpty()) item { EmptyState("まだログはありません", "フレンド情報を更新すると、前回からの変化がここに表示されます。", Icons.Rounded.History) }
    }
}

@Composable
private fun SettingsScreen(model: OrbitViewModel, scrollState: androidx.compose.foundation.ScrollState, bottomInset: Dp) {
    var confirmLogout by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp, 12.dp, 24.dp, 24.dp + bottomInset), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        PageHeading("あなたのOrbit")
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.AccountCircle, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                Text(if (model.demo) "まだ見ぬ出会いへ。" else model.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (model.demo) "VRChatアカウントをつないで、\nいつものフレンドをここに。" else "VRChatと接続しています", style = MaterialTheme.typography.bodyMedium)
                if (model.demo) Button(onClick = model::openLogin, enabled = !model.authBusy) { Text(if (model.authBusy) "接続を確認中…" else "VRChatにログイン"); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp)) }
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("壁紙の色を使う", style = MaterialTheme.typography.titleSmall)
                            Text(if (android.os.Build.VERSION.SDK_INT >= 31) "Material You · 壁紙に合わせた配色" else "Material YouはAndroid 12以降で利用できます", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = model.dynamicColor, onCheckedChange = model::setDynamic,
                            enabled = android.os.Build.VERSION.SDK_INT >= 31, modifier = Modifier.testTag("dynamic_color_switch"))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("現在の配色", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val colors = MaterialTheme.colorScheme
                        listOf("メイン" to colors.primary, "サブ" to colors.secondary, "アクセント" to colors.tertiary).forEach { (label, color) ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.fillMaxWidth().height(30.dp).background(color, RoundedCornerShape(12.dp)))
                                Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("リアルタイム更新", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(model.realtimeState.label); Text("アプリを表示している間に自動更新します。", style = MaterialTheme.typography.bodySmall) }
                Switch(checked = model.realtimeEnabled, onCheckedChange = model::setRealtime)
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
