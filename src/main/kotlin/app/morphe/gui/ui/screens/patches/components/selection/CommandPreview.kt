/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.selection

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.MorpheScrollbarStyle
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CommandPreview(
    command: String,
    cleanMode: Boolean,
    onToggleMode: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val terminalAccent = MaterialTheme.colorScheme.onSurface
    val terminalText = MaterialTheme.colorScheme.onSurface
    val terminalBg = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)

    var showCopied by remember { mutableStateOf(false) }

    LaunchedEffect(showCopied) {
        if (showCopied) {
            delay(1500.milliseconds)
            showCopied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MorpheTheme.shapes.small)
            .border(
                1.dp,
                terminalAccent.copy(alpha = 0.15f),
                MorpheTheme.shapes.small
            )
            .background(terminalBg)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = MorpheIcons.Terminal,
                    contentDescription = null,
                    tint = terminalAccent.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(Res.string.patch_selection_cmd_preview),
                    style = MorpheTheme.typography.labelLarge,
                    color = terminalAccent.copy(alpha = 0.7f)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MorpheButton(
                    label = if (showCopied) stringResource(Res.string.copied) else stringResource(Res.string.copy),
                    variant = MorpheButtonVariant.GHOST,
                    size = MorpheButtonSize.COMPACT,
                    customColor = terminalAccent,
                    onClick = {
                        onCopy()
                        showCopied = true
                    }
                )

                MorpheButton(
                    label = if (cleanMode) stringResource(Res.string.collapse) else stringResource(Res.string.expand),
                    variant = MorpheButtonVariant.GHOST,
                    size = MorpheButtonSize.COMPACT,
                    customColor = terminalAccent,
                    onClick = onToggleMode
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val scrollState = rememberScrollState()
        val hasScroll = scrollState.maxValue > 0
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 120.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = if (hasScroll) 10.dp else 0.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = command,
                    style = MorpheTheme.typography.codeSmall,
                    color = terminalText,
                )
            }
            if (hasScroll) {
                VerticalScrollbar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState),
                    style = MorpheScrollbarStyle()
                )
            }
        }
    }
}
