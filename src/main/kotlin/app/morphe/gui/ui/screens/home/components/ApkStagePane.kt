/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.ApkInfo
import app.morphe.gui.ui.screens.home.HomeUiState
import app.morphe.gui.ui.theme.contrastingForeground
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.util.StatusColorType
import app.morphe.gui.util.VersionStatus
import app.morphe.gui.util.resolveStatusColorType
import app.morphe.gui.util.toColor

// ============================================================================
// APK STAGE (drop zone, APK info, analyzing)
// ============================================================================

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

// ============================================================================
// DROP ZONE (corner brackets, scanner/targeting aesthetic)
// ============================================================================

@Composable
internal fun DropPromptSection(
    isDragHovering: Boolean,
    onBrowseClick: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val bracketColor = if (isDragHovering) accents.primary.copy(alpha = 0.7f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val targetBgColor = if (isDragHovering) accents.primary.copy(alpha = 0.08f) else Color.Transparent
    val animatedBgColor by animateColorAsState(targetValue = targetBgColor, animationSpec = tween(150))

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
    ) {
        // Drop zone
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.4f)
                .clip(RoundedCornerShape(corners.medium))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .background(animatedBgColor)
                .clickable { onBrowseClick() }
                .drawBehind {
                    drawRoundRect(
                        color = bracketColor,
                        cornerRadius = CornerRadius(corners.medium.toPx()),
                        style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 24f), 0f))
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = MorpheIcons.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = if (isDragHovering) accents.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isDragHovering) "Release to drop" else "Drop APK here",
                    fontSize = 18.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDragHovering) accents.primary
                           else MaterialTheme.colorScheme.onSurface
                )
                if (!isDragHovering) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "or click to browse",
                        fontSize = 14.sp,
                        fontFamily = font,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = ".apk  ·  .apkm  ·  .xapk  ·  .apks",
                        fontSize = 10.sp,
                        fontFamily = font,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ============================================================================
// APK SELECTED (info card and action buttons)
// ============================================================================

@Composable
internal fun ApkSelectedSection(
    patchesLoaded: Boolean,
    apkInfo: ApkInfo,
    onClearClick: () -> Unit,
    onChangeClick: () -> Unit,
    onContinueClick: () -> Unit,
    patchSourceNames: List<String> = emptyList(),
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val statusColorType = resolveStatusColorType(apkInfo.versionStatus, apkInfo.checksumStatus)
    val accentColor = if (statusColorType == StatusColorType.PRIMARY) accents.secondary
                      else statusColorType.toColor()
    
    val showWarning = apkInfo.versionStatus != VersionStatus.LATEST_STABLE &&
                      apkInfo.versionStatus != VersionStatus.UNKNOWN
    val isWarningState = showWarning || apkInfo.isUnsupportedApp || apkInfo.hasLimitedInfo
    val buttonColors = when {
        isWarningState -> {
            // For unsupported or limited info, use accents.warning instead of statusColorType if status is PRIMARY
            val warningColor = if (statusColorType == StatusColorType.PRIMARY) accents.warning else statusColorType.toColor()
            ButtonDefaults.outlinedButtonColors(
                containerColor = warningColor,
                contentColor = warningColor.contrastingForeground()
            )
        }
        else -> ButtonDefaults.outlinedButtonColors(
            containerColor = accents.primary,
            contentColor = accents.primary.contrastingForeground()
        )
    }

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
            OutlinedButton(
                onClick = onChangeClick,
                modifier = Modifier.height(44.dp),
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(
                    "Change APK",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font
                )
            }
            val buttonBorderColor = if (showWarning) {
                if (statusColorType == StatusColorType.PRIMARY) accents.warning else statusColorType.toColor()
            } else accents.primary
            OutlinedButton(
                onClick = onContinueClick,
                enabled = patchesLoaded,
                modifier = Modifier.widthIn(min = 160.dp).height(44.dp),
                colors = buttonColors,
                border = BorderStroke(1.dp, buttonBorderColor),
                shape = RoundedCornerShape(corners.small),
                interactionSource = remember { MutableInteractionSource() }
            ) {
                ActionButtonContent(patchesLoaded, font)
            }
        }
    }
}

@Composable
internal fun ActionButtonContent(
    patchesLoaded: Boolean,
    font: FontFamily
) {
    if (!patchesLoaded) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "Loading…",
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font
        )
    } else {
        Text(
            "Continue",
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font
        )
    }
}

// ════════════════════════════════════════════════════════════════════
//  ANALYZING STATE
// ════════════════════════════════════════════════════════════════════

@Composable
internal fun AnalyzingSection() {
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

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
            text = "Analyzing",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Reading app metadata…",
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}