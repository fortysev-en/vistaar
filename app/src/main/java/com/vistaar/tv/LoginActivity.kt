package com.vistaar.tv

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Sign in: mobile number, then the 6-digit code (the "Sign in" boards of the design).
 * The calls are the same as before (Api.sendOtp / Api.verifyOtp); only the interface changed.
 */
class LoginActivity : Activity() {
    private enum class Step { PHONE, SENDING, OTP, VERIFYING, DONE }

    private val ui = Handler(Looper.getMainLooper())
    private var step = Step.PHONE
    private var phone = ""
    private var otp = ""
    private var error = ""
    private var resendIn = 0
    private val amber = 0xFFF2B544.toInt()

    private lateinit var stepLabel: TextView
    private lateinit var heading: TextView
    private lateinit var sub: TextView
    private lateinit var dot2: View
    private lateinit var phoneBlock: LinearLayout
    private lateinit var phoneBox: LinearLayout
    private lateinit var phoneText: TextView
    private lateinit var phoneCaret: View
    private lateinit var phoneHelp: TextView
    private lateinit var otpBlock: LinearLayout
    private lateinit var sentTo: TextView
    private lateinit var otpBoxes: List<Pair<FrameLayout, Pair<TextView, View>>>
    private lateinit var otpHelp: TextView
    private lateinit var resendBtn: TextView
    private lateinit var resendTimer: TextView
    private lateinit var padBlock: LinearLayout
    private lateinit var primary: FrameLayout
    private lateinit var primaryLabel: TextView
    private lateinit var primaryIcon: ImageView
    private lateinit var footnote: TextView
    private lateinit var doneBlock: LinearLayout
    private lateinit var doneSub: TextView
    private lateinit var startBtn: View
    private lateinit var key5: View
    private lateinit var backHint: TextView
    private val tick = object : Runnable { override fun run() { if (resendIn > 0) { resendIn--; render(); ui.postDelayed(this, 1000) } } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        if (Store.loggedIn) return openHome()

        stepLabel = txt("", 20, N.MUTED, 500).apply { letterSpacing = 0.08f }
        heading = txt("", 72, N.TEXT, 600).apply { letterSpacing = -0.025f; setLineSpacing(0f, 1.05f) }
        sub = txt("", 26, N.TEXT2).apply { setLineSpacing(0f, 1.3f) }
        dot2 = View(this)
        val dots = LinearLayout(this).apply {
            addView(View(context).apply { background = rr(N.ACCENT, px(3)) }, LinearLayout.LayoutParams(px(56), px(6)))
            addView(dot2, LinearLayout.LayoutParams(px(56), px(6)).apply { marginStart = px(10) })
        }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(stepLabel)
            addView(heading, LinearLayout.LayoutParams(px(760), -2).apply { topMargin = px(24) })
            addView(sub, LinearLayout.LayoutParams(px(640), -2).apply { topMargin = px(24) })
            addView(dots, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(36) })
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; noClip(); background = rr(0xDB0E1014.toInt(), px(32), N.HAIR, px(1)); setPadding(px(56), px(56), px(56), px(48))
            addView(buildPhone(), LinearLayout.LayoutParams(-1, -2))
            addView(buildOtp(), LinearLayout.LayoutParams(-1, -2))
            addView(buildPad(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(24) })
            addView(buildDone(), LinearLayout.LayoutParams(-1, -1))
        }
        backHint = txt("", 18, N.DIM)
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(N.BG); noClip()
            addView(ArtView(context, 0).apply { alpha = 0.55f }, FrameLayout.LayoutParams(-1, -1))
            addView(View(context).apply { background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0x8C07080A.toInt(), 0xCC07080A.toInt(), 0xEB07080A.toInt())) }, FrameLayout.LayoutParams(-1, -1))
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(BrandMark(context), LinearLayout.LayoutParams(px(40), px(40)))
                addView(txt("Vistaar", 28, N.TEXT, 600), LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(14) })
            }, FrameLayout.LayoutParams(-2, px(64)).apply { setMargins(px(96), px(54), 0, 0) })
            addView(left, FrameLayout.LayoutParams(-2, -2).apply { setMargins(px(96), px(330), 0, 0) })
            addView(card, FrameLayout.LayoutParams(px(824), px(840)).apply { setMargins(px(1000), px(120), 0, 0) })
            addView(LinearLayout(context).apply {
                addView(txt("0–9  Type digits", 18, N.DIM)); addView(txt("OK  Confirm", 18, N.DIM), LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(28) })
                addView(backHint, LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(28) })
            }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM).apply { setMargins(px(96), 0, 0, px(54)) })
        })
        render()
        key5.requestFocus()
    }

    // ---------------------------------------------------------------- building blocks

    private fun caret(h: Int): View = View(this).apply {
        background = rr(N.ACCENT, px(2))
        ObjectAnimator.ofFloat(this, "alpha", 1f, 0f).apply { duration = 500; repeatMode = ObjectAnimator.REVERSE; repeatCount = ObjectAnimator.INFINITE; interpolator = LinearInterpolator(); start() }
    }

    private fun buildPhone(): View {
        phoneText = txt("", 40, N.TEXT, 500, mono = true).apply { letterSpacing = 0.04f }
        phoneCaret = caret(44)
        phoneHelp = txt("", 20, N.MUTED)
        phoneBox = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(px(12), 0, px(24), 0)
            addView(txt("+91", 30, N.TEXT, 600).apply { gravity = Gravity.CENTER; setPadding(px(18), 0, px(18), 0); background = rr(0xFF20242C.toInt(), px(14)) }, LinearLayout.LayoutParams(-2, px(68)))
            addView(phoneText, LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(20) })
            addView(phoneCaret, LinearLayout.LayoutParams(px(3), px(44)).apply { marginStart = px(4) })
        }
        phoneBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(txt("Mobile number", 22, N.MUTED, 500))
            addView(phoneBox, LinearLayout.LayoutParams(-1, px(96)).apply { topMargin = px(12) })
            addView(phoneHelp, LinearLayout.LayoutParams(-1, px(26)).apply { topMargin = px(12) })
        }
        return phoneBlock
    }

    private fun buildOtp(): View {
        sentTo = txt("", 22, N.TEXT, 600)
        val change = txt("Change", 20, N.TEXT, 500, tag = "p").apply {
            gravity = Gravity.CENTER; setPadding(px(18), 0, px(18), 0); focusSkin(px(24), 0xFF20242C.toInt(), 1.05f); setOnClickListener { changeNumber() }
        }
        otpBoxes = (0 until 6).map {
            val t = txt("", 44, N.TEXT, 600, mono = true); val c = caret(44)
            val box = FrameLayout(this).apply {
                addView(t, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER)); addView(c, FrameLayout.LayoutParams(px(3), px(44), Gravity.CENTER))
            }
            box to (t to c)
        }
        otpHelp = txt("", 20, N.MUTED)
        resendBtn = txt("Resend code", 20, N.ACCENT, 600, tag = "a").apply {
            gravity = Gravity.CENTER; setPadding(px(16), 0, px(16), 0); focusSkin(px(22), Color.TRANSPARENT, 1.05f); setOnClickListener { resend() }
        }
        resendTimer = txt("", 20, N.DIM, mono = true)
        otpBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(LinearLayout(context).apply { addView(txt("Code sent to ", 22, N.MUTED, 500)); addView(sentTo) }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(change, LinearLayout.LayoutParams(-2, px(48)))
            })
            addView(LinearLayout(context).apply {
                otpBoxes.forEachIndexed { i, (box, _) -> addView(box, LinearLayout.LayoutParams(0, px(96), 1f).apply { if (i > 0) marginStart = px(16) }) }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) })
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(otpHelp, LinearLayout.LayoutParams(0, -2, 1f)); addView(resendBtn, LinearLayout.LayoutParams(-2, px(44))); addView(resendTimer)
            }, LinearLayout.LayoutParams(-1, px(44)).apply { topMargin = px(12) })
        }
        return otpBlock
    }

    private fun buildPad(): View {
        fun key(label: String?, ico: Int?, idle: Int, click: () -> Unit) = FrameLayout(this).apply {
            focusSkin(px(16), idle, 1.06f); setOnClickListener { click() }
            if (ico != null) addView(icon(ico, 32, N.TEXT, "p"), FrameLayout.LayoutParams(px(32), px(32), Gravity.CENTER))
            else addView(txt(label!!, if (label.length > 1) 22 else 34, N.TEXT, 500, mono = label.length == 1, tag = "p"), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("del", "0", "clear"))
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; noClip()
            rows.forEachIndexed { r, row ->
                addView(LinearLayout(context).apply {
                    noClip()
                    row.forEachIndexed { i, k ->
                        val v = when (k) {
                            "del" -> key(null, R.drawable.ic_backspace, 0xFF20242C.toInt()) { press("del") }
                            "clear" -> key("Clear", null, 0xFF20242C.toInt()) { press("clear") }
                            else -> key(k, null, N.SURFACE) { press(k) }
                        }
                        if (k == "5") key5 = v
                        addView(v, LinearLayout.LayoutParams(0, px(76), 1f).apply { if (i > 0) marginStart = px(12) })
                    }
                }, LinearLayout.LayoutParams(-1, -2).apply { if (r > 0) topMargin = px(12) })
            }
        }
        primaryIcon = icon(R.drawable.ic_refresh, 26, N.TEXT).apply { visibility = View.GONE }
        primaryLabel = txt("", 26, N.INK, 600)
        primary = FrameLayout(this).apply {
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER; addView(primaryIcon, LinearLayout.LayoutParams(px(26), px(26)).apply { marginEnd = px(14) }); addView(primaryLabel)
            }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
            setOnClickListener { if (step == Step.PHONE) sendOtp() else if (step == Step.OTP) verify() }
        }
        footnote = txt("", 18, N.DIM).apply { gravity = Gravity.CENTER }
        padBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; noClip()
            addView(grid)
            addView(primary, LinearLayout.LayoutParams(-1, px(80)).apply { topMargin = px(24) })
            addView(footnote, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(24) })
        }
        return padBlock
    }

    private fun buildDone(): View {
        doneSub = txt("", 24, N.MUTED)
        startBtn = FrameLayout(this).apply {
            focusSkin(px(16), N.TEXT, 1.06f); setOnClickListener { openHome() }
            addView(txt("Start watching", 26, N.INK, 600), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER)); setPadding(px(48), 0, px(48), 0)
        }
        doneBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; noClip()
            addView(FrameLayout(context).apply {
                background = rr(0x295AA9FF, px(60)); addView(icon(R.drawable.ic_check, 60, N.ACCENT), FrameLayout.LayoutParams(px(60), px(60), Gravity.CENTER))
            }, LinearLayout.LayoutParams(px(120), px(120)))
            addView(txt("You're signed in", 44, N.TEXT, 600), LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(24) })
            addView(doneSub, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(24) })
            addView(startBtn, LinearLayout.LayoutParams(-2, px(80)).apply { topMargin = px(40) })
        }
        return doneBlock
    }

    // ---------------------------------------------------------------- state

    private fun fmt(p: String) = if (p.length > 5) p.take(5) + " " + p.drop(5) else p

    private fun press(k: String) {
        if (step != Step.PHONE && step != Step.OTP) return
        val phoneStep = step == Step.PHONE
        val max = if (phoneStep) 10 else 6
        var v = if (phoneStep) phone else otp
        when {
            k == "del" -> v = v.dropLast(1)
            k == "clear" -> v = ""
            k.length == 1 && k[0].isDigit() && v.length < max -> v += k
        }
        if (phoneStep) phone = v else otp = v
        error = ""; render()
        if (v.length == max) primary.requestFocus() // complete: land on the confirm button, like the design
    }

    private fun readable(e: String) = e.replace(Regex("\\s+"), " ").take(110)

    private fun sendOtp() {
        if (phone.length != 10) return
        if (phone[0] !in '6'..'9') { error = "Indian mobile numbers start with 6, 7, 8 or 9"; render(); return }
        step = Step.SENDING; error = ""; render()
        Thread {
            val err = try { Api.sendOtp(phone) } catch (e: Exception) { "Network error: ${e.message}" }
            runOnUiThread {
                if (err != null) { step = Step.PHONE; error = readable(err) }
                else { step = Step.OTP; otp = ""; startTimer() }
                render(); if (step == Step.OTP) key5.requestFocus()
            }
        }.start()
    }

    private fun verify() {
        if (otp.length != 6) return
        step = Step.VERIFYING; error = ""; render()
        Thread {
            val err = try { Api.verifyOtp(phone, otp) } catch (e: Exception) { "Network error: ${e.message}" }
            runOnUiThread {
                if (err != null) { step = Step.OTP; error = readable(err); otp = "" } else { step = Step.DONE; ui.removeCallbacks(tick) }
                render(); if (step == Step.DONE) startBtn.requestFocus() else key5.requestFocus()
            }
        }.start()
    }

    private fun resend() {
        error = ""; otp = ""; startTimer(); render()
        Thread { try { Api.sendOtp(phone) } catch (_: Exception) {} }.start()
    }

    private fun changeNumber() { ui.removeCallbacks(tick); step = Step.PHONE; otp = ""; error = ""; render(); key5.requestFocus() }

    private fun startTimer() { resendIn = 30; ui.removeCallbacks(tick); ui.postDelayed(tick, 1000) }

    private fun render() {
        val onPhone = step == Step.PHONE || step == Step.SENDING
        val onOtp = step == Step.OTP || step == Step.VERIFYING
        val busy = step == Step.SENDING || step == Step.VERIFYING
        stepLabel.text = (if (step == Step.DONE) "All set" else if (onPhone) "Step 1 of 2" else "Step 2 of 2").uppercase()
        heading.text = if (onPhone) "Sign in with your mobile" else if (onOtp) "Enter your code" else "Welcome to Vistaar"
        sub.text = if (onPhone) "Use the number linked to your subscription. We’ll text you a one-time code."
            else if (onOtp) "Check your phone for a 6-digit code. It expires in 10 minutes." else "Your channels, guide and favourites are ready."
        dot2.background = rr(if (onPhone) 0x2EFFFFFF else N.ACCENT, px(3))

        phoneBlock.visibility = if (onPhone) View.VISIBLE else View.GONE
        phoneText.text = if (phone.isEmpty()) "98765 43210" else fmt(phone)
        phoneText.setTextColor(if (phone.isEmpty()) N.DIM else N.TEXT)
        phoneCaret.visibility = if (step == Step.PHONE && phone.length < 10) View.VISIBLE else View.GONE
        phoneBox.background = rr(N.SURFACE, px(20), if (error.isNotEmpty()) amber else if (step == Step.PHONE && phone.length < 10) N.ACCENT else 0x1FFFFFFF, px(2))
        phoneHelp.text = if (error.isNotEmpty()) error else if (phone.length == 10) "Looks good. Press OK to get your code." else "${10 - phone.length} digits to go"
        phoneHelp.setTextColor(if (error.isNotEmpty()) amber else N.MUTED)

        otpBlock.visibility = if (onOtp) View.VISIBLE else View.GONE
        sentTo.text = "+91 ${fmt(phone)}"
        otpBoxes.forEachIndexed { i, (box, tc) ->
            val active = i == otp.length && step == Step.OTP
            tc.first.text = otp.getOrNull(i)?.toString() ?: ""; tc.second.visibility = if (active) View.VISIBLE else View.GONE
            box.background = rr(N.SURFACE, px(18), if (error.isNotEmpty()) amber else if (active) N.ACCENT else if (i < otp.length) 0x3DFFFFFF else 0x14FFFFFF, px(2))
        }
        otpHelp.text = if (error.isNotEmpty()) error else if (otp.length == 6) "Press OK to sign in." else "Didn’t get it? Check the number above."
        otpHelp.setTextColor(if (error.isNotEmpty()) amber else N.MUTED)
        resendBtn.visibility = if (onOtp && resendIn == 0) View.VISIBLE else View.GONE
        resendTimer.visibility = if (onOtp && resendIn > 0) View.VISIBLE else View.GONE
        resendTimer.text = "Resend in 0:" + (if (resendIn < 10) "0" else "") + resendIn

        padBlock.visibility = if (step == Step.DONE) View.GONE else View.VISIBLE
        val ready = !busy && (if (onPhone) phone.length == 10 else otp.length == 6)
        primaryLabel.text = when (step) { Step.SENDING -> "Sending code…"; Step.VERIFYING -> "Verifying…"; else -> if (onPhone) "Send OTP" else "Log in" }
        primaryLabel.setTextColor(if (ready) N.INK else if (busy) N.TEXT else N.DIM)
        primary.focusSkin(px(16), if (ready) N.TEXT else if (busy) N.FILL else 0x0FFFFFFF, 1.03f)
        primary.isFocusable = ready
        primaryIcon.visibility = if (busy) View.VISIBLE else View.GONE
        if (busy) primaryIcon.animate().rotationBy(360f).setDuration(900).setInterpolator(LinearInterpolator()).withEndAction { if (step == Step.SENDING || step == Step.VERIFYING) primaryIcon.animate().rotationBy(360f).setDuration(900).start() }.start()
        footnote.text = if (onPhone) "Standard SMS rates may apply. We never share your number." else "Signing in on this TV keeps you signed in until you sign out in Settings."
        backHint.text = "Back  " + if (onOtp) "Change number" else "Exit"

        doneBlock.visibility = if (step == Step.DONE) View.VISIBLE else View.GONE
        doneSub.text = "+91 ${fmt(phone)} · this TV stays signed in"
    }

    // ---------------------------------------------------------------- input

    override fun onKeyDown(code: Int, e: KeyEvent): Boolean {
        val digit = when (code) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> (code - KeyEvent.KEYCODE_0).toString()
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> (code - KeyEvent.KEYCODE_NUMPAD_0).toString()
            else -> null
        }
        if (digit != null) { press(digit); return true }
        if (code == KeyEvent.KEYCODE_DEL) { press("del"); return true }
        return super.onKeyDown(code, e)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (step == Step.OTP) changeNumber() else confirmExit() }

    override fun onDestroy() { super.onDestroy(); ui.removeCallbacksAndMessages(null) }

    private fun openHome() { startActivity(Intent(this, HomeActivity::class.java)); finish() }
}
