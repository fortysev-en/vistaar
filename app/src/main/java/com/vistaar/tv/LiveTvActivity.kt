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
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Live TV: categories (languages) | channel list | preview of the focused channel. */
class LiveTvActivity : Activity() {
    private data class Cat(val lang: Int, val label: String, val count: Int) // lang -1 = all

    private lateinit var header: Header
    private lateinit var catList: RecyclerView
    private lateinit var chList: RecyclerView
    private lateinit var empty: TextView
    private lateinit var backdrop: ArtView
    private lateinit var pArt: ArtView
    private lateinit var pLogo: LogoBox
    private lateinit var pLang: TextView
    private lateinit var pMeta: TextView
    private lateinit var pTitle: TextView
    private lateinit var pSub: TextView
    private lateinit var pStart: TextView
    private lateinit var pEnd: TextView
    private lateinit var pBar: Bar
    private lateinit var pDesc: TextView
    private lateinit var watchBtn: View

    private var cats: List<Cat> = emptyList()
    private var cat = -1
    private var channels: List<Channel> = emptyList()
    private var previewCh: Channel? = null
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        if (!Store.loggedIn) { startActivity(Intent(this, LoginActivity::class.java)); finish(); return }
        header = Header(this, LiveTvActivity::class.java)
        backdrop = ArtView(this).apply { alpha = 0.28f }

        catList = RecyclerView(this).apply { layoutManager = LinearLayoutManager(context); adapter = CatAdapter(); itemAnimator = null; noClip(); setPadding(px(8), px(4), px(8), px(4)) }
        chList = RecyclerView(this).apply { layoutManager = LinearLayoutManager(context); adapter = ChAdapter(); itemAnimator = null; noClip(); setPadding(px(8), px(8), px(8), px(40)) }
        empty = txt("Checking which channels your plan includes…", 24, N.MUTED).apply { visibility = View.GONE }

        pArt = ArtView(this); pLogo = LogoBox(this, 64, 14)
        pLang = txt("", 18, N.TEXT2); pMeta = txt("", 20, N.MUTED); pTitle = txt("", 40, N.TEXT, 600).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        pSub = txt("", 20, N.MUTED); pStart = txt("", 18, N.MUTED, mono = true); pEnd = txt("", 18, N.MUTED, mono = true)
        pBar = Bar(this, px(3)); pDesc = txt("", 22, N.TEXT2).apply { maxLines = 4; ellipsize = TextUtils.TruncateAt.END; setLineSpacing(0f, 1.3f) }
        watchBtn = LinearLayout(this).apply {
            gravity = Gravity.CENTER; focusSkin(px(14), N.FILL, 1.04f); setPadding(px(28), 0, px(28), 0)
            addView(icon(R.drawable.ic_play, 22, N.TEXT, "p"), LinearLayout.LayoutParams(px(22), px(22)))
            addView(txt("Watch", 22, N.TEXT, 500, tag = "p"), LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(12) })
            setOnClickListener { previewCh?.let { play(it) } }
        }
        val scheduleBtn = LinearLayout(this).apply {
            gravity = Gravity.CENTER; focusSkin(px(14), N.HAIR, 1.04f); setPadding(px(28), 0, px(28), 0)
            addView(txt("Schedule", 22, N.TEXT2, 500, tag = "p")); setOnClickListener { goTo(GuideActivity::class.java) }
        }
        val artBox = FrameLayout(this).apply {
            clipRound(px(24))
            addView(pArt, FrameLayout.LayoutParams(-1, -1))
            addView(View(context).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x0007080A, 0xBF07080A.toInt())) }, FrameLayout.LayoutParams(-1, -1))
            addView(liveBadge(), FrameLayout.LayoutParams(-2, -2).apply { setMargins(px(20), px(20), 0, 0) })
            addView(pLogo, FrameLayout.LayoutParams(px(64), px(64), Gravity.BOTTOM).apply { setMargins(px(20), 0, 0, px(20)) })
            addView(pLang, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, px(20), px(24)) })
        }
        val aside = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(artBox, LinearLayout.LayoutParams(-1, px(356)))
            addView(pSub, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(24) })
            addView(pTitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(8) })
            addView(pMeta, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(8) })
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(pStart); addView(pBar, LinearLayout.LayoutParams(0, px(6), 1f).apply { setMargins(px(16), 0, px(16), 0) }); addView(pEnd)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(20) })
            addView(pDesc, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(20) })
            addView(LinearLayout(context).apply {
                noClip(); addView(watchBtn, LinearLayout.LayoutParams(-2, px(60)))
                addView(scheduleBtn, LinearLayout.LayoutParams(-2, px(60)).apply { marginStart = px(16) })
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(24) })
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
        if (!Store.loggedIn) return
        Catalog.onChange = { refresh() }
        Catalog.start(false); refresh()
    }

    override fun onPause() { super.onPause(); Catalog.onChange = null }

    private fun play(ch: Channel) { startActivity(Intent(this, PlayerActivity::class.java).putExtra("id", ch.id)) }

    private fun refresh() {
        header.update()
        val playable = Catalog.playableChannels()
        val byLang = playable.groupBy { it.lang }
        val order = byLang.keys.sortedWith(compareBy({ if (it == 1) 0 else if (it == 6) 1 else 2 }, { -byLang.getValue(it).size }))
        val newCats = listOf(Cat(-1, "All channels", playable.size)) + order.map { Cat(it, langName(it), byLang.getValue(it).size) }
        if (newCats != cats) { cats = newCats; catList.adapter?.notifyDataSetChanged() }
        if (cats.none { it.lang == cat }) cat = -1
        val newList = if (cat == -1) playable else playable.filter { it.lang == cat }
        val firstFill = channels.isEmpty() && newList.isNotEmpty()
        if (newList != channels) { channels = newList; chList.adapter?.notifyDataSetChanged() }
        empty.visibility = if (channels.isEmpty()) View.VISIBLE else View.GONE
        if (previewCh == null || channels.none { it.id == previewCh?.id }) channels.firstOrNull()?.let { preview(it) }
        if (firstFill) chList.post { chList.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
    }

    private fun preview(ch: Channel) {
        if (previewCh?.id == ch.id) return
        previewCh = ch
        backdrop.setStyle(ch.id.toInt()); pArt.setStyle(ch.id.toInt()); pLogo.bind(ch)
        pLang.text = langName(ch.lang) + if (ch.hd) " · HD" else ""
        pSub.text = "${ch.name} · ${Catalog.numberOf(ch.id)}"; pTitle.text = ch.name; pMeta.text = ""; pDesc.text = ""
        pStart.text = ""; pEnd.text = ""; pBar.progress = 0f
        Epg.load(ch.id) { list ->
            if (previewCh?.id != ch.id) return@load
            val p = Epg.now(list) ?: return@load
            pTitle.text = p.title; pMeta.text = "Live · until ${fmt(p.end)}"; pDesc.text = p.desc
            pStart.text = fmt(p.start); pEnd.text = fmt(p.end)
            pBar.progress = (System.currentTimeMillis() - p.start).toFloat() / (p.end - p.start).coerceAtLeast(1)
        }
    }

    private inner class CatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = cats.size
        override fun onCreateViewHolder(parent: ViewGroup, t: Int): RecyclerView.ViewHolder {
            val c = this@LiveTvActivity
            val row = LinearLayout(c).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(px(20), 0, px(20), 0)
                layoutParams = RecyclerView.LayoutParams(-1, px(64)).apply { bottomMargin = px(6) }
                addView(txt("", 22, N.TEXT, 500, tag = "p").apply { id = View.generateViewId() }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(txt("", 18, N.DIM, mono = true, tag = "s"))
            }
            return object : RecyclerView.ViewHolder(row) {}
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, p: Int) {
            val c = cats[p]; val row = h.itemView as LinearLayout
            val on = c.lang == cat
            (row.getChildAt(0) as TextView).apply { text = c.label; setTextColor(if (on) N.TEXT else N.MUTED); tag = if (on) "p" else "s" }
            (row.getChildAt(1) as TextView).text = c.count.toString()
            row.focusSkin(px(14), if (on) N.SEL else Color.TRANSPARENT, 1.02f)
            val skin = row.onFocusChangeListener
            row.setOnFocusChangeListener { v, f ->
                skin.onFocusChange(v, f)
                if (f) ui.postDelayed({ if (v.hasFocus()) select(c.lang) }, 220) // browsing categories switches the list
            }
            row.setOnClickListener { select(c.lang) }
        }
    }

    private fun select(lang: Int) { if (cat != lang) { cat = lang; refresh(); catList.adapter?.notifyDataSetChanged() } }

    private inner class ChAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = channels.size
        override fun onCreateViewHolder(parent: ViewGroup, t: Int): RecyclerView.ViewHolder {
            val c = this@LiveTvActivity
            val num = c.txt("", 22, N.MUTED, mono = true, tag = "s")
            val logo = LogoBox(c, 56, 12)
            val name = c.txt("", 22, N.TEXT, 600, tag = "p").apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
            val prog = c.txt("", 20, N.MUTED, tag = "s").apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
            val bar = Bar(c, px(2)); val star = c.icon(R.drawable.ic_star, 22, N.ACCENT, "a")
            val row = LinearLayout(c).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(px(24), 0, px(24), 0); focusSkin(px(16), Color.TRANSPARENT, 1.03f)
                layoutParams = RecyclerView.LayoutParams(-1, px(96)).apply { bottomMargin = px(8) }
                addView(num, LinearLayout.LayoutParams(px(56), -2))
                addView(logo, LinearLayout.LayoutParams(px(56), px(56)).apply { marginEnd = px(20) })
                addView(LinearLayout(c).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL; addView(name, LinearLayout.LayoutParams(0, -2, 1f)); addView(star, LinearLayout.LayoutParams(px(22), px(22)).apply { marginStart = px(10) }) })
                    addView(prog, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(4) })
                    addView(bar, LinearLayout.LayoutParams(px(220), px(4)).apply { topMargin = px(8) })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(liveBadge())
            }
            val refs = arrayOf<Any?>(num, logo, name, prog, bar, star, null) // [6] = channel currently bound
            row.tag = refs
            val skin = row.onFocusChangeListener
            row.setOnFocusChangeListener { x, f -> skin.onFocusChange(x, f); if (f) (refs[6] as? Channel)?.let { preview(it) } }
            return object : RecyclerView.ViewHolder(row) {}
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, p: Int) {
            val ch = channels[p]; val row = h.itemView
            @Suppress("UNCHECKED_CAST") val v = row.tag as Array<Any?>
            v[6] = ch
            (v[0] as TextView).text = Catalog.numberOf(ch.id).toString(); (v[1] as LogoBox).bind(ch)
            (v[2] as TextView).text = ch.name; (v[3] as TextView).text = langName(ch.lang); (v[4] as Bar).progress = 0f
            (v[5] as View).visibility = if (Catalog.isFav(ch.id)) View.VISIBLE else View.GONE
            Epg.load(ch.id) { list ->
                if (h.bindingAdapterPosition != p || channels.getOrNull(p)?.id != ch.id) return@load
                Epg.now(list)?.let { pr -> (v[3] as TextView).text = pr.title; (v[4] as Bar).progress = (System.currentTimeMillis() - pr.start).toFloat() / (pr.end - pr.start).coerceAtLeast(1) }
            }
            row.setOnClickListener { play(ch) }
            row.setOnLongClickListener { Catalog.toggleFav(ch.id); notifyItemChanged(p); true }
        }
    }
}
