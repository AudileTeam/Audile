package com.mrsep.musicrecognizer.feature.preferences.presentation.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.util.author
import com.mikepenz.aboutlibraries.util.withContext
import com.mrsep.musicrecognizer.core.ui.util.openUrlImplicitly
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

@Composable
internal fun SoftwareDetailsScreen(
    uniqueId: String,
    onBackPressed: () -> Unit,
) {
    val context = LocalContext.current
    val topBarBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    val lib by produceState<Library?>(null) {
        value = withContext(Dispatchers.IO) {
            Libs.Builder().withContext(context).build()
                .libraries.find { it.uniqueId == uniqueId }
        }
    }
    val library = lib
    val licenses = library?.licenses ?: persistentSetOf()
    var selectedLicense by remember(licenses) { mutableStateOf(licenses.firstOrNull()) }

    Column(
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(color = MaterialTheme.colorScheme.surface)
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        SoftwareDetailsScreenTopBar(
            onBackPressed = onBackPressed,
            scrollBehavior = topBarBehavior,
            website = library?.website,
        )
        if (library != null) {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .nestedScroll(topBarBehavior.nestedScrollConnection)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        .fillMaxSize()
                ) {
                    Text(
                        text = library.name,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = library.uniqueId,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    library.author.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    library.description?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        licenses.forEach { license ->
                            FilterChip(
                                selected = license == selectedLicense,
                                onClick = { selectedLicense = license },
                                label = { Text(text = license.name) }
                            )
                        }
                    }
                    selectedLicense?.licenseContent?.takeIf { it.isNotBlank() }?.let {
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
internal fun SoftwareDetailsScreenTopBar(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior,
    website: String?,
    onBackPressed: () -> Unit,
) {
    val context = LocalContext.current
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
        actions = {
            website?.let {
                IconButton(
                    onClick = { context.openUrlImplicitly(website) }
                ) {
                    Icon(
                        painter = painterResource(UiR.drawable.outline_link_24),
                        contentDescription = stringResource(StringsR.string.web_search)
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior
    )
}
