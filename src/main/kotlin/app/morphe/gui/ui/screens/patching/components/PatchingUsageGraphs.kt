/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patching.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.MorphePanel
import app.morphe.gui.ui.screens.patching.IoUsage
import app.morphe.gui.ui.theme.MorpheMonoFamily
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.currentLocale
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun HeapUsageGraph(
    samples: List<Int>,
    maxHeapMb: Int,
    modifier: Modifier = Modifier
) {
    val barColor = MorpheTheme.colors.primary
    val warnColor = MorpheTheme.colors.danger
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    MorphePanel(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GraphHeaderDotLabel(
                    text = stringResource(Res.string.patching_graph_memory_usage),
                    dotColor = barColor,
                )

                if (maxHeapMb > 0) {
                    val usedMb = samples.lastOrNull() ?: 0
                    val percent = ((usedMb.toFloat() / maxHeapMb) * 100).toInt().coerceIn(0, 100)
                    Text(
                        text = stringResource(Res.string.patching_graph_memory_detail, percent, maxHeapMb),
                        style = MorpheTheme.typography.codeExtraSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            Text(
                text = "${samples.lastOrNull() ?: 0} MB",
                style = MorpheTheme.typography.titleMedium.copy(fontFamily = MorpheMonoFamily, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            val slotCount = 60
            val padded = List(slotCount - samples.size) { 0 } + samples.takeLast(slotCount)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                if (padded.isEmpty()) return@Canvas
                val (gap, barWidth, corner, minimumHeight) = calculateTimelineBarMetrics(slotCount)
                val redThresholdForMaxColor = 1.0f
                val smoothStart = 0.7f
                val memoryFractionRollingAverageSamples = 3
                var memoryFractionAverage = 0.0

                padded.forEachIndexed { index, sample ->
                    val memoryUsage = if (maxHeapMb > 0) {
                        (sample / maxHeapMb.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    memoryFractionAverage =
                        (memoryFractionAverage * memoryFractionRollingAverageSamples + memoryUsage) /
                                (memoryFractionRollingAverageSamples + 1)

                    val t = if (memoryFractionAverage <= smoothStart) {
                        0f
                    } else {
                        ((memoryFractionAverage - smoothStart) / (redThresholdForMaxColor - smoothStart))
                            .coerceIn(0.0, 1.0)
                            .toFloat()
                    }

                    val color = lerp(barColor, warnColor, t).copy(alpha = 0.75f)
                    val activeFraction = maxOf(memoryUsage, 0.04f)
                    val left = index * (barWidth + gap)

                    drawRoundRect(
                        color = trackColor.copy(alpha = 0.12f),
                        topLeft = Offset(left, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = corner
                    )

                    if (sample > 0) {
                        val barHeight = (size.height * activeFraction).coerceAtLeast(minimumHeight)
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(left, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = corner
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun IoUsageGraph(
    samples: List<IoUsage>,
    modifier: Modifier = Modifier
) {
    val accentColor = MorpheTheme.colors.secondary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    val current = samples.lastOrNull()
    val locale = currentLocale()
    val formatRate = { kbPerSec: Int -> 
        FormatUtils.formatTransferRate(kbPerSec, locale)
    }

    MorphePanel(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GraphHeaderDotLabel(
                    text = stringResource(Res.string.patching_graph_storage_io),
                    dotColor = accentColor,
                )

                val readStr = current?.let { formatRate(it.readKbPerSec) } ?: formatRate(0)
                val writeStr = current?.let { formatRate(it.writeKbPerSec) } ?: formatRate(0)
                Text(
                    text = "↓ $readStr  ↑ $writeStr",
                    style = MorpheTheme.typography.codeExtraSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            Text(
                text = current?.let { formatRate(it.totalKbPerSec) } ?: formatRate(0),
                style = MorpheTheme.typography.titleMedium.copy(fontFamily = MorpheMonoFamily, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            val slotCount = 60

            val peak = (samples.maxOfOrNull { it.totalKbPerSec } ?: 0).toFloat()
            val fractions = samples.map { if (peak > 0f) (it.totalKbPerSec / peak).coerceIn(0f, 1f) else 0f }
            val padded = List(slotCount - fractions.size) { 0f } + fractions.takeLast(slotCount)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                if (padded.isEmpty()) return@Canvas
                val (gap, barWidth, corner, minimumHeight) = calculateTimelineBarMetrics(slotCount)

                padded.forEachIndexed { index, fraction ->
                    val left = index * (barWidth + gap)

                    drawRoundRect(
                        color = trackColor.copy(alpha = 0.12f),
                        topLeft = Offset(left, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = corner
                    )

                    if (fraction > 0f) {
                        val barHeight = (size.height * maxOf(fraction, 0.04f)).coerceAtLeast(minimumHeight)
                        drawRoundRect(
                            color = accentColor,
                            topLeft = Offset(left, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = corner
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CpuUsageGraph(
    coreLoads: List<Int>,
    modifier: Modifier = Modifier,
    coreCount: Int = 0
) {
    val accentColor = MaterialTheme.colorScheme.tertiary
    val warnColor = MorpheTheme.colors.danger
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    val average = if (coreLoads.isNotEmpty()) coreLoads.average().toInt() else 0
    val totalCores = if (coreCount > 0) coreCount else if (coreLoads.isNotEmpty()) coreLoads.size else Runtime.getRuntime().availableProcessors()

    MorphePanel(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GraphHeaderDotLabel(
                    text = stringResource(Res.string.patching_graph_cpu_usage),
                    dotColor = accentColor,
                )

                Text(
                    text = pluralStringResource(Res.plurals.patching_graph_cpu_cores_detail, totalCores, totalCores),
                    style = MorpheTheme.typography.codeExtraSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            Text(
                text = "$average%",
                style = MorpheTheme.typography.titleMedium.copy(fontFamily = MorpheMonoFamily, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            val slotCount = if (coreLoads.isNotEmpty()) coreLoads.size else 8

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                val gap = 4.dp.toPx()
                val evenSlot = size.width / slotCount
                val barWidth = minOf(evenSlot - gap, 20.dp.toPx()).coerceAtLeast(1f)
                val leadingOffset = 0f
                val barInset = (evenSlot - barWidth) / 2f
                val corner = CornerRadius(2.dp.toPx())
                val minimumHeight = size.height * 0.04f

                val sortedLoads = coreLoads.sorted()

                repeat(slotCount) { index ->
                    val load = sortedLoads.getOrNull(index) ?: 0
                    val fraction = (load / 100f).coerceIn(0f, 1f)

                    val left = leadingOffset + index * evenSlot + barInset

                    drawRoundRect(
                        color = trackColor.copy(alpha = 0.3f),
                        topLeft = Offset(left, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = corner
                    )

                    if (fraction > 0f) {
                        val barHeight = (size.height * maxOf(fraction, 0.04f)).coerceAtLeast(minimumHeight)
                        val t = ((fraction - 0.7f) / 0.3f).coerceIn(0f, 1f)
                        val color = lerp(accentColor, warnColor, t)
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(left, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = corner
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GraphHeaderDotLabel(
    text: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
        Text(
            text = text,
            style = MorpheTheme.typography.labelExtraSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        )
    }
}

private data class TimelineBarMetrics(
    val gap: Float,
    val barWidth: Float,
    val corner: CornerRadius,
    val minimumHeight: Float,
)

private fun DrawScope.calculateTimelineBarMetrics(slotCount: Int): TimelineBarMetrics {
    val gap = 1.dp.toPx()
    return TimelineBarMetrics(
        gap = gap,
        barWidth = (size.width - (gap * (slotCount - 1))) / slotCount,
        corner = CornerRadius(2.dp.toPx()),
        minimumHeight = size.height * 0.04f,
    )
}

