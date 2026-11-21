package com.lin0721.linmusic.core.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import com.lin0721.linmusic.core.preferences.PerformanceOptimization
import com.lin0721.linmusic.core.preferences.PerformanceOptimizations

val LocalPerformanceOptimizations = staticCompositionLocalOf { PerformanceOptimizations() }

/** Only progress/time labels recompose at this cadence. Karaoke reads the raw clock. */
@Composable
fun sampledProgressPosition(provider: () -> Long): Long {
    val latest by rememberUpdatedState(provider)
    val interval = if (LocalPerformanceOptimizations.current.enabled(PerformanceOptimization.SLOW_PROGRESS)) 500L else 250L
    val position by remember(interval) { derivedStateOf { quantizeProgress(latest(), interval) } }
    return position
}

internal fun quantizeProgress(position: Long, interval: Long): Long =
    position.coerceAtLeast(0L) / interval.coerceAtLeast(1L) * interval.coerceAtLeast(1L)
