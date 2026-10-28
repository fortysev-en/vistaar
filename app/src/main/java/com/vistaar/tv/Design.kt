package com.vistaar.tv

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.Executors

/** Shared look: frosted "glass" surfaces over a soft aurora background, tvOS-style focus. */

internal fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

internal val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
internal val LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)

/** Translucent panel; brightens and gets a white rim when focused. [dark] is for panels over live video. */
internal fun Context.glass(radiusDp: Int, dark: Boolean = false) = StateListDrawable().apply {
    fun shape(top: Int, bottom: Int, stroke: Int, widthDp: Int) =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply {
            cornerRadius = dp(radiusDp).toFloat(); setStroke(dp(widthDp).coerceAtLeast(1), stroke)
        }
    addState(intArrayOf(android.R.attr.state_focused), shape(0x70FFFFFF, 0x48FFFFFF, 0xE6FFFFFF.toInt(), 2))
    addState(intArrayOf(android.R.attr.state_selected), shape(0x50FFFFFF, 0x30FFFFFF, 0x99FFFFFF.toInt(), 2))
    addState(intArrayOf(), if (dark) shape(0xD9141420.toInt(), 0xE60C0C16.toInt(), 0x33FFFFFF, 1)
                           else shape(0x2EFFFFFF, 0x14FFFFFF, 0x33FFFFFF, 1))
}

/** Gentle zoom on focus, like tvOS. */
internal fun View.zoomOnFocus(scale: Float = 1.08f) {
    setOnFocusChangeListener { v, f ->
        v.animate().scaleX(if (f) scale else 1f).scaleY(if (f) scale else 1f).translationZ(if (f) 16f else 0f).setStartDelay(0).setDuration(140).start()
    }
}

/** Static, cheap background: three coloured radial glows on near-black. */
class AuroraView(c: Context) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var orbs: List<Pair<RadialGradient, Triple<Float, Float, Float>>> = emptyList()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        fun orb(x: Float, y: Float, r: Float, color: Int) =
            RadialGradient(x, y, r, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP) to Triple(x, y, r)
        orbs = listOf(
            orb(w * 0.12f, h * 0.18f, w * 0.55f, 0x773B2BFF),
            orb(w * 0.92f, h * 0.10f, w * 0.48f, 0x66FF2D78),
            orb(w * 0.75f, h * 1.00f, w * 0.60f, 0x5500B8D9)
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(0xFF090912.toInt())
        orbs.forEach { (g, p) -> paint.shader = g; canvas.drawCircle(p.first, p.second, p.third, paint) }
    }
}

/** Cheap box blur for a small bitmap (we blur a 1/8-scale screenshot, then stretch it: soft "frosted" backdrop). */
object Blur {
    fun box(src: Bitmap, radius: Int, passes: Int): Bitmap {
        val w = src.width; val h = src.height
        var a = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        var b = IntArray(w * h)
        repeat(passes) { line(a, b, w, h, radius, true); line(b, a, w, h, radius, false) }
        return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun line(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val n = if (horizontal) w else h; val lines = if (horizontal) h else w; val div = 2 * r + 1
        for (l in 0 until lines) {
            fun at(i: Int) = if (horizontal) l * w + i else i * w + l
            var sr = 0; var sg = 0; var sb = 0
            for (k in -r..r) { val p = src[at(k.coerceIn(0, n - 1))]; sr += (p shr 16) and 255; sg += (p shr 8) and 255; sb += p and 255 }
            for (i in 0 until n) {
                dst[at(i)] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = src[at((i + r + 1).coerceAtMost(n - 1))]; val rem = src[at((i - r).coerceAtLeast(0))]
                sr += ((add shr 16) and 255) - ((rem shr 16) and 255)
                sg += ((add shr 8) and 255) - ((rem shr 8) and 255)
                sb += (add and 255) - (rem and 255)
            }
        }
    }
}

/**
 * A panel that shows a blurred snapshot of what is behind it (set via [setBackdrop]), tinted and with a
 * light rim: real frosted glass, which plain translucency can't give. Must be a direct child of the root
 * the snapshot was taken from, so its left/top line up with the bitmap.
 */
class FrostedLayout(c: Context) : android.widget.LinearLayout(c) {
    var corner = 0f
    private var shader: BitmapShader? = null
    private var scale = 1f
    private val density = c.resources.displayMetrics.density
    private val photo = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.5f) }) // vibrancy, as on tvOS
    }
    private val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66080812 }
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * density }

    init { setWillNotDraw(false) }

    fun setBackdrop(bitmap: Bitmap, scale: Float) {
        shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP); this.scale = scale; invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        sheen.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0x3DFFFFFF, 0x12FFFFFF, 0x33000000), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        rim.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0x99FFFFFF.toInt(), 0x1FFFFFFF, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val r = android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat())
        shader?.let {
            it.setLocalMatrix(Matrix().apply { setScale(1f / scale, 1f / scale); postTranslate(-left.toFloat(), -top.toFloat()) })
            photo.shader = it; canvas.drawRoundRect(r, corner, corner, photo)
        }
        canvas.drawRoundRect(r, corner, corner, base)
        canvas.drawRoundRect(r, corner, corner, sheen)
        val inset = rim.strokeWidth / 2
        canvas.drawRoundRect(android.graphics.RectF(inset, inset, width - inset, height - inset), corner, corner, rim)
    }
}

/** Minimal remote-image loader (memory cache, 3 threads). [done] reports whether a bitmap was shown. */
object Images {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(k: String, v: Bitmap) = v.byteCount
    }
    private val pool = Executors.newFixedThreadPool(3)
    private val http = OkHttpClient()
    private val main = Handler(Looper.getMainLooper())

    fun load(view: ImageView, url: String?, done: (Boolean) -> Unit = {}) {
        view.setTag(R.id.img_url, url); view.setImageDrawable(null) // not view.tag: callers look views up by tag
        if (url.isNullOrEmpty()) return done(false)
        cache.get(url)?.let { view.setImageBitmap(it); return done(true) }
        pool.execute {
            val bmp = try {
                http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    val bytes = r.body?.bytes() ?: return@use null
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                    o.inSampleSize = (o.outWidth / 400).coerceAtLeast(1); o.inJustDecodeBounds = false
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                }
            } catch (e: Exception) { null }
            if (bmp != null) cache.put(url, bmp)
            main.post { if (view.getTag(R.id.img_url) == url) { if (bmp != null) view.setImageBitmap(bmp); done(bmp != null) } }
        }
    }
}
