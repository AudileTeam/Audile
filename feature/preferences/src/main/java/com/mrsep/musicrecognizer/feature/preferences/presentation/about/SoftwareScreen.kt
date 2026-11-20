package com.mrsep.musicrecognizer.feature.preferences.presentation.about

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.util.author
import com.mikepenz.aboutlibraries.util.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

@Composable
internal fun SoftwareScreen(
    onNavigateToSoftwareDetailsScreen: (uniqueId: String) -> Unit,
    onBackPressed: () -> Unit,
) {
    val context = LocalContext.current
    val topBarBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    val libs by produceState<Libs?>(null) {
        value = withContext(Dispatchers.IO) {
            Libs.Builder().withContext(context).build()
        }
    }

    Column(
        modifier = Modifier
            .background(color = MaterialTheme.colorScheme.surface)
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        SoftwareScreenTopBar(
            scrollBehavior = topBarBehavior,
            onBackPressed = onBackPressed
        )
        val libraries = libs?.libraries
        if (!libraries.isNullOrEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .nestedScroll(topBarBehavior.nestedScrollConnection)
                    .fillMaxSize(),
            ) {
                itemsIndexed(
                    items = libraries,
//                key = { _, lib -> lib.uniqueId },
                ) { index, library ->
                    Column(modifier = Modifier.animateItem()) {
                        Library(
                            library = library,
                            onClick = { onNavigateToSoftwareDetailsScreen(library.uniqueId) }
                        )
                        if (index != libraries.lastIndex) {
                            HorizontalDivider(modifier = Modifier.alpha(0.2f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Library(
    modifier: Modifier = Modifier,
    library: Library,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 16.dp)
    ) {
        Row {
            Text(
                text = library.name,
                modifier = Modifier
                    .alignByBaseline()
                    .weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val version = library.artifactVersion
            if (version != null) {
                Text(
                    text = version,
                    modifier = Modifier
                        .alignByBaseline()
                        .padding(start = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = library.uniqueId,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val author = library.author
        if (author.isNotBlank()) {
            Text(
                text = author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(8.dp))
        if (library.licenses.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                library.licenses.forEach { license ->
                    LicenseBadge(title = license.name)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun LicenseBadge(
    title: String,
    modifier: Modifier = Modifier,
) {
    Badge(
        modifier = modifier,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        containerColor = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(4.dp),
        )
    }
}

@Composable
private fun SoftwareScreenTopBar(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior,
    onBackPressed: () -> Unit,
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = stringResource(StringsR.string.about_pref_title_licenses).toUpperCase(LocalLocale.current),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
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
