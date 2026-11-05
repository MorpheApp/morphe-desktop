/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.config

import app.morphe.engine.MorpheData
import app.morphe.engine.util.Logger
import app.morphe.engine.util.PortablePaths
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

open class EngineConfigRepository(
    private val configFile: File = MorpheData.configFile,
) {
    private val mutex = Mutex()
    private var cachedConfig: EngineConfig? = null
    private val _configFlow = MutableStateFlow<EngineConfig?>(null)
    val configFlow: StateFlow<EngineConfig?> = _configFlow.asStateFlow()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    open suspend fun loadConfig(): EngineConfig = withContext(Dispatchers.IO) {
        mutex.withLock {
            cachedConfig?.let {
                _configFlow.value = it
                return@withContext it
            }
            if (!configFile.exists()) {
                val default = EngineConfig()
                cachedConfig = default
                _configFlow.value = default
                return@withContext default
            }
            try {
                val element = json.parseToJsonElement(configFile.readText())
                val config = json.decodeFromJsonElement<EngineConfig>(element)
                cachedConfig = config
                _configFlow.value = config
                config
            } catch (e: Exception) {
                Logger.warn("Failed to parse engine config from ${configFile.absolutePath}: ${e.message}")
                val fallback = EngineConfig()
                cachedConfig = fallback
                _configFlow.value = fallback
                fallback
            }
        }
    }

    suspend fun saveConfig(update: (EngineConfig) -> EngineConfig) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = cachedConfig ?: if (configFile.exists()) {
                runCatching {
                    json.decodeFromJsonElement<EngineConfig>(json.parseToJsonElement(configFile.readText()))
                }.getOrDefault(EngineConfig())
            } else EngineConfig()

            val updated = update(current)
            val engineObj = json.encodeToJsonElement(EngineConfig.serializer(), updated).jsonObject
            mergeAndSaveJsonObject(configFile, json, engineObj)
            cachedConfig = updated
            _configFlow.value = updated
        }
    }

    suspend fun getGitHubPat(): String = loadConfig().gitHubPat

    suspend fun setGitHubPat(pat: String) = saveConfig { it.copy(gitHubPat = pat) }

    suspend fun setKeystoreDetails(path: String?, password: String?, alias: String, entryPassword: String) =
        saveConfig {
            it.copy(
                keystorePath = path?.let(PortablePaths::storableForm),
                keystorePassword = password,
                keystoreAlias = alias,
                keystoreEntryPassword = entryPassword,
            )
        }

    suspend fun setKeystorePath(path: String?) =
        saveConfig { it.copy(keystorePath = path?.let(PortablePaths::storableForm)) }

    suspend fun setDefaultOutputDirectory(path: String?) =
        saveConfig { it.copy(defaultOutputDirectory = path?.let(PortablePaths::storableForm)) }

    suspend fun setKeepArchitectures(keep: Set<String>) =
        saveConfig { it.copy(keepArchitectures = keep) }

    suspend fun setAutoCleanupTempFiles(enabled: Boolean) =
        saveConfig { it.copy(autoCleanupTempFiles = enabled) }

    suspend fun setAutoStartAdb(enabled: Boolean) =
        saveConfig { it.copy(autoStartAdb = enabled) }

    suspend fun setAutoRouteLinksAfterInstall(enabled: Boolean) =
        saveConfig { it.copy(autoRouteLinksAfterInstall = enabled) }

    suspend fun setDisableStockLinksAfterInstall(enabled: Boolean) =
        saveConfig { it.copy(disableStockLinksAfterInstall = enabled) }

    suspend fun setDeveloperOptions(enabled: Boolean) =
        saveConfig { it.copy(developerOptions = enabled) }

    suspend fun setBytecodeMode(mode: String) =
        saveConfig { it.copy(bytecodeMode = mode) }

    suspend fun setValidateFingerprints(enabled: Boolean) =
        saveConfig { it.copy(validateFingerprints = enabled) }

    suspend fun setVerifyWithSdk(enabled: Boolean) =
        saveConfig { it.copy(verifyWithSdk = enabled) }

    suspend fun setCustomAndroidSdkPath(path: String?) =
        saveConfig { it.copy(customAndroidSdkPath = path?.let(PortablePaths::storableForm)) }

    suspend fun setLastLocalPatchDir(path: String?) =
        saveConfig { it.copy(lastLocalPatchDir = path) }

    suspend fun setExcludedMppPatterns(patterns: List<String>) =
        saveConfig { it.copy(excludedMppPatterns = patterns) }

    companion object {
        val shared: EngineConfigRepository by lazy { EngineConfigRepository() }
    }
}

internal fun mergeAndSaveJsonObject(
    configFile: File,
    json: Json,
    newEntries: JsonObject,
) {
    val existingObj = if (configFile.exists()) {
        runCatching { json.parseToJsonElement(configFile.readText()).jsonObject }.getOrDefault(JsonObject(emptyMap()))
    } else JsonObject(emptyMap())

    val mergedMap = existingObj.toMutableMap()
    newEntries.forEach { (k, v) -> mergedMap[k] = v }
    val mergedJson = json.encodeToString(JsonObject.serializer(), JsonObject(mergedMap))

    configFile.parentFile?.mkdirs()
    configFile.writeText(mergedJson)
}
