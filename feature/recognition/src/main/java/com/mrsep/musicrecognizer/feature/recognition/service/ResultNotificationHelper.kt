package com.mrsep.musicrecognizer.feature.recognition.service

import android.Manifest.permission
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.ActivityCompat.checkSelfPermission
import androidx.core.app.NotificationCompat
import com.mrsep.musicrecognizer.core.domain.preferences.AudioCaptureMode
import com.mrsep.musicrecognizer.core.domain.recognition.ResultNotificationManager
import com.mrsep.musicrecognizer.core.domain.recognition.TrackMetadataFetchManager
import com.mrsep.musicrecognizer.core.domain.recognition.model.EnqueuedRecognition
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionResult
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionTask
import com.mrsep.musicrecognizer.core.domain.recognition.model.RemoteRecognitionResult
import com.mrsep.musicrecognizer.core.domain.track.model.Track
import com.mrsep.musicrecognizer.core.common.DeeplinkRouter
import com.mrsep.musicrecognizer.feature.recognition.service.ext.cachedNotificationArtwork
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

class ResultNotificationHelper @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val deeplinkRouter: DeeplinkRouter,
    private val trackMetadataFetchManager: TrackMetadataFetchManager,
) : ResultNotificationManager {

    private val notificationManager = appContext
        .getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager

    suspend fun notifyForegroundResult(
        result: RecognitionResult,
        usedAudioCaptureMode: AudioCaptureMode,
    ) {
        if (appContext.isPostNotificationPermissionDenied()) return
        notify(result, NOTIFICATION_CHANNEL_ID_FOREGROUND_RESULT, usedAudioCaptureMode)
    }

    suspend fun notifyBackgroundResult(
        result: RecognitionResult,
        usedAudioCaptureMode: AudioCaptureMode,
    ) {
        if (appContext.isPostNotificationPermissionDenied()) return
        notify(result, NOTIFICATION_CHANNEL_ID_BACKGROUND_RESULT, usedAudioCaptureMode)
    }

    private suspend fun notify(
        result: RecognitionResult,
        channelId: String,
        usedAudioCaptureMode: AudioCaptureMode,
    ) {
        var notificationTag: String? = null
        val notificationBuilder = when (result) {
            is RecognitionResult.Error -> {
                val (titleRes, messageRes) = result.remoteError.notificationTitleToMessageRes()
                val errorTitle = appContext.getString(titleRes)
                val errorMessage = appContext.getString(messageRes)
                val bigText = result.recognitionTask.getMessage()?.let { scheduledTaskMessage ->
                    "$errorMessage\n$scheduledTaskMessage"
                } ?: errorMessage
                resultNotificationBuilder(channelId)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentTitle(errorTitle)
                    .setContentText(errorMessage)
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .setBigContentTitle(errorTitle)
                            .bigText(bigText)
                    )
                    .addOptionalQueueScreenDeepLinkIntent(result.recognitionTask)
            }

            is RecognitionResult.ScheduledOffline -> {
                val (title, message) = when (val task = result.recognitionTask) {
                    is RecognitionTask.Created -> {
                        appContext.getString(StringsR.string.result_title_recognition_scheduled) to
                                if (task.launched)
                                    appContext.getString(StringsR.string.result_message_recognition_scheduled)
                                else
                                    appContext.getString(StringsR.string.result_message_recognition_saved)
                    }

                    is RecognitionTask.Error,
                    RecognitionTask.Ignored -> {
                        appContext.getString(StringsR.string.result_title_internal_error) to
                                appContext.getString(StringsR.string.result_message_internal_error)
                    }
                }
                resultNotificationBuilder(channelId)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .setBigContentTitle(title)
                            .bigText(message)
                    )
                    .addOptionalQueueScreenDeepLinkIntent(result.recognitionTask)
            }

            is RecognitionResult.NoMatches -> {
                val title = appContext.getString(StringsR.string.result_title_no_matches)
                val message = appContext.getString(StringsR.string.widget_tap_to_try_again)
                val bigText = result.recognitionTask.getMessage()?.let { scheduledTaskMessage ->
                    "$message\n$scheduledTaskMessage"
                } ?: message
                resultNotificationBuilder(channelId)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .setBigContentTitle(title)
                            .bigText(bigText)
                    )
                    .setContentIntent(
                        RecognitionControlActivity.retryRecognitionWithPermissionRequestPendingIntent(appContext)
                    )
            }

            RecognitionResult.NoSoundDetected -> {
                val title = appContext.getString(StringsR.string.result_title_no_sound_detected)
                val message = appContext.getString(StringsR.string.widget_tap_to_try_again)
                val bigText = when (usedAudioCaptureMode) {
                    AudioCaptureMode.Microphone -> appContext.getString(StringsR.string.result_message_no_sound_detected_microphone)
                    AudioCaptureMode.Device,
                    AudioCaptureMode.Auto -> appContext.getString(StringsR.string.result_message_no_sound_detected_device)
                }
                resultNotificationBuilder(channelId)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .setBigContentTitle(title)
                            .bigText(bigText)
                    )
                    .setContentIntent(
                        RecognitionControlActivity.retryRecognitionWithPermissionRequestPendingIntent(appContext)
                    )
            }

            is RecognitionResult.Success -> {
                notificationTag = result.track.id
                trackMatchBuilder(channelId, result.track)
            }
        }

        postGrouped(channelId, notificationTag, notificationBuilder.build())
    }

    suspend fun notifyResult(enqueuedRecognition: EnqueuedRecognition) {
        if (appContext.isPostNotificationPermissionDenied()) return
        val track = when (val result = enqueuedRecognition.result) {
            is RemoteRecognitionResult.Success -> result.track
            else -> return
        }
        val contentTitle = appContext.getString(StringsR.string.notification_title_match_found)
        val contentText = "${track.title} - ${track.artist}"
        val isLyricsFetcherRunning = trackMetadataFetchManager
            .isLyricsFetcherEnqueuedOrRunning(track.id).first()

        val channelId = NOTIFICATION_CHANNEL_ID_DEFERRED_RESULT
        val groupKey = groupKeyForChannel(channelId)
        val notificationTag = track.id

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(UiR.drawable.ic_notification_ready)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_LARGE)
            .setOnlyAlertOnce(false)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setOngoing(false)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                resultNotificationBuilder(channelId)
                    .setContentTitle(appContext.getString(StringsR.string.notification_title_match_found))
                    .build()
            )
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSubText(formatDateAsSubText(enqueuedRecognition.creationDate))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(contentTitle)
                    .bigText("${track.title}\n${track.artist}")
            )
            .setLargeIconWithTrack(track.artworkThumbUrl, track.artworkUrl)
            .setGroup(groupKey)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
            .addTrackDeepLinkIntent(track.id)
            .addOptionalShowLyricsButton(track, isLyricsFetcherRunning)
            .addShareButton(track.getSharedBody())
            .addTrackInfoToExtras(track)
            .build()

        postGrouped(channelId, notificationTag, notification)
    }

    suspend fun notifyAutoMatch(track: Track) {
        if (appContext.isPostNotificationPermissionDenied()) return
        val channelId = NOTIFICATION_CHANNEL_ID_AUTO_MATCH
        postGrouped(
            channelId = channelId,
            tag = track.id,
            notification = trackMatchBuilder(channelId, track).build(),
        )
    }

    override fun cancelBackgroundMatches(trackIds: Set<String>) {
        cancelActiveNotifications(
            trackIds = trackIds,
            channelIds = setOf(
                NOTIFICATION_CHANNEL_ID_BACKGROUND_RESULT,
                NOTIFICATION_CHANNEL_ID_AUTO_MATCH,
            )
        )
    }

    override fun cancelForegroundMatches(trackIds: Set<String>) {
        cancelActiveNotifications(
            trackIds = trackIds,
            channelIds = setOf(NOTIFICATION_CHANNEL_ID_FOREGROUND_RESULT)
        )
    }

    override fun cancelScheduledRecognitionsMatches(trackIds: Set<String>) {
        cancelActiveNotifications(
            trackIds = trackIds,
            channelIds = setOf(NOTIFICATION_CHANNEL_ID_DEFERRED_RESULT)
        )
    }

    override fun cancelAllMatches(trackIds: Set<String>) {
        cancelActiveNotifications(
            trackIds = trackIds,
            channelIds = setOf(
                NOTIFICATION_CHANNEL_ID_BACKGROUND_RESULT,
                NOTIFICATION_CHANNEL_ID_FOREGROUND_RESULT,
                NOTIFICATION_CHANNEL_ID_DEFERRED_RESULT,
                NOTIFICATION_CHANNEL_ID_AUTO_MATCH,
            )
        )
    }

    override fun cancelUnsuccessfulBackgroundAndForegroundResults() {
        val notificationIds = setOf(
            NOTIFICATION_ID_BACKGROUND_RESULT,
            NOTIFICATION_ID_FOREGROUND_RESULT
        )
        notificationIds.forEach(notificationManager::cancel)
        cancelSummaryForEmptyGroups(
            groupKeys = setOf(
                NOTIFICATION_GROUP_KEY_BACKGROUND_RESULT,
                NOTIFICATION_GROUP_KEY_FOREGROUND_RESULT
            ),
            isPendingCancellation = { groupNotification ->
                groupNotification.id in notificationIds && groupNotification.tag == null
            }
        )
    }

    private fun cancelActiveNotifications(trackIds: Set<String>, channelIds: Set<String>) {
        if (trackIds.size <= 10) {
            channelIds.forEach { channelId ->
                val notificationId = resultNotificationIdForChannel(channelId)
                trackIds.forEach { trackId -> notificationManager.cancel(trackId, notificationId) }
            }
        } else {
            // When removing many tracks at once, it is faster to filter a dozen active notifications
            // than to call cancel() thousands of times for each notification
            notificationManager.activeNotifications
                .filter { sbn -> sbn.tag in trackIds && sbn.notification.channelId in channelIds }
                .forEach { notification ->
                    notificationManager.cancel(notification.tag, notification.id)
                }
        }
        val groupKeys = channelIds.map(::groupKeyForChannel).toSet()
        cancelSummaryForEmptyGroups(
            groupKeys = groupKeys,
            isPendingCancellation = { groupNotification ->  groupNotification.tag in trackIds }
        )
    }

    // This function is called immediately after some group notifications are canceled.
    // Because cancel() is async, those notifications may still appear in NotificationManager.activeNotifications.
    // To avoid that, we pass a filter of notifications that are still pending removal.
    private fun cancelSummaryForEmptyGroups(
        groupKeys: Set<String>,
        isPendingCancellation: (groupNotification: StatusBarNotification) -> Boolean,
    ) {
        val nonEmptyGroupKeys = mutableSetOf<String>()
        notificationManager.activeNotifications.forEach { sbn ->
            if (sbn.notification.group !in groupKeys) return@forEach
            if (sbn.notification.isGroupSummary) return@forEach
            if (isPendingCancellation(sbn)) return@forEach

            sbn.notification.group?.let { groupKey ->
                nonEmptyGroupKeys += groupKey
            }
        }
        groupKeys.forEach { groupKey ->
            if (groupKey !in nonEmptyGroupKeys) {
                notificationManager.cancel(summaryNotificationIdForGroup(groupKey))
            }
        }
    }

    // Notification posting is async, so consecutive notify() calls may briefly
    // observe the group as empty even when a notification is already being sent.
    // The only side effect in our case is an extra summary refresh.
    // And we don't notify results so frequently.
    private fun postGrouped(
        channelId: String,
        tag: String?,
        notification: Notification,
    ) {
        val groupKey = groupKeyForChannel(channelId)
        val isGroupEmpty = isGroupEmpty(groupKey) // Check before notify
        notificationManager.notify(tag, resultNotificationIdForChannel(channelId), notification)
        if (isGroupEmpty) {
            notificationManager.notify(
                summaryNotificationIdForGroup(groupKey),
                buildResultsSummaryNotification(channelId),
            )
        }
    }

    private fun isGroupEmpty(groupKey: String) = notificationManager.activeNotifications
        .none { sbn -> sbn.notification.group == groupKey && !sbn.notification.isGroupSummary }

    private val Notification.isGroupSummary get() = (flags and Notification.FLAG_GROUP_SUMMARY) != 0

    private fun createPendingIntentForDeeplink(intent: Intent): PendingIntent {
        return TaskStackBuilder.create(appContext).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(
                0, // Each deeplink intent has unique data uri
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    private fun formatDateAsSubText(instant: Instant): String? {
        val zoneId = ZoneId.systemDefault()
        val recognitionDate = instant.atZone(zoneId)
        val now = ZonedDateTime.now(zoneId)

        val sameDay = recognitionDate.toLocalDate() == now.toLocalDate()
        if (sameDay) return null

        val pattern = when {
            recognitionDate.year == now.year -> "d MMM"
            else -> "d MMM yyyy"
        }
        return recognitionDate.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    private suspend fun trackMatchBuilder(
        channelId: String,
        track: Track,
    ): NotificationCompat.Builder {
        val isLyricsFetcherRunning = trackMetadataFetchManager
            .isLyricsFetcherEnqueuedOrRunning(track.id).first()
        return resultNotificationBuilder(channelId)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                resultNotificationBuilder(channelId)
                    .setContentTitle(appContext.getString(StringsR.string.notification_title_match_found))
                    .build()
            )
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(track.title)
                    .bigText(track.artist)
            )
            .setLargeIconWithTrack(track.artworkThumbUrl, track.artworkUrl)
            .addTrackDeepLinkIntent(track.id)
            .addOptionalShowLyricsButton(track, isLyricsFetcherRunning)
            .addShareButton(track.getSharedBody())
            .addTrackInfoToExtras(track)
    }

    private suspend fun NotificationCompat.Builder.setLargeIconWithTrack(
        artworkThumbUrl: String?,
        artworkUrl: String?,
    ): NotificationCompat.Builder {
        val largeIcon = appContext.cachedNotificationArtwork(artworkThumbUrl, artworkUrl)
            ?: return this
        return setLargeIcon(largeIcon)
    }

    private fun NotificationCompat.Builder.addTrackDeepLinkIntent(
        trackId: String,
    ): NotificationCompat.Builder {
        val deepLinkIntent = deeplinkRouter.getDeepLinkIntentToTrack(trackId)
        val pendingIntent = createPendingIntentForDeeplink(deepLinkIntent)
        return setContentIntent(pendingIntent)
    }

    private fun NotificationCompat.Builder.addOptionalQueueScreenDeepLinkIntent(
        recognitionTask: RecognitionTask?,
    ): NotificationCompat.Builder {
        return when (recognitionTask) {
            is RecognitionTask.Created -> {
                val deepLinkIntent = deeplinkRouter.getDeepLinkIntentToQueue()
                val pendingIntent = createPendingIntentForDeeplink(deepLinkIntent)
                setContentIntent(pendingIntent)
            }

            is RecognitionTask.Error,
            RecognitionTask.Ignored,
            null -> this
        }
    }

    private fun NotificationCompat.Builder.addLibraryScreenDeepLink(): NotificationCompat.Builder {
        val deepLinkIntent = deeplinkRouter.getDeepLinkIntentToLibrary()
        val pendingIntent = createPendingIntentForDeeplink(deepLinkIntent)
        return setContentIntent(pendingIntent)
    }

    private fun NotificationCompat.Builder.addOptionalShowLyricsButton(
        track: Track,
        isLyricsFetcherRunning: Boolean,
    ): NotificationCompat.Builder {
        if (track.lyrics == null && !isLyricsFetcherRunning) return this
        val deepLinkIntent = deeplinkRouter.getDeepLinkIntentToLyrics(track.id)
        val pendingIntent = createPendingIntentForDeeplink(deepLinkIntent)
        return addAction(
            android.R.drawable.ic_menu_more,
            appContext.getString(StringsR.string.notification_button_show_lyrics),
            pendingIntent
        )
    }

    private fun NotificationCompat.Builder.addShareButton(
        sharedText: String,
    ): NotificationCompat.Builder {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sharedText)
        }
        val wrappedIntent = Intent.createChooser(intent, null)
        return addAction(
            android.R.drawable.ic_menu_share,
            appContext.getString(StringsR.string.share),
            PendingIntent.getActivity(
                appContext,
                System.currentTimeMillis().toInt(),
                wrappedIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    private fun NotificationCompat.Builder.addTrackInfoToExtras(
        track: Track
    ): NotificationCompat.Builder {
        return addExtras(
            Bundle().apply {
                putString(BUNDLE_KEY_TRACK_ID, track.id)
                putString(BUNDLE_KEY_TRACK_TITLE, track.title)
                putString(BUNDLE_KEY_TRACK_ARTIST, track.artist)
                track.isrc?.let { putString(BUNDLE_KEY_TRACK_ISRC, it) }
                track.album?.let { putString(BUNDLE_KEY_TRACK_ALBUM, it) }
                track.releaseDate?.toString()?.let { putString(BUNDLE_KEY_TRACK_RELEASE_DATE, it) }
                putString(BUNDLE_KEY_TRACK_SAMPLE_TIMESTAMP, track.recognitionDate.toString())
                track.duration?.inWholeMilliseconds?.let { putLong(BUNDLE_KEY_TRACK_DURATION, it) }
                track.recognizedAt?.inWholeMilliseconds?.let { putLong(BUNDLE_KEY_TRACK_PLAYBACK_OFFSET, it) }
            }
        )
    }

    private fun resultNotificationBuilder(channelId: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(UiR.drawable.ic_notification_ready)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_LARGE)
            .setOnlyAlertOnce(false)
            .setShowWhen(true)
            .setOngoing(false)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setGroup(groupKeyForChannel(channelId))
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
    }

    private fun resultsSummaryNotificationBuilder(channelId: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(UiR.drawable.ic_notification_ready)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_LARGE)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setOngoing(false)
            .setAutoCancel(true)
            .setSilent(true)
            .setGroup(groupKeyForChannel(channelId))
            .setGroupSummary(true)
            .addLibraryScreenDeepLink()
    }

    private fun buildResultsSummaryNotification(channelId: String): Notification {
        return resultsSummaryNotificationBuilder(channelId)
            .setPublicVersion(
                resultsSummaryNotificationBuilder(channelId)
                    .setContentTitle(appContext.getString(StringsR.string.notification_title_match_found))
                    .build()
            )
            .build()
    }

    private fun RecognitionTask.getMessage(): String? = when (this) {
        is RecognitionTask.Created -> if (launched)
            appContext.getString(StringsR.string.result_message_recognition_scheduled)
        else
            appContext.getString(StringsR.string.result_message_recognition_saved)

        is RecognitionTask.Error,
        RecognitionTask.Ignored -> null
    }

    private fun resultNotificationIdForChannel(channelId: String) = when (channelId) {
        NOTIFICATION_CHANNEL_ID_BACKGROUND_RESULT -> NOTIFICATION_ID_BACKGROUND_RESULT
        NOTIFICATION_CHANNEL_ID_FOREGROUND_RESULT -> NOTIFICATION_ID_FOREGROUND_RESULT
        NOTIFICATION_CHANNEL_ID_DEFERRED_RESULT -> NOTIFICATION_ID_DEFERRED_RESULT
        NOTIFICATION_CHANNEL_ID_AUTO_MATCH -> NOTIFICATION_ID_AUTO_MATCH
        else -> error("Unknown notification channel id")
    }

    private fun groupKeyForChannel(channelId: String) = when (channelId) {
        NOTIFICATION_CHANNEL_ID_BACKGROUND_RESULT -> NOTIFICATION_GROUP_KEY_BACKGROUND_RESULT
        NOTIFICATION_CHANNEL_ID_FOREGROUND_RESULT -> NOTIFICATION_GROUP_KEY_FOREGROUND_RESULT
        NOTIFICATION_CHANNEL_ID_DEFERRED_RESULT -> NOTIFICATION_GROUP_KEY_DEFERRED_RESULT
        NOTIFICATION_CHANNEL_ID_AUTO_MATCH -> NOTIFICATION_GROUP_KEY_AUTO_MATCH
        else -> error("Unknown notification channel id")
    }

    private fun summaryNotificationIdForGroup(groupKey: String) = when (groupKey) {
        NOTIFICATION_GROUP_KEY_BACKGROUND_RESULT -> NOTIFICATION_ID_BACKGROUND_RESULT_SUMMARY
        NOTIFICATION_GROUP_KEY_FOREGROUND_RESULT -> NOTIFICATION_ID_FOREGROUND_RESULT_SUMMARY
        NOTIFICATION_GROUP_KEY_DEFERRED_RESULT -> NOTIFICATION_ID_DEFERRED_RESULT_SUMMARY
        NOTIFICATION_GROUP_KEY_AUTO_MATCH -> NOTIFICATION_ID_AUTO_MATCH_SUMMARY
        else -> error("Unknown group key")
    }

    private fun Track.getSharedBody() = "$title - $artist"

    companion object {
        private const val NOTIFICATION_ID_BACKGROUND_RESULT = 2
        private const val NOTIFICATION_ID_FOREGROUND_RESULT = 3
        private const val NOTIFICATION_ID_DEFERRED_RESULT = 4
        private const val NOTIFICATION_ID_AUTO_MATCH = 12

        private const val NOTIFICATION_ID_BACKGROUND_RESULT_SUMMARY = 102
        private const val NOTIFICATION_ID_FOREGROUND_RESULT_SUMMARY = 103
        private const val NOTIFICATION_ID_DEFERRED_RESULT_SUMMARY = 104
        private const val NOTIFICATION_ID_AUTO_MATCH_SUMMARY = 112

        private const val NOTIFICATION_GROUP_KEY_BACKGROUND_RESULT = "com.mrsep.musicrecognizer.notification_group.background_result"
        private const val NOTIFICATION_GROUP_KEY_FOREGROUND_RESULT = "com.mrsep.musicrecognizer.notification_group.foreground_result"
        private const val NOTIFICATION_GROUP_KEY_DEFERRED_RESULT = "com.mrsep.musicrecognizer.notification_group.deferred_result"
        private const val NOTIFICATION_GROUP_KEY_AUTO_MATCH = "com.mrsep.musicrecognizer.notification_group.auto_recognition_match"

        private const val NOTIFICATION_CHANNEL_ID_BACKGROUND_RESULT = "com.mrsep.musicrecognizer.result"
        private const val NOTIFICATION_CHANNEL_ID_FOREGROUND_RESULT = "com.mrsep.musicrecognizer.foreground_result"
        private const val NOTIFICATION_CHANNEL_ID_DEFERRED_RESULT = "com.mrsep.musicrecognizer.enqueued_result"
        private const val NOTIFICATION_CHANNEL_ID_AUTO_MATCH = "com.mrsep.musicrecognizer.auto_recognition_match"

        private const val BUNDLE_KEY_TRACK_ID = "com.mrsep.musicrecognizer.track_metadata.id"
        private const val BUNDLE_KEY_TRACK_TITLE = "com.mrsep.musicrecognizer.track_metadata.title"
        private const val BUNDLE_KEY_TRACK_ARTIST = "com.mrsep.musicrecognizer.track_metadata.artist"
        private const val BUNDLE_KEY_TRACK_ISRC = "com.mrsep.musicrecognizer.track_metadata.isrc"
        private const val BUNDLE_KEY_TRACK_ALBUM = "com.mrsep.musicrecognizer.track_metadata.album"
        private const val BUNDLE_KEY_TRACK_RELEASE_DATE = "com.mrsep.musicrecognizer.track_metadata.release_date"
        private const val BUNDLE_KEY_TRACK_DURATION = "com.mrsep.musicrecognizer.track_metadata.duration"
        private const val BUNDLE_KEY_TRACK_SAMPLE_TIMESTAMP = "com.mrsep.musicrecognizer.track_metadata.sample_timestamp"
        private const val BUNDLE_KEY_TRACK_PLAYBACK_OFFSET = "com.mrsep.musicrecognizer.track_metadata.playback_offset"

        fun getChannelForBackgroundRecognitionResult(context: Context): NotificationChannel {
            val name = context.getString(StringsR.string.notification_channel_name_background_result)
            val importance = NotificationManager.IMPORTANCE_HIGH
            return NotificationChannel(NOTIFICATION_CHANNEL_ID_BACKGROUND_RESULT, name, importance).apply {
                description = context.getString(StringsR.string.notification_channel_desc_background_result)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(100, 100, 100, 100)
            }
        }

        fun getChannelForForegroundRecognitionResult(context: Context): NotificationChannel {
            val name = context.getString(StringsR.string.notification_channel_name_foreground_result)
            val importance = NotificationManager.IMPORTANCE_NONE
            return NotificationChannel(NOTIFICATION_CHANNEL_ID_FOREGROUND_RESULT, name, importance).apply {
                description = context.getString(StringsR.string.notification_channel_desc_foreground_result)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                vibrationPattern = longArrayOf(100, 100, 100, 100)
            }
        }

        fun getChannelForScheduledRecognitionResult(context: Context): NotificationChannel {
            val name = context.getString(StringsR.string.notification_channel_name_scheduled_result)
            val importance = NotificationManager.IMPORTANCE_HIGH
            return NotificationChannel(
                NOTIFICATION_CHANNEL_ID_DEFERRED_RESULT,
                name,
                importance
            ).apply {
                description = context.getString(StringsR.string.notification_channel_desc_scheduled_result)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(100, 100, 100, 100)
            }
        }

        fun getChannelForAutoRecognitionMatch(context: Context): NotificationChannel {
            val name = context.getString(StringsR.string.notification_channel_name_auto_recognition_result)
            val importance = NotificationManager.IMPORTANCE_LOW
            return NotificationChannel(NOTIFICATION_CHANNEL_ID_AUTO_MATCH, name, importance).apply {
                description = context.getString(StringsR.string.notification_channel_desc_auto_recognition_result)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                enableLights(false)
                enableVibration(false)
            }
        }
    }
}

internal fun Context.isPostNotificationPermissionDenied(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(this, permission.POST_NOTIFICATIONS) == PERMISSION_DENIED
}

internal fun RemoteRecognitionResult.Error.notificationTitleToMessageRes(): Pair<Int, Int> {
    return when (this) {
        RemoteRecognitionResult.Error.BadConnection ->
            StringsR.string.result_title_bad_connection to
                    StringsR.string.result_message_bad_connection
        RemoteRecognitionResult.Error.AuthError ->
            StringsR.string.result_title_auth_error to
                    StringsR.string.result_message_auth_error
        RemoteRecognitionResult.Error.ApiUsageLimited ->
            StringsR.string.result_title_service_usage_limited to
                    StringsR.string.result_message_service_usage_limited
        is RemoteRecognitionResult.Error.BadRecording ->
            StringsR.string.result_title_recording_error to
                    StringsR.string.result_message_recording_error
        is RemoteRecognitionResult.Error.HttpError ->
            StringsR.string.result_title_bad_network_response to
                    StringsR.string.result_message_bad_network_response
        is RemoteRecognitionResult.Error.UnhandledError ->
            StringsR.string.result_title_internal_error to
                    StringsR.string.result_message_internal_error
    }
}