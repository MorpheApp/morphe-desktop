/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.morphe.engine.model.VersionStatus
import app.morphe.gui.ui.components.DropPromptContent
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.screens.home.ApkInfo
import app.morphe.gui.ui.screens.home.HomeUiState
import app.morphe.gui.ui.theme.MorpheMotion
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.StatusColorType
import app.morphe.gui.util.resolveStatusColorType
import app.morphe.gui.util.toColor
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MiddleContent(
    uiState: HomeUiState,
    patchesLoaded: Boolean,
    onClearClick: () -> Unit,
    onChangeClick: () -> Unit,
    onContinueClick: () -> Unit,
    patchSourceNames: List<String> = emptyList(),
) {
    when {
        uiState.isAnalyzing -> {
            AnalyzingSection()
        }
        uiState.apkInfo != null -> {
            ApkSelectedSection(
                patchesLoaded = patchesLoaded,
                apkInfo = uiState.apkInfo,
                onClearClick = onClearClick,
                onChangeClick = onChangeClick,
                onContinueClick = onContinueClick,
                patchSourceNames = patchSourceNames,
            )
        }
        else -> {
            DropPromptSection(
                isDragHovering = uiState.isDragHovering,
                onBrowseClick = onChangeClick
            )
        }
    }
}

@Composable
internal fun DropPromptSection(
    isDragHovering: Boolean,
    onBrowseClick: () -> Unit
) {
    val accents = MorpheTheme.colors
    val bracketColor = if (isDragHovering) accents.primary.copy(alpha = 0.7f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val targetBgColor = if (isDragHovering) accents.primary.copy(alpha = MorpheTheme.alphas.hover) else Color.Transparent
    val animatedBgColor by animateColorAsState(targetValue = targetBgColor, animationSpec = MorpheMotion.fastTween())

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
    ) {
        val mediumDp = MorpheTheme.shapes.mediumDp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.4f)
                .clip(MorpheTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .background(animatedBgColor)
                .clickable { onBrowseClick() }
                .handCursor()
                .drawBehind {
                    drawRoundRect(
                        color = bracketColor,
                        cornerRadius = CornerRadius(mediumDp.toPx()),
                        style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 24f), 0f))
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            DropPromptContent(isDragHovering = isDragHovering)
        }
    }
}

@Composable
internal fun ApkSelectedSection(
    patchesLoaded: Boolean,
    apkInfo: ApkInfo,
    onClearClick: () -> Unit,
    onChangeClick: () -> Unit,
    onContinueClick: () -> Unit,
    patchSourceNames: List<String> = emptyList(),
) {
    val accents = MorpheTheme.colors
    val statusColorType = resolveStatusColorType(apkInfo.versionStatus, apkInfo.checksumStatus)
    
    val showWarning = apkInfo.versionStatus != VersionStatus.LATEST_STABLE &&
                      apkInfo.versionStatus != VersionStatus.UNKNOWN
    val isWarningState = showWarning || apkInfo.isUnsupportedApp || apkInfo.hasLimitedInfo
    val buttonColor = if (isWarningState) {
        if (statusColorType == StatusColorType.PRIMARY) accents.warning else statusColorType.toColor()
    } else accents.primary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = 500.dp)
    ) {
        ApkInfoCard(
            apkInfo = apkInfo,
            onClearClick = onClearClick,
            modifier = Modifier.fillMaxWidth(),
            patchSourceNames = patchSourceNames,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MorpheButton(
                label = stringResource(Res.string.home_stage_change_apk),
                size = MorpheButtonSize.LARGE,
                variant = MorpheButtonVariant.OUTLINED,
                onClick = onChangeClick,
            )
            MorpheButton(
                onClick = onContinueClick,
                enabled = patchesLoaded,
                size = MorpheButtonSize.LARGE,
                variant = MorpheButtonVariant.FILLED,
                customColor = buttonColor,
                modifier = Modifier.widthIn(min = 160.dp),
            ) {
                if (!patchesLoaded) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.status_loading),
                        style = MorpheTheme.typography.labelLarge,
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.home_stage_continue),
                        style = MorpheTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AnalyzingSection() {
    val accents = MorpheTheme.colors

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = accents.primary,
            strokeWidth = 2.dp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(Res.string.home_stage_analyzing_title),
            style = MorpheTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(Res.string.home_stage_reading_metadata),
            style = MorpheTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
