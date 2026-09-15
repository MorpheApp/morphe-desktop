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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.MorpheDropdown
import app.morphe.gui.ui.components.MorpheDropdownItem
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheAccentColors
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

/**
 * Icon Studio. A stack of foreground layers (image / text / shape) over a
 * solid/gradient/image background, each with transform, colour adjust and
 * effects. The preview renders through [IconRenderer] (pixel-exact to export)
 * under a multi-mask gallery. Save persists the project and bakes the mipmap
 * folder the branding patch reads.
 */
@Composable
fun IconStudioDialog(
    packageName: String,
    onSave: (folderPath: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val accents = LocalMorpheAccents.current
    val font = LocalMorpheFont.current
    val corners = LocalMorpheCorners.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var project by remember { mutableStateOf(IconProjectStore.load(packageName) ?: IconProject()) }
    var selectedId by remember { mutableStateOf(project.layers.lastOrNull()?.id) }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var silhouette by remember { mutableStateOf<ImageBitmap?>(null) }        // themed/monochrome (adaptive position)
    var notifSilhouette by remember { mutableStateOf<ImageBitmap?>(null) }   // notification (fitted to safe zone)
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

    // Undo/redo: all mutations funnel through commit(), which time-coalesces rapid
    // edits (a burst of slider drags = one undo step) so we don't get 100 steps/drag.
    val history = remember { mutableStateListOf(project) }
    var histIndex by remember { mutableStateOf(0) }
    var lastCommit by remember { mutableStateOf(0L) }

    fun commit(new: IconProject) {
        val now = System.currentTimeMillis()
        if (now - lastCommit < 400L && histIndex == history.lastIndex) {
            history[histIndex] = new                                  // coalesce into current step
        } else {
            while (history.lastIndex > histIndex) history.removeAt(history.lastIndex)  // drop redo tail
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

    // Reorder within the displayed (top-of-stack-first) order, then map back.
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
        else -> null  // Already transparent. Nothing to warn about
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
            shape = RoundedCornerShape(corners.medium),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.width(840.dp).heightIn(max = 660.dp).padding(4.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.icon_studio_title), fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    StudioButton(stringResource(Res.string.icon_studio_undo_button), accents, font, enabled = histIndex > 0, filled = false, icon = MorpheIcons.Undo) { undo() }
                    Spacer(Modifier.width(6.dp))
                    StudioButton(stringResource(Res.string.icon_studio_redo_button), accents, font, enabled = histIndex < history.lastIndex, filled = false, icon = MorpheIcons.Redo) { redo() }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                    Label(stringResource(Res.string.icon_studio_templates), font)
                    Toggle(stringResource(Res.string.icon_studio_background_gradient), selectedTemplate == "GRADIENT", accents, font) { applyTemplate("GRADIENT", templateGradient()) }
                    Toggle(stringResource(Res.string.icon_studio_template_badge), selectedTemplate == "BADGE", accents, font) { applyTemplate("BADGE", templateBadge()) }
                    Toggle(stringResource(Res.string.icon_studio_tab_shape), selectedTemplate == "SHAPE", accents, font) { applyTemplate("SHAPE", templateShape()) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.weight(1f, fill = false)) {
                    // ── Previews: adaptive + monochrome side by side, mask gallery, status bar ──
                    Column(Modifier.width(previewColWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Label(stringResource(Res.string.icon_studio_preview_adaptive), font)
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
                                    val guide = accents.primary.copy(alpha = 0.5f)
                                    drawCircle(guide, radius = size.minDimension * 0.33f, style = Stroke(width = 2f))
                                    drawCircle(guide.copy(alpha = 0.3f), radius = size.minDimension * 0.21f, style = Stroke(width = 1.5f))
                                    if (dragging) project.layers.firstOrNull { it.id == selectedId }?.let { sel ->
                                        if (sel.offsetX == 0f) drawLine(accents.primary, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), strokeWidth = 1.5f)
                                        if (sel.offsetY == 0f) drawLine(accents.primary, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 1.5f)
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Label(stringResource(Res.string.icon_studio_preview_monochrome), font)
                                Canvas(Modifier.size(previewDp).clip(MASKS[selectedMask].shape).background(Color(0xFF15171E))) {
                                    silhouette?.let { drawImage(it, dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.toInt(), size.height.toInt()), colorFilter = ColorFilter.tint(accents.primary)) }
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Label(stringResource(Res.string.icon_studio_preview_shapes), font)
                        Text(
                            stringResource(Res.string.icon_studio_preview_shapes_hint),
                            fontFamily = font, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MASKS.forEachIndexed { i, mask ->
                                val sel = i == selectedMask
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Canvas(Modifier.size(48.dp).clip(mask.shape).border(if (sel) 2.dp else 1.dp, accents.primary.copy(alpha = if (sel) 0.8f else 0.25f), mask.shape).clickable { selectedMask = i }) {
                                        drawPreview(preview)
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(stringResource(mask.nameRes), fontFamily = font, fontSize = 11.sp, fontWeight = FontWeight.Normal, color = if (sel) accents.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Label(stringResource(Res.string.icon_studio_preview_notification_status_bar), font)
                        Spacer(Modifier.height(6.dp))
                        StatusBarPreview(notifSilhouette, font)
                    }

                    // ── Controls (scrollable, with scrollbar) ──
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        val scrollState = rememberScrollState()
                        Column(Modifier.fillMaxWidth().verticalScroll(scrollState).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Import warning lives inside the scrollable controls so it never
                        // steals height from the (fixed) preview column and squishes it.
                        importWarning?.let { msg ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(corners.small))
                                    .background(Color(0xFFE0504D).copy(alpha = 0.12f))
                                    .border(1.dp, Color(0xFFE0504D).copy(alpha = 0.4f), RoundedCornerShape(corners.small))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top,
                            ) {
                                Icon(MorpheIcons.Warning, contentDescription = null, tint = Color(0xFFE0504D), modifier = Modifier.size(14.dp))
                                Text(msg, fontFamily = font, fontSize = 11.sp, fontWeight = FontWeight.Normal, lineHeight = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                Icon(MorpheIcons.Close, contentDescription = stringResource(Res.string.close), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp).clickable { importWarning = null })
                            }
                        }
                        LayersPanel(project.layers, selectedId, accents, font, onSelect = { selectedId = it }, onAddImage = { addImageLayer() }, onAddText = { addLayer(IconProject.LayerContent.Text()) }, onAddShape = { addLayer(IconProject.LayerContent.Shape()) }, onReorder = { f, t -> reorderDisplay(f, t) }, onDelete = { deleteSelected() })

                        BackgroundControls(project, accents, font, onImportBg = { importBackground() }) { commit(it) }

                        selected?.let { layer ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Label(stringResource(Res.string.icon_studio_layer_label), font)
                                    Spacer(Modifier.weight(1f))
                                    Toggle(stringResource(Res.string.icon_studio_duplicate_button), false, accents, font) { duplicateSelected() }
                                }
                                TextInput(layer.name ?: "", font, accents, placeholder = stringResource(Res.string.icon_studio_layer_name_placeholder)) { n -> updateSelected { it.copy(name = n.ifBlank { null }) } }
                                AdjustRow(stringResource(Res.string.icon_studio_adjust_opacity), layer.opacity, 0f..1f, 2, font, accents) { o -> updateSelected { it.copy(opacity = o) } }
                            }
                            LayerContentControls(layer, accents, font, onReplaceImage = { replaceSelectedImage() }, onPickFont = { pickAndSetFont() }) { updateSelected(it) }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Label(stringResource(Res.string.icon_studio_transform_label), font)
                                AdjustRow(stringResource(Res.string.icon_studio_adjust_scale), layer.scale, 0.1f..3f, 2, font, accents) { v -> updateSelected { it.copy(scale = v) } }
                                AdjustRow(stringResource(Res.string.icon_studio_adjust_rotation), layer.rotationDeg, -180f..180f, 0, font, accents) { v -> updateSelected { it.copy(rotationDeg = v) } }
                                StudioButton(stringResource(Res.string.icon_studio_reset_position_button), accents, font, filled = false) { updateSelected { it.copy(offsetX = 0f, offsetY = 0f, rotationDeg = 0f) } }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Label(stringResource(Res.string.icon_studio_color_label), font)
                                AdjustRow(stringResource(Res.string.icon_studio_adjust_hue), layer.hueShiftDeg, -180f..180f, 0, font, accents) { v -> updateSelected { it.copy(hueShiftDeg = v) } }
                                AdjustRow(stringResource(Res.string.icon_studio_adjust_saturation), layer.saturation, 0f..2f, 2, font, accents) { v -> updateSelected { it.copy(saturation = v) } }
                                AdjustRow(stringResource(Res.string.icon_studio_adjust_brightness), layer.brightness, 0f..2f, 2, font, accents) { v -> updateSelected { it.copy(brightness = v) } }
                            }
                            EffectSection(stringResource(Res.string.icon_studio_effect_shadow), layer.shadow != null, accents, font, onToggle = { on -> updateSelected { it.copy(shadow = if (on) (it.shadow ?: IconProject.Shadow()) else null) } }) {
                                layer.shadow?.let { sh ->
                                    AdjustRow(stringResource(Res.string.icon_studio_adjust_y_offset), sh.offsetY, -0.15f..0.15f, 3, font, accents) { v -> updateSelected { it.copy(shadow = sh.copy(offsetY = v)) } }
                                    AdjustRow(stringResource(Res.string.icon_studio_adjust_blur), sh.blur, 0f..0.1f, 3, font, accents) { v -> updateSelected { it.copy(shadow = sh.copy(blur = v)) } }
                                    AdjustRow(stringResource(Res.string.icon_studio_adjust_opacity), sh.alpha, 0f..1f, 2, font, accents) { v -> updateSelected { it.copy(shadow = sh.copy(alpha = v)) } }
                                }
                            }
                            EffectSection(stringResource(Res.string.icon_studio_effect_glow), layer.glow != null, accents, font, onToggle = { on -> updateSelected { it.copy(glow = if (on) (it.glow ?: IconProject.Glow()) else null) } }) {
                                layer.glow?.let { gl ->
                                    SwatchRow(gl.color) { c -> updateSelected { it.copy(glow = gl.copy(color = c)) } }
                                    AdjustRow(stringResource(Res.string.icon_studio_adjust_blur), gl.blur, 0f..0.1f, 3, font, accents) { v -> updateSelected { it.copy(glow = gl.copy(blur = v)) } }
                                    AdjustRow(stringResource(Res.string.icon_studio_adjust_opacity), gl.alpha, 0f..1f, 2, font, accents) { v -> updateSelected { it.copy(glow = gl.copy(alpha = v)) } }
                                }
                            }
                            EffectSection(stringResource(Res.string.icon_studio_effect_stroke), layer.stroke != null, accents, font, onToggle = { on -> updateSelected { it.copy(stroke = if (on) (it.stroke ?: IconProject.Stroke()) else null) } }) {
                                layer.stroke?.let { st ->
                                    SwatchRow(st.color) { c -> updateSelected { it.copy(stroke = st.copy(color = c)) } }
                                    AdjustRow(stringResource(Res.string.icon_studio_adjust_width), st.width, 0f..0.04f, 3, font, accents) { v -> updateSelected { it.copy(stroke = st.copy(width = v)) } }
                                }
                            }
                        }
                        }
                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(scrollState),
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            style = morpheScrollbarStyle(),
                        )
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                    StudioButton(stringResource(Res.string.cancel), accents, font, filled = false) { onDismiss() }
                    Spacer(Modifier.width(8.dp))
                    StudioButton(if (busy) stringResource(Res.string.status_saving) else stringResource(Res.string.icon_studio_save_icon_button), accents, font, enabled = project.layers.isNotEmpty() && !busy) { save() }
                }
            }
        }
    }
}

@Composable
private fun LayersPanel(
    layers: List<IconProject.Layer>, selectedId: String?, accents: MorpheAccentColors, font: FontFamily,
    onSelect: (String) -> Unit, onAddImage: () -> Unit, onAddText: () -> Unit, onAddShape: () -> Unit,
    onReorder: (Int, Int) -> Unit, onDelete: () -> Unit,
) {
    val display = layers.reversed()  // top of stack first
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragDy by remember { mutableStateOf(0f) }
    val rowH = with(LocalDensity.current) { 34.dp.toPx() }
    val corners = LocalMorpheCorners.current

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Label(stringResource(Res.string.icon_studio_foreground_label), font)
            Spacer(Modifier.weight(1f))
            Toggle(stringResource(Res.string.icon_studio_add_image_button), false, accents, font, icon = MorpheIcons.Add) { onAddImage() }
            Toggle(stringResource(Res.string.icon_studio_add_text_button), false, accents, font, icon = MorpheIcons.Add) { onAddText() }
            Toggle(stringResource(Res.string.icon_studio_add_shape_button), false, accents, font, icon = MorpheIcons.Add) { onAddShape() }
        }
        Text(stringResource(Res.string.icon_studio_foreground_hint), fontFamily = font, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (display.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(corners.small)).background(accents.primary.copy(alpha = 0.08f))
                    .border(1.dp, accents.primary.copy(alpha = 0.25f), RoundedCornerShape(corners.small)).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(MorpheIcons.Info, contentDescription = null, tint = accents.primary.copy(alpha = 0.8f), modifier = Modifier.size(13.dp))
                Text(
                    stringResource(Res.string.icon_studio_no_foreground_hint),
                    fontFamily = font, fontSize = 11.sp, fontWeight = FontWeight.Normal, lineHeight = 12.sp, color = accents.primary,
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
                        .clip(RoundedCornerShape(corners.small))
                        .background(if (isDragging) accents.primary.copy(alpha = 0.25f) else if (sel) accents.primary.copy(alpha = 0.15f) else Color.Transparent)
                        .border(1.dp, accents.primary.copy(alpha = if (sel || isDragging) 0.5f else 0.15f), RoundedCornerShape(corners.small))
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
                        Icon(MorpheIcons.DragIndicator, contentDescription = stringResource(Res.string.drag_to_reorder), tint = accents.primary.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                    }
                    Text(layer.label, fontFamily = font, fontWeight = FontWeight.Normal, fontSize = 11.sp, color = if (sel) accents.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    if (sel) SymBtn(MorpheIcons.Delete, accents) { onDelete() }
                }
            }
        }
    }
}

@Composable
private fun LayerContentControls(
    layer: IconProject.Layer, accents: MorpheAccentColors, font: FontFamily,
    onReplaceImage: () -> Unit, onPickFont: () -> Unit, update: ((IconProject.Layer) -> IconProject.Layer) -> Unit,
) {
    when (val c = layer.content) {
        is IconProject.LayerContent.Image -> StudioButton(stringResource(Res.string.icon_studio_replace_icon_button), accents, font) { onReplaceImage() }
        is IconProject.LayerContent.Text -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label(stringResource(Res.string.icon_studio_text_label), font)
            TextInput(c.text, font, accents) { t -> update { it.copy(content = c.copy(text = t)) } }
            SwatchRow(c.color) { col -> update { it.copy(content = c.copy(color = col)) } }
            FontSelector(
                c.fontName, c.fontPath, font, accents,
                onSystemFont = { name -> update { it.copy(content = c.copy(fontName = name, fontPath = null)) } },
                onDefault = { update { it.copy(content = c.copy(fontName = null, fontPath = null)) } },
                onPickFont = onPickFont,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Toggle(stringResource(Res.string.icon_studio_toggle_bold), c.bold, accents, font) { update { it.copy(content = c.copy(bold = !c.bold)) } }
                Toggle(stringResource(Res.string.icon_studio_toggle_italic), c.italic, accents, font) { update { it.copy(content = c.copy(italic = !c.italic)) } }
                Toggle(stringResource(Res.string.icon_studio_toggle_underline), c.underline, accents, font) { update { it.copy(content = c.copy(underline = !c.underline)) } }
                Toggle(stringResource(Res.string.icon_studio_toggle_strike), c.strikethrough, accents, font) { update { it.copy(content = c.copy(strikethrough = !c.strikethrough)) } }
            }
            AdjustRow(stringResource(Res.string.icon_studio_adjust_spacing), c.letterSpacing, -0.05f..0.4f, 3, font, accents) { sp -> update { it.copy(content = c.copy(letterSpacing = sp)) } }
        }
        is IconProject.LayerContent.Shape -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label(stringResource(Res.string.icon_studio_tab_shape), font)
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
                    Toggle(label, c.kind == k, accents, font) { update { it.copy(content = c.copy(kind = k)) } }
                }
            }
            SwatchRow(c.color) { col -> update { it.copy(content = c.copy(color = col)) } }
        }
    }
}

@Composable
private fun FontSelector(
    fontName: String?, fontPath: String?, font: FontFamily, accents: MorpheAccentColors,
    onSystemFont: (String) -> Unit, onDefault: () -> Unit, onPickFont: () -> Unit,
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
        Toggle(stringResource(Res.string.icon_studio_add_file_button), false, accents, font) { onPickFont() }
    }
}

@Composable
private fun EffectSection(name: String, on: Boolean, accents: MorpheAccentColors, font: FontFamily, onToggle: (Boolean) -> Unit, body: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Label(name, font)
            Spacer(Modifier.weight(1f))
            Toggle(stringResource(Res.string.off), !on, accents, font, dense = true) { onToggle(false) }
            Toggle(stringResource(Res.string.on), on, accents, font, dense = true) { onToggle(true) }
        }
        body()
    }
}

@Composable
private fun BackgroundControls(project: IconProject, accents: MorpheAccentColors, font: FontFamily, onImportBg: () -> Unit, onChange: (IconProject) -> Unit) {
    val bg = project.background
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Label(stringResource(Res.string.icon_studio_background_label), font)
            Spacer(Modifier.weight(1f))
            Toggle(stringResource(Res.string.icon_studio_background_solid), bg is MorpheFill.Solid, accents, font) {
                onChange(project.copy(background = MorpheFill.Solid(0xFFFFFFFF.toInt())))
            }
            Toggle(stringResource(Res.string.icon_studio_background_gradient), bg is MorpheFill.Gradient, accents, font) {
                onChange(project.copy(background = MorpheFill.Gradient()))
            }
            Toggle(stringResource(Res.string.icon_studio_background_image), bg is MorpheFill.Image, accents, font) { onImportBg() }
        }
        Text(
            stringResource(Res.string.icon_studio_background_hint),
            fontFamily = font,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (bg) {
            is MorpheFill.Accent -> Unit
            is MorpheFill.Solid -> SwatchRow(bg.argb) {
                onChange(project.copy(background = MorpheFill.Solid(it)))
            }
            is MorpheFill.Gradient -> MorpheGradientEditor(bg, font) {
                onChange(project.copy(background = it))
            }
            is MorpheFill.Image -> Text(
                stringResource(Res.string.icon_studio_background_image_set_hint),
                fontFamily = font,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = accents.primary,
            )
        }
    }
}

@Composable
private fun SwatchRow(selected: Int, onPick: (Int) -> Unit) = MorpheSwatchRow(selected, onPick = onPick)

@Composable
private fun AdjustRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    decimals: Int,
    font: FontFamily,
    accents: MorpheAccentColors,
    onChange: (Float) -> Unit,
) = MorpheAdjustRow(label = label, value = value, range = range, font = font, decimals = decimals, onChange = onChange)

@Composable
private fun TextInput(value: String, font: FontFamily, accents: MorpheAccentColors, placeholder: String = "", onValue: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (text != value) text = value }
    val corners = LocalMorpheCorners.current
    Row(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(corners.small)).border(1.dp, accents.primary.copy(alpha = 0.25f), RoundedCornerShape(corners.small)).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.fillMaxWidth()) {
            if (text.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, fontSize = 11.sp, fontWeight = FontWeight.Normal, lineHeight = 14.sp, fontFamily = font, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
            BasicTextField(value = text, onValueChange = { text = it; onValue(it) }, singleLine = true, textStyle = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal, fontFamily = font, color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(accents.primary), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SymBtn(icon: ImageVector, accents: MorpheAccentColors, onClick: () -> Unit) {
    Box(Modifier.size(20.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = accents.primary, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun Toggle(
    text: String,
    active: Boolean,
    accents: MorpheAccentColors,
    font: FontFamily,
    dense: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) = MorpheChoiceChip(text = text, active = active, font = font, dense = dense, icon = icon, onClick = onClick)

private fun DrawScope.drawPreview(bitmap: ImageBitmap?) {
    if (bitmap == null) return
    val s = size.minDimension.toInt()
    drawImage(image = bitmap, dstOffset = IntOffset.Zero, dstSize = IntSize(s, s))
}

/**
 * A realistic status bar showing the actual monochrome notification silhouette (with the
 * clipped-slot guide) between the clock and the system icons. This is the output most
 * easily gotten wrong, mirroring how morphe-manager previews it.
 */
@Composable
private fun StatusBarPreview(silhouette: ImageBitmap?, font: FontFamily) {
    val corners = LocalMorpheCorners.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(corners.medium))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .border(1.dp, onSurface.copy(alpha = 0.1f), RoundedCornerShape(corners.medium))
        .padding(horizontal = 14.dp)) {
        Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("9:41", fontFamily = font, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = onSurface)
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
private fun Label(text: String, font: FontFamily) = Text(
    text, fontFamily = font, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

// Thin wrapper over the shared [MorpheButton] so existing studio call sites (which
// pass accents/font + a `filled` flag) keep working with the canonical styling.
@Composable
private fun StudioButton(text: String, accents: MorpheAccentColors, font: FontFamily, enabled: Boolean = true, filled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    MorpheButton(
        label = text,
        variant = if (filled) MorpheButtonVariant.PRIMARY else MorpheButtonVariant.GHOST,
        icon = icon,
        enabled = enabled,
        onClick = onClick,
    )
}
