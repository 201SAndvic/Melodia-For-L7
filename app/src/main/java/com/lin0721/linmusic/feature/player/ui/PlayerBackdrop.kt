package com.lin0721.linmusic.feature.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.layout.LocalMelodiaWindow
import com.lin0721.linmusic.core.ui.theme.darken
import com.lin0721.linmusic.core.ui.theme.saturate

enum class BackdropMode { Collapsed, Immersive }

// 用原生 Modifier.blur 软化渐变/光斑
private val BACKDROP_BLUR_RADIUS = 60.dp

// Collapsed（播放页 Hero）渐变的终点位置，取原呼吸动画的中间值
private const val CollapsedGradientEndY = 1100f

// Immersive（全屏歌词）背景光斑的固定位置/半径，取原游走动画区间的中值
private const val ImmersiveBlobCenterX = 1.2f
private const val ImmersiveBlobCenterY = 1.25f
private const val ImmersiveBlobRadiusScale = 0.6f

// 单一色相的模糊光斑：深色底 + lighten/darken 变体，Immersive 背景与歌词预览卡共用
// fill 必须是明显压暗过的变体，不能直接传未处理的 base——base 现在取自 Vibrant
// lightBlob 为 null 时只画 darkBlob 这一枚
internal fun DrawScope.drawSingleHueMesh(
    fill: Color,
    lightBlob: Color? = null,
    lightCenter: Offset = Offset.Zero,
    lightRadius: Float = 0f,
    darkBlob: Color,
    darkCenter: Offset,
    darkRadius: Float,
    darkOpacity: Float = 0.6f
) {
    drawRect(color = fill)
    if (lightBlob != null) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(lightBlob.copy(alpha = 0.5f), Color.Transparent),
                center = lightCenter,
                radius = lightRadius
            ),
            center = lightCenter,
            radius = lightRadius
        )
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(darkBlob.copy(alpha = darkOpacity.coerceIn(0f, 1f)), Color.Transparent),
            center = darkCenter,
            radius = darkRadius
        ),
        center = darkCenter,
        radius = darkRadius
    )
}

// 全屏播放器背景：Collapsed（Hero）单色 alpha 渐隐，Immersive（歌词全屏）静态光斑。
// 背景绘制和 content 拆成两个子 Box：只模糊背景层，content（Immersive 态下是实际歌词内容）保持清晰
@Composable
fun PlayerBackdrop(
    base: Color,
    mode: BackdropMode,
    modifier: Modifier = Modifier,
    translationYProvider: () -> Float = { 0f },
    content: @Composable BoxScope.() -> Unit = {}
) {
    when (mode) {
        BackdropMode.Collapsed -> {
            val density = LocalDensity.current
            val useTwoPane = LocalMelodiaWindow.current.useTwoPanePlayer
            // 渐变终点固定，不再做「呼吸」无限动画。
            // 这一层是全屏大小的模糊层，动画每帧都会让整层重新绘制并重新提交模糊，
            // 实测把播放页压到个位数帧率（车机这类弱 GPU 上直接表现为整页卡死），
            // 而它带来的视觉变化几乎看不出来。
            val portraitGradientEndY = with(density) { CollapsedGradientEndY.dp.toPx() }

            // 跟歌单页顶栏/搜索栏/大封面渐变同一套处理：base 直接用太亮，先压暗一档
            val fillColor = remember(base) { base.darken(0.35f) }

            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .then(
                        if (useTwoPane) {
                            // Cover the complete viewport; fade smoothly into its black host.
                            Modifier.fillMaxHeight()
                        } else {
                            // 竖屏按原有高度限制，减少模糊层面积。
                            Modifier.height(minOf(1200.dp, LocalMelodiaWindow.current.height))
                        }
                    )
                    .graphicsLayer { translationY = translationYProvider() }
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(BACKDROP_BLUR_RADIUS)
                        .drawBehind {
                            val gradientEndY = collapsedBackdropGradientEndY(useTwoPane, size.height, portraitGradientEndY)
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0.0f to fillColor,
                                    0.2f to fillColor.copy(alpha = 0.75f),
                                    0.45f to fillColor.copy(alpha = 0.45f),
                                    0.62f to fillColor.copy(alpha = 0.08f),
                                    0.76f to Color.Transparent,
                                    startY = 0f,
                                    endY = gradientEndY
                                )
                            )
                        }
                )
                content()
            }
        }

        BackdropMode.Immersive -> {
            // 全屏歌词页背景同样去掉无限游走：它是一层整屏模糊层，
            // 每帧重新绘制等于每帧重新模糊一次整屏，弱 GPU 设备上直接把歌词页拖到个位数帧率
            val darkCenterX = ImmersiveBlobCenterX
            val darkCenterY = ImmersiveBlobCenterY
            val darkRadiusScale = ImmersiveBlobRadiusScale

            val vividBase = remember(base) { base.saturate(0.6f) }
            val fillColor = remember(vividBase) { vividBase.darken(0.35f) }
            val darkBlob = remember(vividBase) { vividBase.darken(0.15f) }

            Box(modifier = modifier) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(BACKDROP_BLUR_RADIUS)
                        .drawBehind {
                            val baseSize = size.minDimension
                            drawSingleHueMesh(
                                fill = fillColor,
                                darkBlob = darkBlob,
                                darkCenter = Offset(size.width * darkCenterX, size.height * darkCenterY),
                                darkRadius = baseSize * darkRadiusScale
                            )
                        }
                )
                content()
            }
        }
    }
}
