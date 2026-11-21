package com.yamato3010.vrchattools

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yamato3010.vrchattools.data.DemoData
import com.yamato3010.vrchattools.data.OrbitPreferences
import com.yamato3010.vrchattools.data.WorldFavoriteFolder
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WorldFavoriteFolderTest {
    @Test fun legacyFavoritesLoadInDefaultFolderAndFolderChangesPersistPerAccount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileName = "world_folders_test_${UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
        val world = DemoData.worlds.first()
        try {
            prefs.edit().putStringSet("worlds_account", setOf(world.id)).commit()
            val storage = OrbitPreferences(prefs)

            val unfiled = storage.loadWorldFolders("account").single()
            assertEquals("未分類", unfiled.name)
            assertEquals(setOf(world.id), unfiled.worldIds)

            val folders = listOf(
                unfiled.copy(worldIds = emptySet()),
                WorldFavoriteFolder("relax", "のんびり", setOf(world.id)),
                WorldFavoriteFolder("photo", "写真", emptySet())
            )
            storage.saveWorldFolders("account", folders, listOf(world))

            assertEquals(folders, OrbitPreferences(prefs).loadWorldFolders("account"))
            assertEquals(world.name, storage.loadWorldCache("account").getValue(world.id).name)
            assertEquals(emptySet<String>(), storage.loadWorldFolders("other").single().worldIds)
        } finally {
            context.deleteSharedPreferences(fileName)
        }
    }
}
