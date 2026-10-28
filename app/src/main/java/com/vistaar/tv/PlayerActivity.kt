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
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

/** The live player: playback logic unchanged, with an overlay for info, controls, a side sheet and an error card. */
class PlayerActivity : Activity() {
    private data class Item(val label: String, val sub: String = "", val checked: Boolean = false)
    private data class Track(val group: TrackGroup, val index: Int, val format: Format)

    private lateinit var player: ExoPlayer
    private lateinit var pv: PlayerView
    private lateinit var hud: FrameLayout
    private lateinit var logo: LogoBox
    private lateinit var chName: TextView
    private lateinit var chNum: TextView
    private lateinit var star: ImageView
    private lateinit var title: TextView
    private lateinit var meta: TextView
    private lateinit var liveTag: View
    private lateinit var tStart: TextView
    private lateinit var tEnd: TextView
    private lateinit var bar: Bar
    private lateinit var qualityBtn: View
    private lateinit var qualityLabel: TextView
    private lateinit var aspectLabel: TextView
    private lateinit var favIcon: ImageView
    private lateinit var favLabel: TextView
    private lateinit var panel: LinearLayout
    private lateinit var panelTitle: TextView
    private lateinit var panelList: RecyclerView
    private lateinit var errorCard: FrameLayout
    private lateinit var errTitle: TextView
    private lateinit var errMsg: TextView
    private lateinit var retryBtn: View

    private var channels: List<Channel> = emptyList()
    private var index = 0
    private var gen = 0 // discards results from superseded zaps
    private var retries = 0
    private var lastId = -1L
    private var program: Program? = null
    private var upcoming: Program? = null
    private var panelItems: List<Item> = emptyList()
    private var panelPick: (Int) -> Unit = {}
    private val ui = Handler(Looper.getMainLooper())
    private val modes = listOf(AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit", AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom", AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch")
    private var mode = 0

    private val barVisible get() = hud.visibility == View.VISIBLE
    private val panelOpen get() = panel.visibility == View.VISIBLE
    private val errorShown get() = errorCard.visibility == View.VISIBLE
    private val hide = Runnable { if (!panelOpen && !errorShown) hud.animate().alpha(0f).setDuration(300).withEndAction { hud.visibility = View.GONE }.start() }
    private val tick = object : Runnable { override fun run() { if (barVisible) { refreshProgress(); ui.postDelayed(this, 15_000) } } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // The CDN hands out its auth cookie on the master playlist; variants/segments need it replayed.
        if (java.net.CookieHandler.getDefault() == null) java.net.CookieHandler.setDefault(java.net.CookieManager())

        channels = Catalog.playableChannels()
        index = channels.indexOfFirst { it.id == intent.getLongExtra("id", -1) }
        if (index < 0) return finish()

        player = ExoPlayer.Builder(this).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) {
                    val http = e.cause as? HttpDataSource.InvalidResponseCodeException
                    android.util.Log.e("Vistaar", "player error ${e.errorCodeName}: ${e.cause} uri=${http?.dataSpec?.uri?.let { it.host + it.path }}")
                    // Live token/edge expiry: re-resolve the URL a couple of times instead of dying.
                    if (retries++ < 2) { setMeta("Reconnecting…"); reveal(true); tune(index) }
                    else showError("Playback error", "The stream stopped (${e.errorCodeName}). Check your connection and try again.")
                }
                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) { updateLabels() }
            })
        }
        pv = PlayerView(this).apply { useController = false; player = this@PlayerActivity.player }
        buildOverlay(); buildPanel(); buildError()
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK); noClip()
            addView(pv, FrameLayout.LayoutParams(-1, -1))
            addView(hud, FrameLayout.LayoutParams(-1, -1))
            addView(panel, FrameLayout.LayoutParams(px(600), -1, Gravity.END).apply { setMargins(0, px(54), px(96), px(54)) })
            addView(errorCard, FrameLayout.LayoutParams(-1, -1))
        })
        tune(index)
    }

    // ---------------------------------------------------------------- UI

    private fun control(res: Int, label: String?, desc: String, click: () -> Unit): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER; contentDescription = desc; focusSkin(px(14), N.FILL, 1.06f); setPadding(px(if (label == null) 0 else 24), 0, px(if (label == null) 0 else 24), 0)
        addView(icon(res, 24, N.TEXT, "p"), LinearLayout.LayoutParams(px(24), px(24)))
        if (label != null) addView(txt(label, 22, N.TEXT, 500, tag = "p"), LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(12) })
        setOnClickListener { click() }
    }

    private fun buildOverlay() {
        logo = LogoBox(this, 64, 14)
        chName = txt("", 22, N.TEXT, 600); chNum = txt("", 20, N.MUTED, mono = true)
        star = icon(R.drawable.ic_star, 26, N.ACCENT)
        title = txt("", 52, N.TEXT, 600).apply { letterSpacing = -0.02f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        meta = txt("", 22, N.TEXT2); liveTag = liveBadge()
        tStart = txt("", 20, N.TEXT2, mono = true); tEnd = txt("", 20, N.TEXT2, mono = true); bar = Bar(this, px(3))

        val up = control(R.drawable.ic_up, null, "Channel up") { tune(index - 1) }
        val down = control(R.drawable.ic_down, null, "Channel down") { tune(index + 1) }
        val fav = control(R.drawable.ic_star_outline, "Favourite", "Toggle favourite") { toggleFav() }
        favIcon = fav.getChildAt(0) as ImageView; favLabel = fav.getChildAt(1) as TextView
        qualityBtn = control(R.drawable.ic_quality, "Quality", "Quality") { openQuality() }
        qualityLabel = (qualityBtn as LinearLayout).getChildAt(1) as TextView
        val audio = control(R.drawable.ic_audio, "Audio", "Audio") { openAudio() }
        val aspect = control(R.drawable.ic_aspect, "Fit", "Aspect ratio") { cycleAspect() }
        aspectLabel = aspect.getChildAt(1) as TextView
        val guide = control(R.drawable.ic_guide, "Guide", "Programme guide") { startActivity(Intent(this, GuideActivity::class.java).putExtra("id", channels[index].id)) }
        val row = LinearLayout(this).apply {
            noClip(); gravity = Gravity.CENTER_VERTICAL
            addView(up, LinearLayout.LayoutParams(px(64), px(64)))
            listOf(down to 64, fav to 0, qualityBtn to 0, audio to 0, aspect to 0, guide to 0).forEach { (v, w) ->
                addView(v, LinearLayout.LayoutParams(if (w > 0) px(w) else -2, px(64)).apply { marginStart = px(16) })
            }
        }
        val close = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; background = rr(0x9907080A.toInt(), px(26)); setPadding(px(16), 0, px(22), 0)
            addView(icon(R.drawable.ic_left, 20, N.TEXT2), LinearLayout.LayoutParams(px(20), px(20)))
            addView(txt("Back to channels", 20, N.TEXT2), LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(8) })
        }
        hud = FrameLayout(this).apply {
            noClip(); visibility = View.GONE
            addView(View(context).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x00000000, 0x9E000000.toInt(), 0xE6000000.toInt())) },
                FrameLayout.LayoutParams(-1, px(620), Gravity.BOTTOM))
            addView(close, FrameLayout.LayoutParams(-2, px(52)).apply { setMargins(px(96), px(54), 0, 0) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; noClip()
                addView(LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(logo, LinearLayout.LayoutParams(px(64), px(64)))
                    addView(LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; addView(chName); addView(chNum) }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(16) })
                    addView(star, LinearLayout.LayoutParams(px(26), px(26)).apply { marginStart = px(16) })
                })
                addView(title, LinearLayout.LayoutParams(px(1500), -2).apply { topMargin = px(26) })
                addView(LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; addView(liveTag); addView(meta, LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(16) }) },
                    LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(22) })
                addView(LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(tStart); addView(bar, LinearLayout.LayoutParams(0, px(6), 1f).apply { setMargins(px(20), 0, px(20), 0) }); addView(tEnd)
                }, LinearLayout.LayoutParams(px(1500), -2).apply { topMargin = px(26) })
                addView(row, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(26) })
                addView(txt("▲ ▼ change channel   ·   OK controls   ·   Back to leave", 18, N.MUTED), LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(22) })
            }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM).apply { setMargins(px(96), 0, 0, px(54)) })
        }
    }

    private fun buildPanel() {
        panelTitle = txt("", 36, N.TEXT, 600)
        panelList = RecyclerView(this).apply { layoutManager = LinearLayoutManager(context); adapter = PanelAdapter(); itemAnimator = null; noClip(); setPadding(px(6), px(6), px(6), px(6)) }
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE; noClip()
            background = rr(0xDB16191F.toInt(), px(24), 0x14FFFFFF, px(1)); setPadding(px(34), px(40), px(34), px(30))
            addView(panelTitle, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = px(24); marginStart = px(6) })
            addView(panelList, LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }

    private fun buildError() {
        errTitle = txt("", 40, N.TEXT, 600); errMsg = txt("", 22, N.MUTED).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.3f) }
        retryBtn = LinearLayout(this).apply {
            gravity = Gravity.CENTER; focusSkin(px(14), N.FILL, 1.04f); setPadding(px(32), 0, px(32), 0)
            addView(txt("Try again", 24, N.TEXT, 600, tag = "p")); setOnClickListener { hideError(); retries = 0; tune(index) }
        }
        val close = LinearLayout(this).apply {
            gravity = Gravity.CENTER; focusSkin(px(14), N.FILL, 1.04f); setPadding(px(32), 0, px(32), 0)
            addView(txt("Back to channels", 24, N.TEXT, 500, tag = "p")); setOnClickListener { finish() }
        }
        errorCard = FrameLayout(this).apply {
            visibility = View.GONE; setBackgroundColor(0xF207080A.toInt()); isClickable = true
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; noClip()
                addView(FrameLayout(context).apply { background = rr(N.SURFACE, px(60)); addView(icon(R.drawable.ic_warning, 56, N.MUTED), FrameLayout.LayoutParams(px(56), px(56), Gravity.CENTER)) }, LinearLayout.LayoutParams(px(120), px(120)))
                addView(errTitle, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(32) })
                addView(errMsg, LinearLayout.LayoutParams(px(760), -2).apply { topMargin = px(14) })
                addView(LinearLayout(context).apply {
                    noClip(); addView(retryBtn, LinearLayout.LayoutParams(-2, px(68)))
                    addView(close, LinearLayout.LayoutParams(-2, px(68)).apply { marginStart = px(20) })
                }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(40) })
            }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
    }

    private fun showError(t: String, m: String) {
        errTitle.text = t; errMsg.text = m; errorCard.visibility = View.VISIBLE; retryBtn.requestFocus()
        ui.removeCallbacks(hide); hud.visibility = View.GONE; panel.visibility = View.GONE
    }

    private fun hideError() { errorCard.visibility = View.GONE }

    // ---------------------------------------------------------------- info hud

    private fun refreshProgress() {
        val p = program ?: return
        bar.progress = (System.currentTimeMillis() - p.start).toFloat() / (p.end - p.start).coerceAtLeast(1)
    }

    private fun setMeta(s: String) { meta.text = s }

    private fun selectedVideoHeight(): Int? {
        val o = player.trackSelectionParameters.overrides.values.firstOrNull { it.type == C.TRACK_TYPE_VIDEO } ?: return null
        return videoTracks().firstOrNull { it.group == o.mediaTrackGroup && o.trackIndices.contains(it.index) }?.format?.height
    }

    private fun updateLabels() {
        if (!::qualityLabel.isInitialized) return
        val h = selectedVideoHeight()
        qualityLabel.text = "Quality: " + (if (h != null && h > 0) "${h}p" else "Auto")
        aspectLabel.text = modes[mode].second
        val fav = Catalog.isFav(channels[index].id)
        favIcon.setImageResource(if (fav) R.drawable.ic_star else R.drawable.ic_star_outline); favIcon.tag = if (fav) "a" else "p"; favIcon.setColorFilter(if (fav) N.ACCENT else N.TEXT)
        favLabel.text = if (fav) "Favourited" else "Favourite"; star.visibility = if (fav) View.VISIBLE else View.GONE
    }

    /** Fills and shows the hud, then hides it after 7s. [focus] moves the remote onto the controls. */
    private fun showInfo(focus: Boolean = false) {
        val ch = channels[index]; val p = program
        logo.bind(ch); chName.text = ch.name; chNum.text = Catalog.numberOf(ch.id).toString()
        title.text = p?.title ?: ch.name
        meta.text = listOfNotNull(langName(ch.lang), if (ch.hd) "HD" else null, p?.let { "Until ${fmt(it.end)}" }, upcoming?.let { "Next: ${it.title}" }).joinToString("  ·  ")
        tStart.text = p?.let { fmt(it.start) } ?: ""; tEnd.text = p?.let { fmt(it.end) } ?: ""
        bar.visibility = if (p != null) View.VISIBLE else View.INVISIBLE; refreshProgress(); updateLabels(); reveal(false)
        if (focus) qualityBtn.requestFocus()
    }

    private fun showLoading() {
        val ch = channels[index]
        logo.bind(ch); chName.text = ch.name; chNum.text = Catalog.numberOf(ch.id).toString(); title.text = ch.name; meta.text = "Loading…"
        tStart.text = ""; tEnd.text = ""; bar.progress = 0f; updateLabels(); reveal(true)
    }

    private fun reveal(sticky: Boolean) {
        ui.removeCallbacks(hide); ui.removeCallbacks(tick)
        hud.animate().cancel(); hud.alpha = 1f; hud.visibility = View.VISIBLE
        ui.postDelayed(tick, 15_000)
        if (!sticky) ui.postDelayed(hide, 7000)
    }

    private fun bump() { if (barVisible && !panelOpen) { ui.removeCallbacks(hide); ui.postDelayed(hide, 7000) } }

    // ---------------------------------------------------------------- side sheet

    private fun openPanel(heading: String, items: List<Item>, pick: (Int) -> Unit) {
        panelTitle.text = heading; panelItems = items; panelPick = pick
        panelList.adapter?.notifyDataSetChanged()
        ui.removeCallbacks(hide); panel.visibility = View.VISIBLE
        val sel = items.indexOfFirst { it.checked }.coerceAtLeast(0)
        (panelList.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(sel, px(120))
        panelList.post { panelList.findViewHolderForAdapterPosition(sel)?.itemView?.requestFocus() }
    }

    private fun closePanel() { panel.visibility = View.GONE; if (barVisible) { qualityBtn.requestFocus(); bump() } }

    private fun videoTracks(): List<Track> = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        .flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { Track(g.mediaTrackGroup, it, g.getTrackFormat(it)) } }
        .sortedWith(compareByDescending<Track> { it.format.height }.thenByDescending { it.format.bitrate })
        .distinctBy { if (it.format.height > 0) it.format.height else it.format.bitrate }

    private fun openQuality() {
        val tracks = videoTracks()
        val o = player.trackSelectionParameters.overrides.values.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
        val items = listOf(Item("Auto", "Adapts to your connection", o == null)) + tracks.map { t ->
            val mbps = if (t.format.bitrate > 0) "%.1f Mbps".format(t.format.bitrate / 1_000_000.0) else ""
            Item(if (t.format.height > 0) "${t.format.height}p" else mbps, if (t.format.height > 0) mbps else "",
                o != null && o.mediaTrackGroup == t.group && o.trackIndices.contains(t.index))
        }
        openPanel("Quality", items) { i ->
            val params = player.trackSelectionParameters.buildUpon()
            player.trackSelectionParameters = if (i == 0) params.clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
                else params.setOverrideForType(TrackSelectionOverride(tracks[i - 1].group, tracks[i - 1].index)).build()
            closePanel(); updateLabels()
        }
    }

    private fun openAudio() {
        val tracks = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { Triple(g.mediaTrackGroup, it, g.getTrackFormat(it)) } }
        val selected = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { g -> (0 until g.length).filter { g.isTrackSelected(it) }.map { g.getTrackFormat(it) } }
        val items = tracks.mapIndexed { n, (_, _, f) ->
            val name = f.language?.takeIf { it.isNotEmpty() && it != "und" }?.let { Locale(it).getDisplayLanguage(Locale.ENGLISH) } ?: f.label ?: "Track ${n + 1}"
            Item(name, listOfNotNull(f.channelCount.takeIf { it > 0 }?.let { "$it ch" }, f.codecs).joinToString("  ·  "), selected.contains(f))
        }
        if (items.isEmpty()) { setMeta("No alternative audio tracks on this channel"); reveal(false); return }
        openPanel("Audio", items) { i ->
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(tracks[i].first, tracks[i].second)).build()
            closePanel()
        }
    }

    private fun cycleAspect() { mode = (mode + 1) % modes.size; pv.resizeMode = modes[mode].first; updateLabels(); bump() }

    private fun toggleFav() { Catalog.toggleFav(channels[index].id); updateLabels(); bump() }

    private inner class PanelAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = panelItems.size
        override fun onCreateViewHolder(parent: ViewGroup, t: Int): RecyclerView.ViewHolder {
            val c = this@PlayerActivity
            val label = c.txt("", 22, N.TEXT, 600, tag = "p"); val sub = c.txt("", 18, N.MUTED, tag = "s"); val check = c.icon(R.drawable.ic_check, 26, N.ACCENT, "a")
            val row = LinearLayout(c).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(px(20), 0, px(20), 0); focusSkin(px(14), Color.TRANSPARENT, 1.03f)
                layoutParams = RecyclerView.LayoutParams(-1, px(72)).apply { bottomMargin = px(8) }
                addView(LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; addView(label); addView(sub) }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(check, LinearLayout.LayoutParams(px(26), px(26)))
            }
            row.tag = arrayOf<View>(label, sub, check)
            return object : RecyclerView.ViewHolder(row) {}
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, p: Int) {
            val it = panelItems[p]; @Suppress("UNCHECKED_CAST") val v = h.itemView.tag as Array<View>
            (v[0] as TextView).text = it.label
            (v[1] as TextView).apply { text = it.sub; visibility = if (it.sub.isEmpty()) View.GONE else View.VISIBLE }
            v[2].visibility = if (it.checked) View.VISIBLE else View.INVISIBLE
            h.itemView.setOnClickListener { panelPick(h.bindingAdapterPosition) }
        }
    }

    // ---------------------------------------------------------------- playback

    private fun tune(i: Int) {
        index = (i + channels.size) % channels.size
        val ch = channels[index]; val g = ++gen
        if (ch.id != lastId) {
            retries = 0
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).clearOverridesOfType(C.TRACK_TYPE_AUDIO).build()
        }
        hideError(); lastId = ch.id; program = null; upcoming = null
        showLoading()
        Thread {
            try {
                val epg = Api.epg(ch.id)
                val t = System.currentTimeMillis()
                val cur = epg.firstOrNull { t in it.start..it.end }
                val nxt = cur?.let { c -> epg.firstOrNull { it.start >= c.end } }
                val play = Api.streamUrl(ch, cur)
                runOnUiThread {
                    if (g != gen) return@runOnUiThread
                    program = cur; upcoming = nxt; Catalog.pushRecent(ch.id)
                    val headers = Api.authHeaders(ch.id, cur?.srno ?: 0)
                    val ds = DefaultHttpDataSource.Factory().setUserAgent(Api.UA).setAllowCrossProtocolRedirects(true)
                        .setDefaultRequestProperties(headers)
                    player.setMediaSource(
                        if (play.dash) DashMediaSource.Factory(ds).createMediaSource(
                            MediaItem.Builder().setUri(play.url).setMimeType(MimeTypes.APPLICATION_MPD)
                                .setDrmConfiguration(MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                                    .setLicenseUri(play.license).setMultiSession(true).setLicenseRequestHeaders(headers).build())
                                .build())
                        else HlsMediaSource.Factory(ds).setPlaylistParserFactory(NoIFramePlaylistParserFactory())
                            .createMediaSource(MediaItem.fromUri(play.url)))
                    player.prepare()
                    showInfo()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (g == gen) showError("Channel unavailable", "${ch.name} can't be played right now. It may not be part of your plan, or the broadcaster is off air.\n(${e.message?.take(90)})")
                }
            }
        }.start()
    }

    override fun onKeyDown(code: Int, e: KeyEvent): Boolean {
        if (panelOpen || errorShown) return super.onKeyDown(code, e) // let the sheet / error buttons handle focus movement
        bump()
        return when (code) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { tune(index - 1); true }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { tune(index + 1); true }
            // Reaching here means no focused control took the key: bring the hud up and focus its controls.
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> { showInfo(focus = true); true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> if (!barVisible) { showInfo(focus = true); true } else super.onKeyDown(code, e)
            else -> super.onKeyDown(code, e)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            panelOpen -> closePanel()
            errorShown -> finish()
            barVisible -> { ui.removeCallbacks(hide); hud.visibility = View.GONE }
            else -> super.onBackPressed()
        }
    }

    override fun onStop() { super.onStop(); player.pause() }
    override fun onStart() { super.onStart(); if (::player.isInitialized) player.play() }
    override fun onDestroy() { super.onDestroy(); ui.removeCallbacksAndMessages(null); if (::player.isInitialized) player.release() }
}
