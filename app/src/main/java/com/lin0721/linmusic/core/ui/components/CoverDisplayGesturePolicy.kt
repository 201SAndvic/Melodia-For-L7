package com.lin0721.linmusic.core.ui.components

import kotlin.math.abs

internal enum class CoverGestureAxis { UNDECIDED, HORIZONTAL, VERTICAL }

internal fun coverGestureAxis(dx: Float, dy: Float, touchSlop: Float): CoverGestureAxis = when {
    maxOf(abs(dx), abs(dy)) <= touchSlop -> CoverGestureAxis.UNDECIDED
    abs(dy) >= abs(dx) * 1.4f -> CoverGestureAxis.VERTICAL
    abs(dx) >= abs(dy) * 1.4f -> CoverGestureAxis.HORIZONTAL
    else -> CoverGestureAxis.UNDECIDED
}

internal fun coverVerticalAction(axis: CoverGestureAxis, dy: Float, threshold: Float): Boolean? =
    if (axis != CoverGestureAxis.VERTICAL || abs(dy) < threshold) null else dy < 0f
