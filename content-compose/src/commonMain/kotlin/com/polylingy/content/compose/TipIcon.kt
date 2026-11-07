package com.polylingy.content.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import com.polylingy.content.Block

/** A thin line icon for a remark, drawn on a 20 x 20 grid so it has no image files and takes the colour it is given. */
@Composable
internal fun TipIcon(kind: Block.Tip.Kind, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val k = size.width / 20f
        val stroke = Stroke(1.6f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun at(x: Float, y: Float) = Offset(x * k, y * k)
        when (kind) {
            Block.Tip.Kind.TIP -> {
                drawLine(color, at(7f, 14.5f), at(13f, 14.5f), stroke.width, StrokeCap.Round)
                drawLine(color, at(8f, 17f), at(12f, 17f), stroke.width, StrokeCap.Round)
                val bulb = Path().apply {
                    moveTo(10f * k, 2.5f * k)
                    cubicTo(6f * k, 2.5f * k, 4.5f * k, 6.5f * k, 7f * k, 9.5f * k)
                    cubicTo(7.7f * k, 10.4f * k, 8f * k, 11f * k, 8f * k, 12.5f * k)
                    lineTo(12f * k, 12.5f * k)
                    cubicTo(12f * k, 11f * k, 12.3f * k, 10.4f * k, 13f * k, 9.5f * k)
                    cubicTo(15.5f * k, 6.5f * k, 14f * k, 2.5f * k, 10f * k, 2.5f * k)
                    close()
                }
                drawPath(bulb, color, style = stroke)
            }
            Block.Tip.Kind.RULE -> {
                drawLine(color, at(4f, 5f), at(16f, 5f), stroke.width, StrokeCap.Round)
                drawLine(color, at(4f, 10f), at(16f, 10f), stroke.width, StrokeCap.Round)
                drawLine(color, at(4f, 15f), at(11f, 15f), stroke.width, StrokeCap.Round)
            }
            Block.Tip.Kind.WARNING -> {
                val triangle = Path().apply {
                    moveTo(10f * k, 3f * k); lineTo(17.5f * k, 16f * k); lineTo(2.5f * k, 16f * k); close()
                }
                drawPath(triangle, color, style = stroke)
                drawLine(color, at(10f, 8f), at(10f, 12f), stroke.width, StrokeCap.Round)
                drawLine(color, at(10f, 14.2f), at(10f, 14.3f), stroke.width, StrokeCap.Round)
            }
            Block.Tip.Kind.NOTE -> {
                drawCircle(color, 7.5f * k, at(10f, 10f), style = stroke)
                drawLine(color, at(10f, 9f), at(10f, 14f), stroke.width, StrokeCap.Round)
                drawLine(color, at(10f, 6.3f), at(10f, 6.4f), stroke.width, StrokeCap.Round)
            }
        }
    }
}
