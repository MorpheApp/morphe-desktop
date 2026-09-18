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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.components.MorpheAlertDialog
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.currentLocale
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Provider
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Locale
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
    val info = remember(keystorePath, password, alias, entryPassword, locale) {
        readKeystoreInfo(keystorePath, password, alias, entryPassword, locale)
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
            if (info != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.widthIn(min = 300.dp)
                ) {
                    // Show warnings first if there are any
                    if (info.warnings.isNotEmpty()) {
                        info.warnings.forEach { warning ->
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
                        if (info.sha256Fingerprint.isEmpty()) return@Column
                        HorizontalDivider(color = borderColor)
                    }

                    CertInfoRow(stringResource(Res.string.settings_cert_info_alias), info.alias, font)
                    CertInfoRow(stringResource(Res.string.settings_cert_info_issuer), info.issuer, font)
                    CertInfoRow(stringResource(Res.string.settings_cert_info_valid_from), info.validFrom, font)
                    CertInfoRow(stringResource(Res.string.settings_cert_info_valid_until), info.validTo, font)

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
                            text = info.sha256Fingerprint,
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
                            text = info.sha1Fingerprint,
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

internal sealed interface KeystoreWarning {
    data class AliasNotFound(val alias: String) : KeystoreWarning
    data class KeyPasswordIncorrect(val alias: String) : KeystoreWarning
}

internal data class KeystoreInfoResult(
    val alias: String,
    val issuer: String,
    val validFrom: String,
    val validTo: String,
    val sha256Fingerprint: String,
    val sha1Fingerprint: String,
    val warnings: List<KeystoreWarning> = emptyList()
)

internal fun readKeystoreInfo(
    keystorePath: String,
    password: String?,
    alias: String,
    entryPassword: String? = null,
    locale: Locale = Locale.getDefault()
): KeystoreInfoResult? {
    val file = File(keystorePath)
    if (!file.exists()) return null

    val passwordChars = password?.toCharArray() ?: charArrayOf()

    // Ensure BouncyCastle provider is registered (needed for BKS keystores)
    try {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(
                Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider")
                    .getDeclaredConstructor().newInstance() as Provider
            )
        }
    } catch (_: Exception) {
    }

    // Try multiple keystore types: BKS (what Morphe generates), then JKS, then PKCS12
    // BKS requires the BouncyCastle provider. Try with the name, fall back without
    val types = listOf("BKS" to "BC", "BKS" to null, "JKS" to null, "PKCS12" to null)
    for ((type, provider) in types) {
        try {
            val ks = if (provider != null) {
                KeyStore.getInstance(type, provider)
            } else {
                KeyStore.getInstance(type)
            }

            file.inputStream().use { ks.load(it, passwordChars) }

            val warnings = mutableListOf<KeystoreWarning>()

            // Alias must match exactly
            if (!ks.containsAlias(alias)) {
                return KeystoreInfoResult(
                    alias = alias,
                    issuer = "",
                    validFrom = "",
                    validTo = "",
                    sha256Fingerprint = "",
                    sha1Fingerprint = "",
                    warnings = listOf(KeystoreWarning.AliasNotFound(alias))
                )
            }

            val cert = ks.getCertificate(alias) as? X509Certificate ?: continue

            // Verify the entry password actually works
            try {
                ks.getKey(alias, entryPassword?.toCharArray() ?: charArrayOf())
            } catch (_: Exception) {
                return KeystoreInfoResult(
                    alias = alias,
                    issuer = "",
                    validFrom = "",
                    validTo = "",
                    sha256Fingerprint = "",
                    sha1Fingerprint = "",
                    warnings = listOf(KeystoreWarning.KeyPasswordIncorrect(alias))
                )
            }

            val sha256 = MessageDigest.getInstance("SHA-256")
                .digest(cert.encoded)
                .joinToString(":") { "%02X".format(it) }

            val sha1 = MessageDigest.getInstance("SHA-1")
                .digest(cert.encoded)
                .joinToString(":") { "%02X".format(it) }

            return KeystoreInfoResult(
                alias = alias,
                issuer = cert.issuerX500Principal.name,
                validFrom = FormatUtils.formatDate(cert.notBefore, locale),
                validTo = FormatUtils.formatDate(cert.notAfter, locale),
                sha256Fingerprint = sha256,
                sha1Fingerprint = sha1,
                warnings = warnings
            )
        } catch (_: Exception) {
            continue
        }
    }
    return null
}
