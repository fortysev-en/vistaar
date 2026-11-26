package com.vistaar.tv

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** Settings > Phone remote: turns the phone remote on or off and shows the QR code, address and PIN. */
class RemoteActivity : Activity() {
    private lateinit var qr: ImageView
    private lateinit var address: TextView
    private lateinit var pin: TextView
    private lateinit var status: TextView
    private lateinit var toggleLabel: TextView
    private lateinit var steps: LinearLayout
    private lateinit var info: LinearLayout
    private var err: String? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        if (!Store.hasAccess) { startActivity(android.content.Intent(this, SplashActivity::class.java)); finish(); return }

        val back = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; focusSkin(px(26), N.SURFACE, 1.04f); setPadding(px(16), 0, px(22), 0)
            addView(icon(R.drawable.ic_left, 20, N.TEXT2, "p"), LinearLayout.LayoutParams(px(20), px(20)))
            addView(txt("Settings", 20, N.TEXT2, 500, tag = "p"), LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(8) })
            setOnClickListener { finish() }
        }
        qr = ImageView(this).apply { background = rr(N.TEXT, px(28)); setPadding(px(24), px(24), px(24), px(24)) }
        address = txt("", 34, N.TEXT, 600, mono = true); pin = txt("", 96, N.ACCENT, 800, mono = true).apply { letterSpacing = 0.12f }
        status = txt("", 22, N.MUTED).apply { setLineSpacing(0f, 1.25f) }
        toggleLabel = txt("", 24, N.INK, 600, tag = null)
        val toggle = LinearLayout(this).apply {
            gravity = Gravity.CENTER; focusSkin(px(42), N.TEXT, 1.04f); setPadding(px(44), 0, px(44), 0)
            addView(toggleLabel)
            setOnClickListener { err = null; if (RemoteHub.running) RemoteHub.stop() else err = RemoteHub.start(this@RemoteActivity); render() }
        }
        info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(txt("ADDRESS", 18, N.ACCENT, 600).apply { letterSpacing = 0.14f })
            addView(address, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(8) })
            addView(txt("PIN", 18, N.ACCENT, 600).apply { letterSpacing = 0.14f }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(36) })
            addView(pin, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(4) })
        }
        steps = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            listOf("1  Join the same Wi-Fi as this TV.", "2  Scan the code, or type the address in your phone's browser.", "3  Enter the PIN. Arrows, OK and a text box appear.")
                .forEach { addView(txt(it, 22, N.TEXT2), LinearLayout.LayoutParams(px(760), -2).apply { topMargin = px(14) }) }
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(N.BG); noClip()
            addView(back, FrameLayout.LayoutParams(-2, px(52)).apply { setMargins(px(96), px(54), 0, 0) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; noClip()
                addView(txt("Phone remote", 56, N.TEXT, 800).apply { letterSpacing = -0.03f })
                addView(txt("Navigate and type with your phone. Pasting a long playlist link is much easier than the on-screen keyboard.", 24, N.MUTED).apply { setLineSpacing(0f, 1.3f) },
                    LinearLayout.LayoutParams(px(900), -2).apply { topMargin = px(10) })
                addView(LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL; noClip()
                    addView(qr, LinearLayout.LayoutParams(px(380), px(380)))
                    addView(info, LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(64) })
                }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(44) })
                addView(steps)
                addView(status, LinearLayout.LayoutParams(px(900), -2).apply { topMargin = px(24) })
                addView(toggle, LinearLayout.LayoutParams(-2, px(84)).apply { topMargin = px(28) })
            }, FrameLayout.LayoutParams(-2, -2).apply { setMargins(px(96), px(140), 0, 0) })
        })
        toggle.requestFocus()
    }

    override fun onResume() { super.onResume(); RemoteHub.onChange = { runOnUiThread { render() } }; render() }
    override fun onPause() { super.onPause(); RemoteHub.onChange = null }

    private fun render() {
        val on = RemoteHub.running
        toggleLabel.text = if (on) "Turn off" else "Turn on"
        listOf<View>(qr, info, steps).forEach { it.visibility = if (on) View.VISIBLE else View.GONE }
        if (on) {
            address.text = RemoteHub.url; pin.text = RemoteHub.pin
            qr.setImageBitmap(qrBitmap(RemoteHub.url))
            status.text = (if (RemoteHub.phones > 0) "${RemoteHub.phones} phone${if (RemoteHub.phones == 1) "" else "s"} connected. " else "Waiting for a phone. ") +
                "It keeps running on other screens and turns itself off after 15 minutes without use."
        } else status.text = err ?: "Off. Turn it on only while you need it."
    }

    private fun qrBitmap(text: String): Bitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 320, 320)
        return Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888).also { b ->
            for (x in 0 until m.width) for (y in 0 until m.height) b.setPixel(x, y, if (m.get(x, y)) Color.BLACK else Color.WHITE)
        }
    }
}
