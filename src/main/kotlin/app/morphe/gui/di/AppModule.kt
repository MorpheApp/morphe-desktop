/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.di

import app.morphe.engine.OriginalApkStore
import app.morphe.engine.PatchedAppStore
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.engine.network.createHttpClient
import app.morphe.engine.options.PatchPreferencesRepository
import app.morphe.engine.util.AdbManager
import app.morphe.engine.util.KeystoreService
import app.morphe.engine.util.Logger
import app.morphe.gui.batch.BatchPatchCoordinator
import app.morphe.gui.batch.BatchPlanResolver
import app.morphe.gui.data.repository.AppUpdateManager
import app.morphe.gui.data.repository.ChangelogRepository
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.LanguageRepository
import app.morphe.gui.data.repository.PeopleRepository
import app.morphe.gui.data.repository.SeenPatchesRepository
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.data.repository.UpdateCheckRepository
import app.morphe.gui.ui.screens.home.HomeViewModel
import app.morphe.gui.ui.screens.patches.PatchSelectionViewModel
import app.morphe.gui.ui.screens.patches.PatchesViewModel
import app.morphe.gui.ui.screens.patching.PatchingSessionTracker
import app.morphe.gui.ui.screens.patching.PatchingViewModel
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Dependency injection module bindings. */
val appModule = module {

    single {
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }
    }

    single {
        createHttpClient(json = get()) { message ->
            Logger.debug("HTTP: $message")
        }
    }

    single { EngineConfigRepository.shared }
    single { KeystoreService.shared }
    single { ConfigRepository() }
    single { LanguageRepository() }
    single { PatchPreferencesRepository() }
    single { SeenPatchesRepository() }
    single { PatchSourceManager(get(), get()) }
    single { UpdateCheckRepository(get()) }
    single { AppUpdateManager(get(), get()) }
    single { ChangelogRepository(get()) }
    single { PeopleRepository(get(), get()) }
    single { PatchedAppStore.shared }
    single { OriginalApkStore.shared }
    single { AdbManager() }
    single { PatchingSessionTracker() }
    single { BatchPlanResolver(get(), get(), get(), get(), get(), get(), get()) }
    single { BatchPatchCoordinator(get(), get(), get(), get(), get(), get(), get()) }

    viewModel {
        HomeViewModel(get(), get(), get(), get(), get(), batchPatchCoordinator = get())
    }
    viewModel {
        val psm = get<PatchSourceManager>()
        PatchesViewModel(
            psm.getActiveRepositorySync(),
            get(),
            psm.getLocalFilePath(),
            psm
        )
    }
    viewModel { params ->
        val psm = get<PatchSourceManager>()
        val enabledSources = psm.getEnabledSourcesSync()
        PatchSelectionViewModel(
            params.get(),
            params.get(),
            params.get(),
            params.get(),
            params.get(),
            psm.getActiveRepositorySync(),
            get(),
            get(),
            psm.getLocalFilePath(),
            params.get(),
            params.get(),
            params.get(),
            params.get(),
            params.get(),
            sourceIdsByName = enabledSources.associate { it.name to it.id },
            enabledSources = enabledSources,
            initialUseMount = params.get(),
        )
    }
    viewModel { params ->
        PatchingViewModel(
            params.get(),
            get(),
            sessionTracker = get(),
        )
    }
}
