package com.vistaar.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
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
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Programme guide: a time grid. Channels down the left, a 3-hour window of programmes across, the focused programme described above.
 * Left/Right move between programmes (and slide the window at its edges), Up/Down between channels at the same time.
 */
class GuideActivity : Activity() {
    private companion object {
        /** Days the day picker offers (today and the next six), if Jio's guide returns other days. */
        const val DAYS = 7
        const val ROW_H = 88
        const val STEP_MS = Timeline.STEP_MIN * 60_000L
    }

    /** A programme block in a row; [p] is null for the "no schedule" placeholder that keeps the row focusable. */
    private class Blk(val view: View, val p: Program?, val b: Timeline.Block?)

    private lateinit var header: Header
    private lateinit var list: RecyclerView
    private lateinit var pArt: ArtView
    private lateinit var pLive: View
    private lateinit var pWhen: TextView
    private lateinit var pTitle: TextView
    private lateinit var pDesc: TextView
    private lateinit var watch: View
    private lateinit var dayLabel: TextView
    private lateinit var ticks: FrameLayout
    private lateinit var nowPill: TextView
    private lateinit var prevDay: View
    private lateinit var nextDay: View

    private var channels: List<Channel> = emptyList()
    private var day = 0
    private var winStart = 0L
    private var selected: Channel? = null
    private val pp by lazy { PreviewPlayer(this) }
    private var previewId = -1L // channel the live preview is on, so moving between its programmes does not restart it
    private var initial = false
    private val ui = Handler(Looper.getMainLooper())
    private var pendingSel: Runnable? = null
    private val dayFmt = SimpleDateFormat("EEE d", Locale.getDefault())
    private val dayLongFmt = SimpleDateFormat("EEE d MMM", Locale.getDefault())

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        if (!Store.hasAccess) { startActivity(Intent(this, SplashActivity::class.java)); finish(); return }
        header = Header(this, GuideActivity::class.java)
        winStart = Timeline.openStart(System.currentTimeMillis(), 0)

        // ---- selected programme
        pArt = ArtView(this)
        pLive = liveBadge()
        pWhen = txt("", 20, N.MUTED).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        pTitle = txt("", 44, N.TEXT, 600).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; letterSpacing = -0.015f }
        pDesc = txt("", 22, N.TEXT2).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setLineSpacing(0f, 1.2f) }
        watch = LinearLayout(this).apply {
            gravity = Gravity.CENTER; focusSkin(px(14), N.FILL, 1.04f)
            addView(icon(R.drawable.ic_play, 22, N.TEXT, "p"), LinearLayout.LayoutParams(px(22), px(22)))
            addView(txt("Watch Live", 22, N.TEXT, 500, tag = "p"), LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(12) })
        }
        val panel = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; noClip()
            addView(FrameLayout(context).apply { clipRound(px(16)); addView(pArt, FrameLayout.LayoutParams(-1, -1)); addView(pp.view, FrameLayout.LayoutParams(-1, -1)) }, LinearLayout.LayoutParams(px(352), px(198)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(pLive, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = px(14) }); addView(pWhen, LinearLayout.LayoutParams(0, -2, 1f))
                })
                addView(pTitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) })
                addView(pDesc, LinearLayout.LayoutParams(px(980), -2).apply { topMargin = px(10) })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = px(36) })
            addView(watch, LinearLayout.LayoutParams(px(260), px(60)).apply { marginStart = px(36) })
        }

        // ---- time bar: day, ticks with the "now" pill, Earlier / Now / Later
        dayLabel = txt("", 20, N.TEXT, 600).apply { gravity = Gravity.CENTER }
        fun chevron(res: Int, desc: String, click: () -> Unit) = FrameLayout(this).apply {
            focusSkin(px(26), Color.TRANSPARENT, 1.06f); contentDescription = desc
            addView(icon(res, 20, N.TEXT, "p"), FrameLayout.LayoutParams(px(20), px(20), Gravity.CENTER)); setOnClickListener { click() }
        }
        prevDay = chevron(R.drawable.ic_left, "Previous day") { setDay(day - 1) }
        nextDay = chevron(R.drawable.ic_right, "Next day") { setDay(day + 1) }
        val dayPill = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; noClip(); background = rr(N.SURFACE, px(26))
            addView(prevDay, LinearLayout.LayoutParams(px(52), px(52))); addView(dayLabel, LinearLayout.LayoutParams(0, -2, 1f)); addView(nextDay, LinearLayout.LayoutParams(px(52), px(52)))
        }
        ticks = FrameLayout(this).apply { noClip() }
        nowPill = txt("", 18, N.INK, 600, mono = true).apply { gravity = Gravity.CENTER; background = rr(N.TEXT, px(18)); setPadding(px(12), 0, px(12), 0) }
        fun pill(label: String?, res: Int, desc: String, click: () -> Unit) = LinearLayout(this).apply {
            gravity = Gravity.CENTER; focusSkin(px(26), N.SURFACE, 1.04f); contentDescription = desc
            if (label != null) { setPadding(px(20), 0, px(20), 0); addView(txt(label, 20, N.TEXT, 500, tag = "p")) }
            else addView(icon(res, 20, N.TEXT, "p"), LinearLayout.LayoutParams(px(52), px(20)))
            setOnClickListener { click() }
        }
        val earlier = pill(null, R.drawable.ic_left, "Earlier") { shift(-STEP_MS, null) }
        val later = pill(null, R.drawable.ic_right, "Later") { shift(STEP_MS, null) }
        val nowBtn = pill("Now", 0, "Jump to now") { setDay(0, jump = true) }
        val buttons = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; noClip(); setBackgroundColor(N.BG); setPadding(px(12), 0, 0, 0)
            addView(earlier, LinearLayout.LayoutParams(-2, px(52))); addView(nowBtn, LinearLayout.LayoutParams(-2, px(52)).apply { marginStart = px(8) }); addView(later, LinearLayout.LayoutParams(-2, px(52)).apply { marginStart = px(8) })
        }
        val timeBar = FrameLayout(this).apply {
            noClip()
            addView(dayPill, FrameLayout.LayoutParams(px(300), px(52)))
            addView(FrameLayout(context).apply { noClip(); addView(ticks, FrameLayout.LayoutParams(-1, -1)); addView(nowPill, FrameLayout.LayoutParams(-2, px(36))); addView(buttons, FrameLayout.LayoutParams(-2, px(52), Gravity.END)) },
                FrameLayout.LayoutParams(px(Timeline.GRID_W), px(52)).apply { marginStart = px(316) })
        }

        // ---- grid
        list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(context); adapter = Rows(); itemAnimator = null; clipToPadding = false; clipChildren = true; setPadding(0, px(4), 0, px(40)) // clipped at the top so scrolled rows stay under the time bar
            setItemViewCacheSize(4)
        }

        setContentView(FrameLayout(this).apply {
            setBackgroundColor(N.BG); noClip()
            addView(panel, FrameLayout.LayoutParams(-1, px(210)).apply { setMargins(px(96), px(150), px(96), 0) })
            addView(timeBar, FrameLayout.LayoutParams(-1, px(52)).apply { setMargins(px(96), px(384), px(96), 0) })
            addView(list, FrameLayout.LayoutParams(-1, -1).apply { setMargins(px(96), px(444), px(96), px(30)) })
            addView(header.view, header.layoutParams())
        })
        paintTimeBar()
        showSel(null, null)
    }

    override fun onResume() {
        super.onResume()
        if (!Store.hasAccess) return
        Catalog.onChange = { header.update(); refresh() }
        Catalog.start(false); refresh()
        selected?.let { previewChannel(it) }
    }

    override fun onPause() { super.onPause(); Catalog.onChange = null; pp.release(); previewId = -1L } // many TVs have one decoder: free it for the player

    // ---------------------------------------------------------------- data

    private fun refresh() {
        val l = Catalog.playableChannels()
        if (l == channels) return
        channels = l; list.adapter?.notifyDataSetChanged()
        if (!initial && l.isNotEmpty()) {
            initial = true
            val want = intent.getLongExtra("id", -1)
            val start = l.firstOrNull { it.id == want } ?: Catalog.recents().firstOrNull { it in l } ?: l.first()
            val pos = l.indexOf(start)
            (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, 0)
            list.post { focusRow(pos, System.currentTimeMillis()) }
        }
    }

    private fun play(ch: Channel) { startActivity(Intent(this, PlayerActivity::class.java).putExtra("id", ch.id)) }

    // ---------------------------------------------------------------- time bar and window

    private fun paintTimeBar() {
        val now = System.currentTimeMillis()
        dayLabel.text = when (day) { 0 -> "Today · " + dayFmt.format(now); 1 -> "Tomorrow · " + dayFmt.format(Timeline.dayStart(now, 1)); else -> dayLongFmt.format(Timeline.dayStart(now, day)) }
        prevDay.alpha = if (day == 0) 0.3f else 1f; nextDay.alpha = if (day == DAYS - 1) 0.3f else 1f
        ticks.removeAllViews()
        Timeline.ticks(winStart).forEach { (x, t) ->
            ticks.addView(txt(fmt(t).replace(" pm", "").replace(" am", "").uppercase(Locale.ROOT), 20, N.MUTED, mono = true),
                FrameLayout.LayoutParams(-2, -2).apply { leftMargin = px(x); topMargin = px(14) })
        }
        val nx = if (day == 0) Timeline.nowX(winStart, now) else null
        nowPill.visibility = if (nx == null) View.GONE else View.VISIBLE
        if (nx != null) { nowPill.text = SimpleDateFormat("h:mm", Locale.getDefault()).format(now); (nowPill.layoutParams as FrameLayout.LayoutParams).apply { leftMargin = px((nx - 34).coerceAtLeast(0)); topMargin = px(8) } }
    }

    /** Slides the window by [delta]; [keep] is the (channel id, time) to put focus back on afterwards. */
    private fun shift(delta: Long, keep: Pair<Long, Long>?) {
        val next = Timeline.clampStart(winStart + delta, System.currentTimeMillis(), day)
        if (next == winStart) return
        winStart = next; paintTimeBar(); relayout()
        keep?.let { (id, t) -> val pos = channels.indexOfFirst { it.id == id }; if (pos >= 0) list.post { focusRow(pos, t) } }
    }

    private fun setDay(d: Int, jump: Boolean = false) {
        val nd = d.coerceIn(0, DAYS - 1)
        if (nd == day && !jump) return
        day = nd; winStart = Timeline.openStart(System.currentTimeMillis(), day); paintTimeBar()
        list.adapter?.notifyDataSetChanged() // rows reload their programmes for the new day
    }

    private fun relayout() { for (i in 0 until list.childCount) (list.getChildViewHolder(list.getChildAt(i)) as? RowHolder)?.layout() }

    // ---------------------------------------------------------------- selection panel

    private fun showSel(ch: Channel?, p: Program?) {
        selected = ch
        if (ch == null) { pTitle.text = ""; pWhen.text = ""; pDesc.text = ""; pLive.visibility = View.INVISIBLE; watch.visibility = View.GONE; pp.stop(); previewId = -1L; return }
        previewChannel(ch)
        val now = System.currentTimeMillis()
        val airing = p == null || now in p.start until p.end
        pArt.setStyle(ch.id.toInt())
        pLive.visibility = if (airing) View.VISIBLE else View.GONE
        pTitle.text = p?.title ?: Catalog.label(ch)
        pWhen.text = if (p == null) "${Catalog.label(ch)}  ·  ${Catalog.numberOf(ch.id)}" else "${Catalog.label(ch)}  ·  ${if (airing) "Now  ·  " else ""}${fmt(p.start)} – ${fmt(p.end)}"
        pDesc.text = p?.desc?.ifEmpty { null } ?: if (p == null) "No schedule is available for this channel right now." else ""
        watch.visibility = if (airing) View.VISIBLE else View.GONE
        watch.setOnClickListener { play(ch) }
    }

    /** Muted live picture of [ch] in the art tile; restarts only when the channel changes. */
    private fun previewChannel(ch: Channel) { if (previewId == ch.id) return; previewId = ch.id; pp.show(ch) }

    private fun onBlockFocus(ch: Channel, p: Program?) {
        pendingSel?.let { ui.removeCallbacks(it) }
        pendingSel = Runnable { showSel(ch, p) }.also { ui.postDelayed(it, 120) }
    }

    // ---------------------------------------------------------------- keys

    /** Focuses the programme at time [t] in the row at [pos], scrolling to it first if it isn't on screen. */
    private fun focusRow(pos: Int, t: Long) {
        val vh = list.findViewHolderForAdapterPosition(pos) as? RowHolder
        if (vh != null) vh.focusAt(t)
        else { list.scrollToPosition(pos); list.post { (list.findViewHolderForAdapterPosition(pos) as? RowHolder)?.focusAt(t) } }
    }

    private fun blockKey(h: RowHolder, v: View, code: Int, e: KeyEvent): Boolean {
        val i = h.blocks.indexOfFirst { it.view === v }; if (i < 0) return false
        val me = h.blocks[i]
        val ch = h.ch ?: return false
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (e.action != KeyEvent.ACTION_DOWN) return true
                if (i > 0) h.blocks[i - 1].view.requestFocus() else shift(-STEP_MS, ch.id to ((me.p?.start ?: winStart) - 1000))
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (e.action != KeyEvent.ACTION_DOWN) return true
                if (i < h.blocks.size - 1) h.blocks[i + 1].view.requestFocus() else shift(STEP_MS, ch.id to ((me.p?.end ?: Timeline.windowEnd(winStart)) + 1000))
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                val pos = h.bindingAdapterPosition
                val to = if (code == KeyEvent.KEYCODE_DPAD_DOWN) pos + 1 else pos - 1
                if (to >= channels.size) return true // last channel: stay put
                if (to < 0) return false // first channel: up goes on to the time bar
                if (e.action == KeyEvent.ACTION_DOWN) focusRow(to, h.centerTime(me))
                return true
            }
        }
        return false
    }

    // ---------------------------------------------------------------- rows

    private inner class RowHolder(root: View, val logo: LogoBox, val num: TextView, val name: TextView, val timeline: FrameLayout) : RecyclerView.ViewHolder(root) {
        var ch: Channel? = null
        var programs: List<Program>? = null
        var blocks: List<Blk> = emptyList()
        var pendingAt: Long? = null
        var loadedDay = -1

        fun bind(c: Channel) {
            ch = c; programs = null; blocks = emptyList(); loadedDay = day; timeline.removeAllViews()
            logo.bind(c); num.text = Catalog.numberOf(c.id).toString(); name.text = Catalog.label(c)
            val d = day
            Epg.load(c.id, d) { l -> if (ch === c && loadedDay == d) { programs = l; layout() } }
        }

        fun centerTime(b: Blk): Long = b.b?.let { winStart + ((it.x + it.w / 2) / Timeline.PPM * 60_000).toLong() } ?: (winStart + Timeline.SPAN_MIN * 30_000L)

        /** (Re)builds the blocks for the current window. */
        fun layout() {
            val c = ch ?: return; val l = programs ?: return
            val hadFocus = timeline.hasFocus()
            timeline.removeAllViews()
            val now = System.currentTimeMillis()
            val out = ArrayList<Blk>()
            l.forEach { p -> Timeline.block(winStart, p.start, p.end)?.let { b -> out.add(Blk(blockView(this, c, p, b, now), p, b)) } }
            if (out.isEmpty()) out.add(Blk(blockView(this, c, null, null, now), null, null))
            out.forEach { timeline.addView(it.view, FrameLayout.LayoutParams(px(it.b?.w ?: Timeline.GRID_W - Timeline.GAP), px(ROW_H)).apply { leftMargin = px(it.b?.x ?: 0) }) }
            blocks = out
            Timeline.nowX(winStart, now)?.takeIf { day == 0 }?.let { x ->
                timeline.addView(View(this@GuideActivity).apply { setBackgroundColor(0xE6F5F6F8.toInt()) }, FrameLayout.LayoutParams(px(2), px(ROW_H + 8)).apply { leftMargin = px(x); topMargin = -px(4) })
            }
            val at = pendingAt
            if (at != null) { pendingAt = null; itemView.post { focusAt(at) } }
            else if (hadFocus) itemView.post { focusAt(now) }
        }

        fun focusAt(t: Long) {
            if (programs == null) { pendingAt = t; return }
            val target = blocks.firstOrNull { it.p != null && t >= it.p.start && t < it.p.end }
                ?: blocks.minByOrNull { kotlin.math.abs(centerTime(it) - t) } ?: return
            target.view.requestFocus()
        }
    }

    private fun blockView(h: RowHolder, ch: Channel, p: Program?, b: Timeline.Block?, now: Long): View {
        val airing = p != null && now in p.start until p.end
        val past = p != null && p.end <= now
        val idle = if (p == null) 0xFF16191F.toInt() else if (airing) 0xFF20242C.toInt() else if (past) 0xFF0E1014.toInt() else 0xFF16191F.toInt()
        val wide = (b?.w ?: Timeline.GRID_W) > 170
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(px(16), 0, px(16), 0)
            val title = (if (b?.cutLeft == true) "‹ " else "") + (p?.title ?: "No schedule available")
            addView(txt(title, 20, if (past) N.DIM else N.TEXT, if (airing) 600 else 500, tag = if (past) "s" else "p").apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            if (p != null && wide) addView(txt("${fmt(p.start)} – ${fmt(p.end)}", 18, if (past) N.DIM else N.MUTED, tag = "s").apply { maxLines = 1 }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(4) })
        }
        return FrameLayout(this).apply {
            focusSkin(px(12), idle, 1f); contentDescription = p?.title ?: ch.name
            if (airing && b != null) { // the part already aired, tinted
                val elapsed = Math.round(((now - maxOf(p!!.start, winStart)) / 60_000.0 * Timeline.PPM)).toInt().coerceIn(0, b.w)
                addView(View(context).apply { setBackgroundColor(0x245AA9FF) }, FrameLayout.LayoutParams(px(elapsed), -1))
            }
            addView(text, FrameLayout.LayoutParams(-1, -1))
            val skin = onFocusChangeListener
            setOnFocusChangeListener { x, f -> skin.onFocusChange(x, f); if (f) onBlockFocus(ch, p) }
            setOnKeyListener { x, code, e -> blockKey(h, x, code, e) }
            setOnClickListener { if (airing || p == null) play(ch) else showSel(ch, p) }
        }
    }

    private inner class Rows : RecyclerView.Adapter<RowHolder>() {
        override fun getItemCount() = channels.size
        override fun onCreateViewHolder(parent: ViewGroup, t: Int): RowHolder {
            val c = this@GuideActivity
            val logo = LogoBox(c, 52, 12)
            val num = c.txt("", 18, N.DIM, mono = true)
            val name = c.txt("", 20, N.TEXT, 600).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
            val cell = LinearLayout(c).apply {
                gravity = Gravity.CENTER_VERTICAL; background = rr(N.SURFACE2, px(12)); setPadding(px(16), 0, px(16), 0)
                addView(logo, LinearLayout.LayoutParams(px(52), px(52)).apply { marginEnd = px(14) })
                addView(LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; addView(num); addView(name) }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            val timeline = FrameLayout(c).apply { noClip(); clipChildren = true }
            val row = LinearLayout(c).apply {
                noClip(); layoutParams = RecyclerView.LayoutParams(-1, px(ROW_H)).apply { bottomMargin = px(8) }
                addView(cell, LinearLayout.LayoutParams(px(300), -1))
                addView(timeline, LinearLayout.LayoutParams(px(Timeline.GRID_W), -1).apply { marginStart = px(16) })
            }
            return RowHolder(row, logo, num, name, timeline)
        }

        override fun onBindViewHolder(h: RowHolder, p: Int) { h.bind(channels[p]) }
    }
}
