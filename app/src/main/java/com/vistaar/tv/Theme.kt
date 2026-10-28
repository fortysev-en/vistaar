package com.vistaar.tv

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.LinearGradient
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Vistaar design system: near-black ground, white-fill focus, one blue accent.
 * All sizes are written in the design's 1920x1080 pixels and scaled to the real screen by [px].
 */
object N {
    val BG = 0xFF07080A.toInt(); val SURFACE = 0xFF16191F.toInt(); val SURFACE2 = 0xFF0E1014.toInt()
    val TEXT = 0xFFF5F6F8.toInt(); val TEXT2 = 0xFFD7DBE1.toInt(); val MUTED = 0xFFA3A9B4.toInt(); val DIM = 0xFF7E8592.toInt()
    val INK = 0xFF07080A.toInt(); val INK2 = 0xFF3B4250.toInt()
    val ACCENT = 0xFF5AA9FF.toInt(); val ACCENT_DK = 0xFF1A64C8.toInt(); val LIVE = 0xFFE5262F.toInt()
    val GLASS = 0x9E16191F.toInt(); val HAIR = 0x14FFFFFF; val SEL = 0x29FFFFFF; val FILL = 0x24FFFFFF
}

internal fun Context.px(v: Int): Int = (v * resources.displayMetrics.widthPixels / 1920f).toInt()

internal fun Context.txt(s: CharSequence, size: Int, color: Int = N.TEXT, weight: Int = 400, mono: Boolean = false, tag: String? = null) =
    TextView(this).apply {
        text = s; setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size).toFloat()); setTextColor(color); includeFontPadding = false
        typeface = when {
            mono -> Typeface.MONOSPACE
            weight >= 700 -> Typeface.create("sans-serif-medium", Typeface.BOLD)
            weight >= 500 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            else -> Typeface.create("sans-serif", Typeface.NORMAL)
        }
        if (tag != null) this.tag = tag
    }

internal fun Context.icon(res: Int, size: Int, color: Int = N.TEXT, tag: String? = null) =
    ImageView(this).apply { setImageResource(res); setColorFilter(color); if (tag != null) this.tag = tag; minimumWidth = px(size); minimumHeight = px(size) }

internal fun rr(fill: Int, radius: Int, stroke: Int = 0, strokeW: Int = 0) = GradientDrawable().apply {
    setColor(fill); cornerRadius = radius.toFloat(); if (strokeW > 0) setStroke(strokeW, stroke)
}

internal fun View.clipRound(r: Int) {
    outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, r.toFloat()) } }
    clipToOutline = true
}

internal fun ViewGroup.noClip() { clipChildren = false; clipToPadding = false }

internal val clockFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
internal fun fmt(t: Long): String = clockFmt.format(t)

internal fun langName(id: Int) = LANGUAGES[id] ?: "Other"

// ------------------------------------------------------------------ focus

/** Idle fill, white fill with dark text when focused, slight zoom: the focus state for buttons and rows. */
internal fun View.focusSkin(radius: Int, idle: Int, scale: Float = 1.03f) {
    isFocusable = true; isClickable = true
    background = rr(idle, radius)
    setOnFocusChangeListener { v, f ->
        v.background = rr(if (f) N.TEXT else idle, radius)
        recolor(v, f)
        v.animate().scaleX(if (f) scale else 1f).scaleY(if (f) scale else 1f).translationZ(if (f) 24f else 0f).setStartDelay(0).setDuration(120).start()
    }
}

/** Texts/icons tagged p (primary), s (secondary), m (muted icon), a (accent) swap to dark ink on a white fill. */
private fun recolor(v: View, f: Boolean) {
    when (v) {
        is Bar -> v.focused = f
        is TextView -> when (v.tag) {
            "p" -> v.setTextColor(if (f) N.INK else N.TEXT)
            "s" -> v.setTextColor(if (f) N.INK2 else N.MUTED)
            "a" -> v.setTextColor(if (f) N.ACCENT_DK else N.ACCENT)
        }
        is ImageView -> when (v.tag) {
            "p" -> v.setColorFilter(if (f) N.INK else N.TEXT)
            "m" -> v.setColorFilter(if (f) N.INK2 else N.MUTED)
            "a" -> v.setColorFilter(if (f) N.ACCENT_DK else N.ACCENT)
        }
    }
    if (v is ViewGroup) for (i in 0 until v.childCount) recolor(v.getChildAt(i), f)
}

/** Artwork cards: no fill change; a dark gap and a white ring around the card, plus zoom. */
internal fun View.ringOnFocus(ctx: Context, radius: Int, scale: Float = 1.06f) {
    isFocusable = true; isClickable = true
    setOnFocusChangeListener { v, f ->
        v.background = if (f) rr(N.BG, radius + ctx.px(8), N.TEXT, ctx.px(4)) else null
        v.animate().scaleX(if (f) scale else 1f).scaleY(if (f) scale else 1f).translationZ(if (f) 24f else 0f).setStartDelay(0).setDuration(120).start()
    }
}

// ------------------------------------------------------------------ small custom views

/** Progress bar with a track; turns dark-on-white when its row is focused. */
class Bar(c: Context, private val radius: Int) : View(c) {
    var progress = 0f; set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var focused = false; set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        val r = RectF(0f, 0f, width.toFloat(), height.toFloat())
        p.color = if (focused) 0x24070809 else 0x38FFFFFF; canvas.drawRoundRect(r, radius.toFloat(), radius.toFloat(), p)
        p.color = if (focused) N.ACCENT_DK else N.ACCENT
        canvas.drawRoundRect(RectF(0f, 0f, width * progress, height.toFloat()), radius.toFloat(), radius.toFloat(), p)
    }
}

/**
 * The Vistaar "Blade V" monogram on a 64-unit grid: a white stroke carries the base and a blue blade pulls away from it,
 * as if one V had been cut once along its diagonal. Both parts are filled and stroked 2 units with round joins.
 */
class BrandMark(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL_AND_STROKE; strokeJoin = Paint.Join.ROUND }
    private val stroke = Path().apply { moveTo(6f, 11f); lineTo(19f, 11f); lineTo(38f, 53f); lineTo(26f, 53f); close() }
    private val blade = Path().apply { moveTo(45f, 11f); lineTo(58f, 11f); lineTo(40.6f, 47.6f); lineTo(34.4f, 33.9f); close() }
    override fun onDraw(canvas: Canvas) {
        val s = width / 64f
        canvas.save(); canvas.scale(s, s); p.strokeWidth = 2f
        p.color = N.TEXT; canvas.drawPath(stroke, p)
        p.color = N.ACCENT; canvas.drawPath(blade, p)
        canvas.restore()
    }
}

/** Generated "artwork" (no stills are available): one of eight gradient scenes, picked from the channel id. */
class ArtView(c: Context, style: Int = 0) : View(c) {
    private class Orb(val x: Float, val y: Float, val r: Float, val color: Int)
    private class Pal(val colors: IntArray, val pos: FloatArray?, val orbs: List<Orb>)
    private val pals = listOf(
        Pal(intArrayOf(0xFF081525.toInt(), 0xFF0E2A3F.toInt(), 0xFF174A33.toInt(), 0xFF0D2F1F.toInt()), floatArrayOf(0f, .48f, .62f, 1f),
            listOf(Orb(.18f, .06f, .4f, 0xB3FFF8E1.toInt()), Orb(.84f, .04f, .4f, 0x8CFFF8E1.toInt()), Orb(.5f, 1.15f, .6f, 0xFF2F7A46.toInt()))),
        Pal(intArrayOf(0xFF060C1C.toInt(), 0xFF0F1D3D.toInt(), 0xFF2A2440.toInt(), 0xFF3B2A2A.toInt()), floatArrayOf(0f, .55f, .8f, 1f),
            listOf(Orb(.7f, 1f, .5f, 0x8CE88C3C.toInt()), Orb(.25f, .3f, .3f, 0x5978AAFF))),
        Pal(intArrayOf(0xFF1B0D18.toInt(), 0xFF5A2330.toInt(), 0xFFC0603A.toInt(), 0xFF2A140F.toInt(), 0xFF120806.toInt()), floatArrayOf(0f, .38f, .56f, .57f, 1f),
            listOf(Orb(.62f, .58f, .35f, 0xE6FFCE8C.toInt()))),
        Pal(intArrayOf(0xFF0A5B74.toInt(), 0xFF063A52.toInt(), 0xFF031A2A.toInt()), floatArrayOf(0f, .45f, 1f),
            listOf(Orb(.3f, 0f, .6f, 0x738CE6FF), Orb(.75f, .85f, .5f, 0x59FF786E))),
        Pal(intArrayOf(0xFF2B6FD6.toInt(), 0xFF3F8BE8.toInt(), 0xFF5CC06A.toInt(), 0xFF3FA253.toInt()), floatArrayOf(0f, .6f, .61f, 1f),
            listOf(Orb(.25f, .65f, .22f, 0xFFFFD25A.toInt()), Orb(.78f, .4f, .3f, 0xFF4FD1C5.toInt()))),
        Pal(intArrayOf(0xFF0C0F22.toInt(), 0xFF161A36.toInt(), 0xFF0A0B16.toInt()), floatArrayOf(0f, .6f, 1f),
            listOf(Orb(.68f, .42f, .14f, 0xE6FFC478.toInt()), Orb(.2f, .8f, .6f, 0x803C468C.toInt()))),
        Pal(intArrayOf(0xFF120720.toInt(), 0xFF0A0612.toInt()), null,
            listOf(Orb(.3f, 0f, .3f, 0x99BE5AFF.toInt()), Orb(.72f, 0f, .3f, 0x80FF4696.toInt()), Orb(.5f, 1f, .6f, 0x59FFB45A))),
        Pal(intArrayOf(0xFF2A0D14.toInt(), 0xFF14060A.toInt()), null,
            listOf(Orb(.7f, .5f, .4f, 0xB3FF9628.toInt()), Orb(.25f, .7f, .3f, 0x80C8285A.toInt())))
    )
    private var idx = ((style % 8) + 8) % 8
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var base: Shader? = null
    private var orbShaders: List<Triple<Shader, Float, Float>> = emptyList()
    private var orbR: List<Float> = emptyList()

    fun setStyle(s: Int) { val n = ((s % 8) + 8) % 8; if (n != idx) { idx = n; build(); invalidate() } }
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { build() }

    private fun build() {
        if (width == 0) return
        val p = pals[idx]; val w = width.toFloat(); val h = height.toFloat()
        base = LinearGradient(0f, 0f, 0f, h, p.colors, p.pos, Shader.TileMode.CLAMP)
        orbR = p.orbs.map { it.r * w }
        orbShaders = p.orbs.map { o -> Triple(RadialGradient(o.x * w, o.y * h, o.r * w, o.color, o.color and 0x00FFFFFF, Shader.TileMode.CLAMP), o.x * w, o.y * h) }
    }

    override fun onDraw(canvas: Canvas) {
        paint.shader = base; canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        orbShaders.forEachIndexed { i, (s, x, y) -> paint.shader = s; canvas.drawCircle(x, y, orbR[i], paint) }
    }
}

/** Rounded channel logo (real logo when available, initials otherwise). */
class LogoBox(private val ctx: Context, size: Int, radius: Int, fill: Int = 0xFF1B1F27.toInt()) : FrameLayout(ctx) {
    private val img = ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(ctx.px(size / 8), ctx.px(size / 10), ctx.px(size / 8), ctx.px(size / 10)) }
    private val ini = ctx.txt("", size / 3, N.TEXT, 700).apply { gravity = Gravity.CENTER }
    init {
        background = rr(fill, ctx.px(radius))
        addView(ini, LayoutParams(-1, -1)); addView(img, LayoutParams(-1, -1))
    }
    fun bind(ch: Channel) {
        ini.text = ch.name.split(' ').filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
        ini.visibility = View.VISIBLE
        Images.load(img, ch.logo) { shown -> if (shown) ini.visibility = View.GONE }
    }
}

internal fun Context.liveBadge() = txt("LIVE", 18, Color.WHITE, 700).apply {
    letterSpacing = 0.06f; setPadding(px(10), px(3), px(10), px(3)); background = rr(N.LIVE, px(8))
}

// ------------------------------------------------------------------ cards

/** A channel shown as a ring-focused card: artwork, LIVE badge, logo, current programme, progress. */
class LiveCard(private val ctx: Context, private val w: Int, private val h: Int) {
    val view = FrameLayout(ctx)
    private val art = ArtView(ctx)
    private val logo = LogoBox(ctx, 48, 12)
    private val title = ctx.txt("", 22, N.TEXT, 600).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
    private val sub = ctx.txt("", 18, N.MUTED).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
    private val bar = Bar(ctx, ctx.px(2))
    private val star = ctx.icon(R.drawable.ic_star, 28, N.ACCENT)
    private var bound = -1L

    init {
        val pad = ctx.px(8)
        view.setPadding(pad, pad, pad, pad); view.noClip(); view.ringOnFocus(ctx, ctx.px(16))
        val card = FrameLayout(ctx).apply { clipRound(ctx.px(16)) }
        card.addView(art, FrameLayout.LayoutParams(-1, -1))
        card.addView(View(ctx).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x0007080A, 0xE007080A.toInt())) },
            FrameLayout.LayoutParams(-1, -1))
        card.addView(ctx.liveBadge(), FrameLayout.LayoutParams(-2, -2).apply { setMargins(ctx.px(16), ctx.px(16), 0, 0) })
        card.addView(star, FrameLayout.LayoutParams(ctx.px(28), ctx.px(28), Gravity.TOP or Gravity.END).apply { setMargins(0, ctx.px(16), ctx.px(16), 0) })
        card.addView(LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(logo, LinearLayout.LayoutParams(ctx.px(48), ctx.px(48)))
            addView(LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; addView(title); addView(sub) },
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ctx.px(14) })
        }, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(ctx.px(16), 0, ctx.px(16), ctx.px(20)) })
        card.addView(bar, FrameLayout.LayoutParams(-1, ctx.px(4), Gravity.BOTTOM))
        view.addView(card, FrameLayout.LayoutParams(w, h))
        view.layoutParams = RecyclerView.LayoutParams(w + 2 * pad, h + 2 * pad)
    }

    fun bind(ch: Channel, editing: Boolean = false) {
        bound = ch.id
        art.setStyle(ch.id.toInt()); logo.bind(ch)
        title.text = ch.name; sub.text = langName(ch.lang); bar.progress = 0f
        // In edit mode the star becomes a white "remove" button, as in the design's Favourites screen.
        star.setImageResource(if (editing) R.drawable.ic_close else R.drawable.ic_star)
        star.setColorFilter(if (editing) N.INK else N.ACCENT)
        star.background = if (editing) rr(N.TEXT, ctx.px(24)) else null
        star.setPadding(if (editing) ctx.px(6) else 0, if (editing) ctx.px(6) else 0, if (editing) ctx.px(6) else 0, if (editing) ctx.px(6) else 0)
        star.visibility = if (editing || Catalog.isFav(ch.id)) View.VISIBLE else View.GONE
        Epg.load(ch.id) { list ->
            if (bound != ch.id) return@load
            Epg.now(list)?.let { p ->
                title.text = p.title; sub.text = "${ch.name} · until ${fmt(p.end)}"
                bar.progress = (System.currentTimeMillis() - p.start).toFloat() / (p.end - p.start).coerceAtLeast(1)
            }
        }
    }
}

/** Logo tile with the programme and channel underneath (favourites, language rows). */
class LogoTile(private val ctx: Context, private val w: Int = 260, private val h: Int = 146) {
    val view = LinearLayout(ctx)
    private val logo = LogoBox(ctx, 80, 16, 0xFF1B1F27.toInt())
    private val title = ctx.txt("", 20, N.TEXT, 600).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
    private val sub = ctx.txt("", 18, N.MUTED).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
    private val star = ctx.icon(R.drawable.ic_star, 24, N.ACCENT)
    private var bound = -1L

    init {
        val pad = ctx.px(8)
        view.orientation = LinearLayout.VERTICAL; view.setPadding(pad, pad, pad, pad); view.noClip(); view.ringOnFocus(ctx, ctx.px(16))
        view.addView(FrameLayout(ctx).apply {
            addView(logo, FrameLayout.LayoutParams(-1, -1))
            addView(star, FrameLayout.LayoutParams(ctx.px(24), ctx.px(24), Gravity.TOP or Gravity.END).apply { setMargins(0, ctx.px(10), ctx.px(10), 0) })
        }, LinearLayout.LayoutParams(w.let { ctx.px(it) }, ctx.px(h)))
        view.addView(title, LinearLayout.LayoutParams(ctx.px(w), -2).apply { topMargin = ctx.px(12) })
        view.addView(sub, LinearLayout.LayoutParams(ctx.px(w), -2).apply { topMargin = ctx.px(2) })
        view.layoutParams = RecyclerView.LayoutParams(ctx.px(w) + 2 * pad, -2)
    }

    fun bind(ch: Channel) {
        bound = ch.id; logo.bind(ch); title.text = ch.name; sub.text = langName(ch.lang) + if (ch.hd) " · HD" else ""
        star.visibility = if (Catalog.isFav(ch.id)) View.VISIBLE else View.GONE
        Epg.load(ch.id) { list -> if (bound == ch.id) Epg.now(list)?.let { p -> title.text = p.title; sub.text = ch.name } }
    }
}

/** Horizontal strip adapter for either card kind. Keeps Left/Right inside the row even while a key is held. */
class StripAdapter(
    private val ctx: Context, private val tiles: Boolean,
    private val onClick: (Channel) -> Unit, private val onLong: (Channel) -> Unit
) : RecyclerView.Adapter<StripAdapter.VH>() {
    class VH(val v: View, val bind: (Channel) -> Unit) : RecyclerView.ViewHolder(v)
    var items: List<Channel> = emptyList(); private set

    fun submit(l: List<Channel>) { if (l != items) { items = l; notifyDataSetChanged() } }
    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, t: Int): VH =
        if (tiles) LogoTile(ctx).let { VH(it.view) { ch -> it.bind(ch) } }
        else LiveCard(ctx, ctx.px(400), ctx.px(225)).let { VH(it.view) { ch -> it.bind(ch) } }

    override fun onBindViewHolder(h: VH, p: Int) {
        val ch = items[p]; h.bind(ch)
        h.v.setOnClickListener { onClick(ch) }
        h.v.setOnLongClickListener { onLong(ch); true }
        h.v.setOnKeyListener { view, code, ev ->
            if (ev.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            val strip = view.parent as? RecyclerView ?: return@setOnKeyListener false
            val to = when (code) {
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> h.bindingAdapterPosition + 1
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> h.bindingAdapterPosition - 1
                else -> return@setOnKeyListener false
            }
            if (to in items.indices) {
                val next = strip.findViewHolderForAdapterPosition(to)
                if (next != null) next.itemView.requestFocus()
                else { strip.scrollToPosition(to); strip.post { strip.findViewHolderForAdapterPosition(to)?.itemView?.requestFocus() } }
            }
            true // also swallows the key at both ends of the row
        }
    }
}

/** Vertical grid of [LiveCard]s (favourites, search results). */
class GridAdapter(
    private val ctx: Context, private val w: Int, private val h: Int,
    private val onClick: (Channel) -> Unit, private val onLong: (Channel) -> Unit
) : RecyclerView.Adapter<StripAdapter.VH>() {
    var items: List<Channel> = emptyList(); private set
    var editing = false
    fun submit(l: List<Channel>) { items = l; notifyDataSetChanged() }
    override fun getItemCount() = items.size
    override fun onCreateViewHolder(parent: ViewGroup, t: Int): StripAdapter.VH =
        LiveCard(ctx, ctx.px(w), ctx.px(h)).let { c -> StripAdapter.VH(c.view) { ch -> c.bind(ch, editing) } }
    override fun onBindViewHolder(h: StripAdapter.VH, p: Int) {
        val ch = items[p]; h.bind(ch)
        h.v.setOnClickListener { onClick(ch) }; h.v.setOnLongClickListener { onLong(ch); true }
    }
}

internal fun Context.strip(adapter: RecyclerView.Adapter<*>, height: Int, startPad: Int = 96): RecyclerView =
    RecyclerView(this).apply {
        layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
        this.adapter = adapter; itemAnimator = null; noClip(); setPadding(px(startPad) - px(8), px(4), px(96), px(4))
        layoutParams = ViewGroup.MarginLayoutParams(-1, px(height))
    }

// ------------------------------------------------------------------ header + navigation

internal fun Activity.goTo(cls: Class<*>) {
    if (cls == this::class.java) return
    val i = Intent(this, cls)
    if (cls == HomeActivity::class.java) i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    startActivity(i); overridePendingTransition(0, 0)
    if (this !is HomeActivity) finish()
}

/** Logo, pill navigation, and on the right: scan status, clock, refresh and settings. Identical on every main screen. */
class Header(private val a: Activity, private val current: Class<*>) {
    val view = FrameLayout(a)
    private val status = a.txt("", 18, N.MUTED, mono = true)
    private val refresh: View
    val firstFocus: View

    init {
        view.noClip()
        view.addView(LinearLayout(a).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(BrandMark(a), LinearLayout.LayoutParams(a.px(40), a.px(40)))
            addView(a.txt("Vistaar", 28, N.TEXT, 600).apply { letterSpacing = -0.01f }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = a.px(14) })
        }, FrameLayout.LayoutParams(a.px(320), -1, Gravity.START or Gravity.CENTER_VERTICAL))

        fun item(label: String?, iconRes: Int, target: Class<*>): View {
            val on = target == current
            return LinearLayout(a).apply {
                gravity = Gravity.CENTER; focusSkin(a.px(26), if (on) N.SEL else Color.TRANSPARENT, 1.04f)
                if (label != null) { setPadding(a.px(24), 0, a.px(24), 0); addView(a.txt(label, 22, if (on) N.TEXT else N.MUTED, 500, tag = if (on) "p" else "s")) }
                else addView(a.icon(iconRes, 24, if (on) N.TEXT else N.MUTED, if (on) "p" else "m"), LinearLayout.LayoutParams(a.px(24), a.px(24)))
                setOnClickListener { a.goTo(target) }
            }
        }
        val search = item(null, R.drawable.ic_search, SearchActivity::class.java)
        val nav = LinearLayout(a).apply {
            gravity = Gravity.CENTER_VERTICAL; noClip(); setPadding(a.px(6), a.px(6), a.px(6), a.px(6))
            background = rr(N.GLASS, a.px(40), N.HAIR + 0x04000000, a.px(1))
            addView(search, LinearLayout.LayoutParams(a.px(52), a.px(52)))
            listOf("Home" to HomeActivity::class.java, "Live TV" to LiveTvActivity::class.java, "Guide" to GuideActivity::class.java,
                "Favourites" to FavoritesActivity::class.java).forEach { (l, c) ->
                addView(item(l, 0, c), LinearLayout.LayoutParams(-2, a.px(52)).apply { marginStart = a.px(4) })
            }
        }
        view.addView(nav, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        firstFocus = nav.getChildAt(1)

        fun circle(res: Int, desc: String, click: () -> Unit) = FrameLayout(a).apply {
            focusSkin(a.px(26), N.GLASS, 1.08f); contentDescription = desc
            addView(a.icon(res, 24, N.MUTED, "m"), FrameLayout.LayoutParams(a.px(24), a.px(24), Gravity.CENTER)); setOnClickListener { click() }
        }
        refresh = circle(R.drawable.ic_refresh, "Refresh channels") { Catalog.start(true); update() }
        val clock = TextClock(a).apply {
            format12Hour = "h:mm a"; format24Hour = "HH:mm"; typeface = Typeface.MONOSPACE; setTextColor(N.TEXT2)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, a.px(22).toFloat()); includeFontPadding = false
        }
        view.addView(LinearLayout(a).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END; noClip()
            addView(status, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = a.px(20) })
            addView(clock, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = a.px(20) })
            addView(refresh, LinearLayout.LayoutParams(a.px(52), a.px(52)).apply { marginEnd = a.px(12) })
            addView(circle(R.drawable.ic_settings, "Settings") { a.goTo(SettingsActivity::class.java) }, LinearLayout.LayoutParams(a.px(52), a.px(52)))
        }, FrameLayout.LayoutParams(a.px(700), -1, Gravity.END or Gravity.CENTER_VERTICAL))
        update()
    }

    /** Call from the screen's catalogue listener. */
    fun update() {
        status.text = if (Catalog.scanning) "Checking plan ${Catalog.scanned}/${Catalog.all.size}" else ""
        status.visibility = if (Catalog.scanning) View.VISIBLE else View.GONE
        refresh.animate().cancel()
        if (Catalog.scanning) refresh.animate().rotationBy(360f).setDuration(900).withEndAction { if (Catalog.scanning) update() }.start()
        else refresh.rotation = 0f
    }

    fun layoutParams() = FrameLayout.LayoutParams(-1, a.px(64)).apply { setMargins(a.px(96), a.px(54), a.px(96), 0) }
}

// ------------------------------------------------------------------ dialog

/** "Exit?" confirmation. Cancel has focus, so a stray OK can't close the app. */
internal fun Activity.confirmExit() {
    val d = Dialog(this)
    d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
    d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); d.window?.setDimAmount(0.7f)
    fun btn(label: String, click: () -> Unit) = FrameLayout(this).apply {
        focusSkin(px(14), N.FILL, 1.0f); setOnClickListener { click() }
        addView(txt(label, 24, N.TEXT, 600, tag = "p"), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
    }
    val cancel = btn("Cancel") { d.dismiss() }
    d.setContentView(LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = rr(N.SURFACE, px(24), 0x1AFFFFFF, px(1)); noClip()
        setPadding(px(48), px(44), px(48), px(40))
        addView(txt("Exit the app?", 36, N.TEXT, 600))
        addView(txt("You'll need to reopen it to keep watching.", 22, N.MUTED), LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(10); bottomMargin = px(32) })
        addView(LinearLayout(context).apply {
            noClip(); addView(cancel, LinearLayout.LayoutParams(0, px(68), 1f))
            addView(btn("Exit") { d.dismiss(); finishAffinity() }, LinearLayout.LayoutParams(0, px(68), 1f).apply { marginStart = px(16) })
        })
    })
    d.window?.setLayout(px(760), ViewGroup.LayoutParams.WRAP_CONTENT)
    d.show(); cancel.requestFocus()
}
