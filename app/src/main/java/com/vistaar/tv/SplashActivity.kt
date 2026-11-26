package com.vistaar.tv

import android.animation.Animator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Launcher screen: the Vistaar mark and the maker's wordmark for a moment, then the disclaimer (until accepted) or Home. */
class SplashActivity : Activity() {
    private companion object { const val LOAD_MS = 2100L }
    private val ui = Handler(Looper.getMainLooper())
    private val go = Runnable { next() }

    private val running = ArrayList<Animator>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        val halo = View(this).apply {
            background = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT; gradientRadius = px(600).toFloat(); shape = GradientDrawable.OVAL
                colors = intArrayOf(0x385AA9FF, 0x105AA9FF, 0x005AA9FF)
            }
            alpha = 0f; scaleX = 0.6f; scaleY = 0.6f
        }
        val mark = BrandMark(this).apply { alpha = 0f; scaleX = 0.84f; scaleY = 0.84f }
        val word = txt("Vistaar", 112, N.TEXT, 700).apply { letterSpacing = 0.05f; alpha = 0f; translationY = px(18).toFloat() }
        val bar = LoadBar(this)
        val maker = ImageView(this).apply { setImageResource(R.drawable.brand_wordmark); alpha = 0f }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(N.BG)
            addView(halo, FrameLayout.LayoutParams(px(1200), px(1200), Gravity.CENTER))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; noClip()
                addView(mark, LinearLayout.LayoutParams(px(168), px(168)))
                addView(word, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(36) })
            }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
            addView(bar, FrameLayout.LayoutParams(px(280), px(16), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = px(176) })
            addView(maker, FrameLayout.LayoutParams(px(240), px(53), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = px(72) })
        })

        // The halo blooms, the mark settles in, the word rises and closes up, a light passes over it while the bar fills.
        val soft = PathInterpolator(0.2f, 0.9f, 0.3f, 1f)
        halo.animate().alpha(1f).scaleX(1.1f).scaleY(1.1f).setDuration(2400).setInterpolator(soft).start()
        mark.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(900).setInterpolator(soft).start()
        word.animate().alpha(1f).translationY(0f).setStartDelay(350).setDuration(800).setInterpolator(soft).start()
        maker.animate().alpha(1f).setStartDelay(600).setDuration(600).start()
        play(ValueAnimator.ofFloat(0.05f, -0.025f), 350, 900, soft) { word.letterSpacing = it }
        play(ValueAnimator.ofFloat(0f, 1f), 300, LOAD_MS, PathInterpolator(0.4f, 0f, 0.2f, 1f)) { bar.progress = it }
        play(ValueAnimator.ofFloat(0f, 1f), 1150, 1100, PathInterpolator(0.4f, 0f, 0.2f, 1f)) { shine(word, it) }
    }

    private fun play(a: ValueAnimator, delay: Long, length: Long, curve: TimeInterpolator, step: (Float) -> Unit) {
        a.startDelay = delay; a.duration = length; a.interpolator = curve
        a.addUpdateListener { step(it.animatedValue as Float) }
        running.add(a); a.start()
    }

    /** A band of pale blue light crossing the letters once: [at] 0 is just before the word, 1 just past it. */
    private fun shine(word: TextView, at: Float) {
        val w = word.width.toFloat()
        if (w <= 0f) return
        val band = w * 0.45f
        val x = -band + (w + band * 2) * at - band / 2
        word.paint.shader = LinearGradient(x, 0f, x + band, band * 0.18f, intArrayOf(N.TEXT, 0xFF8CC4FF.toInt(), N.TEXT), null, Shader.TileMode.CLAMP)
        word.invalidate()
    }

    override fun onDestroy() { super.onDestroy(); running.forEach { it.cancel() } }

    override fun onResume() { super.onResume(); ui.postDelayed(go, 2600) }
    override fun onPause() { super.onPause(); ui.removeCallbacks(go) }

    private fun next() {
        if (Store.disclaimerV < DisclaimerActivity.VERSION) startActivity(Intent(this, DisclaimerActivity::class.java))
        else {
            if (!Store.hasAccess) Store.freeOnly = true // Jio sign-in lives in Settings; nothing to sign in to here
            val home = Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // The app opens on the tab chosen in Settings, Startup. Home stays underneath, so Back from that tab goes to Home.
            val first = startTab().second
            if (first == HomeActivity::class.java || !Store.langsDone) startActivity(home) else startActivities(arrayOf(home, Intent(this, first)))
        }
        finish()
    }
}

/** The splash loading line: a hairline track, an accent fill, and a soft glow riding its leading edge. */
private class LoadBar(c: Context) : View(c) {
    var progress = 0f; set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val line = context.px(4).toFloat(); val glow = height / 2f
        val left = glow; val right = width - glow; val mid = height / 2f; val top = mid - line / 2; val end = left + (right - left) * progress
        p.shader = null; p.color = 0x24FFFFFF
        canvas.drawRoundRect(left, top, right, top + line, line / 2, line / 2, p)
        if (progress <= 0f) return
        p.color = N.ACCENT
        canvas.drawRoundRect(left, top, end.coerceAtLeast(left + line), top + line, line / 2, line / 2, p)
        p.shader = RadialGradient(end, mid, glow, intArrayOf(0xB38CC4FF.toInt(), 0x005AA9FF), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(end, mid, glow, p)
    }
}
