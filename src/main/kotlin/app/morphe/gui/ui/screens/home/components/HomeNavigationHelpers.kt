/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.navigation.NavController
import app.morphe.engine.apk.BundleFormats
import app.morphe.engine.model.VersionStatus
import app.morphe.gui.PatchSelectionParams
import app.morphe.gui.PatchSelectionScreenRoute
import app.morphe.gui.PatchingScreenRoute
import app.morphe.gui.navigateComplex
import app.morphe.gui.ui.screens.home.ApkInfo
import app.morphe.gui.ui.screens.home.HomeUiState
import app.morphe.gui.ui.screens.home.HomeViewModel
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

internal fun handleContinue(
    uiState: HomeUiState,
    viewModel: HomeViewModel,
    navController: NavController,
    isSimple: Boolean,
    coroutineScope: CoroutineScope,
    selectedPatchSource: String? = null,
    showWarning: () -> Unit
) {
    val apkInfo = uiState.apkInfo ?: return
    if (viewModel.getCachedPatchesFile() == null) return
    if (isSimple && (apkInfo.isUnsupportedApp || selectedPatchSource == null)) return
    val versionStatus = apkInfo.versionStatus
    val shouldWarn = versionStatus == VersionStatus.EXPERIMENTAL ||
                     versionStatus == VersionStatus.BUILD_UNSUPPORTED ||
                     versionStatus == VersionStatus.UNSUPPORTED
    if (shouldWarn) {
        showWarning()
    } else {
        proceedToPatching(
            apkInfo = apkInfo,
            viewModel = viewModel,
            navController = navController,
            isSimple = isSimple,
            selectedPatchSource = selectedPatchSource,
            coroutineScope = coroutineScope,
        )
    }
}

internal fun proceedToPatching(
    apkInfo: ApkInfo,
    viewModel: HomeViewModel,
    navController: NavController,
    isSimple: Boolean,
    selectedPatchSource: String? = null,
    coroutineScope: CoroutineScope,
) {
    if (isSimple) {
        if (apkInfo.isUnsupportedApp || selectedPatchSource == null) return
        coroutineScope.launch {
            val config = viewModel.createSimplePatchConfig(apkInfo, selectedPatchSource)
            navController.navigateComplex(PatchingScreenRoute, config)
        }
    } else {
        val patchesFile = viewModel.getCachedPatchesFile() ?: return
        navController.navigateComplex(
            PatchSelectionScreenRoute,
            PatchSelectionParams(
                apkPath = apkInfo.filePath,
                apkName = apkInfo.appName,
                patchesFilePath = patchesFile.absolutePath,
                packageName = apkInfo.packageName,
                apkArchitectures = apkInfo.architectures,
                apkVersion = apkInfo.versionName,
                patchesFilePaths = viewModel.getAllResolvedPatchFiles().map { it.absolutePath },
                patchSourceNames = viewModel.getAllResolvedPatchSourceNames(),
            )
        )
    }
}

internal suspend fun openFilePicker(): File? =
    MorpheFilePicker.pickFile(
        title = getString(Res.string.home_select_apk_file),
        extensions = BundleFormats.SUPPORTED_EXTENSIONS,
    )
