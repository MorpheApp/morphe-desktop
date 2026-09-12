/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import app.morphe.engine.model.DevicePatchDeploymentRecord
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.util.FileChecksum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.logging.Logger

/** Persistent per-device receipts for successful Morphe APK installations. */
class DevicePatchDeploymentStore(
    private val file: File = MorpheData.devicePatchDeploymentsFile,
) {
    private val logger = Logger.getLogger(DevicePatchDeploymentStore::class.java.name)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mutex = Mutex()
    private var cache: List<DevicePatchDeploymentRecord>? = null

    suspend fun get(deviceSerial: String, packageName: String): DevicePatchDeploymentRecord? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                load().firstOrNull { it.deviceSerial == deviceSerial && it.packageName == packageName }
            }
        }

    suspend fun getForDevice(deviceSerial: String): List<DevicePatchDeploymentRecord> =
        withContext(Dispatchers.IO) {
            mutex.withLock { load().filter { it.deviceSerial == deviceSerial } }
        }

    suspend fun upsert(record: DevicePatchDeploymentRecord): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val remaining = load().filterNot {
                it.deviceSerial == record.deviceSerial && it.packageName == record.packageName
            }
            persist(listOf(record) + remaining)
        }
    }

    /** Capture the artifact and patch-source snapshot only after installation succeeds. */
    suspend fun recordSuccessfulInstall(
        deviceSerial: String,
        record: PatchedAppRecord,
        installedAt: Long = System.currentTimeMillis(),
        packageLastUpdateTime: String? = null,
    ): DevicePatchDeploymentRecord? {
        val sha256 = record.outputApkSha256
            ?.takeIf { it.isNotBlank() }
            ?: FileChecksum.fingerprintOrNull(record.outputApkPath).first
            ?: return null
        val deployment = DevicePatchDeploymentRecord(
            deviceSerial = deviceSerial,
            packageName = record.packageName,
            installedPackageName = record.installedPackageName,
            apkVersion = record.apkVersion,
            outputApkSha256 = sha256,
            sourcesSnapshot = record.sourcesSnapshot,
            installedAt = installedAt,
            packageLastUpdateTime = packageLastUpdateTime,
        )
        upsert(deployment)
        return deployment
    }

    suspend fun delete(deviceSerial: String, packageName: String): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = load()
            val remaining = current.filterNot {
                it.deviceSerial == deviceSerial && it.packageName == packageName
            }
            if (remaining.size != current.size) persist(remaining)
        }
    }

    private fun load(): List<DevicePatchDeploymentRecord> {
        cache?.let { return it }
        val records = if (file.exists()) {
            try {
                json.decodeFromString<StoreFile>(file.readText()).records
            } catch (e: Exception) {
                logger.warning("Could not read device patch deployments (${e.message}); starting empty")
                emptyList()
            }
        } else {
            emptyList()
        }
        cache = records
        return records
    }

    private fun persist(records: List<DevicePatchDeploymentRecord>) {
        try {
            file.parentFile?.mkdirs()
            val content = json.encodeToString(StoreFile.serializer(), StoreFile(records = records))
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(content)
            try {
                Files.move(
                    tmp.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: Exception) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            cache = records
        } catch (e: Exception) {
            logger.warning("Failed to write device patch deployments: ${e.message}")
        }
    }

    @Serializable
    private data class StoreFile(
        val version: Int = SCHEMA_VERSION,
        val records: List<DevicePatchDeploymentRecord> = emptyList(),
    )

    companion object {
        const val FILE_NAME = "device-patch-deployments.json"
        const val SCHEMA_VERSION = 1
        val shared: DevicePatchDeploymentStore by lazy { DevicePatchDeploymentStore() }
    }
}
