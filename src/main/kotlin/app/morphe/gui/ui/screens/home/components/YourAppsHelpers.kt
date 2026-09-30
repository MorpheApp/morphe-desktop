/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.currentLocale
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * App-version advice for a patched app, or null if current. Returns (message,
 * recommended): recommended=true (amber) when the version is unsupported or a newer
 * stable is out. False (blue) for an optional experimental bump.
 */
@Composable
internal fun appAdvice(u: RecallUpdateInfo): Pair<String, Boolean>? {
    if (u.appUsedSupported) return null
    return stringResource(Res.string.home_advice_unsupported_by_patches, u.appUsedVersion.removePrefix("v")) to true
}

/** GitLab nests releases under `/-/`, GitHub does not. */
internal fun releaseUrl(sourceUrl: String, tag: String): String =
    if (sourceUrl.contains("gitlab", ignoreCase = true)) "$sourceUrl/-/releases/$tag"
    else "$sourceUrl/releases/tag/$tag"

/** Shared device-install line (mirrors the supported-row variant). */
@Composable
internal fun DeviceLine(info: DeviceAppInfo, font: FontFamily, mutedColor: Color, activeColor: Color) {
    val version = info.installedVersion?.removePrefix("v")
    val text = when {
        !info.installed -> stringResource(Res.string.home_app_row_not_on_device)
        info.signedByMorphe == false -> {
            if (version != null) stringResource(Res.string.home_app_row_on_device_with_version_not_signed, version)
            else stringResource(Res.string.home_app_row_on_device_not_signed)
        }
        else -> {
            if (version != null) stringResource(Res.string.home_app_row_on_device_with_version, version)
            else stringResource(Res.string.home_app_row_on_device)
        }
    }
    val color = when {
        !info.installed -> mutedColor
        info.signedByMorphe == false -> Color(0xFFE0504D)
        else -> activeColor
    }
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = font,
        color = color
    )
}

internal fun patchDisplayName(uniqueId: String): String =
    uniqueId.substringBeforeLast('|').substringBeforeLast('|')

@Composable
internal fun fullDate(millis: Long): String =
    FormatUtils.formatDateTime(millis, currentLocale())

/** "today / yesterday / 3d ago / MMM d" — compact for the list row. */
@Composable
internal fun relativeOrShortDate(millis: Long): String {
    val now = System.currentTimeMillis()
    val days = ((now - millis) / 86_400_000L).toInt()
    return when {
        days <= 0 -> stringResource(Res.string.home_date_today)
        days == 1 -> stringResource(Res.string.home_date_yesterday)
        days < 7 -> stringResource(Res.string.home_date_days_ago, days)
        else -> FormatUtils.formatShortDate(millis, currentLocale())
    }
}

@Composable
internal fun humanSize(bytes: Long): String {
    if (bytes <= 0) return "-"
    return FormatUtils.formatFileSize(bytes, currentLocale())
}
