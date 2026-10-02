package de.kilometerbuch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Eine Datenreihe (meist ein Auto); [values] hat einen Wert pro Monat, null = kein Eintrag. */
data class ChartSeries(val name: String, val color: Color, val values: List<Float?>)

enum class ChartKind {
    /** Gestapelte Balken; der Stapel ist die Summe aller Reihen. */
    Bars,

    /** Eine Linie pro Reihe. */
    Line,
}

private val PAD_LEFT = 44.dp
private val PAD_RIGHT = 8.dp
private val PAD_TOP = 10.dp
private val PAD_BOTTOM = 24.dp

private class Scale(val lo: Float, val hi: Float, val step: Float)

private fun niceStep(raw: Float): Float {
    val mag = 10f.pow(floor(log10(raw)))
    val norm = raw / mag
    val nice = when {
        norm <= 1f -> 1f
        norm <= 2f -> 2f
        norm <= 5f -> 5f
        else -> 10f
    }
    return nice * mag
}

private fun niceScale(min: Float, max: Float, zeroBased: Boolean): Scale {
    var lo = if (zeroBased) 0f else min
    var hi = max
    if (zeroBased && hi <= 0f) hi = 100f
    if (hi - lo < 1e-3f) {
        hi += 1f
        lo = max(0f, lo - 1f)
    }
    val step = niceStep((hi - lo) / 4f)
    val niceLo = if (zeroBased) 0f else max(0f, floor(lo / step) * step)
    return Scale(niceLo, ceil(hi / step) * step, step)
}

/**
 * Monatsdiagramm. Antippen wählt einen Monat; darüber steht sein Wert (bei Balken die Summe),
 * bei mehreren Reihen zeigt die Legende darunter den Wert jeder Reihe.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthChart(
    months: List<YearMonth>,
    series: List<ChartSeries>,
    kind: ChartKind,
    unit: String,
    format: (Float) -> String,
    surfaceColor: Color,
    modifier: Modifier = Modifier,
    showAverage: Boolean = false,
    /** Beschriftung der y-Achse, falls kürzer als [format] gewünscht. */
    axisFormat: (Float) -> String = format,
) {
    val n = months.size
    val allValues = series.flatMap { s -> s.values.filterNotNull() }
    if (n == 0 || allValues.isEmpty()) return

    val totals: List<Float?> = List(n) { i ->
        val vs = series.mapNotNull { it.values.getOrNull(i) }
        if (vs.isEmpty()) null else vs.sum()
    }

    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.outline
    val labelStyle = TextStyle(fontSize = 11.sp, color = labelColor)

    var selected by remember(months, series) { mutableIntStateOf(totals.indexOfLast { it != null }) }
    val scale = remember(months, series, kind) {
        if (kind == ChartKind.Bars) {
            niceScale(0f, totals.filterNotNull().max(), zeroBased = true)
        } else {
            niceScale(allValues.min(), allValues.max(), zeroBased = false)
        }
    }
    val average = totals.filterNotNull().average().toFloat()

    Column(modifier) {
        val headline: Float? = when {
            kind == ChartKind.Bars -> totals.getOrNull(selected)
            series.size == 1 -> series[0].values.getOrNull(selected)
            else -> null
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                text = months.getOrNull(selected)?.let(::monthLong) ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = labelColor,
                modifier = Modifier.weight(1f),
            )
            if (kind == ChartKind.Bars || series.size == 1) {
                Text(
                    text = headline?.let { "${format(it)} $unit" } ?: "–",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .pointerInput(months, series) {
                    detectTapGestures { pos ->
                        val left = PAD_LEFT.toPx()
                        val band = (size.width - left - PAD_RIGHT.toPx()) / n
                        val i = ((pos.x - left) / band).toInt().coerceIn(0, n - 1)
                        if (totals[i] != null) selected = i
                    }
                },
        ) {
            val left = PAD_LEFT.toPx()
            val right = size.width - PAD_RIGHT.toPx()
            val top = PAD_TOP.toPx()
            val bottom = size.height - PAD_BOTTOM.toPx()
            val plotH = bottom - top
            val band = (right - left) / n
            fun yOf(v: Float) = bottom - (v - scale.lo) / (scale.hi - scale.lo) * plotH
            fun cx(i: Int) = left + band * (i + 0.5f)

            // Raster und y-Beschriftung
            val ticks = ((scale.hi - scale.lo) / scale.step).roundToInt()
            for (t in 0..ticks) {
                val v = scale.lo + t * scale.step
                val y = yOf(v)
                drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                val label = textMeasurer.measure(axisFormat(v), labelStyle)
                drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), y - label.size.height / 2f))
            }

            // x-Beschriftung: so viele Monate, wie nebeneinander passen; Januar mit Jahreszahl
            val maxLabels = max(2, ((right - left) / 44.dp.toPx()).toInt())
            val every = max(1, ceil(n / maxLabels.toFloat()).toInt())
            months.forEachIndexed { i, m ->
                if (i % every != 0) return@forEachIndexed
                val text = if (i == 0 || m.monthValue == 1) {
                    "${monthShort(m)} ’${(m.year % 100).toString().padStart(2, '0')}"
                } else {
                    monthShort(m)
                }
                val label = textMeasurer.measure(text, labelStyle)
                val x = (cx(i) - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
                drawText(label, topLeft = Offset(x, bottom + 6.dp.toPx()))
            }

            when (kind) {
                ChartKind.Bars -> {
                    val barW = min(band * 0.62f, 28.dp.toPx())
                    val gap = 2.dp.toPx()
                    for (i in 0 until n) {
                        val segments = series.mapNotNull { s -> s.values.getOrNull(i)?.takeIf { it > 0f }?.let { s.color to it } }
                        val alpha = if (i == selected) 1f else 0.6f
                        val x0 = cx(i) - barW / 2
                        var acc = 0f
                        segments.forEachIndexed { k, (color, v) ->
                            val segBottom = yOf(acc) - if (k > 0) gap else 0f
                            acc += v
                            val segTop = yOf(acc)
                            val h = segBottom - segTop
                            if (h < 0.5f) return@forEachIndexed
                            val r = if (k == segments.lastIndex) min(4.dp.toPx(), min(h, barW / 2)) else 0f
                            val path = Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        left = x0, top = segTop, right = x0 + barW, bottom = segBottom,
                                        topLeftCornerRadius = CornerRadius(r),
                                        topRightCornerRadius = CornerRadius(r),
                                        bottomRightCornerRadius = CornerRadius.Zero,
                                        bottomLeftCornerRadius = CornerRadius.Zero,
                                    ),
                                )
                            }
                            drawPath(path, color.copy(alpha = alpha))
                        }
                    }
                    drawLine(axisColor, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())

                    if (showAverage && totals.count { it != null } >= 2) {
                        val y = yOf(average)
                        drawLine(
                            labelColor,
                            Offset(left, y),
                            Offset(right, y),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                        )
                        val label = textMeasurer.measure("Ø ${format(average)} $unit", labelStyle.copy(fontWeight = FontWeight.SemiBold))
                        val pad = 3.dp.toPx()
                        val boxW = label.size.width + 2 * pad
                        val boxH = label.size.height + pad
                        val boxTop = max(top, y - boxH - 3.dp.toPx())
                        drawRoundRect(
                            surfaceColor.copy(alpha = 0.9f),
                            topLeft = Offset(right - boxW, boxTop),
                            size = Size(boxW, boxH),
                            cornerRadius = CornerRadius(4.dp.toPx()),
                        )
                        drawText(label, topLeft = Offset(right - boxW + pad, boxTop + pad / 2))
                    }
                }

                ChartKind.Line -> {
                    if (selected in 0 until n) {
                        drawLine(axisColor, Offset(cx(selected), top), Offset(cx(selected), bottom), strokeWidth = 1.dp.toPx())
                    }
                    val showAllDots = n <= 36
                    series.forEach { s ->
                        val path = Path()
                        var penDown = false
                        s.values.forEachIndexed { i, v ->
                            if (v == null) {
                                penDown = false
                            } else {
                                if (penDown) path.lineTo(cx(i), yOf(v)) else path.moveTo(cx(i), yOf(v))
                                penDown = true
                            }
                        }
                        drawPath(path, s.color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

                        s.values.forEachIndexed { i, v ->
                            if (v == null) return@forEachIndexed
                            val isSel = i == selected
                            if (!showAllDots && !isSel) return@forEachIndexed
                            val c = Offset(cx(i), yOf(v))
                            drawCircle(surfaceColor, radius = (if (isSel) 7.0 else 5.5).dp.toPx(), center = c)
                            drawCircle(s.color, radius = (if (isSel) 5.0 else 3.5).dp.toPx(), center = c)
                        }
                    }
                }
            }
        }

        if (series.size >= 2) {
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                series.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(s.color, size = 10.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(s.name, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            s.values.getOrNull(selected)?.let { "${format(it)} $unit" } ?: "–",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
