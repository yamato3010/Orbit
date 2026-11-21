package com.yamato3010.vrchattools.data

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal class OrbitPreferences(private val prefs: SharedPreferences) {
    fun isProvisioned() = prefs.getBoolean("provisioned", false)

    fun markProvisioned() {
        prefs.edit().putBoolean("provisioned", true).apply()
    }

    fun loadPresets(accountId: String): List<StatusPreset> = runCatching {
        val stored = prefs.getString("presets_$accountId", null) ?: return defaultStatusPresets
        val array = JSONArray(stored)
        List(array.length()) { index ->
            val preset = array.getJSONObject(index)
            StatusPreset(
                preset.getString("id"),
                preset.getString("name"),
                SocialStatus.from(preset.getString("status")),
                preset.getString("description")
            )
        }
    }.getOrDefault(defaultStatusPresets)

    fun savePresets(accountId: String, presets: List<StatusPreset>) {
        val array = JSONArray(presets.map { preset ->
            JSONObject()
                .put("id", preset.id)
                .put("name", preset.name)
                .put("status", preset.status.apiValue)
                .put("description", preset.description)
        })
        prefs.edit().putString("presets_$accountId", array.toString()).apply()
    }

    fun loadSavedFriends(accountId: String): Set<String> =
        prefs.getStringSet("friends_$accountId", if (accountId == "demo") setOf("demo-1", "demo-3") else emptySet()).orEmpty().toSet()

    fun loadWorldFolders(accountId: String): List<WorldFavoriteFolder> = runCatching {
        val stored = prefs.getString("world_folders_$accountId", null)
            ?: return listOf(defaultWorldFolder(loadSavedWorlds(accountId)))
        val array = JSONArray(stored)
        List(array.length()) { index ->
            val folder = array.getJSONObject(index)
            val ids = folder.getJSONArray("worldIds")
            WorldFavoriteFolder(folder.getString("id"), folder.getString("name"),
                (0 until ids.length()).map { ids.getString(it) }.toSet())
        }.ifEmpty { listOf(defaultWorldFolder(loadSavedWorlds(accountId))) }
    }.getOrElse { listOf(defaultWorldFolder(loadSavedWorlds(accountId))) }

    private fun loadSavedWorlds(accountId: String): Set<String> =
        prefs.getStringSet("worlds_$accountId", if (accountId == "demo") setOf("demo-world-1", "demo-world-3") else emptySet()).orEmpty().toSet()

    fun saveFriends(accountId: String, ids: Set<String>) {
        prefs.edit().putStringSet("friends_$accountId", ids).apply()
    }

    fun loadWorldCache(accountId: String): Map<String, World> = buildMap {
        runCatching {
            val cached = JSONArray(prefs.getString("world_cache_$accountId", "[]"))
            for (index in 0 until cached.length()) {
                val world = cached.getJSONObject(index).toWorld()
                put(world.id, world)
            }
        }
    }

    fun saveWorldFolders(accountId: String, folders: List<WorldFavoriteFolder>, cachedWorlds: Collection<World>) {
        val storedFolders = JSONArray(folders.map { folder ->
            JSONObject()
                .put("id", folder.id)
                .put("name", folder.name)
                .put("worldIds", JSONArray(folder.worldIds.toList()))
        })
        val array = JSONArray(cachedWorlds.map { world ->
            JSONObject()
                .put("id", world.id)
                .put("name", world.name)
                .put("authorName", world.author)
                .put("occupants", world.occupants)
                .put("capacity", world.capacity)
                .put("thumbnailImageUrl", world.image)
                .put("imageUrl", world.imageFallbacks.firstOrNull().orEmpty())
                .put("description", world.description)
                .put("unityPackages", JSONArray(world.platforms.map { JSONObject().put("platform", it.apiName) }))
        })
        prefs.edit()
            .putString("world_folders_$accountId", storedFolders.toString())
            .putStringSet("worlds_$accountId", folders.flatMap { it.worldIds }.toSet())
            .putString("world_cache_$accountId", array.toString())
            .apply()
    }
}
