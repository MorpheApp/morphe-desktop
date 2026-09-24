/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.components.LabeledField
import app.morphe.gui.ui.components.SlimTextField
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun GitHubPatSection(
    gitHubPat: String,
    onGitHubPatChange: (String) -> Unit,
    font: FontFamily,
    accentColor: Color,
    enabled: Boolean = true,
    expanded: Boolean = false,
    icon: ImageVector? = null,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    val corners = LocalMorpheCorners.current
    val accents = LocalMorpheAccents.current
    val alpha = if (enabled) 1f else 0.4f

    var localPat by remember(gitHubPat) { mutableStateOf(gitHubPat) }
    var showPat by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CollapsibleSection(
            title = stringResource(Res.string.settings_section_github_pat),
            font = font,
            expanded = expanded,
            icon = icon,
            onExpandedChange = onExpandedChange
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val linkText = "github.com"
                val linkUrl = "https://github.com/settings/tokens/new?scopes=public_repo&description=morphe-desktop-github-integration"
                val rawDesc = stringResource(Res.string.settings_github_pat_desc, linkText)
                val linkIndex = rawDesc.indexOf(linkText)
                val annotatedString = buildAnnotatedString {
                    if (linkIndex != -1) {
                        append(rawDesc.substring(0, linkIndex))
                        withLink(
                            LinkAnnotation.Url(
                                linkUrl,
                                TextLinkStyles(
                                    style = SpanStyle(
                                        color = MaterialTheme.colorScheme.primary,
                                        textDecoration = TextDecoration.Underline
                                    )
                                )
                            )
                        ) {
                            append(linkText)
                        }
                        append(rawDesc.substring(linkIndex + linkText.length))
                    } else {
                        append(rawDesc)
                    }
                }

                Text(
                    text = annotatedString,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    fontFamily = font,
                    lineHeight = 14.sp
                )

                LabeledField(label = stringResource(Res.string.settings_github_pat_label), font = font) {
                    SlimTextField(
                        value = localPat,
                        onValueChange = {
                            localPat = it
                            onGitHubPatChange(it.trim())
                        },
                        placeholder = "ghp_xxxxxxxxxxxxxxx",
                        font = font,
                        accents = accents,
                        corners = corners,
                        enabled = enabled,
                        visualTransformation = if (showPat) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        trailing = {
                            IconButton(
                                onClick = { showPat = !showPat },
                                modifier = Modifier.size(24.dp).handCursor(enabled),
                            ) {
                                Icon(
                                    imageVector = if (showPat) MorpheIcons.VisibilityOff else MorpheIcons.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
