package com.mczadvanced.wear

import android.app.Application
import android.content.Context

class StoveApp : Application() {
    companion object {
        fun get(context: Context): StoveApp = context.applicationContext as StoveApp
    }
}
