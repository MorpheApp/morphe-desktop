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

/** Shared [HttpClient] instance for CLI commands and engine utilities. */
val sharedHttpClient: HttpClient by lazy { createHttpClient() }

/**
 * Creates an OkHttp-backed [HttpClient] configured with IPv4 preference,
 * HTTP/1.1 pinning (to avoid CDN stream resets), and redirect handling.
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
