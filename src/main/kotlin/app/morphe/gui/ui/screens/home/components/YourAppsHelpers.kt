/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.currentLocale
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Returns version guidance for a patched app, or null if currently supported. */
@Composable
internal fun appAdvice(u: RecallUpdateInfo): Pair<String, Boolean>? {
    if (u.appUsedSupported) return null
    return stringResource(Res.string.home_advice_unsupported_by_patches, u.appUsedVersion.removePrefix("v")) to true
}

/** Formats the release URL based on host platform conventions. */
internal fun releaseUrl(sourceUrl: String, tag: String): String =
    if (sourceUrl.contains("gitlab", ignoreCase = true)) "$sourceUrl/-/releases/$tag"
    else "$sourceUrl/releases/tag/$tag"

/** Displays device installation status and signing verification. */
@Composable
internal fun DeviceLine(
    info: DeviceAppInfo,
    mutedColor: Color,
    activeColor: Color,
    modifier: Modifier = Modifier,
) {
    val version = info.installedVersion?.removePrefix("v")
    val text = when {
        !info.installed -> stringResource(Res.string.home_app_row_not_on_device)
        info.signedByMorphe == false -> {
            version?.let { stringResource(Res.string.home_app_row_on_device_with_version_not_signed, it) }
                ?: stringResource(Res.string.home_app_row_on_device_not_signed)
        }
        else -> {
            version?.let { stringResource(Res.string.home_app_row_on_device_with_version, it) }
                ?: stringResource(Res.string.home_app_row_on_device)
        }
    }
    val color = when {
        !info.installed -> mutedColor
        info.signedByMorphe == false -> MorpheTheme.colors.error
        else -> activeColor
    }
    Text(
        text = text,
        style = MorpheTheme.typography.bodySmall,
        color = color,
        modifier = modifier,
    )
}

internal fun patchDisplayName(uniqueId: String): String =
    uniqueId.substringBeforeLast('|').substringBeforeLast('|')

@Composable
internal fun fullDate(millis: Long): String =
    FormatUtils.formatDateTime(millis, currentLocale())

/** Formats a timestamp into a compact relative or short date string. */
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
