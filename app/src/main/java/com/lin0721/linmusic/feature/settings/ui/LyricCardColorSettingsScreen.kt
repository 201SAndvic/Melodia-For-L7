package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.preferences.*
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.layout.LocalDisplayLyricCardColors
import com.lin0721.linmusic.core.ui.layout.LocalPassengerDisplay
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.feature.player.ui.LyricsCard
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricCardColorSettingsScreen(onBack: () -> Unit) {
    val preferences: SettingsPreferences = koinInject()
    val colors by preferences.displayLyricCardColors.collectAsStateWithLifecycle(initialValue = LocalDisplayLyricCardColors.current)
    val initialPassenger = LocalPassengerDisplay.current
    var passenger by remember { mutableStateOf(initialPassenger) }
    val saved = colors.forDisplay(passenger)
    var draft by remember(passenger, saved) { mutableStateOf(saved) }
    val scope = rememberCoroutineScope()
    fun update(field: LyricCardColorField, value: Int) {
        draft = draft.update(field, value)
        val target = passenger
        scope.launch { preferences.saveLyricCardColorField(target, field, value) }
    }
    fun preset(value: LyricCardColorSettings) {
        draft = value
        val target = passenger
        scope.launch { preferences.saveLyricCardColorPreset(target, value) }
    }
    @Composable
    fun ValueSlider(field: LyricCardColorField, title: String, description: String, value: Int,
                    range: IntRange, enabled: Boolean = draft.enabled) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("$value%", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(description, fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
            Slider(value = value.toFloat(), onValueChange = { draft = draft.update(field, it.roundToInt()) },
                onValueChangeFinished = {
                    val latest = when (field) {
                        LyricCardColorField.BRIGHTNESS -> draft.brightness
                        LyricCardColorField.SATURATION -> draft.saturation
                        LyricCardColorField.MAX_BRIGHTNESS -> draft.maxBrightness
                        LyricCardColorField.GRADIENT_STRENGTH -> draft.gradientStrength
                        LyricCardColorField.TEXT_TINT -> draft.textTint
                        else -> value
                    }
                    update(field, latest)
                }, valueRange = range.first.toFloat()..range.last.toFloat(),
                enabled = enabled, modifier = Modifier.fillMaxWidth().height(48.dp))
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("歌词卡片取色", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            navigationIcon = { MelodiaIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
    }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = MelodiaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
            contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(selected = !passenger, onClick = { passenger = false }, label = { Text("主驾 · 主屏") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = passenger, onClick = { passenger = true }, label = { Text("副驾 · 副屏") }, modifier = Modifier.weight(1f))
                }
                Text("正在调整${if (passenger) "副驾" else "主驾"}：两屏分别保存，按实际显示屏应用，不随横竖屏切换。只影响歌词预览卡片。",
                    fontSize = 12.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                SettingsGroupCard("示例预览（粉紫色，不是当前歌曲）") {
                    val previewColors = if (passenger) colors.copy(passenger = draft) else colors.copy(main = draft)
                    CompositionLocalProvider(LocalDisplayLyricCardColors provides previewColors,
                        LocalPassengerDisplay provides passenger) {
                        LyricsCard(lyrics = listOf(LyricLine(0, text = "In every breath there's life", translation = "生与死于瞬息之间"),
                            LyricLine(1000, text = "Between my teeth a knife", translation = "嘴里一直暗藏着杀机")),
                            currentIndex = 1, isLoading = false, base = Color(0xFFE090D0), onOpenFullScreen = {})
                    }
                }
            }
            item {
                SettingsGroupCard("${if (passenger) "副驾" else "主驾"}卡片配色") {
                    SettingsSwitchRow("启用背景封面取色", "关闭后背景固定深灰；文字取色由下方独立开关控制，背景参数保留", draft.enabled) {
                        update(LyricCardColorField.ENABLED, if (it) 1 else 0)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { preset(LyricCardColorSettings.ORIGINAL) }, modifier = Modifier.weight(1f)) { Text("原版") }
                        OutlinedButton(onClick = { preset(LyricCardColorSettings.SOFT) }, modifier = Modifier.weight(1f)) { Text("柔和") }
                        OutlinedButton(onClick = { preset(draft.copy(enabled = false, textFollowCover = false)) }, modifier = Modifier.weight(1f)) { Text("深灰") }
                    }
                    Text("原版保留现有效果；柔和降低明亮、鲜艳颜色；深灰关闭当前屏背景与文字取色，可再单独开启文字取色。",
                        fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    ValueSlider(LyricCardColorField.BRIGHTNESS, "背景亮度", "相对原版亮度，100%为原效果；不调整屏幕背光", draft.brightness, 20..100)
                    ValueSlider(LyricCardColorField.SATURATION, "颜色饱和度", "相对原版，0%为灰阶、100%为原色、150%更鲜艳", draft.saturation, 0..150)
                    SettingsSwitchRow("限制过亮颜色", "在亮度调整后限制颜色明度上限；暗色不提亮，保留原色相", draft.limitBrightness) {
                        update(LyricCardColorField.LIMIT_BRIGHTNESS, if (it) 1 else 0)
                    }
                    ValueSlider(LyricCardColorField.MAX_BRIGHTNESS, "颜色明度上限", "HSV明度上限，不是整机屏幕亮度；较低值更柔和", draft.maxBrightness, 10..65,
                        enabled = draft.enabled && draft.limitBrightness)
                    ValueSlider(LyricCardColorField.GRADIENT_STRENGTH, "静态渐变强度", "0%为纯色，100%为原版光斑；无持续游走动画", draft.gradientStrength, 0..100)
                    SettingsSwitchRow("歌词字体跟随封面取色", "独立于背景开关，深灰模式也可开启；非当前歌词和翻译跟随封面颜色，当前歌词保持白色高亮", draft.textFollowCover) {
                        update(LyricCardColorField.TEXT_FOLLOW_COVER, if (it) 1 else 0)
                    }
                    ValueSlider(LyricCardColorField.TEXT_TINT, "非当前歌词染色", "0%为中性白，100%为原封面染色；当前歌词始终白色", draft.textTint, 0..100,
                        enabled = draft.textFollowCover)
                    Text("拖动时仅本页示例预览变化，松手后保存并应用到目标屏，避免连续写盘。\n关闭卡片取色不会停掉播放页本身的封面取色，因为页面背景仍需要它。",
                        fontSize = 12.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }
    }
}
