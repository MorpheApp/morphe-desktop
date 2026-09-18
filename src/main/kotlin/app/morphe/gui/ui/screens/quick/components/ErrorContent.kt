/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.quick.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.quick.QuickApkInfo
import app.morphe.gui.ui.theme.*
import app.morphe.morphe_desktop.generated.resources.*
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ErrorContent(
    errorMessage: String?,
    apkInfo: QuickApkInfo?,
    patchSourceName: String?,
    patchesVersion: String?,
    onStartOver: () -> Unit,
    onViewLogs: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val mono = LocalMorpheMono.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)
    val cardBg = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    val accents = LocalMorpheAccents.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(Res.string.status_patching_failed),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(20.dp))

        // App info card
        Box(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(corners.medium))
                .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
                .background(cardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.app_info_shared_label),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                val unknown = stringResource(Res.string.unknown)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = apkInfo?.packageName ?: unknown,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = apkInfo?.versionName ?: unknown,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurface
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
                Text(
                    text = patchSourceName ?: unknown,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = patchesVersion ?: unknown,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Error log card
        Box(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(corners.medium))
                .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
                .background(cardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(Res.string.error_log_label),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.error
                        )

                        val clipboard = LocalClipboard.current
                        val clipboardScope = rememberCoroutineScope()
                        var copied by remember { mutableStateOf(false) }

                        val copyHover = remember { MutableInteractionSource() }
                        val isCopyHovered by copyHover.collectIsHoveredAsState()
                        val copyBg by animateColorAsState(
                            if (isCopyHovered) accents.primary.copy(alpha = 0.1f) else Color.Transparent,
                            animationSpec = tween(150)
                        )
                        Box(
                            modifier = Modifier
                                .hoverable(copyHover)
                                .clip(RoundedCornerShape(corners.small))
                                .background(copyBg)
                                .clickable {
                                    clipboardScope.launch {
                                        clipboard.setClipEntry(
                                            ClipEntry(StringSelection(errorMessage ?: ""))
                                        )
                                    }
                                    copied = true
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (copied) stringResource(Res.string.copied) else stringResource(Res.string.copy),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = font,
                                color = if (copied) accents.secondary else accents.primary,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    
                    val scrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .wrapContentHeight()
                    ) {
                        Text(
                            text = errorMessage ?: stringResource(Res.string.quick_patch_error_unknown_occurred),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                                .padding(end = 12.dp)
                        )
                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            adapter = rememberScrollbarAdapter(scrollState),
                            style = morpheScrollbarStyle()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        Row(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onStartOver,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                shape = RoundedCornerShape(corners.small),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = stringResource(Res.string.quick_patch_screen_start_over_button),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            OutlinedButton(
                onClick = onViewLogs,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = stringResource(Res.string.view_full_logs_button),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                )
            }
        }
    }
}
