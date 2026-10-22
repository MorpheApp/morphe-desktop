/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.engine.MorpheData
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_ALIAS
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_PASSWORD
import app.morphe.engine.util.KeystoreImporter
import app.morphe.engine.util.KeystoreService
import app.morphe.engine.util.KeystoreWarning
import app.morphe.engine.util.Logger
import app.morphe.engine.util.PortablePaths
import app.morphe.gui.ui.components.LabeledField
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.SlimPasswordField
import app.morphe.gui.ui.components.SlimTextField
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SigningSection(
    keystorePath: String?,
    keystorePassword: String?,
    keystoreAlias: String,
    keystoreEntryPassword: String,
    onKeystorePathChange: (String?) -> Unit,
    onCredentialsChange: (password: String?, alias: String, entryPassword: String) -> Unit,
    borderColor: Color,
    enabled: Boolean = true,
    expanded: Boolean = false,
    icon: ImageVector? = null,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    val dimens = MorpheTheme.dimens
    val alpha = if (enabled) 1f else 0.4f
    val scope = rememberCoroutineScope()

    var localPassword by remember(keystorePassword) { mutableStateOf(keystorePassword ?: "") }
    var localAlias by remember(keystoreAlias) { mutableStateOf(keystoreAlias) }
    var localEntryPassword by remember(keystoreEntryPassword) { mutableStateOf(keystoreEntryPassword) }
    var showKeystoreInfo by remember { mutableStateOf(false) }
    var keystoreError by remember { mutableStateOf<String?>(null) }

    var keystoreExistsTrigger by remember { mutableStateOf(0) }
    val keystoreFile = keystorePath?.let { File(it) }
    val keystoreExists = remember(keystorePath, keystoreExistsTrigger) {
        keystoreFile?.exists() == true
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CollapsibleSection(
            title = stringResource(Res.string.settings_section_signing),
            expanded = expanded,
            icon = icon,
            onExpandedChange = onExpandedChange
        ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (!enabled) stringResource(Res.string.disabled_while_patching)
                   else stringResource(Res.string.settings_signing_desc),
            style = MorpheTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().height(dimens.controlHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(MorpheTheme.shapes.small)
                    .border(1.dp, borderColor, MorpheTheme.shapes.small)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = if (keystorePath != null) {
                        keystoreFile?.name ?: keystorePath
                    } else stringResource(Res.string.settings_signing_default_keystore),
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            MorpheButton(
                label = stringResource(Res.string.browse),
                onClick = {
                    scope.launch {
                        val selected = MorpheFilePicker.pickFile(
                            title = getString(Res.string.settings_signing_picker_title),
                            extensions = listOf("keystore", "jks", "bks", "p12", "pfx"),
                        ) ?: return@launch
                        val validExtensions = listOf(".keystore", ".jks", ".bks", ".p12", ".pfx")
                        if (validExtensions.any { selected.name.lowercase().endsWith(it) }) {
                            // Convert non-BKS keystores to BKS without mutating original file.
                            val result = withContext(Dispatchers.IO) {
                                KeystoreImporter.ensureBks(
                                    source = selected,
                                    convertedOutput = MorpheData.importedKeystoreFile,
                                    alias = keystoreAlias,
                                    password = keystoreEntryPassword,
                                )
                            }
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
                                    keystoreError = result.reason
                                    result.cause?.let {
                                        Logger.error("Keystore import failed for ${selected.name}", it)
                                    }
                                }
                            }
                        } else {
                            keystoreError = getString(Res.string.settings_signing_invalid_file_type, validExtensions.joinToString(", "))
                        }
                    }
                },
                enabled = enabled,
                variant = MorpheButtonVariant.OUTLINED,
                size = MorpheButtonSize.DEFAULT,
                modifier = Modifier.fillMaxHeight(),
            )

            if (keystorePath != null) {
                MorpheButton(
                    label = stringResource(Res.string.settings_dialog_reset_button),
                    onClick = { onKeystorePathChange(null) },
                    enabled = enabled,
                    variant = MorpheButtonVariant.OUTLINED,
                    size = MorpheButtonSize.DEFAULT,
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }

        // Warn if configured keystore file does not exist.
        if (keystorePath != null && !keystoreExists) {
            Text(
                text = stringResource(Res.string.settings_signing_not_found),
                style = MorpheTheme.typography.labelSmall,
                color = MorpheTheme.colors.warning
            )
        }

        keystoreError?.let {
            Text(
                text = it,
                style = MorpheTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (keystorePath != null) {
            StoredPathResolutionText(keystorePath)
        } else {
            val defaultAbs = MorpheData.defaultKeystoreFile.absolutePath
            val defaultStored = PortablePaths.storableForm(defaultAbs)
            val isBundleRelative = defaultStored != defaultAbs
            val defaultText = if (MorpheData.defaultKeystoreFile.exists())
                stringResource(Res.string.settings_signing_using_default, defaultStored)
            else
                stringResource(Res.string.settings_signing_will_create_default, defaultStored)
            Text(
                text = defaultText,
                style = MorpheTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (isBundleRelative) {
                Text(
                    text = stringResource(Res.string.settings_dialog_resolves_to_message, defaultAbs),
                    style = MorpheTheme.typography.labelExtraSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledField(label = stringResource(Res.string.settings_signing_keystore_password_label)) {
                SlimPasswordField(
                    value = localPassword,
                    onValueChange = {
                        localPassword = it
                        onCredentialsChange(it.ifEmpty { null }, localAlias, localEntryPassword)
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            LabeledField(label = stringResource(Res.string.settings_signing_key_alias_label)) {
                SlimTextField(
                    value = localAlias,
                    onValueChange = {
                        localAlias = it
                        onCredentialsChange(localPassword.ifEmpty { null }, it, localEntryPassword)
                    },
                    placeholder = "",
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            LabeledField(label = stringResource(Res.string.settings_signing_key_password_label)) {
                SlimPasswordField(
                    value = localEntryPassword,
                    onValueChange = {
                        localEntryPassword = it
                        onCredentialsChange(localPassword.ifEmpty { null }, localAlias, it)
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Verify credentials button
        var verifyResult by remember { mutableStateOf<String?>(null) }
        var verifySuccess by remember { mutableStateOf(false) }
        var isVerifying by remember { mutableStateOf(false) }

        if (keystoreExists) {
            Spacer(Modifier.height(6.dp))
            MorpheButton(
                onClick = {
                    val file = keystoreFile ?: return@MorpheButton
                    verifyResult = null
                    verifySuccess = false
                    isVerifying = true
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                KeystoreService.shared.inspectKeystore(
                                    file,
                                    localPassword.ifEmpty { null },
                                    localAlias.ifEmpty { DEFAULT_KEYSTORE_ALIAS },
                                    localEntryPassword.ifEmpty { DEFAULT_KEYSTORE_PASSWORD }
                                )
                            }
                            if (result == null) {
                                verifyResult = getString(Res.string.settings_signing_verify_could_not_open)
                                verifySuccess = false
                            } else if (result.warnings.isNotEmpty()) {
                                verifyResult = when (val w = result.warnings.first()) {
                                    is KeystoreWarning.AliasNotFound -> getString(Res.string.settings_cert_warning_alias_not_found, w.alias)
                                    is KeystoreWarning.KeyPasswordIncorrect -> getString(Res.string.settings_cert_warning_key_password_incorrect, w.alias)
                                }
                                verifySuccess = false
                            } else {
                                verifyResult = getString(Res.string.settings_signing_verify_valid)
                                verifySuccess = true
                            }
                        } finally {
                            isVerifying = false
                        }
                    }
                },
                enabled = enabled && !isVerifying,
                modifier = Modifier.fillMaxWidth().height(dimens.controlHeight),
                variant = MorpheButtonVariant.OUTLINED,
                size = MorpheButtonSize.DEFAULT,
                borderColor = when {
                    verifySuccess -> MorpheColors.Teal.copy(alpha = 0.4f)
                    verifyResult != null -> MorpheTheme.colors.warning.copy(alpha = 0.4f)
                    else -> borderColor
                },
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Icon(
                        imageVector = MorpheIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Text(stringResource(Res.string.settings_signing_verify_button))
            }

            verifyResult?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MorpheTheme.typography.labelSmall,
                    color = if (verifySuccess) MorpheColors.Teal else MorpheTheme.colors.warning,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        var generateError by remember { mutableStateOf<String?>(null) }
        var generateSuccess by remember { mutableStateOf(false) }
        var isGenerating by remember { mutableStateOf(false) }

        if (!keystoreExists) {
            MorpheButton(
                onClick = {
                    generateError = null
                    generateSuccess = false
                    isGenerating = true
                    scope.launch {
                        try {
                            val path = keystorePath ?: run {
                                val chosen = MorpheFilePicker.saveFile(
                                    title = getString(Res.string.settings_signing_picker_save_keystore),
                                    baseName = "morphe",
                                    extension = "keystore",
                                ) ?: return@launch
                                val chosenPath = chosen.absolutePath
                                onKeystorePathChange(chosenPath)
                                chosenPath
                            }

                            val file = File(path)
                            withContext(Dispatchers.IO) {
                                KeystoreService.shared.generateKeystore(
                                    destination = file,
                                    alias = localAlias.ifEmpty { DEFAULT_KEYSTORE_ALIAS },
                                    storePassword = localPassword.ifEmpty { null },
                                    entryPassword = localEntryPassword.ifEmpty { DEFAULT_KEYSTORE_PASSWORD },
                                )
                            }
                            onCredentialsChange(
                                localPassword.ifEmpty { null },
                                localAlias.ifEmpty { DEFAULT_KEYSTORE_ALIAS },
                                localEntryPassword.ifEmpty { DEFAULT_KEYSTORE_PASSWORD }
                            )
                            generateSuccess = true
                            keystoreExistsTrigger++
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            generateError = getString(Res.string.settings_signing_failed_to_generate, e.message ?: "")
                            Logger.error("Failed to generate keystore", e)
                        } finally {
                            isGenerating = false
                        }
                    }
                },
                enabled = enabled && !isGenerating,
                modifier = Modifier.fillMaxWidth().height(dimens.controlHeight),
                variant = MorpheButtonVariant.OUTLINED,
                size = MorpheButtonSize.DEFAULT,
                borderColor = if (generateSuccess)
                    MorpheColors.Teal.copy(alpha = 0.4f)
                else MorpheTheme.colors.primary.copy(alpha = 0.3f),
                contentColor = if (generateSuccess) MorpheColors.Teal else MorpheTheme.colors.primary,
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = MorpheTheme.colors.primary
                    )
                } else {
                    Icon(
                        imageVector = MorpheIcons.Add,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = if (generateSuccess) MorpheColors.Teal else MorpheTheme.colors.primary
                    )
                }
                Text(
                    stringResource(if (generateSuccess) Res.string.settings_signing_generated_button else Res.string.settings_signing_generate_button),
                )
            }

            generateError?.let {
                Text(
                    text = it,
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!generateSuccess) {
                Text(
                    text = stringResource(Res.string.settings_signing_uses_credentials_hint),
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(4.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MorpheButton(
                label = stringResource(Res.string.settings_signing_certificate_button),
                icon = MorpheIcons.Info,
                onClick = { showKeystoreInfo = true },
                enabled = enabled && keystoreExists,
                variant = MorpheButtonVariant.OUTLINED,
                size = MorpheButtonSize.DEFAULT,
                modifier = Modifier.weight(1f),
            )

            MorpheButton(
                label = stringResource(Res.string.settings_signing_export_button),
                icon = MorpheIcons.Share,
                onClick = {
                    val sourceFile = keystoreFile ?: return@MorpheButton
                    if (!sourceFile.exists()) return@MorpheButton
                    scope.launch {
                        val dest = MorpheFilePicker.saveFile(
                            title = getString(Res.string.settings_signing_picker_export_title),
                            baseName = sourceFile.nameWithoutExtension,
                            extension = sourceFile.extension.ifEmpty { "keystore" },
                        ) ?: return@launch
                        try {
                            withContext(Dispatchers.IO) {
                                sourceFile.copyTo(dest, overwrite = true)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Logger.error("Failed to export keystore", e)
                        }
                    }
                },
                enabled = enabled && keystoreExists,
                variant = MorpheButtonVariant.OUTLINED,
                size = MorpheButtonSize.DEFAULT,
                modifier = Modifier.weight(1f),
            )
        }
        }
        }
    }

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
