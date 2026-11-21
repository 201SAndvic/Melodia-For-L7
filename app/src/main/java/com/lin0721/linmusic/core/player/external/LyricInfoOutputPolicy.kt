package com.lin0721.linmusic.core.player.external

import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.preferences.LyricInfoFormat
import com.lin0721.linmusic.core.preferences.LyricInfoLanguageMode
import com.lin0721.linmusic.core.preferences.LyricInfoOutputSettings
import java.util.Locale
import org.json.JSONObject

/** Selection is performed once per lyrics/settings/language change, never per playback tick. */
object LyricInfoOutputPolicy {
    // Braille blank stays visually empty but survives String.trim/isBlank in LRC bridges.
    // Ordinary ASCII spaces are discarded by many readers and trigger a no-lyrics message.
    const val HUD_BLANK = "\u2800"
    private val credits = Regex("""^(?:作词|作曲|编曲|词曲|制作人|制作|混音|母带|录音|演唱|歌手|专辑|歌曲|词|曲|发行|出品|版权|词作者|曲作者|录音师|混音师|制作公司|发行公司|OP|SP|ISRC|Composer|Lyricist|Arranger|Producer|Mixed by|Mastered by|Written by|Lyrics by|Composed by|Artist|Album|Title|Copyright)\s*[:：].*$""", RegexOption.IGNORE_CASE)
    private val instrumental = Regex("""^(?:纯音乐(?:[，,。\s]*(?:请欣赏|敬请欣赏))?[。.!！]?|instrumental|instrumental music|no lyrics|music only)$""", RegexOption.IGNORE_CASE)
    private val aliases = mapOf(
        "zh" to setOf("中文", "汉语", "国语", "华语", "普通话", "粤语", "闽南语", "台语", "客家语", "chinese", "mandarin", "cantonese", "zh", "zh-cn", "zh-tw"),
        "en" to setOf("英语", "英文", "english", "en"),
        "ja" to setOf("日语", "日文", "japanese", "ja", "jp"),
        "ko" to setOf("韩语", "韩文", "朝鲜语", "korean", "ko"),
        "de" to setOf("德语", "德文", "german", "deutsch", "de"),
        "ru" to setOf("俄语", "俄文", "russian", "ru"),
        "fr" to setOf("法语", "法文", "french", "fr"),
        "es" to setOf("西班牙语", "西语", "spanish", "es"))
    private val noLanguage = setOf("", "未知", "未知语种", "unknown", "其他", "其它", "多语种", "多语言", "混合", "mixed")

    fun languageKeys(label: String): Set<String> {
        val normalized = label.trim().lowercase(Locale.ROOT)
        if (normalized in noLanguage) return emptySet()
        return normalized.split(Regex("[/、,，;&＋+|]" )).mapNotNull { part ->
            val text = part.trim()
            if (text in noLanguage) null else aliases.entries.firstOrNull { text in it.value }?.key ?: "other"
        }.toSet()
    }

    /** Script evidence is only a fallback. Latin letters alone cannot identify English. */
    fun scriptLanguage(text: String): String? = when {
        text.any { it in '\u3040'..'\u30ff' } -> "ja"
        text.any { it in '\uac00'..'\ud7af' || it in '\u1100'..'\u11ff' } -> "ko"
        text.any { it in '\u0400'..'\u052f' || it in '\u0600'..'\u06ff' || it in '\u0900'..'\u097f' || it in '\u0e00'..'\u0e7f' || it in '\u0370'..'\u03ff' } -> "other"
        else -> null
    }

    fun isInstrumental(text: String): Boolean {
        val value = text.trim().removeSurrounding("(", ")").removeSurrounding("（", "）")
        return instrumental.matches(value) || Regex("""^(?:此|本)?(?:歌曲|曲)?(?:是|为)?(?:没有填词的)?纯音乐[，,\s]*(?:请您|请|敬请)?欣赏[。.!！]?$""").matches(value)
    }

    fun isMetadata(text: String): Boolean {
        val value = text.trim()
        return credits.matches(value) || value.startsWith("http://") || value.startsWith("https://") ||
            (value.startsWith("{") && (value.contains("\"c\"") || value.contains("\"lyric\"") || value.contains("\"songName\"")))
    }

    fun select(lines: List<LyricLine>, language: String, settings: LyricInfoOutputSettings): List<LyricLine> {
        if (lines.any { isInstrumental(it.text) }) return emptyList()
        val clean = lines.filter { it.text.isNotBlank() && !isMetadata(it.text) }
        // Even with credit filtering disabled, credit-only files are not sung lyrics.
        if (clean.isEmpty()) return emptyList()
        val input = if (settings.filterCredits) clean else lines.filter { it.text.isNotBlank() }
        val keys = languageKeys(language)
        val fallback = scriptLanguage(clean.joinToString("\n") { it.text })
        val translate = when (settings.mode) {
            LyricInfoLanguageMode.ORIGINAL -> false
            LyricInfoLanguageMode.TRANSLATION -> true
            LyricInfoLanguageMode.AUTO -> when {
                keys.size == 1 -> keys.first() in settings.translatedLanguages
                keys.isNotEmpty() && keys.all { it in settings.translatedLanguages } -> true
                keys.isNotEmpty() && keys.none { it in settings.translatedLanguages } -> false
                fallback != null -> fallback in settings.translatedLanguages
                else -> settings.translateUnknown
            }
        }
        return input.map { line ->
            val translation = line.translation?.trim()?.takeIf { it.isNotEmpty() && !isMetadata(it) && !isInstrumental(it) }
            if (translate && translation != null) line.copy(text = translation, translation = null, roma = null, words = emptyList())
            else line.copy(translation = null, roma = null)
        }
    }

    fun build(songName: String, artist: String, songId: String, album: String, lines: List<LyricLine>,
              language: String, settings: LyricInfoOutputSettings): String {
        val selected = select(lines, language, settings)
        val silent = selected.isEmpty() && settings.clearInstrumental
        val outputLines = if (selected.isEmpty()) listOf(LyricLine(0, text = if (silent) HUD_BLANK else "纯音乐")) else selected
        val lrc = LyricInfoBuilder.buildPlainLrc(outputLines) + "\n"
        if (settings.format == LyricInfoFormat.HUD_LRC) return lrc
        // Empty tracks only need a clearing signal: never send an information-only
        // envelope containing title/artist/album which a fallback reader may print.
        val json = if (selected.isEmpty()) JSONObject() else
            JSONObject(LyricInfoBuilder.buildLyricInfoJson(songName, artist, songId, album, outputLines, showTranslation = false))
        // A true noLyric flag makes some HUDs generate their own "纯音乐，请欣赏".
        // For silence, publish a visually blank lyric rather than an absence of lyrics.
        json.put("noLyric", selected.isEmpty() && !silent)
        // Readers may prefer any of these fields. All must obey the selected language rule.
        json.put("lyric", lrc)
        json.put("translationLyric", lrc)
        json.put("rawLyric", if (selected.isEmpty()) lrc else LyricInfoBuilder.buildRawLyric(selected) + "\n")
        return json.toString()
    }
}
