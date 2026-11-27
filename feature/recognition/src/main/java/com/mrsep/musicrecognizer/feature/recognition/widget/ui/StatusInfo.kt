package com.mrsep.musicrecognizer.feature.recognition.widget.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionResult
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionStatus
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionTask
import com.mrsep.musicrecognizer.core.domain.recognition.model.RemoteRecognitionResult
import com.mrsep.musicrecognizer.core.strings.R
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.contentPadding
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.statusExtraStartPadding
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.subtitleTextSize
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.subtitleTopPadding
import com.mrsep.musicrecognizer.feature.recognition.widget.ui.RecognitionWidgetLayout.Companion.titleTextSize
import com.mrsep.musicrecognizer.feature.recognition.widget.util.textCenteringPadding

@Composable
internal fun RowScope.StatusInfo(
    title: String,
    subtitle: String? = null,
    titleMaxLines: Int = 1,
    subtitleMaxLines: Int = 2,
) {
    Column(
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start,
        modifier = GlanceModifier
            .defaultWeight()
            .fillMaxHeight()
            .padding(start = statusExtraStartPadding)
            .padding(contentPadding)
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = titleTextSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Start,
            ),
            maxLines = titleMaxLines,
            modifier = GlanceModifier.fillMaxWidth()
        )
        subtitle?.let {
            Spacer(GlanceModifier.height(subtitleTopPadding))
            Text(
                text = it,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = subtitleTextSize,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Start,
                ),
                maxLines = subtitleMaxLines,
                modifier = GlanceModifier.fillMaxWidth()
            )
        }
        TextCenteringPadding(subtitle)
    }
}

@Composable
internal fun TextCenteringPadding(subtitle: String?) {
    val padding = textCenteringPadding(LocalContext.current, subtitle != null)
    if (padding > 0.dp) {
        Spacer(GlanceModifier.height(padding))
    }
}

internal fun Context.getWidgetTitleForStatus(status: RecognitionStatus) = when (status) {
    RecognitionStatus.Ready -> getString(R.string.widget_tap_to_recognize)
    is RecognitionStatus.Recognizing -> getString(R.string.widget_listening)
    is RecognitionStatus.Done -> when (val recognitionResult = status.result) {
        is RecognitionResult.Success -> recognitionResult.track.title
        is RecognitionResult.NoMatches -> getString(R.string.result_title_no_matches)
        RecognitionResult.NoSoundDetected -> getString(R.string.result_title_no_sound_detected)
        is RecognitionResult.ScheduledOffline -> getString(R.string.result_title_recognition_scheduled)
        is RecognitionResult.Error -> {
            when (recognitionResult.remoteError) {
                RemoteRecognitionResult.Error.ApiUsageLimited -> getString(R.string.result_title_service_usage_limited)
                RemoteRecognitionResult.Error.AuthError -> getString(R.string.result_title_auth_error)
                RemoteRecognitionResult.Error.BadConnection -> getString(R.string.result_title_bad_connection)
                is RemoteRecognitionResult.Error.BadRecording -> getString(R.string.result_title_recording_error)
                is RemoteRecognitionResult.Error.HttpError -> getString(R.string.result_title_bad_network_response)
                is RemoteRecognitionResult.Error.UnhandledError -> getString(R.string.result_title_internal_error)
            }
        }
    }
}

internal fun Context.getWidgetSubtitleForStatus(status: RecognitionStatus) = when (status) {
    RecognitionStatus.Ready -> null
    is RecognitionStatus.Recognizing -> if (status.extraTime) {
        getString(R.string.widget_listening_subtitle_extra_time)
    } else {
        getString(R.string.widget_listening_subtitle)
    }

    is RecognitionStatus.Done -> when (val recognitionResult = status.result) {
        is RecognitionResult.Success -> recognitionResult.track.artist
        is RecognitionResult.NoMatches -> getString(R.string.widget_tap_to_try_again)
        RecognitionResult.NoSoundDetected -> getString(R.string.widget_tap_to_try_again)
        is RecognitionResult.ScheduledOffline -> getSubtitle(recognitionResult.recognitionTask)
        is RecognitionResult.Error -> {
            when (recognitionResult.remoteError) {
                RemoteRecognitionResult.Error.ApiUsageLimited -> getSubtitle(recognitionResult.recognitionTask)
                    ?: getString(R.string.result_message_service_usage_limited)

                RemoteRecognitionResult.Error.AuthError -> getSubtitle(recognitionResult.recognitionTask)
                    ?: getString(R.string.result_message_auth_error)

                RemoteRecognitionResult.Error.BadConnection -> getSubtitle(recognitionResult.recognitionTask)
                    ?: getString(R.string.result_title_bad_connection)

                is RemoteRecognitionResult.Error.BadRecording -> getSubtitle(recognitionResult.recognitionTask)
                    ?: getString(R.string.result_message_recording_error)

                is RemoteRecognitionResult.Error.HttpError -> getSubtitle(recognitionResult.recognitionTask)
                    ?: getString(R.string.result_message_bad_network_response)

                is RemoteRecognitionResult.Error.UnhandledError -> getSubtitle(recognitionResult.recognitionTask)
                    ?: getString(R.string.result_message_internal_error)
            }
        }
    }
}

private fun Context.getSubtitle(task: RecognitionTask): String? {
    return when (task) {
        is RecognitionTask.Created -> if (task.launched) {
            getString(R.string.result_message_recognition_scheduled)
        } else {
            getString(R.string.result_message_recognition_saved)
        }

        RecognitionTask.Ignored,
        is RecognitionTask.Error,
        -> null
    }
}
