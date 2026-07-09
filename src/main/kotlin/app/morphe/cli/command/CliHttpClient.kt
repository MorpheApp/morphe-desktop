/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.cli.command

import app.morphe.engine.network.HttpService
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.Protocol

/**
 * Lazy initialized [HttpService] for CLI commands. One instance per process
 * is fine for short-lived `morphe-cli ....` invocations. Engine remote
 * sources (like GitHub and GitLab) require this to be passed in.
 *
 * Built on OkHttp rather than CIO — see [app.morphe.gui.di.appModule] for
 * the full rationale (this configuration mirrors it exactly, since both the
 * CLI and GUI need identical reliability for large patch bundle downloads).
 * HTTP/1.1 is forced to avoid intermittent HTTP/2 PROTOCOL_ERROR stream
 * resets observed when downloading large assets from GitHub-backed CDNs —
 * ported from Morphe Manager, which hit and fixed the same issue.
 *
 * We could later swap `by lazy` for `fun create()` if we ever want the CLI
 * to share lifecycle with anything else.
 */
object CliHttpClient {
    private fun buildClient(): HttpClient = HttpClient(OkHttp) {
        engine {
            config {
                // Force HTTP/1.1: avoids intermittent HTTP/2 PROTOCOL_ERROR
                // stream resets when downloading patch bundles from
                // GitHub-backed endpoints (same fix as Morphe Manager).
                protocols(listOf(Protocol.HTTP_1_1))
                followRedirects(true)
                followSslRedirects(true)
            }
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 20_000L
            socketTimeoutMillis = 5 * 60_000L
            requestTimeoutMillis = 10 * 60_000L
        }
        install(UserAgent) {
            agent = "Morphe-CLI"
        }
    }

    val instance: HttpService by lazy {
        HttpService(buildClient())
    }
}
