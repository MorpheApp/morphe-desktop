/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.quick.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.*
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
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)
    val cardBg = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(Res.string.status_patching_failed),
            style = MorpheTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .clip(MorpheTheme.shapes.medium)
                .border(1.dp, borderColor, MorpheTheme.shapes.medium)
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
                        style = MorpheTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                val unknown = stringResource(Res.string.unknown)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = apkInfo?.packageName ?: unknown,
                    style = MorpheTheme.typography.codeSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = apkInfo?.versionName ?: unknown,
                    style = MorpheTheme.typography.codeSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
                Text(
                    text = patchSourceName ?: unknown,
                    style = MorpheTheme.typography.codeSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = patchesVersion ?: unknown,
                    style = MorpheTheme.typography.codeSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .clip(MorpheTheme.shapes.medium)
                .border(1.dp, borderColor, MorpheTheme.shapes.medium)
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
                            style = MorpheTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MorpheTheme.colors.danger
                        )

                        val clipboard = LocalClipboard.current
                        val clipboardScope = rememberCoroutineScope()
                        var copied by remember { mutableStateOf(false) }

                        MorpheButton(
                            label = if (copied) stringResource(Res.string.copied) else stringResource(Res.string.copy),
                            variant = if (copied) MorpheButtonVariant.SECONDARY else MorpheButtonVariant.PRIMARY,
                            size = MorpheButtonSize.COMPACT,
                            onClick = {
                                clipboardScope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(StringSelection(errorMessage ?: ""))
                                    )
                                }
                                copied = true
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    
                    val scrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .wrapContentHeight()
                    ) {
                        ProvideTextStyle(MorpheTheme.typography.codeSmall) {
                            Text(
                                text = errorMessage ?: stringResource(Res.string.quick_patch_error_unknown_occurred),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState)
                                    .padding(end = 12.dp)
                            )
                        }
                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            adapter = rememberScrollbarAdapter(scrollState),
                            style = MorpheScrollbarStyle()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MorpheButton(
                label = stringResource(Res.string.quick_patch_screen_start_over_button),
                variant = MorpheButtonVariant.OUTLINED,
                size = MorpheButtonSize.LARGE,
                modifier = Modifier.weight(1f),
                onClick = onStartOver
            )
            
            MorpheButton(
                label = stringResource(Res.string.view_full_logs_button),
                variant = MorpheButtonVariant.PRIMARY,
                size = MorpheButtonSize.LARGE,
                modifier = Modifier.weight(1f),
                onClick = onViewLogs
            )
        }
    }
}
