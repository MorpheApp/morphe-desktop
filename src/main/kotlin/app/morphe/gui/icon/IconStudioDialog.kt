/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.icon

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.MorpheDropdown
import app.morphe.gui.ui.components.MorpheDropdownItem
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheAccentColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private const val RENDER = 320
private const val SNAP = 0.015f

private val MASKS: List<Pair<String, Shape>> = listOf(
    "CIRCLE" to CircleShape,
    "SQUIRCLE" to RoundedCornerShape(46),
    "ROUNDED" to RoundedCornerShape(28),
    "SQUARE" to RoundedCornerShape(8),
)

private val SWATCHES = listOf(
    0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFF0033.toInt(), 0xFF00E5FF.toInt(),
    0xFF1DE9B6.toInt(), 0xFFFFC400.toInt(), 0xFF7C4DFF.toInt(), 0xFFFF6D00.toInt(),
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
    val mono = LocalMorpheFont.current
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

    fun pickImage(): File? {
        val dialog = FileDialog(null as Frame?, "Select image", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name ->
            val n = name.lowercase()
            n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp")
        }
        dialog.isVisible = true
        val name = dialog.file ?: return null
        return File(dialog.directory, name)
    }

    fun copyIntoProject(src: File): String {
        val dir = File(IconExporter.projectDir(packageName), "source").apply { mkdirs() }
        val dest = File(dir, "${System.nanoTime()}-${src.name}")
        runCatching { src.copyTo(dest, overwrite = true) }
        return dest.absolutePath
    }

    fun warnFor(outcome: ForegroundPrep.Outcome): String? = when (outcome) {
        ForegroundPrep.Outcome.OPAQUE ->
            "This image has no transparency, so the themed & notification icons will be a solid block. Use a cut-out (transparent) PNG for those to show your logo's shape."
        ForegroundPrep.Outcome.UNREADABLE -> "Couldn't read this image to check its transparency."
        else -> null  // Already transparent. Nothing to warn about
    }

    fun addImageLayer() {
        val picked = pickImage() ?: return
        scope.launch {
            val prepared = withContext(Dispatchers.IO) { ForegroundPrep.prepare(picked, File(IconExporter.projectDir(packageName), "source")) }
            addLayer(IconProject.LayerContent.Image(prepared.path))
            importWarning = warnFor(prepared.outcome)
        }
    }

    fun replaceSelectedImage() {
        val sel = selected ?: return
        if (sel.content !is IconProject.LayerContent.Image) return
        val picked = pickImage() ?: return
        scope.launch {
            val prepared = withContext(Dispatchers.IO) { ForegroundPrep.prepare(picked, File(IconExporter.projectDir(packageName), "source")) }
            updateSelected { it.copy(content = IconProject.LayerContent.Image(prepared.path)) }
            importWarning = warnFor(prepared.outcome)
        }
    }

    fun importBackground() {
        val picked = pickImage() ?: return
        scope.launch {
            val path = withContext(Dispatchers.IO) { copyIntoProject(picked) }
            commit(project.copy(background = IconProject.Background.Image(path)))
        }
    }

    fun pickAndSetFont() {
        if (selected?.content !is IconProject.LayerContent.Text) return
        val dialog = FileDialog(null as Frame?, "Select font", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name -> val n = name.lowercase(); n.endsWith(".ttf") || n.endsWith(".otf") }
        dialog.isVisible = true
        val name = dialog.file ?: return
        val picked = File(dialog.directory, name)
        scope.launch {
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
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, accents.secondary.copy(alpha = 0.25f)),
            modifier = Modifier.width(800.dp).heightIn(max = 660.dp).padding(4.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("ICON STUDIO", fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 3.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    StudioButton("UNDO", accents, mono, enabled = histIndex > 0, filled = false, icon = Icons.AutoMirrored.Filled.Undo) { undo() }
                    Spacer(Modifier.width(6.dp))
                    StudioButton("REDO", accents, mono, enabled = histIndex < history.lastIndex, filled = false, icon = Icons.AutoMirrored.Filled.Redo) { redo() }
                }
                Text(
                    "An adaptive icon = a FOREGROUND (your logo) over a BACKGROUND (fills the tile). Launchers mask the two together into any shape.",
                    fontFamily = mono, fontSize = 10.sp, lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                    Label("TEMPLATES", mono)
                    Toggle("GRADIENT", selectedTemplate == "GRADIENT", accents, mono) { applyTemplate("GRADIENT", templateGradient()) }
                    Toggle("BADGE", selectedTemplate == "BADGE", accents, mono) { applyTemplate("BADGE", templateBadge()) }
                    Toggle("SHAPE", selectedTemplate == "SHAPE", accents, mono) { applyTemplate("SHAPE", templateShape()) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.weight(1f, fill = false)) {
                    // ── Previews: adaptive + monochrome side by side, mask gallery, status bar ──
                    Column(Modifier.width(previewColWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Adaptive icon — the full-colour result; interactive (drag the selected layer).
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Label("ADAPTIVE", mono)
                                Canvas(
                                    Modifier.size(previewDp).clip(MASKS[selectedMask].second)
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
                                    val guide = accents.secondary.copy(alpha = 0.5f)
                                    drawCircle(guide, radius = size.minDimension * 0.33f, style = Stroke(width = 2f))
                                    drawCircle(guide.copy(alpha = 0.3f), radius = size.minDimension * 0.21f, style = Stroke(width = 1.5f))
                                    if (dragging) project.layers.firstOrNull { it.id == selectedId }?.let { sel ->
                                        if (sel.offsetX == 0f) drawLine(accents.secondary, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), strokeWidth = 1.5f)
                                        if (sel.offsetY == 0f) drawLine(accents.secondary, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 1.5f)
                                    }
                                }
                            }
                            // Monochrome / themed icon — Android 13+ launchers tint the foreground silhouette.
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Label("MONOCHROME", mono)
                                Canvas(Modifier.size(previewDp).clip(MASKS[selectedMask].second).background(Color(0xFF15171E))) {
                                    silhouette?.let { drawImage(it, dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.toInt(), size.height.toInt()), colorFilter = ColorFilter.tint(accents.secondary)) }
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Label("PREVIEW SHAPES", mono)
                        Text(
                            "Each launcher picks its own shape (Not a choice). Tap to preview how it'll be cropped.",
                            fontFamily = mono, fontSize = 8.sp, lineHeight = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MASKS.forEachIndexed { i, (name, shape) ->
                                val sel = i == selectedMask
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Canvas(Modifier.size(48.dp).clip(shape).border(if (sel) 2.dp else 1.dp, accents.secondary.copy(alpha = if (sel) 0.8f else 0.25f), shape).clickable { selectedMask = i }) {
                                        drawPreview(preview)
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(name, fontFamily = mono, fontSize = 7.sp, letterSpacing = 0.5.sp, color = if (sel) accents.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Label("NOTIFICATION / STATUS BAR", mono)
                        Spacer(Modifier.height(6.dp))
                        StatusBarPreview(notifSilhouette, mono)
                    }

                    // ── Controls (scrollable, with scrollbar) ──
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        val scrollState = rememberScrollState()
                        Column(Modifier.fillMaxWidth().verticalScroll(scrollState).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Import warning lives inside the scrollable controls so it never
                        // steals height from the (fixed) preview column and squishes it.
                        importWarning?.let { msg ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFE0504D).copy(alpha = 0.12f))
                                    .border(1.dp, Color(0xFFE0504D).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top,
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE0504D), modifier = Modifier.size(14.dp))
                                Text(msg, fontFamily = mono, fontSize = 9.sp, lineHeight = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp).clickable { importWarning = null })
                            }
                        }
                        LayersPanel(project.layers, selectedId, accents, mono, onSelect = { selectedId = it }, onAddImage = { addImageLayer() }, onAddText = { addLayer(IconProject.LayerContent.Text()) }, onAddShape = { addLayer(IconProject.LayerContent.Shape()) }, onReorder = { f, t -> reorderDisplay(f, t) }, onDelete = { deleteSelected() })

                        BackgroundControls(project, accents, mono, onImportBg = { importBackground() }) { commit(it) }

                        selected?.let { layer ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Label("LAYER", mono)
                                    Spacer(Modifier.weight(1f))
                                    Toggle("DUPLICATE", false, accents, mono) { duplicateSelected() }
                                }
                                TextInput(layer.name ?: "", mono, accents, placeholder = "Layer name") { n -> updateSelected { it.copy(name = n.ifBlank { null }) } }
                                AdjustRow("Opacity", layer.opacity, 0f..1f, 2, mono, accents) { o -> updateSelected { it.copy(opacity = o) } }
                            }
                            LayerContentControls(layer, accents, mono, onReplaceImage = { replaceSelectedImage() }, onPickFont = { pickAndSetFont() }) { updateSelected(it) }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Label("TRANSFORM", mono)
                                AdjustRow("Scale", layer.scale, 0.1f..3f, 2, mono, accents) { v -> updateSelected { it.copy(scale = v) } }
                                AdjustRow("Rotation", layer.rotationDeg, -180f..180f, 0, mono, accents) { v -> updateSelected { it.copy(rotationDeg = v) } }
                                StudioButton("RESET POSITION", accents, mono, filled = false) { updateSelected { it.copy(offsetX = 0f, offsetY = 0f, rotationDeg = 0f) } }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Label("COLOR", mono)
                                AdjustRow("Hue", layer.hueShiftDeg, -180f..180f, 0, mono, accents) { v -> updateSelected { it.copy(hueShiftDeg = v) } }
                                AdjustRow("Saturation", layer.saturation, 0f..2f, 2, mono, accents) { v -> updateSelected { it.copy(saturation = v) } }
                                AdjustRow("Brightness", layer.brightness, 0f..2f, 2, mono, accents) { v -> updateSelected { it.copy(brightness = v) } }
                            }
                            EffectSection("SHADOW", layer.shadow != null, accents, mono, onToggle = { on -> updateSelected { it.copy(shadow = if (on) (it.shadow ?: IconProject.Shadow()) else null) } }) {
                                layer.shadow?.let { sh ->
                                    AdjustRow("Y offset", sh.offsetY, -0.15f..0.15f, 3, mono, accents) { v -> updateSelected { it.copy(shadow = sh.copy(offsetY = v)) } }
                                    AdjustRow("Blur", sh.blur, 0f..0.1f, 3, mono, accents) { v -> updateSelected { it.copy(shadow = sh.copy(blur = v)) } }
                                    AdjustRow("Opacity", sh.alpha, 0f..1f, 2, mono, accents) { v -> updateSelected { it.copy(shadow = sh.copy(alpha = v)) } }
                                }
                            }
                            EffectSection("GLOW", layer.glow != null, accents, mono, onToggle = { on -> updateSelected { it.copy(glow = if (on) (it.glow ?: IconProject.Glow()) else null) } }) {
                                layer.glow?.let { gl ->
                                    SwatchRow(gl.color) { c -> updateSelected { it.copy(glow = gl.copy(color = c)) } }
                                    AdjustRow("Blur", gl.blur, 0f..0.1f, 3, mono, accents) { v -> updateSelected { it.copy(glow = gl.copy(blur = v)) } }
                                    AdjustRow("Opacity", gl.alpha, 0f..1f, 2, mono, accents) { v -> updateSelected { it.copy(glow = gl.copy(alpha = v)) } }
                                }
                            }
                            EffectSection("STROKE", layer.stroke != null, accents, mono, onToggle = { on -> updateSelected { it.copy(stroke = if (on) (it.stroke ?: IconProject.Stroke()) else null) } }) {
                                layer.stroke?.let { st ->
                                    SwatchRow(st.color) { c -> updateSelected { it.copy(stroke = st.copy(color = c)) } }
                                    AdjustRow("Width", st.width, 0f..0.04f, 3, mono, accents) { v -> updateSelected { it.copy(stroke = st.copy(width = v)) } }
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
                    StudioButton("CANCEL", accents, mono, filled = false) { onDismiss() }
                    Spacer(Modifier.width(8.dp))
                    StudioButton(if (busy) "SAVING…" else "SAVE ICON", accents, mono, enabled = project.layers.isNotEmpty() && !busy) { save() }
                }
            }
        }
    }
}

@Composable
private fun LayersPanel(
    layers: List<IconProject.Layer>, selectedId: String?, accents: MorpheAccentColors, mono: FontFamily,
    onSelect: (String) -> Unit, onAddImage: () -> Unit, onAddText: () -> Unit, onAddShape: () -> Unit,
    onReorder: (Int, Int) -> Unit, onDelete: () -> Unit,
) {
    val display = layers.reversed()  // top of stack first
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragDy by remember { mutableStateOf(0f) }
    val rowH = with(LocalDensity.current) { 34.dp.toPx() }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Label("FOREGROUND", mono)
            Spacer(Modifier.weight(1f))
            Toggle("+ IMG", false, accents, mono) { onAddImage() }
            Toggle("+ TEXT", false, accents, mono) { onAddText() }
            Toggle("+ SHAPE", false, accents, mono) { onAddShape() }
        }
        Text("Your logo, layered in the safe zone. Drives the themed & notification icons.", fontFamily = mono, fontSize = 8.sp, lineHeight = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        if (display.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(accents.secondary.copy(alpha = 0.08f))
                    .border(1.dp, accents.secondary.copy(alpha = 0.25f), RoundedCornerShape(6.dp)).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = accents.secondary.copy(alpha = 0.8f), modifier = Modifier.size(13.dp))
                Text(
                    "No foreground yet — add a layer (+ IMG / TEXT / SHAPE). Themed & notification icons come from the foreground, so a background-only icon shows nothing there (we fall back to the whole icon).",
                    fontFamily = mono, fontSize = 9.sp, lineHeight = 12.sp, color = accents.secondary.copy(alpha = 0.85f),
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
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isDragging) accents.secondary.copy(alpha = 0.25f) else if (sel) accents.secondary.copy(alpha = 0.15f) else Color.Transparent)
                        .border(1.dp, accents.secondary.copy(alpha = if (sel || isDragging) 0.5f else 0.15f), RoundedCornerShape(4.dp))
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
                        Icon(Icons.Default.DragIndicator, contentDescription = "Drag to reorder", tint = accents.secondary.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                    }
                    Text(layer.label, fontFamily = mono, fontSize = 10.sp, color = if (sel) accents.secondary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    if (sel) SymBtn(Icons.Default.Delete, accents) { onDelete() }
                }
            }
        }
    }
}

@Composable
private fun LayerContentControls(
    layer: IconProject.Layer, accents: MorpheAccentColors, mono: FontFamily,
    onReplaceImage: () -> Unit, onPickFont: () -> Unit, update: ((IconProject.Layer) -> IconProject.Layer) -> Unit,
) {
    when (val c = layer.content) {
        is IconProject.LayerContent.Image -> StudioButton("REPLACE IMAGE", accents, mono) { onReplaceImage() }
        is IconProject.LayerContent.Text -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label("TEXT", mono)
            TextInput(c.text, mono, accents) { t -> update { it.copy(content = c.copy(text = t)) } }
            SwatchRow(c.color) { col -> update { it.copy(content = c.copy(color = col)) } }
            FontSelector(
                c.fontName, c.fontPath, mono, accents,
                onSystemFont = { name -> update { it.copy(content = c.copy(fontName = name, fontPath = null)) } },
                onDefault = { update { it.copy(content = c.copy(fontName = null, fontPath = null)) } },
                onPickFont = onPickFont,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Toggle("BOLD", c.bold, accents, mono) { update { it.copy(content = c.copy(bold = !c.bold)) } }
                Toggle("ITALIC", c.italic, accents, mono) { update { it.copy(content = c.copy(italic = !c.italic)) } }
                Toggle("UNDERLINE", c.underline, accents, mono) { update { it.copy(content = c.copy(underline = !c.underline)) } }
                Toggle("STRIKE", c.strikethrough, accents, mono) { update { it.copy(content = c.copy(strikethrough = !c.strikethrough)) } }
            }
            AdjustRow("Spacing", c.letterSpacing, -0.05f..0.4f, 3, mono, accents) { sp -> update { it.copy(content = c.copy(letterSpacing = sp)) } }
        }
        is IconProject.LayerContent.Shape -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label("SHAPE", mono)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IconProject.ShapeKind.entries.forEach { k ->
                    Toggle(k.name, c.kind == k, accents, mono) { update { it.copy(content = c.copy(kind = k)) } }
                }
            }
            SwatchRow(c.color) { col -> update { it.copy(content = c.copy(color = col)) } }
        }
    }
}

@Composable
private fun FontSelector(
    fontName: String?, fontPath: String?, mono: FontFamily, accents: MorpheAccentColors,
    onSystemFont: (String) -> Unit, onDefault: () -> Unit, onPickFont: () -> Unit,
) {
    val fonts = remember { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList() }
    val label = when {
        fontPath != null -> File(fontPath).name
        fontName != null -> fontName
        else -> "Default font"
    }
    val items = buildList {
        add(MorpheDropdownItem("Default font", onDefault))
        fonts.forEach { f -> add(MorpheDropdownItem(f) { onSystemFont(f) }) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        MorpheDropdown(label, items, modifier = Modifier.weight(1f), searchable = true)
        Toggle("+ FILE", false, accents, mono) { onPickFont() }
    }
}

@Composable
private fun EffectSection(name: String, on: Boolean, accents: MorpheAccentColors, mono: FontFamily, onToggle: (Boolean) -> Unit, body: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Label(name, mono)
            Spacer(Modifier.weight(1f))
            Toggle("OFF", !on, accents, mono, dense = true) { onToggle(false) }
            Toggle("ON", on, accents, mono, dense = true) { onToggle(true) }
        }
        body()
    }
}

@Composable
private fun BackgroundControls(project: IconProject, accents: MorpheAccentColors, mono: FontFamily, onImportBg: () -> Unit, onChange: (IconProject) -> Unit) {
    val bg = project.background
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Label("BACKGROUND", mono)
            Spacer(Modifier.weight(1f))
            Toggle("SOLID", bg is IconProject.Background.Solid, accents, mono) { onChange(project.copy(background = IconProject.Background.Solid(0xFFFFFFFF.toInt()))) }
            Toggle("GRAD", bg is IconProject.Background.Gradient, accents, mono) { onChange(project.copy(background = IconProject.Background.Gradient())) }
            Toggle("IMAGE", bg is IconProject.Background.Image, accents, mono) { onImportBg() }
        }
        Text("Fills the whole tile, behind the foreground.", fontFamily = mono, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        when (bg) {
            is IconProject.Background.Solid -> SwatchRow(bg.argb) { onChange(project.copy(background = IconProject.Background.Solid(it))) }
            is IconProject.Background.Gradient -> {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Toggle("LINEAR", bg.type == IconProject.GradientType.LINEAR, accents, mono) { onChange(project.copy(background = bg.copy(type = IconProject.GradientType.LINEAR))) }
                    Toggle("RADIAL", bg.type == IconProject.GradientType.RADIAL, accents, mono) { onChange(project.copy(background = bg.copy(type = IconProject.GradientType.RADIAL))) }
                    Toggle("CONIC", bg.type == IconProject.GradientType.CONIC, accents, mono) { onChange(project.copy(background = bg.copy(type = IconProject.GradientType.CONIC))) }
                }
                bg.stops.forEachIndexed { i, stop ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ColorChip(stop.argb) { c -> onChange(project.copy(background = bg.copy(stops = bg.stops.mapIndexed { j, s -> if (j == i) s.copy(argb = c) else s }))) }
                        Slider(value = stop.position, onValueChange = { p -> onChange(project.copy(background = bg.copy(stops = bg.stops.mapIndexed { j, s -> if (j == i) s.copy(position = p) else s }))) }, valueRange = 0f..1f, modifier = Modifier.weight(1f).height(20.dp))
                        if (bg.stops.size > 2) SymBtn(Icons.Default.Delete, accents) { onChange(project.copy(background = bg.copy(stops = bg.stops.filterIndexed { j, _ -> j != i }))) }
                    }
                }
                Toggle("+ STOP", false, accents, mono) { onChange(project.copy(background = bg.copy(stops = bg.stops + IconProject.Background.Stop(0.5f, 0xFFFFFFFF.toInt())))) }
                if (bg.type != IconProject.GradientType.RADIAL) AdjustRow("Angle", bg.angleDeg, 0f..360f, 0, mono, accents) { onChange(project.copy(background = bg.copy(angleDeg = it))) }
            }
            is IconProject.Background.Image -> Text("image background set — click IMAGE to replace", fontFamily = mono, fontSize = 9.sp, color = accents.secondary.copy(alpha = 0.7f))
        }
    }
}

/** A single colour swatch that opens the full picker popup, for gradient stops. */
@Composable
private fun ColorChip(argb: Int, onChange: (Int) -> Unit) {
    val accents = LocalMorpheAccents.current
    val mono = LocalMorpheFont.current
    var open by remember { mutableStateOf(false) }
    val yOff = with(LocalDensity.current) { 26.dp.roundToPx() }
    Box {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)).background(Color(argb)).border(1.dp, accents.secondary.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).clickable { open = true })
        if (open) {
            Popup(alignment = Alignment.TopStart, offset = IntOffset(0, yOff), onDismissRequest = { open = false }, properties = PopupProperties(focusable = true)) {
                ColorPickerCard(argb, accents, mono, onChange)
            }
        }
    }
}

@Composable
private fun SwatchRow(selected: Int, onPick: (Int) -> Unit) {
    val accents = LocalMorpheAccents.current
    val mono = LocalMorpheFont.current
    var pickerOpen by remember { mutableStateOf(false) }
    val yOff = with(LocalDensity.current) { 26.dp.roundToPx() }

    @Composable
    fun swatch(argb: Int) {
        val isSel = selected == argb
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)).background(Color(argb)).border(if (isSel) 2.dp else 1.dp, if (isSel) accents.secondary else Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).clickable { onPick(argb) })
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SWATCHES.forEach { swatch(it) }
        CustomSwatches.colors.forEach { swatch(it) }
        // Palette chip opens the full picker (wheel + hex + saved colours).
        Box {
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)).border(1.dp, accents.secondary.copy(alpha = 0.4f), RoundedCornerShape(4.dp)).clickable { pickerOpen = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Palette, contentDescription = "Custom colour", tint = accents.secondary, modifier = Modifier.size(13.dp)) }
            if (pickerOpen) {
                Popup(alignment = Alignment.TopStart, offset = IntOffset(0, yOff), onDismissRequest = { pickerOpen = false }, properties = PopupProperties(focusable = true)) {
                    ColorPickerCard(selected, accents, mono, onPick)
                }
            }
        }
    }
}

@Composable
private fun ColorPickerCard(argb: Int, accents: MorpheAccentColors, mono: FontFamily, onPick: (Int) -> Unit) {
    val init = remember { argbToHsva(argb) }
    var h by remember { mutableStateOf(init[0]) }
    var s by remember { mutableStateOf(init[1]) }
    var v by remember { mutableStateOf(init[2]) }
    var a by remember { mutableStateOf(init[3]) }
    var hex by remember { mutableStateOf(argbToHex(argb)) }

    fun emit() { val out = hsvaToArgb(h, s, v, a); hex = argbToHex(out); onPick(out) }
    fun setFrom(c: Int) { val q = argbToHsva(c); h = q[0]; s = q[1]; v = q[2]; a = q[3]; hex = argbToHex(c); onPick(c) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accents.secondary.copy(alpha = 0.4f)),
        shadowElevation = 8.dp,
        modifier = Modifier.width(220.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorWheel(h, s) { nh, ns -> h = nh; s = ns; emit() }
            PickerSlider("V", v, mono) { v = it; emit() }
            PickerSlider("A", a, mono) { a = it; emit() }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(4.dp)).background(Color(hsvaToArgb(h, s, v, a))).border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp)))
                Row(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(4.dp)).border(1.dp, accents.secondary.copy(alpha = 0.25f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = hex,
                        onValueChange = { hex = it; parseHex(it)?.let { p -> setFrom(p) } },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontFamily = mono, color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(accents.secondary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SAVED", fontFamily = mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                Spacer(Modifier.weight(1f))
                Toggle("+ SAVE", false, accents, mono) { if (!CustomSwatches.isFull) CustomSwatches.add(hsvaToArgb(h, s, v, a)) }
            }
            if (CustomSwatches.colors.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CustomSwatches.colors.forEach { c ->
                        Box(Modifier.size(24.dp)) {
                            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(Color(c)).border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).clickable { setFrom(c) })
                            Box(Modifier.align(Alignment.TopEnd).size(11.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).clickable { CustomSwatches.remove(c) }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(9.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerSlider(label: String, value: Float, mono: FontFamily, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontFamily = mono, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(10.dp))
        Slider(value = value, onValueChange = onChange, valueRange = 0f..1f, modifier = Modifier.weight(1f).height(20.dp))
    }
}

/** HSV colour wheel: angle = hue, radius = saturation (brightness is a separate slider). */
@Composable
private fun ColorWheel(hue: Float, sat: Float, onChange: (Float, Float) -> Unit) {
    val hueColors = remember { (0..360 step 30).map { Color.hsv(it.toFloat(), 1f, 1f) } }
    Canvas(
        Modifier.fillMaxWidth().height(150.dp)
            .pointerInput(Unit) { detectTapGestures { p -> val (nh, ns) = wheelPick(p, size.width.toFloat(), size.height.toFloat()); onChange(nh, ns) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> val (nh, ns) = wheelPick(change.position, size.width.toFloat(), size.height.toFloat()); onChange(nh, ns) } },
    ) {
        val r = size.minDimension / 2f
        val c = center
        // Always full-brightness hue/saturation wheel — brightness is the separate V
        // slider, so the wheel never goes dark (it used to blank to black at value 0).
        drawCircle(Brush.sweepGradient(hueColors, center = c), radius = r, center = c)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center = c, radius = r), radius = r, center = c)
        val ang = hue * 2f * PI.toFloat()
        val ind = Offset(c.x + cos(ang) * sat * r, c.y + sin(ang) * sat * r)
        drawCircle(Color.White, radius = 6f, center = ind, style = Stroke(width = 2f))
        drawCircle(Color.Black.copy(alpha = 0.4f), radius = 6f, center = ind, style = Stroke(width = 1f))
    }
}

private fun wheelPick(pos: Offset, w: Float, h: Float): Pair<Float, Float> {
    val cx = w / 2f; val cy = h / 2f; val r = minOf(w, h) / 2f
    val dx = pos.x - cx; val dy = pos.y - cy
    val dist = hypot(dx, dy).coerceAtMost(r)
    var hue = atan2(dy, dx) / (2f * PI.toFloat()); if (hue < 0) hue += 1f
    return hue to (dist / r).coerceIn(0f, 1f)
}

// ── Colour helpers (ARGB ↔ HSVA ↔ hex) ──

private fun argbToHsva(argb: Int): FloatArray {
    val hsb = java.awt.Color.RGBtoHSB((argb ushr 16) and 0xFF, (argb ushr 8) and 0xFF, argb and 0xFF, null)
    return floatArrayOf(hsb[0], hsb[1], hsb[2], ((argb ushr 24) and 0xFF) / 255f)
}

private fun hsvaToArgb(h: Float, s: Float, v: Float, a: Float): Int {
    val rgb = java.awt.Color.HSBtoRGB(h, s, v) and 0xFFFFFF
    return ((a * 255).roundToInt().coerceIn(0, 255) shl 24) or rgb
}

private fun argbToHex(argb: Int): String = "#%08X".format(argb)

private fun parseHex(s: String): Int? {
    val h = s.trim().removePrefix("#")
    val value = h.toLongOrNull(16) ?: return null
    return when (h.length) {
        6 -> (0xFF000000L or value).toInt()
        8 -> value.toInt()
        else -> null
    }
}

@Composable
private fun AdjustRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, decimals: Int, mono: FontFamily, accents: MorpheAccentColors, onChange: (Float) -> Unit) {
    Column {
        Text(label, fontFamily = mono, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f).height(22.dp))
            NumberField(value, decimals, range, mono, accents, onChange)
        }
    }
}

@Composable
private fun NumberField(value: Float, decimals: Int, range: ClosedFloatingPointRange<Float>, mono: FontFamily, accents: MorpheAccentColors, onValue: (Float) -> Unit) {
    val step = when (decimals) { 0 -> 1f; 1 -> 0.1f; 2 -> 0.05f; else -> 0.005f }
    fun fmt(v: Float) = if (decimals == 0) "%.0f".format(v) else "%.${decimals}f".format(v)
    var text by remember { mutableStateOf(fmt(value)) }
    LaunchedEffect(value) { if (text.trim().toFloatOrNull() != value) text = fmt(value) }
    Row(Modifier.width(66.dp).height(26.dp).clip(RoundedCornerShape(4.dp)).border(1.dp, accents.secondary.copy(alpha = 0.25f), RoundedCornerShape(4.dp)), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = text,
            onValueChange = { text = it; it.trim().toFloatOrNull()?.let { v -> onValue(v.coerceIn(range)) } },
            singleLine = true,
            textStyle = TextStyle(fontSize = 9.sp, lineHeight = 12.sp, fontFamily = mono, color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(accents.secondary),
            modifier = Modifier.weight(1f).padding(start = 6.dp),
        )
        Column(Modifier.fillMaxHeight().width(15.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth().clickable { onValue((value + step).coerceIn(range)) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ArrowDropUp, contentDescription = null, tint = accents.secondary.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth().clickable { onValue((value - step).coerceIn(range)) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = accents.secondary.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun TextInput(value: String, mono: FontFamily, accents: MorpheAccentColors, placeholder: String = "", onValue: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (text != value) text = value }
    Row(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(4.dp)).border(1.dp, accents.secondary.copy(alpha = 0.25f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.fillMaxWidth()) {
            if (text.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, fontSize = 11.sp, lineHeight = 14.sp, fontFamily = mono, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            }
            BasicTextField(value = text, onValueChange = { text = it; onValue(it) }, singleLine = true, textStyle = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontFamily = mono, color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(accents.secondary), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SymBtn(icon: ImageVector, accents: MorpheAccentColors, onClick: () -> Unit) {
    Box(Modifier.size(20.dp).clip(RoundedCornerShape(3.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = accents.secondary, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun Toggle(text: String, active: Boolean, accents: MorpheAccentColors, mono: FontFamily, dense: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(if (active) accents.secondary.copy(alpha = 0.2f) else Color.Transparent).border(1.dp, accents.secondary.copy(alpha = if (active) 0.6f else 0.2f), RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = if (dense) 2.dp else 4.dp)) {
        Text(text, fontFamily = mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = if (active) accents.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

private fun DrawScope.drawPreview(bitmap: ImageBitmap?) {
    if (bitmap == null) return
    val s = size.minDimension.toInt()
    drawImage(image = bitmap, dstOffset = IntOffset.Zero, dstSize = IntSize(s, s))
}

/**
 * A realistic status bar showing the actual monochrome notification silhouette (with the
 * clipped-slot guide) between the clock and the system icons — this is the output most
 * easily gotten wrong, mirroring how morphe-manager previews it.
 */
@Composable
private fun StatusBarPreview(silhouette: ImageBitmap?, mono: FontFamily) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 14.dp)) {
        Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("9:41", fontFamily = mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = onSurface)
            Canvas(Modifier.size(18.dp)) {
                drawRect(color = onSurface.copy(alpha = 0.3f), topLeft = Offset(0.5f, 0.5f), size = Size(size.width - 1f, size.height - 1f), style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))))
                silhouette?.let { drawImage(it, dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.toInt(), size.height.toInt()), colorFilter = ColorFilter.tint(onSurface)) }
            }
        }
        Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SignalCellular4Bar, contentDescription = null, tint = onSurface, modifier = Modifier.size(14.dp))
            Icon(Icons.Default.Wifi, contentDescription = null, tint = onSurface, modifier = Modifier.size(14.dp))
            Icon(Icons.Default.BatteryFull, contentDescription = null, tint = onSurface, modifier = Modifier.size(14.dp))
        }
    }
}

// ── Templates (starting points; the user then adds/edits layers) ──

private fun templateGradient() = IconProject(
    background = IconProject.Background.Gradient(
        stops = listOf(IconProject.Background.Stop(0f, 0xFF00E5FF.toInt()), IconProject.Background.Stop(1f, 0xFF7C4DFF.toInt())),
        type = IconProject.GradientType.RADIAL,
    ),
)

private fun templateBadge() = IconProject(
    background = IconProject.Background.Solid(0xFF1DE9B6.toInt()),
    layers = listOf(IconProject.Layer(System.nanoTime().toString(), IconProject.LayerContent.Text("A", 0xFF102027.toInt(), true), scale = 0.6f)),
)

private fun templateShape() = IconProject(
    background = IconProject.Background.Gradient(
        stops = listOf(IconProject.Background.Stop(0f, 0xFFFF6D00.toInt()), IconProject.Background.Stop(1f, 0xFFFF0033.toInt())),
        type = IconProject.GradientType.RADIAL,
    ),
    layers = listOf(IconProject.Layer(System.nanoTime().toString(), IconProject.LayerContent.Shape(IconProject.ShapeKind.CIRCLE, 0xFFFFFFFF.toInt()), scale = 0.5f)),
)

@Composable
private fun Label(text: String, mono: FontFamily) = Text(
    text, fontFamily = mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
)

// Thin wrapper over the shared [MorpheButton] so existing studio call sites (which
// pass accents/mono + a `filled` flag) keep working with the canonical styling.
@Composable
private fun StudioButton(text: String, accents: MorpheAccentColors, mono: FontFamily, enabled: Boolean = true, filled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    MorpheButton(
        label = text,
        variant = if (filled) MorpheButtonVariant.PRIMARY else MorpheButtonVariant.GHOST,
        icon = icon,
        enabled = enabled,
        onClick = onClick,
    )
}
