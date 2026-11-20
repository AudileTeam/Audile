package com.mrsep.musicrecognizer.feature.developermode.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
internal fun DeveloperScreen(
    onBackPressed: () -> Unit,
    viewModel: DeveloperViewModel = hiltViewModel()
) {
    val topBarBehaviour = TopAppBarDefaults.pinnedScrollBehavior()
    val isProcessing = false
    Column(
        modifier = Modifier
            .background(color = MaterialTheme.colorScheme.surface)
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        DeveloperScreenTopBar(
            scrollBehavior = topBarBehaviour,
            onBackPressed = onBackPressed
        )
        AnimatedVisibility(isProcessing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Column(
            modifier = Modifier
                .nestedScroll(topBarBehaviour.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

        }
    }
}

@Composable
private fun ButtonGroup(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    content: (@Composable () -> Unit)? = null,
    buttons: @Composable RowScope.() -> Unit
) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            subtitle?.let {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            content?.let {
                content()
            }
            FlowRow(
                verticalArrangement = Arrangement.Center,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                content = buttons
            )
        }
    }
}
