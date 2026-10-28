package com.vistaar.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Favourites: a grid of channel cards, with sort and an edit mode that removes channels. */
class FavoritesActivity : Activity() {
    private lateinit var header: Header
    private lateinit var grid: RecyclerView
    private lateinit var adapter: GridAdapter
    private lateinit var count: TextView
    private lateinit var content: View
    private lateinit var empty: View
    private lateinit var sortLabel: TextView
    private lateinit var editLabel: TextView
    private lateinit var editBtn: View
    private lateinit var hint: TextView
    private var sortAlpha = false
    private var editing = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        if (!Store.loggedIn) { startActivity(Intent(this, LoginActivity::class.java)); finish(); return }
        header = Header(this, FavoritesActivity::class.java)
        adapter = GridAdapter(this, 400, 225, { ch -> if (editing) remove(ch) else play(ch) }, { remove(it) })
        grid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(context, 4); this.adapter = this@FavoritesActivity.adapter; itemAnimator = null
            noClip(); setPadding(px(88), px(8), px(88), px(120))
        }
        count = txt("", 22, N.MUTED)
        sortLabel = txt("Channel order", 20, N.TEXT, 500, tag = "p"); editLabel = txt("Edit", 20, N.TEXT, 500, tag = "p")
        fun pill(label: TextView, ico: Int?, click: () -> Unit) = LinearLayout(this).apply {
            gravity = Gravity.CENTER; focusSkin(px(14), N.SURFACE, 1.04f); setPadding(px(22), 0, px(22), 0)
            if (ico != null) addView(icon(ico, 20, N.TEXT, "p"), LinearLayout.LayoutParams(px(20), px(20)).apply { marginEnd = px(10) })
            addView(label); setOnClickListener { click() }
        }
        editBtn = pill(editLabel, null) { setEditing(!editing) }
        hint = txt("OK to watch  ·  Hold OK to remove", 18, N.DIM)
        content = FrameLayout(this).apply {
            noClip()
            addView(LinearLayout(context).apply {
                gravity = Gravity.BOTTOM; setPadding(px(96), 0, px(96), 0)
                addView(LinearLayout(context).apply {
                    gravity = Gravity.BOTTOM
                    addView(txt("Favourites", 48, N.TEXT, 600).apply { letterSpacing = -0.01f })
                    addView(count, LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(20); bottomMargin = px(8) })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(pill(sortLabel, R.drawable.ic_channels) { sortAlpha = !sortAlpha; refresh() }, LinearLayout.LayoutParams(-2, px(56)))
                addView(editBtn, LinearLayout.LayoutParams(-2, px(56)).apply { marginStart = px(12) })
            }, FrameLayout.LayoutParams(-1, px(60)).apply { topMargin = px(160) })
            addView(grid, FrameLayout.LayoutParams(-1, -1).apply { topMargin = px(250) })
            addView(hint, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM).apply { setMargins(px(96), 0, 0, px(40)) })
        }
        empty = FrameLayout(this).apply {
            visibility = View.GONE
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                addView(FrameLayout(context).apply {
                    background = rr(N.SURFACE, px(70)); addView(icon(R.drawable.ic_star_outline, 64, N.MUTED), FrameLayout.LayoutParams(px(64), px(64), Gravity.CENTER))
                }, LinearLayout.LayoutParams(px(140), px(140)))
                addView(txt("No favourites yet", 40, N.TEXT, 600), LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(32) })
                addView(txt("Hold OK on any channel to keep it here for quick access.", 24, N.MUTED), LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(12) })
                addView(LinearLayout(context).apply {
                    gravity = Gravity.CENTER; focusSkin(px(14), N.TEXT, 1.04f); setPadding(px(36), 0, px(36), 0)
                    addView(txt("Browse Live TV", 24, N.INK, 600)); setOnClickListener { goTo(LiveTvActivity::class.java) }
                }, LinearLayout.LayoutParams(-2, px(68)).apply { topMargin = px(40) })
            }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER).apply { topMargin = px(60) })
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(N.BG); noClip(); addView(content); addView(empty); addView(header.view, header.layoutParams())
        })
    }

    override fun onResume() {
        super.onResume()
        if (!Store.loggedIn) return
        Catalog.onChange = { header.update(); refresh() }
        Catalog.start(false); refresh()
    }

    override fun onPause() { super.onPause(); Catalog.onChange = null }

    private fun play(ch: Channel) { startActivity(Intent(this, PlayerActivity::class.java).putExtra("id", ch.id)) }

    private fun remove(ch: Channel) {
        Catalog.toggleFav(ch.id); refresh()
        if (adapter.items.isEmpty()) setEditing(false)
    }

    private fun setEditing(on: Boolean) {
        editing = on; adapter.editing = on; editLabel.text = if (on) "Done" else "Edit"
        hint.text = if (on) "OK removes the channel from Favourites  ·  Done to finish" else "OK to watch  ·  Hold OK to remove"
        adapter.notifyDataSetChanged()
    }

    private fun refresh() {
        var list = Catalog.favourites()
        if (sortAlpha) list = list.sortedBy { it.name.lowercase() }
        sortLabel.text = if (sortAlpha) "A to Z" else "Channel order"
        count.text = "${list.size} channel${if (list.size == 1) "" else "s"}"
        adapter.submit(list)
        content.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }
}
