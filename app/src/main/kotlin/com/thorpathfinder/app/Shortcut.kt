package com.thorpathfinder.app

/**
 * One shortcut: an action and whichever of its choices it uses. A button's
 * gesture stores one ([Shortcuts.shortcut]), and the Shortcut menu builds one
 * to run once without storing it anywhere.
 */
data class Shortcut(
    val action: ButtonAction,
    /** The app an "Open an app" shortcut opens; with [second], the top screen's. */
    val app: String? = null,
    val screen: LaunchScreen = LaunchScreen.TOP,
    /** With two apps, the bottom screen's. */
    val second: String? = null,
    /** The screens a Home shortcut sends home; null for Android's own Home. */
    val home: HomeTarget? = null,
    val close: CloseTarget = CloseTarget.ALL,
    val closeApps: Set<String> = emptySet(),
    val profile: ProfileSwitch = ProfileSwitch.CYCLE,
    val profileId: Int = Profiles.ORIGINAL,
    val focus: FocusSwitch = FocusSwitch.CYCLE,
)
