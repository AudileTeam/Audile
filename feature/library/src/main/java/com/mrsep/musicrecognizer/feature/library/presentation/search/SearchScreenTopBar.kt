package com.mrsep.musicrecognizer.feature.library.presentation.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.maxLengthReject
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.mrsep.musicrecognizer.core.domain.track.model.TrackDataField
import kotlinx.collections.immutable.ImmutableSet
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

@Composable
internal fun SearchScreenTopBar(
    queryState: TextFieldState,
    onBackPressed: () -> Unit,
    onSearchScopeChanged: (Set<TrackDataField>) -> Unit,
    searchScope: ImmutableSet<TrackDataField>,
    focusRequester: FocusRequester,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(),
        modifier = modifier,
        title = {
            val interactionSource = remember { MutableInteractionSource() }
            BasicTextField(
                state = queryState,
                modifier = modifier
                    .height(SearchBarDefaults.InputFieldHeight)
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = LocalTextStyle.current.color.takeOrElse {
                        MaterialTheme.colorScheme.onSurface
                    }
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = InputTransformation.maxLengthReject(
                    LibrarySearchViewModel.SEARCH_QUERY_MAX_LENGTH
                ),
                interactionSource = interactionSource,
                decorator = TextFieldDefaults.decorator(
                    state = queryState,
                    enabled = true,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    outputTransformation = null,
                    interactionSource = interactionSource,
                    placeholder = { Text(stringResource(StringsR.string.library_search_hint)) },
                    colors = SearchBarDefaults.inputFieldColors(),
                    contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(),
                    container = {},
                ),
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
        actions = {
            Row {
                AnimatedVisibility(
                    visible = queryState.text.isNotBlank(),
                    enter = fadeIn(spring()),
                    exit = fadeOut(spring()),
                ) {
                    IconButton(onClick = { queryState.clearText() }) {
                        Icon(
                            painter = painterResource(UiR.drawable.outline_close_24),
                            contentDescription = stringResource(StringsR.string.clear_text_field)
                        )
                    }
                }
                SearchScopeDropdownMenu(
                    searchScope = searchScope,
                    onSearchScopeChanged = onSearchScopeChanged,
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}
