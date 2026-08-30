package io.github.mouse233.localsendkotlin.model

import android.graphics.drawable.Drawable

/** An installed application whose base APK can be shared. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val apkPath: String,
    val icon: Drawable?
)
