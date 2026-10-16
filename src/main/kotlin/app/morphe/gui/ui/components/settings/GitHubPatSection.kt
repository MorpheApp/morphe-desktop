/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.LabeledField
import app.morphe.gui.ui.components.MorpheIconButton
import app.morphe.gui.ui.components.MorpheIconButtonSize
import app.morphe.gui.ui.components.MorpheIconButtonVariant
import app.morphe.gui.ui.components.SlimTextField
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun GitHubPatSection(
    gitHubPat: String,
    onGitHubPatChange: (String) -> Unit,
    enabled: Boolean = true,
    expanded: Boolean = false,
    icon: ImageVector? = null,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    val alpha = if (enabled) 1f else 0.4f

    var localPat by remember(gitHubPat) { mutableStateOf(gitHubPat) }
    var showPat by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CollapsibleSection(
            title = stringResource(Res.string.settings_section_github_pat),
            expanded = expanded,
            icon = icon,
            onExpandedChange = onExpandedChange
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val linkText = "github.com"
                val linkUrl = "https://github.com/settings/tokens/new?scopes=public_repo&description=morphe-desktop-github-integration"
                val rawDesc = stringResource(Res.string.settings_github_pat_desc, linkText)
                val annotatedString = buildLinkedText(
                    template = rawDesc,
                    linkText = linkText,
                    url = linkUrl,
                )

                Text(
                    text = annotatedString,
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )

                LabeledField(label = stringResource(Res.string.settings_github_pat_label)) {
                    SlimTextField(
                        value = localPat,
                        onValueChange = {
                            localPat = it
                            onGitHubPatChange(it.trim())
                        },
                        placeholder = "ghp_xxxxxxxxxxxxxxx",
                        enabled = enabled,
                        visualTransformation = if (showPat) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        trailing = {
                            MorpheIconButton(
                                icon = if (showPat) MorpheIcons.VisibilityOff else MorpheIcons.Visibility,
                                contentDescription = null,
                                size = MorpheIconButtonSize.COMPACT,
                                variant = MorpheIconButtonVariant.GHOST,
                                enabled = enabled,
                                onClick = { showPat = !showPat },
                            )
                        }
                    )
                }
            }
        }
    }
}
