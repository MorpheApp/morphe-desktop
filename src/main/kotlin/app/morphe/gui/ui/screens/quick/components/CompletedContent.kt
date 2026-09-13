/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.quick.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.engine.DevicePatchDeploymentStore
import app.morphe.engine.PatchedAppStore
import app.morphe.engine.util.ApkManifestReader
import app.morphe.gui.LocalAdbPreference
import app.morphe.gui.ui.components.UpdateOwnerMigrationDialog
import app.morphe.gui.ui.components.DeviceInstallConfirmationDialog
import app.morphe.gui.ui.components.MorpheTooltip
import app.morphe.gui.ui.components.TooltipText
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.ui.screens.quick.QuickApkInfo
import app.morphe.gui.ui.screens.quick.formatFileSize
import app.morphe.gui.ui.theme.*
import app.morphe.gui.util.AdbManager
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.DeviceOperationTarget
import app.morphe.gui.util.DevicePackageMutations
import app.morphe.gui.util.DeviceDeploymentState
import app.morphe.gui.util.UpdateOwnerMigrationCoordinator
import app.morphe.gui.util.UpdateOwnerMigrationRequest
import app.morphe.gui.util.UpdateOwnerMigrationResult
import app.morphe.gui.util.migrationRequestOrNull
import app.morphe.gui.util.captureOperationTarget
import app.morphe.gui.util.forSerial
import java.awt.Desktop
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

// ============================================================================
// COMPLETED CONTENT (output + actions)
// ============================================================================

@Composable
internal fun CompletedContent(
    outputPath: String,
    apkInfo: QuickApkInfo,
    onPatchAnother: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val outputFile = File(outputPath)
    val scope = rememberCoroutineScope()
    val adbManager = remember { AdbManager() }
    val configRepository: ConfigRepository = koinInject()
    val monitorState by DeviceMonitor.state.collectAsState()
    val adbPreference = LocalAdbPreference.current
    val isAdbDisabledByUser = !adbPreference.enabled
    var deployments by remember(outputPath) { mutableStateOf<Map<String, DeviceDeploymentState>>(emptyMap()) }
    var installTarget by remember { mutableStateOf<DeviceOperationTarget?>(null) }
    var migrationRequest by remember { mutableStateOf<UpdateOwnerMigrationRequest?>(null) }
    var showMigrationConfirm by remember { mutableStateOf(false) }
    var migrationBusy by remember { mutableStateOf(false) }
    var migrationError by remember { mutableStateOf<String?>(null) }
    var outputPackage by remember(outputPath) { mutableStateOf<String?>(null) }
    var pendingInstallTarget by remember { mutableStateOf<DeviceOperationTarget?>(null) }

    LaunchedEffect(outputPath) {
        outputPackage = withContext(Dispatchers.IO) {
            PatchedAppStore.shared.getAll().firstOrNull { it.outputApkPath == outputPath }?.installedPackageName
                ?: ApkManifestReader.read(outputFile)?.packageName
        }
    }

    val readySerialsKey = monitorState.devices.filter { it.isReady }.joinToString("|") { it.id }
    LaunchedEffect(readySerialsKey, outputPackage) {
        val pkg = outputPackage ?: return@LaunchedEffect
        monitorState.devices.filter { it.isReady }.forEach { device ->
            val current = deployments.forSerial(device.id)
            if (current.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING) return@forEach
            val installed = adbManager.listInstalledPackages(device.id).getOrNull()?.contains(pkg)
                ?: return@forEach
            val version = if (installed) adbManager.getInstalledPackageInfo(device.id, pkg)?.first else null
            if (DeviceMonitor.state.value.devices.any { it.id == device.id && it.isReady }) {
                deployments = deployments + (device.id to deployments.forSerial(device.id).observed(installed, version))
            }
        }
    }

    suspend fun runPostInstall(deviceId: String, notifyMutation: Boolean = true) {
        val config = configRepository.loadConfig()
        val record = PatchedAppStore.shared.getAll()
            .firstOrNull { it.outputApkPath == outputPath }
        record?.let { DevicePatchDeploymentStore.shared.recordSuccessfulInstall(deviceId, it) }
        if (notifyMutation) {
            val affectedPackage = record?.installedPackageName ?: withContext(Dispatchers.IO) {
                runCatching { ApkManifestReader.read(outputFile)?.packageName }.getOrNull()
            } ?: apkInfo.packageName
            DevicePackageMutations.notify(
                deviceId,
                affectedPackage,
            )
        }
        if (config.autoRouteLinksAfterInstall) {
            record?.let {
                deployments = deployments + (deviceId to deployments.forSerial(deviceId).applyingLinks())
                val result = adbManager.setLinkHandling(
                    deviceId = deviceId,
                    patchedPackage = it.installedPackageName,
                    stockPackage = if (config.disableStockLinksAfterInstall) it.packageName else null,
                    enable = true,
                )
                deployments = deployments + (deviceId to result.fold(
                    onSuccess = { deployments.forSerial(deviceId).linksConfigured("Links routed to patched app") },
                    onFailure = { deployments.forSerial(deviceId).linksFailed(it.message ?: "Link handling failed") },
                ))
            }
        }
    }

    fun installViaAdb(target: DeviceOperationTarget) {
        if (deployments.values.any { it.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING }) return
        if (DeviceMonitor.state.value.devices.none { it.id == target.serial && it.isReady }) {
            installTarget = target
            deployments = deployments + (target.serial to deployments.forSerial(target.serial).installFailed(
                "${target.displayName} is no longer connected and ready. Installation was not started.",
            ))
            return
        }
        val wasAlreadyInstalled = deployments.forSerial(target.serial).installed == true
        scope.launch {
            migrationRequest = null
            installTarget = target
            deployments = deployments + (target.serial to deployments.forSerial(target.serial).installing(
                "${if (wasAlreadyInstalled) "Updating" else "Installing"} on ${target.displayName}…",
            ))
            val installer = adbManager.resolveSpoofInstaller(target.serial)
            val result = adbManager.installApk(
                apkPath = outputPath,
                deviceId = target.serial,
                installerPackage = installer,
            )
            result.fold(
                onSuccess = {
                    deployments = deployments + (target.serial to deployments.forSerial(target.serial).installed(
                        "${if (wasAlreadyInstalled) "Updated" else "Installed"} on ${target.displayName}",
                        apkInfo.versionName,
                    ))
                    runPostInstall(target.serial)
                },
                onFailure = { error ->
                    migrationRequest = migrationRequestOrNull(error, target.serial, outputPath)
                    deployments = deployments + (target.serial to deployments.forSerial(target.serial).installFailed(
                        "Installation failed on ${target.displayName}: ${error.message ?: "Unknown error"}",
                    ))
                },
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Patching complete",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Output file card
        Box(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(corners.medium))
                .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accents.secondary)
                    .align(Alignment.CenterStart)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 14.dp)
                ) {
                    Text(
                        text = "Output file",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = outputFile.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (outputFile.exists()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = formatFileSize(outputFile.length()),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = accents.primary
                        )
                    }
                }

                // Open folder link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val folderHover = remember { MutableInteractionSource() }
                    val isFolderHovered by folderHover.collectIsHoveredAsState()
                    val folderBg by animateColorAsState(
                        if (isFolderHovered) accents.primary.copy(alpha = 0.08f)
                        else Color.Transparent,
                        animationSpec = tween(150)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .hoverable(folderHover)
                            .clip(RoundedCornerShape(corners.small))
                            .background(folderBg, RoundedCornerShape(corners.small))
                            .border(
                                1.dp,
                                if (isFolderHovered) accents.primary.copy(alpha = 0.5f) else accents.primary.copy(alpha = 0.25f),
                                RoundedCornerShape(corners.small)
                            )
                            .clickable {
                                try {
                                    val folder = outputFile.parentFile
                                    if (folder != null && Desktop.isDesktopSupported()) {
                                        Desktop.getDesktop().open(folder)
                                    }
                                } catch (_: Exception) {}
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Open folder",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = accents.primary
                        )
                    }
                }
            }
        }

        // ADB install, when the user has the toggle off, render a compact
        // "ADB OFF" hint with an inline enable button rather than hiding the
        // affordance entirely (otherwise users wonder where install went).
        if (isAdbDisabledByUser) {
            Spacer(modifier = Modifier.height(12.dp))
            val enableHover = remember { MutableInteractionSource() }
            val enableHovered by enableHover.collectIsHoveredAsState()
            Box(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .height(38.dp)
                    .hoverable(enableHover)
                    .clip(RoundedCornerShape(corners.small))
                    .border(
                        1.dp,
                        if (enableHovered) accents.primary.copy(alpha = 0.5f)
                        else accents.primary.copy(alpha = 0.25f),
                        RoundedCornerShape(corners.small)
                    )
                    .background(
                        if (enableHovered) accents.primary.copy(alpha = 0.08f)
                        else Color.Transparent,
                        RoundedCornerShape(corners.small)
                    )
                    .clickable { adbPreference.onChange(true) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ADB off · Enable to install",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = accents.primary
                )
            }
        } else if (monitorState.isAdbAvailable == true) {
            Spacer(modifier = Modifier.height(12.dp))

            val readyDevices = monitorState.devices.filter { it.isReady }
            val selectedTarget = monitorState.captureOperationTarget()
            val selectedState = selectedTarget?.let { deployments.forSerial(it.serial) }

            when {
                selectedState?.installPhase == DeviceDeploymentState.InstallPhase.INSTALLED -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(accents.secondary, CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = selectedState.installMessage ?: "Installed on ${selectedTarget.displayName}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = accents.secondary
                        )
                    }
                }
                selectedState?.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = accents.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = selectedState.installMessage ?: "Installing on ${selectedTarget.displayName}…",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = accents.primary
                        )
                    }
                }
                deployments.values.any { it.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING } -> {
                    Text(
                        text = "Another device installation is still running.",
                        fontSize = 11.sp,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                selectedTarget != null -> {
                    val target = selectedTarget
                    val installHover = remember { MutableInteractionSource() }
                    val isInstallHovered by installHover.collectIsHoveredAsState()
                    val installBg by animateColorAsState(
                        if (isInstallHovered) accents.secondary.copy(alpha = 0.9f) else accents.secondary,
                        animationSpec = tween(150)
                    )

                    Box(
                        modifier = Modifier
                            .widthIn(max = 480.dp)
                            .fillMaxWidth()
                            .height(38.dp)
                            .hoverable(installHover)
                            .clip(RoundedCornerShape(corners.small))
                            .background(installBg, RoundedCornerShape(corners.small))
                            .clickable {
                                if (deployments.values.any {
                                        it.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING
                                    }
                                ) return@clickable
                                pendingInstallTarget = DeviceMonitor.state.value.captureOperationTarget()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Install on ${target.displayName}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = Color.White
                        )
                    }
                }
                readyDevices.isNotEmpty() -> {
                    Text(
                        text = "Select a device before installing",
                        fontSize = 11.sp,
                        fontFamily = font,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Text(
                        text = "Connect a device via USB to install with ADB",
                        fontSize = 11.sp,
                        fontFamily = font,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            selectedState?.installError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                if (migrationRequest != null) {
                    Spacer(Modifier.height(8.dp))
                    MorpheTooltip(TooltipText.MIGRATE) {
                        Button(
                            onClick = { showMigrationConfirm = true },
                            enabled = !migrationBusy,
                        ) {
                            Text("Uninstall & retry", fontFamily = font, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Patch another button
        OutlinedButton(
            onClick = onPatchAnother,
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .height(42.dp),
            shape = RoundedCornerShape(corners.small),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "Back to Home",
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
            )
        }
    }

    pendingInstallTarget?.let { target ->
        val deployment = deployments.forSerial(target.serial)
        DeviceInstallConfirmationDialog(
            appName = apkInfo.displayName,
            packageName = outputPackage ?: apkInfo.packageName,
            apkVersion = apkInfo.versionName,
            deviceName = target.displayName,
            deviceSerial = target.serial,
            installedVersion = deployment.installedVersion,
            replacingExisting = deployment.installed == true,
            onDismiss = { pendingInstallTarget = null },
            onConfirm = {
                pendingInstallTarget = null
                installViaAdb(target)
            },
        )
    }

    if (showMigrationConfirm && migrationRequest != null) {
        UpdateOwnerMigrationDialog(
            isBusy = migrationBusy,
            error = migrationError,
            deviceName = installTarget?.displayName,
            onCancel = {
                if (!migrationBusy) {
                    showMigrationConfirm = false
                    migrationError = null
                }
            },
            onConfirm = {
                if (!migrationBusy) {
                    migrationBusy = true
                    migrationError = null
                    scope.launch {
                        val request = migrationRequest!!
                        when (val result = UpdateOwnerMigrationCoordinator.using(adbManager).execute(request)) {
                            UpdateOwnerMigrationResult.Success -> {
                                deployments = deployments + (request.deviceSerial to
                                    deployments.forSerial(request.deviceSerial).installed(
                                        "Installed on ${installTarget?.displayName ?: request.deviceSerial}",
                                    ))
                                migrationRequest = null
                                showMigrationConfirm = false
                                // The central migration coordinator already
                                // published the package-state invalidation.
                                runPostInstall(request.deviceSerial, notifyMutation = false)
                            }
                            UpdateOwnerMigrationResult.DeviceUnavailable ->
                                migrationError = "${installTarget?.displayName ?: "The original device"} is no longer connected and ready. Nothing was uninstalled."
                            is UpdateOwnerMigrationResult.UninstallFailed ->
                                migrationError = "Uninstall failed on ${installTarget?.displayName ?: request.deviceSerial}. The patched APK was not reinstalled: ${result.message}"
                            is UpdateOwnerMigrationResult.ReinstallFailed -> {
                                deployments = deployments + (request.deviceSerial to
                                    deployments.forSerial(request.deviceSerial).installFailed(
                                        "The existing app was uninstalled from ${installTarget?.displayName ?: request.deviceSerial}, but reinstalling the patched APK failed: ${result.message}. The patched APK remains at $outputPath",
                                    ))
                                migrationRequest = null
                                showMigrationConfirm = false
                            }
                        }
                        migrationBusy = false
                    }
                }
            },
        )
    }
}
