/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.batch.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.engine.util.AdbDevice
import app.morphe.gui.batch.BatchInstallPolicy
import app.morphe.gui.batch.BatchPatchItem
import app.morphe.gui.ui.components.MorpheSwitch
import app.morphe.gui.ui.components.settings.SettingsSegmentCard
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BatchPolicyCard(
    policy: BatchInstallPolicy,
    runnableItems: List<BatchPatchItem>,
    selectedDevice: AdbDevice,
    onPolicyChange: (BatchInstallPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    val installAfter = policy == BatchInstallPolicy.INSTALL_AFTER
    val anyMount = runnableItems.any { it.useMount }
    val anyStandard = runnableItems.any { !it.useMount }

    val (title, description) = when {
        anyMount && anyStandard ->
            stringResource(Res.string.batch_patch_policy_mixed_title) to
                stringResource(Res.string.batch_patch_policy_mixed_desc)
        anyMount ->
            stringResource(Res.string.batch_patch_policy_mount_title) to
                stringResource(Res.string.batch_patch_policy_mount_desc)
        else ->
            stringResource(Res.string.batch_patch_policy_install_title) to
                stringResource(Res.string.batch_patch_policy_install_desc)
    }

    SettingsSegmentCard(
        modifier = modifier,
        onClick = {
            onPolicyChange(if (installAfter) BatchInstallPolicy.SAVE_ONLY else BatchInstallPolicy.INSTALL_AFTER)
        },
        role = Role.Switch,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = MorpheIcons.InstallMobile,
                    contentDescription = null,
                    tint = if (installAfter) MorpheTheme.colors.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MorpheTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = description,
                        style = MorpheTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MorpheSwitch(
                    checked = installAfter,
                    onCheckedChange = { checked ->
                        onPolicyChange(if (checked) BatchInstallPolicy.INSTALL_AFTER else BatchInstallPolicy.SAVE_ONLY)
                    },
                )
            }

            AnimatedVisibility(visible = installAfter) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = MorpheIcons.PhoneAndroid,
                        contentDescription = null,
                        tint = MorpheTheme.colors.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(Res.string.batch_patch_policy_target_device, selectedDevice.displayName),
                        style = MorpheTheme.typography.labelSmall,
                        color = MorpheTheme.colors.secondary,
                    )
                }
            }
        }
    }
}
