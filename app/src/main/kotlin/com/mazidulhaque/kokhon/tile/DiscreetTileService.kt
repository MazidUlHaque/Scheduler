package com.mazidulhaque.kokhon.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mazidulhaque.kokhon.KokhonApplication
import com.mazidulhaque.kokhon.R
import com.mazidulhaque.kokhon.core.designsystem.format.TimeFormat
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Duration

/** A Quick Settings tile: one tap makes alarms vibrate only for an hour. Useful when walking into a meeting. */
class DiscreetTileService : TileService() {
    private val scope = MainScope()
    private val app get() = application as KokhonApplication

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refresh() }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val now = app.clock.instant()
            val on = app.settingsRepository.current().isDiscreetAt(now)
            app.settingsRepository.setDiscreetUntil(if (on) null else now.plus(Duration.ofHours(1)))
            refresh()
        }
    }

    private suspend fun refresh() {
        val tile = qsTile ?: return
        val settings = app.settingsRepository.current()
        val on = settings.isDiscreetAt(app.clock.instant())
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.discreet_mode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle =
                if (on) getString(R.string.tile_until, TimeFormat.time(this, settings.discreetUntil!!)) else getString(R.string.tile_off)
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
