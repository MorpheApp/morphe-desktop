/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.engine.util.KeystoreInspectionResult
import app.morphe.engine.util.KeystoreService
import app.morphe.engine.util.KeystoreWarning
import app.morphe.gui.ui.components.MorpheAlertDialog
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.currentLocale
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun KeystoreInfoDialog(
    keystorePath: String,
    password: String?,
    alias: String,
    entryPassword: String,
    onDismiss: () -> Unit
) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)

    val locale = currentLocale()
    val info by produceState<KeystoreInspectionResult?>(
        initialValue = null,
        keystorePath, password, alias, entryPassword, locale
    ) {
        value = withContext(Dispatchers.IO) {
            KeystoreService.shared.inspectKeystore(File(keystorePath), password, alias, entryPassword, locale)
        }
    }

    MorpheAlertDialog(
        onDismiss = onDismiss,
        title = {
            Text(
                stringResource(Res.string.settings_cert_info_title),
                style = MorpheTheme.typography.titleMedium,
            )
        },
        text = {
            val certInfo = info
            if (certInfo != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.widthIn(min = 300.dp)
                ) {
                    // Show warnings first if there are any
                    if (certInfo.warnings.isNotEmpty()) {
                        certInfo.warnings.forEach { warning ->
                            val warningText = when (warning) {
                                is KeystoreWarning.AliasNotFound -> stringResource(Res.string.settings_cert_warning_alias_not_found, warning.alias)
                                is KeystoreWarning.KeyPasswordIncorrect -> stringResource(Res.string.settings_cert_warning_key_password_incorrect, warning.alias)
                            }
                            Text(
                                text = warningText,
                                style = MorpheTheme.typography.labelSmall,
                                color = MorpheTheme.colors.warning,
                            )
                        }
                        // If no cert data (alias not found), stop here
                        if (certInfo.sha256Fingerprint.isEmpty()) return@Column
                        HorizontalDivider(color = borderColor)
                    }

                    CertInfoRow(stringResource(Res.string.settings_cert_info_alias), certInfo.alias)
                    CertInfoRow(stringResource(Res.string.settings_cert_info_issuer), certInfo.issuer)
                    CertInfoRow(stringResource(Res.string.settings_cert_info_valid_from), certInfo.validFrom)
                    CertInfoRow(stringResource(Res.string.settings_cert_info_valid_until), certInfo.validTo)

                    HorizontalDivider(color = borderColor)

                    FingerprintRow(
                        label = stringResource(Res.string.settings_cert_info_sha256),
                        fingerprint = certInfo.sha256Fingerprint,
                    )

                    HorizontalDivider(color = borderColor)

                    FingerprintRow(
                        label = stringResource(Res.string.settings_cert_info_sha1),
                        fingerprint = certInfo.sha1Fingerprint,
                    )
                }
            } else {
                Text(
                    text = stringResource(Res.string.settings_cert_info_could_not_read),
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            MorpheButton(
                label = stringResource(Res.string.close),
                variant = MorpheButtonVariant.OUTLINED,
                size = MorpheButtonSize.COMPACT,
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun CertInfoRow(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            style = MorpheTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            style = MorpheTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FingerprintRow(
    label: String,
    fingerprint: String,
) {
    Text(
        text = label,
        style = MorpheTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    SelectionContainer {
        Text(
            text = fingerprint,
            style = MorpheTheme.typography.codeSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
