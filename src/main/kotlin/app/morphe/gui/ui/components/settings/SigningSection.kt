/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.engine.MorpheData
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_ALIAS
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_PASSWORD
import app.morphe.engine.util.KeystoreImporter
import app.morphe.engine.util.PortablePaths
import app.morphe.gui.ui.components.LabeledField
import app.morphe.gui.ui.components.SlimTextField
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheDimens
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.util.Logger
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.patcher.apk.ApkSigner
import java.io.File
import java.util.Date
import kotlinx.coroutines.launch

@Composable
internal fun SigningSection(
    keystorePath: String?,
    keystorePassword: String?,
    keystoreAlias: String,
    keystoreEntryPassword: String,
    onKeystorePathChange: (String?) -> Unit,
    onCredentialsChange: (password: String?, alias: String, entryPassword: String) -> Unit,
    font: FontFamily,
    accentColor: Color,
    borderColor: Color,
    enabled: Boolean = true,
    expanded: Boolean = false,
    icon: ImageVector? = null,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    val corners = LocalMorpheCorners.current
    val dimens = LocalMorpheDimens.current
    val accents = LocalMorpheAccents.current
    val alpha = if (enabled) 1f else 0.4f
    val scope = rememberCoroutineScope()

    var localPassword by remember(keystorePassword) { mutableStateOf(keystorePassword ?: "") }
    var localAlias by remember(keystoreAlias) { mutableStateOf(keystoreAlias) }
    var localEntryPassword by remember(keystoreEntryPassword) { mutableStateOf(keystoreEntryPassword) }
    var showPassword by remember { mutableStateOf(false) }
    var showEntryPassword by remember { mutableStateOf(false) }
    var showKeystoreInfo by remember { mutableStateOf(false) }
    var keystoreError by remember { mutableStateOf<String?>(null) }

    val keystoreFile = keystorePath?.let { File(it) }
    val keystoreExists = keystoreFile?.exists() == true

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CollapsibleSection(
            title = "Signing",
            font = font,
            expanded = expanded,
            icon = icon,
            onExpandedChange = onExpandedChange
        ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (!enabled) "Disabled while patching"
                   else "Keystore used to sign patched APKs",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            fontFamily = font,
            fontWeight = FontWeight.Normal
        )

        Spacer(Modifier.height(8.dp))

        // Keystore path row
        Row(
            modifier = Modifier.fillMaxWidth().height(dimens.controlHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(corners.small))
                    .border(1.dp, borderColor, RoundedCornerShape(corners.small))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = if (keystorePath != null) {
                        keystoreFile?.name ?: keystorePath
                    } else "Default (auto-generated)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        val selected = MorpheFilePicker.pickFile(
                            title = "Select keystore",
                            extensions = listOf("keystore", "jks", "bks", "p12", "pfx"),
                        ) ?: return@launch
                        val validExtensions = listOf(".keystore", ".jks", ".bks", ".p12", ".pfx")
                        if (validExtensions.any { selected.name.lowercase().endsWith(it) }) {
                            // Route the picked file through KeystoreImporter:
                            // BKS files pass through unchanged. PKCS12 and JKS get
                            // converted to BKS and saved as MorpheData.importedKeystoreFile
                            // (original user file is never mutated). The config
                            // then points at whichever file is BKS. The patcher
                            // only speaks BKS, so this is the only safe input.
                            val result = KeystoreImporter.ensureBks(
                                source = selected,
                                convertedOutput = MorpheData.importedKeystoreFile,
                                alias = keystoreAlias,
                                password = keystoreEntryPassword,
                            )
                            when (result) {
                                is KeystoreImporter.Result.AlreadyBks -> {
                                    keystoreError = null
                                    onKeystorePathChange(result.file.absolutePath)
                                }
                                is KeystoreImporter.Result.Converted -> {
                                    keystoreError = null
                                    Logger.info(
                                        "Converted ${result.sourceFormat.displayName} → BKS for ${selected.name}"
                                    )
                                    onKeystorePathChange(result.file.absolutePath)
                                }
                                is KeystoreImporter.Result.Failed -> {
                                    // Most common failure: wrong password. The
                                    // user's configured entry password didn't
                                    // match the source file. Surface inline so
                                    // they can update it and re-import.
                                    keystoreError = result.reason
                                    result.cause?.let {
                                        Logger.error("Keystore import failed for ${selected.name}", it)
                                    }
                                }
                            }
                        } else {
                            keystoreError = "Invalid file type. Expected: ${validExtensions.joinToString(", ")}"
                        }
                    }
                },
                enabled = enabled,
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, borderColor),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.fillMaxHeight().handCursor()
            ) {
                Text(
                    "Browse",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = font
                )
            }

            if (keystorePath != null) {
                OutlinedButton(
                    onClick = { onKeystorePathChange(null) },
                    enabled = enabled,
                    shape = RoundedCornerShape(corners.small),
                    border = BorderStroke(1.dp, borderColor),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.fillMaxHeight().handCursor()
                ) {
                    Text(
                        "Reset",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = font
                    )
                }
            }
        }

        // Warning if keystore path set but file doesn't exist. Patching will
        // refuse to start with this configured (see PatchingViewModel). The user
        // must restore the file, pick another, or reset to use Morphe's default.
        if (keystorePath != null && !keystoreExists) {
            Text(
                text = "Keystore not found - patching will fail until you restore it, pick another, or reset",
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = Color(0xFFE0A030)
            )
        }

        // Error for invalid file type selection
        keystoreError?.let {
            Text(
                text = it,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Either: stored form (relative when inside the bundle, absolute otherwise)
        // with a "Resolves to: ..." subtitle when relative. Mirrors config.json
        // so users can see which paths follow the bundle vs which are pinned.
        // Or: "using default" hint when no user-configured path is set.
        if (keystorePath != null) {
            val stored = PortablePaths.storableForm(keystorePath)
            val isBundleRelative = stored != keystorePath
            Text(
                text = stored,
                fontSize = 11.sp,
                fontFamily = font,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isBundleRelative) {
                Text(
                    text = "Resolves to: $keystorePath",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            // Mirror the storage form treatment used for user-configured paths above.
            // The default keystore lives in the bundle (`morphe-data/`) in the happy case,
            // so the storable form will be relative.
            // Verb is conditional on file existence. Patcher creates the file on first sign,
            // so on a fresh install the hint accurately says "Will create..."
            // instead of making up claims like "Using..." an absent file.
            val defaultAbs = MorpheData.defaultKeystoreFile.absolutePath
            val defaultStored = PortablePaths.storableForm(defaultAbs)
            val isBundleRelative = defaultStored != defaultAbs
            val verb = if (MorpheData.defaultKeystoreFile.exists()) "Using"
                       else "Will create"
            Text(
                text = "$verb Morphe's default keystore at $defaultStored",
                fontSize = 11.sp,
                fontFamily = font,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (isBundleRelative) {
                Text(
                    text = "Resolves to: $defaultAbs",
                    fontSize = 9.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledField(label = "Keystore password", font = font) {
                SlimTextField(
                    value = localPassword,
                    onValueChange = {
                        localPassword = it
                        onCredentialsChange(it.ifEmpty { null }, localAlias, localEntryPassword)
                    },
                    placeholder = "",
                    font = font,
                    accents = accents,
                    corners = corners,
                    enabled = enabled,
                    visualTransformation = if (showPassword) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    trailing = {
                        IconButton(
                            onClick = { showPassword = !showPassword },
                            modifier = Modifier.size(24.dp).handCursor(),
                        ) {
                            Icon(
                                imageVector = if (showPassword) MorpheIcons.VisibilityOff else MorpheIcons.Visibility,
                                contentDescription = if (showPassword) "Hide" else "Show",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    },
                )
            }

            LabeledField(label = "Key alias", font = font) {
                SlimTextField(
                    value = localAlias,
                    onValueChange = {
                        localAlias = it
                        onCredentialsChange(localPassword.ifEmpty { null }, it, localEntryPassword)
                    },
                    placeholder = "",
                    font = font,
                    accents = accents,
                    corners = corners,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            LabeledField(label = "Key password", font = font) {
                SlimTextField(
                    value = localEntryPassword,
                    onValueChange = {
                        localEntryPassword = it
                        onCredentialsChange(localPassword.ifEmpty { null }, localAlias, it)
                    },
                    placeholder = "",
                    font = font,
                    accents = accents,
                    corners = corners,
                    enabled = enabled,
                    visualTransformation = if (showEntryPassword) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    trailing = {
                        IconButton(
                            onClick = { showEntryPassword = !showEntryPassword },
                            modifier = Modifier.size(24.dp).handCursor(),
                        ) {
                            Icon(
                                imageVector = if (showEntryPassword) MorpheIcons.VisibilityOff else MorpheIcons.Visibility,
                                contentDescription = if (showEntryPassword) "Hide" else "Show",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    },
                )
            }
        }

        // Verify credentials button
        var verifyResult by remember { mutableStateOf<String?>(null) }
        var verifySuccess by remember { mutableStateOf(false) }

        if (keystoreExists) {
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    verifyResult = null
                    verifySuccess = false
                    val result = readKeystoreInfo(
                        keystorePath,
                        localPassword.ifEmpty { null },
                        localAlias.ifEmpty { DEFAULT_KEYSTORE_ALIAS },
                        localEntryPassword.ifEmpty { DEFAULT_KEYSTORE_PASSWORD }
                    )
                    if (result == null) {
                        verifyResult = "Could not open keystore — check keystore password"
                        verifySuccess = false
                    } else if (result.warnings.isNotEmpty()) {
                        verifyResult = result.warnings.first()
                        verifySuccess = false
                    } else {
                        verifyResult = "Credentials valid"
                        verifySuccess = true
                    }
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(dimens.controlHeight).handCursor(),
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(
                    1.dp,
                    when {
                        verifySuccess -> MorpheColors.Teal.copy(alpha = 0.4f)
                        verifyResult != null -> Color(0xFFE0A030).copy(alpha = 0.4f)
                        else -> borderColor
                    }
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) {
                Icon(
                    imageVector = MorpheIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Verify credentials",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = font
                )
            }

            verifyResult?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = if (verifySuccess) MorpheColors.Teal else Color(0xFFE0A030),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Generate button (only when no keystore exists yet)
        var generateError by remember { mutableStateOf<String?>(null) }
        var generateSuccess by remember { mutableStateOf(false) }

        if (!keystoreExists) {
            OutlinedButton(
                onClick = {
                    generateError = null
                    generateSuccess = false
                    scope.launch {
                        // If no path set, ask the user where to save
                        val path = keystorePath ?: run {
                            val chosen = MorpheFilePicker.saveFile(
                                title = "Save keystore",
                                baseName = "morphe",
                                extension = "keystore",
                            ) ?: return@launch // user cancelled
                            val chosenPath = chosen.absolutePath
                            onKeystorePathChange(chosenPath)
                            chosenPath
                        }

                        try {
                            val file = File(path)
                            file.parentFile?.mkdirs()
                            val keyPair = ApkSigner.newPrivateKeyCertificatePair(
                                "Morphe",
                                Date(System.currentTimeMillis() + 8L * 365 * 24 * 60 * 60 * 1000))
                            val ks = ApkSigner.newKeyStore(setOf(
                                ApkSigner.KeyStoreEntry(
                                    localAlias.ifEmpty { DEFAULT_KEYSTORE_ALIAS },
                                    localEntryPassword.ifEmpty { DEFAULT_KEYSTORE_PASSWORD },
                                    keyPair
                                )
                            ))
                            file.outputStream().use {
                                ks.store(it, localPassword.ifEmpty { null }?.toCharArray())
                            }
                            // Save credentials to config
                            onCredentialsChange(
                                localPassword.ifEmpty { null },
                                localAlias.ifEmpty { DEFAULT_KEYSTORE_ALIAS },
                                localEntryPassword.ifEmpty { DEFAULT_KEYSTORE_PASSWORD }
                            )
                            generateSuccess = true
                        } catch (e: Exception) {
                            generateError = "Failed to generate: ${e.message}"
                            Logger.error("Failed to generate keystore", e)
                        }
                    }
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(dimens.controlHeight).handCursor(),
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(
                    1.dp, if (generateSuccess)
                        MorpheColors.Teal.copy(alpha = 0.4f)
                    else accentColor.copy(alpha = 0.3f)
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) {
                Icon(
                    imageVector = MorpheIcons.Add,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = if (generateSuccess) MorpheColors.Teal else accentColor
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (generateSuccess) "Keystore generated" else "Generate keystore",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (generateSuccess) MorpheColors.Teal else accentColor,
                    fontFamily = font
                )
            }

            generateError?.let {
                Text(
                    text = it,
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!generateSuccess) {
                Text(
                    text = "Uses the credentials entered above",
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(4.dp))
        }

        // Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Certificate info
            OutlinedButton(
                onClick = { showKeystoreInfo = true },
                enabled = enabled && keystoreExists,
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, borderColor),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f).handCursor()
            ) {
                Icon(
                    imageVector = MorpheIcons.Info,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Certificate",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = font
                )
            }

            // Export
            OutlinedButton(
                onClick = {
                    val sourceFile = keystoreFile ?: return@OutlinedButton
                    if (!sourceFile.exists()) return@OutlinedButton
                    scope.launch {
                        val dest = MorpheFilePicker.saveFile(
                            title = "Export keystore",
                            baseName = sourceFile.nameWithoutExtension,
                            extension = sourceFile.extension.ifEmpty { "keystore" },
                        ) ?: return@launch
                        try {
                            sourceFile.copyTo(dest, overwrite = true)
                        } catch (e: Exception) {
                            Logger.error("Failed to export keystore", e)
                        }
                    }
                },
                enabled = enabled && keystoreExists,
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, borderColor),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f).handCursor()
            ) {
                Icon(
                    imageVector = MorpheIcons.Share,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Export",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = font
                )
            }
        }
        } // inner Column
        } // CollapsibleSection
    }

    // Certificate info dialog
    if (showKeystoreInfo && keystorePath != null) {
        KeystoreInfoDialog(
            keystorePath = keystorePath,
            password = keystorePassword,
            alias = keystoreAlias,
            entryPassword = keystoreEntryPassword,
            onDismiss = { showKeystoreInfo = false }
        )
    }
}
