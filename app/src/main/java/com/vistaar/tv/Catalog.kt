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
        // Free channels have no guide of their own; borrow the one of the same channel on the Jio plan, if there is one.
        if (id < 0) return Catalog.twinOf(id)?.let { load(it, done) } ?: done(emptyList())
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
    private const val RECHECK_MS = 10 * 60_000L
    private val main = Handler(Looper.getMainLooper())
    private var gen = 0

    var all: List<Channel> = emptyList(); private set
    val playable = HashSet<Long>()
    var scanned = 0; private set
    var scanning = false; private set
    var error: String? = null; private set
    var onChange: (() -> Unit)? = null

    private val favs = LinkedHashSet<Long>()
    private var pending = false
    private val fire = Runnable { pending = false; onChange?.invoke() }
    /** Throttled, not debounced: during a scan results arrive constantly, and a debounce would never fire until the scan ended. */
    private var twinFor = -1
    private val twinIdx = HashMap<String, Long>()
    private fun twinKey(n: String) = n.lowercase().replace(Regex("[(].*?[)]"), "").replace(Regex("[^a-z0-9]"), "").removeSuffix("hd")

    /** The playable Jio channel with the same name as free channel [id] ("StarPlus (India) HD" is "Star Plus HD"), or null. */
    fun twinOf(id: Long): Long? {
        val f = Free.shown.firstOrNull { it.id == id } ?: return null
        if (twinFor != playable.size) { twinIdx.clear(); playableChannels().forEach { twinIdx.putIfAbsent(twinKey(it.name), it.id) }; twinFor = playable.size }
        return twinKey(f.name).takeIf { it.length >= 4 }?.let { twinIdx[it] }
    }

    private fun changed() { if (!pending) { pending = true; main.postDelayed(fire, 700) } }

    /** Channels the plan includes, limited to the languages chosen on first run (none chosen = all). */
    fun playableChannels() = all.filter { it.id in playable && visible(it) }
    fun visible(c: Channel): Boolean { val l = Store.langs; return l.isEmpty() || c.lang !in LANGUAGES || c.lang in l }
    fun isFav(id: Long) = id in favs

    /** Channels that listed fine but failed to play twice in a row; remembered for 6 hours, then tried again. */
    private fun bad(): Map<Long, Long> = Store.prefs.getString("bad", "")!!.split(",").mapNotNull { e ->
        val p = e.split(":"); val id = p.getOrNull(0)?.toLongOrNull(); val t = p.getOrNull(1)?.toLongOrNull()
        if (id != null && t != null && System.currentTimeMillis() - t < CACHE_MS) id to t else null
    }.toMap()
    fun isBad(id: Long) = id in bad()
    private fun saveBad(m: Map<Long, Long>) = Store.prefs.edit().putString("bad", m.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
    fun markBad(id: Long) = saveBad(bad() + (id to System.currentTimeMillis()))
    fun clearBad(id: Long) { val m = bad(); if (id in m) saveBad(m - id) }
    fun favourites() = playableChannels().filter { it.id in favs } + Free.shown.filter { it.id in favs }
    fun freeChannels() = Free.shown

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
    fun recents(): List<Channel> = recentIds().mapNotNull { id ->
        if (id < 0) Free.shown.firstOrNull { it.id == id } else all.firstOrNull { it.id == id && it.id in playable }
    }
    fun clearRecents() { Store.prefs.edit().remove("recent").apply() }

    /** Number shown for a channel: its position among the channels the plan includes. */
    fun numberOf(id: Long) = (if (id < 0) Free.shown else playableChannels()).indexOfFirst { it.id == id } + 1

    fun clearFavs() {
        favs.clear(); Store.prefs.edit().remove("favs").apply()
    }

    fun reset() {
        gen++; all = emptyList(); playable.clear(); scanning = false; scanned = 0; error = null
        Store.prefs.edit().remove("ok").remove("okAt").apply()
    }

    /** Loads the list and tests every channel (4 at a time); a fresh cache of playable ids skips the test. */
    fun start(force: Boolean) {
        if (favs.isEmpty()) favs.addAll(Store.prefs.getString("favs", "")!!.split(",").mapNotNull { it.toLongOrNull() })
        Free.load(force) { changed() }
        Free.loadLangs { changed() }
        if (!Store.loggedIn) { changed(); return } // free-only mode: nothing to check against a Jio plan
        if (!force && (scanning || all.isNotEmpty())) { changed(); return }
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
                    if (fresh) {
                        playable.addAll(cached); scanning = false; changed()
                        // The plan may have changed since (a new channel pack): quietly re-test only what the cached scan refused.
                        val miss = c.filter { it.id !in cached }
                        if (miss.isNotEmpty() && System.currentTimeMillis() - sp.getLong("okAt", 0) > RECHECK_MS) scan(miss, g, quiet = true)
                    } else scan(c, g)
                }
            } catch (e: Exception) {
                main.post { if (g == gen) { error = "Channel list failed: ${e.message}"; scanning = false; changed() } }
            }
        }.start()
    }

    /**
     * Favourites, recents and the chosen languages go first so what the user cares about shows up straight away.
     * With [quiet] (a re-check of channels the cached scan had refused) nothing shows as scanning; channels that now play are just added.
     */
    private fun scan(all: List<Channel>, g: Int, quiet: Boolean = false) {
        val first = favs + recentIds(); val langs = Store.langs
        val c = all.sortedBy { if (it.id in first) 0 else if (langs.isEmpty() || it.lang in langs) 1 else 2 }
        val pool = Executors.newFixedThreadPool(8)
        var done = 0
        c.forEach { ch ->
            pool.execute {
                val ok = try { Api.streamUrl(ch, null); true } catch (e: Exception) {
                    // Some channels only answer with a programme id: retry those the old way, unless the plan itself refused it (code 3012).
                    val again = e.message?.contains("3012") != true && try { Api.streamUrl(ch, Api.nowPlaying(ch.id)); true } catch (_: Exception) { false }
                    if (!again && !quiet) android.util.Log.d("Vistaar", "not playable: ${ch.name} (${e.message?.take(60)})")
                    again
                }
                main.post {
                    if (g != gen) return@post
                    if (ok) playable.add(ch.id)
                    if (!quiet) scanned++
                    if (++done == c.size) {
                        scanning = false
                        android.util.Log.d("Vistaar", "scan done: ${playable.size} of ${this.all.size} playable (quiet=$quiet)")
                        Store.prefs.edit().putString("ok", playable.joinToString(",")).apply()
                        if (!quiet) Store.prefs.edit().putLong("okAt", System.currentTimeMillis()).apply()
                    }
                    if (ok || !quiet) changed()
                }
            }
        }
        pool.shutdown()
    }
}
