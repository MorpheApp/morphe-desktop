/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.data.model.ImageSize
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheDimens
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.morphe_desktop.generated.resources.*
import javax.imageio.ImageIO
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

enum class PathPickerMode {
    File,
    Folder,
    Image,
}

/**
 * Reusable path input component for files, folders, and images in Morphe Desktop.
 * Provides a text field for manually entering/viewing paths, a clear button,
 * and a browse button that invokes native OS pickers via [MorpheFilePicker].
 */
@Composable
fun MorphePathInput(
    value: String,
    onValueChange: (String) -> Unit,
    mode: PathPickerMode,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    allowedExtensions: List<String>? = null,
    recommendedSize: ImageSize? = null,
    isInvalid: Boolean = false,
    enabled: Boolean = true,
) {
    val accents = LocalMorpheAccents.current
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val dimens = LocalMorpheDimens.current
    val scope = rememberCoroutineScope()

    var localPath by remember(value) { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (localPath != value) localPath = value
    }

    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        when {
            isInvalid -> MaterialTheme.colorScheme.error
            isFocused -> accents.primary.copy(alpha = 0.6f)
            else -> accents.primary.copy(alpha = 0.2f)
        },
        animationSpec = tween(150),
        label = "pathInputBorder",
    )

    // Optional image preview when mode is Image and file exists
    var thumbnailBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(localPath, mode) {
        thumbnailBitmap = if (mode == PathPickerMode.Image && localPath.isNotBlank()) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(localPath)
                    if (file.isFile && file.length() in 1..(20 * 1024 * 1024)) {
                        ImageIO.read(file)?.toComposeImageBitmap()
                    } else null
                }.getOrNull()
            }
        } else {
            null
        }
    }

    val defaultPlaceholder = when (mode) {
        PathPickerMode.Image -> stringResource(Res.string.select_image)
        PathPickerMode.Folder -> stringResource(Res.string.select_folder)
        PathPickerMode.File -> stringResource(Res.string.select_file)
    }

    val effectiveExtensions = remember(allowedExtensions, mode) {
        when {
            !allowedExtensions.isNullOrEmpty() -> allowedExtensions
            mode == PathPickerMode.Image -> listOf("png", "jpg", "jpeg", "webp")
            else -> emptyList()
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.controlHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Text field container with optional thumbnail and clear button
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(corners.small))
                    .border(1.dp, borderColor, RoundedCornerShape(corners.small))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap!!,
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(corners.small))
                            .border(1.dp, accents.primary.copy(alpha = 0.3f), RoundedCornerShape(corners.small)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    val icon = when (mode) {
                        PathPickerMode.Image -> MorpheIcons.Wallpaper
                        PathPickerMode.Folder -> MorpheIcons.FolderOpen
                        PathPickerMode.File -> MorpheIcons.Description
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accents.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp),
                    )
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (localPath.isEmpty()) {
                        Text(
                            text = placeholder ?: defaultPlaceholder,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                    BasicTextField(
                        value = localPath,
                        onValueChange = {
                            localPath = it
                            onValueChange(it)
                        },
                        singleLine = true,
                        enabled = enabled,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(accents.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isFocused = it.isFocused },
                    )
                }

                if (localPath.isNotBlank() && enabled) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(corners.small))
                            .handCursor()
                            .clickable {
                                localPath = ""
                                onValueChange("")
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MorpheIcons.Close,
                            contentDescription = stringResource(Res.string.clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }

            // Browse button
            val browseHover = remember { MutableInteractionSource() }
            val isBrowseHovered by browseHover.collectIsHoveredAsState()
            val browseBorder by animateColorAsState(
                if (isBrowseHovered) accents.primary.copy(alpha = 0.5f)
                else accents.primary.copy(alpha = 0.2f),
                animationSpec = tween(150),
                label = "browseBorder",
            )

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .hoverable(browseHover)
                    .clip(RoundedCornerShape(corners.small))
                    .border(1.dp, browseBorder, RoundedCornerShape(corners.small))
                    .handCursor()
                    .clickable(enabled = enabled) {
                        scope.launch {
                            val pickedPath = when (mode) {
                                PathPickerMode.Folder -> {
                                    val startDir = File(localPath).takeIf { it.isDirectory }
                                    MorpheFilePicker.pickDirectory(startDir = startDir)?.absolutePath
                                }
                                PathPickerMode.File, PathPickerMode.Image -> {
                                    val startDir = File(localPath).parentFile?.takeIf { it.isDirectory }
                                    MorpheFilePicker.pickFile(
                                        startDir = startDir,
                                        extensions = effectiveExtensions,
                                    )?.absolutePath
                                }
                            }
                            if (pickedPath != null) {
                                localPath = pickedPath
                                onValueChange(pickedPath)
                            }
                        }
                    }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.browse),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = if (isBrowseHovered) accents.primary else accents.primary.copy(alpha = 0.7f),
                )
            }
        }

        // Recommended size hint if present
        if (recommendedSize != null) {
            Text(
                text = stringResource(Res.string.patch_option_recommended_size, recommendedSize.width, recommendedSize.height),
                fontSize = 10.sp,
                fontFamily = font,
                color = accents.primary.copy(alpha = 0.8f),
            )
        }
    }
}
