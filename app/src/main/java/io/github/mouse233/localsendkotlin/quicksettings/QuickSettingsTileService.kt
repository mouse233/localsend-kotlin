package io.github.mouse233.localsendkotlin.quicksettings

import android.content.Context
import android.content.ComponentName
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import io.github.mouse233.localsendkotlin.R
import io.github.mouse233.localsendkotlin.ForegroundActivityState
import io.github.mouse233.localsendkotlin.transfer.TransferServiceState

/** Toggles the LocalSend background service from the Android Quick Settings panel. */
@RequiresApi(Build.VERSION_CODES.N)
class QuickSettingsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        if (TransferServiceState.isRunning()) {
            TransferServiceState.stop(this)
        } else {
            TransferServiceState.start(this)
        }
        updateTileState()
    }

    companion object {
        @RequiresApi(Build.VERSION_CODES.N)
        internal fun requestTileUpdate(context: Context) {
            TileService.requestListeningState(
                context.applicationContext,
                ComponentName(context, QuickSettingsTileService::class.java)
            )
        }
    }

    private fun updateTileState() {
        qsTile?.apply {
            label = getString(R.string.quick_settings_tile_label)
            state = if (QuickSettingsTileState.shouldBeActive(TransferServiceState.isRunning(), ForegroundActivityState.isForeground())) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
            updateTile()
        }
    }
}
