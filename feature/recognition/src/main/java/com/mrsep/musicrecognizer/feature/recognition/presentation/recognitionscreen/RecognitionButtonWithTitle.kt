package com.mrsep.musicrecognizer.feature.recognition.presentation.recognitionscreen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mrsep.musicrecognizer.core.strings.R as StringsR

@Composable
internal fun RecognitionButtonWithTitle(
    title: String,
    activated: Boolean,
    soundLevelState: State<Float>,
    onButtonClick: () -> Unit,
    onButtonLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedContent(
            contentAlignment = Alignment.Center,
            targetState = title,
            transitionSpec = {
                (slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(durationMillis = 350)
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 300, easing = EaseOut)
                )).togetherWith(
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(durationMillis = 350)
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 150, easing = EaseIn)
                    )
                )
            },
            label = "buttonTitleTransition"
        ) {
            Text(
                text = it,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .padding(bottom = 40.dp)
            )
        }

        RecognitionButton(
            onClick = onButtonClick,
            onLongClick = onButtonLongClick,
            activated = activated,
            soundLevelState = soundLevelState,
        )
    }
}

@Composable
private fun RecognitionButton(
    activated: Boolean,
    soundLevelState: State<Float>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        RippleEffect(
            activated = activated,
            modifier = Modifier.size(256.dp),
            startOffsetFraction = 0.5f, // Should be a bit less than buttonSize/rippleSize
            baseColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            activatedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            circlesCount = 7,
            animationSpeed = 12_000,
            scaleEasing = LinearEasing,
            fadeEasing = LinearEasing,
        )
        BasicRecognitionButton(
            modifier = Modifier
                .size(176.dp),
            activated = activated,
            onClick = onClick,
            onLongClick = onLongClick
        ) {
            WaveAnimated(
                activated = activated,
                soundLevelState = soundLevelState,
                properties = WaveAnimatedProperties(
                    baseColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                    activatedColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier
                    .fillMaxHeight(0.65f)
                    .fillMaxWidth(0.9f)
            )
        }
    }
}

@Composable
private fun BasicRecognitionButton(
    activated: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val buttonScaleFactor = remember { Animatable(1f) }
    LaunchedEffect(activated) {
        buttonScaleFactor.animateTo(
            targetValue = 1.1f,
            animationSpec = SpringSpec(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        buttonScaleFactor.animateTo(
            targetValue = 1f,
            animationSpec = SpringSpec(
                dampingRatio = Spring.DampingRatioHighBouncy,
                stiffness = Spring.StiffnessVeryLow
            )
        )
    }
    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = buttonScaleFactor.value
                scaleY = buttonScaleFactor.value
            }
            .shadow(
                elevation = 6.dp,
                shape = CircleShape,
                ambientColor = MaterialTheme.colorScheme.primary,
                spotColor = MaterialTheme.colorScheme.primary
            )
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onClickLabel = stringResource(
                    if (activated) StringsR.string.action_cancel_recognition
                    else StringsR.string.action_recognize
                ),
                onLongClick = onLongClick.takeIf { !activated },
                onLongClickLabel = stringResource(StringsR.string.audio_source_dialog_button_long_press_mode_title).takeIf { !activated },
                role = Role.Button
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(
                    minWidth = ButtonDefaults.MinWidth,
                    minHeight = ButtonDefaults.MinHeight,
                )
                .padding(ButtonDefaults.ContentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}
