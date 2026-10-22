/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.icon

import app.morphe.gui.ui.components.color.MorpheGradientEditor
import app.morphe.gui.ui.components.color.MorpheSwatchRow
import app.morphe.gui.ui.components.MorpheChoiceChip
import app.morphe.gui.ui.components.MorpheAdjustRow
import app.morphe.gui.data.model.GradientType
import app.morphe.gui.data.model.MorpheFill
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.MorpheDropdown
import app.morphe.gui.ui.components.MorpheDropdownItem
import app.morphe.gui.ui.components.MorpheIconButton
import app.morphe.gui.ui.components.MorpheIconButtonSize
import app.morphe.gui.ui.components.MorpheIconButtonVariant
import app.morphe.gui.ui.components.MorpheScrollbarStyle
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.morphe_desktop.generated.resources.*
import java.awt.GraphicsEnvironment
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private const val RENDER = 320
private const val SNAP = 0.015f

private data class MaskOption(val nameRes: StringResource, val shape: Shape)

private val MASKS: List<MaskOption> = listOf(
    MaskOption(Res.string.icon_studio_shape_circle, CircleShape),
    MaskOption(Res.string.icon_studio_mask_squircle, RoundedCornerShape(46)),
    MaskOption(Res.string.icon_studio_shape_rounded, RoundedCornerShape(28)),
    MaskOption(Res.string.icon_studio_shape_square, RoundedCornerShape(8)),
)

/** Dialog for designing, previewing, and exporting custom adaptive app icons. */
@Composable
fun IconStudioDialog(
    packageName: String,
    onSave: (folderPath: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MorpheTheme.colors
    val shapes = MorpheTheme.shapes
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var project by remember { mutableStateOf(IconProjectStore.load(packageName) ?: IconProject()) }
    var selectedId by remember { mutableStateOf(project.layers.lastOrNull()?.id) }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var silhouette by remember { mutableStateOf<ImageBitmap?>(null) }
    var notifSilhouette by remember { mutableStateOf<ImageBitmap?>(null) }
    var selectedMask by remember { mutableStateOf(3) }
    var dragging by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableStateOf<String?>(null) }
    var importWarning by remember { mutableStateOf<String?>(null) }

    val selected = project.layers.firstOrNull { it.id == selectedId }

    LaunchedEffect(project) {
        preview = withContext(Dispatchers.IO) { IconRenderer.renderComposite(project, RENDER).toComposeImageBitmap() }
        silhouette = withContext(Dispatchers.IO) { IconRenderer.renderSilhouette(project, RENDER, 0xFFFFFFFF.toInt()).toComposeImageBitmap() }
        notifSilhouette = withContext(Dispatchers.IO) { IconRenderer.renderSilhouette(project, RENDER, 0xFFFFFFFF.toInt(), 0.72f).toComposeImageBitmap() }
    }

    val previewDp = 170.dp
    val previewPx = with(density) { previewDp.toPx() }
    val previewColWidth = previewDp * 2 + 12.dp  // two previews (adaptive + monochrome) side by side

    // Time-coalesce rapid edits into a single undo step.
    val history = remember { mutableStateListOf(project) }
    var histIndex by remember { mutableStateOf(0) }
    var lastCommit by remember { mutableStateOf(0L) }

    fun commit(new: IconProject) {
        val now = System.currentTimeMillis()
        if (now - lastCommit < 400L && histIndex == history.lastIndex) {
            history[histIndex] = new
        } else {
            while (history.lastIndex > histIndex) history.removeAt(history.lastIndex)
            history.add(new); histIndex = history.lastIndex
        }
        lastCommit = now
        project = new
    }

    fun restore(p: IconProject) {
        project = p
        if (selectedId != null && p.layers.none { it.id == selectedId }) selectedId = p.layers.lastOrNull()?.id
    }

    fun undo() { if (histIndex > 0) { histIndex--; restore(history[histIndex]) } }
    fun redo() { if (histIndex < history.lastIndex) { histIndex++; restore(history[histIndex]) } }

    fun updateSelected(f: (IconProject.Layer) -> IconProject.Layer) {
        val id = selectedId ?: return
        commit(project.copy(layers = project.layers.map { if (it.id == id) f(it) else it }))
    }

    fun addLayer(content: IconProject.LayerContent) {
        val id = System.nanoTime().toString()
        commit(project.copy(layers = project.layers + IconProject.Layer(id, content)))
        selectedId = id
    }

    // Reorder layers in top-to-bottom visual order.
    fun reorderDisplay(fromD: Int, toD: Int) {
        val display = project.layers.reversed().toMutableList()
        if (fromD !in display.indices) return
        display.add(toD.coerceIn(0, display.size - 1), display.removeAt(fromD))
        commit(project.copy(layers = display.reversed()))
    }

    fun deleteSelected() {
        val id = selectedId ?: return
        commit(project.copy(layers = project.layers.filterNot { it.id == id }))
        selectedId = project.layers.lastOrNull()?.id
    }

    fun duplicateSelected() {
        val id = selectedId ?: return
        val i = project.layers.indexOfFirst { it.id == id }
        if (i < 0) return
        val newId = System.nanoTime().toString()
        val list = project.layers.toMutableList(); list.add(i + 1, project.layers[i].copy(id = newId))
        commit(project.copy(layers = list))
        selectedId = newId
    }

    fun applyTemplate(name: String, p: IconProject) {
        commit(p)
        selectedId = p.layers.lastOrNull()?.id
        selectedTemplate = name
    }

    suspend fun pickImage(): File? =
        MorpheFilePicker.pickFile(
            title = getString(Res.string.select_image),
            extensions = listOf("png", "jpg", "jpeg", "webp"),
        )

    fun copyIntoProject(src: File): String {
        val dir = File(IconExporter.projectDir(packageName), "source").apply { mkdirs() }
        val dest = File(dir, "${System.nanoTime()}-${src.name}")
        runCatching { src.copyTo(dest, overwrite = true) }
        return dest.absolutePath
    }

    suspend fun warnFor(outcome: ForegroundPrep.Outcome): String? = when (outcome) {
        ForegroundPrep.Outcome.OPAQUE ->
            getString(Res.string.icon_studio_warn_opaque)
        ForegroundPrep.Outcome.UNREADABLE -> getString(Res.string.icon_studio_warn_unreadable)
        else -> null
    }

    fun addImageLayer() {
        scope.launch {
            val picked = pickImage() ?: return@launch
            val prepared = withContext(Dispatchers.IO) { ForegroundPrep.prepare(picked, File(IconExporter.projectDir(packageName), "source")) }
            addLayer(IconProject.LayerContent.Image(prepared.path))
            importWarning = warnFor(prepared.outcome)
        }
    }

    fun replaceSelectedImage() {
        val sel = selected ?: return
        if (sel.content !is IconProject.LayerContent.Image) return
        scope.launch {
            val picked = pickImage() ?: return@launch
            val prepared = withContext(Dispatchers.IO) { ForegroundPrep.prepare(picked, File(IconExporter.projectDir(packageName), "source")) }
            updateSelected { it.copy(content = IconProject.LayerContent.Image(prepared.path)) }
            importWarning = warnFor(prepared.outcome)
        }
    }

    fun importBackground() {
        scope.launch {
            val picked = pickImage() ?: return@launch
            val path = withContext(Dispatchers.IO) { copyIntoProject(picked) }
            commit(project.copy(background = MorpheFill.Image(path)))
        }
    }

    fun pickAndSetFont() {
        if (selected?.content !is IconProject.LayerContent.Text) return
        scope.launch {
            val picked = MorpheFilePicker.pickFile(
                title = getString(Res.string.icon_studio_picker_select_font),
                extensions = listOf("ttf", "otf"),
            ) ?: return@launch
            val path = withContext(Dispatchers.IO) { copyIntoProject(picked) }
            updateSelected { layer ->
                val txt = layer.content as? IconProject.LayerContent.Text ?: return@updateSelected layer
                layer.copy(content = txt.copy(fontPath = path, fontName = null))
            }
        }
    }

    fun save() {
        if (project.layers.isEmpty()) return
        busy = true
        scope.launch {
            val folder = withContext(Dispatchers.IO) {
                IconProjectStore.save(project, packageName)
                IconExporter.export(project, packageName)
            }
            busy = false
            onSave(folder.absolutePath)
            onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.width(840.dp).heightIn(max = 660.dp).padding(4.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.icon_studio_title),
                        style = MorpheTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.weight(1f))
                    MorpheButton(
                        label = stringResource(Res.string.icon_studio_undo_button),
                        icon = MorpheIcons.Undo,
                        variant = MorpheButtonVariant.GHOST,
                        size = MorpheButtonSize.COMPACT,
                        enabled = histIndex > 0,
                        onClick = { undo() },
                    )
                    Spacer(Modifier.width(6.dp))
                    MorpheButton(
                        label = stringResource(Res.string.icon_studio_redo_button),
                        icon = MorpheIcons.Redo,
                        variant = MorpheButtonVariant.GHOST,
                        size = MorpheButtonSize.COMPACT,
                        enabled = histIndex < history.lastIndex,
                        onClick = { redo() },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                    Label(stringResource(Res.string.icon_studio_templates))
                    MorpheChoiceChip(
                        text = stringResource(Res.string.icon_studio_background_gradient),
                        active = selectedTemplate == "GRADIENT",
                        onClick = { applyTemplate("GRADIENT", templateGradient()) },
                    )
                    MorpheChoiceChip(
                        text = stringResource(Res.string.icon_studio_template_badge),
                        active = selectedTemplate == "BADGE",
                        onClick = { applyTemplate("BADGE", templateBadge()) },
                    )
                    MorpheChoiceChip(
                        text = stringResource(Res.string.icon_studio_tab_shape),
                        active = selectedTemplate == "SHAPE",
                        onClick = { applyTemplate("SHAPE", templateShape()) },
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.weight(1f, fill = false)) {
                    Column(Modifier.width(previewColWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Label(stringResource(Res.string.icon_studio_preview_adaptive))
                                Canvas(
                                    Modifier.size(previewDp).clip(MASKS[selectedMask].shape)
                                        .pointerInput(selectedId) {
                                            detectDragGestures(
                                                onDragStart = { dragging = true },
                                                onDragEnd = { dragging = false },
                                                onDragCancel = { dragging = false },
                                            ) { _, drag ->
                                                val sel = project.layers.firstOrNull { it.id == selectedId }
                                                if (sel != null) {
                                                    var nx = (sel.offsetX + drag.x / previewPx).coerceIn(-0.5f, 0.5f)
                                                    var ny = (sel.offsetY + drag.y / previewPx).coerceIn(-0.5f, 0.5f)
                                                    if (abs(nx) < SNAP) nx = 0f
                                                    if (abs(ny) < SNAP) ny = 0f
                                                    updateSelected { it.copy(offsetX = nx, offsetY = ny) }
                                                }
                                            }
                                        },
                                ) {
                                    drawPreview(preview)
                                    val guide = colors.primary.copy(alpha = 0.5f)
                                    drawCircle(guide, radius = size.minDimension * 0.33f, style = Stroke(width = 2f))
                                    drawCircle(guide.copy(alpha = 0.3f), radius = size.minDimension * 0.21f, style = Stroke(width = 1.5f))
                                    if (dragging) project.layers.firstOrNull { it.id == selectedId }?.let { sel ->
                                        if (sel.offsetX == 0f) drawLine(colors.primary, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), strokeWidth = 1.5f)
                                        if (sel.offsetY == 0f) drawLine(colors.primary, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 1.5f)
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Label(stringResource(Res.string.icon_studio_preview_monochrome))
                                Canvas(Modifier.size(previewDp).clip(MASKS[selectedMask].shape).background(Color(0xFF15171E))) {
                                    silhouette?.let { drawImage(it, dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.toInt(), size.height.toInt()), colorFilter = ColorFilter.tint(colors.primary)) }
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Label(stringResource(Res.string.icon_studio_preview_shapes))
                        Text(
                            text = stringResource(Res.string.icon_studio_preview_shapes_hint),
                            style = MorpheTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MASKS.forEachIndexed { i, mask ->
                                val sel = i == selectedMask
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Canvas(Modifier.size(48.dp).clip(mask.shape).border(if (sel) 2.dp else 1.dp, colors.primary.copy(alpha = if (sel) 0.8f else 0.25f), mask.shape).clickable { selectedMask = i }) {
                                        drawPreview(preview)
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = stringResource(mask.nameRes),
                                        style = MorpheTheme.typography.labelSmall,
                                        color = if (sel) colors.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Label(stringResource(Res.string.icon_studio_preview_notification_status_bar))
                        Spacer(Modifier.height(6.dp))
                        StatusBarPreview(notifSilhouette)
                    }

                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        val scrollState = rememberScrollState()
                        Column(Modifier.fillMaxWidth().verticalScroll(scrollState).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Position warning within scrollable controls to preserve preview dimensions.
                        importWarning?.let { msg ->
                            Row(
                                Modifier.fillMaxWidth().clip(shapes.small)
                                    .background(colors.danger.copy(alpha = 0.12f))
                                    .border(1.dp, colors.danger.copy(alpha = 0.4f), shapes.small)
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top,
                            ) {
                                Icon(MorpheIcons.Warning, contentDescription = null, tint = colors.danger, modifier = Modifier.size(14.dp))
                                Text(msg, style = MorpheTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                Icon(MorpheIcons.Close, contentDescription = stringResource(Res.string.close), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp).clickable { importWarning = null })
                            }
                        }
                        LayersPanel(project.layers, selectedId, onSelect = { selectedId = it }, onAddImage = { addImageLayer() }, onAddText = { addLayer(IconProject.LayerContent.Text()) }, onAddShape = { addLayer(IconProject.LayerContent.Shape()) }, onReorder = { f, t -> reorderDisplay(f, t) }, onDelete = { deleteSelected() })

                        BackgroundControls(project, onImportBg = { importBackground() }) { commit(it) }

                        selected?.let { layer ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Label(stringResource(Res.string.icon_studio_layer_label))
                                    Spacer(Modifier.weight(1f))
                                    MorpheChoiceChip(stringResource(Res.string.icon_studio_duplicate_button), active = false) { duplicateSelected() }
                                }
                                TextInput(layer.name ?: "", placeholder = stringResource(Res.string.icon_studio_layer_name_placeholder)) { n -> updateSelected { it.copy(name = n.ifBlank { null }) } }
                                MorpheAdjustRow(stringResource(Res.string.icon_studio_adjust_opacity), layer.opacity, 0f..1f, decimals = 2) { o -> updateSelected { it.copy(opacity = o) } }
                            }
                            LayerContentControls(layer, onReplaceImage = { replaceSelectedImage() }, onPickFont = { pickAndSetFont() }) { updateSelected(it) }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Label(stringResource(Res.string.icon_studio_transform_label))
                                MorpheAdjustRow(stringResource(Res.string.icon_studio_adjust_scale), layer.scale, 0.1f..3f, decimals = 2) { v -> updateSelected { it.copy(scale = v) } }
                                MorpheAdjustRow(stringResource(Res.string.icon_studio_adjust_rotation), layer.rotationDeg, -180f..180f, decimals = 0) { v -> updateSelected { it.copy(rotationDeg = v) } }
                                MorpheButton(
                                    label = stringResource(Res.string.icon_studio_reset_position_button),
                                    variant = MorpheButtonVariant.GHOST,
                                    size = MorpheButtonSize.COMPACT,
                                    onClick = { updateSelected { it.copy(offsetX = 0f, offsetY = 0f, rotationDeg = 0f) } }
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Label(stringResource(Res.string.icon_studio_color_label))
                                MorpheAdjustRow(stringResource(Res.string.icon_studio_adjust_hue), layer.hueShiftDeg, -180f..180f, decimals = 0) { v -> updateSelected { it.copy(hueShiftDeg = v) } }
                                MorpheAdjustRow(stringResource(Res.string.icon_studio_adjust_saturation), layer.saturation, 0f..2f, decimals = 2) { v -> updateSelected { it.copy(saturation = v) } }
                                MorpheAdjustRow(stringResource(Res.string.icon_studio_adjust_brightness), layer.brightness, 0f..2f, decimals = 2) { v -> updateSelected { it.copy(brightness = v) } }
                            }
                            EffectSection(stringResource(Res.string.icon_studio_effect_shadow), layer.shadow != null, onToggle = { on -> updateSelected { it.copy(shadow = if (on) (it.shadow ?: IconProject.Shadow()) else null) } }) {
                                layer.shadow?.let { sh ->
                                    MorpheAdjustRow(stringResource(Res.string.icon_studio_adjust_y_offset), sh.offsetY, -0.15f..0.15f, decimals = 3) { v -> updateSelected { it.copy(shadow = sh.copy(offsetY = v)) } }
                                    BlurAndOpacityRows(blur = sh.blur, alpha = sh.alpha, onBlurChange = { v -> updateSelected { it.copy(shadow = sh.copy(blur = v)) } }, onAlphaChange = { v -> updateSelected { it.copy(shadow = sh.copy(alpha = v)) } })
                                }
                            }
                            EffectSection(stringResource(Res.string.icon_studio_effect_glow), layer.glow != null, onToggle = { on -> updateSelected { it.copy(glow = if (on) (it.glow ?: IconProject.Glow()) else null) } }) {
                                layer.glow?.let { gl ->
                                    MorpheSwatchRow(gl.color) { c -> updateSelected { it.copy(glow = gl.copy(color = c)) } }
                                    BlurAndOpacityRows(blur = gl.blur, alpha = gl.alpha, onBlurChange = { v -> updateSelected { it.copy(glow = gl.copy(blur = v)) } }, onAlphaChange = { v -> updateSelected { it.copy(glow = gl.copy(alpha = v)) } })
                                }
                            }
                            EffectSection(stringResource(Res.string.icon_studio_effect_stroke), layer.stroke != null, onToggle = { on -> updateSelected { it.copy(stroke = if (on) (it.stroke ?: IconProject.Stroke()) else null) } }) {
                                layer.stroke?.let { st ->
                                    MorpheSwatchRow(st.color) { c -> updateSelected { it.copy(stroke = st.copy(color = c)) } }
                                    MorpheAdjustRow(stringResource(Res.string.icon_studio_adjust_width), st.width, 0f..0.04f, decimals = 3) { v -> updateSelected { it.copy(stroke = st.copy(width = v)) } }
                                }
                            }
                        }
                        }
                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(scrollState),
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            style = MorpheScrollbarStyle(),
                        )
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                    MorpheButton(
                        label = stringResource(Res.string.cancel),
                        variant = MorpheButtonVariant.GHOST,
                        size = MorpheButtonSize.COMPACT,
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.width(8.dp))
                    MorpheButton(
                        label = if (busy) stringResource(Res.string.status_saving) else stringResource(Res.string.icon_studio_save_icon_button),
                        variant = MorpheButtonVariant.PRIMARY,
                        size = MorpheButtonSize.COMPACT,
                        enabled = project.layers.isNotEmpty() && !busy,
                        onClick = { save() },
                    )
                }
            }
        }
    }
}

@Composable
private fun LayersPanel(
    layers: List<IconProject.Layer>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onAddImage: () -> Unit,
    onAddText: () -> Unit,
    onAddShape: () -> Unit,
    onReorder: (Int, Int) -> Unit,
    onDelete: () -> Unit,
) {
    val display = layers.reversed()
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragDy by remember { mutableStateOf(0f) }
    val rowH = with(LocalDensity.current) { MorpheTheme.dimens.chipHeight.toPx() }
    val colors = MorpheTheme.colors
    val shapes = MorpheTheme.shapes

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Label(stringResource(Res.string.icon_studio_foreground_label))
            Spacer(Modifier.weight(1f))
            MorpheChoiceChip(stringResource(Res.string.icon_studio_add_image_button), active = false, icon = MorpheIcons.Add, onClick = onAddImage)
            MorpheChoiceChip(stringResource(Res.string.icon_studio_add_text_button), active = false, icon = MorpheIcons.Add, onClick = onAddText)
            MorpheChoiceChip(stringResource(Res.string.icon_studio_add_shape_button), active = false, icon = MorpheIcons.Add, onClick = onAddShape)
        }
        Text(
            text = stringResource(Res.string.icon_studio_foreground_hint),
            style = MorpheTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (display.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().clip(shapes.small).background(colors.primary.copy(alpha = 0.08f))
                    .border(1.dp, colors.primary.copy(alpha = 0.25f), shapes.small).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(MorpheIcons.Info, contentDescription = null, tint = colors.primary.copy(alpha = 0.8f), modifier = Modifier.size(13.dp))
                Text(
                    text = stringResource(Res.string.icon_studio_no_foreground_hint),
                    style = MorpheTheme.typography.labelSmall,
                    color = colors.primary,
                )
            }
        } else {
            display.forEachIndexed { i, layer ->
                val sel = layer.id == selectedId
                val isDragging = layer.id == dragId
                Row(
                    Modifier.fillMaxWidth()
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragging) dragDy else 0f }
                        .clip(shapes.small)
                        .background(if (isDragging) colors.primary.copy(alpha = 0.25f) else if (sel) colors.primary.copy(alpha = 0.15f) else Color.Transparent)
                        .border(1.dp, colors.primary.copy(alpha = if (sel || isDragging) 0.5f else 0.15f), shapes.small)
                        .clickable { onSelect(layer.id) }.padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier.pointerInput(layer.id) {
                            detectDragGestures(
                                onDragStart = { dragId = layer.id; dragDy = 0f },
                                onDragEnd = {
                                    val target = (i + (dragDy / rowH).roundToInt()).coerceIn(0, display.size - 1)
                                    if (target != i) onReorder(i, target)
                                    dragId = null; dragDy = 0f
                                },
                                onDragCancel = { dragId = null; dragDy = 0f },
                            ) { _, drag -> dragDy += drag.y }
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(MorpheIcons.DragIndicator, contentDescription = stringResource(Res.string.drag_to_reorder), tint = colors.primary.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                    }
                    Text(
                        text = layer.label,
                        style = MorpheTheme.typography.labelSmall,
                        color = if (sel) colors.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (sel) {
                        MorpheIconButton(
                            icon = MorpheIcons.Delete,
                            contentDescription = stringResource(Res.string.remove),
                            size = MorpheIconButtonSize.COMPACT,
                            variant = MorpheIconButtonVariant.GHOST,
                            onClick = onDelete
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LayerContentControls(
    layer: IconProject.Layer,
    onReplaceImage: () -> Unit,
    onPickFont: () -> Unit,
    update: ((IconProject.Layer) -> IconProject.Layer) -> Unit,
) {
    when (val c = layer.content) {
        is IconProject.LayerContent.Image -> MorpheButton(
            label = stringResource(Res.string.icon_studio_replace_icon_button),
            variant = MorpheButtonVariant.PRIMARY,
            size = MorpheButtonSize.COMPACT,
            onClick = onReplaceImage,
        )
        is IconProject.LayerContent.Text -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label(stringResource(Res.string.icon_studio_text_label))
            TextInput(c.text) { t -> update { it.copy(content = c.copy(text = t)) } }
            MorpheSwatchRow(c.color) { col -> update { it.copy(content = c.copy(color = col)) } }
            FontSelector(
                c.fontName, c.fontPath,
                onSystemFont = { name -> update { it.copy(content = c.copy(fontName = name, fontPath = null)) } },
                onDefault = { update { it.copy(content = c.copy(fontName = null, fontPath = null)) } },
                onPickFont = onPickFont,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    Triple(Res.string.icon_studio_toggle_bold, c.bold) { b: Boolean -> c.copy(bold = b) },
                    Triple(Res.string.icon_studio_toggle_italic, c.italic) { b: Boolean -> c.copy(italic = b) },
                    Triple(Res.string.icon_studio_toggle_underline, c.underline) { b: Boolean -> c.copy(underline = b) },
                    Triple(Res.string.icon_studio_toggle_strike, c.strikethrough) { b: Boolean -> c.copy(strikethrough = b) },
                ).forEach { (res, active, copyFn) ->
                    MorpheChoiceChip(stringResource(res), active = active) {
                        update { it.copy(content = copyFn(!active)) }
                    }
                }
            }
            MorpheAdjustRow(stringResource(Res.string.icon_studio_adjust_spacing), c.letterSpacing, -0.05f..0.4f, decimals = 3) { sp -> update { it.copy(content = c.copy(letterSpacing = sp)) } }
        }
        is IconProject.LayerContent.Shape -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label(stringResource(Res.string.icon_studio_tab_shape))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IconProject.ShapeKind.entries.forEach { k ->
                    val label = when (k) {
                        IconProject.ShapeKind.CIRCLE -> stringResource(Res.string.icon_studio_shape_circle)
                        IconProject.ShapeKind.SQUARE -> stringResource(Res.string.icon_studio_shape_square)
                        IconProject.ShapeKind.ROUNDED -> stringResource(Res.string.icon_studio_shape_rounded)
                        IconProject.ShapeKind.TRIANGLE -> stringResource(Res.string.icon_studio_shape_triangle)
                        IconProject.ShapeKind.DIAMOND -> stringResource(Res.string.icon_studio_shape_diamond)
                        IconProject.ShapeKind.PENTAGON -> stringResource(Res.string.icon_studio_shape_pentagon)
                        IconProject.ShapeKind.HEXAGON -> stringResource(Res.string.icon_studio_shape_hexagon)
                        IconProject.ShapeKind.STAR -> stringResource(Res.string.icon_studio_shape_star)
                    }
                    MorpheChoiceChip(label, active = c.kind == k) { update { it.copy(content = c.copy(kind = k)) } }
                }
            }
            MorpheSwatchRow(c.color) { col -> update { it.copy(content = c.copy(color = col)) } }
        }
    }
}

@Composable
private fun FontSelector(
    fontName: String?,
    fontPath: String?,
    onSystemFont: (String) -> Unit,
    onDefault: () -> Unit,
    onPickFont: () -> Unit,
) {
    val defaultFontLabel = stringResource(Res.string.icon_studio_default_font)
    val fonts = remember { GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList() }
    val label = when {
        fontPath != null -> File(fontPath).name
        fontName != null -> fontName
        else -> defaultFontLabel
    }
    val items = buildList {
        add(MorpheDropdownItem(defaultFontLabel, onDefault))
        fonts.forEach { f -> add(MorpheDropdownItem(f) { onSystemFont(f) }) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        MorpheDropdown(label, items, modifier = Modifier.weight(1f), searchable = true)
        MorpheChoiceChip(stringResource(Res.string.icon_studio_add_file_button), active = false, onClick = onPickFont)
    }
}

@Composable
private fun EffectSection(
    name: String,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    body: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Label(name)
            Spacer(Modifier.weight(1f))
            MorpheChoiceChip(stringResource(Res.string.off), active = !on, dense = true) { onToggle(false) }
            MorpheChoiceChip(stringResource(Res.string.on), active = on, dense = true) { onToggle(true) }
        }
        body()
    }
}

@Composable
private fun BackgroundControls(
    project: IconProject,
    onImportBg: () -> Unit,
    onChange: (IconProject) -> Unit,
) {
    val bg = project.background
    val colors = MorpheTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Label(stringResource(Res.string.icon_studio_background_label))
            Spacer(Modifier.weight(1f))
            MorpheChoiceChip(stringResource(Res.string.icon_studio_background_solid), active = bg is MorpheFill.Solid) {
                onChange(project.copy(background = MorpheFill.Solid(0xFFFFFFFF.toInt())))
            }
            MorpheChoiceChip(stringResource(Res.string.icon_studio_background_gradient), active = bg is MorpheFill.Gradient) {
                onChange(project.copy(background = MorpheFill.Gradient()))
            }
            MorpheChoiceChip(stringResource(Res.string.icon_studio_background_image), active = bg is MorpheFill.Image, onClick = onImportBg)
        }
        Text(
            text = stringResource(Res.string.icon_studio_background_hint),
            style = MorpheTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (bg) {
            is MorpheFill.Accent -> Unit
            is MorpheFill.Solid -> MorpheSwatchRow(bg.argb) {
                onChange(project.copy(background = MorpheFill.Solid(it)))
            }
            is MorpheFill.Gradient -> MorpheGradientEditor(bg) {
                onChange(project.copy(background = it))
            }
            is MorpheFill.Image -> Text(
                text = stringResource(Res.string.icon_studio_background_image_set_hint),
                style = MorpheTheme.typography.labelSmall,
                color = colors.primary,
            )
        }
    }
}

@Composable
private fun TextInput(value: String, placeholder: String = "", onValue: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (text != value) text = value }
    val colors = MorpheTheme.colors
    val shapes = MorpheTheme.shapes
    Row(
        Modifier.fillMaxWidth().height(28.dp).clip(shapes.small)
            .border(1.dp, colors.primary.copy(alpha = 0.25f), shapes.small).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it; onValue(it) },
                singleLine = true,
                textStyle = MorpheTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun DrawScope.drawPreview(bitmap: ImageBitmap?) {
    if (bitmap == null) return
    val s = size.minDimension.toInt()
    drawImage(image = bitmap, dstOffset = IntOffset.Zero, dstSize = IntSize(s, s))
}

/** Previews status bar notification icon silhouette. */
@Composable
private fun StatusBarPreview(silhouette: ImageBitmap?) {
    val shapes = MorpheTheme.shapes
    val onSurface = MaterialTheme.colorScheme.onSurface
    Box(Modifier.fillMaxWidth().height(40.dp).clip(shapes.medium)
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .border(1.dp, onSurface.copy(alpha = 0.1f), shapes.medium)
        .padding(horizontal = 14.dp)) {
        Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "9:41",
                style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = onSurface
            )
            Canvas(Modifier.size(18.dp)) {
                drawRect(color = onSurface.copy(alpha = 0.3f), topLeft = Offset(0.5f, 0.5f), size = Size(size.width - 1f, size.height - 1f), style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))))
                silhouette?.let { drawImage(it, dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.toInt(), size.height.toInt()), colorFilter = ColorFilter.tint(onSurface)) }
            }
        }
        Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(MorpheIcons.SignalCellular4Bar, contentDescription = null, tint = onSurface, modifier = Modifier.size(14.dp))
            Icon(MorpheIcons.Wifi, contentDescription = null, tint = onSurface, modifier = Modifier.size(14.dp))
            Icon(MorpheIcons.BatteryFull, contentDescription = null, tint = onSurface, modifier = Modifier.size(14.dp))
        }
    }
}

private fun templateGradient() = IconProject(
    background = MorpheFill.Gradient(
        stops = listOf(MorpheFill.Stop(0f, 0xFF00E5FF.toInt()), MorpheFill.Stop(1f, 0xFF7C4DFF.toInt())),
        type = GradientType.RADIAL,
    ),
)

private fun templateBadge() = IconProject(
    background = MorpheFill.Solid(0xFF1DE9B6.toInt()),
    layers = listOf(IconProject.Layer(System.nanoTime().toString(), IconProject.LayerContent.Text("A", 0xFF102027.toInt(), true), scale = 0.6f)),
)

private fun templateShape() = IconProject(
    background = MorpheFill.Gradient(
        stops = listOf(MorpheFill.Stop(0f, 0xFFFF6D00.toInt()), MorpheFill.Stop(1f, 0xFFFF0033.toInt())),
        type = GradientType.RADIAL,
    ),
    layers = listOf(IconProject.Layer(System.nanoTime().toString(), IconProject.LayerContent.Shape(IconProject.ShapeKind.CIRCLE, 0xFFFFFFFF.toInt()), scale = 0.5f)),
)

@Composable
private fun Label(text: String) = Text(
    text = text,
    style = MorpheTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun BlurAndOpacityRows(
    blur: Float,
    alpha: Float,
    onBlurChange: (Float) -> Unit,
    onAlphaChange: (Float) -> Unit,
) {
    MorpheAdjustRow(stringResource(Res.string.icon_studio_adjust_blur), blur, 0f..0.1f, decimals = 3, onChange = onBlurChange)
    MorpheAdjustRow(stringResource(Res.string.icon_studio_adjust_opacity), alpha, 0f..1f, decimals = 2, onChange = onAlphaChange)
}

