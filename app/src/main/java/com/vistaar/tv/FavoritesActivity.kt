package com.vistaar.tv

/** Favourites: the Free TV layout (categories | channels | preview) over the channels you saved, from your Jio plan and the free playlist. */
class FavoritesActivity : FreeTvActivity() {
    override fun pool() = Catalog.favourites()
    override fun catOf(ch: Channel) = if (ch.url.isEmpty()) "Jio TV" else "Free TV"
    override val orderKey: String? = null
    override fun catOrder(counts: Map<String, Int>) = listOf("Jio TV", "Free TV").filter { it in counts }
    override fun emptyMessage() = "No favourites yet. Hold OK on any channel to save it here."
    override val hint = "OK to watch  ·  Hold OK to remove  ·  ◀ categories"
    /** A removed favourite leaves the list. */
    override fun onFavToggled(ch: Channel) = refresh()
}
