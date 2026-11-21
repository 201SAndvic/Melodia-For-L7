package com.lin0721.linmusic.core.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp

/** Reserve only clearly vertical drags on the artwork. Horizontal drags remain
 * unconsumed for the existing swipe-to-skip implementation. No nested scroll is
 * dispatched for the reserved gesture, so it cannot also dismiss the player. */
@Composable
fun rememberCoverDisplayGestureModifier(
    enabled: Boolean,
    artworkInset: Dp,
    confirmDistance: Dp,
    onAction: (Boolean) -> Unit
): Modifier {
    val latestAction by rememberUpdatedState(onAction)
    if (!enabled) return Modifier
    return Modifier.pointerInput(artworkInset, confirmDistance) {
        val inset = artworkInset.toPx()
        val threshold = confirmDistance.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // This modifier surrounds the artwork's horizontal padding as well.
            if (down.position.x !in inset..(size.width - inset)) return@awaitEachGesture
            val start = down.position
            var axis = CoverGestureAxis.UNDECIDED
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                // Multi-touch and an already-owned horizontal/page gesture cancel.
                if (event.changes.count { it.pressed } > 1) break
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed) break
                val offset = change.position - start
                if (axis == CoverGestureAxis.UNDECIDED) axis = coverGestureAxis(offset.x, offset.y, viewConfiguration.touchSlop)
                if (axis == CoverGestureAxis.HORIZONTAL) break
                if (axis == CoverGestureAxis.VERTICAL) change.consume()
                if (!change.pressed) {
                    coverVerticalAction(axis, offset.y, threshold)?.let(latestAction)
                    break
                }
            }
        }
    }
}
