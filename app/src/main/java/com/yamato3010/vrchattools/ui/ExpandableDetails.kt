package com.yamato3010.vrchattools.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.yamato3010.vrchattools.data.ApiException
import com.yamato3010.vrchattools.data.DetailField
import kotlinx.coroutines.CancellationException

@Composable
fun ExpandableDetails(resourceKey: String, initial: List<DetailField>, demo: Boolean, load: (suspend () -> List<DetailField>)? = null) {
    var expanded by rememberSaveable(resourceKey) { mutableStateOf(false) }
    var loaded by remember(resourceKey) { mutableStateOf<List<DetailField>?>(null) }
    var loading by remember(resourceKey) { mutableStateOf(false) }
    var error by remember(resourceKey) { mutableStateOf<String?>(null) }
    var requested by remember(resourceKey) { mutableStateOf(expanded) }
    var attempt by remember(resourceKey) { mutableIntStateOf(0) }
    LaunchedEffect(resourceKey, requested, attempt) {
        if (!requested || loaded != null || load == null) return@LaunchedEffect
        loading = true; error = null
        try { loaded = load() }
        catch (e: Exception) {
            if (e is CancellationException) throw e
            error = (e as? ApiException)?.message ?: "追加情報を取得できませんでした。接続を確認して再試行してください。"
        } finally { loading = false }
    }
    val fields = (initial + loaded.orEmpty()).associateBy { it.label }.values.toList()
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "detail chevron")
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(Modifier.fillMaxWidth().semantics { stateDescription = if (expanded) "展開中" else "折りたたみ中" }
                .clickable(role = Role.Button, onClickLabel = if (expanded) "詳しい情報を閉じる" else "詳しい情報を開く") { expanded = !expanded; if (expanded) requested = true }
                .padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("詳しい情報", style = MaterialTheme.typography.titleSmall)
                    Text("取得できた追加情報", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(rotation))
            }
            AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HorizontalDivider()
                    if (demo) Text("デモの詳細情報です。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { message ->
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { attempt++ }) { Text("追加情報を再試行") }
                    }
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            fields.forEach { field -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(field.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(field.value, style = MaterialTheme.typography.bodyMedium)
                            } }
                        }
                    }
                    if (fields.isEmpty() && !loading && error == null) Text("追加情報はありません。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
