package com.vistaar.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * First-run disclaimer. Shown once to everyone (also existing users); bump [VERSION] when the wording changes to ask again.
 * Opened from Settings with "view" it is read-only.
 */
class DisclaimerActivity : Activity() {
    companion object {
        const val VERSION = 1
        const val TEXT = "Vistaar is a media player only. It does not host, store or provide any content. " +
            "Vistaar is not affiliated with any broadcaster, channel or content provider, including Jio, " +
            "and does not endorse the use of illegal content sources. " +
            "Free channels come from public playlists, and Jio channels appear only if you sign in with your own subscription. " +
            "You are responsible for what you watch."
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        val view = intent.getBooleanExtra("view", false)

        val accept = LinearLayout(this).apply {
            gravity = Gravity.CENTER; focusSkin(px(16), N.TEXT, 1.04f); setPadding(px(44), 0, px(44), 0)
            addView(txt(if (view) "Close" else "Accept and continue", 26, N.INK, 600))
            setOnClickListener { if (view) finish() else accepted() }
        }
        val exit = LinearLayout(this).apply {
            gravity = Gravity.CENTER; focusSkin(px(16), N.FILL, 1.04f); setPadding(px(36), 0, px(36), 0)
            addView(txt("Exit", 24, N.TEXT, 500, tag = "p")); setOnClickListener { finishAffinity() }
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(N.BG); noClip() // the focused button zooms past the column's edge
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(BrandMark(context), LinearLayout.LayoutParams(px(40), px(40)))
                addView(txt("Vistaar", 28, N.TEXT, 600), LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(14) })
            }, FrameLayout.LayoutParams(-2, px(64)).apply { setMargins(px(96), px(54), 0, 0) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; noClip()
                addView(txt("DISCLAIMER", 20, N.MUTED, 500).apply { letterSpacing = 0.08f })
                addView(txt("Before you start", 72, N.TEXT, 600).apply { letterSpacing = -0.025f }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(24) })
                addView(txt(TEXT, 30, N.TEXT2).apply { setLineSpacing(0f, 1.4f) }, LinearLayout.LayoutParams(px(1180), -2).apply { topMargin = px(36) })
                addView(LinearLayout(context).apply {
                    noClip(); gravity = Gravity.CENTER_VERTICAL
                    addView(accept, LinearLayout.LayoutParams(-2, px(84)))
                    if (!view) addView(exit, LinearLayout.LayoutParams(-2, px(84)).apply { marginStart = px(16) })
                }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(56) })
            }, FrameLayout.LayoutParams(-2, -2).apply { setMargins(px(96), px(220), 0, 0) })
        })
        accept.requestFocus()
    }

    private fun accepted() {
        Store.disclaimerV = VERSION
        if (!Store.loggedIn) Store.freeOnly = true // no sign-in screen: Jio is added from Settings
        startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (intent.getBooleanExtra("view", false)) super.onBackPressed() else finishAffinity() }
}
