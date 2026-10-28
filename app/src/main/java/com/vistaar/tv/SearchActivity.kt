package com.vistaar.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Search with the design's on-screen keyboard (works with a plain remote, no system keyboard needed). */
class SearchActivity : Activity() {
    private lateinit var header: Header
    private lateinit var queryText: TextView
    private lateinit var hintText: TextView
    private lateinit var caret: View
    private lateinit var results: RecyclerView
    private lateinit var adapter: GridAdapter
    private lateinit var heading: TextView
    private lateinit var none: TextView
    private lateinit var recentRow: LinearLayout
    private var query = ""

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        if (!Store.loggedIn) { startActivity(Intent(this, LoginActivity::class.java)); finish(); return }
        header = Header(this, SearchActivity::class.java)

        queryText = txt("", 36, N.TEXT, 500).apply { maxLines = 1 }
        hintText = txt("Channels, shows, sports", 30, N.DIM)
        caret = View(this).apply { background = rr(N.ACCENT, px(2)) }
        val box = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(px(24), 0, px(24), 0); background = rr(N.SURFACE, px(20), 0x995AA9FF.toInt(), px(2))
            addView(icon(R.drawable.ic_search, 30, N.MUTED), LinearLayout.LayoutParams(px(30), px(30)).apply { marginEnd = px(16) })
            addView(queryText); addView(caret, LinearLayout.LayoutParams(px(3), px(40)).apply { marginStart = px(4) })
            addView(hintText, LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(12) })
        }
        fun key(label: String?, ico: Int?, click: () -> Unit) = FrameLayout(this).apply {
            focusSkin(px(14), N.SURFACE, 1.06f); setOnClickListener { click() }
            if (ico != null) addView(icon(ico, 30, N.TEXT, "p"), FrameLayout.LayoutParams(px(30), px(30), Gravity.CENTER))
            else addView(txt(label!!, if (label.length > 1) 22 else 28, N.TEXT, 500, tag = "p"), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
        val keys = "abcdefghijklmnopqrstuvwxyz0123456789"
        val keyboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; noClip()
            keys.chunked(6).forEachIndexed { r, row ->
                addView(LinearLayout(context).apply {
                    noClip(); row.forEachIndexed { i, c -> addView(key(c.toString().uppercase(), null) { type(c.toString()) }, LinearLayout.LayoutParams(0, px(68), 1f).apply { if (i > 0) marginStart = px(12) }) }
                }, LinearLayout.LayoutParams(-1, -2).apply { if (r > 0) topMargin = px(12) })
            }
            addView(LinearLayout(context).apply {
                noClip()
                addView(key("Space", null) { type(" ") }, LinearLayout.LayoutParams(0, px(68), 2f))
                addView(key(null, R.drawable.ic_backspace) { delete() }, LinearLayout.LayoutParams(0, px(68), 1f).apply { marginStart = px(12) })
                addView(key("Clear", null) { query = ""; update() }, LinearLayout.LayoutParams(0, px(68), 1f).apply { marginStart = px(12) })
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) })
        }
        recentRow = LinearLayout(this).apply { noClip() }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; noClip()
            addView(box, LinearLayout.LayoutParams(-1, px(88)))
            addView(keyboard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(24) })
            addView(txt("RECENT", 18, N.MUTED, 500).apply { letterSpacing = 0.08f }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(28) })
            addView(recentRow, LinearLayout.LayoutParams(-1, px(60)).apply { topMargin = px(12) })
        }
        heading = txt("", 30, N.TEXT, 600)
        none = txt("", 28, N.MUTED).apply { visibility = View.GONE }
        adapter = GridAdapter(this, 320, 180, { open(it) }, { Catalog.toggleFav(it.id); adapter.notifyDataSetChanged() })
        results = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(context, 3); this.adapter = this@SearchActivity.adapter; itemAnimator = null
            noClip(); setPadding(0, px(8), 0, px(80))
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(N.BG); noClip()
            addView(left, FrameLayout.LayoutParams(px(612), -1).apply { setMargins(px(96), px(160), 0, 0) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; noClip()
                addView(heading); addView(none, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(24) })
                addView(results, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = px(16) })
            }, FrameLayout.LayoutParams(-1, -1).apply { setMargins(px(780), px(160), px(88), px(40)) })
            addView(header.view, header.layoutParams())
        })
        caret.animate().alpha(0.15f).setDuration(520).withEndAction(object : Runnable {
            override fun run() { caret.animate().alpha(if (caret.alpha < 0.5f) 1f else 0.15f).setDuration(520).withEndAction(this).start() }
        }).start()
    }

    override fun onResume() {
        super.onResume()
        if (!Store.loggedIn) return
        Catalog.onChange = { header.update(); update() }
        Catalog.start(false); update()
    }

    override fun onPause() { super.onPause(); Catalog.onChange = null }

    private fun type(s: String) { if (query.length < 40) { query += s; update() } }
    private fun delete() { if (query.isNotEmpty()) { query = query.dropLast(1); update() } }

    private fun recentQueries() = Store.prefs.getString("queries", "")!!.split("|").filter { it.isNotBlank() }

    private fun open(ch: Channel) {
        if (query.isNotBlank()) Store.prefs.edit().putString("queries", (listOf(query.trim()) + recentQueries().filter { it != query.trim() }).take(5).joinToString("|")).apply()
        startActivity(Intent(this, PlayerActivity::class.java).putExtra("id", ch.id))
    }

    private fun update() {
        queryText.text = query; hintText.visibility = if (query.isEmpty()) View.VISIBLE else View.GONE
        val playable = Catalog.playableChannels()
        val hits = if (query.isBlank()) Catalog.favourites().ifEmpty { Catalog.recents() }.ifEmpty { playable.take(9) }
                   else playable.filter { it.name.contains(query.trim(), ignoreCase = true) }
        heading.text = if (query.isBlank()) "Suggested for tonight" else "Channels  ·  ${hits.size}"
        none.visibility = if (query.isNotBlank() && hits.isEmpty()) View.VISIBLE else View.GONE
        none.text = if (Catalog.scanning) "No match yet. Still checking which channels your plan includes." else "No matches for “${query.trim()}”. Try a channel name."
        adapter.submit(hits)
        recentRow.removeAllViews()
        recentQueries().forEachIndexed { i, q ->
            recentRow.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; focusSkin(px(30), N.SURFACE, 1.05f); setPadding(px(20), 0, px(20), 0)
                addView(icon(R.drawable.ic_history, 18, N.DIM, "m"), LinearLayout.LayoutParams(px(18), px(18)).apply { marginEnd = px(10) })
                addView(txt(q, 20, N.TEXT2, 500, tag = "p")); setOnClickListener { query = q; update() }
            }, LinearLayout.LayoutParams(-2, px(52)).apply { if (i > 0) marginStart = px(12) })
        }
    }
}
