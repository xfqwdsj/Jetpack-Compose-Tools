package xyz.xfqlittlefan.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlin.math.roundToInt

@Composable
fun NestedScrollLayout(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    nestedScrollBehavior: NestedScrollBehavior,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    content: @Composable () -> Unit
) {
    Surface(modifier = modifier, color = containerColor, contentColor = contentColor) {
        SubcomposeLayout { constraints ->
            val layoutWidth = constraints.maxWidth
            val layoutHeight = constraints.maxHeight

            val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

            nestedScrollBehavior.maxHeight = layoutHeight.toFloat()

            val topBarPlaceableList = subcompose(NestedScrollLayoutContent.TopBar, topBar).map {
                it.measure(looseConstraints)
            }

            val topBarHeight = topBarPlaceableList.maxByOrNull { it.height }?.height ?: 0
            nestedScrollBehavior.offsetLimit = (-topBarHeight).toFloat()

            val bodyY = (nestedScrollBehavior.blankOffset - nestedScrollBehavior.offsetLimit).roundToInt()
            val bodyContentHeight = layoutHeight - bodyY

            val bodyContentPlaceableList = subcompose(NestedScrollLayoutContent.MainContent, content).map {
                it.measure(looseConstraints.copy(maxHeight = bodyContentHeight))
            }

            layout(layoutWidth, layoutHeight) {
                bodyContentPlaceableList.forEach {
                    it.place(0, bodyY)
                }
                topBarPlaceableList.forEach {
                    it.place(0, nestedScrollBehavior.offset.roundToInt())
                }
            }
        }
    }
}

class NestedScrollBehavior(coroutineScope: CoroutineScope) {
    var canScroll by mutableStateOf(true)
    val scrollFraction
        get() = if (offsetLimit != 0f) {
            1 - ((offsetLimit - contentOffset).coerceIn(
                minimumValue = offsetLimit,
                maximumValue = 0f
            ) / offsetLimit)
        } else {
            0f
        }
    internal var offset
        get() = _offset.value
        set(value) {
            _offset.value = value
        }
    private var _offset = AnimatedFloatValue(0f, coroutineScope)
    internal var blankOffset
        get() = _blankOffset.value
        set(value) {
            _blankOffset.value = value
        }
    private var _blankOffset = AnimatedFloatValue(0f, coroutineScope)
    private var contentOffset by mutableStateOf(0f)
    internal var offsetLimit by mutableStateOf(-Float.MAX_VALUE)
    internal var maxHeight by mutableStateOf(Float.MAX_VALUE)
    private var direction by mutableStateOf(0f)
    private var previousOffset by mutableStateOf(0f)
    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!canScroll) return Offset.Zero
            direction += available.y
            offset = (offset + available.y).coerceIn(minimumValue = offsetLimit, maximumValue = 0f)
            val newBlankOffset = (blankOffset + available.y).coerceIn(minimumValue = offsetLimit, maximumValue = 0f)
            val difference = newBlankOffset - blankOffset
            if (available.y < 0f) blankOffset = newBlankOffset
            return Offset(x = 0f, y = if (difference < 0f) difference else 0f)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!canScroll) return Offset.Zero
            contentOffset += consumed.y
            direction = consumed.y + available.y
            if (offset == 0f || offset == offsetLimit) {
                if (available.y > 0f) {
                    contentOffset = 0f
                }
            }
            val newOffset = (blankOffset + available.y).coerceIn(minimumValue = offsetLimit, maximumValue = 0f)
            val difference = newOffset - blankOffset
            if (available.y > 0f) blankOffset = newOffset
            return Offset(x = 0f, y = if (available.y > 0f) difference else 0f)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (canScroll) {
                animateSetOffset(
                    when {
                        direction > 0 -> 0f
                        direction < 0 -> offsetLimit
                        else -> previousOffset
                    }
                )
                direction = 0f
            }
            return Velocity.Zero
        }
    }

    private fun animateSetOffset(to: Float) {
        previousOffset = to
        if (offset != offsetLimit && offset != 0f) _offset.animatedTo(to)
        if (blankOffset != offsetLimit && blankOffset != 0f) _blankOffset.animatedTo(to)
    }
}

private enum class NestedScrollLayoutContent { TopBar, MainContent }