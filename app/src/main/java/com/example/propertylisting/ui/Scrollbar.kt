package com.example.propertylisting.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a vertical scrollbar over a lazy list. Items aren't measured until they're on screen,
 * so the total content height is estimated from the average size of the visible items.
 */
@Composable
fun Modifier.verticalScrollbar(
    state: LazyListState,
    trackTop: Dp = 0.dp,
    trackBottom: Dp = 0.dp,
    width: Dp = 4.dp,
    endPadding: Dp = 4.dp,
    minThumbHeight: Dp = 32.dp,
): Modifier {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val alpha by animateFloatAsState(
        targetValue = if (state.isScrollInProgress) 0.8f else 0.35f,
        label = "scrollbarAlpha",
    )

    return drawWithContent {
        drawContent()

        val info = state.layoutInfo
        val visible = info.visibleItemsInfo
        if (visible.isEmpty()) return@drawWithContent

        val first = visible.first()
        val last = visible.last()
        val viewport = info.viewportSize.height.toFloat()
        // Span of the visible items divided by their count includes inter-item spacing.
        val averageItemSize = (last.offset + last.size - first.offset).toFloat() / visible.size
        val estimatedContent = averageItemSize * info.totalItemsCount
        if (estimatedContent <= viewport) return@drawWithContent

        val atStart = first.index == 0 && state.firstVisibleItemScrollOffset == 0
        val atEnd = last.index == info.totalItemsCount - 1 &&
            last.offset + last.size <= info.viewportEndOffset - info.afterContentPadding
        val progress = when {
            atStart -> 0f
            atEnd -> 1f
            else -> {
                val scrolled = state.firstVisibleItemIndex * averageItemSize + state.firstVisibleItemScrollOffset
                (scrolled / (estimatedContent - viewport)).coerceIn(0f, 1f)
            }
        }

        val trackStart = trackTop.toPx()
        val trackHeight = size.height - trackStart - trackBottom.toPx()
        val thumbHeight = (trackHeight * viewport / estimatedContent)
            .coerceIn(minThumbHeight.toPx(), trackHeight)
        val thumbTop = trackStart + (trackHeight - thumbHeight) * progress
        val thumbWidth = width.toPx()

        drawRoundRect(
            color = color,
            alpha = alpha,
            topLeft = Offset(size.width - thumbWidth - endPadding.toPx(), thumbTop),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = CornerRadius(thumbWidth / 2),
        )
    }
}
