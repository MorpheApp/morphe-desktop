/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.morphe.engine.options.ImageSize
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheMotion
import app.morphe.gui.ui.theme.MorpheTheme
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

/** Path input component for files, folders, and images with integrated picker dialog. */
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
    val colors = MorpheTheme.colors
    val shapes = MorpheTheme.shapes
    val dimens = MorpheTheme.dimens
    val scope = rememberCoroutineScope()

    var localPath by remember(value) { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (localPath != value) localPath = value
    }

    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        when {
            isInvalid -> MaterialTheme.colorScheme.error
            isFocused -> colors.primary.copy(alpha = 0.6f)
            else -> colors.primary.copy(alpha = 0.2f)
        },
        animationSpec = MorpheMotion.fastTween(),
        label = "pathInputBorder",
    )

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
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(shapes.small)
                    .border(1.dp, borderColor, shapes.small)
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
                            .clip(shapes.small)
                            .border(1.dp, colors.primary.copy(alpha = 0.3f), shapes.small),
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
                        tint = colors.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp),
                    )
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (localPath.isEmpty()) {
                        Text(
                            placeholder ?: defaultPlaceholder,
                            style = MorpheTheme.typography.bodySmall,
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
                        textStyle = MorpheTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(colors.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isFocused = it.isFocused },
                    )
                }

                if (localPath.isNotBlank() && enabled) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(shapes.small)
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

            MorpheButton(
                label = stringResource(Res.string.browse),
                variant = MorpheButtonVariant.OUTLINED,
                size = MorpheButtonSize.COMPACT,
                customColor = colors.primary,
                modifier = Modifier.fillMaxHeight(),
                enabled = enabled,
                onClick = {
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
                },
            )
        }

        if (recommendedSize != null) {
            Text(
                text = stringResource(Res.string.patch_option_recommended_size, recommendedSize.width, recommendedSize.height),
                style = MorpheTheme.typography.labelMicro,
                color = colors.primary.copy(alpha = 0.8f),
            )
        }
    }
}
