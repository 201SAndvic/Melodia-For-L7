package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.preferences.*

@Composable
fun LyricInfoOutputSettingsCard(settings: LyricInfoOutputSettings, update: ((LyricInfoOutputSettings) -> LyricInfoOutputSettings) -> Unit) {
    @Composable
    fun Choice(title: String, detail: String, selected: Boolean, click: () -> Unit) {
        Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = click).padding(vertical = 10.dp)) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, fontSize = 16.sp)
                Text(detail, fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    SettingsGroupCard("LyricInfo · HUD输出设置") {
        Text("只影响LyricInfo，不改变主副屏歌词、蓝牙或其他歌词通道；设置自动保存。使用歌曲已有译文，没有译文的行保留原文。",
            fontSize = 12.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Choice("按语种选择", "默认中英文原文，其他明确语种优先译文；可在下方逐项选择", settings.mode == LyricInfoLanguageMode.AUTO) { update { it.copy(mode = LyricInfoLanguageMode.AUTO) } }
        Choice("全部使用原文", "不发送译文，不随应用内翻译开关变化", settings.mode == LyricInfoLanguageMode.ORIGINAL) { update { it.copy(mode = LyricInfoLanguageMode.ORIGINAL) } }
        Choice("全部优先译文", "所有语种有译文就使用译文", settings.mode == LyricInfoLanguageMode.TRANSLATION) { update { it.copy(mode = LyricInfoLanguageMode.TRANSLATION) } }
        if (settings.mode == LyricInfoLanguageMode.AUTO) {
            Text("开启表示该语种优先译文；关闭表示使用原文。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf("zh" to "中文（含国语、粤语等）", "en" to "英语", "ja" to "日语", "ko" to "韩语", "de" to "德语", "ru" to "俄语", "fr" to "法语", "es" to "西班牙语", "other" to "其他语种").forEach { (key, title) ->
                SettingsSwitchRow(title, "有译文时优先使用译文", key in settings.translatedLanguages) { checked -> update { it.forLanguage(key, checked) } }
            }
            SettingsSwitchRow("未知或混合语种优先译文", "接口缺少语种且文字特征无法确定时使用；默认关闭，避免英文误判", settings.translateUnknown) { checked -> update { it.copy(translateUnknown = checked) } }
            Text("优先使用并缓存歌曲百科语种。文字特征仅作兜底，不把所有拉丁字母当英文。整首选择，不承诺混合歌曲逐句识别；弱网下可手动选全部原文或译文。",
                fontSize = 12.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Choice("标准JSON输出", "沿用原接口，原文/译文字段统一为所选内容；适合正常解析JSON的软件", settings.format == LyricInfoFormat.JSON) { update { it.copy(format = LyricInfoFormat.JSON) } }
        Choice("HUD纯歌词兼容输出（试验）", "同一lyricInfo位置只发送带时间轴的歌词，不含JSON包装；出现末尾引号花括号或长串歌曲信息时尝试。若不显示，切回标准JSON", settings.format == LyricInfoFormat.HUD_LRC) { update { it.copy(format = LyricInfoFormat.HUD_LRC) } }
        SettingsSwitchRow("纯音乐与无歌词发送空白", "发送带时间轴的视觉空白字符，不标记为无歌词，避免HUD自行生成“纯音乐，请欣赏”；关闭则允许纯音乐提示", settings.clearInstrumental) { checked -> update { it.copy(clearInstrumental = checked) } }
        SettingsSwitchRow("过滤制作人员等信息行", "过滤“作词：”“作曲：”等明确资料行，不按长度误删正常歌词；只有资料的文件不作为歌词发送", settings.filterCredits) { checked -> update { it.copy(filterCredits = checked) } }
    }
}
