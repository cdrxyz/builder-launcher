package xyz.cdr.builderlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.cdr.builderlauncher.ui.theme.Dim
import xyz.cdr.builderlauncher.weather.WeatherKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WeatherGlyph(
    kind: WeatherKind,
    modifier: Modifier = Modifier,
    isDay: Boolean = true,
    color: Color = Dim,
    size: Dp = 16.dp,
    contentDescription: String? = null,
) {
    val described = if (contentDescription == null) {
        modifier
    } else {
        modifier.semantics { this.contentDescription = contentDescription }
    }
    Canvas(described.size(size)) {
        drawWeather(kind = kind, isDay = isDay, color = color)
    }
}

private fun DrawScope.drawWeather(kind: WeatherKind, isDay: Boolean, color: Color) {
    val strokeW = maxOf(1.6.dp.toPx(), size.minDimension * 0.055f)
    val stroke = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (kind) {
        WeatherKind.CLEAR -> if (isDay) drawSun(color, stroke, cx = 0.50f, cy = 0.50f, scale = 1f) else drawMoon(color, stroke)
        WeatherKind.FAIR -> {
            if (isDay) {
                drawSun(color, stroke, cx = 0.68f, cy = 0.34f, scale = 0.58f)
            } else {
                drawMoon(color, stroke, cx = 0.68f, cy = 0.32f, scale = 0.62f)
            }
            drawCloud(color, stroke, left = 0.04f, top = 0.38f, w = 0.78f, h = 0.50f)
        }
        WeatherKind.CLOUDY -> drawCloud(color, stroke, left = 0.08f, top = 0.22f, w = 0.84f, h = 0.56f)
        WeatherKind.FOG -> drawFog(color, stroke)
        WeatherKind.DRIZZLE -> {
            drawCloud(color, stroke, left = 0.10f, top = 0.08f, w = 0.80f, h = 0.48f)
            drawPrecip(color, stroke, dashes = true)
        }
        WeatherKind.RAIN -> {
            drawCloud(color, stroke, left = 0.10f, top = 0.08f, w = 0.80f, h = 0.48f)
            drawPrecip(color, stroke, dashes = false)
        }
        WeatherKind.SNOW -> {
            drawCloud(color, stroke, left = 0.10f, top = 0.08f, w = 0.80f, h = 0.48f)
            drawSnow(color, stroke)
        }
        WeatherKind.STORM -> {
            drawCloud(color, stroke, left = 0.08f, top = 0.06f, w = 0.78f, h = 0.44f)
            drawBolt(color, stroke)
        }
    }
}

private fun DrawScope.drawSun(color: Color, stroke: Stroke, cx: Float, cy: Float, scale: Float) {
    val c = Offset(size.width * cx, size.height * cy)
    val r = size.minDimension * 0.18f * scale
    drawCircle(color = color, radius = r, center = c, style = Fill)
    val inner = r * 1.45f
    val outer = r * 2.05f
    for (i in 0 until 8) {
        val a = i * PI.toFloat() / 4f - PI.toFloat() / 8f
        drawLine(
            color = color,
            start = Offset(c.x + cos(a) * inner, c.y + sin(a) * inner),
            end = Offset(c.x + cos(a) * outer, c.y + sin(a) * outer),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawMoon(color: Color, stroke: Stroke, cx: Float = 0.52f, cy: Float = 0.50f, scale: Float = 1f) {
    val c = Offset(size.width * cx, size.height * cy)
    val r = size.minDimension * 0.28f * scale
    val moon = Path().apply {
        addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r))
    }
    val cutR = r * 0.82f
    val cut = Path().apply {
        addOval(
            Rect(
                c.x - cutR + r * 0.42f,
                c.y - cutR - r * 0.12f,
                c.x + cutR + r * 0.42f,
                c.y + cutR - r * 0.12f,
            ),
        )
    }
    val crescent = Path().apply { op(moon, cut, PathOperation.Difference) }
    drawPath(crescent, color = color, style = Fill)
    drawPath(crescent, color = color, style = stroke)
}

private fun DrawScope.drawCloud(color: Color, stroke: Stroke, left: Float, top: Float, w: Float, h: Float) {
    val l = size.width * left
    val t = size.height * top
    val width = size.width * w
    val height = size.height * h
    val path = Path().apply {
        val baseY = t + height * 0.78f
        moveTo(l + width * 0.16f, baseY)
        lineTo(l + width * 0.82f, baseY)
        cubicTo(
            l + width * 1.02f, baseY,
            l + width * 1.02f, t + height * 0.28f,
            l + width * 0.78f, t + height * 0.32f,
        )
        cubicTo(
            l + width * 0.76f, t - height * 0.08f,
            l + width * 0.42f, t - height * 0.10f,
            l + width * 0.38f, t + height * 0.28f,
        )
        cubicTo(
            l + width * 0.18f, t + height * 0.08f,
            l - width * 0.04f, t + height * 0.38f,
            l + width * 0.16f, baseY,
        )
        close()
    }
    drawPath(path, color = color, style = stroke)
}

private fun DrawScope.drawFog(color: Color, stroke: Stroke) {
    val ys = floatArrayOf(0.28f, 0.50f, 0.72f)
    val lefts = floatArrayOf(0.12f, 0.22f, 0.16f)
    val rights = floatArrayOf(0.88f, 0.78f, 0.84f)
    for (i in ys.indices) {
        drawLine(
            color = color,
            start = Offset(size.width * lefts[i], size.height * ys[i]),
            end = Offset(size.width * rights[i], size.height * ys[i]),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawPrecip(color: Color, stroke: Stroke, dashes: Boolean) {
    val xs = floatArrayOf(0.30f, 0.50f, 0.70f)
    val len = if (dashes) 0.10f else 0.18f
    val gap = if (dashes) 0.08f else 0f
    for (x in xs) {
        val start = Offset(size.width * x, size.height * 0.64f)
        if (dashes) {
            drawLine(
                color = color,
                start = start,
                end = Offset(start.x - size.width * 0.04f, start.y + size.height * len),
                strokeWidth = stroke.width * 0.85f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = Offset(start.x - size.width * 0.05f, start.y + size.height * (len + gap)),
                end = Offset(start.x - size.width * 0.09f, start.y + size.height * (len * 2f + gap)),
                strokeWidth = stroke.width * 0.85f,
                cap = StrokeCap.Round,
            )
        } else {
            drawLine(
                color = color,
                start = start,
                end = Offset(start.x - size.width * 0.08f, start.y + size.height * len),
                strokeWidth = stroke.width * 0.9f,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.drawSnow(color: Color, stroke: Stroke) {
    val spots = arrayOf(
        Offset(size.width * 0.30f, size.height * 0.72f),
        Offset(size.width * 0.50f, size.height * 0.82f),
        Offset(size.width * 0.70f, size.height * 0.72f),
    )
    val arm = size.minDimension * 0.06f
    for (c in spots) {
        drawLine(color, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), stroke.width * 0.8f, StrokeCap.Round)
        drawLine(color, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), stroke.width * 0.8f, StrokeCap.Round)
        val d = arm * 0.7f
        drawLine(color, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), stroke.width * 0.7f, StrokeCap.Round)
        drawLine(color, Offset(c.x - d, c.y + d), Offset(c.x + d, c.y - d), stroke.width * 0.7f, StrokeCap.Round)
    }
}

private fun DrawScope.drawBolt(color: Color, stroke: Stroke) {
    val path = Path().apply {
        moveTo(size.width * 0.54f, size.height * 0.46f)
        lineTo(size.width * 0.40f, size.height * 0.64f)
        lineTo(size.width * 0.50f, size.height * 0.64f)
        lineTo(size.width * 0.38f, size.height * 0.90f)
        lineTo(size.width * 0.66f, size.height * 0.58f)
        lineTo(size.width * 0.54f, size.height * 0.58f)
        close()
    }
    drawPath(path, color = color, style = Fill)
    drawPath(path, color = color, style = stroke)
}
