/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.cli.command

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

/**
 * Lazy initialized HttpClient for CLI commands. One client per process is fine for short-lived
 * `morhpe-cli ....` invocations. Engine remote sources (like GitHub and GitLab) require this to be passed in.
 *
 * We could later swap `by lazy` for `fun create()` if we ever want the CLI to share lifecycle with anything else.
 */
object CliHttpClient {
    val instance: HttpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) { json() }
        }
    }
}
