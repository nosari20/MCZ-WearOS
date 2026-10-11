package com.mczadvanced.wear.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mczadvanced.wear.protocol.StoveStateCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class SavedStove(val address: String, val name: String)

class SettingsStore(private val store: DataStore<Preferences>) {
    val stove: Flow<SavedStove?> = store.data.map { p ->
        p[ADDRESS]?.let { SavedStove(it, p[NAME] ?: it) }
    }

    suspend fun current(): SavedStove? = stove.first()

    suspend fun save(stove: SavedStove) {
        store.edit {
            it[ADDRESS] = stove.address
            it[NAME] = stove.name
        }
    }

    private companion object {
        val ADDRESS = stringPreferencesKey("stove_address")
        val NAME = stringPreferencesKey("stove_name")
    }
}

class DataStoreStoveCache(private val store: DataStore<Preferences>) : StoveCache {
    override suspend fun load(): CachedStove? {
        val p = store.data.first()
        val at = p[UPDATED_AT] ?: return null
        return CachedStove(StoveStateCodec.decode(p[STATE]), at)
    }

    override suspend fun save(value: CachedStove) {
        store.edit {
            it[STATE] = StoveStateCodec.encode(value.state)
            it[UPDATED_AT] = value.updatedAt
        }
    }

    private companion object {
        val STATE = stringPreferencesKey("cached_state")
        val UPDATED_AT = longPreferencesKey("cached_at")
    }
}
