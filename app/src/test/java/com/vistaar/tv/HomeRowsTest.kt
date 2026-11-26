package com.vistaar.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRowsTest {
    @Test fun pickedCategoriesComeFirstInPickOrder() = assertEquals(listOf(8, 12, 5, 6), HomeRows.order(listOf(8, 12), listOf(5, 6, 12, 8)))

    @Test fun emptyCategoriesAreLeftOutEvenIfPicked() = assertEquals(listOf(5, 6), HomeRows.order(listOf(8), listOf(5, 6)))

    @Test fun cappedUnlessMoreWerePicked() {
        assertEquals(listOf(1, 2), HomeRows.order(emptyList(), listOf(1, 2, 3), max = 2))
        assertEquals(listOf(3, 2, 1), HomeRows.order(listOf(3, 2, 1), listOf(1, 2, 3), max = 2))
    }

    @Test fun rowsMixJioByLanguageThenFree() {
        fun ch(id: Long, lang: Int, cat: Int, url: String = "") = Channel(id, "c$id", 0, lang, false, "", url = url, cat = cat)
        val rows = HomeRows.build(listOf(ch(1, 6, 8), ch(2, 1, 8)), listOf(ch(-1, -1, 0, "u")), listOf(8), listOf(1, 6)) { "Sports" }
        assertEquals(listOf(2L, 1L, -1L), rows.first().second.map { it.id })
    }

    @Test fun freeCategoryNamesMatchTheirGenre() {
        assertTrue(HomeRows.freeMatches(15, "Religious"))
        assertTrue(HomeRows.freeMatches(8, "sports"))
    }
}
