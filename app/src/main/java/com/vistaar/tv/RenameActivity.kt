package com.vistaar.tv

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Gives a favourite your own name, with the on-screen keyboard (or by typing from the phone remote). */
class RenameActivity : Activity(), RemoteText {
    private companion object { const val MAX = 40 }

    private lateinit var field: TextView
    private var name = ""
    private var caps = false

    override fun onRemoteText(s: String) { name = s.take(MAX); update() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        val id = intent.getLongExtra("id", 0)
        val ch = Catalog.favourites().firstOrNull { it.id == id } ?: return finish()
        name = Catalog.label(ch)

        field = txt("", 34, N.TEXT, 600).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.START }
        val box = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(px(28), 0, px(28), 0); background = rr(N.SURFACE, px(20), 0x995AA9FF.toInt(), px(2))
            addView(field, LinearLayout.LayoutParams(-1, -2))
        }
        fun key(label: String?, ico: Int?, click: () -> Unit) = FrameLayout(this).apply {
            focusSkin(px(14), N.SURFACE, 1.06f); setOnClickListener { click() }
            if (ico != null) addView(icon(ico, 28, N.TEXT, "p"), FrameLayout.LayoutParams(px(28), px(28), Gravity.CENTER))
            else addView(txt(label!!, if (label.length > 1) 20 else 26, N.TEXT, 500, tag = "p"), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
        fun row(vararg items: Pair<View, Float>) = LinearLayout(this).apply {
            noClip(); items.forEachIndexed { i, (v, w) -> addView(v, LinearLayout.LayoutParams(0, px(68), w).apply { if (i > 0) marginStart = px(10) }) }
        }
        val keyboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; noClip()
            "abcdefghijklmnopqrstuvwxyz0123456789".chunked(9).forEach { r ->
                addView(row(*r.map { c -> key(c.uppercaseChar().toString(), null) { type(c) } to 1f }.toTypedArray()), LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) })
            }
            addView(row(key("Caps", null) { caps = !caps; update() } to 2f, key("Space", null) { type(' ') } to 3f, key("&", null) { type('&') } to 1f, key("-", null) { type('-') } to 1f,
                key(null, R.drawable.ic_backspace) { name = name.dropLast(1); update() } to 1f, key("Clear", null) { name = ""; update() } to 1f), LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) })
        }
        val save = LinearLayout(this).apply {
            gravity = Gravity.CENTER; focusSkin(px(16), N.TEXT, 1.03f)
            addView(txt("Save name", 24, N.INK, 600)); setOnClickListener { Catalog.rename(ch, name); finish() }
        }
        val original = LinearLayout(this).apply {
            gravity = Gravity.CENTER; focusSkin(px(16), N.FILL, 1.03f); setPadding(px(28), 0, px(28), 0)
            addView(txt("Use original name", 22, N.TEXT, 500, tag = "p")); setOnClickListener { Catalog.rename(ch, ""); finish() }
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(N.BG); noClip()
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL; focusSkin(px(26), N.SURFACE, 1.04f); setPadding(px(16), 0, px(22), 0)
                addView(icon(R.drawable.ic_left, 20, N.TEXT2, "p"), LinearLayout.LayoutParams(px(20), px(20)))
                addView(txt("Favourites", 20, N.TEXT2, 500, tag = "p"), LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(8) })
                setOnClickListener { finish() }
            }, FrameLayout.LayoutParams(-2, px(52)).apply { setMargins(px(96), px(54), 0, 0) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; noClip()
                addView(txt("Rename channel", 48, N.TEXT, 600).apply { letterSpacing = -0.01f })
                addView(txt("Original name: ${ch.name}", 22, N.MUTED), LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(8); bottomMargin = px(24) })
                addView(box, LinearLayout.LayoutParams(-1, px(96)))
                addView(keyboard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) })
                addView(LinearLayout(context).apply {
                    noClip(); addView(save, LinearLayout.LayoutParams(0, px(76), 1f)); addView(original, LinearLayout.LayoutParams(-2, px(76)).apply { marginStart = px(16) })
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(24) })
            }, FrameLayout.LayoutParams(px(1000), -2, Gravity.CENTER_HORIZONTAL).apply { topMargin = px(150) })
        })
        update()
        keyboard.post { ((keyboard.getChildAt(0) as LinearLayout).getChildAt(0)).requestFocus() }
    }

    /** Words start with a capital on their own; Caps keeps every letter upper case (for names such as HD or TV). */
    private fun type(c: Char) {
        if (name.length >= MAX) return
        name += if (caps || name.isEmpty() || name.endsWith(' ')) c.uppercaseChar() else c
        update()
    }

    private fun update() {
        field.text = (name.ifEmpty { "Original name" }) + if (caps) "   ⇧" else ""
        field.setTextColor(if (name.isEmpty()) N.DIM else N.TEXT)
    }
}
