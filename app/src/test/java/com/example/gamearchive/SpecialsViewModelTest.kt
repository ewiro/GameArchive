package com.example.gamearchive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialsViewModelTest {
    private val viewModel = SpecialsViewModel()

    @Test
    fun parsesGameFieldsAndDeduplicatesNames() {
        val row = """
            <a href="/app/10" data-ds-appid="10">
              <img src="https://example.com/header.jpg">
              <span class="title">Game Name</span>
              <div class="discount_pct">-50%</div>
              <div class="discount_original_price">¥ 100.00</div>
              <div class="discount_final_price">¥ 50.00</div>
              <span data-tooltip-html="95% positive"></span>
            </a>
        """.trimIndent()

        val result = viewModel.parseSteamSearchHtml(row + row)

        assertEquals(1, result.size)
        assertEquals(10, result.single().id)
        assertEquals("Game Name", result.single().name)
        assertEquals(50, result.single().discount)
        assertEquals(50.0, result.single().priceVal, 0.0)
        assertEquals(95, result.single().reviewScore)
    }

    @Test
    fun filtersBundlesAndAddOnContent() {
        val html = """
            <a href="/bundle"><span class="title">Bundle Name</span></a>
            <a href="/app/20" data-ds-appid="20"><span class="title">Game DLC</span></a>
        """.trimIndent()

        assertTrue(viewModel.parseSteamSearchHtml(html).isEmpty())
    }
}
