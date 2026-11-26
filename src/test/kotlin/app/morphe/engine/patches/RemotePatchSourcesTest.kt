/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import app.morphe.engine.network.HttpService
import app.morphe.engine.network.createHttpClient
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class RemotePatchSourcesTest {

    private fun startServer(handler: (HttpExchange) -> Unit): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange -> handler(exchange) }
            start()
        }

    private fun <T> withLocalHttp(
        handler: (HttpExchange) -> Unit,
        block: suspend (baseUrl: String, http: HttpService) -> T,
    ): T {
        val server = startServer(handler)
        return try {
            createHttpClient().use { client ->
                val base = "http://127.0.0.1:${server.address.port}"
                val http = HttpService(client)
                runBlocking(Dispatchers.IO) {
                    block(base, http)
                }
            }
        } finally {
            server.stop(0)
        }
    }

    private fun HttpExchange.respond(status: Int, body: ByteArray, contentType: String = "application/json") {
        responseHeaders.add("Content-Type", contentType)
        sendResponseHeaders(status, if (requestMethod == "HEAD") -1 else body.size.toLong())
        if (requestMethod != "HEAD") {
            responseBody.use { it.write(body) }
        } else {
            close()
        }
    }

    @Test
    fun `GitHubPatchSource fetches releases and main or dev manifests`() {
        var acceptValues: List<String> = emptyList()
        var apiVersionHeader: String? = null
        withLocalHttp(
            handler = { exchange ->
                when (exchange.requestURI.path) {
                    "/api/repos/Owner/Repo/releases" -> {
                        acceptValues = exchange.requestHeaders["Accept"]?.toList().orEmpty()
                        apiVersionHeader = exchange.requestHeaders.getFirst("X-GitHub-Api-Version")
                        exchange.respond(
                            200,
                            """
                            [
                              {
                                "id": 1,
                                "tag_name": "v1.4.0",
                                "prerelease": false,
                                "assets": [{"id": 10, "name": "patches-1.4.0.mpp", "browser_download_url": "https://dl/1.4.0.mpp"}]
                              },
                              {
                                "id": 2,
                                "tag_name": "v1.5.0-dev.1",
                                "prerelease": true,
                                "assets": [{"id": 11, "name": "patches-1.5.0-dev.1.mpp", "browser_download_url": "https://dl/dev.mpp"}]
                              }
                            ]
                            """.trimIndent().toByteArray(),
                        )
                    }
                    "/raw/Owner/Repo/dev/patches-bundle.json" -> exchange.respond(
                        200,
                        """{"version":"1.5.0-dev.1","download_url":"https://dl/dev.mpp"}""".toByteArray(),
                    )
                    else -> exchange.respond(404, ByteArray(0))
                }
            },
        ) { base, http ->
            val source = GitHubPatchSource(http, "Owner/Repo", apiBase = "$base/api", rawBase = "$base/raw")
            val releases = source.listReleases().getOrThrow()
            assertTrue(acceptValues.any { "application/vnd.github+json" in it }, "Accept headers were: $acceptValues")
            assertEquals("2022-11-28", apiVersionHeader)
            assertEquals(listOf("v1.4.0", "v1.5.0-dev.1"), releases.map { it.tagName })
            assertEquals("v1.5.0-dev.1", source.fetchLatestFromManifest(prerelease = true).getOrThrow().tagName)
        }
    }

    @Test
    fun `GitLabPatchSource fetches normalized releases with asset sizes and raw manifests`() {
        val mppPayload = ByteArray(128) { 1 }
        withLocalHttp(
            handler = { exchange ->
                when (exchange.requestURI.rawPath) {
                    "/api/projects/Owner%2FRepo/releases" -> exchange.respond(
                        200,
                        """
                        [
                          {
                            "tag_name": "v2.0.0",
                            "name": "v2.0.0",
                            "released_at": "2026-01-01T00:00:00Z",
                            "assets": {
                              "links": [
                                {"name": "patches-2.0.0.mpp", "direct_asset_url": "http://127.0.0.1:${exchange.localAddress.port}/files/patches.mpp"}
                              ]
                            }
                          }
                        ]
                        """.trimIndent().toByteArray(),
                    )
                    "/files/patches.mpp" -> {
                        exchange.responseHeaders.add("Content-Length", mppPayload.size.toString())
                        exchange.respond(200, mppPayload, contentType = "application/octet-stream")
                    }
                    "/raw/Owner/Repo/-/raw/main/patches-bundle.json" -> exchange.respond(
                        200,
                        """{"version":"v2.0.0","download_url":"http://127.0.0.1:${exchange.localAddress.port}/files/patches.mpp"}""".toByteArray(),
                    )
                    else -> exchange.respond(404, ByteArray(0))
                }
            },
        ) { base, http ->
            val source = GitLabPatchSource(http, "Owner/Repo", apiBase = "$base/api", rawBase = "$base/raw")
            val releases = source.listReleases().getOrThrow()
            assertEquals(1, releases.size)
            assertEquals(128L, releases.single().assets.single().size)
            assertEquals("v2.0.0", source.fetchLatestFromManifest(prerelease = false).getOrThrow().tagName)
        }
    }

    @Test
    fun `CodebergPatchSource filters drafts, coerces null assets, fetches manifests, and downloads assets`() {
        val mppBytes = "mpp-binary-data".toByteArray()
        val tempFile = Files.createTempFile("cb-test-", ".mpp").toFile()
        var seenQuery: String? = null
        try {
            withLocalHttp(
                handler = { exchange ->
                    val base = "http://127.0.0.1:${exchange.localAddress.port}"
                    when (exchange.requestURI.path) {
                        "/api/v1/repos/Owner/Repo/releases" -> {
                            seenQuery = exchange.requestURI.rawQuery
                            exchange.respond(
                                200,
                                """
                                [
                                  {
                                    "id": 1,
                                    "tag_name": "v1.0.0-draft",
                                    "draft": true,
                                    "assets": null
                                  },
                                  {
                                    "id": 2,
                                    "tag_name": "v1.1.0",
                                    "prerelease": false,
                                    "draft": false,
                                    "assets": [{"id": 9, "name": "patches-1.1.0.mpp", "browser_download_url": "$base/dl/p.mpp"}]
                                  },
                                  {
                                    "id": 3,
                                    "tag_name": "v1.0.0-empty",
                                    "draft": false,
                                    "assets": null
                                  }
                                ]
                                """.trimIndent().toByteArray(),
                            )
                        }
                        "/raw/Owner/Repo/raw/branch/dev/patches-bundle.json" -> exchange.respond(
                            200,
                            """{"version":"1.2.0-dev.2","download_url":"$base/dl/p.mpp"}""".toByteArray(),
                        )
                        "/dl/p.mpp" -> exchange.respond(200, mppBytes, contentType = "application/octet-stream")
                        else -> exchange.respond(404, ByteArray(0))
                    }
                },
            ) { base, http ->
                val source = CodebergPatchSource(
                    http = http,
                    repoPath = "Owner/Repo",
                    apiBase = "$base/api/v1",
                    rawBase = "$base/raw",
                )
                val releases = source.listReleases().getOrThrow()
                assertEquals("limit=50", seenQuery)
                assertEquals(listOf("v1.1.0", "v1.0.0-empty"), releases.map { it.tagName })
                assertTrue(releases[1].assets.isEmpty())

                val devManifest = source.fetchLatestFromManifest(prerelease = true).getOrThrow()
                assertEquals("v1.2.0-dev.2", devManifest.tagName)
                assertTrue(devManifest.isPrerelease)

                source.downloadAsset(devManifest.assets.single(), tempFile).getOrThrow()
            }
            assertEquals(mppBytes.decodeToString(), tempFile.readText())
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `JsonPatchSource handles custom endpoints, channel comparison, page_url, and PAT retry`() {
        var mainVersion = "1.1.0"
        var devVersion = "1.2.0-dev.1"
        var patRetryAttempts = 0
        val tempFile = Files.createTempFile("json-test-", ".mpp").toFile()
        try {
            withLocalHttp(
                handler = { exchange ->
                    val base = "http://127.0.0.1:${exchange.localAddress.port}"
                    when (exchange.requestURI.path) {
                        "/custom/bundle.json" -> exchange.respond(
                            200,
                            """
                            {
                              "version": "3.0.0-dev.2",
                              "download_url": "$base/files/download?token=abc",
                              "page_url": "https://github.com/External/Repo/releases/tag/v3.0.0-dev.2"
                            }
                            """.trimIndent().toByteArray(),
                        )
                        "/gh/Owner/Repo/main/patches-bundle.json" -> exchange.respond(
                            200,
                            """{"version":"$mainVersion","download_url":"$base/files/download?token=abc"}""".toByteArray(),
                        )
                        "/gh/Owner/Repo/dev/patches-bundle.json" -> exchange.respond(
                            200,
                            """{"version":"$devVersion","download_url":"$base/files/download?token=abc"}""".toByteArray(),
                        )
                        "/private/Owner/Repo/main/patches-bundle.json" -> {
                            val auth = exchange.requestHeaders.getFirst("Authorization")
                            if (auth == "Bearer secret-pat") {
                                patRetryAttempts++
                                exchange.respond(
                                    200,
                                    """{"version":"4.0.0","download_url":"$base/files/download?token=abc"}""".toByteArray(),
                                )
                            } else {
                                exchange.respond(404, ByteArray(0))
                            }
                        }
                        "/files/download" -> exchange.respond(200, "json-bundle-bytes".toByteArray(), "application/octet-stream")
                        else -> exchange.respond(404, ByteArray(0))
                    }
                },
            ) { base, http ->
                // 1. Custom third-party endpoint
                val customSource = JsonPatchSource(http, "$base/custom/bundle.json")
                assertFalse(customSource.supportsPrerelease)
                val customRelease = customSource.fetchLatestFromManifest(prerelease = false).getOrThrow()
                assertEquals("v3.0.0-dev.2", customRelease.tagName)
                assertEquals("https://github.com/External/Repo/releases/tag/v3.0.0-dev.2", customRelease.pageUrl)
                assertEquals("download.mpp", customRelease.assets.single().name)
                assertTrue(customRelease.assets.single().isPatchFile())
                customSource.downloadAsset(customRelease.assets.single(), tempFile).getOrThrow()

                // 2. Code-host JSON endpoint comparing dev vs main in parallel
                val ghSource = JsonPatchSource(
                    http = http,
                    endpoint = "https://raw.githubusercontent.com/Owner/Repo/main/patches-bundle.json",
                    urlRewriter = { it.replace("https://raw.githubusercontent.com", "$base/gh") },
                )
                assertTrue(ghSource.supportsPrerelease)
                assertEquals("v1.2.0-dev.1", ghSource.fetchLatestFromManifest(prerelease = true).getOrThrow().tagName)

                mainVersion = "1.3.0"
                devVersion = "1.2.0-dev.5"
                val chosenWhenStableAhead = ghSource.fetchLatestFromManifest(prerelease = true).getOrThrow()
                assertEquals("v1.3.0", chosenWhenStableAhead.tagName)
                assertEquals(
                    "https://github.com/Owner/Repo/releases/tag/v1.3.0",
                    chosenWhenStableAhead.pageUrl,
                )
                assertEquals(listOf("v1.3.0", "v1.2.0-dev.5"), ghSource.listReleases().getOrThrow().map { it.tagName })

                // 3. PAT retry on raw.githubusercontent.com 404
                val privateSource = JsonPatchSource(
                    http = http,
                    endpoint = "https://raw.githubusercontent.com/Owner/Repo/main/patches-bundle.json",
                    patProvider = { "secret-pat" },
                    urlRewriter = { it.replace("https://raw.githubusercontent.com", "$base/private") },
                )
                val privateRelease = privateSource.fetchLatestFromManifest(prerelease = false).getOrThrow()
                assertEquals("v4.0.0", privateRelease.tagName)
                assertEquals(1, patRetryAttempts)

                // 4. PatchCache dirName mapping & 404 error handling
                assertEquals("MorpheApp-morphe-patches", PatchCache.dirName("MorpheApp/morphe-patches"))
                assertEquals("json-example.com-0123456789ab", PatchCache.dirName("json/example.com-0123456789ab"))
                val missing = JsonPatchSource(http, "$base/missing.json").fetchLatestFromManifest(prerelease = false)
                assertTrue(missing.isFailure)
                assertNull(missing.getOrNull())
            }
            assertEquals("json-bundle-bytes", tempFile.readText())
        } finally {
            tempFile.delete()
        }
    }
}
