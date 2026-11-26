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
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

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
    private lateinit var guideBtn: View
    private lateinit var qualityLabel: TextView
    private lateinit var aspectLabel: TextView
    private lateinit var favIcon: ImageView
    private lateinit var favLabel: TextView
    private lateinit var panel: LinearLayout
    private lateinit var panelTitle: TextView
    private lateinit var panelList: RecyclerView
    private lateinit var errorCard: FrameLayout
    private lateinit var noAudio: TextView
    private lateinit var errTitle: TextView
    private lateinit var errMsg: TextView
    private lateinit var retryBtn: View

    private var channels: List<Channel> = emptyList()
    private var index = 0
    private var gen = 0 // discards results from superseded zaps
    private var retries = 0
    private var lastId = -1L
    private var tuneAt = 0L
    private var audit = false // debug: zap through the list and log one audio line per channel (adb shell am start ... --ez audit true)
    private val auditNext = Runnable { logAudio(); if (index + 1 < channels.size) tune(index + 1) else android.util.Log.i("Vistaar", "audio audit done") }
    private var audioDone = -1L // channel whose remembered audio language has been applied
    private var program: Program? = null
    private var upcoming: Program? = null
    private var panelItems: List<Item> = emptyList()
    private var panelPick: (Int) -> Unit = {}
    private val ui = Handler(Looper.getMainLooper())
    /** Stream URL + guide for a channel, resolved ahead of time for the neighbouring channels so zapping starts at once. */
    private val ready = ConcurrentHashMap<Long, Streams.Resolved>()
    private val prefetcher = Executors.newSingleThreadExecutor()
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

        val want = intent.getLongExtra("id", -1)
        val ids = intent.getLongArrayExtra("ids") // the list the user was browsing (a category, Free TV, favourites): zap inside it
        channels = if (ids != null) { val byId = (Catalog.all + Free.shown).associateBy { it.id }; ids.toList().mapNotNull { byId[it] } }
            else (if (want < 0) Catalog.freeChannels() else Catalog.playableChannels()) // free channels zap among themselves
                .filter { !intent.getBooleanExtra("hd", false) || it.hd } // opened from the HD tab: zap through HD only
        index = channels.indexOfFirst { it.id == want }
        if (index < 0) return finish()

        player = ExoPlayer.Builder(this, DefaultRenderersFactory(this).setEnableDecoderFallback(true))
            // start playing after 1s of media instead of the 2.5s default
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 50_000, 1_000, 2_000).build()).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) {
                    val http = e.cause as? HttpDataSource.InvalidResponseCodeException
                    android.util.Log.e("Vistaar", "player error ${e.errorCodeName}: ${e.cause} uri=${http?.dataSpec?.uri?.let { it.host + it.path }}")
                    // Live token/edge expiry: re-resolve the URL a couple of times instead of dying.
                    ready.remove(channels[index].id) // its URL may be what expired
                    if (retries++ < 2) { setMeta("Reconnecting…"); reveal(true); tune(index) }
                    else { Catalog.markBad(channels[index].id); showError("Playback error", "The stream stopped (${e.errorCodeName}). Check your connection and try again.") }
                }
                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) { updateLabels(); applySavedAudio(); if (audioStatus() != "NO_AUDIO_TRACK") noAudio.visibility = View.GONE }
                override fun onPlaybackStateChanged(s: Int) {
                    if (s == Player.STATE_READY) { Catalog.clearBad(channels[index].id); ui.removeCallbacks(audioCheck); ui.postDelayed(audioCheck, 3_000) }
                    if (s == Player.STATE_READY && tuneAt != 0L) { android.util.Log.d("Vistaar", "first frame ready ${System.currentTimeMillis() - tuneAt} ms after tune"); tuneAt = 0L }
                }
            })
        }
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioCodecError(t: AnalyticsListener.EventTime, e: Exception) { android.util.Log.w("Vistaar", "audio codec error on ${channels[index].name}: $e") }
            override fun onAudioSinkError(t: AnalyticsListener.EventTime, e: Exception) { android.util.Log.w("Vistaar", "audio sink error on ${channels[index].name}: $e") }
        })
        audit = intent.getBooleanExtra("audit", false)
        pv = PlayerView(this).apply { useController = false; player = this@PlayerActivity.player }
        buildOverlay(); buildPanel(); buildError()
        noAudio = txt("This stream has no audio", 22, N.TEXT, 500).apply {
            visibility = View.GONE; setPadding(px(26), px(14), px(26), px(14)); background = rr(0xDB16191F.toInt(), px(28), 0x24FFFFFF, px(1))
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK); noClip()
            addView(pv, FrameLayout.LayoutParams(-1, -1))
            addView(hud, FrameLayout.LayoutParams(-1, -1))
            addView(panel, FrameLayout.LayoutParams(px(600), -1, Gravity.END).apply { setMargins(0, px(54), px(96), px(54)) })
            addView(noAudio, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, px(54), px(96), 0) })
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

        val fav = control(R.drawable.ic_star_outline, "Favourite", "Toggle favourite") { toggleFav() }
        favIcon = fav.getChildAt(0) as ImageView; favLabel = fav.getChildAt(1) as TextView
        qualityBtn = control(R.drawable.ic_quality, "Quality", "Quality") { openQuality() }
        qualityLabel = (qualityBtn as LinearLayout).getChildAt(1) as TextView
        val audio = control(R.drawable.ic_audio, "Audio", "Audio") { openAudio() }
        val aspect = control(R.drawable.ic_aspect, "Fit", "Aspect ratio") { cycleAspect() }
        aspectLabel = aspect.getChildAt(1) as TextView
        val guide = control(R.drawable.ic_guide, "Guide", "Programme guide") { startActivity(Intent(this, GuideActivity::class.java).putExtra("id", channels[index].id)) }
        guideBtn = guide
        val row = LinearLayout(this).apply {
            noClip(); gravity = Gravity.CENTER_VERTICAL
            listOf(fav, qualityBtn, audio, aspect, guide).forEachIndexed { i, v ->
                addView(v, LinearLayout.LayoutParams(-2, px(64)).apply { if (i > 0) marginStart = px(16) })
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
                addView(txt("▲ ▼ show guide, then change channel   ·   OK controls   ·   Back to leave", 18, N.MUTED), LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(22) })
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
        guideBtn.visibility = if (channels[index].url.isEmpty()) View.VISIBLE else View.GONE // free channels have no guide
        val fav = Catalog.isFav(channels[index].id)
        favIcon.setImageResource(if (fav) R.drawable.ic_star else R.drawable.ic_star_outline); favIcon.tag = if (fav) "a" else "p"; favIcon.setColorFilter(if (fav) N.ACCENT else N.TEXT)
        favLabel.text = if (fav) "Favourited" else "Favourite"; star.visibility = if (fav) View.VISIBLE else View.GONE
    }

    /** Fills and shows the hud, then hides it after 7s. [focus] moves the remote onto the controls. */
    private fun showInfo(focus: Boolean = false) {
        val ch = channels[index]; val p = program
        logo.bind(ch); chName.text = Catalog.label(ch); chNum.text = Catalog.numberOf(ch.id).toString()
        title.text = p?.title ?: Catalog.label(ch)
        meta.text = listOfNotNull(langName(ch.lang), if (ch.hd) "HD" else null, p?.let { "Until ${fmt(it.end)}" }, upcoming?.let { "Next: ${it.title}" }).joinToString("  ·  ")
        tStart.text = p?.let { fmt(it.start) } ?: ""; tEnd.text = p?.let { fmt(it.end) } ?: ""
        bar.visibility = if (p != null) View.VISIBLE else View.INVISIBLE; refreshProgress(); updateLabels(); reveal(false)
        if (focus) qualityBtn.requestFocus()
    }

    private fun showLoading() {
        val ch = channels[index]
        logo.bind(ch); chName.text = Catalog.label(ch); chNum.text = Catalog.numberOf(ch.id).toString(); title.text = Catalog.label(ch); meta.text = "Loading…"
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
            audioKey(tracks[i].third).takeIf { it.isNotEmpty() }?.let { Store.setAudioPref(channels[index].id, it) } // remembered for this channel
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(tracks[i].first, tracks[i].second)).build()
            closePanel()
        }
    }

    private fun audioKey(f: androidx.media3.common.Format) = f.language?.takeIf { it.isNotEmpty() && it != "und" } ?: f.label ?: ""

    /** Re-selects the audio language the user last chose on this channel, once its tracks are known. */
    private fun applySavedAudio() {
        val ch = channels[index]
        if (audioDone == ch.id) return
        val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (groups.isEmpty()) return
        audioDone = ch.id
        val pref = Store.audioPref(ch.id).ifEmpty { return }
        for (g in groups) for (i in 0 until g.length) {
            if (!g.isTrackSupported(i) || audioKey(g.getTrackFormat(i)) != pref) continue
            if (!g.isTrackSelected(i)) player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build()
            return
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

    /** Why a channel might be silent: no audio track in the stream, none the device can decode, none selected, or nothing reaching the output. */
    private fun audioStatus(): String {
        val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (groups.isEmpty()) return "NO_AUDIO_TRACK"
        val fmts = groups.flatMap { g -> (0 until g.length).map { g.getTrackFormat(it) to g.isTrackSupported(it) } }
        if (fmts.none { it.second }) return "UNSUPPORTED:" + fmts.joinToString(",") { it.first.sampleMimeType ?: "?" }
        if (groups.none { it.isSelected }) return "NOT_SELECTED"
        val f = player.audioFormat ?: return "NO_AUDIO_OUTPUT"
        return "OK:${f.sampleMimeType}${f.codecs?.let { "/$it" } ?: ""} ${f.channelCount}ch"
    }
    private fun logAudio() { val ch = channels[index]; android.util.Log.i("Vistaar", "audio [${index + 1}/${channels.size}] ${ch.name} (${ch.id}${if (ch.url.isNotEmpty()) ", free" else ""}) state=${player.playbackState}: ${audioStatus()}") }

    /** A few seconds after playback starts: a stream that carries no audio track at all says so, instead of just being silent. */
    private val audioCheck = Runnable { noAudio.visibility = if (player.playbackState == Player.STATE_READY && audioStatus() == "NO_AUDIO_TRACK") View.VISIBLE else View.GONE }

    private fun tune(i: Int) {
        ui.removeCallbacks(audioCheck); noAudio.visibility = View.GONE
        index = (i + channels.size) % channels.size
        val ch = channels[index]; val g = ++gen
        if (ch.id != lastId) {
            retries = 0
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).clearOverridesOfType(C.TRACK_TYPE_AUDIO).build()
        }
        if (ch.lang <= 0) player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setPreferredAudioLanguages().build() // free channels: drop the previous channel's preference
        if (ch.lang > 0) { // no saved choice: prefer the channel's own language, then the user's chosen ones
            val prefer = (listOfNotNull(LANG_CODES[ch.lang]) + Store.langs.mapNotNull { LANG_CODES[it] }).distinct()
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setPreferredAudioLanguages(*prefer.toTypedArray()).build()
        }
        hideError(); lastId = ch.id; program = null; upcoming = null
        tuneAt = System.currentTimeMillis()
        if (audit) { ui.removeCallbacks(auditNext); ui.postDelayed(auditNext, 8_000) }
        showLoading()
        Thread {
            try {
                val free = ch.url.isNotEmpty() // third-party stream: never send Jio tokens to it
                val r = ready.remove(ch.id)?.takeIf { System.currentTimeMillis() - it.at < 90_000 } ?: Streams.resolve(ch)
                val cur = r.cur; val nxt = r.nxt; val play = r.play
                runOnUiThread {
                    if (g != gen) return@runOnUiThread
                    program = cur; upcoming = nxt; Catalog.pushRecent(ch.id)
                    player.setMediaSource(Streams.source(ch, play, cur?.srno ?: 0))
                    player.prepare()
                    showInfo()
                    prefetch(index + 1); prefetch(index - 1)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (g == gen) showError("Channel unavailable", when {
                        ch.url.isNotEmpty() -> "${ch.name} can't be played right now. Free streams go offline often, so try another channel."
                        e.message?.contains("419") == true -> "Your Jio session has expired. Sign out and sign in again from Settings."
                        else -> "${ch.name} can't be played right now. It may not be part of your plan, or the broadcaster is off air."
                    } + "\n(${e.message?.take(90)})")
                }
            }
        }.start()
    }

    /** Warms the next/previous Jio channel so a channel change doesn't wait on two round trips. */
    private fun prefetch(i: Int) {
        val ch = channels[(i + channels.size) % channels.size]
        if (ch.url.isNotEmpty() || ready[ch.id]?.let { System.currentTimeMillis() - it.at < 60_000 } == true) return
        ready.values.removeAll { System.currentTimeMillis() - it.at > 90_000 }
        prefetcher.execute { try { ready[ch.id] = Streams.resolve(ch) } catch (_: Exception) { } }
    }

    override fun onKeyDown(code: Int, e: KeyEvent): Boolean {
        if (panelOpen || errorShown) return super.onKeyDown(code, e) // let the sheet / error buttons handle focus movement
        val wasBarVisible = barVisible // before bump(): a hidden guide must not count as showing
        bump()
        return when (code) {
            // Up/Down first bring up the guide on the picture; only while it is showing do they change the channel. The remote's CH +/- always change it.
            KeyEvent.KEYCODE_DPAD_UP -> { if (wasBarVisible) tune(index - 1) else showInfo(); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { if (wasBarVisible) tune(index + 1) else showInfo(); true }
            KeyEvent.KEYCODE_CHANNEL_UP -> { tune(index - 1); true }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> { tune(index + 1); true }
            // Reaching here means no focused control took the key: bring the hud up and focus its controls.
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> { showInfo(focus = true); true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> if (!barVisible) { showInfo(focus = true); true } else super.onKeyDown(code, e)
            KeyEvent.KEYCODE_PROG_RED -> if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) { audit = true; android.util.Log.i("Vistaar", "audio audit started at ${channels[index].name}"); tune(index); true } else super.onKeyDown(code, e) // debug builds only
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
    override fun onDestroy() { super.onDestroy(); prefetcher.shutdownNow(); ui.removeCallbacksAndMessages(null); if (::player.isInitialized) player.release() }
}
