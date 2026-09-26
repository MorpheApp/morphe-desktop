/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.navigation.NavController
import app.morphe.gui.PatchSelectionParams
import app.morphe.gui.PatchSelectionScreenRoute
import app.morphe.gui.navigateComplex
import app.morphe.gui.ui.screens.home.HomeUiState
import app.morphe.gui.ui.screens.home.HomeViewModel
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.gui.util.VersionStatus
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import org.jetbrains.compose.resources.getString

internal fun handleContinue(
    uiState: HomeUiState,
    viewModel: HomeViewModel,
    navController: NavController,
    showWarning: () -> Unit
) {
    val patchesFile = viewModel.getCachedPatchesFile() ?: return
    val versionStatus = uiState.apkInfo?.versionStatus
    if (versionStatus != null && versionStatus != VersionStatus.LATEST_STABLE && versionStatus != VersionStatus.UNKNOWN) {
        showWarning()
    } else {
        uiState.apkInfo?.let { info ->
            navController.navigateComplex(
                PatchSelectionScreenRoute,
                PatchSelectionParams(
                    apkPath = info.filePath,
                    apkName = info.appName,
                    patchesFilePath = patchesFile.absolutePath,
                    packageName = info.packageName,
                    apkArchitectures = info.architectures,
                    apkVersion = info.versionName,
                    patchesFilePaths = viewModel.getAllResolvedPatchFiles().map { it.absolutePath },
                    patchSourceNames = viewModel.getAllResolvedPatchSourceNames(),
                )
            )
        }
    }
}

internal suspend fun openFilePicker(): File? =
    MorpheFilePicker.pickFile(
        title = getString(Res.string.home_select_apk_file),
        extensions = listOf("apk", "apkm", "xapk", "apks"),
    )
