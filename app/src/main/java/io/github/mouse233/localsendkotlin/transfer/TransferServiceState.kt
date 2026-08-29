package io.github.mouse233.localsendkotlin.transfer

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

/** Tracks the process-local service state used by the Quick Settings tile. */
internal object TransferServiceState {
    private val running = AtomicBoolean(false)

    fun markRunning(value: Boolean) {
        running.set(value)
    }

    fun isRunning(): Boolean = running.get()

    fun start(context: Context) {
        markRunning(true)
        val intent = Intent(context, TransferService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }

    fun stop(context: Context) {
        markRunning(false)
        val intent = Intent(context, TransferService::class.java).setAction(TransferService.ACTION_STOP_SERVICE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }
}
