package com.vistaar.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Home: a hero for what you watched last (or your first favourite), then rows of channels. */
class HomeActivity : Activity() {
    private class Section(val box: LinearLayout, val title: TextView, val adapter: StripAdapter)

    private lateinit var header: Header
    private lateinit var scroll: ScrollView
    private lateinit var backdrop: ArtView
    private lateinit var hero: LinearLayout
    private lateinit var skeleton: LinearLayout
    private lateinit var heroLogo: LogoBox
    private lateinit var heroName: TextView
    private lateinit var heroNum: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroSub: TextView
    private lateinit var heroUntil: TextView
    private lateinit var heroDesc: TextView
    private lateinit var heroStart: TextView
    private lateinit var heroEnd: TextView
    private lateinit var heroBar: Bar
    private lateinit var watchBtn: View
    private lateinit var favIcon: ImageView
    private lateinit var recent: Section
    private lateinit var favs: Section
    private val langs = ArrayList<Section>()
    private var heroCh: Channel? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        if (!Store.loggedIn) return goLogin()
        header = Header(this, HomeActivity::class.java)
        backdrop = ArtView(this)

        // ---- hero
        heroLogo = LogoBox(this, 56, 14)
        heroName = txt("", 22, N.TEXT, 500); heroNum = txt("", 22, N.MUTED, mono = true)
        heroTitle = txt("", 80, N.TEXT, 600).apply { letterSpacing = -0.025f; maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setLineSpacing(0f, 1.05f) }
        heroSub = txt("", 28, N.TEXT2, 500)
        heroUntil = txt("", 22, N.TEXT, 600)
        heroDesc = txt("", 24, N.TEXT2).apply { maxLines = 3; ellipsize = TextUtils.TruncateAt.END; setLineSpacing(0f, 1.3f) }
        heroStart = txt("", 18, N.MUTED, mono = true); heroEnd = txt("", 18, N.MUTED, mono = true)
        heroBar = Bar(this, px(3))
        watchBtn = LinearLayout(this).apply {
            gravity = Gravity.CENTER; focusSkin(px(14), N.FILL, 1.06f); setPadding(px(36), 0, px(36), 0)
            addView(icon(R.drawable.ic_play, 24, N.TEXT, "p"), LinearLayout.LayoutParams(px(24), px(24)))
            addView(txt("Watch Live", 24, N.TEXT, 600, tag = "p"), LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(14) })
            setOnClickListener { heroCh?.let { play(it) } }
        }
        val guideBtn = LinearLayout(this).apply {
            gravity = Gravity.CENTER; focusSkin(px(14), N.FILL, 1.06f); setPadding(px(32), 0, px(32), 0)
            addView(icon(R.drawable.ic_guide, 24, N.TEXT, "p"), LinearLayout.LayoutParams(px(24), px(24)))
            addView(txt("Guide", 24, N.TEXT, 500, tag = "p"), LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(14) })
            setOnClickListener { goTo(GuideActivity::class.java) }
        }
        favIcon = icon(R.drawable.ic_star_outline, 28, N.TEXT, "p")
        val favBtn = FrameLayout(this).apply {
            focusSkin(px(34), N.FILL, 1.06f); contentDescription = "Toggle favourite"
            addView(favIcon, FrameLayout.LayoutParams(px(28), px(28), Gravity.CENTER))
            setOnClickListener { heroCh?.let { Catalog.toggleFav(it.id); bindFav(it); refreshRows(true) } }
        }
        hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; noClip()
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(heroLogo, LinearLayout.LayoutParams(px(56), px(56)))
                addView(heroName, LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(16) })
                addView(heroNum, LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(16) })
            })
            addView(heroTitle, LinearLayout.LayoutParams(px(900), -2).apply { topMargin = px(22) })
            addView(heroSub, LinearLayout.LayoutParams(px(900), -2).apply { topMargin = px(8) })
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(liveBadge()); addView(heroUntil, LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(16) })
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(22) })
            addView(heroDesc, LinearLayout.LayoutParams(px(760), -2).apply { topMargin = px(22) })
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(heroStart); addView(heroBar, LinearLayout.LayoutParams(px(400), px(6)).apply { setMargins(px(20), 0, px(20), 0) }); addView(heroEnd)
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(22) })
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL; noClip()
                addView(watchBtn, LinearLayout.LayoutParams(-2, px(68)))
                addView(guideBtn, LinearLayout.LayoutParams(-2, px(68)).apply { marginStart = px(32) })
                addView(favBtn, LinearLayout.LayoutParams(px(68), px(68)).apply { marginStart = px(20) })
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(32) })
        }

        // loading skeleton (shown until the first playable channel is known)
        skeleton = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(View(context).apply { background = rr(N.SURFACE, px(14)) }, LinearLayout.LayoutParams(px(420), px(56)))
            addView(View(context).apply { background = rr(N.SURFACE, px(14)) }, LinearLayout.LayoutParams(px(820), px(84)).apply { topMargin = px(28) })
            addView(View(context).apply { background = rr(N.SURFACE, px(10)) }, LinearLayout.LayoutParams(px(560), px(34)).apply { topMargin = px(24) })
            addView(LinearLayout(context).apply {
                (0 until 5).forEach { addView(View(context).apply { background = rr(N.SURFACE, px(16)) }, LinearLayout.LayoutParams(px(400), px(225)).apply { marginEnd = px(32) }) }
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(120) })
        }

        // ---- rows
        fun section(tiles: Boolean): Section {
            val adapter = StripAdapter(this, tiles, { play(it) }, { Catalog.toggleFav(it.id); refreshRows(true) })
            val title = txt("", 30, N.TEXT, 600)
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; noClip()
                addView(title, LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(96) })
                addView(strip(adapter, if (tiles) 250 else 262), LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) })
            }
            return Section(box, title, adapter)
        }
        recent = section(false); favs = section(true); repeat(3) { langs.add(section(true)) }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; noClip(); setPadding(0, px(218), 0, px(80))
            addView(FrameLayout(context).apply { noClip(); addView(hero); addView(skeleton); setPadding(px(96), 0, 0, 0) },
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(96) })
            (listOf(recent, favs) + langs).forEach { addView(it.box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(28) }) }
        }
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false; noClip(); isFillViewport = true; addView(column)
            viewTreeObserver.addOnScrollChangedListener {
                val a = (1f - scrollY / px(160).toFloat()).coerceIn(0f, 1f)
                header.view.alpha = a; header.view.visibility = if (a <= 0f) View.INVISIBLE else View.VISIBLE
            }
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(N.BG); noClip()
            addView(backdrop, FrameLayout.LayoutParams(-1, -1))
            addView(View(context).apply { background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xF007080A.toInt(), 0xB307080A.toInt(), 0x0007080A)) },
                FrameLayout.LayoutParams(-1, -1))
            addView(View(context).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x8C07080A.toInt(), 0x0007080A, 0x0007080A, N.BG)) },
                FrameLayout.LayoutParams(-1, -1))
            addView(scroll, FrameLayout.LayoutParams(-1, -1))
            addView(header.view, header.layoutParams())
        })
    }

    override fun onResume() {
        super.onResume()
        if (!Store.loggedIn) return
        Catalog.onChange = { refresh() }
        Catalog.start(false)
        refresh()
        watchBtn.requestFocus()
    }

    override fun onPause() { super.onPause(); Catalog.onChange = null }

    override fun onNewIntent(i: Intent?) { super.onNewIntent(i); scroll.scrollTo(0, 0); watchBtn.requestFocus() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (scroll.scrollY > 0) { scroll.smoothScrollTo(0, 0); watchBtn.requestFocus() } else confirmExit() }

    private fun goLogin() { startActivity(Intent(this, LoginActivity::class.java)); finish() }

    private fun play(ch: Channel) { startActivity(Intent(this, PlayerActivity::class.java).putExtra("id", ch.id)) }

    private fun refresh() {
        header.update()
        val playable = Catalog.playableChannels()
        val loading = playable.isEmpty()
        skeleton.visibility = if (loading) View.VISIBLE else View.GONE
        hero.visibility = if (loading) View.INVISIBLE else View.VISIBLE
        if (loading) { (listOf(recent, favs) + langs).forEach { it.box.visibility = View.GONE }; return }
        val target = Catalog.recents().firstOrNull() ?: Catalog.favourites().firstOrNull() ?: playable.first()
        if (heroCh?.id != target.id) { heroCh = target; bindHero(target) }
        refreshRows()
    }

    private fun refreshRows(force: Boolean = false) {
        forceRows = force
        val playable = Catalog.playableChannels()
        val recents = Catalog.recents()
        show(recent, if (recents.isNotEmpty()) "Recently watched" else "Channels for you", recents.ifEmpty { playable.take(12) })
        show(favs, "Your favourites", Catalog.favourites())
        val byLang = playable.groupBy { it.lang }
        val order = byLang.keys.sortedWith(compareBy({ if (it == 1) 0 else if (it == 6) 1 else 2 }, { -byLang.getValue(it).size }))
        langs.forEachIndexed { i, s ->
            val l = order.getOrNull(i)
            if (l == null) show(s, "", emptyList()) else show(s, "${langName(l)}  ·  ${byLang.getValue(l).size} channels", byLang.getValue(l).take(14))
        }
        heroCh?.let { bindFav(it) }
    }

    private var forceRows = false

    private fun show(s: Section, title: String, list: List<Channel>) {
        s.title.text = title; s.adapter.submit(list); if (forceRows) s.adapter.notifyDataSetChanged() // stars changed
        s.box.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun bindFav(ch: Channel) {
        val fav = Catalog.isFav(ch.id)
        favIcon.setImageResource(if (fav) R.drawable.ic_star else R.drawable.ic_star_outline)
        favIcon.tag = if (fav) "a" else "p"; favIcon.setColorFilter(if (fav) N.ACCENT else N.TEXT)
    }

    private fun bindHero(ch: Channel) {
        backdrop.setStyle(ch.id.toInt()); heroLogo.bind(ch)
        heroName.text = ch.name; heroNum.text = Catalog.numberOf(ch.id).toString()
        heroTitle.text = ch.name; heroSub.text = langName(ch.lang) + if (ch.hd) " · HD" else ""
        heroUntil.text = "Live now"; heroDesc.text = ""; heroStart.text = ""; heroEnd.text = ""; heroBar.progress = 0f
        bindFav(ch)
        Epg.load(ch.id) { list ->
            if (heroCh?.id != ch.id) return@load
            val p = Epg.now(list) ?: return@load
            heroTitle.text = p.title
            heroSub.text = Epg.next(list)?.let { "Up next: ${it.title} at ${fmt(it.start)}" } ?: (langName(ch.lang) + if (ch.hd) " · HD" else "")
            val left = ((p.end - System.currentTimeMillis()) / 60_000).toInt().coerceAtLeast(0)
            heroUntil.text = "Until ${fmt(p.end)}  ·  $left min left"
            heroDesc.text = p.desc; heroStart.text = fmt(p.start); heroEnd.text = fmt(p.end)
            heroBar.progress = (System.currentTimeMillis() - p.start).toFloat() / (p.end - p.start).coerceAtLeast(1)
        }
    }
}
