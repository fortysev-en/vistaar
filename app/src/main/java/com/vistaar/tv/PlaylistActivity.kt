package com.vistaar.tv

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Free TV playlist: pick an iptv-org country playlist or type any M3U link with the on-screen keyboard. The old list is kept if the new one fails. */
class PlaylistActivity : Activity(), RemoteText {
    override fun onRemoteText(s: String) { url = s.trim().take(300); update() }
    private lateinit var urlText: TextView
    private lateinit var status: TextView
    private var url = ""
    private var busy = false

    private val presets = listOf("India" to "in", "United States" to "us", "United Kingdom" to "gb", "Pakistan" to "pk", "Bangladesh" to "bd", "Nepal" to "np",
        "Sri Lanka" to "lk", "United Arab Emirates" to "ae", "Singapore" to "sg", "Malaysia" to "my", "Canada" to "ca", "Australia" to "au")

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        url = Store.playlistUrl

        urlText = txt("", 26, N.TEXT, 500, mono = true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.START }
        status = txt("", 20, N.MUTED)
        val box = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(px(24), px(12), px(24), px(12)); background = rr(N.SURFACE, px(20), 0x995AA9FF.toInt(), px(2))
            addView(urlText, LinearLayout.LayoutParams(-1, -2))
        }
        fun key(label: String?, ico: Int?, click: () -> Unit) = FrameLayout(this).apply {
            focusSkin(px(14), N.SURFACE, 1.06f); setOnClickListener { click() }
            if (ico != null) addView(icon(ico, 28, N.TEXT, "p"), FrameLayout.LayoutParams(px(28), px(28), Gravity.CENTER))
            else addView(txt(label!!, if (label.length > 1) 20 else 26, N.TEXT, 500, tag = "p"), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
        fun row(vararg items: Pair<View, Float>) = LinearLayout(this).apply {
            noClip(); items.forEachIndexed { i, (v, w) -> addView(v, LinearLayout.LayoutParams(0, px(64), w).apply { if (i > 0) marginStart = px(10) }) }
        }
        val keyboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; noClip()
            "abcdefghijklmnopqrstuvwxyz0123456789".chunked(9).forEach { r ->
                addView(row(*r.map { c -> key(c.toString().uppercase(), null) { type(c.toString()) } to 1f }.toTypedArray()), LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) })
            }
            addView(row(*":/.-_?=&%".map { c -> key(c.toString(), null) { type(c.toString()) } to 1f }.toTypedArray()), LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) })
            addView(row(key("https://", null) { url = "https://"; update() } to 2f, key(null, R.drawable.ic_backspace) { if (url.isNotEmpty()) { url = url.dropLast(1); update() } } to 1f,
                key("Clear", null) { url = ""; update() } to 1f), LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) })
        }
        val use = LinearLayout(this).apply {
            gravity = Gravity.CENTER; focusSkin(px(16), N.TEXT, 1.03f)
            addView(txt("Use this link", 24, N.INK, 600)); setOnClickListener { apply(url.trim()) }
        }
        val presetList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; noClip(); setPadding(px(8), px(4), px(8), px(40))
            presets.forEachIndexed { i, (name, code) ->
                val link = "https://iptv-org.github.io/iptv/countries/$code.m3u"
                addView(LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL; focusSkin(px(14), N.SURFACE, 1.03f); setPadding(px(22), 0, px(22), 0)
                    addView(txt(name, 22, N.TEXT, 500, tag = "p"), LinearLayout.LayoutParams(0, -2, 1f))
                    addView(txt(if (i == 0) "default" else code.uppercase(), 18, N.MUTED, mono = true, tag = "s"))
                    setOnClickListener { url = link; update(); apply(link) }
                }, LinearLayout.LayoutParams(-1, px(68)).apply { bottomMargin = px(8) })
            }
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(N.BG); noClip()
            addView(View(context).apply { setBackgroundColor(N.SURFACE2) }, FrameLayout.LayoutParams(px(640), -1, Gravity.END))
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL; focusSkin(px(26), N.SURFACE, 1.04f); setPadding(px(16), 0, px(22), 0)
                addView(icon(R.drawable.ic_left, 20, N.TEXT2, "p"), LinearLayout.LayoutParams(px(20), px(20)))
                addView(txt("Settings", 20, N.TEXT2, 500, tag = "p"), LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(8) })
                setOnClickListener { finish() }
            }, FrameLayout.LayoutParams(-2, px(52)).apply { setMargins(px(96), px(54), 0, 0) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; noClip()
                addView(txt("Free TV playlist", 48, N.TEXT, 600).apply { letterSpacing = -0.01f })
                addView(txt("Any M3U link works. The default is the iptv-org India list.", 22, N.MUTED), LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(8); bottomMargin = px(20) })
                addView(box, LinearLayout.LayoutParams(-1, px(104)))
                addView(keyboard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(6) })
                addView(use, LinearLayout.LayoutParams(-1, px(72)).apply { topMargin = px(20) })
                addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(14) })
            }, FrameLayout.LayoutParams(px(1000), -1).apply { setMargins(px(96), px(150), 0, 0) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; noClip()
                addView(txt("Country playlists", 28, N.TEXT, 600), LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(8); bottomMargin = px(16) })
                addView(ScrollView(context).apply { isVerticalScrollBarEnabled = false; noClip(); addView(presetList) }, LinearLayout.LayoutParams(-1, 0, 1f))
            }, FrameLayout.LayoutParams(px(500), -1, Gravity.END).apply { setMargins(0, px(150), px(70), px(54)) })
        })
        update()
    }

    private fun type(s: String) { if (url.length < 200) { url += s; update() } }

    private fun update() { urlText.text = url.ifEmpty { "https://" }; urlText.setTextColor(if (url.isEmpty()) N.DIM else N.TEXT) }

    private fun apply(link: String) {
        if (busy) return
        if (!link.startsWith("http://") && !link.startsWith("https://")) { say("The link must start with http:// or https://", true); return }
        busy = true; say("Loading playlist…", false)
        Free.switchTo(link) { err ->
            busy = false
            if (err == null) say("Loaded ${Free.channels.size} channels. Free TV now uses this playlist.", false) else say("$err. The previous playlist is still in use.", true)
        }
    }

    private fun say(m: String, bad: Boolean) { status.text = m; status.setTextColor(if (bad) 0xFFF2B544.toInt() else N.MUTED) }
}
