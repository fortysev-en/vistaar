package com.vistaar.tv

/** The category rows on Home: the viewer's favourite categories first, in their order, then the others. */
object HomeRows {
    /** Rows shown at most, unless more categories than this were picked. */
    const val MAX = 8

    /** Free-playlist category names that belong to a Jio genre but are spelled differently. */
    private val FREE_ALIASES = mapOf(7 to listOf("Animation"), 15 to listOf("Religious"), 17 to listOf("Education"), 10 to listOf("Documentary", "Science"), 18 to listOf("Shop"))

    fun freeMatches(genre: Int, freeCategory: String): Boolean =
        freeCategory.equals(GENRES[genre], ignoreCase = true) || FREE_ALIASES[genre].orEmpty().any { it.equals(freeCategory, ignoreCase = true) }

    /** Genre ids to show: [picked] first in pick order, then the rest of [available] in the usual order; empty categories are left out. */
    fun order(picked: List<Int>, available: List<Int>, max: Int = MAX): List<Int> {
        val first = picked.filter { it in available }
        return (first + available.filter { it !in first }).take(maxOf(max, first.size))
    }

    /** Each row's channels: Jio first (the chosen languages in the order picked), then free channels of the same category. */
    fun build(jio: List<Channel>, free: List<Channel>, picked: List<Int>, langs: List<Int>, freeCategory: (Channel) -> String): List<Pair<Int, List<Channel>>> {
        val rank = { c: Channel -> langs.indexOf(c.lang).let { if (it < 0) langs.size else it } }
        val byGenre = GENRES.keys.associateWith { g -> jio.filter { it.cat == g }.sortedBy(rank) + free.filter { freeMatches(g, freeCategory(it)) } }
        return order(picked, byGenre.keys.filter { byGenre.getValue(it).isNotEmpty() }).map { it to byGenre.getValue(it) }
    }
}
