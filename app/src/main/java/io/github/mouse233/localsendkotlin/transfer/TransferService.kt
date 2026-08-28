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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import io.github.mouse233.localsendkotlin.MainActivity
import io.github.mouse233.localsendkotlin.R
import io.github.mouse233.localsendkotlin.discovery.DiscoveryListener
import io.github.mouse233.localsendkotlin.discovery.DiscoveryManager
import io.github.mouse233.localsendkotlin.discovery.LocalIdentity
import io.github.mouse233.localsendkotlin.discovery.ManualDeviceConnector
import io.github.mouse233.localsendkotlin.discovery.ManualEndpoint
import io.github.mouse233.localsendkotlin.history.ReceiveHistoryStore
import io.github.mouse233.localsendkotlin.model.ReceivedFile
import io.github.mouse233.localsendkotlin.model.RemoteDevice
import io.github.mouse233.localsendkotlin.model.ActiveTransferFile
import io.github.mouse233.localsendkotlin.settings.AppSettings
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Owns discovery and transfers independently of the Activity lifecycle. */
class TransferService : Service(), DiscoveryListener {
    interface Listener {
        fun onDevicesChanged(devices: List<RemoteDevice>)
        fun onDiscoveryError(message: String)
        fun onIncomingTransferRequest(request: IncomingTransferManager.PrepareUploadRequest, decide: (IncomingReceiveOptions?) -> Unit)
        fun onIncomingSessionPrepared(sessionId: String, request: IncomingTransferManager.PrepareUploadRequest)
        fun onOutgoingSessionPreparing(sessionId: String, files: List<ActiveTransferFile>)
        fun onOutgoingChecksumProgress(sessionId: String, current: Int, total: Int)
        fun onOutgoingSessionPrepared(sessionId: String, files: List<ActiveTransferFile>)
        fun onOutgoingSessionStarted(preparationSessionId: String, sessionId: String, files: List<ActiveTransferFile>)
        fun onActiveTransfersRestored(files: List<ActiveTransferFile>)
        fun onFileReceiveProgress(file: ActiveTransferFile)
        fun onFileSendProgress(file: ActiveTransferFile)
        fun onFileReceiveCancelled(file: ActiveTransferFile, sessionComplete: Boolean)
        fun onFileReceived(sessionId: String, fileId: String, file: ReceivedFile, sessionComplete: Boolean)
        fun onIncomingSessionCompleted(sessionId: String)
        fun onOutgoingSessionCompleted(sessionId: String)
        fun onUploadStatus(message: String)
        fun onPinRequired(device: RemoteDevice, attempt: Int, reply: (String?) -> Unit)
        fun onUploadProgress(fileName: String, fileIndex: Int, fileCount: Int, sent: Long, total: Long, totalSent: Long, totalBytes: Long)
        fun onTransferStateRestored(title: String, percent: Int)
        fun onTransferFinished(message: String)
        fun onUploadCompleted(names: List<String>)
        fun onUploadError(message: String)
    }

    inner class LocalBinder : Binder() {
        fun service(): TransferService = this@TransferService
    }

    private val binder = LocalBinder()
    private val listenerLock = Any()
    private val listeners = LinkedHashSet<Listener>()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var discoveryManager: DiscoveryManager? = null
    private lateinit var uploadClient: UploadClient
    private lateinit var receiveHistory: ReceiveHistoryStore
    private lateinit var settings: AppSettings
    private val cancellationExecutor = Executors.newSingleThreadExecutor()
    private val networkExecutor = Executors.newSingleThreadExecutor()
    private val screenAwakeLock = Any()
    private val activeScreenAwakeSessions = LinkedHashSet<String>()
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var outgoingScreenAwakeSessionId: String? = null
    private var activeNotificationTitle: String? = null
    private var activeNotificationProgress = 0
    private var hasActiveTransfer = false
    @Volatile private var currentDevices: List<RemoteDevice> = emptyList()
    private var lastTransferMessage: String? = null
    private var lastProgressDispatchAt = 0L
    private var lastProgressDispatchPercent = -1
    private var activeTransferredBytes = 0L
    private var activeTotalBytes = -1L
    private var progressStartedAt = 0L
    private var transferSpeedBytesPerSecond = 0L
    @Volatile private var notificationGeneration = 0L
    @Volatile private var cancellationRequested = false
    @Volatile private var serviceDestroyed = false
    private val cancellationInProgress = AtomicBoolean(false)
    @Volatile private var pendingIncoming: PendingIncoming? = null
    private val incomingFiles = LinkedHashMap<String, LinkedHashMap<String, ActiveTransferFile>>()
    private val incomingSenders = LinkedHashMap<String, String>()
    private val outgoingFiles = LinkedHashMap<String, LinkedHashMap<String, ActiveTransferFile>>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(NOTIFICATION_ID, baseNotification())
        settings = AppSettings(this)
        uploadClient = UploadClient(this, LocalIdentity(this))
        receiveHistory = ReceiveHistoryStore(this)
        restartNetwork()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            // Cancel only the active transfer. Keep the foreground receiver
            // alive so the next sender can still reach port 53317.
            ACTION_CANCEL -> cancelCurrent()
            ACTION_ACCEPT_INCOMING -> resolveIncoming(true)
            ACTION_REJECT_INCOMING -> resolveIncoming(false)
            ACTION_RELOAD_SETTINGS -> restartNetwork()
            ACTION_REFRESH_SCREEN_AWAKE -> updateScreenAwakeLock()
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
                listener.onIncomingTransferRequest(pending.request) { options -> resolveIncoming(options) }
            }
            synchronized(outgoingFiles) {
                outgoingFiles.forEach { (sessionId, files) ->
                    listener.onOutgoingSessionPrepared(sessionId, files.values.toList())
                }
            }
            val activeSnapshot = synchronized(incomingFiles) {
                incomingFiles.values.flatMap { it.values }
            } + synchronized(outgoingFiles) {
                outgoingFiles.values.flatMap { it.values }
            }
            listener.onActiveTransfersRestored(activeSnapshot)
        }
    }

    fun removeListener(listener: Listener) {
        synchronized(listenerLock) { listeners -= listener }
    }

    fun announce() = discoveryManager?.announce()

    fun refreshDevices() = discoveryManager?.refresh()

    fun recordReceivedMessage(message: String, senderAlias: String?) {
        if (!settings.saveReceiveHistory()) return
        networkExecutor.execute {
            if (!serviceDestroyed) receiveHistory.addMessage(message, senderAlias)
        }
    }

    private fun restartNetwork() {
        if (serviceDestroyed) return
        networkExecutor.execute {
            discoveryManager?.stop()
            if (serviceDestroyed) return@execute
            val manager = DiscoveryManager(this, this)
            discoveryManager = manager
            if (settings.serverEnabled()) manager.start() else onDevicesChanged(emptyList())
        }
    }

    fun send(uri: Uri, device: RemoteDevice) = send(listOf(uri), device)

    fun sendManual(uris: List<Uri>, endpoint: ManualEndpoint, messageText: String? = null) {
        if (uris.isEmpty()) return
        notifyListeners { it.onUploadStatus(getString(R.string.manual_send_connecting)) }
        networkExecutor.execute {
            try {
                // A discovered device already carries its announced protocol and, for HTTPS,
                // the certificate fingerprint. Reuse that information instead of probing the
                // same endpoint again and guessing between HTTP and HTTPS.
                val discoveredDevice = currentDevices.firstOrNull { device ->
                    endpoint.matches(device.address, device.port)
                }
                if (discoveredDevice != null) {
                    mainHandler.post {
                        if (!serviceDestroyed) send(uris, discoveredDevice, messageText)
                    }
                    return@execute
                }
                val device = ManualDeviceConnector(this).resolve(endpoint)
                mainHandler.post {
                    if (!serviceDestroyed) send(uris, device, messageText)
                }
            } catch (_: Exception) {
                notifyListeners { it.onUploadError(getString(R.string.manual_send_failed, endpoint.host, endpoint.port)) }
            }
        }
    }

    fun send(uris: List<Uri>, device: RemoteDevice, messageText: String? = null) {
        notificationGeneration++
        cancellationRequested = false
        hasActiveTransfer = true
        lastTransferMessage = null
        resetProgressDispatch()
        resetTransferMetrics()
        activeNotificationTitle = getString(R.string.notification_uploading)
        activeNotificationProgress = 0
        uploadClient.send(uris, device, messageText, object : UploadClient.Listener {
            override fun onStatus(message: String) {
                if (cancellationRequested) return
                activeNotificationTitle = message
                notifyProgress()
                notifyListeners { it.onUploadStatus(message) }
            }

            override fun onPinRequired(device: RemoteDevice, attempt: Int, reply: (String?) -> Unit) {
                val hasForegroundListener = synchronized(listenerLock) { listeners.isNotEmpty() }
                if (hasForegroundListener) {
                    notifyListeners { it.onPinRequired(device, attempt, reply) }
                } else {
                    reply(null)
                }
            }

            override fun onPreparationStarted(sessionId: String, files: List<UploadClient.QueueFile>) {
                val queue = LinkedHashMap<String, ActiveTransferFile>()
                files.forEach { file ->
                    queue[file.id] = ActiveTransferFile(
                        sessionId, file.id, file.fileName, 0L, file.size,
                        ActiveTransferFile.Status.WAITING, ActiveTransferFile.Direction.OUTGOING
                    )
                }
                synchronized(outgoingFiles) { outgoingFiles[sessionId] = queue }
                notifyListeners { it.onOutgoingSessionPreparing(sessionId, queue.values.toList()) }
            }

            override fun onChecksumProgress(sessionId: String, current: Int, total: Int) {
                notifyListeners { it.onOutgoingChecksumProgress(sessionId, current, total) }
            }

            override fun onSessionPrepared(preparationSessionId: String, sessionId: String, files: List<UploadClient.QueueFile>) {
                beginScreenAwakeSession(sessionId)
                outgoingScreenAwakeSessionId = sessionId
                val queue = LinkedHashMap<String, ActiveTransferFile>()
                files.forEach { file ->
                    queue[file.id] = ActiveTransferFile(
                        sessionId, file.id, file.fileName, 0L, file.size,
                        ActiveTransferFile.Status.WAITING, ActiveTransferFile.Direction.OUTGOING
                    )
                }
                synchronized(outgoingFiles) {
                    outgoingFiles.remove(preparationSessionId)
                    outgoingFiles[sessionId] = queue
                }
                notifyListeners { it.onOutgoingSessionStarted(preparationSessionId, sessionId, queue.values.toList()) }
            }

            override fun onProgress(sessionId: String, fileId: String, fileName: String, fileIndex: Int, fileCount: Int, sent: Long, total: Long, totalSent: Long, totalBytes: Long) {
                if (cancellationRequested) return
                hasActiveTransfer = true
                activeNotificationTitle = getString(R.string.notification_uploading)
                activeNotificationProgress = if (totalBytes > 0) ((totalSent * 100L) / totalBytes).toInt().coerceIn(0, 100) else 0
                updateTransferMetrics(totalSent, totalBytes)
                dispatchProgressIfNeeded(totalSent, totalBytes) { listener ->
                    val state = synchronized(outgoingFiles) {
                        outgoingFiles[sessionId]?.get(fileId)?.copy(
                            receivedBytes = sent,
                            totalBytes = total,
                            status = ActiveTransferFile.Status.TRANSFERRING
                        )?.also { outgoingFiles[sessionId]?.set(fileId, it) }
                    }
                    if (state != null) {
                        listener.onFileSendProgress(state)
                    }
                    listener.onUploadProgress(fileName, fileIndex, fileCount, sent, total, totalSent, totalBytes)
                }
            }

            override fun onFileCompleted(sessionId: String, fileId: String) {
                val state = synchronized(outgoingFiles) {
                    outgoingFiles[sessionId]?.get(fileId)?.copy(
                        receivedBytes = outgoingFiles[sessionId]?.get(fileId)?.totalBytes ?: 0L,
                        status = ActiveTransferFile.Status.COMPLETED
                    )?.also { outgoingFiles[sessionId]?.set(fileId, it) }
                }
                if (state != null) notifyListeners { it.onFileSendProgress(state) }
            }

            override fun onCompleted(names: List<String>) {
                outgoingScreenAwakeSessionId?.let(::endScreenAwakeSession)
                outgoingScreenAwakeSessionId = null
                resetProgressDispatch()
                lastTransferMessage = getString(R.string.upload_completed, names.joinToString("、"))
                finishOutgoingQueues(ActiveTransferFile.Status.COMPLETED)
                cancellationRequested = false
                hasActiveTransfer = hasActiveTransferEntries()
                notifyListeners { it.onUploadCompleted(names) }
                clearTransferNotification()
            }

            override fun onError(message: String) {
                outgoingScreenAwakeSessionId?.let(::endScreenAwakeSession)
                outgoingScreenAwakeSessionId = null
                val terminalStatus = if (cancellationRequested) ActiveTransferFile.Status.CANCELLED else ActiveTransferFile.Status.FAILED
                finishOutgoingQueues(terminalStatus)
                cancellationRequested = false
                hasActiveTransfer = hasActiveTransferEntries()
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
        endAllScreenAwakeSessions()
        if (stopService) removeNotificationsAndStopForeground()
        cancellationExecutor.execute {
            uploadClient.cancelCurrent()
            // DiscoveryManager performs the peer cancellation synchronously
            // on this worker, before the local session is removed.
            discoveryManager?.cancelIncomingTransfer()
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

    fun cancelIncomingFile(sessionId: String, fileId: String): Boolean = discoveryManager?.cancelIncomingFile(sessionId, fileId) == true

    override fun onDevicesChanged(devices: List<RemoteDevice>) {
        currentDevices = devices
        notifyListeners { it.onDevicesChanged(devices) }
    }
    override fun onDiscoveryError(message: String) = notifyListeners { it.onDiscoveryError(message) }

    override fun onIncomingTransferRequest(
        request: IncomingTransferManager.PrepareUploadRequest,
        decide: (IncomingReceiveOptions?) -> Unit
    ) {
        // A message must reach the foreground UI even when automatic file
        // saving is enabled; otherwise it would be acknowledged and silently
        // disappear because it has no file session to display.
        if (request.messageText() != null) {
            val listener = listeners.firstOrNull()
            if (listener == null) {
                pendingIncoming?.decide?.invoke(null)
                pendingIncoming = PendingIncoming(request, decide)
                showIncomingRequestNotification(request)
            } else {
                mainHandler.post { listener.onIncomingTransferRequest(request, decide) }
            }
            return
        }
        val shouldAutoSave = settings.autoSaveReceivedFiles() ||
            (settings.autoSaveFavoriteReceivedFiles() && settings.isFavorite(request.info.fingerprint))
        if (shouldAutoSave) {
            decide(IncomingReceiveOptions.forAll(request, settings))
            return
        }
        val listener = listeners.firstOrNull()
        if (listener == null) {
            pendingIncoming?.decide?.invoke(null)
            pendingIncoming = PendingIncoming(request, decide)
            showIncomingRequestNotification(request)
        } else {
            mainHandler.post { listener.onIncomingTransferRequest(request, decide) }
        }
    }

    override fun onIncomingSessionPrepared(sessionId: String, request: IncomingTransferManager.PrepareUploadRequest) {
        beginScreenAwakeSession(sessionId)
        val files = LinkedHashMap<String, ActiveTransferFile>()
        request.files.forEach { (fileId, file) ->
            files[fileId] = ActiveTransferFile(sessionId, fileId, file.fileName, 0L, file.size, ActiveTransferFile.Status.WAITING)
        }
        synchronized(incomingFiles) {
            incomingFiles[sessionId] = files
            incomingSenders[sessionId] = request.info.alias
        }
        notifyListeners { it.onIncomingSessionPrepared(sessionId, request) }
    }

    override fun onFileReceiveProgress(sessionId: String, fileId: String, fileName: String, received: Long, total: Long) {
        if (cancellationRequested) return
        val state = ActiveTransferFile(sessionId, fileId, fileName, received, total, ActiveTransferFile.Status.TRANSFERRING)
        synchronized(incomingFiles) { incomingFiles[sessionId]?.set(fileId, state) }
        hasActiveTransfer = true
        // Multiple files may upload concurrently. Keep the notification title
        // stable instead of replacing it on every file progress callback.
        activeNotificationTitle = getString(R.string.notification_receiving_files)
        val aggregate = incomingProgress(sessionId)
        activeNotificationProgress = if (aggregate.second > 0) ((aggregate.first * 100L) / aggregate.second).toInt().coerceIn(0, 100) else 0
        updateTransferMetrics(aggregate.first, aggregate.second)
        dispatchProgressIfNeeded(aggregate.first, aggregate.second) { it.onFileReceiveProgress(state) }
    }

    override fun onFileReceiveCancelled(sessionId: String, fileId: String, fileName: String, sessionComplete: Boolean) {
        val state = synchronized(incomingFiles) {
            val previous = incomingFiles[sessionId]?.get(fileId)
            ActiveTransferFile(
                sessionId,
                fileId,
                fileName,
                previous?.receivedBytes ?: 0L,
                previous?.totalBytes ?: 0L,
                ActiveTransferFile.Status.CANCELLED
            ).also { incomingFiles[sessionId]?.set(fileId, it) }
        }
        lastTransferMessage = getString(R.string.download_cancelled, fileName)
        notifyListeners { it.onFileReceiveCancelled(state, sessionComplete) }
        if (sessionComplete) {
            endScreenAwakeSession(sessionId)
            finishIncomingSession(sessionId, ActiveTransferFile.Status.CANCELLED)
            resetProgressDispatch()
            notifyListeners { it.onIncomingSessionCompleted(sessionId) }
            if (!cancellationRequested) clearTransferNotification()
        }
    }

    override fun onFileReceived(sessionId: String, fileId: String, file: ReceivedFile, sessionComplete: Boolean) {
        if (cancellationRequested) return
        synchronized(incomingFiles) {
            incomingFiles[sessionId]?.get(fileId)?.let { incomingFiles[sessionId]?.set(fileId, it.copy(receivedBytes = it.totalBytes, status = ActiveTransferFile.Status.COMPLETED)) }
            if (settings.saveReceiveHistory()) receiveHistory.add(file, incomingSenders[sessionId])
        }
        lastTransferMessage = getString(R.string.download_completed, file.displayName)
        notifyListeners { it.onFileReceived(sessionId, fileId, file, sessionComplete) }
        if (sessionComplete) {
            endScreenAwakeSession(sessionId)
            finishIncomingSession(sessionId, ActiveTransferFile.Status.COMPLETED)
            resetProgressDispatch()
            notifyListeners { it.onIncomingSessionCompleted(sessionId) }
            clearTransferNotification()
        }
    }

    private fun incomingProgress(sessionId: String): Pair<Long, Long> = synchronized(incomingFiles) {
        val files = incomingFiles[sessionId]?.values ?: return@synchronized 0L to 0L
        files.sumOf { it.receivedBytes } to files.sumOf { it.totalBytes }
    }

    private fun finishOutgoingQueues(status: ActiveTransferFile.Status) {
        val updated = synchronized(outgoingFiles) {
            val states = mutableListOf<ActiveTransferFile>()
            outgoingFiles.values.forEach { session ->
                session.values.toList().forEach { file ->
                    if (file.status != status) {
                        file.copy(
                            receivedBytes = if (status == ActiveTransferFile.Status.COMPLETED) file.totalBytes else file.receivedBytes,
                            status = status
                        ).also {
                            session[file.fileId] = it
                            states += it
                        }
                    }
                }
            }
            states
        }
        updated.forEach { state -> notifyListeners { it.onFileSendProgress(state) } }
        val sessions = synchronized(outgoingFiles) { outgoingFiles.keys.toList() }
        sessions.forEach { sessionId -> notifyListeners { it.onOutgoingSessionCompleted(sessionId) } }
    }

    private fun finishIncomingSession(sessionId: String, status: ActiveTransferFile.Status) {
        val updated = synchronized(incomingFiles) {
            incomingFiles[sessionId]?.values?.mapNotNull { file ->
                if (file.status != ActiveTransferFile.Status.WAITING && file.status != ActiveTransferFile.Status.TRANSFERRING) return@mapNotNull null
                file.copy(
                    receivedBytes = if (status == ActiveTransferFile.Status.COMPLETED) file.totalBytes else file.receivedBytes,
                    status = status
                ).also { incomingFiles[sessionId]?.set(file.fileId, it) }
            }.orEmpty()
        }
        updated.forEach { state -> notifyListeners { it.onFileReceiveProgress(state) } }
        hasActiveTransfer = hasActiveTransferEntries()
    }

    private fun hasActiveTransferEntries(): Boolean {
        val incomingActive = synchronized(incomingFiles) {
            incomingFiles.values.any { files -> files.values.any(::isActive) }
        }
        val outgoingActive = synchronized(outgoingFiles) {
            outgoingFiles.values.any { files -> files.values.any(::isActive) }
        }
        return incomingActive || outgoingActive
    }

    private fun isActive(file: ActiveTransferFile): Boolean =
        file.status == ActiveTransferFile.Status.WAITING || file.status == ActiveTransferFile.Status.TRANSFERRING

    private fun clearTransferHistory() {
        synchronized(incomingFiles) {
            incomingFiles.clear()
            incomingSenders.clear()
        }
        synchronized(outgoingFiles) { outgoingFiles.clear() }
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
        resolveIncoming(if (accepted) IncomingReceiveOptions.forAll(pending.request, settings) else null)
    }

    private fun resolveIncoming(options: IncomingReceiveOptions?) {
        val pending = pendingIncoming ?: return
        pendingIncoming = null
        notificationManager().cancel(INCOMING_NOTIFICATION_ID)
        pending.decide(options)
    }

    private fun showIncomingRequestNotification(request: IncomingTransferManager.PrepareUploadRequest) {
        val message = request.messageText()
        val files = message ?: request.files.values.joinToString(", ") { it.fileName }
        val notification = NotificationCompat.Builder(this, CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(if (message == null) R.string.notification_incoming_title else R.string.incoming_message_title, request.info.alias))
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
        serviceDestroyed = true
        resolveIncoming(false)
        endAllScreenAwakeSessions()
        cancellationExecutor.shutdownNow()
        networkExecutor.shutdownNow()
        discoveryManager?.stop()
        clearTransferHistory()
        if (::receiveHistory.isInitialized) receiveHistory.close()
        stopForeground(true)
        notificationManager().cancel(NOTIFICATION_ID)
        notificationManager().cancel(PROGRESS_NOTIFICATION_ID)
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
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getString(R.string.notification_service_title))
        .setContentText(getString(R.string.notification_service_text))
        .setContentIntent(openAppIntent())
        .setOngoing(true)
        .setProgress(0, 0, false)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun notifyProgress() {
        val generation = notificationGeneration
        val title = activeNotificationTitle ?: getString(R.string.notification_service_title)
        val progress = activeNotificationProgress
        val transferred = activeTransferredBytes
        val total = activeTotalBytes
        val speed = transferSpeedBytesPerSecond
        mainHandler.post {
            if (generation != notificationGeneration || !hasActiveTransfer) return@post
            val firstLine = if (total > 0L) {
                "${progress}% (${formatBytes(transferred)}/${formatBytes(total)})"
            } else {
                "${progress}% (${formatBytes(transferred)})"
            }
            val secondLine = if (speed > 0L && total >= transferred) {
                val remaining = (total - transferred) / speed
                "ETA: ${formatDuration(remaining)} · ${formatBytes(speed)}/s"
            } else {
                "ETA: --:-- · ${formatBytes(speed)}/s"
            }
            val builder = NotificationCompat.Builder(this, CHANNEL_TRANSFER)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(firstLine)
                .setSubText(secondLine)
                .setContentIntent(openAppIntent())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(0, getString(R.string.cancel_transfer), cancelIntent())
                .setStyle(NotificationCompat.BigTextStyle().bigText("$firstLine\n$secondLine"))
                .setProgress(100, progress, false)
            startForeground(NOTIFICATION_ID, builder.build())
        }
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
        val generation = ++notificationGeneration
        mainHandler.post {
            if (generation != notificationGeneration) return@post
            notificationManager().cancel(NOTIFICATION_ID)
            notificationManager().cancel(PROGRESS_NOTIFICATION_ID)
            // Return the foreground service to its idle notification after the
            // transfer progress has been removed.
            startForeground(NOTIFICATION_ID, baseNotification())
        }
    }

    private fun removeNotificationsAndStopForeground() {
        notificationManager().cancel(NOTIFICATION_ID)
        notificationManager().cancel(PROGRESS_NOTIFICATION_ID)
        notificationManager().cancel(INCOMING_NOTIFICATION_ID)
        stopForeground(true)
    }

    private fun beginScreenAwakeSession(sessionId: String) {
        synchronized(screenAwakeLock) {
            activeScreenAwakeSessions += sessionId
            updateScreenAwakeLockLocked()
        }
    }

    private fun endScreenAwakeSession(sessionId: String) {
        synchronized(screenAwakeLock) {
            activeScreenAwakeSessions -= sessionId
            updateScreenAwakeLockLocked()
        }
    }

    private fun endAllScreenAwakeSessions() {
        synchronized(screenAwakeLock) {
            activeScreenAwakeSessions.clear()
            updateScreenAwakeLockLocked()
        }
    }

    private fun updateScreenAwakeLock() {
        synchronized(screenAwakeLock) {
            updateScreenAwakeLockLocked()
        }
    }

    @Suppress("DEPRECATION", "WakelockTimeout")
    private fun updateScreenAwakeLockLocked() {
        val shouldHold = settings.keepScreenAwakeDuringTransfer() && activeScreenAwakeSessions.isNotEmpty()
        if (shouldHold && wakeLock?.isHeld != true) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK,
                "$packageName:transfer-screen-awake"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        } else if (!shouldHold) {
            wakeLock?.let { lock ->
                if (lock.isHeld) lock.release()
            }
            wakeLock = null
        }
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
        const val ACTION_RELOAD_SETTINGS = "io.github.mouse233.localsendkotlin.RELOAD_SETTINGS"
        const val ACTION_REFRESH_SCREEN_AWAKE = "io.github.mouse233.localsendkotlin.REFRESH_SCREEN_AWAKE"
        private const val ACTION_ACCEPT_INCOMING = "io.github.mouse233.localsendkotlin.ACCEPT_INCOMING"
        private const val ACTION_REJECT_INCOMING = "io.github.mouse233.localsendkotlin.REJECT_INCOMING"
        private const val CHANNEL_TRANSFER = "transfer_progress"
        private const val CHANNEL_INCOMING = "incoming_request"
        private const val NOTIFICATION_ID = 53317
        private const val PROGRESS_NOTIFICATION_ID = 53319
        private const val INCOMING_NOTIFICATION_ID = 53318
        private const val PROGRESS_INTERVAL_MS = 200L
        private const val METRICS_MIN_SAMPLE_MS = 500L
        private const val CANCEL_SHUTDOWN_DELAY_MS = 1500L
    }

    private val progressLock = Any()

    private data class PendingIncoming(
        val request: IncomingTransferManager.PrepareUploadRequest,
        val decide: (IncomingReceiveOptions?) -> Unit
    )
}
