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
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Provider
import java.security.Security
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat

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

    val info = remember(keystorePath, password, alias, entryPassword) {
        readKeystoreInfo(keystorePath, password, alias, entryPassword)
    }

    MorpheAlertDialog(
        onDismiss = onDismiss,
        title = {
            Text(
                "Certificate info",
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
                            Text(
                                text = warning,
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

                    CertInfoRow("Alias", info.alias, font)
                    CertInfoRow("Issuer", info.issuer, font)
                    CertInfoRow("Valid from", info.validFrom, font)
                    CertInfoRow("Valid until", info.validTo, font)

                    HorizontalDivider(color = borderColor)

                    Text(
                        "SHA-256 fingerprint",
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
                        "SHA-1 fingerprint",
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
                    text = "Could not read keystore. Check the password and alias",
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
                    "Close",
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

internal data class KeystoreInfoResult(
    val alias: String,
    val issuer: String,
    val validFrom: String,
    val validTo: String,
    val sha256Fingerprint: String,
    val sha1Fingerprint: String,
    val warnings: List<String> = emptyList()
)

internal fun readKeystoreInfo(
    keystorePath: String,
    password: String?,
    alias: String,
    entryPassword: String? = null
): KeystoreInfoResult? {
    val file = File(keystorePath)
    if (!file.exists()) return null

    val passwordChars = password?.toCharArray() ?: charArrayOf()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd")

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

            val warnings = mutableListOf<String>()

            // Alias must match exactly
            if (!ks.containsAlias(alias)) {
                return KeystoreInfoResult(
                    alias = alias,
                    issuer = "",
                    validFrom = "",
                    validTo = "",
                    sha256Fingerprint = "",
                    sha1Fingerprint = "",
                    warnings = listOf("Alias \"$alias\" not found in keystore")
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
                    warnings = listOf("Key password is incorrect for alias \"$alias\"")
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
                validFrom = dateFormat.format(cert.notBefore),
                validTo = dateFormat.format(cert.notAfter),
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
