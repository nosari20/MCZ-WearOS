package com.mczadvanced.wear

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.datastore.preferences.preferencesDataStore
import com.mczadvanced.wear.data.DataStoreStoveCache
import com.mczadvanced.wear.data.DemoStoveTransport
import com.mczadvanced.wear.data.SettingsStore
import com.mczadvanced.wear.data.StoveRepository
import com.mczadvanced.wear.data.StoveTransport
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.dataStore by preferencesDataStore("mcz")

class StoveApp : Application() {
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, e -> Log.e(TAG, "Background task failed", e) },
    )

    val settings by lazy { SettingsStore(dataStore) }

    val repository by lazy {
        StoveRepository(
            transportFactory = { settings.current()?.let { createTransport(it.address) } },
            cache = DataStoreStoveCache(dataStore),
            scope = appScope,
        )
    }

    private fun createTransport(address: String): StoveTransport = when (address) {
        DemoStoveTransport.ADDRESS -> DemoStoveTransport()
        else -> DemoStoveTransport() // replaced by StoveBleClient in Task 8
    }

    companion object {
        private const val TAG = "StoveApp"

        fun get(context: Context): StoveApp = context.applicationContext as StoveApp
    }
}
