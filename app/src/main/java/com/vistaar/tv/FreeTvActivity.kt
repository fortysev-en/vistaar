package com.vistaar.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Free TV: the iptv-org India playlist. Same layout as Live TV (categories | channels | preview), without a programme guide. */
open class FreeTvActivity : Activity() {
    /** The HD tab reuses this screen with a different channel pool. */
    protected open val hdOnly = false
    protected open fun pool(): List<Channel> = Catalog.freeChannels()
    protected open fun catOf(ch: Channel): String = Free.catName(ch.lang)
    protected open fun catOrder(counts: Map<String, Int>): List<String> = Free.cats.filter { it in counts }

    private data class Cat(val name: String?, val count: Int) // name null = all

    private lateinit var header: Header
    private lateinit var catList: RecyclerView
    private lateinit var chList: RecyclerView
    private lateinit var empty: TextView
    private lateinit var backdrop: ArtView
    private lateinit var pArt: ArtView
    private lateinit var pLogo: LogoBox
    private lateinit var pCat: TextView
    private lateinit var pMeta: TextView
    private lateinit var pTitle: TextView
    private lateinit var pSub: TextView
    private lateinit var pNote: TextView

    private var cats: List<Cat> = emptyList()
    private var cat: String? = null
    private var channels: List<Channel> = emptyList()
    private var previewCh: Channel? = null
    private val pp by lazy { PreviewPlayer(this) }
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        if (!Store.hasAccess) { startActivity(Intent(this, LoginActivity::class.java)); finish(); return }
        header = Header(this, this::class.java)
        backdrop = ArtView(this).apply { alpha = 0.28f }

        catList = RecyclerView(this).apply { layoutManager = LinearLayoutManager(context); adapter = CatAdapter(); itemAnimator = null; noClip(); setPadding(px(8), px(4), px(8), px(4)) }
        chList = RecyclerView(this).apply { layoutManager = LinearLayoutManager(context); adapter = ChAdapter(); itemAnimator = null; noClip(); setPadding(px(8), px(8), px(8), px(40)) }
        empty = txt("", 24, N.MUTED).apply { visibility = View.GONE; maxWidth = px(640); setLineSpacing(0f, 1.3f) }

        pArt = ArtView(this); pLogo = LogoBox(this, 64, 14)
        pCat = txt("", 18, N.TEXT2); pMeta = txt("", 20, N.MUTED); pTitle = txt("", 40, N.TEXT, 600).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        pSub = txt("", 20, N.MUTED); pNote = txt("", 22, N.TEXT2).apply { maxLines = 3; setLineSpacing(0f, 1.3f) }
        val watchBtn = LinearLayout(this).apply {
            gravity = Gravity.CENTER; focusSkin(px(14), N.FILL, 1.04f); setPadding(px(28), 0, px(28), 0)
            addView(icon(R.drawable.ic_play, 22, N.TEXT, "p"), LinearLayout.LayoutParams(px(22), px(22)))
            addView(txt("Watch", 22, N.TEXT, 500, tag = "p"), LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(12) })
            setOnClickListener { previewCh?.let { play(it) } }
        }
        val artBox = FrameLayout(this).apply {
            clipRound(px(24))
            addView(pArt, FrameLayout.LayoutParams(-1, -1))
            addView(pp.view, FrameLayout.LayoutParams(-1, -1))
            addView(View(context).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x0007080A, 0xBF07080A.toInt())) }, FrameLayout.LayoutParams(-1, -1))
            addView(liveBadge(), FrameLayout.LayoutParams(-2, -2).apply { setMargins(px(20), px(20), 0, 0) })
            addView(pLogo, FrameLayout.LayoutParams(px(64), px(64), Gravity.BOTTOM).apply { setMargins(px(20), 0, 0, px(20)) })
            addView(pCat, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, px(20), px(24)) })
        }
        val aside = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(artBox, LinearLayout.LayoutParams(-1, px(356)))
            addView(pSub, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(24) })
            addView(pTitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(8) })
            addView(pMeta, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(8) })
            addView(pNote, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(20) })
            addView(LinearLayout(context).apply { noClip(); addView(watchBtn, LinearLayout.LayoutParams(-2, px(60))) }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(24) })
            addView(txt("OK to watch  ·  Hold OK to favourite  ·  ◀ categories", 18, N.DIM), LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(20) })
        }
        val body = LinearLayout(this).apply {
            noClip()
            addView(catList, LinearLayout.LayoutParams(px(316), -1))
            addView(FrameLayout(context).apply { noClip(); addView(chList, FrameLayout.LayoutParams(-1, -1)); addView(empty, FrameLayout.LayoutParams(-2, -2).apply { topMargin = px(40); marginStart = px(24) }) },
                LinearLayout.LayoutParams(px(716), -1).apply { marginStart = px(32) })
            addView(aside, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = px(48) })
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(N.BG); noClip()
            addView(backdrop, FrameLayout.LayoutParams(px(1100), -1, Gravity.END))
            addView(View(context).apply { background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(N.BG, N.BG, 0x9907080A.toInt())) }, FrameLayout.LayoutParams(-1, -1))
            addView(body, FrameLayout.LayoutParams(-1, -1).apply { setMargins(px(88), px(160), px(96), px(54)) })
            addView(header.view, header.layoutParams())
        })
    }

    override fun onResume() {
        super.onResume()
        if (!Store.hasAccess) return
        Catalog.onChange = { refresh() }
        previewCh?.let { pp.show(it) }
        Catalog.start(false); refresh()
    }

    override fun onPause() { super.onPause(); Catalog.onChange = null; pp.release() }

    private fun play(ch: Channel) { startActivity(Intent(this, PlayerActivity::class.java).putExtra("id", ch.id).putExtra("hd", hdOnly)) }

    private fun refresh() {
        header.update()
        val all = pool()
        val counts = all.groupingBy { catOf(it) }.eachCount()
        val newCats = listOf(Cat(null, all.size)) + catOrder(counts).map { Cat(it, counts.getValue(it)) }
        if (newCats != cats) { cats = newCats; catList.adapter?.notifyDataSetChanged() }
        if (cats.none { it.name == cat }) cat = null
        val newList = if (cat == null) all else all.filter { catOf(it) == cat }
        val firstFill = channels.isEmpty() && newList.isNotEmpty()
        if (newList != channels) { channels = newList; chList.adapter?.notifyDataSetChanged() }
        empty.text = when {
            Free.loading || Catalog.scanning && hdOnly -> "Loading channels…"
            Free.error != null -> "Couldn't load the free channels (${Free.error}). Check your connection, then press the refresh button."
            else -> "No channels found."
        }
        empty.visibility = if (channels.isEmpty()) View.VISIBLE else View.GONE
        if (previewCh == null || channels.none { it.id == previewCh?.id }) channels.firstOrNull()?.let { preview(it) }
        if (firstFill) chList.post { chList.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
    }

    private fun preview(ch: Channel) {
        if (previewCh?.id == ch.id) return
        previewCh = ch
        pp.show(ch)
        backdrop.setStyle(ch.id.toInt()); pArt.setStyle(ch.id.toInt()); pLogo.bind(ch)
        val free = ch.url.isNotEmpty()
        pCat.text = langName(ch.lang) + if (ch.hd) " · HD" else ""
        pSub.text = "${ch.name} · ${Catalog.numberOf(ch.id)}"; pTitle.text = ch.name; pMeta.text = (if (free) "Free TV" else "Jio TV") + if (ch.hd) " · HD" else ""
        pNote.text = if (free) "A free stream from the community iptv-org playlist. Availability varies, and some channels may be offline." else "On your Jio plan."
    }

    /** Down on the last channel stays put; Left goes back to the open category, not whichever row is nearest. */
    private fun chKey(code: Int, e: KeyEvent, pos: Int): Boolean {
        if (code == KeyEvent.KEYCODE_DPAD_LEFT && e.action == KeyEvent.ACTION_DOWN) {
            val pos = cats.indexOfFirst { it.name == cat }.coerceAtLeast(0)
            (catList.layoutManager as LinearLayoutManager).scrollToPosition(pos)
            catList.post { catList.findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus() }
            return true
        }
        return chList.stepFocus(pos, code, e, channels.size)
    }

    private fun select(c: String?) {
        if (cat == c) return
        cat = c; refresh()
        chList.scrollToPosition(0)
        for (i in 0 until catList.childCount) { // repaint in place: rebinding would drop focus
            val v = catList.getChildAt(i) as LinearLayout
            (v.tag as? Cat)?.let { v.paintCat(px(14), it.name == cat, v.isFocused) }
        }
    }

    private inner class CatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = cats.size
        override fun onCreateViewHolder(parent: ViewGroup, t: Int): RecyclerView.ViewHolder {
            val c = this@FreeTvActivity
            val row = LinearLayout(c).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(px(20), 0, px(20), 0)
                layoutParams = RecyclerView.LayoutParams(-1, px(64)).apply { bottomMargin = px(6) }
                addView(txt("", 22, N.TEXT, 500).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(txt("", 18, N.DIM, mono = true))
                isFocusable = true; isClickable = true
                setOnFocusChangeListener { v, f ->
                    val c = v.tag as? Cat ?: return@setOnFocusChangeListener
                    (v as LinearLayout).paintCat(px(14), c.name == cat, f)
                    if (f) ui.postDelayed({ if (v.hasFocus()) select(c.name) }, 220) // browsing categories switches the list
                }
            }
            return object : RecyclerView.ViewHolder(row) {}
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, p: Int) {
            val c = cats[p]; val row = h.itemView as LinearLayout
            row.tag = c
            (row.getChildAt(0) as TextView).text = c.name ?: "All channels"
            (row.getChildAt(1) as TextView).text = c.count.toString()
            row.paintCat(px(14), c.name == cat, row.isFocused)
            row.setOnClickListener { select(c.name) }
            row.setOnKeyListener { _, code, e -> catList.stepFocus(h.bindingAdapterPosition, code, e, cats.size) }
        }
    }

    private inner class ChAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = channels.size
        override fun onCreateViewHolder(parent: ViewGroup, t: Int): RecyclerView.ViewHolder {
            val c = this@FreeTvActivity
            val num = c.txt("", 22, N.MUTED, mono = true, tag = "s")
            val logo = LogoBox(c, 56, 12)
            val name = c.txt("", 22, N.TEXT, 600, tag = "p").apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
            val sub = c.txt("", 20, N.MUTED, tag = "s").apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
            val star = c.icon(R.drawable.ic_star, 22, N.ACCENT, "a")
            val row = LinearLayout(c).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(px(24), 0, px(24), 0); focusSkin(px(16), Color.TRANSPARENT, 1.03f)
                layoutParams = RecyclerView.LayoutParams(-1, px(96)).apply { bottomMargin = px(8) }
                addView(num, LinearLayout.LayoutParams(px(56), -2))
                addView(logo, LinearLayout.LayoutParams(px(56), px(56)).apply { marginEnd = px(20) })
                addView(LinearLayout(c).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL; addView(name, LinearLayout.LayoutParams(0, -2, 1f)); addView(star, LinearLayout.LayoutParams(px(22), px(22)).apply { marginStart = px(10) }) })
                    addView(sub, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(4) })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(liveBadge())
            }
            val refs = arrayOf<Any?>(num, logo, name, sub, star, null) // [5] = channel currently bound
            row.tag = refs
            val skin = row.onFocusChangeListener
            row.setOnFocusChangeListener { x, f -> skin.onFocusChange(x, f); if (f) (refs[5] as? Channel)?.let { preview(it) } }
            return object : RecyclerView.ViewHolder(row) {}
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, p: Int) {
            val ch = channels[p]; val row = h.itemView
            @Suppress("UNCHECKED_CAST") val v = row.tag as Array<Any?>
            v[5] = ch
            (v[0] as TextView).text = Catalog.numberOf(ch.id).toString(); (v[1] as LogoBox).bind(ch)
            (v[2] as TextView).text = ch.name; (v[3] as TextView).text = langName(ch.lang) + if (ch.hd) " · HD" else ""
            (v[4] as View).visibility = if (Catalog.isFav(ch.id)) View.VISIBLE else View.GONE
            row.setOnClickListener { play(ch) }
            row.setOnLongClickListener { Catalog.toggleFav(ch.id); notifyItemChanged(p); true }
            row.setOnKeyListener { _, code, e -> chKey(code, e, h.bindingAdapterPosition) }
        }
    }
}
