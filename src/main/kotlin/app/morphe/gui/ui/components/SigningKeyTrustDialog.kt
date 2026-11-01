/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import org.jetbrains.compose.resources.stringResource

@Composable
fun SigningKeyTrustDialog(
    sourceName: String,
    expectedFingerprint: String?,
    actualFingerprint: String?,
    actualKey: String?,
    onDismiss: () -> Unit,
    onTrust: () -> Unit,
) {
    var copiedKey by remember { mutableStateOf<String?>(null) }

    fun copyToClipboard(key: String, label: String) {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(key), null)
            copiedKey = label
        } catch (_: Exception) {}
    }

    val isUnsigned = actualFingerprint == null

    MorpheAlertDialog(
        onDismiss = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.sources_signing_key_trust_title),
                style = MorpheTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(Res.string.sources_signing_key_changed_named, sourceName),
                    style = MorpheTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = if (isUnsigned) {
                        stringResource(Res.string.sources_signing_key_trust_message_unsigned)
                    } else {
                        stringResource(Res.string.sources_signing_key_trust_message)
                    },
                    style = MorpheTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (expectedFingerprint != null) {
                        MorphePanel(
                            modifier = Modifier.fillMaxWidth(),
                            color = MorpheTheme.colors.primary.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, MorpheTheme.colors.primary.copy(alpha = 0.35f)),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(Res.string.sources_signing_key_trusted),
                                        style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MorpheTheme.colors.primary,
                                    )
                                    Icon(
                                        imageVector = MorpheIcons.Verified,
                                        contentDescription = null,
                                        tint = MorpheTheme.colors.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Text(
                                    text = expectedFingerprint,
                                    style = MorpheTheme.typography.codeSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }

                    MorphePanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (actualKey != null) {
                                    Modifier
                                        .handCursor()
                                        .clickable { copyToClipboard(actualKey, "new") }
                                } else Modifier
                            ),
                        color = MorpheTheme.colors.danger.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, MorpheTheme.colors.danger.copy(alpha = 0.35f)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(Res.string.sources_signing_key_new),
                                    style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MorpheTheme.colors.danger,
                                )
                                if (actualKey != null) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = if (copiedKey == "new") stringResource(Res.string.copied) else stringResource(Res.string.copy),
                                            style = MorpheTheme.typography.labelSmall,
                                            color = MorpheTheme.colors.danger,
                                        )
                                        Icon(
                                            imageVector = MorpheIcons.ContentCopy,
                                            contentDescription = stringResource(Res.string.copy),
                                            tint = MorpheTheme.colors.danger,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = MorpheIcons.GppBad,
                                        contentDescription = null,
                                        tint = MorpheTheme.colors.danger,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            Text(
                                text = actualFingerprint ?: stringResource(Res.string.sources_signing_key_trust_message_unsigned),
                                style = if (actualFingerprint != null) MorpheTheme.typography.codeSmall else MorpheTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            MorpheButton(
                label = stringResource(Res.string.cancel),
                variant = MorpheButtonVariant.GHOST,
                size = MorpheButtonSize.DEFAULT,
                onClick = onDismiss,
            )
        },
        confirmButton = {
            MorpheButton(
                label = stringResource(Res.string.trust),
                variant = MorpheButtonVariant.PRIMARY,
                size = MorpheButtonSize.DEFAULT,
                onClick = {
                    onTrust()
                    onDismiss()
                },
            )
        },
    )
}
