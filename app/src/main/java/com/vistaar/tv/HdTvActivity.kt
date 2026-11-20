package com.vistaar.tv

/** HD tab: every HD channel, from your Jio plan and from the free playlist, in the Free TV layout. */
class HdTvActivity : FreeTvActivity() {
    override val hdOnly = true
    override fun pool() = Catalog.playableChannels().filter { it.hd } + Catalog.freeChannels().filter { it.hd }
    override fun catOf(ch: Channel) = if (ch.url.isEmpty()) "Jio TV" else "Free TV"
    override fun catOrder(counts: Map<String, Int>) = listOf("Jio TV", "Free TV").filter { it in counts }
}
