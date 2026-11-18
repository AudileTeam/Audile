package com.mrsep.musicrecognizer.feature.backup.presentation

import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.mrsep.musicrecognizer.core.common.APP_DEEP_LINK_BASE
import com.mrsep.musicrecognizer.core.ui.navigation.HideSplashWhenResumed
import com.mrsep.musicrecognizer.core.ui.navigation.ifResumed
import kotlinx.serialization.Serializable

fun EntryProviderScope<NavKey>.backupRestoreEntry(
    onBackPressed: () -> Unit,
    hideSplashScreen: () -> Unit,
) {
    entry<BackupRestoreRoute> {
        HideSplashWhenResumed(hideSplashScreen)
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        BackupRestoreScreen(
            onBackPressed = { lifecycle.ifResumed(onBackPressed) },
        )
    }
}

@Serializable
data object BackupRestoreRoute : NavKey {

    fun deepLinkUri(): String = "$APP_DEEP_LINK_BASE/backup_restore"
}
