package com.vistaar.tv

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Language choice, shown once on first run and again from Settings: pick one or more Jio languages and only those channels are listed.
 * Same look as the sign-in screen (left: heading, right: card).
 */
class LanguageActivity : Activity() {
    private val picked = LinkedHashSet<Int>()
    private val chips = LinkedHashMap<Int, LinearLayout>()
    private lateinit var go: FrameLayout
    private lateinit var goLabel: TextView
    private val first get() = intent.getBooleanExtra("first", false)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        picked.addAll(Store.langs)

        fun chip(id: Int, name: String) = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(px(22), 0, px(18), 0)
            addView(txt(name, 24, N.TEXT, 500, tag = "p"), LinearLayout.LayoutParams(0, -2, 1f))
            addView(icon(R.drawable.ic_check, 24, N.ACCENT, "a"), LinearLayout.LayoutParams(px(24), px(24)))
            setOnClickListener { if (!picked.remove(id)) picked.add(id); paint() }
            chips[id] = this
        }
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; noClip()
            LANGUAGES.entries.chunked(3).forEachIndexed { r, row ->
                addView(LinearLayout(context).apply {
                    noClip()
                    row.forEachIndexed { i, (id, name) -> addView(chip(id, name), LinearLayout.LayoutParams(0, px(76), 1f).apply { if (i > 0) marginStart = px(12) }) }
                    repeat(3 - row.size) { addView(View(context), LinearLayout.LayoutParams(0, px(76), 1f).apply { marginStart = px(12) }) }
                }, LinearLayout.LayoutParams(-1, -2).apply { if (r > 0) topMargin = px(12) })
            }
        }
        goLabel = txt("", 26, N.INK, 600)
        go = FrameLayout(this).apply {
            addView(goLabel, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER)); setOnClickListener { if (picked.isNotEmpty()) save(picked) }
        }
        val all = LinearLayout(this).apply {
            gravity = Gravity.CENTER; focusSkin(px(16), N.FILL, 1.04f); setPadding(px(28), 0, px(28), 0)
            addView(txt("All languages", 22, N.TEXT, 500, tag = "p")); setOnClickListener { save(emptySet()) }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; noClip(); background = rr(0xDB0E1014.toInt(), px(32), N.HAIR, px(1)); setPadding(px(48), px(48), px(48), px(40))
            addView(grid)
            addView(LinearLayout(context).apply {
                noClip(); gravity = Gravity.CENTER_VERTICAL
                addView(go, LinearLayout.LayoutParams(0, px(80), 1f)); addView(all, LinearLayout.LayoutParams(-2, px(80)).apply { marginStart = px(16) })
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(32) })
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(N.BG); noClip()
            addView(ArtView(context, 0).apply { alpha = 0.55f }, FrameLayout.LayoutParams(-1, -1))
            addView(View(context).apply { background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0x8C07080A.toInt(), 0xCC07080A.toInt(), 0xEB07080A.toInt())) }, FrameLayout.LayoutParams(-1, -1))
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(BrandMark(context), LinearLayout.LayoutParams(px(40), px(40)))
                addView(txt("Vistaar", 28, N.TEXT, 600), LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(14) })
            }, FrameLayout.LayoutParams(-2, px(64)).apply { setMargins(px(96), px(54), 0, 0) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(txt(if (first) "YOUR LANGUAGES" else "LANGUAGES", 20, N.MUTED, 500).apply { letterSpacing = 0.08f })
                addView(txt("What do you watch?", 72, N.TEXT, 600).apply { letterSpacing = -0.025f; setLineSpacing(0f, 1.05f) }, LinearLayout.LayoutParams(px(700), -2).apply { topMargin = px(24) })
                addView(txt("Pick one or more. Only channels in these languages are listed, and they are checked first. You can change this any time in Settings.", 26, N.TEXT2).apply { setLineSpacing(0f, 1.3f) },
                    LinearLayout.LayoutParams(px(640), -2).apply { topMargin = px(24) })
            }, FrameLayout.LayoutParams(-2, -2).apply { setMargins(px(96), px(300), 0, 0) })
            addView(card, FrameLayout.LayoutParams(px(900), -2).apply { setMargins(px(960), px(110), 0, 0) })
        })
        paint()
        chips.values.first().requestFocus()
    }

    private fun paint() {
        chips.forEach { (id, v) ->
            val on = id in picked
            v.focusSkin(px(16), if (on) N.SEL else N.SURFACE, 1.04f)
            v.getChildAt(1).visibility = if (on) View.VISIBLE else View.INVISIBLE
            if (v.isFocused) v.background = rr(N.TEXT, px(16))
        }
        val ready = picked.isNotEmpty()
        goLabel.text = if (ready) "Continue  ·  ${picked.size} selected" else "Pick at least one"
        goLabel.setTextColor(if (ready) N.INK else N.DIM)
        go.focusSkin(px(16), if (ready) N.TEXT else 0x0FFFFFFF, 1.03f)
        go.isFocusable = ready
        if (go.isFocused) go.background = rr(N.TEXT, px(16))
    }

    private fun save(l: Set<Int>) { Store.setLangs(l); finish() }

    // First run: leaving without choosing means every language, so the picker never reappears on its own.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (first && !Store.langsDone) Store.setLangs(emptySet()); super.onBackPressed() }
}
