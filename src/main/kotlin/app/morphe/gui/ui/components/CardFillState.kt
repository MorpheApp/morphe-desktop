/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.morphe.gui.data.model.MorpheFill

data class CardFillState(
    val fills: Map<String, MorpheFill> = emptyMap(),
    val globalFill: MorpheFill? = null,
    val onChange: (packageName: String, fill: MorpheFill?) -> Unit = { _, _ -> },
    val onGlobalChange: (MorpheFill?) -> Unit = {},
    val onClearAll: () -> Unit = {},
    val requestEdit: (packageName: String, appName: String, appIconColorHex: String?) -> Unit =
        { _, _, _ -> },
    val requestEditGlobal: () -> Unit = {},
) {
    operator fun get(packageName: String): MorpheFill? = fills[packageName] ?: globalFill
}

val LocalCardFills = compositionLocalOf { CardFillState() }

private data class EditTarget(
    val packageName: String?,
    val appName: String,
    val appIconColorHex: String?,
)

@Composable
fun CardFillHost(
    fills: Map<String, MorpheFill>,
    globalFill: MorpheFill?,
    onChange: (packageName: String, fill: MorpheFill?) -> Unit,
    onGlobalChange: (MorpheFill?) -> Unit,
    onClearAll: () -> Unit,
    content: @Composable () -> Unit,
) {
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    val state = CardFillState(
        fills = fills,
        globalFill = globalFill,
        onChange = onChange,
        onGlobalChange = onGlobalChange,
        onClearAll = onClearAll,
        requestEdit = { pkg, name, hex -> editing = EditTarget(pkg, name, hex) },
        requestEditGlobal = { editing = EditTarget(null, "All app cards", null) },
    )

    CompositionLocalProvider(LocalCardFills provides state) {
        content()
        editing?.let { target ->
            AppCardFillDialog(
                appName = target.appName,
                appIconColorHex = target.appIconColorHex,
                initialFill = target.packageName?.let { fills[it] } ?: globalFill,
                onDismiss = { editing = null },
                onSave = { fill ->
                    if (target.packageName == null) onGlobalChange(fill)
                    else onChange(target.packageName, fill)
                    editing = null
                },
            )
        }
    }
}
