/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.engine.util.KeystoreInspectionResult
import app.morphe.engine.util.KeystoreService
import app.morphe.engine.util.KeystoreWarning
import app.morphe.gui.ui.components.MorpheAlertDialog
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
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
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
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
                fontFamily = font,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
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
                                fontSize = 11.sp,
                                fontFamily = font,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFFE0A030),
                                lineHeight = 14.sp
                            )
                        }
                        // If no cert data (alias not found), stop here
                        if (certInfo.sha256Fingerprint.isEmpty()) return@Column
                        HorizontalDivider(color = borderColor)
                    }

                    CertInfoRow(stringResource(Res.string.settings_cert_info_alias), certInfo.alias, font)
                    CertInfoRow(stringResource(Res.string.settings_cert_info_issuer), certInfo.issuer, font)
                    CertInfoRow(stringResource(Res.string.settings_cert_info_valid_from), certInfo.validFrom, font)
                    CertInfoRow(stringResource(Res.string.settings_cert_info_valid_until), certInfo.validTo, font)

                    HorizontalDivider(color = borderColor)

                    Text(
                        stringResource(Res.string.settings_cert_info_sha256),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SelectionContainer {
                        Text(
                            text = certInfo.sha256Fingerprint,
                            fontSize = 11.sp,
                            fontFamily = font,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp
                        )
                    }

                    HorizontalDivider(color = borderColor)

                    Text(
                        stringResource(Res.string.settings_cert_info_sha1),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SelectionContainer {
                        Text(
                            text = certInfo.sha1Fingerprint,
                            fontSize = 11.sp,
                            fontFamily = font,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(Res.string.settings_cert_info_could_not_read),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            OutlinedButton(
                modifier = Modifier.handCursor(),
                onClick = onDismiss,
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Text(
                    stringResource(Res.string.close),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = font
                )
            }
        }
    )
}

@Composable
private fun CertInfoRow(
    label: String,
    value: String,
    font: FontFamily
) {
    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = font,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
