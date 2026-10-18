package xyz.cdr.builderlauncher.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange

suspend fun PointerInputScope.detectTapOrLongDrag(
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        val longPress = awaitLongPressOrCancellation(down.id)
        if (longPress != null) {
            onDragStart()
            val released = drag(longPress.id) { change ->
                onDrag(change.positionChange().y)
                change.consume()
            }
            if (released) onDragEnd() else onDragCancel()
            return@awaitEachGesture
        }
        val up = currentEvent.changes.firstOrNull { it.id == down.id }
        val moved = up?.let { (it.position - down.position).getDistance() } ?: slop
        if (up != null && !up.pressed && moved < slop) onTap()
    }
}
