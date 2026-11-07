package com.mrsep.musicrecognizer.feature.recognition.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import com.mrsep.musicrecognizer.core.common.DeeplinkRouter
import com.mrsep.musicrecognizer.core.domain.recognition.model.AutoRecognitionState
import com.mrsep.musicrecognizer.core.domain.recognition.model.RemoteRecognitionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

class AutoServiceNotificationHelper @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val deeplinkRouter: DeeplinkRouter,
) {

    private val notificationManager = appContext
        .getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager

    fun statusBody(
        phase: AutoRecognitionState.Phase,
        nowPlayingTitle: String?,
        nowPlayingArtist: String?,
        songsFound: Int,
    ): String {
        if (phase == AutoRecognitionState.Phase.PausedDuringCall) {
            return appContext.getString(StringsR.string.notification_auto_recognition_status_during_call)
        }
        if (phase == AutoRecognitionState.Phase.PausedLowBattery) {
            return appContext.getString(StringsR.string.notification_auto_recognition_status_low_battery)
        }
        if (phase == AutoRecognitionState.Phase.WaitingForNetwork) {
            return appContext.getString(StringsR.string.notification_auto_recognition_status_waiting_network)
        }
        if (nowPlayingTitle != null) {
            return appContext.getString(
                StringsR.string.notification_auto_recognition_now_playing,
                nowPlayingTitle,
                nowPlayingArtist.orEmpty(),
            )
        }
        if (songsFound > 0) {
            return appContext.resources.getQuantityString(
                StringsR.plurals.notification_auto_recognition_songs_found,
                songsFound,
                songsFound,
            )
        }
        return appContext.getString(StringsR.string.notification_auto_recognition_status_subtitle_on)
    }

    fun buildStatusNotification(
        body: String,
        largeIcon: Bitmap? = null,
        allowLowBattery: Boolean = false,
        contentTrackId: String? = null,
    ): Notification {
        val title = appContext.getString(StringsR.string.notification_auto_recognition_status_title_on)
        val turnOff = AutoRecognitionService.stopPendingIntent(appContext)
        val contentIntent = contentTrackId?.let { trackId ->
            deepLinkPendingIntent(deeplinkRouter.getDeepLinkIntentToTrack(trackId))
        } ?: openAppPendingIntent()
        return NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID_STATUS)
            .setSmallIcon(UiR.drawable.rounded_auto_recognition_24)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(title).bigText(body))
            .setContentIntent(contentIntent)
            .setDeleteIntent(turnOff)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                appContext.getString(StringsR.string.notification_auto_recognition_action_turn_off),
                turnOff,
            )
            .apply {
                if (allowLowBattery) {
                    addAction(
                        0,
                        appContext.getString(StringsR.string.recognition_queue_low_battery_allow),
                        AutoRecognitionService.allowLowBatteryPendingIntent(appContext),
                    )
                }
                if (largeIcon != null) setLargeIcon(largeIcon)
            }
            .build()
    }

    fun notifyStatus(
        body: String,
        largeIcon: Bitmap? = null,
        allowLowBattery: Boolean = false,
        contentTrackId: String? = null,
    ) {
        if (appContext.isPostNotificationPermissionDenied()) return
        notificationManager.notify(
            NOTIFICATION_ID_STATUS,
            buildStatusNotification(body, largeIcon, allowLowBattery, contentTrackId),
        )
    }

    fun notifyFatal(error: RemoteRecognitionResult.Error) {
        if (appContext.isPostNotificationPermissionDenied()) return
        val (titleRes, messageRes) = error.notificationTitleToMessageRes()
        val title = appContext.getString(titleRes)
        val message = appContext.getString(messageRes)
        val contentIntent = if (error.opensPreferences()) {
            deepLinkPendingIntent(deeplinkRouter.getDeepLinkIntentToPreferences())
        } else {
            openAppPendingIntent()
        }
        val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID_STATUS)
            .setSmallIcon(UiR.drawable.ic_notification_ready)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setOngoing(false)
            .setSilent(true)
            .setCategory(Notification.CATEGORY_ERROR)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(title).bigText(message))
            .setContentIntent(contentIntent)
            .build()
        notificationManager.notify(NOTIFICATION_ID_STATUS, notification)
    }

    fun notifyTimeLimit() {
        if (appContext.isPostNotificationPermissionDenied()) return
        val title = appContext.getString(StringsR.string.notification_auto_recognition_status_title_off)
        val message = appContext.getString(StringsR.string.notification_auto_recognition_status_time_limit)
        val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID_STATUS)
            .setSmallIcon(UiR.drawable.ic_notification_ready)
            .setShowWhen(true)
            .setOngoing(false)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(title).bigText(message))
            .setContentIntent(openAppPendingIntent())
            .addAction(
                0,
                appContext.getString(StringsR.string.notification_auto_recognition_action_turn_on),
                RecognitionControlActivity.startAutoRecognitionPendingIntent(appContext),
            )
            .build()
        notificationManager.notify(NOTIFICATION_ID_STATUS, notification)
    }

    private fun RemoteRecognitionResult.Error.opensPreferences(): Boolean {
        return this is RemoteRecognitionResult.Error.AuthError ||
            this is RemoteRecognitionResult.Error.ApiUsageLimited
    }

    private fun openAppPendingIntent(): PendingIntent? {
        val launch = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
            ?: return null
        return PendingIntent.getActivity(
            appContext,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun deepLinkPendingIntent(intent: Intent): PendingIntent? {
        return TaskStackBuilder.create(appContext).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(
                0,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }

    companion object {
        const val NOTIFICATION_ID_STATUS = 11

        private const val NOTIFICATION_CHANNEL_ID_STATUS = "com.mrsep.musicrecognizer.auto_recognition_status"

        fun getStatusChannel(context: Context): NotificationChannel {
            val name = context.getString(StringsR.string.notification_channel_name_auto_recognition_status)
            return NotificationChannel(
                NOTIFICATION_CHANNEL_ID_STATUS,
                name,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(
                    StringsR.string.notification_channel_desc_auto_recognition_status,
                )
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
        }
    }
}
