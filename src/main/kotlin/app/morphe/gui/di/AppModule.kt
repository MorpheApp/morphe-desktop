/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.di

import app.morphe.engine.PatchedAppStore
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.LanguageRepository
import app.morphe.gui.data.repository.PatchPreferencesRepository
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.data.repository.UpdateCheckRepository
import app.morphe.gui.ui.screens.home.HomeViewModel
import app.morphe.gui.ui.screens.patches.PatchSelectionViewModel
import app.morphe.gui.ui.screens.patches.PatchesViewModel
import app.morphe.gui.ui.screens.patching.PatchingViewModel
import app.morphe.gui.ui.screens.quick.QuickPatchViewModel
import app.morphe.gui.util.Logger as MorpheLogger
import app.morphe.gui.util.PatchService
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import java.net.Inet4Address
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.Protocol
import org.koin.dsl.module

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

    // Ktor HTTP Client
    single {
        HttpClient(OkHttp) {
            engine {
                config {
                    // Prefer IPv4. A host with an advertised but unroutable IPv6
                    // address otherwise burns the whole connect timeout before
                    // anything tries the working A record.
                    dns { hostname ->
                        val all = Dns.SYSTEM.lookup(hostname)
                        all.filterIsInstance<Inet4Address>().ifEmpty { all }
                    }
                    // Pin HTTP/1.1. Avoids intermittent HTTP/2 PROTOCOL_ERROR stream
                    // resets seen against GitHub-backed download endpoints.
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
                        MorpheLogger.debug("HTTP: $message")
                    }
                }
            }
            // Socket-based (idle) timeouts, not a wall-clock cap. A large but flowing
            // patch download is never killed just for being big.
            // requestTimeoutMillis is left unset (infinite).
            install(HttpTimeout) {
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 60_000
            }
            // Retry/429 handling lives in HttpService (single layer). Not a client plugin, to avoid compounding retries.
        }
    }

    // Repositories and Services
    single { ConfigRepository() }
    single { LanguageRepository() }
    single { PatchPreferencesRepository() }
    single { PatchSourceManager(get(), get()) }
    single { PatchService() }
    single { UpdateCheckRepository(get()) }
    single { PatchedAppStore.shared }

    // ViewModels (ScreenModels)
    // ViewModels observe PatchSourceManager.sourceVersion and reload on source changes.
    factory {
        HomeViewModel(get(), get(), get(), get(), get())
    }
    factory {
        QuickPatchViewModel(get(), get(), get(), get())
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
            params.get(),
            params.get(),
            params.get(),
        )
    }
    factory { params ->
        PatchingViewModel(
            params.get(),
            get(),
            get(),
            get()
        )
    }
}
