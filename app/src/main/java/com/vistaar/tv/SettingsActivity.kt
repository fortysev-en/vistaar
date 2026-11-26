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
    /** [selected] is set only for the rows of a one-of-many choice: it shows a tick on the chosen row instead of an arrow. */
    private class Row(val label: String, val desc: String = "", val value: String = "", val kind: Int = 0, val destructive: Boolean = false, val selected: Boolean? = null, val act: (() -> Unit)? = null)
    private class Cat(val label: String, val icon: Int, val intro: String, val rows: () -> List<Row>)

    private lateinit var cats: List<Cat>
    private lateinit var navBox: LinearLayout
    private lateinit var title: android.widget.TextView
    private lateinit var intro: android.widget.TextView
    private lateinit var rowsBox: LinearLayout
    private lateinit var scroll: android.widget.ScrollView
    private var current = 0
    private var updateState = ""
    private var signOutState = ""
    private var signingOut = false

    /** Logs this TV out on Jio's side first (so it stops counting against the device limit), then clears the local sign-in. */
    private fun signOut() {
        if (signingOut) return
        signingOut = true; signOutState = "Signing out…"; render()
        Thread {
            Api.logoutRemote()
            runOnUiThread {
                Store.clear(); Store.freeOnly = true; Catalog.reset() // back to the free channels; Jio sign-in stays in Settings
                startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)); finish()
            }
        }.start()
    }

    private fun checkUpdate() {
        updateState = "Checking…"; render()
        Updater.check(this) { r, e ->
            updateState = if (e != null) "Couldn’t check" else if (r == null) "You’re up to date" else "Version ${r.version} available"
            render(); if (r != null) Updater.show(this, r)
        }
    }

    private fun openLink(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
        catch (e: Exception) { updateState = "No browser on this TV: $url"; render() } // TVs often have none; show the address instead
    }

    @Suppress("DEPRECATION")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        if (!Store.hasAccess) { startActivity(Intent(this, SplashActivity::class.java)); finish(); return }
        val checked = { Store.prefs.getLong("okAt", 0).let { if (it == 0L) "never" else SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(it) } }
        val info = packageManager.getPackageInfo(packageName, 0)
        val jio = Store.loggedIn
        val langSummary = { val l = Store.langs; if (l.isEmpty()) "All" else l.mapNotNull { LANGUAGES[it] }.sorted().let { n -> if (n.size <= 2) n.joinToString(", ") else "${n.size} languages" } }
        val playlistName = { Store.playlistUrl.let { u -> if (u == Free.DEFAULT) "India (default)" else u.removePrefix("https://").removePrefix("http://").substringAfterLast('/').ifEmpty { u.take(28) } } }
        cats = listOf(
            Cat("Library", R.drawable.ic_channels, "Keep the channel list and your saved channels tidy.") { listOfNotNull(
                Row("Refresh channels", if (jio) "Re-check which channels your plan includes" else "Reload the free channel list", if (Catalog.scanning) "Checking ${Catalog.scanned}/${Catalog.all.size}" else if (jio) "Checked ${checked()}" else "", 1) { Catalog.start(true); render() },
                Row("Languages", "Only channels in these languages are listed", langSummary(), 1) { startActivity(Intent(this, LanguageActivity::class.java)) },
                Row("Home categories", "Choose which rows Home shows first", Store.genres.let { g -> if (g.isEmpty()) "Default order" else g.take(2).joinToString(", ") { GENRES.getValue(it) } + if (g.size > 2) " +${g.size - 2}" else "" }, 1) { startActivity(Intent(this, LanguageActivity::class.java).putExtra("categories", true)) },
                Row("Free TV playlist", "Choose a country or paste any M3U link", playlistName(), 1) { startActivity(Intent(this, PlaylistActivity::class.java)) },
                Row("Clear favourites", "Remove every saved channel", "${Catalog.favCount()} saved", 1) { Catalog.clearFavs(); render() },
                Row("Reset order", "Put the tabs and categories back in their usual order", "", 1) { Order.resetAll(); render() },
                Row("Clear recently watched", "Resets Home and the remembered audio language of each channel", "", 1) { Catalog.clearRecents(); Store.clearAudioPrefs(); render() }) },
            Cat("Startup", R.drawable.ic_play, "Choose the screen Vistaar opens on.") {
                navTabs().map { (name, _) -> Row(name, kind = 1, selected = name == startTab().first) { Store.startTab = name; render() } }
            },
            Cat("Account", R.drawable.ic_logout, if (jio) "Manage the account this TV is signed in to." else "You are using the free channels only.") { listOfNotNull(
                if (jio) Row("Sign out", "Frees this TV on your Jio account and lets you use a different number", signOutState, 1, destructive = true) { signOut() }
                else Row("Sign in to Jio", "Add your Jio channels, guide and HD", "", 1) { startActivity(Intent(this, LoginActivity::class.java).putExtra("add", true)) },
                Row("Exit app", "Close the app", "", 1) { confirmExit() }) },
            Cat("Remote", R.drawable.ic_phone, "Use your phone as a remote and a keyboard.") { listOf(
                Row("Phone remote", "Navigate and type with your phone", if (RemoteHub.running) "On" else "Off", 1) { startActivity(Intent(this, RemoteActivity::class.java)) }) },
            Cat("About", R.drawable.ic_info, "Share these details if something isn’t working.") { listOfNotNull(
                Row("App version", "", "${info.versionName} (${info.versionCode})", 2),
                Row("Check for updates", "Looks for a newer Vistaar on GitHub", updateState, 1) { checkUpdate() },
                Row("Disclaimer", "What Vistaar is and is not", "", 1) { startActivity(Intent(this, DisclaimerActivity::class.java).putExtra("view", true)) },
                Row("GitHub", "Source code and releases", "fortysev-en/vistaar", 1) { openLink("https://github.com/fortysev-en/vistaar") },
                Row("Documentation", "Guides and how it works", "fortyseven.dev/projects/vistaar", 1) { openLink("https://fortyseven.dev/projects/vistaar") },
                Row("Device", "", "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}", 2),
                if (jio) Row("Channels on your plan", "", "${Catalog.playableChannels().size} of ${Catalog.all.size}", 2) else null,
                if (jio) Row("Plan last checked", "", checked(), 2) else null) }
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
            scroll = android.widget.ScrollView(context).apply { // About has more rows than fit: scroll with the focus, with room under the last one
                isVerticalScrollBarEnabled = false; clipToPadding = false; clipChildren = false; setPadding(px(24), px(8), px(24), px(140))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL; noClip()
                    addView(title); addView(intro, LinearLayout.LayoutParams(px(880), -2).apply { topMargin = px(8); bottomMargin = px(28) }); addView(rowsBox)
                })
            }
            addView(scroll, FrameLayout.LayoutParams(-1, -1).apply { setMargins(px(712), px(142), px(72), 0) })
        })
        render()
    }

    override fun onResume() { super.onResume(); if (Store.hasAccess) { Catalog.onChange = { render() }; Catalog.start(false); render() } }
    override fun onPause() { super.onPause(); Catalog.onChange = null }

    /** Left from a row goes back to the category it belongs to, not to whichever category sits level with the row. */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT && ::rowsBox.isInitialized && rowsBox.hasFocus()) {
            navBox.getChildAt(current)?.requestFocus(); return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun render(focusNav: Boolean = false) {
        if (!::cats.isInitialized) return
        val hadFocus = rowsBox.hasFocus()
        val focusedRow = rowsBox.indexOfChild(rowsBox.focusedChild).coerceAtLeast(0)
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
                if (r.selected != null) addView(icon(R.drawable.ic_check, 26, N.ACCENT, "a").apply { visibility = if (r.selected) View.VISIBLE else View.INVISIBLE },
                    LinearLayout.LayoutParams(px(26), px(26)).apply { marginStart = px(14) })
                else if (r.kind == 1) addView(icon(R.drawable.ic_right, 18, N.MUTED, "m"), LinearLayout.LayoutParams(px(18), px(18)).apply { marginStart = px(14) })
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
        if (hadFocus) rowsBox.getChildAt(focusedRow)?.requestFocus() // stay on the row that was just used
        if (focusNav) { scroll.scrollTo(0, 0); navBox.getChildAt(current)?.requestFocus() } // new category: start at the top
    }
}
