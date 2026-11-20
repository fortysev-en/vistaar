package com.vistaar.tv

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File

/** Checks GitHub Releases for a newer Vistaar and offers to install it. The release must have the APK attached. */
object Updater {
    private const val LATEST = "https://api.github.com/repos/fortysev-en/vistaar/releases/latest"
    private const val EVERY_MS = 12 * 3600_000L
    private val main = Handler(Looper.getMainLooper())
    private var checkedThisRun = false

    class Release(val version: String, val notes: String, val apk: String, val page: String)

    /** 0.10 > 0.9: compares dotted numbers, ignoring a leading "v" and any suffix. */
    internal fun newer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V").split('.').map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) { val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }; if (x != y) return x > y }
        return false
    }

    /** [done] gets the newer release, or null if up to date; [err] is set when the check itself failed. Runs off the main thread. */
    fun check(a: Activity, done: (Release?, String?) -> Unit) {
        Thread {
            var rel: Release? = null; var err: String? = null
            try {
                val j = JSONObject(Api.text(LATEST))
                val tag = j.optString("tag_name")
                val apk = j.optJSONArray("assets")?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") }?.optString("browser_download_url") }.orEmpty()
                val cur = a.packageManager.getPackageInfo(a.packageName, 0).versionName ?: "0"
                if (tag.isNotEmpty() && newer(tag, cur)) rel = Release(tag.removePrefix("v"), j.optString("body"), apk, j.optString("html_url"))
            } catch (e: Exception) { err = e.message ?: e.toString() }
            main.post { done(rel, err) }
        }.start()
    }

    /** Once per app run and at most every 12 hours; stays silent when up to date or offline. */
    fun autoCheck(a: Activity) {
        if (checkedThisRun) return
        checkedThisRun = true
        val last = Store.prefs.getLong("updChecked", 0)
        if (System.currentTimeMillis() - last < EVERY_MS) return
        check(a) { r, e ->
            if (e == null) Store.prefs.edit().putLong("updChecked", System.currentTimeMillis()).apply()
            if (r != null && !a.isFinishing) show(a, r)
        }
    }

    fun show(a: Activity, r: Release) {
        val d = Dialog(a)
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); d.window?.setDimAmount(0.7f)
        val msg = a.txt(r.notes.trim().lineSequence().filter { it.isNotBlank() }.take(4).joinToString("\n").take(280).ifEmpty { "A newer version of Vistaar is ready." }, 22, N.MUTED).apply { setLineSpacing(0f, 1.3f) }
        lateinit var updateLabel: TextView
        fun btn(label: String, click: () -> Unit) = FrameLayout(a).apply {
            focusSkin(a.px(14), N.FILL, 1.0f); setOnClickListener { click() }
            addView(a.txt(label, 24, N.TEXT, 600, tag = "p").also { if (label == "Update") updateLabel = it }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
        val later = btn("Later") { d.dismiss() }
        var busy = false
        val update = btn("Update") {
            if (busy) return@btn
            if (r.apk.isEmpty()) { msg.text = "This release has no APK attached. Download it from ${r.page}"; return@btn }
            busy = true; updateLabel.text = "Downloading…"
            Thread {
                val out = File(File(a.cacheDir, "updates").apply { mkdirs() }, "Vistaar-${r.version}.apk")
                try {
                    Api.download(r.apk, out) { p -> main.post { updateLabel.text = "Downloading $p%" } }
                    main.post { d.dismiss(); install(a, out) }
                } catch (e: Exception) { main.post { busy = false; updateLabel.text = "Update"; msg.text = "Download failed: ${e.message?.take(80)}" } }
            }.start()
        }
        d.setContentView(LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; background = rr(N.SURFACE, a.px(24), 0x1AFFFFFF, a.px(1)); noClip()
            setPadding(a.px(48), a.px(44), a.px(48), a.px(40))
            addView(a.txt("Version ${r.version} is available", 36, N.TEXT, 600))
            addView(msg, LinearLayout.LayoutParams(-1, -2).apply { topMargin = a.px(10); bottomMargin = a.px(32) })
            addView(LinearLayout(context).apply {
                noClip(); addView(later, LinearLayout.LayoutParams(0, a.px(68), 1f))
                addView(update, LinearLayout.LayoutParams(0, a.px(68), 1f).apply { marginStart = a.px(16) })
            })
        })
        d.window?.setLayout(a.px(820), ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show(); later.requestFocus()
    }

    /** Hands the APK to the system installer; the TV asks once to allow installs from Vistaar. Updates only apply over the same signing key. */
    private fun install(a: Activity, f: File) {
        val uri = FileProvider.getUriForFile(a, a.packageName + ".files", f)
        a.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
