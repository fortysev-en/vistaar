package com.vistaar.tv

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** Today's programmes per channel, fetched on demand and cached for two minutes. Main-thread API. */
object Epg {
    private val cache = HashMap<Long, Pair<Long, List<Program>>>()
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun cached(id: Long): List<Program>? = cache[id]?.takeIf { System.currentTimeMillis() - it.first < 120_000 }?.second

    fun load(id: Long, done: (List<Program>) -> Unit) {
        cached(id)?.let { return done(it) }
        pool.execute { val l = Api.epg(id); main.post { cache[id] = System.currentTimeMillis() to l; done(l) } }
    }

    fun now(list: List<Program>): Program? { val t = System.currentTimeMillis(); return list.firstOrNull { t in it.start..it.end } }
    fun next(list: List<Program>): Program? = now(list)?.let { c -> list.firstOrNull { it.start >= c.end } }
}

/**
 * Channel list, "which channels does this account actually get a stream for" and favourites.
 * All state is touched on the main thread only; workers post results back.
 */
object Catalog {
    private const val CACHE_MS = 6 * 3600_000L
    private val main = Handler(Looper.getMainLooper())
    private var gen = 0

    var all: List<Channel> = emptyList(); private set
    val playable = HashSet<Long>()
    var scanned = 0; private set
    var scanning = false; private set
    var error: String? = null; private set
    var onChange: (() -> Unit)? = null

    private val favs = LinkedHashSet<Long>()
    private val fire = Runnable { onChange?.invoke() }
    private fun changed() { main.removeCallbacks(fire); main.postDelayed(fire, 400) }

    fun playableChannels() = all.filter { it.id in playable }
    fun isFav(id: Long) = id in favs
    fun favourites() = all.filter { it.id in favs && it.id in playable }

    fun toggleFav(id: Long): Boolean {
        if (!favs.remove(id)) favs.add(id)
        Store.prefs.edit().putString("favs", favs.joinToString(",")).apply()
        return id in favs
    }

    fun favCount() = favs.size

    /** Most recently watched channel ids, newest first (UI convenience; playback logic doesn't use it). */
    private fun recentIds() = Store.prefs.getString("recent", "")!!.split(",").mapNotNull { it.toLongOrNull() }
    fun pushRecent(id: Long) {
        val l = (listOf(id) + recentIds().filter { it != id }).take(12)
        Store.prefs.edit().putString("recent", l.joinToString(",")).apply()
    }
    fun recents(): List<Channel> = recentIds().mapNotNull { id -> all.firstOrNull { it.id == id && it.id in playable } }
    fun clearRecents() { Store.prefs.edit().remove("recent").apply() }

    /** Number shown for a channel: its position among the channels the plan includes. */
    fun numberOf(id: Long) = playableChannels().indexOfFirst { it.id == id } + 1

    fun clearFavs() {
        favs.clear(); Store.prefs.edit().remove("favs").apply()
    }

    fun reset() {
        gen++; all = emptyList(); playable.clear(); scanning = false; scanned = 0; error = null
        Store.prefs.edit().remove("ok").remove("okAt").apply()
    }

    /** Loads the list and tests every channel (4 at a time); a fresh cache of playable ids skips the test. */
    fun start(force: Boolean) {
        if (!force && (scanning || all.isNotEmpty())) { changed(); return }
        if (favs.isEmpty()) favs.addAll(Store.prefs.getString("favs", "")!!.split(",").mapNotNull { it.toLongOrNull() })
        val g = ++gen
        scanning = true; error = null; scanned = 0; playable.clear(); if (force) all = emptyList()
        changed()
        Thread {
            try {
                try { Api.beginSession() } catch (e: Exception) { android.util.Log.d("Vistaar", "beginsession failed: $e") }
                val c = Api.channels()
                val sp = Store.prefs
                val cached = sp.getString("ok", "")!!.split(",").mapNotNull { it.toLongOrNull() }.toSet()
                val fresh = !force && cached.isNotEmpty() && System.currentTimeMillis() - sp.getLong("okAt", 0) < CACHE_MS
                main.post {
                    if (g != gen) return@post
                    all = c
                    if (fresh) { playable.addAll(cached); scanning = false; changed() } else scan(c, g)
                }
            } catch (e: Exception) {
                main.post { if (g == gen) { error = "Channel list failed: ${e.message}"; scanning = false; changed() } }
            }
        }.start()
    }

    private fun scan(c: List<Channel>, g: Int) {
        val pool = Executors.newFixedThreadPool(4)
        c.forEach { ch ->
            pool.execute {
                val ok = try { Api.streamUrl(ch, Api.nowPlaying(ch.id)); true } catch (e: Exception) { false }
                main.post {
                    if (g != gen) return@post
                    if (ok) playable.add(ch.id)
                    if (++scanned == c.size) {
                        scanning = false
                        Store.prefs.edit().putString("ok", playable.joinToString(",")).putLong("okAt", System.currentTimeMillis()).apply()
                    }
                    changed()
                }
            }
        }
        pool.shutdown()
    }
}
