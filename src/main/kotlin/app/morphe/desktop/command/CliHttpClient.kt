/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.desktop.command

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Lazy initialized HttpClient for CLI commands. One client per process is fine for short-lived
 * `morhpe-cli ....` invocations. Engine remote sources (like GitHub and GitLab) require this to be passed in.
 *
 * We could later swap `by lazy` for `fun create()` if we ever want the CLI to share lifecycle with anything else.
 */
object CliHttpClient {
    val instance: HttpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            // Idle/socket timeouts (not a total cap) so large .mpp downloads don't fail
            // for being big. Only genuine stalls or issues fail. Default CIO requestTimeout is 15s.
            install(HttpTimeout) {
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 60_000
            }
            // Retry/429 handling lives in HttpService (single layer), not a client plugin.
            engine {
                requestTimeout = 0
            }
        }
    }
}
