package com.lin0721.linmusic.core.ui.layout

import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 当前窗口（屏幕）的尺寸信息，供各页面做断点判断。
 *
 * 车机副屏这类「屏幕很大、但上报密度只有 1.0」的设备上，界面按裸 dp 铺开会又小又散：
 * [MelodiaWindowProvider] 会在超宽窗口下把整棵组合树的 density 统一放大一档
 * （等价于把这台设备当成一台更窄的设备来布局），这里暴露的 width/height
 * 已经是换算回设计尺寸之后的值，页面直接用即可，不用关心是否被放大过。
 */
@Immutable
data class MelodiaWindowInfo(
    val width: Dp,
    val height: Dp,
    val isLandscape: Boolean,
    val uiScale: Float,
    // 放大前的系统密度（px/dp）：抽屉/弹窗这类独立窗口拿不到放大后的 LocalDensity，
    // 需要用「基准密度 × uiScale」重新补一遍
    val baseDensity: Float = 1f
) {
    /** 窄窗口：手机竖屏、车机主屏 */
    val isCompact: Boolean get() = width < 600.dp

    /** 宽窗口：横屏车机、平板、桌面模式 */
    val isExpanded: Boolean get() = width >= 840.dp

    /** 播放页是否横排双栏：横屏且足够宽时，封面在左、控件与详情卡片在右 */
    val useTwoPanePlayer: Boolean get() = isLandscape && isExpanded
}

val LocalMelodiaWindow = staticCompositionLocalOf {
    MelodiaWindowInfo(width = 360.dp, height = 640.dp, isLandscape = false, uiScale = 1f)
}

// 宽屏归一化基准：横屏窗口（车机副屏 1973dp、桌面模式 2880dp）收敛到接近 1480dp 的设计宽度。
// 界面按 dp 铺开的物理大小就和车机主屏一致，不超过这个宽度时完全不动
private const val WideBaselineDp = 1480f

// 大画布归一化：短边达到这个值的窗口（车机主屏 1440×1920dp、平板竖屏）才做面板密度对齐，
// 手机上永远不触发，避免影响正常设备
private const val LargeCanvasShortSideDp = 900f
private const val LargeCanvasBaselineDp = 745f

// 只对「系统上报密度明显偏低」的屏幕做对齐：240dpi(=1.5x) 以上的设备密度本来就配得合理
private const val LowDensityThresholdDpi = 240

// 面板密度高于系统密度这个比例，才算「屏幕没按真实密度渲染」
private const val PanelDensityGapThreshold = 1.25f

// 判定「设备没上报真实物理密度」的阈值：xdpi 与系统密度几乎相等时退回按画布短边归一化
private const val PanelDensityMissingThreshold = 1.02f

// 归一化后的目标渲染密度上限（px/dp）：约等于常见手机的水平，避免 xdpi 乱报时界面被放得过大
private const val MaxNormalDensity = 2.2f

private const val MaxUiScale = 1.95f

/**
 * 计算界面整体放大倍数。
 *
 * @param xdpi 面板真实像素密度，@param densityDpi 系统上报密度。
 * 车机主屏这类「7.5 英寸 1440×1920、真实约 320dpi，却按 160dpi 上报」的屏幕，
 * 界面按 dp 铺开会只有正常手机的一半大小；这里按面板真实密度把它放大回来。
 * 密度本来就配得合理的设备（手机、平板）不会被放大：放大倍数下限固定为 1。
 */
internal fun melodiaUiScaleFor(
    screenWidthDp: Int,
    screenHeightDp: Int,
    xdpi: Float,
    densityDpi: Int
): Float {
    val longSide = maxOf(screenWidthDp, screenHeightDp).toFloat()
    val shortSide = minOf(screenWidthDp, screenHeightDp).toFloat()
    val wideTerm = longSide / WideBaselineDp
    val density = densityDpi / 160f
    val panelDensity = xdpi / 160f
    val largeCanvasTerm = if (shortSide < LargeCanvasShortSideDp || densityDpi !in 1..LowDensityThresholdDpi) {
        0f
    } else {
        when {
            // 面板密度可信且明显高于系统密度：放大到贴近面板真实密度（再压一个上限）
            panelDensity > density * PanelDensityGapThreshold ->
                minOf(panelDensity, MaxNormalDensity) / density
            // 设备没上报物理密度：退回按画布短边归一化，保证车机主屏不会又小又挤
            panelDensity <= density * PanelDensityMissingThreshold -> shortSide / LargeCanvasBaselineDp
            else -> 0f
        }
    }
    return maxOf(1f, wideTerm, largeCanvasTerm).coerceAtMost(MaxUiScale)
}

@Composable
fun MelodiaWindowProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val displayMetrics = context.resources.displayMetrics
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val uiScale = remember(screenWidthDp, screenHeightDp, displayMetrics.xdpi, displayMetrics.densityDpi) {
        melodiaUiScaleFor(
            screenWidthDp = screenWidthDp,
            screenHeightDp = screenHeightDp,
            xdpi = displayMetrics.xdpi,
            densityDpi = displayMetrics.densityDpi
        )
    }
    val windowInfo = remember(screenWidthDp, screenHeightDp, uiScale) {
        MelodiaWindowInfo(
            width = (screenWidthDp / uiScale).dp,
            height = (screenHeightDp / uiScale).dp,
            isLandscape = screenWidthDp > screenHeightDp,
            uiScale = uiScale,
            baseDensity = density.density
        )
    }
    if (uiScale == 1f) {
        CompositionLocalProvider(LocalMelodiaWindow provides windowInfo, content = content)
    } else {
        val scaledDensity = remember(density, uiScale) {
            Density(density = density.density * uiScale, fontScale = density.fontScale)
        }
        CompositionLocalProvider(
            LocalMelodiaWindow provides windowInfo,
            LocalDensity provides scaledDensity,
            content = content
        )
    }
}

/**
 * 底部抽屉 / 弹窗（Compose 的 Dialog、Popup）内容的密度补偿。
 *
 * 这些容器渲染在独立窗口里，窗口密度只认系统上报值，页面主体那层放大的 LocalDensity 传不进去；
 * 车机上把界面放大到 1.95 倍后，抽屉里的字号只有正常界面的一半（小到看不清）。
 * 这里在抽屉内部再补一层目标密度，幂等实现：已经达到目标密度就不再重复放大。
 */
@Composable
fun MelodiaSheetDensity(content: @Composable () -> Unit) {
    val window = LocalMelodiaWindow.current
    val density = LocalDensity.current
    val targetDensity = window.baseDensity * window.uiScale
    if (window.uiScale <= 1f || density.density >= targetDensity - 0.01f) {
        content()
    } else {
        val scaled = remember(density, targetDensity) {
            Density(density = targetDensity, fontScale = density.fontScale)
        }
        CompositionLocalProvider(LocalDensity provides scaled, content = content)
    }
}

// 抽屉/弹窗的宽度上限：放大过的设备上按放大后的密度算（640dp × 1.95 ≈ 全屏宽），
// 未放大的设备保持 Material 默认值，行为完全不变
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
val MelodiaSheetWidth: Dp
    @Composable get() =
        if (LocalMelodiaWindow.current.uiScale > 1f) Dp.Unspecified
        else BottomSheetDefaults.SheetMaxWidth

// 播放页大封面：参考手机端比例（约 88% 屏宽、约 40% 屏高），并压一个绝对上限，
// 避免车机这种又宽又高的屏幕上封面撑满整屏、把歌名与控制区挤到屏幕外
private val MaxPlayerCoverSize = 340.dp
private val MinPlayerCoverSize = 180.dp

fun MelodiaWindowInfo.playerCoverSize(): Dp =
    minOf(width * 0.88f, height * 0.42f, MaxPlayerCoverSize)
        .coerceAtLeast(MinPlayerCoverSize)
