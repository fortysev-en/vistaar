package com.vistaar.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Locale

/** Settings: Library, Account, About. Every row does something real; nothing here is a placeholder. */
class SettingsActivity : Activity() {
    private class Row(val label: String, val desc: String = "", val value: String = "", val kind: Int = 0, val destructive: Boolean = false, val act: (() -> Unit)? = null)
    private class Cat(val label: String, val icon: Int, val intro: String, val rows: () -> List<Row>)

    private lateinit var cats: List<Cat>
    private lateinit var navBox: LinearLayout
    private lateinit var title: android.widget.TextView
    private lateinit var intro: android.widget.TextView
    private lateinit var rowsBox: LinearLayout
    private var current = 0

    @Suppress("DEPRECATION")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        if (!Store.loggedIn) { startActivity(Intent(this, LoginActivity::class.java)); finish(); return }
        val checked = { Store.prefs.getLong("okAt", 0).let { if (it == 0L) "never" else SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(it) } }
        val info = packageManager.getPackageInfo(packageName, 0)
        cats = listOf(
            Cat("Library", R.drawable.ic_channels, "Keep the channel list and your saved channels tidy.") { listOf(
                Row("Refresh channels", "Re-check which channels your plan includes", if (Catalog.scanning) "Checking ${Catalog.scanned}/${Catalog.all.size}" else "Checked ${checked()}", 1) { Catalog.start(true); render() },
                Row("Clear favourites", "Remove every saved channel", "${Catalog.favCount()} saved", 1) { Catalog.clearFavs(); render() },
                Row("Clear recently watched", "Resets the Home hero and Recently watched row", "", 1) { Catalog.clearRecents(); render() }) },
            Cat("Account", R.drawable.ic_logout, "Manage the account this TV is signed in to.") { listOf(
                Row("Sign out", "Use a different number on this TV", "", 1, destructive = true) { Store.clear(); Catalog.reset(); startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)); finish() },
                Row("Exit app", "Close the app", "", 1) { confirmExit() }) },
            Cat("About", R.drawable.ic_info, "Share these details if something isn’t working.") { listOf(
                Row("App version", "", "${info.versionName} (${info.versionCode})", 2),
                Row("Device", "", "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}", 2),
                Row("Channels on your plan", "", "${Catalog.playableChannels().size} of ${Catalog.all.size}", 2),
                Row("Plan last checked", "", checked(), 2)) }
        )
        val back = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; focusSkin(px(26), N.SURFACE, 1.04f); setPadding(px(16), 0, px(22), 0)
            addView(icon(R.drawable.ic_left, 20, N.TEXT2, "p"), LinearLayout.LayoutParams(px(20), px(20)))
            addView(txt("Home", 20, N.TEXT2, 500, tag = "p"), LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(8) })
            setOnClickListener { goTo(HomeActivity::class.java) }
        }
        navBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; noClip() }
        title = txt("", 36, N.TEXT, 600); intro = txt("", 22, N.MUTED).apply { setLineSpacing(0f, 1.25f) }
        rowsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; noClip() }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(N.BG); noClip()
            addView(View(context).apply { setBackgroundColor(N.SURFACE2) }, FrameLayout.LayoutParams(px(640), -1))
            addView(back, FrameLayout.LayoutParams(-2, px(52)).apply { setMargins(px(96), px(54), 0, 0) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; noClip()
                addView(txt("Settings", 48, N.TEXT, 600).apply { letterSpacing = -0.01f }, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = px(24) })
                addView(navBox)
            }, FrameLayout.LayoutParams(px(448), -2).apply { setMargins(px(96), px(150), 0, 0) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; noClip()
                addView(title); addView(intro, LinearLayout.LayoutParams(px(880), -2).apply { topMargin = px(8); bottomMargin = px(28) }); addView(rowsBox)
            }, FrameLayout.LayoutParams(-1, -1).apply { setMargins(px(736), px(150), px(96), px(54)) })
        })
        render()
    }

    override fun onResume() { super.onResume(); if (Store.loggedIn) { Catalog.onChange = { render() }; Catalog.start(false) } }
    override fun onPause() { super.onPause(); Catalog.onChange = null }

    private fun render(focusNav: Boolean = false) {
        if (!::cats.isInitialized) return
        val hadFocus = rowsBox.hasFocus()
        navBox.removeAllViews()
        cats.forEachIndexed { i, c ->
            val on = i == current
            navBox.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; focusSkin(px(14), if (on) N.SEL else Color.TRANSPARENT, 1.02f); setPadding(px(20), 0, px(20), 0)
                addView(icon(c.icon, 26, if (on) N.TEXT else N.MUTED, if (on) "p" else "m"), LinearLayout.LayoutParams(px(26), px(26)))
                addView(txt(c.label, 22, if (on) N.TEXT else N.MUTED, if (on) 600 else 500, tag = if (on) "p" else "s"), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = px(18) })
                addView(icon(R.drawable.ic_right, 18, N.DIM, "m"), LinearLayout.LayoutParams(px(18), px(18)))
                val skin = onFocusChangeListener
                setOnFocusChangeListener { v, f -> skin.onFocusChange(v, f); if (f && current != i) { current = i; post { render(true) } } }
                setOnClickListener { current = i; render() }
            }, LinearLayout.LayoutParams(-1, px(68)).apply { bottomMargin = px(6) })
        }
        val cat = cats[current]; title.text = cat.label; intro.text = cat.intro
        rowsBox.removeAllViews()
        cat.rows().forEach { r ->
            rowsBox.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(px(28), px(14), px(28), px(14)); minimumHeight = px(92)
                if (r.kind == 2) background = rr(N.SURFACE, px(16)) else focusSkin(px(16), N.SURFACE, 1.02f)
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(txt(r.label, 24, if (r.destructive) 0xFFFF6B6B.toInt() else N.TEXT, 500, tag = if (r.destructive) null else "p"))
                    if (r.desc.isNotEmpty()) addView(txt(r.desc, 18, N.MUTED, tag = "s"), LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(4) })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                if (r.value.isNotEmpty()) addView(txt(r.value, if (r.kind == 2) 20 else 22, N.MUTED, mono = r.kind == 2, tag = "s"), LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(24) })
                if (r.kind == 1) addView(icon(R.drawable.ic_right, 18, N.MUTED, "m"), LinearLayout.LayoutParams(px(18), px(18)).apply { marginStart = px(14) })
                r.act?.let { a -> setOnClickListener { a() } }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(8) })
        }
        if (current == cats.lastIndex) { // About: the studio wordmark, white + #E63946
            rowsBox.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(android.widget.ImageView(context).apply { setImageResource(R.drawable.brand_wordmark) }, LinearLayout.LayoutParams(px(240), px(53)))
                addView(txt("https://fortyseven.dev", 22, N.TEXT2), LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(14); marginStart = px(2) })
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(36); marginStart = px(8) })
        }
        if (hadFocus) rowsBox.getChildAt(0)?.requestFocus()
        if (focusNav) navBox.getChildAt(current)?.requestFocus()
    }
}
