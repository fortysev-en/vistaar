package com.vistaar.tv

import android.os.Handler
import android.os.Looper
import java.io.File

/**
 * Free channels from the community-maintained iptv-org India playlist (https://github.com/iptv-org/iptv).
 * Ids are negative so they never clash with Jio channel ids; they are hashed from the playlist's channel id and name,
 * so favourites survive playlist refreshes. Main-thread API, like [Catalog].
 */
object Free {
    const val DEFAULT = "https://iptv-org.github.io/iptv/countries/in.m3u"
    private const val CACHE_MS = 6 * 3600_000L
    private val main = Handler(Looper.getMainLooper())
    var dir: File? = null

    var channels: List<Channel> = emptyList(); private set
    var cats: List<String> = emptyList(); private set
    var loading = false; private set
    var error: String? = null; private set

    /** Category label for a free channel's [Channel.lang] (-1 for the first category, -2 for the next...). */
    fun catName(lang: Int) = cats.getOrNull(-lang - 1) ?: "Free"

    // iptv-org publishes a playlist per language (ISO 639-3); the country playlist itself says nothing about language.
    private val LANG_FILES = mapOf(1 to "hin", 2 to "mar", 3 to "pan", 4 to "urd", 5 to "ben", 6 to "eng", 7 to "mal", 8 to "tam", 9 to "guj",
        10 to "ori", 11 to "tel", 12 to "bho", 13 to "kan", 14 to "asm", 15 to "nep", 16 to "fra")
    private var langIds: Set<Long>? = null
    private var langKey = ""

    /** Free channels limited to the languages chosen on first run. Falls back to everything if the language lists are missing or match nothing (custom playlists). */
    val shown: List<Channel> get() { val ids = langIds; return if (ids == null || channels.none { it.id in ids }) channels else channels.filter { it.id in ids } }

    /** Fetches the language lists for the chosen languages (cached 6 h). Cheap to call repeatedly: it only works when the choice changed. */
    fun loadLangs(done: () -> Unit) {
        val codes = Store.langs.mapNotNull { LANG_FILES[it] }.sorted()
        val key = codes.joinToString(",")
        if (key == langKey) return
        langKey = key
        if (codes.isEmpty()) { langIds = null; done(); return }
        Thread {
            val ids = HashSet<Long>(); var ok = false
            for (c in codes) try {
                parse(cachedText("lang-$c.m3u", "https://iptv-org.github.io/iptv/languages/$c.m3u", false)).first?.first?.forEach { ids.add(it.id) }; ok = true
            } catch (_: Exception) { }
            main.post { if (langKey == key) { langIds = if (ok) ids else null; if (!ok) langKey = ""; done() } }
        }.start()
    }

    /** Switches to another playlist: fetches [url] first and only keeps it if it parses, so a bad link never wipes the list. */
    fun switchTo(url: String, done: (String?) -> Unit) {
        Thread {
            var text = ""; var parsed: Pair<List<Channel>, List<String>>? = null; var err: String? = null
            try {
                text = Api.text(url)
                if (!text.trimStart().startsWith("#EXTM3U")) throw ApiException("That link is not an M3U playlist")
                val r = parse(text); parsed = r.first; err = r.second
                if (parsed != null) File(dir, cacheName(url)).writeText(text)
            } catch (e: Exception) { err = e.message ?: e.toString() }
            main.post {
                val p = parsed
                if (p == null) { done(err ?: "Could not load that playlist"); return@post }
                Store.playlistUrl = url; channels = p.first; cats = p.second; error = null; done(null)
            }
        }.start()
    }

    private fun cacheName(url: String = Store.playlistUrl) = "free-" + Integer.toHexString(url.hashCode()) + ".m3u"

    fun load(force: Boolean, done: () -> Unit) {
        if (loading || (!force && channels.isNotEmpty())) return
        loading = true; error = null; done()
        Thread {
            val r = try { parse(fetch(force)) } catch (e: Exception) { null to (e.message ?: e.toString()) }
            main.post {
                loading = false
                if (r.first != null) { channels = r.first!!.first; cats = r.first!!.second } else error = r.second
                done()
            }
        }.start()
    }

    private fun fetch(force: Boolean) = cachedText(cacheName(), Store.playlistUrl, force)

    private fun cachedText(name: String, url: String, force: Boolean): String {
        val f = File(dir, name)
        if (!force && f.exists() && System.currentTimeMillis() - f.lastModified() < CACHE_MS) return f.readText()
        return try {
            Api.text(url).also { if (!it.trimStart().startsWith("#EXTM3U")) throw ApiException("Not a playlist"); f.writeText(it) }
        } catch (e: Exception) { if (f.exists()) f.readText() else throw e } // stale list beats no list
    }

    private fun fnv(s: String): Long { var h = -3750763034362895579L; for (c in s) { h = h xor c.code.toLong(); h *= 1099511628211L }; return h }
    private fun attr(line: String, k: String) = Regex("""$k="([^"]*)"""").find(line)?.groupValues?.get(1)?.trim().orEmpty()
    private val quality = Regex("""\s*\((\d{3,4})p\)\s*$""")
    private val tags = Regex("""\s*\[[^\]]*]""")

    /** Returns (channels, categories) or null with an error message. */
    internal fun parse(text: String): Pair<Pair<List<Channel>, List<String>>?, String?> {
        class Raw(val id: Long, val name: String, val cat: String, val hd: Boolean, val logo: String, val url: String, val ua: String, val ref: String)
        val raw = ArrayList<Raw>(); val seen = HashSet<Long>()
        var info: String? = null; var ua = ""; var ref = ""
        for (l in text.lineSequence()) {
            val line = l.trim()
            when {
                line.startsWith("#EXTINF") -> { info = line; ua = ""; ref = "" }
                line.startsWith("#EXTVLCOPT:") -> {
                    val kv = line.replace("#EXTVLCOPT:", ""); val k = kv.substringBefore('=').trim(); val v = kv.substringAfterLast("$k=").trim()
                    if (k == "http-user-agent") ua = v else if (k == "http-referrer") ref = v
                }
                line.isEmpty() || line.startsWith("#") -> {}
                info != null -> {
                    val i = info; info = null
                    if (!line.startsWith("http") || "youtube.com" in line || "youtu.be" in line) continue // not playable by the player
                    var name = i.substring(i.indexOf(',', i.lastIndexOf('"') + 1) + 1).replace(tags, "").trim()
                    val q = quality.find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    name = name.replace(quality, "").trim()
                    if (name.isEmpty()) continue
                    val id = -(fnv(attr(i, "tvg-id") + "|" + name) ushr 1) - 1
                    if (!seen.add(id)) continue
                    val cat = attr(i, "group-title").substringBefore(';').trim().ifEmpty { "General" }
                    raw.add(Raw(id, name, cat, q >= 720, attr(i, "tvg-logo"), line, ua, ref))
                }
            }
        }
        if (raw.isEmpty()) return null to "The playlist had no playable channels"
        val cats = raw.groupingBy { it.cat }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        val list = raw.mapIndexed { n, r -> Channel(r.id, r.name, n, -(cats.indexOf(r.cat) + 1), r.hd, r.logo, r.url, r.ua, r.ref) }
        return (list to cats) to null
    }
}
