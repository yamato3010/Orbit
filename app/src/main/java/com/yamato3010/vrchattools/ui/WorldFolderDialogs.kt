package com.yamato3010.vrchattools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yamato3010.vrchattools.data.OrbitViewModel
import com.yamato3010.vrchattools.data.World
import com.yamato3010.vrchattools.data.WorldFavoriteFolder

@Composable
fun WorldFolderPicker(world: World, model: OrbitViewModel, onDismiss: () -> Unit) {
    val folders = model.worldFolders
    val currentFolder = folders.firstOrNull { world.id in it.worldIds }
    var selectedId by rememberSaveable(world.id) { mutableStateOf<String?>(currentFolder?.id ?: folders.first().id) }
    var creating by rememberSaveable(world.id) { mutableStateOf(false) }

    if (creating) {
        CreateWorldFolderDialog(model, onDismiss = { creating = false }) { folder ->
            selectedId = folder.id
            creating = false
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("保存先フォルダ") },
            text = {
                Column {
                    Text(world.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    LazyColumn(Modifier.heightIn(max = 240.dp).selectableGroup()) {
                        items(folders, key = { it.id }) { folder ->
                            WorldFolderOption(folder.name, folder.id == selectedId) { selectedId = folder.id }
                        }
                        if (currentFolder != null) item {
                            WorldFolderOption("お気に入りから解除", selectedId == null) { selectedId = null }
                        }
                    }
                    TextButton(onClick = { creating = true }) { Text("フォルダを作成") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    model.setWorldFolder(world, selectedId)
                    onDismiss()
                }) { Text(if (selectedId == null) "解除" else "保存") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
        )
    }
}

@Composable
private fun WorldFolderOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CreateWorldFolderDialog(
    model: OrbitViewModel,
    onDismiss: () -> Unit,
    onCreated: (WorldFavoriteFolder) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    val trimmed = name.trim()
    val duplicate = model.worldFolders.any { it.name.equals(trimmed, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("フォルダを作成") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("フォルダ名") },
                singleLine = true,
                isError = duplicate,
                supportingText = if (duplicate) ({ Text("同じ名前のフォルダがあります。") }) else null
            )
        },
        confirmButton = {
            TextButton(
                enabled = trimmed.isNotEmpty() && !duplicate,
                onClick = { model.createWorldFolder(name)?.let(onCreated) }
            ) { Text("作成") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}
