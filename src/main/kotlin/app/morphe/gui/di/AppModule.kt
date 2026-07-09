/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.di

import app.morphe.engine.network.HttpService
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchPreferencesRepository
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.data.repository.UpdateCheckRepository
import app.morphe.gui.util.PatchService
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import okhttp3.Protocol
import org.koin.dsl.module
import app.morphe.gui.ui.screens.home.HomeViewModel
import app.morphe.gui.ui.screens.patches.PatchesViewModel
import app.morphe.gui.ui.screens.patches.PatchSelectionViewModel
import app.morphe.gui.ui.screens.patching.PatchingViewModel

/**
 * Main Koin module for dependency injection.
 */
val appModule = module {

    // JSON serialization
    single {
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }
    }

    // Ktor HTTP Client — OkHttp engine (not CIO). OkHttp is a battle-tested
    // JVM-native engine (fully supported on Compose Desktop) with mature
    // connection pooling and protocol negotiation, matching the engine
    // Morphe Manager uses on Android. Forcing HTTP/1.1 avoids intermittent
    // HTTP/2 PROTOCOL_ERROR stream resets seen when downloading large patch
    // bundles from GitHub-backed CDNs — the same fix Manager already ships.
    single {
        HttpClient(OkHttp) {
            engine {
                config {
                    protocols(listOf(Protocol.HTTP_1_1))
                    followRedirects(true)
                    followSslRedirects(true)
                }
            }
            install(ContentNegotiation) {
                json(get())
            }
            install(Logging) {
                level = LogLevel.INFO
                logger = object : Logger {
                    override fun log(message: String) {
                        app.morphe.gui.util.Logger.debug("HTTP: $message")
                    }
                }
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 20_000L
                socketTimeoutMillis = 5 * 60_000L
                requestTimeoutMillis = 10 * 60_000L
            }
            install(UserAgent) {
                agent = "Morphe-CLI-GUI"
            }
        }
    }

    // Central networking layer — see app.morphe.engine.network.HttpService.
    // GitHubPatchSource / GitLabPatchSource (and anything else that needs
    // reliable large-file downloads, retries, and streaming) depend on this
    // instead of the raw HttpClient.
    single { HttpService(get()) }

    // Repositories and Services
    single { ConfigRepository() }
    single { PatchPreferencesRepository() }
    single { PatchSourceManager(get(), get()) }
    single { PatchService() }
    single { UpdateCheckRepository(get()) }

    // ViewModels (ScreenModels)
    // ViewModels observe PatchSourceManager.sourceVersion and reload on source changes.
    factory {
        HomeViewModel(get(), get(), get(), get())
    }
    factory { params ->
        val psm = get<PatchSourceManager>()
        PatchesViewModel(
            params.get(),
            params.get(),
            psm.getActiveRepositorySync(),
            get(),
            psm.getLocalFilePath(),
            psm
        )
    }
    factory { params ->
        val psm = get<PatchSourceManager>()
        PatchSelectionViewModel(
            params.get(),
            params.get(),
            params.get(),
            params.get(),
            params.get(),
            get(),
            psm.getActiveRepositorySync(),
            get(),
            get(),
            psm.getActiveSourceName(),
            psm.getLocalFilePath(),
            params.get(),
            params.get(),
        )
    }
    factory { params ->
        PatchingViewModel(
            params.get(),
            get(),
            get()
        )
    }
}
