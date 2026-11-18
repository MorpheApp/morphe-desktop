/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.repository

import app.morphe.engine.network.createHttpClient
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class PeopleRepositoryTest {

    private val tmpDir: File = Files.createTempDirectory("morphe-people-test").toFile()
    private val cacheFile: File get() = File(tmpDir, "people.json")
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @AfterTest
    fun cleanup() {
        tmpDir.deleteRecursively()
    }

    private fun samplePayload(): String = """
        {
          "contributors": [
            {
              "name": "Alice",
              "url": "https://github.com/alice",
              "avatar": "https://avatars.githubusercontent.com/u/1?v=4"
            },
            {
              "name": "Bob",
              "url": "https://github.com/bob"
            }
          ],
          "supporters": [
            {
              "name": "MegaBacker",
              "url": "https://opencollective.com/megabacker",
              "avatar": "https://images.opencollective.com/mega/avatar.png",
              "mega": true
            },
            {
              "name": "SupporterTwo",
              "url": "https://opencollective.com/supporter2"
            }
          ],
          "supporters_total": 42
        }
    """.trimIndent()

    @Test
    fun `decodes contributors, supporters, supporters_total, and mega flag`() {
        createHttpClient(json = json).use { http ->
            val repo = PeopleRepository(http = http, json = json, cacheFileProvider = { cacheFile })
            val parsed = repo.decode(samplePayload())
            assertNotNull(parsed)
            assertEquals(2, parsed.contributors.size)
            assertEquals("Alice", parsed.contributors[0].name)
            assertEquals("https://avatars.githubusercontent.com/u/1?v=4", parsed.contributors[0].avatar)
            assertFalse(parsed.contributors[0].mega)
            assertNull(parsed.contributors[1].avatar)

            assertEquals(2, parsed.supporters.size)
            assertTrue(parsed.supporters[0].mega)
            assertFalse(parsed.supporters[1].mega)
            assertEquals(42, parsed.supportersTotal)
        }
    }

    @Test
    fun `decode returns null on malformed json`() {
        createHttpClient(json = json).use { http ->
            val repo = PeopleRepository(http = http, json = json, cacheFileProvider = { cacheFile })
            assertNull(repo.decode("{ invalid json"))
        }
    }

    @Test
    fun `refresh loads fresh disk cache into StateFlow`() = runBlocking {
        cacheFile.writeText(samplePayload())
        cacheFile.setLastModified(System.currentTimeMillis())

        createHttpClient(json = json).use { http ->
            val repo = PeopleRepository(http = http, json = json, cacheFileProvider = { cacheFile })
            assertNull(repo.people.value)

            val available = repo.refresh()
            assertTrue(available)
            val loaded = repo.people.value
            assertNotNull(loaded)
            assertEquals(2, loaded.contributors.size)
            assertEquals(2, loaded.supporters.size)
            assertEquals(42, loaded.supportersTotal)
        }
    }

    @Test
    fun `refresh keeps stale disk cache when network fails`() = runBlocking {
        cacheFile.writeText(samplePayload())
        // Age cache beyond the 1-hour refresh threshold so fetch() is attempted.
        cacheFile.setLastModified(System.currentTimeMillis() - 2 * 60 * 60 * 1000L)

        createHttpClient(json = json).use { http ->
            val repo = PeopleRepository(
                http = http,
                json = json,
                cacheFileProvider = { cacheFile },
                endpointUrl = "http://127.0.0.1:1/people",
            )
            val available = repo.refresh()
            assertTrue(available)
            assertEquals(42, repo.people.value?.supportersTotal)
        }
    }

    @Test
    fun `refresh fetches from endpoint and updates disk cache`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val payload = samplePayload()
        server.createContext("/v2/people") { exchange ->
            val bytes = payload.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        val http = createHttpClient(json = json)
        try {
            val repo = PeopleRepository(
                http = http,
                json = json,
                cacheFileProvider = { cacheFile },
                endpointUrl = "http://127.0.0.1:${server.address.port}/v2/people",
            )
            assertTrue(repo.refresh())
            assertEquals(42, repo.people.value?.supportersTotal)
            assertTrue(cacheFile.exists())
            assertEquals(payload, cacheFile.readText())
        } finally {
            http.close()
            server.stop(0)
        }
    }
}
