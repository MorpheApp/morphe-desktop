/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.config

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class MergeAndSaveJsonObjectTest {

    private val tmpDir: File = Files.createTempDirectory("morphe-config-test").toFile()
    private val configFile = File(tmpDir, "config.json")
    private val json = Json { prettyPrint = true }

    @AfterTest
    fun cleanup() {
        tmpDir.deleteRecursively()
    }

    @Test
    fun `concurrent saves keep the file valid and keep every key`() {
        val writers = 8
        val savesPerWriter = 25
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(writers)

        val tasks = (0 until writers).map { w ->
            pool.submit {
                start.await()
                repeat(savesPerWriter) { i ->
                    mergeAndSaveJsonObject(configFile, json, buildJsonObject { put("w$w-$i", "x".repeat(i)) })
                }
            }
        }
        start.countDown()
        tasks.forEach { it.get() }
        pool.shutdown()

        val saved = json.parseToJsonElement(configFile.readText()).jsonObject
        val expected = (0 until writers).flatMap { w -> (0 until savesPerWriter).map { "w$w-$it" } }.toSet()
        assertEquals(expected, saved.keys)
    }
}
