package io.github.mouse233.localsendkotlin

import android.app.Application
import android.app.Activity
import android.os.Build
import io.github.mouse233.localsendkotlin.quicksettings.QuickSettingsTileService
import java.util.concurrent.atomic.AtomicInteger

class LocalSendApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ForegroundActivityState.reset()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) = ForegroundActivityState.started(activity)
            override fun onActivityStopped(activity: Activity) = ForegroundActivityState.stopped(activity)
            override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}

internal object ForegroundActivityState {
    private val startedActivities = AtomicInteger(0)
    @Volatile private var foreground = false

    fun reset() {
        startedActivities.set(0)
        foreground = false
    }

    fun started(activity: Activity) {
        if (startedActivities.incrementAndGet() == 1) {
            foreground = true
            requestTileUpdate(activity)
        }
    }

    fun stopped(activity: Activity) {
        val count = startedActivities.decrementAndGet()
        if (count <= 0) {
            startedActivities.set(0)
            foreground = false
            requestTileUpdate(activity)
        }
    }

    fun isForeground(): Boolean = foreground

    private fun requestTileUpdate(context: android.content.Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            QuickSettingsTileService.requestTileUpdate(context)
        }
    }
}
