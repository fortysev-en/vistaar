package com.vistaar.tv

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import java.util.WeakHashMap

/** A small phone icon in the top right of every screen while a phone is connected to the remote. Drawn over the screen's own content, so it shows in the player too. */
object StatusOverlay {
    private val ui = Handler(Looper.getMainLooper())
    private val views = WeakHashMap<Activity, ImageView>()
    private var resumed: Activity? = null
    private val tick = object : Runnable { override fun run() { resumed?.let { refresh(it) }; ui.postDelayed(this, 1000) } }

    fun onResume(a: Activity) {
        if (a is SplashActivity || a is DisclaimerActivity) return
        resumed = a; refresh(a)
        ui.removeCallbacks(tick); ui.postDelayed(tick, 1000)
    }

    fun onPause(a: Activity) { if (resumed === a) { resumed = null; ui.removeCallbacks(tick) } }

    private fun badge(a: Activity): ImageView = views.getOrPut(a) {
        val v = ImageView(a).apply { setImageResource(R.drawable.ic_phone); setColorFilter(N.ACCENT); contentDescription = "Phone remote connected"; visibility = View.GONE; isFocusable = false; isClickable = false }
        (a.window.decorView as ViewGroup).addView(v, FrameLayout.LayoutParams(a.px(30), a.px(30), Gravity.TOP or Gravity.END).apply { setMargins(0, a.px(12), a.px(96), 0) })
        v
    }

    private fun refresh(a: Activity) { badge(a).visibility = if (RemoteHub.connected) View.VISIBLE else View.GONE }
}
