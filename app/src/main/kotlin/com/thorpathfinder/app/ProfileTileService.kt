package com.thorpathfinder.app

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.thorpathfinder.app.ui.ProfileChoiceActivity

/**
 * The Quick Settings tile: shows the profile in use and, when tapped, asks
 * which profile to switch to, Disabled included. It is the way back from
 * Disabled that doesn't need the buttons, which Pathfinder no longer reads
 * there. The tile is lit while Pathfinder is watching the buttons.
 */
class ProfileTileService : TileService() {

    // Kept in a field, since Android only holds a preference listener weakly.
    private val profilesChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> update() }

    override fun onStartListening() {
        Profiles.watch(this, profilesChanged)
        update()
    }

    override fun onStopListening() {
        Profiles.unwatch(this, profilesChanged)
    }

    override fun onClick() {
        if (isLocked) unlockAndRun(::ask) else ask()
    }

    private fun ask() {
        // Through the service, so the question lands on the top screen.
        if (PathfinderService.askProfileForTile()) return
        val intent = Intent(this, ProfileChoiceActivity::class.java)
            .putExtra(ProfileChoiceActivity.EXTRA_WITH_DISABLED, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // From Android 14 an app targeting it must hand over a PendingIntent.
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun update() {
        val tile = qsTile ?: return
        val active = Profiles.active(this)
        tile.label = getString(R.string.tile_label)
        tile.subtitle = active.name
        tile.state = if (Profiles.filtersKeys(active.id)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
