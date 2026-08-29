package io.github.mouse233.localsendkotlin.quicksettings

/** Pure state rule for the Quick Settings tile, kept testable without Android services. */
internal object QuickSettingsTileState {
    fun shouldBeActive(serviceRunning: Boolean, appForeground: Boolean): Boolean =
        serviceRunning || appForeground
}
