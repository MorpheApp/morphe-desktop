/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import app.morphe.gui.ui.icons.MorpheIcons

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheColors
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
    val font = LocalMorpheFont.current
    val icon = when (errorType) {
        ErrorType.NETWORK -> MorpheIcons.WifiOff
        ErrorType.FILE -> MorpheIcons.Error
        ErrorType.CLI -> MorpheIcons.Error
        ErrorType.GENERIC -> MorpheIcons.Warning
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = message,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            if (onRetry != null) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MorpheColors.Blue
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(retryText, fontFamily = font)
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MorpheColors.Blue
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(dismissText, fontFamily = font)
                }
            }
        },
        dismissButton = if (onRetry != null) {
            {
                TextButton(onClick = onDismiss) {
                    Text(dismissText, fontFamily = font)
                }
            }
        } else null
    )
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
