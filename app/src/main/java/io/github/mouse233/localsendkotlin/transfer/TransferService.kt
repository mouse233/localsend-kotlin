package io.github.mouse233.localsendkotlin.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import io.github.mouse233.localsendkotlin.MainActivity
import io.github.mouse233.localsendkotlin.R
import io.github.mouse233.localsendkotlin.discovery.DiscoveryListener
import io.github.mouse233.localsendkotlin.discovery.DiscoveryManager
import io.github.mouse233.localsendkotlin.discovery.LocalIdentity
import io.github.mouse233.localsendkotlin.model.ReceivedFile
import io.github.mouse233.localsendkotlin.model.RemoteDevice
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Owns discovery and transfers independently of the Activity lifecycle. */
class TransferService : Service(), DiscoveryListener {
    interface Listener {
        fun onDevicesChanged(devices: List<RemoteDevice>)
        fun onDiscoveryError(message: String)
        fun onIncomingTransferRequest(request: IncomingTransferManager.PrepareUploadRequest, decide: (Boolean) -> Unit)
        fun onFileReceiveProgress(fileName: String, received: Long, total: Long)
        fun onFileReceiveCancelled(fileName: String)
        fun onFileReceived(file: ReceivedFile)
        fun onUploadStatus(message: String)
        fun onUploadProgress(sent: Long, total: Long)
        fun onTransferStateRestored(title: String, percent: Int)
        fun onTransferFinished(message: String)
        fun onUploadCompleted(name: String)
        fun onUploadError(message: String)
    }

    inner class LocalBinder : Binder() {
        fun service(): TransferService = this@TransferService
    }

    private val binder = LocalBinder()
    private val listenerLock = Any()
    private val listeners = LinkedHashSet<Listener>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var discoveryManager: DiscoveryManager
    private lateinit var uploadClient: UploadClient
    private val cancellationExecutor = Executors.newSingleThreadExecutor()
    private var activeNotificationTitle: String? = null
    private var activeNotificationProgress = 0
    private var hasActiveTransfer = false
    private var currentDevices: List<RemoteDevice> = emptyList()
    private var lastTransferMessage: String? = null
    private var lastProgressDispatchAt = 0L
    private var lastProgressDispatchPercent = -1
    private var activeTransferredBytes = 0L
    private var activeTotalBytes = -1L
    private var progressStartedAt = 0L
    private var transferSpeedBytesPerSecond = 0L
    @Volatile private var cancellationRequested = false
    private val cancellationInProgress = AtomicBoolean(false)
    @Volatile private var pendingIncoming: PendingIncoming? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(NOTIFICATION_ID, baseNotification())
        discoveryManager = DiscoveryManager(this, this)
        uploadClient = UploadClient(this, LocalIdentity(this))
        discoveryManager.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            // Cancel only the active transfer. Keep the foreground receiver
            // alive so the next sender can still reach port 53317.
            ACTION_CANCEL -> cancelCurrent()
            ACTION_ACCEPT_INCOMING -> resolveIncoming(true)
            ACTION_REJECT_INCOMING -> resolveIncoming(false)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun addListener(listener: Listener) {
        synchronized(listenerLock) { listeners += listener }
        mainHandler.post {
            listener.onDevicesChanged(currentDevices)
            if (hasActiveTransfer) listener.onTransferStateRestored(activeNotificationTitle ?: getString(R.string.notification_service_title), activeNotificationProgress)
            else lastTransferMessage?.let(listener::onTransferFinished)
            pendingIncoming?.let { pending ->
                listener.onIncomingTransferRequest(pending.request) { accepted -> resolveIncoming(accepted) }
            }
        }
    }

    fun removeListener(listener: Listener) {
        synchronized(listenerLock) { listeners -= listener }
    }

    fun announce() = discoveryManager.announce()

    fun send(uri: Uri, device: RemoteDevice) {
        cancellationRequested = false
        hasActiveTransfer = true
        lastTransferMessage = null
        resetProgressDispatch()
        resetTransferMetrics()
        activeNotificationTitle = getString(R.string.notification_uploading)
        activeNotificationProgress = 0
        uploadClient.send(uri, device, object : UploadClient.Listener {
            override fun onStatus(message: String) {
                if (cancellationRequested) return
                activeNotificationTitle = message
                notifyProgress()
                notifyListeners { it.onUploadStatus(message) }
            }

            override fun onProgress(sent: Long, total: Long) {
                if (cancellationRequested) return
                hasActiveTransfer = true
                activeNotificationTitle = getString(R.string.notification_uploading)
                activeNotificationProgress = if (total > 0) ((sent * 100L) / total).toInt().coerceIn(0, 100) else 0
                updateTransferMetrics(sent, total)
                dispatchProgressIfNeeded(sent, total) { it.onUploadProgress(sent, total) }
            }

            override fun onCompleted(name: String) {
                hasActiveTransfer = false
                cancellationRequested = false
                resetProgressDispatch()
                lastTransferMessage = getString(R.string.upload_completed, name)
                notifyListeners { it.onUploadCompleted(name) }
                clearTransferNotification()
            }

            override fun onError(message: String) {
                hasActiveTransfer = false
                cancellationRequested = false
                resetProgressDispatch()
                lastTransferMessage = message
                notifyListeners { it.onUploadError(message) }
                clearTransferNotification()
            }
        })
    }

    fun cancelCurrent(stopService: Boolean = false) {
        if (!cancellationInProgress.compareAndSet(false, true)) return
        // Block progress callbacks immediately while the remote cancellation
        // request is still in flight.
        cancellationRequested = true
        hasActiveTransfer = false
        if (stopService) removeNotificationsAndStopForeground()
        cancellationExecutor.execute {
            uploadClient.cancelCurrent()
            // DiscoveryManager performs the peer cancellation synchronously
            // on this worker, before the local session is removed.
            discoveryManager.cancelIncomingTransfer()
            mainHandler.post {
                lastTransferMessage = getString(R.string.upload_cancelled)
                if (!stopService) {
                    cancellationRequested = false
                    clearTransferNotification()
                }
                cancellationInProgress.set(false)
                if (stopService) {
                    removeNotificationsAndStopForeground()
                    stopSelf()
                }
            }
        }
    }

    override fun onDevicesChanged(devices: List<RemoteDevice>) {
        currentDevices = devices
        notifyListeners { it.onDevicesChanged(devices) }
    }
    override fun onDiscoveryError(message: String) = notifyListeners { it.onDiscoveryError(message) }

    override fun onIncomingTransferRequest(
        request: IncomingTransferManager.PrepareUploadRequest,
        decide: (Boolean) -> Unit
    ) {
        val listener = listeners.firstOrNull()
        if (listener == null) {
            pendingIncoming?.decide?.invoke(false)
            pendingIncoming = PendingIncoming(request, decide)
            showIncomingRequestNotification(request)
        } else {
            mainHandler.post { listener.onIncomingTransferRequest(request, decide) }
        }
    }

    override fun onFileReceiveProgress(fileName: String, received: Long, total: Long) {
        if (cancellationRequested) return
        hasActiveTransfer = true
        activeNotificationTitle = getString(R.string.notification_receiving, fileName)
        activeNotificationProgress = if (total > 0) ((received * 100L) / total).toInt().coerceIn(0, 100) else 0
        updateTransferMetrics(received, total)
        dispatchProgressIfNeeded(received, total) { it.onFileReceiveProgress(fileName, received, total) }
    }

    override fun onFileReceiveCancelled(fileName: String) {
        hasActiveTransfer = false
        resetProgressDispatch()
        lastTransferMessage = getString(R.string.download_cancelled, fileName)
        notifyListeners { it.onFileReceiveCancelled(fileName) }
        if (!cancellationRequested) clearTransferNotification()
    }

    override fun onFileReceived(file: ReceivedFile) {
        if (cancellationRequested) return
        hasActiveTransfer = false
        resetProgressDispatch()
        lastTransferMessage = getString(R.string.download_completed, file.displayName)
        notifyListeners { it.onFileReceived(file) }
        clearTransferNotification()
    }

    /** Activity callbacks must always run on the main thread because they update Views. */
    private fun notifyListeners(callback: (Listener) -> Unit) {
        val snapshot = synchronized(listenerLock) { listeners.toList() }
        mainHandler.post { snapshot.forEach(callback) }
    }

    /**
     * Progress arrives once per 32KB chunk. Do not enqueue every chunk on the
     * main looper or synchronously update NotificationManager: large files can
     * otherwise starve Activity rendering and cause an ANR.
     */
    private fun dispatchProgressIfNeeded(received: Long, total: Long, callback: (Listener) -> Unit) {
        val now = android.os.SystemClock.elapsedRealtime()
        val percent = if (total > 0) ((received * 100L) / total).toInt().coerceIn(0, 100) else -1
        val shouldDispatch = synchronized(progressLock) {
            val due = now - lastProgressDispatchAt >= PROGRESS_INTERVAL_MS
            val changedEnough = percent >= 0 && percent != lastProgressDispatchPercent
            if (due || changedEnough || received >= total) {
                lastProgressDispatchAt = now
                lastProgressDispatchPercent = percent
                true
            } else false
        }
        if (!shouldDispatch) return
        notifyProgress()
        notifyListeners(callback)
    }

    private fun resetProgressDispatch() {
        synchronized(progressLock) {
            lastProgressDispatchAt = 0L
            lastProgressDispatchPercent = -1
        }
    }

    private fun resetTransferMetrics() {
        activeTransferredBytes = 0L
        activeTotalBytes = -1L
        progressStartedAt = 0L
        transferSpeedBytesPerSecond = 0L
    }

    private fun updateTransferMetrics(transferred: Long, total: Long) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (progressStartedAt == 0L || total != activeTotalBytes || transferred < activeTransferredBytes) {
            progressStartedAt = now
            transferSpeedBytesPerSecond = 0L
        }
        activeTransferredBytes = transferred
        activeTotalBytes = total
        val elapsed = now - progressStartedAt
        if (elapsed >= METRICS_MIN_SAMPLE_MS && transferred > 0L) {
            transferSpeedBytesPerSecond = transferred * 1000L / elapsed
        }
    }

    private fun resolveIncoming(accepted: Boolean) {
        val pending = pendingIncoming ?: return
        pendingIncoming = null
        notificationManager().cancel(INCOMING_NOTIFICATION_ID)
        pending.decide(accepted)
    }

    private fun showIncomingRequestNotification(request: IncomingTransferManager.PrepareUploadRequest) {
        val files = request.files.values.joinToString(", ") { it.fileName }
        val notification = NotificationCompat.Builder(this, CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_incoming_title, request.info.alias))
            .setContentText(files)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, getString(R.string.incoming_request_accept), acceptIncomingIntent())
            .addAction(0, getString(R.string.incoming_request_reject), rejectIncomingIntent())
            .build()
        notificationManager().notify(INCOMING_NOTIFICATION_ID, notification)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Match the normal LocalSend lifecycle: swiping the app task away is
        // an explicit request to stop accepting or sending files.
        resolveIncoming(false)
        cancelCurrent(stopService = true)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        resolveIncoming(false)
        cancellationExecutor.shutdownNow()
        discoveryManager.stop()
        stopForeground(true)
        notificationManager().cancel(NOTIFICATION_ID)
        notificationManager().cancel(INCOMING_NOTIFICATION_ID)
        super.onDestroy()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = notificationManager()
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_TRANSFER, getString(R.string.notification_channel_transfer), NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_transfer_description)
                setSound(null, null)
                enableVibration(false)
            })
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_INCOMING, getString(R.string.notification_channel_incoming), NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_incoming_description)
            })
        }
    }

    private fun baseNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_TRANSFER)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(getString(R.string.notification_service_title))
        .setContentText(getString(R.string.notification_service_text))
        .setContentIntent(openAppIntent())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .addAction(0, getString(R.string.cancel_transfer), cancelIntent())
        .build()

    private fun notifyProgress() {
        val firstLine = if (activeTotalBytes > 0L) {
            "${activeNotificationProgress}% (${formatBytes(activeTransferredBytes)}/${formatBytes(activeTotalBytes)})"
        } else {
            "${activeNotificationProgress}% (${formatBytes(activeTransferredBytes)})"
        }
        val secondLine = if (transferSpeedBytesPerSecond > 0L && activeTotalBytes >= activeTransferredBytes) {
            val remaining = (activeTotalBytes - activeTransferredBytes) / transferSpeedBytesPerSecond
            "ETA: ${formatDuration(remaining)} · ${formatBytes(transferSpeedBytesPerSecond)}/s"
        } else {
            "ETA: --:-- · ${formatBytes(transferSpeedBytesPerSecond)}/s"
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_TRANSFER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(activeNotificationTitle ?: getString(R.string.notification_service_title))
            .setContentText(firstLine)
            .setSubText(secondLine)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.cancel_transfer), cancelIntent())
            .setStyle(NotificationCompat.BigTextStyle().bigText("$firstLine\n$secondLine"))
        builder.setProgress(100, activeNotificationProgress, false)
        notificationManager().notify(NOTIFICATION_ID, builder.build())
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }

    private fun formatDuration(seconds: Long): String {
        val minutes = seconds / 60L
        val remainingSeconds = seconds % 60L
        return "%d:%02d".format(minutes, remainingSeconds)
    }

    private fun clearTransferNotification() {
        notificationManager().cancel(NOTIFICATION_ID)
        // Keep the receiver alive after a transfer. Stopping foreground mode
        // here lets Android reclaim the server while the app is backgrounded.
        startForeground(NOTIFICATION_ID, baseNotification())
    }

    private fun removeNotificationsAndStopForeground() {
        notificationManager().cancel(NOTIFICATION_ID)
        notificationManager().cancel(INCOMING_NOTIFICATION_ID)
        stopForeground(true)
    }

    @Suppress("DEPRECATION")
    private fun notificationManager(): NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), pendingIntentFlags())
    private fun cancelIntent(): PendingIntent = PendingIntent.getService(this, 1, Intent(this, TransferService::class.java).setAction(ACTION_CANCEL), pendingIntentFlags())
    private fun acceptIncomingIntent(): PendingIntent = PendingIntent.getService(this, 2, Intent(this, TransferService::class.java).setAction(ACTION_ACCEPT_INCOMING), pendingIntentFlags())
    private fun rejectIncomingIntent(): PendingIntent = PendingIntent.getService(this, 3, Intent(this, TransferService::class.java).setAction(ACTION_REJECT_INCOMING), pendingIntentFlags())
    private fun pendingIntentFlags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    companion object {
        const val ACTION_CANCEL = "io.github.mouse233.localsendkotlin.CANCEL_TRANSFER"
        private const val ACTION_ACCEPT_INCOMING = "io.github.mouse233.localsendkotlin.ACCEPT_INCOMING"
        private const val ACTION_REJECT_INCOMING = "io.github.mouse233.localsendkotlin.REJECT_INCOMING"
        private const val CHANNEL_TRANSFER = "transfer_progress"
        private const val CHANNEL_INCOMING = "incoming_request"
        private const val NOTIFICATION_ID = 53317
        private const val INCOMING_NOTIFICATION_ID = 53318
        private const val PROGRESS_INTERVAL_MS = 200L
        private const val METRICS_MIN_SAMPLE_MS = 500L
        private const val CANCEL_SHUTDOWN_DELAY_MS = 1500L
    }

    private val progressLock = Any()

    private data class PendingIncoming(
        val request: IncomingTransferManager.PrepareUploadRequest,
        val decide: (Boolean) -> Unit
    )
}
