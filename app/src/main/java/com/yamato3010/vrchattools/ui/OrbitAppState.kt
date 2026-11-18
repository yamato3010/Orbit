package com.yamato3010.vrchattools.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.yamato3010.vrchattools.data.World

/** テーマの外側で状態を保持し、配色を変えても画面遷移の状態と設定画面のスクロール位置を維持する。 */
@Stable
class OrbitAppState(initialScroll: Int = 0) {
    var page by mutableIntStateOf(0)
    var settings by mutableStateOf(false)
    var notifications by mutableStateOf(false)
    var statusEditor by mutableStateOf(false)
    var friendId by mutableStateOf<String?>(null)
    var selectedWorld by mutableStateOf<World?>(null)
    var selectedInstance by mutableStateOf<String?>(null)
    val settingsScroll = ScrollState(initialScroll)

    companion object {
        val Saver = listSaver<OrbitAppState, Any>(
            save = { listOf(it.page, it.settings, it.friendId.orEmpty(), it.selectedInstance.orEmpty(), it.settingsScroll.value, it.notifications, it.statusEditor) },
            restore = { values ->
                OrbitAppState(values[4] as Int).apply {
                    page = (values[0] as Int).coerceIn(0, 3)
                    settings = values[1] as Boolean
                    notifications = values.getOrNull(5) as? Boolean ?: false
                    statusEditor = values.getOrNull(6) as? Boolean ?: false
                    friendId = (values[2] as String).ifBlank { null }
                    selectedInstance = (values[3] as String).ifBlank { null }
                }
            }
        )
    }
}

@Composable
fun rememberOrbitAppState(): OrbitAppState = rememberSaveable(saver = OrbitAppState.Saver) { OrbitAppState() }
