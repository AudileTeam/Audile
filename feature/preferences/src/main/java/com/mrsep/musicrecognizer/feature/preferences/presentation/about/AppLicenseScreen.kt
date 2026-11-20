package com.mrsep.musicrecognizer.feature.preferences.presentation.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.License
import com.mikepenz.aboutlibraries.util.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

@Composable
internal fun AppLicenseScreen(
    onBackPressed: () -> Unit,
) {
    val context = LocalContext.current
    val topBarBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    val license by produceState<License?>(null) {
        value = withContext(Dispatchers.IO) {
            Libs.Builder().withContext(context).build()
                .licenses.find { it.spdxId == "GPL-3.0-or-later" }
        }
    }
    val appLicense = license
    Column(
        modifier = Modifier
            .background(color = MaterialTheme.colorScheme.surface)
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        AppLicenseScreenTopBar(
            scrollBehavior = topBarBehavior,
            onBackPressed = onBackPressed
        )
        if (appLicense != null) {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .nestedScroll(topBarBehavior.nestedScrollConnection)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        .fillMaxSize()
                ) {
                    Text(
                        text = appLicense.name,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    appLicense.licenseContent?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppLicenseScreenTopBar(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior,
    onBackPressed: () -> Unit,
) {
    TopAppBar(
        modifier = modifier,
        title = { },
        navigationIcon = {
            IconButton(onClick = onBackPressed) {
                Icon(
                    painter = painterResource(UiR.drawable.outline_arrow_back_24),
                    contentDescription = stringResource(StringsR.string.nav_back)
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}
