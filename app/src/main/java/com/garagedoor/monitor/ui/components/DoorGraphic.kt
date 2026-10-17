package com.garagedoor.monitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garagedoor.monitor.model.DoorPhase

@Composable
fun DoorGraphic(
    position: Float,
    phase: DoorPhase,
    description: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val panel = if (phase == DoorPhase.Offline) scheme.surfaceContainerHigh else scheme.surface
    val line = scheme.outline
    val frame = scheme.onSurface
    Canvas(
        modifier
            .fillMaxWidth()
            .height(260.dp)
            .semantics { contentDescription = description },
    ) {
        val openingWidth = size.width * 0.62f
        val openingHeight = size.height * 0.72f
        val left = (size.width - openingWidth) / 2f
        val top = size.height * 0.16f
        val bottom = top + openingHeight
        val radius = 10.dp.toPx()
        val openAmount = position.coerceIn(0f, 1f)
        val doorHeight = openingHeight * (1f - openAmount)

        drawRoundRect(
            color = scheme.surfaceContainerHigh,
            topLeft = Offset(left - 18.dp.toPx(), top - 22.dp.toPx()),
            size = Size(openingWidth + 36.dp.toPx(), 28.dp.toPx()),
            cornerRadius = CornerRadius(8.dp.toPx()),
        )

        drawRoundRect(
            color = Color(0xFF121614),
            topLeft = Offset(left, top),
            size = Size(openingWidth, openingHeight),
            cornerRadius = CornerRadius(radius),
        )

        if (openAmount > 0.02f && phase != DoorPhase.Offline) {
            clipRect(left, top, left + openingWidth, bottom) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0x55E7C27A), Color(0x14E7C27A)),
                        startY = top + doorHeight,
                        endY = bottom,
                    ),
                    topLeft = Offset(left, top + doorHeight),
                    size = Size(openingWidth, (openingHeight - doorHeight).coerceAtLeast(0f)),
                )
            }
        }

        clipRect(left, top, left + openingWidth, bottom) {
            drawRect(
                color = panel,
                topLeft = Offset(left, top),
                size = Size(openingWidth, doorHeight),
            )
            val panelGap = openingHeight / 4f
            for (index in 1 until 4) {
                val y = top + panelGap * index
                if (y < top + doorHeight - 1f) {
                    drawLine(
                        color = line,
                        start = Offset(left + 8.dp.toPx(), y),
                        end = Offset(left + openingWidth - 8.dp.toPx(), y),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
        }

        drawRoundRect(
            color = frame,
            topLeft = Offset(left, top),
            size = Size(openingWidth, openingHeight),
            cornerRadius = CornerRadius(radius),
            style = Stroke(width = 3.dp.toPx()),
        )

        drawLine(
            color = frame.copy(alpha = 0.35f),
            start = Offset(left - 8.dp.toPx(), bottom + 10.dp.toPx()),
            end = Offset(left + openingWidth + 8.dp.toPx(), bottom + 10.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
        )
    }
}
