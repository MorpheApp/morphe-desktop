/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import java.net.Inet4Address
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.Protocol

private val defaultHttpJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

/**
 * Shared lazy [HttpClient] instance for CLI commands and engine utilities
 * that don't manage a separate DI container.
 */
val sharedHttpClient: HttpClient by lazy { createHttpClient() }

/**
 * Factory for Morphe's standard Ktor [HttpClient] backed by OkHttp:
 * - Prefers IPv4 A records when available so broken IPv6 routes don't stall connects
 * - Pins HTTP/1.1 to avoid intermittent HTTP/2 stream resets on GitHub CDN redirects
 * - Follows HTTP and SSL redirects
 * - Uses idle/socket timeouts (30s connect / 60s socket) without a total wall-clock cap
 */
fun createHttpClient(
    json: Json = defaultHttpJson,
    onLog: ((String) -> Unit)? = null,
): HttpClient = HttpClient(OkHttp) {
    engine {
        config {
            dns { hostname ->
                val all = Dns.SYSTEM.lookup(hostname)
                all.filterIsInstance<Inet4Address>().ifEmpty { all }
            }
            protocols(listOf(Protocol.HTTP_1_1))
            followRedirects(true)
            followSslRedirects(true)
        }
    }
    install(ContentNegotiation) {
        json(json)
    }
    if (onLog != null) {
        install(Logging) {
            level = LogLevel.INFO
            logger = object : Logger {
                override fun log(message: String) {
                    onLog(message)
                }
            }
        }
    }
    install(HttpTimeout) {
        connectTimeoutMillis = 30_000
        socketTimeoutMillis = 60_000
    }
}
