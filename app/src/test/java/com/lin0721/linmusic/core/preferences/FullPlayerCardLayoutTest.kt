package com.lin0721.linmusic.core.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// 播放页卡片顺序/显隐的持久化格式：改了默认值或字段名都会让用户已保存的配置失效，这里锁住行为
class FullPlayerCardLayoutTest {

    @Test
    fun `encode与decode可往返`() {
        val layout = listOf(
            FullPlayerCardSetting(FullPlayerCard.COMMENTS_PREVIEW, visible = true),
            FullPlayerCardSetting(FullPlayerCard.LYRICS, visible = false),
            FullPlayerCardSetting(FullPlayerCard.SIMILAR_ARTISTS, visible = true)
        )

        val decoded = FullPlayerCardLayout.decode(FullPlayerCardLayout.encode(layout))

        // 顺序按传入顺序保留，被隐藏的卡片仍然留在列表里（只是不展示），缺失的卡片补在末尾且默认可见
        assertEquals(FullPlayerCard.COMMENTS_PREVIEW, decoded[0].card)
        assertTrue(decoded[0].visible)
        assertEquals(FullPlayerCard.LYRICS, decoded[1].card)
        assertFalse(decoded[1].visible)
        assertEquals(FullPlayerCard.SIMILAR_ARTISTS, decoded[2].card)
        assertEquals(FullPlayerCard.entries.size, decoded.size)
    }

    @Test
    fun `空配置回退默认且全部可见`() {
        val decoded = FullPlayerCardLayout.decode(null)

        assertEquals(FullPlayerCard.entries.toList(), decoded.map { it.card })
        assertTrue(decoded.all { it.visible })
    }

    @Test
    fun `未知key被忽略重复key只保留一次`() {
        val decoded = FullPlayerCardLayout.decode("unknown:1,lyrics:0,lyrics:1,comments_preview:0")

        assertEquals(FullPlayerCard.entries.size, decoded.size)
        val lyrics = decoded.first { it.card == FullPlayerCard.LYRICS }
        assertFalse(lyrics.visible)
        assertFalse(decoded.first { it.card == FullPlayerCard.COMMENTS_PREVIEW }.visible)
    }
}
