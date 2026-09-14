/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheAccentColors
import app.morphe.gui.ui.theme.MorpheCornerStyle
import app.morphe.gui.util.Logger
import app.morphe.morphe_desktop.generated.resources.*
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.entity.License
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LicensesDialog(onDismiss: () -> Unit) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)

    val libs = remember {
        try {
            val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("aboutlibraries.json")
            val json = stream?.bufferedReader()?.use { it.readText() }
            if (json != null) Libs.Builder().withJson(json).build() else null
        } catch (e: Throwable) {
            // Throwable, not Exception: a JVM/dep version mismatch surfaces as an
            // UnsupportedClassVersionError (a LinkageError), which isn't an Exception —
            // catch it so the viewer degrades to empty instead of taking down the screen.
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
        Surface(
            modifier = Modifier
                .widthIn(min = 640.dp, max = 960.dp)
                .heightIn(min = 520.dp, max = 780.dp)
                .fillMaxWidth(0.88f)
                .fillMaxHeight(0.88f),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(corners.medium),
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Header ──
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
                            fontFamily = font,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "[${libs?.libraries?.size ?: 0}]",
                            fontFamily = font,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = accents.primary
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // GPLv3 §7b requires the warranty NOTICE be provided to the user.
                        NoticeButton(
                            onClick = { showNotice = true },
                            font = font,
                            accentColor = accents.primary,
                            corners = corners
                        )

                        val closeHover = remember { MutableInteractionSource() }
                        val isCloseHovered by closeHover.collectIsHoveredAsState()
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(corners.small))
                                .hoverable(closeHover)
                                .background(
                                    if (isCloseHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                    else Color.Transparent
                                )
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = MorpheIcons.Close,
                                contentDescription = stringResource(Res.string.close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = if (isCloseHovered) 0.85f else 0.55f
                                ),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = dividerColor)

                // ── Search bar ──
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp)) {
                    LicenseSearchBar(query = searchQuery, onQueryChange = { searchQuery = it })
                }

                HorizontalDivider(color = dividerColor)

                // ── List ──
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        libs == null -> EmptyHint(text = stringResource(Res.string.licenses_dialog_empty_failed), font = font, isError = true)
                        filtered.isEmpty() -> EmptyHint(text = stringResource(Res.string.licenses_dialog_empty_no_matches), font = font, isError = false)
                        else -> {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp)
                            ) {
                                items(items = filtered, key = { it.uniqueId }) { library ->
                                    LibraryRow(
                                        library = library,
                                        font = font,
                                        accents = accents,
                                        corners = corners,
                                        borderColor = borderColor,
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
                                style = morpheScrollbarStyle()
                            )
                        }
                    }
                }

                HorizontalDivider(color = dividerColor)

                // ── Footer ──
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
                        fontFamily = font,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(corners.small),
                        border = BorderStroke(1.dp, borderColor)
                    ) {
                        Text(
                            stringResource(Res.string.close),
                            fontFamily = font,
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
private fun LicenseSearchBar(query: String, onQueryChange: (String) -> Unit) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val searchFocused = remember { mutableStateOf(false) }
    val searchBorderColor by animateColorAsState(
        if (searchFocused.value) accents.primary.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
        animationSpec = tween(150)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, searchBorderColor, RoundedCornerShape(corners.small))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = MorpheIcons.Search,
            contentDescription = stringResource(Res.string.search),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp)
        )

        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(Res.string.licenses_search_placeholder),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 12.sp,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(accents.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { searchFocused.value = it.isFocused }
            )
        }

        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(corners.small))
                    .clickable { onQueryChange("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MorpheIcons.Clear,
                    contentDescription = stringResource(Res.string.clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun LibraryRow(
    library: Library,
    font: FontFamily,
    accents: MorpheAccentColors,
    corners: MorpheCornerStyle,
    borderColor: Color,
    dividerColor: Color,
    onLicenseClick: (License) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(180)
    )
    val bgAlpha by animateFloatAsState(
        targetValue = when {
            expanded -> 0.05f
            isHovered -> 0.03f
            else -> 0f
        },
        animationSpec = tween(180)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.small))
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
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    library.artifactVersion?.takeIf { it.isNotBlank() }?.let { v ->
                        Text(
                            text = "v$v",
                            fontSize = 11.sp,
                            fontFamily = font,
                            fontWeight = FontWeight.Medium,
                            color = accents.secondary
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = library.uniqueId,
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
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
                        font = font,
                        corners = corners,
                        accentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        onClick = null
                    )
                } else {
                    library.licenses.forEach { license ->
                        LicenseChip(
                            label = licenseDisplayLabel(license),
                            font = font,
                            corners = corners,
                            accentColor = accents.primary,
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
            enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(200)) +
                fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(180)) +
                fadeOut(animationSpec = tween(140))
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
                        fontSize = 11.sp,
                        fontFamily = font,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )
                }

                val devs = library.developers.mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }
                val org = library.organization?.name?.takeIf { it.isNotBlank() }
                if (devs.isNotEmpty() || org != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (devs.isNotEmpty()) {
                            MetaLine(label = stringResource(Res.string.licenses_row_authors_label), value = devs.joinToString(", "), font = font)
                        }
                        org?.let { MetaLine(label = stringResource(Res.string.licenses_row_org_label), value = it, font = font) }
                    }
                }

                val website = library.website?.takeIf { it.isNotBlank() }
                val source = library.scm?.url?.takeIf { it.isNotBlank() }
                if (website != null || source != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        website?.let {
                            LinkPill(label = stringResource(Res.string.app_info_dialog_link_website), url = it, font = font, corners = corners, borderColor = borderColor)
                        }
                        source?.let {
                            LinkPill(label = stringResource(Res.string.licenses_row_link_source), url = it, font = font, corners = corners, borderColor = borderColor)
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
    font: FontFamily,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LicenseChip(
    label: String,
    font: FontFamily,
    corners: MorpheCornerStyle,
    accentColor: Color,
    onClick: (() -> Unit)?,
) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val bg by animateColorAsState(
        if (isHovered && onClick != null) accentColor.copy(alpha = 0.18f)
        else accentColor.copy(alpha = 0.08f),
        animationSpec = tween(140)
    )
    Box(
        modifier = Modifier
            .hoverable(hover)
            .clip(RoundedCornerShape(corners.small))
            .background(bg, RoundedCornerShape(corners.small))
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(corners.small))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = font,
            fontWeight = FontWeight.Normal,
            color = accentColor,
            maxLines = 1
        )
    }
}

@Composable
private fun LinkPill(
    label: String,
    url: String,
    font: FontFamily,
    corners: MorpheCornerStyle,
    borderColor: Color,
) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier
            .hoverable(hover)
            .clip(RoundedCornerShape(corners.small))
            .border(
                1.dp,
                if (isHovered) borderColor.copy(alpha = 0.4f) else borderColor,
                RoundedCornerShape(corners.small)
            )
            .clickable { uriHandler.openUri(url) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = font,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            imageVector = MorpheIcons.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isHovered) 0.75f else 0.45f),
            modifier = Modifier.size(10.dp)
        )
    }
}

@Composable
private fun EmptyHint(text: String, font: FontFamily, isError: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            fontFamily = font,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

/**
 * Morphe's project NOTICE. Hardcoded on purpose since it never changes. GPLv3 §7b requires this
 * warranty statement reach the user. The NOTICE button in the header shows it.
 */
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

/** Header pill that opens the [NoticeTextDialog]. */
@Composable
private fun NoticeButton(
    onClick: () -> Unit,
    font: FontFamily,
    accentColor: Color,
    corners: MorpheCornerStyle,
) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val bg by animateColorAsState(
        if (isHovered) accentColor.copy(alpha = 0.18f) else accentColor.copy(alpha = 0.08f),
        animationSpec = tween(140)
    )
    Box(
        modifier = Modifier
            .hoverable(hover)
            .clip(RoundedCornerShape(corners.small))
            .background(bg, RoundedCornerShape(corners.small))
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(corners.small))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = stringResource(Res.string.licenses_notice_button),
            fontSize = 11.sp,
            fontFamily = font,
            fontWeight = FontWeight.Normal,
            color = accentColor,
            maxLines = 1
        )
    }
}

/** Scrollable viewer for the project [MORPHE_NOTICE] — mirrors [LicenseTextDialog]. */
@Composable
private fun NoticeTextDialog(onDismiss: () -> Unit) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 540.dp, max = 820.dp)
                .heightIn(min = 380.dp, max = 680.dp)
                .fillMaxWidth(0.78f)
                .fillMaxHeight(0.82f),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(corners.medium),
            border = BorderStroke(1.dp, borderColor)
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
                            fontFamily = font,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                        Text(
                            text = stringResource(Res.string.licenses_notice_subtitle),
                            fontFamily = font,
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(corners.small))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = MorpheIcons.Close,
                            contentDescription = stringResource(Res.string.close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                HorizontalDivider(color = borderColor)

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val scrollState = rememberScrollState()
                    Text(
                        text = MORPHE_NOTICE,
                        fontSize = 11.sp,
                        fontFamily = font,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 16.sp,
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
                        style = morpheScrollbarStyle()
                    )
                }
            }
        }
    }
}

@Composable
private fun LicenseTextDialog(license: License, onDismiss: () -> Unit) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    val content = license.licenseContent?.takeIf { it.isNotBlank() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 540.dp, max = 820.dp)
                .heightIn(min = 380.dp, max = 680.dp)
                .fillMaxWidth(0.78f)
                .fillMaxHeight(0.82f),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(corners.medium),
            border = BorderStroke(1.dp, borderColor)
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
                            fontFamily = font,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = accents.primary
                        )
                        if (license.name.isNotBlank() && !license.name.equals(chipLabel, ignoreCase = true)) {
                            Text(
                                text = license.name,
                                fontFamily = font,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(corners.small))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = MorpheIcons.Close,
                            contentDescription = stringResource(Res.string.close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                HorizontalDivider(color = borderColor)

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (content != null) {
                        val scrollState = rememberScrollState()
                        Text(
                            text = content,
                            fontSize = 11.sp,
                            fontFamily = font,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 16.sp,
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
                            style = morpheScrollbarStyle()
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(Res.string.licenses_text_not_bundled),
                                fontFamily = font,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            license.url?.takeIf { it.isNotBlank() }?.let { url ->
                                Text(
                                    text = stringResource(Res.string.licenses_text_open_canonical),
                                    fontFamily = font,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LinkPill(
                                    label = stringResource(Res.string.licenses_text_open_license_button),
                                    url = url,
                                    font = font,
                                    corners = corners,
                                    borderColor = borderColor
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
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(corners.small),
                        border = BorderStroke(1.dp, borderColor)
                    ) {
                        Text(
                            stringResource(Res.string.close),
                            fontFamily = font,
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
    return license.name.ifBlank { "—" }
}
