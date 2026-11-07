package com.mrsep.musicrecognizer.feature.recognition.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mrsep.musicrecognizer.core.domain.recognition.AutoRecognitionInteractor
import com.mrsep.musicrecognizer.core.domain.recognition.model.AutoRecognitionState
import com.mrsep.musicrecognizer.core.domain.recognition.model.RemoteRecognitionResult
import com.mrsep.musicrecognizer.core.domain.track.TrackRepository
import com.mrsep.musicrecognizer.core.domain.track.model.Track
import com.mrsep.musicrecognizer.feature.recognition.platform.AlarmWaitImpl
import com.mrsep.musicrecognizer.feature.recognition.platform.BatteryExemptionLost
import com.mrsep.musicrecognizer.feature.recognition.service.AutoServiceNotificationHelper.Companion.NOTIFICATION_ID_STATUS
import com.mrsep.musicrecognizer.feature.recognition.service.ext.cachedNotificationArtwork
import com.mrsep.musicrecognizer.feature.recognition.service.ext.downloadImageToDiskCache
import com.mrsep.musicrecognizer.feature.recognition.service.ext.prefetchArtworkAndGenerateSeedColor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours
import com.mrsep.musicrecognizer.core.strings.R as StringsR

private const val TAG = "AutoRecognitionService"

@AndroidEntryPoint
class AutoRecognitionService : Service() {

    @Inject
    lateinit var interactor: AutoRecognitionInteractor

    @Inject
    lateinit var statusNotificationHelper: AutoServiceNotificationHelper

    @Inject
    lateinit var resultNotificationHelper: ResultNotificationHelper

    @Inject
    lateinit var trackRepository: TrackRepository

    @Inject
    internal lateinit var alarmWait: AlarmWaitImpl

    private lateinit var alarmManager: AlarmManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sessionJob: Job? = null

    private var startedForeground = false

    // Cleared before stopForeground, so a later notify cannot post the ongoing notification back.
    private var canNotifyStatus = false
    private var destroyed = false
    private var latestStartId = 0

    private var postedStatus: StatusText? = null
    private var postedMatchCount = 0

    private var timeLimitFired = false
    private var stoppingForTimeLimit = false

    private var cachedTrack: CachedTrack? = null

    private var nowPlayingExpiryJob: Job? = null
    private var nowPlayingExpiryFor: AutoRecognitionState.LastPlaying? = null

    private lateinit var publishMatchWakeLock: PowerManager.WakeLock

    private val fullArtworkUrls = Channel<String>(Channel.UNLIMITED)

    private val localReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                ACTION_STOP -> AutoRecognitionService.stop(context)
                ACTION_ALLOW_LOW_BATTERY -> interactor.allowLowBatteryThisTime()
                ACTION_TIME_LIMIT -> onTimeLimit()
                // delay() pauses in sleep. Screen on is the cheap signal that NowPlaying may have ended.
                Intent.ACTION_SCREEN_ON -> resyncNowPlayingAfterWake()
                AlarmWaitImpl.ACTION -> {
                    // Run here so the attempt lock is held before onReceive returns and
                    // AlarmManager drops its lock at the end of onReceive.
                    alarmWait.onAlarm()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        publishMatchWakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "$packageName:auto_recognition_publish_match"
            )
            .apply { setReferenceCounted(false) }
        ContextCompat.registerReceiver(
            this,
            localReceiver,
            IntentFilter().apply {
                addAction(ACTION_STOP)
                addAction(ACTION_ALLOW_LOW_BATTERY)
                addAction(ACTION_TIME_LIMIT)
                addAction(AlarmWaitImpl.ACTION)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        serviceScope.launch { observeSession() }
        serviceScope.launch { for (url in fullArtworkUrls) downloadImageToDiskCache(url) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        when (intent?.action) {
            ACTION_START -> {
                // Every startForegroundService() must reach startForeground(),
                // including a start that does not own the session.
                if (!startForegroundSafely()) {
                    if (!sessionJobRunning()) stopSelf(startId)
                    return START_NOT_STICKY
                }
                if (!isIgnoringBatteryOptimizations() && !sessionJobRunning()) {
                    Log.w(TAG, "Refusing auto recognition without a battery-optimization exemption")
                    toastBatteryExemptionLost()
                    shutdown(stopSelf = true)
                    return START_NOT_STICKY
                }
                launchSessionIfNeeded()
            }
            null -> {
                if (!sessionJobRunning()) stopSelf(startId)
            }
            else -> {
                Log.e(TAG, "Unknown action ${intent.action}")
                if (!startedForeground) stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        destroyed = true
        unregisterReceiver(localReceiver)
        shutdown(stopSelf = false)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundSafely(): Boolean {
        val alreadyForeground = startedForeground
        try {
            val status = statusText(interactor.state.value as? AutoRecognitionState.Running)
            val notification = statusNotificationHelper.buildStatusNotification(
                body = status.body,
                allowLowBattery = status.allowLowBattery,
                contentTrackId = status.contentTrackId,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID_STATUS,
                    notification,
                    FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
            } else {
                startForeground(NOTIFICATION_ID_STATUS, notification)
            }
            startedForeground = true
            canNotifyStatus = true
            postedStatus = status
            return true
        } catch (e: SecurityException) {
            val message = "Foreground service cannot start due to denied permissions"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            Log.w(TAG, message, e)
            if (!alreadyForeground) startedForeground = false
            return false
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException
            ) {
                val message = "Foreground service cannot start from the background"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                Log.e(TAG, message, e)
                if (!alreadyForeground) startedForeground = false
                return false
            }
            throw e
        }
    }

    private fun launchSessionIfNeeded() {
        if (sessionJobRunning() || stoppingForTimeLimit) return
        cancelTimeLimit()
        timeLimitFired = false
        stoppingForTimeLimit = false
        scheduleTimeLimit()
        sessionJob = serviceScope.launch {
            var error: RemoteRecognitionResult.Error? = null
            try {
                error = interactor.run()
            } catch (e: CancellationException) {
                if (e is BatteryExemptionLost) toastBatteryExemptionLost()
                throw e
            } finally {
                shutdown(stopSelf = true, error = error, timeLimit = stoppingForTimeLimit)
            }
        }
    }

    private fun shutdown(
        stopSelf: Boolean,
        error: RemoteRecognitionResult.Error? = null,
        timeLimit: Boolean = false,
    ) {
        // Fatal and the time limit are posted once without check.
        canNotifyStatus = false
        cancelNowPlayingExpiry()
        timeLimitFired = false
        stoppingForTimeLimit = false
        cancelTimeLimit()
        cachedTrack = null
        postedStatus = null
        releasePublishMatchWakeLock()
        if (startedForeground) {
            startedForeground = false
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        if (error != null) {
            statusNotificationHelper.notifyFatal(error)
        } else if (timeLimit) {
            statusNotificationHelper.notifyTimeLimit()
        }
        // stopSelf(startId) doesn't stop the service if a newer start was already delivered.
        if (stopSelf && !destroyed) stopSelf(latestStartId)
        AutoRecognitionTileService.requestListeningState(this)
    }

    private fun onTimeLimit() {
        if (sessionJob?.isActive != true) return
        timeLimitFired = true
        stopIfTimeLimit()
    }

    private fun stopIfTimeLimit(): Boolean {
        if (!timeLimitFired || sessionJob?.isActive != true) return false
        val state = interactor.state.value as? AutoRecognitionState.Running ?: return false
        if (!state.phase.isWaiting()) return false
        if (state.sessionTrackIds.size > postedMatchCount) return false
        stoppingForTimeLimit = true
        sessionJob?.cancel()
        return true
    }

    @SuppressLint("MissingPermission", "ScheduleExactAlarm")
    private fun scheduleTimeLimit() {
        if (!TIME_LIMIT_ENABLED) return
        val triggerAt = SystemClock.elapsedRealtime() + TIME_LIMIT.inWholeMilliseconds
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                timeLimitPendingIntent(),
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Time limit alarm was not scheduled", e)
        }
    }

    private fun cancelTimeLimit() {
        if (!TIME_LIMIT_ENABLED) return
        alarmManager.cancel(timeLimitPendingIntent())
    }

    private fun timeLimitPendingIntent(): PendingIntent {
        return localBroadcastPendingIntent(this, ACTION_TIME_LIMIT)
    }

    private fun sessionJobRunning() = sessionJob?.isCompleted == false

    private suspend fun observeSession() {
        var lastTileContent = AutoRecognitionState.Idle.tileContent()
        interactor.state.collect { state ->
            val tileContent = state.tileContent()
            if (tileContent != lastTileContent) {
                lastTileContent = tileContent
                AutoRecognitionTileService.requestListeningState(this@AutoRecognitionService)
            }
            when (state) {
                AutoRecognitionState.Idle -> onIdle()
                is AutoRecognitionState.Running -> onRunning(state)
            }
        }
    }

    private fun onIdle() {
        cancelNowPlayingExpiry()
        postedMatchCount = 0
        postedStatus = null
        cachedTrack = null
    }

    private suspend fun onRunning(state: AutoRecognitionState.Running) {
        // Observe can skip Idle, so a shorter id list is a new session.
        if (state.sessionTrackIds.size < postedMatchCount) {
            postedMatchCount = 0
            cachedTrack = null
        }
        val newTrackIds = if (canNotifyStatus && state.sessionTrackIds.size > postedMatchCount) {
            state.sessionTrackIds.drop(postedMatchCount)
        } else {
            emptyList()
        }
        if (newTrackIds.isNotEmpty()) {
            newTrackIds.forEach { trackId -> publishNewMatch(trackId) }
            postedMatchCount = state.sessionTrackIds.size
        }
        if (stopIfTimeLimit()) return
        postStatus(state)
        syncNowPlayingExpiry(state)
    }

    private suspend fun postStatus(state: AutoRecognitionState.Running) {
        if (!canNotifyStatus) return
        val status = statusText(state)
        if (status == postedStatus) return
        val largeIcon = visibleNowPlaying(state)?.let { track ->
            cachedNotificationArtwork(track.artworkThumbUrl, track.artworkUrl)
        }
        if (!canNotifyStatus) return
        postedStatus = status
        statusNotificationHelper.notifyStatus(
            body = status.body,
            largeIcon = largeIcon,
            allowLowBattery = status.allowLowBattery,
            contentTrackId = status.contentTrackId,
        )
    }

    private fun statusText(state: AutoRecognitionState.Running?): StatusText {
        val track = state?.let(::visibleNowPlaying)
        return StatusText(
            body = statusNotificationHelper.statusBody(
                phase = state?.phase ?: AutoRecognitionState.Phase.BetweenAttempts,
                nowPlayingTitle = track?.title,
                nowPlayingArtist = track?.artist,
                songsFound = state?.sessionTrackIds?.size ?: 0,
            ),
            contentTrackId = track?.id,
            allowLowBattery = state?.phase == AutoRecognitionState.Phase.PausedLowBattery,
        )
    }

    private fun visibleNowPlaying(state: AutoRecognitionState.Running): CachedTrack? {
        val track = cachedTrack ?: return null
        if (state.visibleNowPlayingTrackId() != track.id) return null
        return track
    }

    // Uptime delay. Sleep pauses it and must not wake the phone.
    private fun syncNowPlayingExpiry(state: AutoRecognitionState.Running) {
        val playing = state.lastPlaying
        if (!canNotifyStatus || playing == null) {
            cancelNowPlayingExpiry()
            return
        }
        if (playing.isNowPlayingExpired()) {
            cancelNowPlayingExpiry()
            interactor.clearLastPlaying(playing)
            return
        }
        if (visibleNowPlaying(state) == null) {
            cancelNowPlayingExpiry()
            return
        }
        if (playing == nowPlayingExpiryFor && nowPlayingExpiryJob?.isActive == true) return
        cancelNowPlayingExpiry()
        nowPlayingExpiryFor = playing
        nowPlayingExpiryJob = serviceScope.launch {
            playing.awaitNowPlayingHidden()
            onNowPlayingExpired(playing)
        }
    }

    private fun resyncNowPlayingAfterWake() {
        val playing = nowPlayingExpiryFor ?: return
        // Uptime delay was measured before sleep, so take wall time again.
        cancelNowPlayingExpiry()
        val state = interactor.state.value as? AutoRecognitionState.Running ?: return
        if (!canNotifyStatus || state.lastPlaying != playing) return
        syncNowPlayingExpiry(state)
    }

    private fun onNowPlayingExpired(playing: AutoRecognitionState.LastPlaying) {
        if (nowPlayingExpiryFor == playing) {
            nowPlayingExpiryJob = null
            nowPlayingExpiryFor = null
        }
        if (!canNotifyStatus) return
        interactor.clearLastPlaying(playing)
    }

    private fun cancelNowPlayingExpiry() {
        nowPlayingExpiryJob?.cancel()
        nowPlayingExpiryJob = null
        nowPlayingExpiryFor = null
    }

    private fun rememberTrack(track: Track) {
        cachedTrack = CachedTrack(
            id = track.id,
            title = track.title,
            artist = track.artist,
            artworkThumbUrl = track.artworkThumbUrl,
            artworkUrl = track.artworkUrl,
        )
    }

    private suspend fun publishNewMatch(trackId: String) {
        acquirePublishMatchWakeLock()
        try {
            // A stop during prefetch still posts the match.
            val track = withContext(NonCancellable) {
                trackRepository.getTrackFlow(trackId).first()
            } ?: return
            val fullArtworkUrl = try {
                val url = prepareSufficientArtwork(track)
                if (canNotifyStatus) rememberTrack(track)
                url
            } finally {
                // Post even if canceled or if artwork failed.
                withContext(NonCancellable) { resultNotificationHelper.notifyAutoMatch(track) }
            }
            if (canNotifyStatus && fullArtworkUrl != null) fullArtworkUrls.trySend(fullArtworkUrl)
        } finally {
            releasePublishMatchWakeLock()
        }
    }

    // Prefer the thumb. A different full URL is returned for the background downloader.
    private suspend fun prepareSufficientArtwork(track: Track): String? {
        val thumb = track.artworkThumbUrl
        val full = track.artworkUrl
        if (thumb != null && cacheArtworkAndSeed(track.id, thumb)) {
            return full?.takeIf { it != thumb }
        }
        if (full != null && full != thumb) cacheArtworkAndSeed(track.id, full)
        return null
    }

    private suspend fun cacheArtworkAndSeed(trackId: String, url: String): Boolean {
        return prefetchArtworkAndGenerateSeedColor(url) { color ->
            trackRepository.setThemeSeedColor(trackId, color)
        }
    }

    private fun acquirePublishMatchWakeLock() {
        publishMatchWakeLock.acquire(PUBLISH_WAKE_LOCK_TIMEOUT_MS)
    }

    private fun releasePublishMatchWakeLock() {
        if (publishMatchWakeLock.isHeld) publishMatchWakeLock.release()
    }

    private fun toastBatteryExemptionLost() {
        Toast.makeText(
            this,
            getString(StringsR.string.auto_recognition_battery_unrestricted_required),
            Toast.LENGTH_LONG,
        ).show()
    }

    private data class CachedTrack(
        val id: String,
        val title: String,
        val artist: String,
        val artworkThumbUrl: String?,
        val artworkUrl: String?,
    )

    private data class StatusText(
        val body: String,
        val contentTrackId: String?,
        val allowLowBattery: Boolean,
    )

    companion object {
        private const val TIME_LIMIT_ENABLED = true
        private val TIME_LIMIT = 6.hours
        private const val PUBLISH_WAKE_LOCK_TIMEOUT_MS = 120_000L

        private const val ACTION_START = "com.mrsep.musicrecognizer.auto_recognition.action.start"
        private const val ACTION_STOP = "com.mrsep.musicrecognizer.auto_recognition.action.local.stop"
        private const val ACTION_ALLOW_LOW_BATTERY = "com.mrsep.musicrecognizer.auto_recognition.action.local.allow_low_battery"
        private const val ACTION_TIME_LIMIT = "com.mrsep.musicrecognizer.auto_recognition.action.local.time_limit"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AutoRecognitionService::class.java)
                    .setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AutoRecognitionService::class.java))
        }

        fun stopPendingIntent(context: Context): PendingIntent {
            return localBroadcastPendingIntent(context, ACTION_STOP)
        }

        fun allowLowBatteryPendingIntent(context: Context): PendingIntent {
            return localBroadcastPendingIntent(context, ACTION_ALLOW_LOW_BATTERY)
        }

        private fun localBroadcastPendingIntent(context: Context, action: String): PendingIntent {
            return PendingIntent.getBroadcast(
                context,
                0,
                Intent(action)
                    .setPackage(context.packageName)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}

private fun AutoRecognitionState.Phase.isWaiting(): Boolean = when (this) {
    AutoRecognitionState.Phase.WaitingForNetwork,
    AutoRecognitionState.Phase.PausedLowBattery,
    AutoRecognitionState.Phase.PausedDuringCall,
    AutoRecognitionState.Phase.BetweenAttempts,
    -> true
    AutoRecognitionState.Phase.Recording,
    AutoRecognitionState.Phase.Recognizing,
    -> false
}
