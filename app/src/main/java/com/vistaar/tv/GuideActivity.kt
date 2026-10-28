package com.vistaar.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Guide: pick a channel on the left, see today's programmes on the right. */
class GuideActivity : Activity() {
    private lateinit var header: Header
    private lateinit var list: RecyclerView
    private lateinit var title: TextView
    private lateinit var sub: TextView
    private lateinit var rows: LinearLayout
    private lateinit var scroll: ScrollView
    private var channels: List<Channel> = emptyList()
    private var shown: Channel? = null
    private val ui = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        if (!Store.loggedIn) { startActivity(Intent(this, LoginActivity::class.java)); finish(); return }
        header = Header(this, GuideActivity::class.java)
        list = RecyclerView(this).apply { layoutManager = LinearLayoutManager(context); adapter = Rows(); itemAnimator = null; noClip(); setPadding(px(8), px(8), px(8), px(40)) }
        title = txt("", 40, N.TEXT, 600); sub = txt("", 22, N.MUTED)
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(rows) }
        setContentView(android.widget.FrameLayout(this).apply {
            setBackgroundColor(N.BG); noClip()
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(txt("Guide", 48, N.TEXT, 600).apply { letterSpacing = -0.01f }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(8) })
                addView(LinearLayout(context).apply {
                    noClip()
                    addView(list, LinearLayout.LayoutParams(px(620), -1))
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL; background = rr(N.SURFACE2, px(24)); setPadding(px(40), px(36), px(40), px(24))
                        addView(sub); addView(title, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(6); bottomMargin = px(20) })
                        addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
                    }, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = px(40) })
                }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = px(28) })
            }, android.widget.FrameLayout.LayoutParams(-1, -1).apply { setMargins(px(88), px(160), px(96), px(54)) })
            addView(header.view, header.layoutParams())
        })
    }

    override fun onResume() {
        super.onResume()
        if (!Store.loggedIn) return
        Catalog.onChange = { header.update(); refresh() }
        Catalog.start(false); refresh()
    }

    override fun onPause() { super.onPause(); Catalog.onChange = null }

    private fun refresh() {
        val l = Catalog.playableChannels()
        if (l != channels) {
            channels = l; list.adapter?.notifyDataSetChanged()
            if (shown == null && l.isNotEmpty()) {
                val want = intent.getLongExtra("id", -1)
                val start = l.firstOrNull { it.id == want } ?: Catalog.recents().firstOrNull() ?: l.first()
                show(start); list.scrollToPosition(l.indexOf(start))
                list.post { list.findViewHolderForAdapterPosition(l.indexOf(start))?.itemView?.requestFocus() }
            }
        }
    }

    private fun show(ch: Channel) {
        if (shown?.id == ch.id) return
        shown = ch; title.text = ch.name; sub.text = "${Catalog.numberOf(ch.id)}  ·  ${langName(ch.lang)}"
        rows.removeAllViews()
        Epg.load(ch.id) { epg ->
            if (shown?.id != ch.id) return@load
            val now = System.currentTimeMillis(); var nowView: View? = null
            if (epg.isEmpty()) rows.addView(txt("No schedule is available for this channel right now.", 22, N.MUTED))
            epg.forEach { p ->
                val live = now in p.start..p.end; val past = p.end < now
                val row = LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL; setPadding(px(20), px(14), px(20), px(14)); alpha = if (past) 0.45f else 1f
                    if (live) background = rr(N.SEL, px(14))
                    addView(txt(fmt(p.start), 22, if (live) N.TEXT else N.MUTED, mono = true), LinearLayout.LayoutParams(px(150), -2))
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(txt(p.title, 26, N.TEXT, 600).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                        if (p.desc.isNotEmpty()) addView(txt(p.desc, 18, N.MUTED).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(4) })
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    if (live) addView(liveBadge())
                }
                rows.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(6) })
                if (live) nowView = row
            }
            nowView?.let { v -> scroll.post { scroll.scrollTo(0, (v.top - px(40)).coerceAtLeast(0)) } }
        }
    }

    private inner class Rows : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = channels.size
        override fun onCreateViewHolder(parent: ViewGroup, t: Int): RecyclerView.ViewHolder {
            val c = this@GuideActivity
            val num = c.txt("", 22, N.MUTED, mono = true, tag = "s"); val logo = LogoBox(c, 56, 12)
            val name = c.txt("", 22, N.TEXT, 600, tag = "p").apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
            val now = c.txt("", 18, N.MUTED, tag = "s").apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
            val row = LinearLayout(c).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(px(20), 0, px(20), 0); focusSkin(px(16), Color.TRANSPARENT, 1.02f)
                layoutParams = RecyclerView.LayoutParams(-1, px(88)).apply { bottomMargin = px(6) }
                addView(num, LinearLayout.LayoutParams(px(64), -2)); addView(logo, LinearLayout.LayoutParams(px(56), px(56)).apply { marginEnd = px(20) })
                addView(LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; addView(name); addView(now) }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            val refs = arrayOf<Any?>(num, logo, name, now, null)
            row.tag = refs
            val skin = row.onFocusChangeListener
            row.setOnFocusChangeListener { x, f ->
                skin.onFocusChange(x, f)
                if (f) (refs[4] as? Channel)?.let { ch -> pending?.let { r -> ui.removeCallbacks(r) }; pending = Runnable { if (x.hasFocus()) show(ch) }.also { ui.postDelayed(it, 160) } }
            }
            return object : RecyclerView.ViewHolder(row) {}
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, p: Int) {
            val ch = channels[p]; @Suppress("UNCHECKED_CAST") val v = h.itemView.tag as Array<Any?>
            v[4] = ch; (v[0] as TextView).text = Catalog.numberOf(ch.id).toString(); (v[1] as LogoBox).bind(ch)
            (v[2] as TextView).text = ch.name; (v[3] as TextView).text = langName(ch.lang)
            Epg.load(ch.id) { l -> if (v[4] === ch) Epg.now(l)?.let { (v[3] as TextView).text = it.title } }
            h.itemView.setOnClickListener { startActivity(Intent(this@GuideActivity, PlayerActivity::class.java).putExtra("id", ch.id)) }
        }
    }
}
