package com.mczadvanced.wear.tile

import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.LayoutString
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.mczadvanced.wear.StoveApp
import com.mczadvanced.wear.data.LinkStatus
import com.mczadvanced.wear.data.StoveUiState
import com.mczadvanced.wear.formatAge
import com.mczadvanced.wear.formatTemp
import com.mczadvanced.wear.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Cached stove status; refreshes over BLE when shown or tapped. Power goes through the app's confirm screen. */
class StoveTileService : TileService() {

    override fun onRecentInteractionEventsAsync(
        events: List<EventBuilders.TileInteractionEvent>,
    ): ListenableFuture<Void?> = SuspendToFutureAdapter.launchFuture(Dispatchers.Main) {
        if (events.any { it.eventType == EventBuilders.TileInteractionEvent.ENTER }) refreshInBackground()
        null
    }

    /** Older Wear OS versions only deliver the legacy enter event. */
    @Deprecated("Legacy event, kept for Wear OS versions without interaction events")
    override fun onTileEnterEvent(requestParams: EventBuilders.TileEnterEvent) {
        refreshInBackground()
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.Main) {
            val repo = StoveApp.get(this@StoveTileService).repository
            repo.loadCache()
            if (requestParams.currentState.lastClickableId == ID_REFRESH) refreshInBackground()
            val ui = repo.state.value

            val confirmPower = ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(packageName)
                        .setClassName(MainActivity::class.java.name)
                        .addKeyToExtraMapping(
                            MainActivity.EXTRA_CONFIRM_POWER,
                            ActionBuilders.AndroidBooleanExtra.Builder().setValue(true).build(),
                        )
                        .build(),
                )
                .build()
            val refresh = ActionBuilders.LoadAction.Builder().build()

            val layout = materialScope(this@StoveTileService, requestParams.deviceConfiguration) {
                primaryLayout(
                    titleSlot = { text(LayoutString("Stove · ${ui.stove.stateLabel}")) },
                    mainSlot = {
                        LayoutElementBuilders.Column.Builder()
                            .setModifiers(
                                ModifiersBuilders.Modifiers.Builder()
                                    .setClickable(clickable(refresh, ID_REFRESH))
                                    .build(),
                            )
                            .addContent(text(LayoutString(formatTemp(ui.stove.roomTemp)), typography = Typography.DISPLAY_MEDIUM))
                            .addContent(text(LayoutString("Set ${formatTemp(ui.stove.setpoint)}"), typography = Typography.BODY_MEDIUM))
                            .addContent(text(LayoutString(statusLine(ui)), typography = Typography.BODY_SMALL))
                            .build()
                    },
                    bottomSlot = {
                        textEdgeButton(onClick = clickable(confirmPower, ID_POWER)) {
                            text(LayoutString(if (ui.stove.isOn == true) "Turn off" else "Turn on"))
                        }
                    },
                )
            }

            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
                .build()
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.Main) {
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
        }

    private fun refreshInBackground() {
        val app = StoveApp.get(this)
        app.appScope.launch {
            app.repository.refresh()
            TileService.getUpdater(app).requestUpdate(StoveTileService::class.java)
        }
    }

    private fun statusLine(ui: StoveUiState): String = when {
        ui.busy -> "Updating…"
        ui.link == LinkStatus.UNREACHABLE -> "Unreachable · ${formatAge(ui.updatedAt, System.currentTimeMillis())}"
        else -> formatAge(ui.updatedAt, System.currentTimeMillis())
    }

    private companion object {
        const val RESOURCES_VERSION = "1"
        const val ID_REFRESH = "refresh"
        const val ID_POWER = "power"
    }
}
