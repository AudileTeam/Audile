package com.mrsep.musicrecognizer.feature.preferences.presentation.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mrsep.musicrecognizer.core.common.util.getAppVersionCode
import com.mrsep.musicrecognizer.core.common.util.getAppVersionName
import com.mrsep.musicrecognizer.core.ui.components.preferences.PreferenceClickableItem
import com.mrsep.musicrecognizer.core.ui.components.preferences.PreferenceGroup
import com.mrsep.musicrecognizer.core.ui.util.openUrlImplicitly
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

private const val AUDD_URL = "https://audd.io/"
private const val ACR_CLOUD_URL = "https://www.acrcloud.com/"
private const val SHAZAM_URL = "https://www.shazam.com/company/"
private const val ODESLI_URL = "https://odesli.co/"
private const val GITHUB_REPO_URL = "https://github.com/AudileTeam/Audile"
private const val PRIVACY_POLICY_URL = "https://github.com/AudileTeam/Audile/blob/master/PRIVACY.md"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutScreen(
    onBackPressed: () -> Unit,
    onNavigateToSoftwareScreen: () -> Unit,
    onNavigateToAppLicenseScreen: () -> Unit,
) {
    val context = LocalContext.current
    val topBarBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val donationSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    var showDonationBottomSheet by rememberSaveable { mutableStateOf(false) }
    val version = rememberSaveable {
        context.getAppVersionName() ?: context.getAppVersionCode().toString()
    }
    Column(
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(color = MaterialTheme.colorScheme.surface)
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        AboutScreenTopBar(
            onBackPressed = onBackPressed,
            scrollBehavior = topBarBehavior
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(topBarBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(4.dp))
            AppLogo(
                modifier = Modifier.size(120.dp)
            )
            Text(
                text = stringResource(StringsR.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = version,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(Modifier.height(16.dp))
            PreferenceGroup(title = stringResource(StringsR.string.pref_group_misc)) {
                PreferenceClickableItem(
                    title = stringResource(StringsR.string.about_pref_title_github_repo),
                    subtitle = stringResource(StringsR.string.about_pref_subtitle_github_repo),
                    onItemClick = { context.openUrlImplicitly(GITHUB_REPO_URL) }
                )
                PreferenceClickableItem(
                    title = stringResource(StringsR.string.about_pref_title_privacy_policy),
                    subtitle = stringResource(StringsR.string.about_pref_subtitle_privacy_policy),
                    onItemClick = { context.openUrlImplicitly(PRIVACY_POLICY_URL) }
                )
                PreferenceClickableItem(
                    title = stringResource(StringsR.string.about_pref_title_donation),
                    subtitle = stringResource(StringsR.string.about_pref_subtitle_donation),
                    onItemClick = { showDonationBottomSheet = true }
                )
                PreferenceClickableItem(
                    title = stringResource(StringsR.string.about_pref_title_third_licenses),
                    subtitle = stringResource(StringsR.string.about_pref_subtitle_third_licenses),
                    onItemClick = onNavigateToSoftwareScreen
                )
                PreferenceClickableItem(
                    title = stringResource(StringsR.string.about_pref_title_license),
                    subtitle = stringResource(StringsR.string.about_pref_subtitle_license),
                    onItemClick = onNavigateToAppLicenseScreen
                )
            }
            HorizontalDivider(modifier = Modifier.alpha(0.2f))
            Spacer(Modifier.height(16.dp))
            PreferenceGroup(title = stringResource(StringsR.string.about_pref_group_powered_by)) {
                PreferenceClickableItem(
                    title = stringResource(StringsR.string.audd),
                    subtitle = stringResource(StringsR.string.about_purpose_recognition_service),
                    onItemClick = { context.openUrlImplicitly(AUDD_URL) }
                )
                PreferenceClickableItem(
                    title = stringResource(StringsR.string.acr_cloud),
                    subtitle = stringResource(StringsR.string.about_purpose_recognition_service),
                    onItemClick = { context.openUrlImplicitly(ACR_CLOUD_URL) }
                )
                PreferenceClickableItem(
                    title = stringResource(StringsR.string.shazam),
                    subtitle = stringResource(StringsR.string.about_purpose_recognition_service),
                    onItemClick = { context.openUrlImplicitly(SHAZAM_URL) }
                )
                PreferenceClickableItem(
                    title = stringResource(StringsR.string.odesli),
                    subtitle = stringResource(StringsR.string.about_purpose_track_links_service),
                    onItemClick = { context.openUrlImplicitly(ODESLI_URL) }
                )
            }
        }
    }
    if (showDonationBottomSheet) {
        DonationBottomSheet(
            sheetState = donationSheetState,
            onDismissRequest = { showDonationBottomSheet = false },
        )
    }
}

@Composable
private fun AppLogo(
    modifier: Modifier = Modifier
) {
    val iconBrush = Brush.verticalGradient(
        0.2f to MaterialTheme.colorScheme.onSecondaryContainer,
        1f to MaterialTheme.colorScheme.primary
    )
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_retro_microphone),
            contentDescription = null,
            modifier = Modifier
                .size(84.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        drawRect(
                            brush = iconBrush,
                            blendMode = BlendMode.SrcAtop
                        )
                    }
                }
        )
    }
}
