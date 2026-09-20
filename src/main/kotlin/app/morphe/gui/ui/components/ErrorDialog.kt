/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

enum class ErrorType {
    NETWORK,
    FILE,
    CLI,
    GENERIC
}

@Composable
fun ErrorDialog(
    title: String,
    message: String,
    errorType: ErrorType = ErrorType.GENERIC,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
    dismissText: String = stringResource(Res.string.ok),
    retryText: String = stringResource(Res.string.retry)
) {
    val icon = when (errorType) {
        ErrorType.NETWORK -> MorpheIcons.WifiOff
        ErrorType.FILE -> MorpheIcons.Error
        ErrorType.CLI -> MorpheIcons.Error
        ErrorType.GENERIC -> MorpheIcons.Warning
    }

    MorpheDialogCard(onDismiss = onDismiss, title = title) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(28.dp),
        )
        MorpheDialogText(message)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            MorpheDialogButton(
                label = dismissText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                filled = false,
                onClick = onDismiss,
            )
            if (onRetry != null) {
                MorpheDialogButton(
                    label = retryText,
                    color = MaterialTheme.colorScheme.error,
                    filled = true,
                    onClick = onRetry,
                )
            }
        }
    }
}

/**
 * Helper function to determine error type from exception or message.
 */
fun getErrorType(error: String): ErrorType {
    val lowerError = error.lowercase()
    return when {
        lowerError.contains("network") ||
        lowerError.contains("connect") ||
        lowerError.contains("timeout") ||
        lowerError.contains("unreachable") ||
        lowerError.contains("internet") -> ErrorType.NETWORK

        lowerError.contains("file") ||
        lowerError.contains("permission") ||
        lowerError.contains("access") ||
        lowerError.contains("read") ||
        lowerError.contains("write") -> ErrorType.FILE

        lowerError.contains("cli") ||
        lowerError.contains("patch") ||
        lowerError.contains("exit code") -> ErrorType.CLI

        else -> ErrorType.GENERIC
    }
}

/**
 * Get user-friendly error message.
 */
@Composable
fun getFriendlyErrorMessage(error: String): String {
    val lowerError = error.lowercase()
    return when {
        lowerError.contains("timeout") ->
            stringResource(Res.string.error_dialog_timeout)

        lowerError.contains("unreachable") || lowerError.contains("connect") ->
            stringResource(Res.string.error_dialog_unreachable)

        lowerError.contains("permission") || lowerError.contains("access denied") ->
            stringResource(Res.string.error_dialog_permission_denied)

        lowerError.contains("not found") ->
            stringResource(Res.string.error_dialog_not_found)

        lowerError.contains("disk full") || lowerError.contains("no space") ->
            stringResource(Res.string.error_dialog_disk_full)

        lowerError.contains("exit code") ->
            stringResource(Res.string.error_dialog_exit_code)

        else -> error
    }
}
