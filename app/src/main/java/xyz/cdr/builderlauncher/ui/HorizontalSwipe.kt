package xyz.cdr.builderlauncher.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

fun Modifier.horizontalSwipe(
    key: Any?,
    onRight: (() -> Unit)? = null,
    onLeft: (() -> Unit)? = null,
): Modifier = pointerInput(key) {
    val threshold = 72.dp.toPx()
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragCancel = { total = 0f },
        onDragEnd = {
            when {
                total > threshold -> onRight?.invoke()
                total < -threshold -> onLeft?.invoke()
            }
            total = 0f
        },
        onHorizontalDrag = { change, amount ->
            total += amount
            change.consume()
        },
    )
}
