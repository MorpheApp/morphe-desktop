/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.repository

import app.morphe.engine.MorpheConstants
import app.morphe.engine.MorpheData
import app.morphe.engine.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Repository for the people behind Morphe: contributors (all time, 6 and 3 months) and Open Collective supporters,
 * fetched from the Morphe API and cached on disk so lists render immediately and survive offline use.
 */
class PeopleRepository(
    private val http: HttpClient,
    private val json: Json,
    private val cacheFileProvider: () -> File = { MorpheData.peopleCacheFile },
    private val endpointUrl: String = MorpheConstants.PEOPLE_URL,
) {
    private val _people = MutableStateFlow<People?>(null)

    /** Null until the disk cache or the network has answered. */
    val people: StateFlow<People?> = _people.asStateFlow()

    private val refreshMutex = Mutex()

    /**
     * Brings [people] up to date, loading the cached lists first if nothing is loaded yet.
     *
     * @return Whether there is any data to display, fresh or cached.
     */
    suspend fun refresh(): Boolean = refreshMutex.withLock {
        withContext(Dispatchers.IO) {
            val cacheFile = cacheFileProvider()
            if (_people.value == null) {
                _people.value = readCache(cacheFile)
            }
            if (!cacheFile.exists() || System.currentTimeMillis() - cacheFile.lastModified() >= FETCH_INTERVAL_MILLIS) {
                fetch(cacheFile)
            }
            _people.value != null
        }
    }

    private suspend fun fetch(cacheFile: File) {
        val body = try {
            val response = http.get(endpointUrl) {
                header(HttpHeaders.UserAgent, MorpheConstants.MORPHE_USER_AGENT)
            }
            if (!response.status.isSuccess()) {
                Logger.warn("Failed to fetch people: ${response.status}")
                return
            }
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn("Failed to fetch people", e)
            return
        }
        val fresh = decode(body) ?: return
        _people.value = fresh
        runCatching {
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(body)
        }.onFailure { Logger.warn("Failed to cache people", it) }
    }

    internal fun decode(body: String): People? = try {
        json.decodeFromString<People>(body)
    } catch (e: Exception) {
        Logger.warn("Failed to decode people", e)
        null
    }

    private fun readCache(cacheFile: File): People? {
        if (!cacheFile.exists()) return null
        return runCatching { cacheFile.readText() }.getOrNull()?.let(::decode)
    }

    @Serializable
    data class People(
        val contributors: List<Person> = emptyList(),
        @SerialName("contributors_6_months")
        val contributors6Months: List<Person> = emptyList(),
        @SerialName("contributors_3_months")
        val contributors3Months: List<Person> = emptyList(),
        val supporters: List<Person> = emptyList(),
        @SerialName("supporters_total")
        val supportersTotal: Int = 0,
    )

    @Serializable
    data class Person(
        val name: String,
        val url: String,
        val avatar: String? = null,
        val mega: Boolean = false,
    )

    private companion object {
        const val FETCH_INTERVAL_MILLIS = 60 * 60 * 1000L
    }
}
