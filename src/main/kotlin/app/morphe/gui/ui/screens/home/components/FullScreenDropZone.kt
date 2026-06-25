/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import java.io.File

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun FullScreenDropZone(
    isDragHovering: Boolean,
    onDragHoverChange: (Boolean) -> Unit,
    onFilesDropped: (List<File>) -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val dragAndDropTarget = remember {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                onDragHoverChange(true)
            }

            override fun onEnded(event: DragAndDropEvent) {
                onDragHoverChange(false)
            }

            override fun onExited(event: DragAndDropEvent) {
                onDragHoverChange(false)
            }

            override fun onEntered(event: DragAndDropEvent) {
                onDragHoverChange(true)
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                onDragHoverChange(false)
                if (!enabled) return false
                val transferable = event.awtTransferable
                return try {
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @Suppress("UNCHECKED_CAST")
                        val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                        if (files.isNotEmpty()) {
                            onFilesDropped(files)
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    false
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .dragAndDropTarget(
                shouldStartDragAndDrop = { true },
                target = dragAndDropTarget
            )
    ) {
        content()
    }
}
