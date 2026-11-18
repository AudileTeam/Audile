package com.mrsep.musicrecognizer.presentation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import com.mrsep.musicrecognizer.feature.library.presentation.library.LibraryRoute
import com.mrsep.musicrecognizer.feature.preferences.presentation.PreferencesRoute
import com.mrsep.musicrecognizer.feature.recognition.presentation.queuescreen.QueueRoute
import com.mrsep.musicrecognizer.feature.recognition.presentation.recognitionscreen.RecognitionRoute
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

enum class TopLevelDestination(
    val key: NavKey,
    @StringRes val titleResId: Int,
    @DrawableRes val selectedIconResId: Int,
    @DrawableRes val unselectedIconResId: Int,
) {
    Recognition(
        key = RecognitionRoute,
        titleResId = StringsR.string.recognition,
        selectedIconResId = UiR.drawable.ic_lines_24,
        unselectedIconResId = UiR.drawable.ic_lines_24,
    ),
    Library(
        key = LibraryRoute,
        titleResId = StringsR.string.library,
        selectedIconResId = UiR.drawable.outline_library_music_fill1_24,
        unselectedIconResId = UiR.drawable.outline_library_music_24,
    ),
    Queue(
        key = QueueRoute,
        titleResId = StringsR.string.queue,
        selectedIconResId = UiR.drawable.outline_overview_fill1_24,
        unselectedIconResId = UiR.drawable.outline_overview_24,
    ),
    Preferences(
        key = PreferencesRoute,
        titleResId = StringsR.string.preferences,
        selectedIconResId = UiR.drawable.outline_settings_fill1_24,
        unselectedIconResId = UiR.drawable.outline_settings_24,
    );

    companion object {
        val keys: List<NavKey> = entries.map { it.key }
    }
}
