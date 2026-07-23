/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.engine.update.DownloadProgress
import app.morphe.engine.update.UpdateInstaller
import app.morphe.engine.update.UpdateReleaseInfo
import app.morphe.engine.update.VerificationResult
import app.morphe.gui.data.model.UpdateChannelPreference
import app.morphe.gui.data.repository.SelfUpdateRepository
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.system.exitProcess

/**
 * State machine for the dialog. Purely descriptive — every transition is
 * driven by a suspend call into [SelfUpdateRepository]; this file has no
 * update logic of its own beyond sequencing those calls (see [SelfUpdateDialog]).
 */
private sealed class SelfUpdatePhase {
    object Checking : SelfUpdatePhase()
    object UpToDate : SelfUpdatePhase()
    data class Available(val info: UpdateReleaseInfo, val alreadyStaged: File?) : SelfUpdatePhase()
    data class Downloading(val info: UpdateReleaseInfo, val progress: DownloadProgress?) : SelfUpdatePhase()
    data class Verifying(val info: UpdateReleaseInfo) : SelfUpdatePhase()
    data class Installing(val info: UpdateReleaseInfo) : SelfUpdatePhase()
    data class Failed(val info: UpdateReleaseInfo?, val message: String) : SelfUpdatePhase()
}

/**
 * Self-update dialog. Opened from [UpdateBanner]'s Download action.
 *
 * @param autoDownload when true (from [app.morphe.gui.data.model.AppConfig.autoDownloadUpdates]),
 *   skips straight from [SelfUpdatePhase.Available] into downloading instead
 *   of waiting for the user to press "Download & Install".
 * @param onExitForInstall called right before the process exits to hand off
 *   to the updater — lets the caller do any last Compose-side cleanup
 *   (closing the window) before [exitProcess] runs. The dialog itself
 *   performs the exit; there's no "installed" state to render because the
 *   process is gone by the time installation actually happens.
 */
@Composable
fun SelfUpdateDialog(
    channel: UpdateChannelPreference,
    autoDownload: Boolean,
    repository: SelfUpdateRepository,
    onDismiss: () -> Unit,
    onExitForInstall: () -> Unit = {},
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf<SelfUpdatePhase>(SelfUpdatePhase.Checking) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    fun startDownload(info: UpdateReleaseInfo) {
        downloadJob = scope.launch {
            phase = SelfUpdatePhase.Downloading(info, null)
            try {
                val file = repository.download(info) { progress ->
                    phase = SelfUpdatePhase.Downloading(info, progress)
                }
                phase = SelfUpdatePhase.Verifying(info)
                when (val result = repository.verify(file, info)) {
                    is VerificationResult.Success -> {
                        Logger.info("SelfUpdateDialog: verified ${file.name} (sha256=${result.sha256})")
                        phase = SelfUpdatePhase.Installing(info)
                        when (val launch = repository.launchInstaller(file)) {
                            is UpdateInstaller.LaunchResult.Success -> {
                                onExitForInstall()
                                exitProcess(0)
                            }
                            is UpdateInstaller.LaunchResult.Unsupported ->
                                phase = SelfUpdatePhase.Failed(info, launch.reason)
                            is UpdateInstaller.LaunchResult.Failure ->
                                phase = SelfUpdatePhase.Failed(info, launch.reason)
                        }
                    }
                    else -> {
                        Logger.error("SelfUpdateDialog: verification failed: $result")
                        file.delete()
                        phase = SelfUpdatePhase.Failed(info, result.describe())
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.error("SelfUpdateDialog: download failed", e)
                phase = SelfUpdatePhase.Failed(info, e.message ?: "Download failed.")
            }
        }
    }

    LaunchedEffect(channel) {
        phase = SelfUpdatePhase.Checking
        val info = repository.checkForUpdate(channel)
        if (info == null) {
            phase = SelfUpdatePhase.UpToDate
            return@LaunchedEffect
        }

        val staged = repository.alreadyStaged(info)
        if (staged == null) {
            phase = SelfUpdatePhase.Available(info, alreadyStaged = null)
            if (autoDownload) startDownload(info)
            return@LaunchedEffect
        }

        // A matching-size file is already sitting in the staging dir from a
        // prior session — re-verify it (never trust "same size" alone)
        // before offering to install it, falling back to a fresh download
        // if it turns out to be a stale/corrupt leftover.
        phase = SelfUpdatePhase.Verifying(info)
        when (val result = repository.verify(staged, info)) {
            is VerificationResult.Success -> {
                phase = SelfUpdatePhase.Installing(info)
                when (val launch = repository.launchInstaller(staged)) {
                    is UpdateInstaller.LaunchResult.Success -> {
                        onExitForInstall()
                        exitProcess(0)
                    }
                    is UpdateInstaller.LaunchResult.Unsupported -> phase = SelfUpdatePhase.Failed(info, launch.reason)
                    is UpdateInstaller.LaunchResult.Failure -> phase = SelfUpdatePhase.Failed(info, launch.reason)
                }
            }
            else -> {
                staged.delete()
                phase = SelfUpdatePhase.Available(info, alreadyStaged = null)
                if (autoDownload) startDownload(info)
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            downloadJob?.cancel()
            onDismiss()
        },
        shape = RoundedCornerShape(corners.medium),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.NewReleases, contentDescription = null, tint = accents.secondary, modifier = Modifier.size(18.dp))
                Text(
                    text = "UPDATE MORPHE",
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    fontSize = 13.sp,
                    letterSpacing = 1.5.sp,
                )
            }
        },
        text = {
            Box(modifier = Modifier.widthIn(min = 360.dp, max = 420.dp)) {
                DialogBody(phase, mono, accents)
            }
        },
        confirmButton = {
            when (val p = phase) {
                is SelfUpdatePhase.Available -> TextButton(onClick = { startDownload(p.info) }) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("DOWNLOAD & INSTALL", fontFamily = mono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                is SelfUpdatePhase.Downloading -> TextButton(onClick = {
                    downloadJob?.cancel()
                    phase = SelfUpdatePhase.Available(p.info, alreadyStaged = null)
                }) {
                    Text("CANCEL", fontFamily = mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
                is SelfUpdatePhase.Failed -> {
                    Row {
                        if (p.info != null) {
                            TextButton(onClick = { startDownload(p.info) }) {
                                Text("RETRY", fontFamily = mono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                else -> {}
            }
        },
        dismissButton = {
            val dismissable = phase !is SelfUpdatePhase.Downloading &&
                phase !is SelfUpdatePhase.Verifying &&
                phase !is SelfUpdatePhase.Installing
            if (dismissable) {
                TextButton(onClick = { downloadJob?.cancel(); onDismiss() }) {
                    Text(
                        text = if (phase is SelfUpdatePhase.Available) "LATER" else "CLOSE",
                        fontFamily = mono,
                        fontSize = 11.sp,
                    )
                }
            }
        },
    )
}

@Composable
private fun DialogBody(
    phase: SelfUpdatePhase,
    mono: androidx.compose.ui.text.font.FontFamily,
    accents: app.morphe.gui.ui.theme.MorpheAccentColors,
) {
    when (phase) {
        is SelfUpdatePhase.Checking -> LoadingRow("Checking for updates…", mono)
        is SelfUpdatePhase.UpToDate -> LoadingRow("You're already on the latest version.", mono, icon = Icons.Default.CheckCircle)
        is SelfUpdatePhase.Available -> ReleaseSummary(phase.info, mono, accents)
        is SelfUpdatePhase.Downloading -> DownloadProgressView(phase.info, phase.progress, mono, accents)
        is SelfUpdatePhase.Verifying -> LoadingRow("Verifying download…", mono)
        is SelfUpdatePhase.Installing -> LoadingRow("Handing off to installer…", mono)
        is SelfUpdatePhase.Failed -> Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Column {
                Text("Update failed", fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(phase.message, fontFamily = mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LoadingRow(text: String, mono: androidx.compose.ui.text.font.FontFamily, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        } else {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        Text(text, fontFamily = mono, fontSize = 12.sp)
    }
}

@Composable
private fun ReleaseSummary(
    info: UpdateReleaseInfo,
    mono: androidx.compose.ui.text.font.FontFamily,
    accents: app.morphe.gui.ui.theme.MorpheAccentColors,
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LabeledValue("CURRENT", "v${info.currentVersion}", mono)
            LabeledValue("LATEST", "v${info.latestVersion}", mono, accents.secondary)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LabeledValue("CHANNEL", info.channel.name, mono)
            LabeledValue("SIZE", info.asset.getFormattedSize(), mono)
            LabeledValue("PUBLISHED", formatPublishDate(info.release.publishedAt), mono)
        }
        val notes = info.release.body?.trim()
        if (!notes.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text("RELEASE NOTES", fontFamily = mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Spacer(Modifier.height(4.dp))
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .heightIn(max = 160.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                ) {
                    Text(
                        text = notes.take(2000),
                        fontFamily = mono,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String, mono: androidx.compose.ui.text.font.FontFamily, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified) {
    Column {
        Text(label, fontFamily = mono, fontSize = 9.sp, letterSpacing = 0.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
        Text(value, fontFamily = mono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (color == androidx.compose.ui.graphics.Color.Unspecified) MaterialTheme.colorScheme.onSurface else color)
    }
}

@Composable
private fun DownloadProgressView(
    info: UpdateReleaseInfo,
    progress: DownloadProgress?,
    mono: androidx.compose.ui.text.font.FontFamily,
    accents: app.morphe.gui.ui.theme.MorpheAccentColors,
) {
    Column {
        Text("Downloading v${info.latestVersion}…", fontFamily = mono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        val fraction = progress?.fraction
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = accents.primary,
            trackColor = accents.primary.copy(alpha = 0.12f),
            progress = { fraction ?: 0f },
        )
        Spacer(Modifier.height(8.dp))
        if (progress != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = buildString {
                        append(formatBytes(progress.bytesRead))
                        progress.totalBytes?.let { append(" / ${formatBytes(it)}") }
                        fraction?.let { append("  ·  ${(it * 100).toInt()}%") }
                    },
                    fontFamily = mono,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = buildString {
                        append(formatBytes(progress.bytesPerSecond) + "/s")
                        progress.etaSeconds?.let { append("  ·  ${formatEta(it)} left") }
                    },
                    fontFamily = mono,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun VerificationResult.describe(): String = when (this) {
    is VerificationResult.Success -> "OK"
    is VerificationResult.SizeMismatch -> "Downloaded file size ($actual bytes) didn't match the expected size ($expected bytes). The download may have been interrupted — try again."
    is VerificationResult.CorruptArchive -> "The downloaded file is corrupted ($reason). Try downloading again."
    is VerificationResult.ChecksumMismatch -> "Checksum did not match the published value. For safety, this update was not installed."
    is VerificationResult.Error -> message
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

private fun formatEta(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
}

private val publishDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

private fun formatPublishDate(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    return try {
        OffsetDateTime.parse(raw).format(publishDateFormatter)
    } catch (e: DateTimeParseException) {
        raw
    }
}
