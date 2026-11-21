package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.theme.SurfaceLight
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// 进度条与两端时间：拖动期间用本地进度接管，松手才真正 seek
@Composable
fun ProgressSection(
    currentPositionProvider: () -> Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    compact: Boolean = false,
    chorusStartMs: Long? = null
) {
    var isSeeking by remember(duration, chorusStartMs) { mutableStateOf(false) }
    var seekPosition by remember(duration, chorusStartMs) { mutableFloatStateOf(0f) }
    var rawSeekPosition by remember(duration, chorusStartMs) { mutableFloatStateOf(0f) }
    var snapped by remember(duration, chorusStartMs) { mutableStateOf(false) }
    val chorus = chorusFraction(chorusStartMs, duration)
    val haptic = LocalHapticFeedback.current

    val currentPosition = com.lin0721.linmusic.core.ui.layout.sampledProgressPosition(currentPositionProvider)
    val progress = if (duration > 0) {
        if (isSeeking) seekPosition else (currentPosition.toFloat() / duration).coerceIn(0f, 1f)
    } else 0f

    val thumbSize by animateDpAsState(
        targetValue = if (isSeeking) 14.dp else 8.dp,
        animationSpec = tween(150),
        label = "thumb_size"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.lg)
            .padding(top = MelodiaSpacing.sm)
    ) {
        var trackWidthPx by remember { mutableFloatStateOf(0f) }
        val density = LocalDensity.current
        val radius = with(density) { 12.dp.toPx() }
        fun updateSeek(raw: Float) {
            rawSeekPosition = raw.coerceIn(0f, 1f)
            val result = snapChorusSeek(rawSeekPosition, chorus, trackWidthPx, radius)
            if (result.snapped && !snapped) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            snapped = result.snapped
            seekPosition = result.fraction
        }
        fun finishSeek(commit: Boolean) {
            isSeeking = false
            snapped = false
            if (commit && duration > 0) onSeek((seekPosition * duration).toLong())
        }

        // 自定义全宽进度条（两端 0 缩进，精准对齐外边缘与时间）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 18.dp else 28.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(duration, chorus) {
                    detectTapGestures(
                        onPress = { offset ->
                            if (trackWidthPx > 0f && duration > 0L) {
                                isSeeking = true
                                updateSeek(offset.x / trackWidthPx)
                                val released = tryAwaitRelease()
                                finishSeek(released)
                            }
                        }
                    )
                }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        if (trackWidthPx > 0f && duration > 0L) {
                            isSeeking = true
                            updateSeek(rawSeekPosition + delta / trackWidthPx)
                        }
                    },
                    onDragStarted = { offset ->
                        isSeeking = true
                        if (trackWidthPx > 0) updateSeek(offset.x / trackWidthPx)
                    },
                    onDragStopped = {
                        finishSeek(true)
                    }
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            // 背景轨道（满宽 4dp）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SurfaceLight)
            )

            // 已播放进度轨道（满宽比例）
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White)
            )

            // Upstream first-chorus marker. Passed markers hide until seeking back.
            if (chorus != null && trackWidthPx > 0 && (isSeeking || progress < chorus)) {
                Box(Modifier.offset(x = with(density) {
                    (chorus * trackWidthPx - 3.dp.toPx()).coerceIn(0f, (trackWidthPx - 6.dp.toPx()).coerceAtLeast(0f)).toDp()
                }).size(6.dp).background(Color.White.copy(alpha = 0.65f), CircleShape)
                    .semantics { contentDescription = "副歌起点 ${formatTime(chorusStartMs!!)}" })
            }
            // 滑块圆点（居中跟随当前进度）
            if (trackWidthPx > 0f) {
                val thumbOffsetDp = with(density) {
                    (progress * trackWidthPx - thumbSize.toPx() / 2f).coerceIn(
                        0f,
                        (trackWidthPx - thumbSize.toPx()).coerceAtLeast(0f)
                    ).toDp()
                }
                Box(
                    modifier = Modifier
                        .offset(x = thumbOffsetDp)
                        .size(thumbSize)
                        .background(Color.White, CircleShape)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val displayPosition = if (isSeeking) (seekPosition * duration).toLong() else currentPosition
            Text(formatTime(displayPosition), color = TextGray, fontSize = if (compact) 10.sp else 13.sp)
            Text(formatTime(duration), color = TextGray, fontSize = if (compact) 10.sp else 13.sp)
        }
    }
}
