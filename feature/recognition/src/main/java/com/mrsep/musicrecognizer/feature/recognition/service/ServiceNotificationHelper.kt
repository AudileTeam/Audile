package com.mrsep.musicrecognizer.feature.recognition.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.mrsep.musicrecognizer.core.ui.R as UiR
import com.mrsep.musicrecognizer.core.strings.R as StringsR

class ServiceNotificationHelper @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {

    private val notificationManager = appContext
        .getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager

    private fun statusNotificationBuilder(): NotificationCompat.Builder {
        return NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID_STATUS)
            .setSmallIcon(UiR.drawable.ic_notification_ready)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(true) // Can be dismissed by user since API 34
            .setCategory(Notification.CATEGORY_SERVICE)
            .setSilent(true) // Avoids alert sound during recording
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDeleteIntent(DisableRecognitionControlServiceReceiver.pendingIntent(appContext))
    }

    fun buildReadyNotification(): Notification {
        val title = appContext.getString(StringsR.string.notification_tap_to_recognize)
        val body = appContext.getString(StringsR.string.notification_tap_to_recognize_subtitle)
        return statusNotificationBuilder()
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(
                RecognitionControlActivity.startRecognitionWithPermissionRequestPendingIntent(appContext)
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(body)
            )
            .apply {
                /* Status notification is truly ongoing for API 33 and below, so add disable button.
                Starting from API 34, users can disable the service by dismissing control notification. */
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        appContext.getString(StringsR.string.notification_button_disable_service),
                        DisableRecognitionControlServiceReceiver.pendingIntent(appContext),
                    )
                } else {
                    addInvisibleAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        appContext.getString(StringsR.string.notification_button_disable_service),
                        DisableRecognitionControlServiceReceiver.pendingIntent(appContext),
                    )
                }
            }
            .build()
    }

    fun buildListeningNotification(extraTry: Boolean): Notification {
        val title = appContext.getString(StringsR.string.notification_listening)
        val body = if (extraTry) {
            appContext.getString(StringsR.string.notification_listening_subtitle_extra_time)
        } else {
            appContext.getString(StringsR.string.notification_listening_subtitle)
        }
        return statusNotificationBuilder()
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(UiR.drawable.ic_notification_listening)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(body)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                appContext.getString(StringsR.string.cancel),
                RecognitionControlService.cancelRecognitionPendingIntent(appContext)
            )
            .build()
    }

    fun notifyListeningStatus(extraTry: Boolean) {
        if (appContext.isPostNotificationPermissionDenied()) return
        notificationManager.notify(NOTIFICATION_ID_STATUS, buildListeningNotification(extraTry))
    }

    fun notifyDetachedReadyStatusIfServiceStopped() {
        if (appContext.isPostNotificationPermissionDenied()) return
        if (hasNotificationAttachedToForegroundService()) return
        notificationManager.notify(NOTIFICATION_ID_STATUS, buildReadyNotification())
    }

    fun cancelDetachedReadyStatus() {
        // This doesn't cancel the notification attached to running service
        notificationManager.cancel(NOTIFICATION_ID_STATUS)
    }

    private fun hasNotificationAttachedToForegroundService(): Boolean {
        return notificationManager.activeNotifications
            .any { it.id == NOTIFICATION_ID_STATUS && it.notification.isAttachedToForegroundService }
    }

    private val Notification.isAttachedToForegroundService
        get() = (flags and Notification.FLAG_FOREGROUND_SERVICE) != 0

    companion object {
        const val NOTIFICATION_ID_STATUS = 1
        private const val NOTIFICATION_CHANNEL_ID_STATUS = "com.mrsep.musicrecognizer.status"

        // TODO: Consider grouping status notifications to prevent accidental auto-grouping with others
//        private const val NOTIFICATION_ID_STATUS_SUMMARY = 101
//        private const val NOTIFICATION_GROUP_KEY_STATUS = "com.mrsep.musicrecognizer.notification_group.status"

        fun getStatusChannel(context: Context): NotificationChannel {
            val name = context.getString(StringsR.string.notification_channel_name_control)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            return NotificationChannel(NOTIFICATION_CHANNEL_ID_STATUS, name, importance).apply {
                description = context.getString(StringsR.string.notification_channel_desc_control)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
        }
    }
}
