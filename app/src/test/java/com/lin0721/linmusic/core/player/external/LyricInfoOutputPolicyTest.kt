package com.lin0721.linmusic.core.player.external

import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.LyricParser
import com.lin0721.linmusic.core.player.domain.WordInfo
import com.lin0721.linmusic.core.preferences.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LyricInfoOutputPolicyTest {
    private val options = LyricInfoOutputSettings()
    private val lines = listOf(LyricLine(1200, text = "Original", translation = "中文译文", words = listOf(WordInfo("Original", 0, 800))))
    private fun select(language: String, settings: LyricInfoOutputSettings = options) = LyricInfoOutputPolicy.select(lines, language, settings)
    private fun payload(input: List<LyricLine> = lines, language: String = "日语", settings: LyricInfoOutputSettings = options) =
        LyricInfoOutputPolicy.build("Song", "Artist", "123", "Album", input, language, settings)

    @Test fun chineseAndEnglishUseOriginalButOtherLanguagesTranslate() {
        listOf("中文", "国语", "粤语", "闽南语", "English", "en").forEach { assertEquals(it, "Original", select(it).single().text) }
        listOf("日语", "德语", "俄语", "韩语", "法语", "西班牙语", "意大利语", "阿拉伯语").forEach { assertEquals(it, "中文译文", select(it).single().text) }
    }
    @Test fun allOriginalAndAllTranslationOverrideLanguageMetadata() {
        assertEquals("Original", select("日语", options.copy(mode = LyricInfoLanguageMode.ORIGINAL)).single().text)
        assertEquals("中文译文", select("英语", options.copy(mode = LyricInfoLanguageMode.TRANSLATION)).single().text)
    }
    @Test fun userMayTranslateEnglishAndKeepGermanOriginal() {
        val custom = options.forLanguage("en", true).forLanguage("de", false)
        assertEquals("中文译文", select("英语", custom).single().text)
        assertEquals("Original", select("德语", custom).single().text)
    }
    @Test fun translationFallsBackPerLineAndDropsOriginalWordTimesOnlyForReplacedLines() {
        val result = LyricInfoOutputPolicy.select(lines + LyricLine(2500, text = "No translation"), "日语", options)
        assertEquals(listOf("中文译文", "No translation"), result.map { it.text })
        assertEquals(1200L, result[0].timeMs)
        assertTrue(result[0].words.isEmpty())
        assertEquals(lines.single().words, select("英语").single().words)
        assertTrue(result.all { it.translation == null && it.roma == null })
    }
    @Test fun missingMetadataUsesScriptButNeverAssumesLatinMeansEnglish() {
        assertEquals("Original", select("").single().text)
        assertEquals("中文译文", select("", options.copy(translateUnknown = true)).single().text)
        assertEquals("ja", LyricInfoOutputPolicy.scriptLanguage("君のこと"))
        assertEquals("ko", LyricInfoOutputPolicy.scriptLanguage("사랑"))
        assertEquals("other", LyricInfoOutputPolicy.scriptLanguage("Привет"))
        assertNull(LyricInfoOutputPolicy.scriptLanguage("Ich liebe dich"))
        assertNull(LyricInfoOutputPolicy.scriptLanguage("Hello"))
    }
    @Test fun allLanguageLabelsAreReadAndUnknownMixedDoesNotGetFalseSingleLanguage() {
        assertEquals(setOf("zh", "en"), LyricInfoOutputPolicy.languageKeys("国语 / 英语"))
        assertEquals("Original", select("国语 / 英语").single().text)
        assertEquals("Original", select("日语 / 英语").single().text)
        assertEquals("中文译文", select("德语 / 法语").single().text)
        assertEquals(emptySet<String>(), LyricInfoOutputPolicy.languageKeys("未知"))
    }
    @Test fun jsonFieldsAllRespectSameSelectionWithoutOriginalWordsInTranslation() {
        val json = JSONObject(payload())
        assertEquals(json.getString("lyric"), json.getString("translationLyric"))
        assertFalse(json.getString("lyric").contains("Original"))
        assertFalse(json.getString("rawLyric").contains("Original"))
        assertEquals("中文译文", LyricParser.parseLrc(json.getString("lyric")).single().text)
        assertFalse(json.getString("lyric").contains("\"}"))
    }
    @Test fun hudOutputIsOnlyLrcWithNoJsonOrSongMetadataAndPreservesLegitimateQuotes() {
        val output = payload(settings = options.copy(format = LyricInfoFormat.HUD_LRC))
        assertEquals("[00:01.200]中文译文\n", output)
        assertFalse(output.contains("songName"))
        assertFalse(output.contains("\"}"))
        val quote = payload(listOf(LyricLine(1000, text = "He said \"hello\"")), settings = options.copy(format = LyricInfoFormat.HUD_LRC))
        assertTrue(quote.contains("He said \"hello\""))
    }
    @Test fun instrumentalAndCreditOnlyFilesClearWithoutLeakingSongMetadataInHudMode() {
        val hud = options.copy(format = LyricInfoFormat.HUD_LRC)
        listOf(emptyList(), listOf(LyricLine(0, text = "纯音乐")), listOf(LyricLine(0, text = "Instrumental")),
            listOf(LyricLine(0, text = "作曲：某某"), LyricLine(1000, text = "专辑：专辑名称"))).forEach {
            assertEquals("[00:00.000]\u2800\n", payload(it, settings = hud))
        }
        assertTrue(payload(emptyList(), settings = hud.copy(clearInstrumental = false)).contains("纯音乐"))
    }
    @Test fun creditsFilteredButLongLyricsAndWordsInsideLyricsRemain() {
        val input = listOf(LyricLine(0, text = "作词：某某"), LyricLine(1000, text = "我在这首歌里提到了作词：这仍然是歌词"))
        assertEquals(input.drop(1), LyricInfoOutputPolicy.select(input, "中文", options))
        assertEquals(input, LyricInfoOutputPolicy.select(input, "中文", options.copy(filterCredits = false)))
    }
    @Test fun jsonNoLyricsHasExplicitClearingContentsForEveryField() {
        val json = JSONObject(payload(listOf(LyricLine(0, text = "纯音乐"))))
        assertFalse(json.getBoolean("noLyric"))
        assertFalse(json.has("songName"))
        assertFalse(json.has("artist"))
        assertFalse(json.has("album"))
        listOf("lyric", "translationLyric", "rawLyric").forEach { assertEquals("[00:00.000]\u2800\n", json.getString(it)) }
    }
    @Test fun visualBlankSurvivesLrcTrimmingAndDoesNotTriggerAnInstrumentalFlag() {
        assertFalse(LyricInfoOutputPolicy.HUD_BLANK.isBlank())
        val lrc = JSONObject(payload(emptyList())).getString("lyric")
        assertEquals(LyricInfoOutputPolicy.HUD_BLANK, LyricParser.parseLrc(lrc).single().text)
        assertFalse(lrc.contains("纯音乐"))
        assertFalse(lrc.contains("欣赏"))
        assertTrue(LyricInfoOutputPolicy.isInstrumental("此歌曲没有填词的纯音乐，请您欣赏"))
        assertTrue(LyricInfoOutputPolicy.isInstrumental("纯音乐，请欣赏"))
    }
    @Test fun disablingSilenceStillExplicitlyRequestsTheInstrumentalMessage() {
        val json = JSONObject(payload(emptyList(), settings = options.copy(clearInstrumental = false)))
        assertTrue(json.getBoolean("noLyric"))
        assertTrue(json.getString("lyric").contains("纯音乐"))
    }
    @Test fun translationMetadataIsNotUsedAsLyricsAndInputObjectsAreNotMutated() {
        val input = listOf(LyricLine(1000, text = "Original", translation = "作曲：某某"))
        assertEquals("Original", LyricInfoOutputPolicy.select(input, "日语", options).single().text)
        assertEquals("作曲：某某", input.single().translation)
    }
}
