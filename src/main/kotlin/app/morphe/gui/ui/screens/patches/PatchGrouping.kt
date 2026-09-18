/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.components.MorpheBadge
import app.morphe.gui.ui.components.MorpheBadgeTone
import app.morphe.gui.ui.components.MorpheChevron
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One collapsible block of a patch list.
 *
 * A null [title] is the ungrouped remainder: it carries no header and is always drawn.
 * [key] is what the fold state is stored under.
 */
@Immutable
data class PatchGroup<T>(
    val key: String,
    val title: String?,
    val items: List<T>,
    val icon: ImageVector = MorpheIcons.Category,
    val selectedCount: Int = 0,
    val defaultExpanded: Boolean = true
)

const val UNIVERSAL_GROUP_KEY = "universal"
const val UNGROUPED_GROUP_KEY = "ungrouped"

/**
 * Splits [patches] into the blocks a list is drawn as: uncategorized patches,
 * followed by declared category blocks (alphabetically), followed by universal patches.
 *
 * When [groupByCategory] is false, uncategorized patches have [PatchGroup.title] as null
 * (rendered flat without a header).
 */
fun <T> buildPatchGroups(
    patches: List<T>,
    groupByCategory: Boolean,
    universalTitle: String,
    ungroupedTitle: String,
    categoryOf: (T) -> String?,
    isUniversal: (T) -> Boolean = { false },
    isEnabled: (T) -> Boolean = { false }
): List<PatchGroup<T>> {
    val byCategory = patches.groupBy {
        if (groupByCategory) categoryOf(it)?.takeIf { cat -> cat.isNotBlank() } else null
    }
    val (universal, ungrouped) = byCategory[null].orEmpty().partition { isUniversal(it) }
    val hasCategories = byCategory.keys.any { it != null }

    return buildList {
        if (ungrouped.isNotEmpty()) {
            add(
                PatchGroup(
                    key = UNGROUPED_GROUP_KEY,
                    title = if (groupByCategory && hasCategories) ungroupedTitle else null,
                    items = ungrouped,
                    icon = MorpheIcons.Category,
                    selectedCount = ungrouped.count(isEnabled),
                    defaultExpanded = true
                )
            )
        }

        byCategory.keys.filterNotNull().sortedBy { it.lowercase() }.forEach { category ->
            val items = byCategory.getValue(category)
            add(
                PatchGroup(
                    key = "category:$category",
                    title = category,
                    items = items,
                    icon = MorpheIcons.Category,
                    selectedCount = items.count(isEnabled),
                    defaultExpanded = true
                )
            )
        }

        if (universal.isNotEmpty()) {
            add(
                PatchGroup(
                    key = UNIVERSAL_GROUP_KEY,
                    title = universalTitle,
                    items = universal,
                    icon = MorpheIcons.Public,
                    selectedCount = universal.count(isEnabled),
                    defaultExpanded = !groupByCategory
                )
            )
        }
    }
}

/**
 * The folds of a patch list, as an immutable snapshot.
 */
@Immutable
class PatchFolds(private val overrides: Map<String, Boolean> = emptyMap()) {
    fun isExpanded(sectionKey: Any, group: PatchGroup<*>): Boolean =
        overrides[foldKey(sectionKey, group.key)] ?: group.defaultExpanded

    fun with(sectionKey: Any, groupKey: String, expanded: Boolean): PatchFolds =
        PatchFolds(overrides + (foldKey(sectionKey, groupKey) to expanded))
}

private fun foldKey(sectionKey: Any, groupKey: String) = "$sectionKey:$groupKey"

/**
 * Tracks which blocks of a patch list the user has folded open or shut.
 */
@Stable
class PatchSectionState {
    var folds by mutableStateOf(PatchFolds())
        private set

    fun isExpanded(sectionKey: Any, group: PatchGroup<*>): Boolean =
        folds.isExpanded(sectionKey, group)

    fun toggle(sectionKey: Any, group: PatchGroup<*>) {
        setExpanded(sectionKey, group.key, !folds.isExpanded(sectionKey, group))
    }

    fun setExpanded(sectionKey: Any, groupKey: String, expanded: Boolean) {
        folds = folds.with(sectionKey, groupKey, expanded)
    }
}

@Composable
fun rememberPatchSectionState() = remember { PatchSectionState() }

/**
 * Collapsible header for a category group in the patch list.
 */
@Composable
fun PatchGroupHeader(
    title: String,
    count: Int,
    isExpanded: Boolean,
    onToggle: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: ImageVector = MorpheIcons.Category,
    selectedCount: Int = 0
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    val countLabel = pluralStringResource(Res.plurals.patch_selection_group_patch_count, count, count)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.small))
            .background(
                if (isHovered && onToggle != null) accents.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
            .border(
                1.dp,
                if (isHovered && onToggle != null) accents.primary.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(corners.small)
            )
            .then(
                if (onToggle != null) Modifier
                    .hoverable(hoverInteraction)
                    .handCursor()
                    .clickable(onClick = onToggle)
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "•",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Text(
            text = countLabel,
            fontSize = 11.sp,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.weight(1f))

        if (selectedCount > 0) {
            MorpheBadge(
                text = stringResource(Res.string.patch_selection_group_selected_count, selectedCount),
                tone = MorpheBadgeTone.Primary
            )
        }

        if (onToggle != null) {
            MorpheChevron(
                expanded = isExpanded,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 18.dp
            )
        }
    }
}
