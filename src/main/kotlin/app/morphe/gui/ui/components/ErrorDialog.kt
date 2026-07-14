/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.theme.MorpheColors

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
    dismissText: String = "OK",
    retryText: String = "Retry"
) {
    val icon = when (errorType) {
        ErrorType.NETWORK -> Icons.Default.WifiOff
        ErrorType.FILE -> Icons.Default.Error
        ErrorType.CLI -> Icons.Default.Error
        ErrorType.GENERIC -> Icons.Default.Warning
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
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = message,
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
                    Text(retryText)
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MorpheColors.Blue
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(dismissText)
                }
            }
        },
        dismissButton = if (onRetry != null) {
            {
                TextButton(onClick = onDismiss) {
                    Text(dismissText)
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
fun getFriendlyErrorMessage(error: String): String {
    val lowerError = error.lowercase()
    return when {
        lowerError.contains("timeout") ->
            "The connection timed out. Please check your internet connection and try again."

        lowerError.contains("unreachable") || lowerError.contains("connect") ->
            "Unable to connect to the server. Please check your internet connection."

        lowerError.contains("permission") || lowerError.contains("access denied") ->
            "Permission denied. Please check that you have access to the file or folder."

        lowerError.contains("not found") ->
            "The requested file or resource was not found."

        lowerError.contains("disk full") || lowerError.contains("no space") ->
            "Not enough disk space. Please free up some space and try again."

        lowerError.contains("exit code") ->
            "The patching process encountered an error. Check the logs for details."

        else -> error
    }
}
