package com.vistaar.tv

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Favourites: a grid of large logo cards. OK watches; holding OK offers Move, Rename and Remove. */
class FavoritesActivity : Activity() {
    private companion object { const val COLUMNS = 6 }

    private lateinit var header: Header
    private lateinit var grid: RecyclerView
    private lateinit var count: TextView
    private lateinit var empty: TextView
    private var items: List<Channel> = emptyList()

    private val mover by lazy {
        Mover(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, 0, { items.size }, { from, to -> move(from, to) },
            { p -> if (p >= 0) grid.adapter?.notifyItemChanged(p) }, px(24), { grid.findViewHolderForAdapterPosition(it)?.itemView }, COLUMNS)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        if (!Store.hasAccess) { startActivity(Intent(this, SplashActivity::class.java)); finish(); return }
        header = Header(this, FavoritesActivity::class.java)
        count = txt("", 22, N.MUTED)
        empty = txt("No favourites yet. Hold OK on any channel to save it here.", 24, N.MUTED).apply { visibility = View.GONE }
        grid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(context, COLUMNS); adapter = CardAdapter(); itemAnimator = null
            noClip(); setPadding(px(12), px(8), px(12), px(60)) // side room inside the grid, so a zoomed edge card isn't cut
        }
        val title = LinearLayout(this).apply {
            gravity = Gravity.BOTTOM
            addView(txt("Favourites", 40, N.TEXT, 600))
            addView(count, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = px(20); bottomMargin = px(4) })
            addView(txt("OK to watch  ·  Hold OK for options", 18, N.DIM), LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = px(6) })
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(N.BG); noClip()
            addView(title, FrameLayout.LayoutParams(-1, -2).apply { setMargins(px(96), px(156), px(96), 0) })
            // The wrapper clips the grid to its own bounds, so scrolled rows never run into the title.
            addView(FrameLayout(context).apply { addView(grid, FrameLayout.LayoutParams(-1, -1)) },
                FrameLayout.LayoutParams(-1, -1).apply { setMargins(px(72), px(226), px(72), 0) })
            addView(empty, FrameLayout.LayoutParams(-2, -2).apply { setMargins(px(96), px(260), 0, 0) })
            addView(header.view, header.layoutParams())
        })
    }

    override fun onResume() {
        super.onResume()
        if (!Store.hasAccess) return
        Catalog.onChange = { refresh() }
        Catalog.start(false); refresh(rebind = true) // a name may have changed on the rename screen
    }

    override fun onPause() { super.onPause(); Catalog.onChange = null }

    /** Rebinds only when the list changed (or [rebind] asks), and puts focus back on the same channel. */
    private fun refresh(rebind: Boolean = false, focusAt: Int = -1) {
        header.update()
        if (mover.on) return // the cards are being arranged by hand
        val next = Catalog.favourites()
        val changed = next.map { it.id } != items.map { it.id }
        val focused = grid.focusedChild?.let { grid.getChildAdapterPosition(it) } ?: -1
        val focusedId = items.getOrNull(focused)?.id
        val firstFill = items.isEmpty() && next.isNotEmpty()
        items = next
        count.text = if (items.isEmpty()) "" else "${items.size} channel${if (items.size == 1) "" else "s"}"
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        if (!changed && !rebind) return
        grid.adapter?.notifyDataSetChanged()
        val target = when {
            focusAt >= 0 -> focusAt.coerceAtMost(items.size - 1)
            focusedId != null -> items.indexOfFirst { it.id == focusedId }
            firstFill -> 0
            else -> -1
        }
        if (target >= 0) focusCard(target)
        else if (items.isEmpty()) header.firstFocus.requestFocus()
    }

    private fun focusCard(pos: Int) { grid.scrollToPosition(pos); grid.post { grid.findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus() } }

    private fun move(from: Int, to: Int) {
        items = Order.moved(items, from, to)
        grid.adapter?.notifyItemMoved(from, to)
        Catalog.saveFavOrder(items.map { it.id })
        grid.scrollToPosition(to)
        grid.post { grid.findViewHolderForAdapterPosition(to)?.itemView?.requestFocus(); mover.lift() }
    }

    private fun play(ch: Channel) { startActivity(Intent(this, PlayerActivity::class.java).putExtra("id", ch.id).putExtra("ids", items.map { it.id }.toLongArray())) }

    /** The hold-OK menu for one card. */
    private fun options(pos: Int) {
        val ch = items.getOrNull(pos) ?: return
        val d = Dialog(this)
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); d.window?.setDimAmount(0.7f)
        fun option(label: String, color: Int? = null, act: () -> Unit) = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; focusSkin(px(14), N.FILL, 1.0f); setPadding(px(28), 0, px(28), 0)
            addView(txt(label, 24, color ?: N.TEXT, 600, tag = if (color == null) "p" else null))
            setOnClickListener { d.dismiss(); act() }
        }
        val rows = listOf(
            option("Move") { mover.begin(this, pos, held = false) },
            option("Rename") { startActivity(Intent(this, RenameActivity::class.java).putExtra("id", ch.id)) },
            option("Remove from favourites", 0xFFFF6B6B.toInt()) { Catalog.toggleFav(ch.id); refresh(focusAt = pos) },
        )
        d.setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = rr(N.SURFACE, px(24), 0x1AFFFFFF, px(1)); noClip()
            setPadding(px(40), px(36), px(40), px(36))
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(LogoBox(context, 72, 14).apply { bind(ch) }, LinearLayout.LayoutParams(px(72), px(72)))
                addView(txt(Catalog.label(ch), 30, N.TEXT, 600).apply { maxLines = 2 }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = px(20) })
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(28) })
            rows.forEachIndexed { i, r -> addView(r, LinearLayout.LayoutParams(-1, px(72)).apply { if (i > 0) topMargin = px(12) }) }
        })
        // The OK that opened the menu is still held down: swallow it until it is released, or it would press the first option.
        var armed = true
        d.setOnKeyListener { _, code, e ->
            val ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
            if (ok && armed) { if (e.action == KeyEvent.ACTION_UP || e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) armed = false; e.action == KeyEvent.ACTION_UP || e.repeatCount > 0 } else false
        }
        d.window?.setLayout(px(640), ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show(); rows.first().requestFocus()
    }

    private class CardHolder(view: View, val logo: LogoBox, val name: TextView) : RecyclerView.ViewHolder(view)

    private inner class CardAdapter : RecyclerView.Adapter<CardHolder>() {
        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, t: Int): CardHolder {
            val c = this@FavoritesActivity
            val logo = LogoBox(c, 176, 0, Color.TRANSPARENT)
            val name = c.txt("", 22, N.TEXT, 600).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER_HORIZONTAL }
            val card = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL; background = rr(0xFF232935.toInt(), px(18), N.HAIR, px(1))
                addView(logo, LinearLayout.LayoutParams(-1, px(176)))
                addView(name, LinearLayout.LayoutParams(-1, -2).apply { setMargins(px(14), 0, px(14), px(18)) })
            }
            val view = FrameLayout(c).apply {
                val pad = px(8); setPadding(pad, pad, pad, pad); noClip(); ringOnFocus(c, px(18), 1.06f)
                addView(card, FrameLayout.LayoutParams(-1, -2))
                layoutParams = RecyclerView.LayoutParams(-1, -2).apply { setMargins(px(4), px(4), px(4), px(8)) }
            }
            return CardHolder(view, logo, name)
        }

        override fun onBindViewHolder(h: CardHolder, p: Int) {
            val ch = items[p]
            h.logo.bind(ch); h.name.text = Catalog.label(ch)
            h.itemView.contentDescription = h.name.text
            if (mover.on && p == mover.cur) h.itemView.post { mover.lift() }
            h.itemView.setOnClickListener { if (!mover.on) play(ch) }
            h.itemView.setOnLongClickListener { if (!mover.on) options(h.bindingAdapterPosition); true }
            h.itemView.setOnKeyListener { _, code, e -> mover.key(h.bindingAdapterPosition, code, e) }
        }
    }
}
