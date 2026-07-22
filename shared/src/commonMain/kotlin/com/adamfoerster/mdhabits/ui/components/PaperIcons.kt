package com.adamfoerster.mdhabits.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.adamfoerster.mdhabits.ui.theme.Paper

/**
 * Hand-stroked icons matching the design's inline SVGs. All are drawn on a 24x24 grid and scale
 * with [size].
 */

private fun DrawScope.stroke(width: Float) = Stroke(
    width = width,
    cap = StrokeCap.Round,
    join = StrokeJoin.Round,
)

private fun DrawScope.grid(x: Float, y: Float): androidx.compose.ui.geometry.Offset =
    androidx.compose.ui.geometry.Offset(size.width * x / 24f, size.height * y / 24f)

@Composable
fun FolderIcon(color: Color = Paper.subtle, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val p = Path().apply {
            moveTo(grid(3f, 7f).x, grid(3f, 7f).y)
            cubicTo(grid(3f, 5.9f).x, grid(3f, 5.9f).y, grid(3.9f, 5f).x, grid(3.9f, 5f).y, grid(5f, 5f).x, grid(5f, 5f).y)
            lineTo(grid(9f, 5f).x, grid(9f, 5f).y)
            lineTo(grid(11f, 7f).x, grid(11f, 7f).y)
            lineTo(grid(19f, 7f).x, grid(19f, 7f).y)
            cubicTo(grid(20.1f, 7f).x, grid(20.1f, 7f).y, grid(21f, 7.9f).x, grid(21f, 7.9f).y, grid(21f, 9f).x, grid(21f, 9f).y)
            lineTo(grid(21f, 18f).x, grid(21f, 18f).y)
            cubicTo(grid(21f, 19.1f).x, grid(21f, 19.1f).y, grid(20.1f, 20f).x, grid(20.1f, 20f).y, grid(19f, 20f).x, grid(19f, 20f).y)
            lineTo(grid(5f, 20f).x, grid(5f, 20f).y)
            cubicTo(grid(3.9f, 20f).x, grid(3.9f, 20f).y, grid(3f, 19.1f).x, grid(3f, 19.1f).y, grid(3f, 18f).x, grid(3f, 18f).y)
            close()
        }
        drawPath(p, color, style = stroke(1.7.dp.toPx()))
    }
}

@Composable
fun GearIcon(color: Color = Paper.subtle, size: Dp = 17.dp) {
    Canvas(Modifier.size(size)) {
        val s = stroke(1.7.dp.toPx())
        drawCircle(color, radius = size.toPx() * 3f / 24f, center = grid(12f, 12f), style = s)
        val rays = listOf(
            grid(12f, 2f) to grid(12f, 5f), grid(12f, 19f) to grid(12f, 22f),
            grid(2f, 12f) to grid(5f, 12f), grid(19f, 12f) to grid(22f, 12f),
            grid(4.9f, 4.9f) to grid(7f, 7f), grid(17f, 17f) to grid(19.1f, 19.1f),
            grid(19.1f, 4.9f) to grid(17f, 7f), grid(7f, 17f) to grid(4.9f, 19.1f),
        )
        rays.forEach { (a, b) -> drawLine(color, a, b, strokeWidth = 1.7.dp.toPx(), cap = StrokeCap.Round) }
    }
}

@Composable
fun DownloadIcon(color: Color = Paper.subtle, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val w = 1.7.dp.toPx()
        drawLine(color, grid(12f, 3f), grid(12f, 15f), w, StrokeCap.Round)
        val arrow = Path().apply {
            moveTo(grid(8f, 11f).x, grid(8f, 11f).y)
            lineTo(grid(12f, 15f).x, grid(12f, 15f).y)
            lineTo(grid(16f, 11f).x, grid(16f, 11f).y)
        }
        drawPath(arrow, color, style = stroke(w))
        drawLine(color, grid(5f, 19f), grid(19f, 19f), w, StrokeCap.Round)
    }
}

@Composable
fun CalendarIcon(color: Color = Paper.subtle, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val w = 1.7.dp.toPx()
        drawRoundRect(
            color,
            topLeft = grid(3f, 5f),
            size = androidx.compose.ui.geometry.Size(this.size.width * 18f / 24f, this.size.height * 16f / 24f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(this.size.width * 2f / 24f),
            style = stroke(w),
        )
        drawLine(color, grid(3f, 9f), grid(21f, 9f), w, StrokeCap.Round)
        drawLine(color, grid(8f, 3f), grid(8f, 7f), w, StrokeCap.Round)
        drawLine(color, grid(16f, 3f), grid(16f, 7f), w, StrokeCap.Round)
    }
}

@Composable
fun PencilIcon(color: Color = Paper.subtle, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val w = 1.7.dp.toPx()
        drawLine(color, grid(12f, 20f), grid(21f, 20f), w, StrokeCap.Round)
        val p = Path().apply {
            moveTo(grid(16.5f, 3.5f).x, grid(16.5f, 3.5f).y)
            cubicTo(grid(17.3f, 2.7f).x, grid(17.3f, 2.7f).y, grid(18.7f, 2.7f).x, grid(18.7f, 2.7f).y, grid(19.5f, 3.5f).x, grid(19.5f, 3.5f).y)
            cubicTo(grid(20.3f, 4.3f).x, grid(20.3f, 4.3f).y, grid(20.3f, 5.7f).x, grid(20.3f, 5.7f).y, grid(19.5f, 6.5f).x, grid(19.5f, 6.5f).y)
            lineTo(grid(7f, 19f).x, grid(7f, 19f).y)
            lineTo(grid(3f, 20f).x, grid(3f, 20f).y)
            lineTo(grid(4f, 16f).x, grid(4f, 16f).y)
            close()
        }
        drawPath(p, color, style = stroke(w))
    }
}

@Composable
fun StarIcon(color: Color = Paper.subtle, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val p = Path().apply {
            moveTo(grid(12f, 2f).x, grid(12f, 2f).y)
            lineTo(grid(14.4f, 9.4f).x, grid(14.4f, 9.4f).y)
            lineTo(grid(22f, 9.4f).x, grid(22f, 9.4f).y)
            lineTo(grid(16f, 13.8f).x, grid(16f, 13.8f).y)
            lineTo(grid(18.3f, 21f).x, grid(18.3f, 21f).y)
            lineTo(grid(12f, 16.6f).x, grid(12f, 16.6f).y)
            lineTo(grid(5.7f, 21f).x, grid(5.7f, 21f).y)
            lineTo(grid(8f, 13.8f).x, grid(8f, 13.8f).y)
            lineTo(grid(2f, 9.4f).x, grid(2f, 9.4f).y)
            lineTo(grid(9.6f, 9.4f).x, grid(9.6f, 9.4f).y)
            close()
        }
        drawPath(p, color, style = stroke(1.7.dp.toPx()))
    }
}

@Composable
fun CoinIcon(color: Color = Paper.onDarkFaded, size: Dp = 52.dp) {
    Canvas(Modifier.size(size)) {
        val w = 1.3.dp.toPx()
        drawCircle(color, radius = this.size.width * 9f / 24f, center = grid(12f, 12f), style = stroke(w))
        drawLine(color, grid(12f, 7f), grid(12f, 17f), w, StrokeCap.Round)
        val sPath = Path().apply {
            moveTo(grid(9f, 9.5f).x, grid(9f, 9.5f).y)
            cubicTo(grid(9f, 8.2f).x, grid(9f, 8.2f).y, grid(10.3f, 7.5f).x, grid(10.3f, 7.5f).y, grid(12f, 7.5f).x, grid(12f, 7.5f).y)
            cubicTo(grid(13.7f, 7.5f).x, grid(13.7f, 7.5f).y, grid(15f, 8.2f).x, grid(15f, 8.2f).y, grid(15f, 9.5f).x, grid(15f, 9.5f).y)
            cubicTo(grid(15f, 10.8f).x, grid(15f, 10.8f).y, grid(13.5f, 11.3f).x, grid(13.5f, 11.3f).y, grid(12f, 11.7f).x, grid(12f, 11.7f).y)
            cubicTo(grid(10.5f, 12.1f).x, grid(10.5f, 12.1f).y, grid(9f, 12.6f).x, grid(9f, 12.6f).y, grid(9f, 14f).x, grid(9f, 14f).y)
            cubicTo(grid(9f, 15.3f).x, grid(9f, 15.3f).y, grid(10.3f, 16f).x, grid(10.3f, 16f).y, grid(12f, 16f).x, grid(12f, 16f).y)
            cubicTo(grid(13.7f, 16f).x, grid(13.7f, 16f).y, grid(15f, 15.3f).x, grid(15f, 15.3f).y, grid(15f, 14f).x, grid(15f, 14f).y)
        }
        drawPath(sPath, color, style = stroke(w))
    }
}

/* --------- tab bar icons (20x20 grid in the design; reuse the 24-grid helper) --------- */

@Composable
fun HomeTabIcon(color: Color, size: Dp = 21.dp) {
    Canvas(Modifier.size(size)) {
        val p = Path().apply {
            moveTo(grid(3.6f, 11.4f).x, grid(3.6f, 11.4f).y)
            lineTo(grid(12f, 4.2f).x, grid(12f, 4.2f).y)
            lineTo(grid(20.4f, 11.4f).x, grid(20.4f, 11.4f).y)
            lineTo(grid(20.4f, 20.4f).x, grid(20.4f, 20.4f).y)
            lineTo(grid(14.9f, 20.4f).x, grid(14.9f, 20.4f).y)
            lineTo(grid(14.9f, 14.9f).x, grid(14.9f, 14.9f).y)
            lineTo(grid(9.1f, 14.9f).x, grid(9.1f, 14.9f).y)
            lineTo(grid(9.1f, 20.4f).x, grid(9.1f, 20.4f).y)
            lineTo(grid(3.6f, 20.4f).x, grid(3.6f, 20.4f).y)
            close()
        }
        drawPath(p, color, style = Fill)
    }
}

@Composable
fun GiftTabIcon(color: Color, size: Dp = 21.dp) {
    Canvas(Modifier.size(size)) {
        val w = 1.7.dp.toPx()
        drawRoundRect(
            color,
            topLeft = grid(4.2f, 8.4f),
            size = androidx.compose.ui.geometry.Size(this.size.width * 15.6f / 24f, this.size.height * 11.4f / 24f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(this.size.width * 1.2f / 24f),
            style = stroke(w),
        )
        drawLine(color, grid(12f, 8.4f), grid(12f, 19.8f), w, StrokeCap.Round)
        drawLine(color, grid(4.2f, 12.6f), grid(19.8f, 12.6f), w, StrokeCap.Round)
        // bow
        val bow = Path().apply {
            moveTo(grid(12f, 8.4f).x, grid(12f, 8.4f).y)
            cubicTo(grid(8.4f, 8.4f).x, grid(8.4f, 8.4f).y, grid(7.2f, 4.8f).x, grid(7.2f, 4.8f).y, grid(9.6f, 4.2f).x, grid(9.6f, 4.2f).y)
            cubicTo(grid(11.4f, 3.8f).x, grid(11.4f, 3.8f).y, grid(12f, 6.6f).x, grid(12f, 6.6f).y, grid(12f, 8.4f).x, grid(12f, 8.4f).y)
            cubicTo(grid(12f, 6.6f).x, grid(12f, 6.6f).y, grid(12.6f, 3.8f).x, grid(12.6f, 3.8f).y, grid(14.4f, 4.2f).x, grid(14.4f, 4.2f).y)
            cubicTo(grid(16.8f, 4.8f).x, grid(16.8f, 4.8f).y, grid(15.6f, 8.4f).x, grid(15.6f, 8.4f).y, grid(12f, 8.4f).x, grid(12f, 8.4f).y)
            close()
        }
        drawPath(bow, color, style = stroke(w))
    }
}

@Composable
fun ListTabIcon(color: Color, size: Dp = 21.dp) {
    Canvas(Modifier.size(size)) {
        val w = 1.7.dp.toPx()
        drawLine(color, grid(4.8f, 6.6f), grid(19.2f, 6.6f), w, StrokeCap.Round)
        drawLine(color, grid(4.8f, 12f), grid(19.2f, 12f), w, StrokeCap.Round)
        drawLine(color, grid(4.8f, 17.4f), grid(13.2f, 17.4f), w, StrokeCap.Round)
    }
}
