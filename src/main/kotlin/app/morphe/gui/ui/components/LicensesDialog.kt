/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.morphe.engine.util.Logger
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheMotion
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.entity.License
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LicensesDialog(onDismiss: () -> Unit) {
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)

    val libs = remember {
        try {
            val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("aboutlibraries.json")
            val json = stream?.bufferedReader()?.use { it.readText() }
            if (json != null) Libs.Builder().withJson(json).build() else null
        } catch (e: Throwable) {
            // Catch LinkageError and other non-Exception throwables to avoid crashing the screen.
            Logger.error("Failed to load licenses", e)
            null
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var viewingLicense by remember { mutableStateOf<License?>(null) }
    var showNotice by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val filtered = remember(libs, searchQuery) {
        val all = libs?.libraries.orEmpty()
        if (searchQuery.isBlank()) all
        else {
            val q = searchQuery.trim().lowercase()
            all.filter { lib ->
                lib.name.lowercase().contains(q) ||
                    lib.uniqueId.lowercase().contains(q) ||
                    (lib.description?.lowercase()?.contains(q) == true) ||
                    lib.licenses.any { it.name.lowercase().contains(q) || (it.spdxId?.lowercase()?.contains(q) == true) }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        MorpheDialogSurface(
            modifier = Modifier
                .widthIn(min = 640.dp, max = 960.dp)
                .heightIn(min = 520.dp, max = 780.dp)
                .fillMaxWidth(0.88f)
                .fillMaxHeight(0.88f),
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.licenses_dialog_title),
                            style = MorpheTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "[${libs?.libraries?.size ?: 0}]",
                            style = MorpheTheme.typography.labelSmall,
                            color = MorpheTheme.colors.primary
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // GPLv3 §7b requires providing the warranty notice.
                        NoticeButton(onClick = { showNotice = true })

                        MorpheIconButton(
                            icon = MorpheIcons.Close,
                            contentDescription = stringResource(Res.string.close),
                            size = MorpheIconButtonSize.COMPACT,
                            variant = MorpheIconButtonVariant.GHOST,
                            onClick = onDismiss,
                        )
                    }
                }

                HorizontalDivider(color = dividerColor)

                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp)) {
                    SlimSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = stringResource(Res.string.licenses_search_placeholder),
                    )
                }

                HorizontalDivider(color = dividerColor)

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        libs == null -> EmptyHint(text = stringResource(Res.string.licenses_dialog_empty_failed), isError = true)
                        filtered.isEmpty() -> EmptyHint(text = stringResource(Res.string.licenses_dialog_empty_no_matches), isError = false)
                        else -> {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp)
                            ) {
                                items(items = filtered, key = { it.uniqueId }) { library ->
                                    LibraryRow(
                                        library = library,
                                        dividerColor = dividerColor,
                                        onLicenseClick = { viewingLicense = it }
                                    )
                                }
                            }

                            VerticalScrollbar(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .padding(vertical = 6.dp),
                                adapter = rememberScrollbarAdapter(listState),
                                style = MorpheScrollbarStyle()
                            )
                        }
                    }
                }

                HorizontalDivider(color = dividerColor)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) pluralStringResource(Res.plurals.licenses_dialog_footer_count, filtered.size, filtered.size)
                               else stringResource(Res.string.licenses_dialog_footer_matched, filtered.size, libs?.libraries?.size ?: 0),
                        style = MorpheTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MorpheButton(
                        label = stringResource(Res.string.close),
                        onClick = onDismiss,
                        variant = MorpheButtonVariant.OUTLINED,
                        size = MorpheButtonSize.COMPACT,
                    )
                }
            }
        }
    }

    viewingLicense?.let { license ->
        LicenseTextDialog(license = license, onDismiss = { viewingLicense = null })
    }

    if (showNotice) {
        NoticeTextDialog(onDismiss = { showNotice = false })
    }
}

@Composable
private fun LibraryRow(
    library: Library,
    dividerColor: Color,
    onLicenseClick: (License) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MorpheMotion.mediumTween()
    )
    val bgAlpha by animateFloatAsState(
        targetValue = when {
            expanded -> 0.05f
            isHovered -> MorpheTheme.alphas.hover
            else -> 0f
        },
        animationSpec = MorpheMotion.mediumTween()
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MorpheTheme.shapes.small)
            .hoverable(hoverInteraction)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = bgAlpha))
            .clickable { expanded = !expanded }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = library.name,
                        style = MorpheTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    library.artifactVersion?.takeIf { it.isNotBlank() }?.let { v ->
                        Text(
                            text = "v$v",
                            style = MorpheTheme.typography.labelSmall,
                            color = MorpheTheme.colors.secondary
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = library.uniqueId,
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (library.licenses.isEmpty()) {
                    LicenseChip(
                        label = stringResource(Res.string.unknown),
                        accentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        onClick = null
                    )
                } else {
                    library.licenses.forEach { license ->
                        LicenseChip(
                            label = licenseDisplayLabel(license),
                            accentColor = MorpheTheme.colors.primary,
                            onClick = { onLicenseClick(license) }
                        )
                    }
                }
            }

            Icon(
                imageVector = MorpheIcons.ExpandMore,
                contentDescription = if (expanded) stringResource(Res.string.collapse) else stringResource(Res.string.expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isHovered) 0.7f else 0.4f),
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = rotation }
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top, animationSpec = MorpheMotion.mediumTween()) +
                fadeIn(animationSpec = MorpheMotion.mediumTween()),
            exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = MorpheMotion.mediumTween()) +
                fadeOut(animationSpec = MorpheMotion.fastTween())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 14.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                library.description?.trim()?.takeIf { it.isNotEmpty() }?.let { desc ->
                    Text(
                        text = desc,
                        style = MorpheTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val devs = library.developers.mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }
                val org = library.organization?.name?.takeIf { it.isNotBlank() }
                if (devs.isNotEmpty() || org != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (devs.isNotEmpty()) {
                            MetaLine(label = stringResource(Res.string.licenses_row_authors_label), value = devs.joinToString(", "))
                        }
                        org?.let { MetaLine(label = stringResource(Res.string.licenses_row_org_label), value = it) }
                    }
                }

                val website = library.website?.takeIf { it.isNotBlank() }
                val source = library.scm?.url?.takeIf { it.isNotBlank() }
                if (website != null || source != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        website?.let {
                            LinkPill(label = stringResource(Res.string.app_info_dialog_link_website), url = it)
                        }
                        source?.let {
                            LinkPill(label = stringResource(Res.string.licenses_row_link_source), url = it)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = dividerColor)
    }
}

@Composable
private fun MetaLine(
    label: String,
    value: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MorpheTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MorpheTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LicenseChip(
    label: String,
    accentColor: Color,
    onClick: (() -> Unit)?,
) {
    MorpheButton(
        label = label,
        onClick = onClick ?: {},
        enabled = onClick != null,
        variant = MorpheButtonVariant.OUTLINED,
        size = MorpheButtonSize.COMPACT,
        customColor = accentColor,
    )
}

@Composable
private fun LinkPill(
    label: String,
    url: String,
) {
    val uriHandler = LocalUriHandler.current
    MorpheButton(
        label = label,
        icon = MorpheIcons.OpenInNew,
        variant = MorpheButtonVariant.OUTLINED,
        size = MorpheButtonSize.COMPACT,
        onClick = { uriHandler.openUri(url) }
    )
}

@Composable
private fun EmptyHint(text: String, isError: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MorpheTheme.typography.labelSmall,
            color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

/** Morphe project warranty notice text required by GPLv3 §7b. */
private val MORPHE_NOTICE = """
    Morphe NOTICE

    https://github.com/MorpheApp/morphe-desktop

    =============

    GPLv3 Section 7b: Preservation of User Notices

    All derivative works (source code and binaries) must include this
    NOTICE text and provide it to the software user.

    Portions of this software are provided "AS IS" by the Morphe software project.
    Any express or implied warranties, including the implied warranties of
    merchantability and fitness for a particular purpose, are disclaimed.


    GPLv3 Section 7c: Prohibiting Misrepresentation of Origin

    You are prohibited from misrepresenting the origin of the Program,
    and modified versions of the Program must be identified and marked in
    reasonable ways as different from the original version so as not to cause
    confusion regarding their origin.


    GPLv3 Section 7e: Declining Grant of Trademark Rights

    This License does not grant any rights or permission under trademark law
    to use the name "Morphe" or any of the Program's trade names, trademarks,
    service marks, or logos.
""".trimIndent()

/** Header button that opens the [NoticeTextDialog]. */
@Composable
private fun NoticeButton(
    onClick: () -> Unit,
) {
    MorpheButton(
        label = stringResource(Res.string.licenses_notice_button),
        onClick = onClick,
        variant = MorpheButtonVariant.PRIMARY,
        size = MorpheButtonSize.COMPACT,
    )
}

/** Modal dialog displaying the project [MORPHE_NOTICE]. */
@Composable
private fun NoticeTextDialog(onDismiss: () -> Unit) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = MorpheTheme.alphas.borderMuted)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        MorpheDialogSurface(
            modifier = Modifier
                .widthIn(min = 540.dp, max = 820.dp)
                .heightIn(min = 380.dp, max = 680.dp)
                .fillMaxWidth(0.78f)
                .fillMaxHeight(0.82f),
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(Res.string.licenses_notice_title),
                            style = MorpheTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(Res.string.licenses_notice_subtitle),
                            style = MorpheTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    MorpheIconButton(
                        icon = MorpheIcons.Close,
                        contentDescription = stringResource(Res.string.close),
                        size = MorpheIconButtonSize.COMPACT,
                        variant = MorpheIconButtonVariant.GHOST,
                        onClick = onDismiss,
                    )
                }

                HorizontalDivider(color = borderColor)

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val scrollState = rememberScrollState()
                    Text(
                        text = MORPHE_NOTICE,
                        style = MorpheTheme.typography.codeSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 22.dp, vertical = 16.dp)
                    )
                    VerticalScrollbar(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = 6.dp),
                        adapter = rememberScrollbarAdapter(scrollState),
                        style = MorpheScrollbarStyle()
                    )
                }
            }
        }
    }
}

@Composable
private fun LicenseTextDialog(license: License, onDismiss: () -> Unit) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = MorpheTheme.alphas.borderMuted)
    val content = license.licenseContent?.takeIf { it.isNotBlank() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        MorpheDialogSurface(
            modifier = Modifier
                .widthIn(min = 540.dp, max = 820.dp)
                .heightIn(min = 380.dp, max = 680.dp)
                .fillMaxWidth(0.78f)
                .fillMaxHeight(0.82f),
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val chipLabel = licenseDisplayLabel(license)
                        Text(
                            text = chipLabel,
                            style = MorpheTheme.typography.titleMedium,
                            color = MorpheTheme.colors.primary
                        )
                        if (license.name.isNotBlank() && !license.name.equals(chipLabel, ignoreCase = true)) {
                            Text(
                                text = license.name,
                                style = MorpheTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    MorpheIconButton(
                        icon = MorpheIcons.Close,
                        contentDescription = stringResource(Res.string.close),
                        size = MorpheIconButtonSize.COMPACT,
                        variant = MorpheIconButtonVariant.GHOST,
                        onClick = onDismiss,
                    )
                }

                HorizontalDivider(color = borderColor)

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (content != null) {
                        val scrollState = rememberScrollState()
                        Text(
                            text = content,
                            style = MorpheTheme.typography.codeSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(horizontal = 22.dp, vertical = 16.dp)
                        )
                        VerticalScrollbar(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(vertical = 6.dp),
                            adapter = rememberScrollbarAdapter(scrollState),
                            style = MorpheScrollbarStyle()
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(Res.string.licenses_text_not_bundled),
                                style = MorpheTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            license.url?.takeIf { it.isNotBlank() }?.let { url ->
                                Text(
                                    text = stringResource(Res.string.licenses_text_open_canonical),
                                    style = MorpheTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LinkPill(
                                    label = stringResource(Res.string.licenses_text_open_license_button),
                                    url = url,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = borderColor)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    MorpheButton(
                        label = stringResource(Res.string.close),
                        onClick = onDismiss,
                        variant = MorpheButtonVariant.OUTLINED,
                        size = MorpheButtonSize.COMPACT,
                    )
                }
            }
        }
    }
}

private val MD5_HASH_REGEX = Regex("^[0-9a-f]{32}$")

private fun licenseDisplayLabel(license: License): String {
    license.spdxId?.takeIf { it.isNotBlank() }?.let { return it }
    val hash = license.hash
    if (hash.isNotBlank() && !MD5_HASH_REGEX.matches(hash)) return hash
    return license.name.ifBlank { "-" }
}
