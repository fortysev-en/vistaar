package com.vistaar.tv

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import org.json.JSONObject
import java.io.BufferedInputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors

/** A screen that can take text typed on the phone (search, playlist link). */
interface RemoteText { fun onRemoteText(s: String) }

/**
 * Phone remote: a tiny HTTP server on the home network that serves a page with arrow keys and a text box.
 * Off by default. A random 4-digit PIN shown on the TV is needed first (5 wrong tries lock it for a minute), only private network
 * addresses are served, and it switches itself off after 15 minutes without a request. Plain HTTP: it never leaves the home network.
 */
object RemoteHub {
    private const val IDLE_MS = 15 * 60_000L
    private const val LOCK_MS = 60_000L
    private const val MAX_BODY = 16_384 // a key press or a pasted link; nothing here needs more
    private val main = Handler(Looper.getMainLooper())
    private val keys = Executors.newSingleThreadExecutor() // key injection waits for the event to be handled, so never on the main thread
    private val pool = Executors.newCachedThreadPool()
    private val rng = SecureRandom()

    @Volatile private var server: ServerSocket? = null
    @Volatile private var current: Activity? = null
    private val sessions = HashSet<String>()
    private var fails = 0
    private var lockedUntil = 0L
    @Volatile private var lastRequest = 0L
    private var page = ""
    private lateinit var app: Application

    var pin = ""; private set
    var port = 0; private set
    var address = ""; private set
    var onChange: (() -> Unit)? = null
    val running get() = server != null
    @Volatile private var lastAuthed = 0L
    /** A phone is connected while its page keeps asking (it checks in every few seconds). */
    val connected get() = running && System.currentTimeMillis() - lastAuthed < 15_000
    val phones get() = synchronized(sessions) { sessions.size }
    val url get() = "http://$address:$port"

    /** Call once from [App]: remembers the visible screen so key presses and text go to it. */
    fun track(a: Application) {
        app = a
        a.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(x: Activity) { current = x; StatusOverlay.onResume(x) }
            override fun onActivityPaused(x: Activity) { if (current === x) current = null; StatusOverlay.onPause(x) }
            override fun onActivityCreated(x: Activity, b: Bundle?) {}
            override fun onActivityStarted(x: Activity) {}
            override fun onActivityStopped(x: Activity) {}
            override fun onActivitySaveInstanceState(x: Activity, b: Bundle) {}
            override fun onActivityDestroyed(x: Activity) {}
        })
    }

    /** The TV's address on the home network (Wi-Fi or Ethernet), or null when it has none. */
    private fun lanAddress(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
    } catch (_: Exception) { null }

    /** Starts the server; returns an error message, or null when it is running. */
    fun start(c: Context): String? {
        if (running) return null
        val ip = lanAddress() ?: return "This TV isn't on a home network."
        val s = try { ServerSocket(8080) } catch (_: Exception) { try { ServerSocket(0) } catch (e: Exception) { return "Couldn't open a port (${e.message})." } }
        page = c.assets.open("remote.html").bufferedReader().use { it.readText() }
        server = s; port = s.localPort; address = ip
        pin = "%04d".format(rng.nextInt(10_000)); fails = 0; lockedUntil = 0; synchronized(sessions) { sessions.clear() }
        lastRequest = System.currentTimeMillis()
        pool.execute { while (!s.isClosed) try { val sock = s.accept(); pool.execute { serve(sock) } } catch (_: Exception) { } }
        main.postDelayed(idle, 60_000)
        main.post { onChange?.invoke() }
        return null
    }

    fun stop() {
        server?.let { try { it.close() } catch (_: Exception) { } }
        server = null; main.removeCallbacks(idle); synchronized(sessions) { sessions.clear() }
        main.post { onChange?.invoke() }
    }

    private val idle = object : Runnable {
        override fun run() { if (!running) return; if (System.currentTimeMillis() - lastRequest > IDLE_MS) stop() else main.postDelayed(this, 60_000) }
    }

    // ---------------------------------------------------------------- HTTP

    private class Req(val method: String, val path: String, val headers: Map<String, String>, val body: String)

    private fun read(sock: Socket): Req? {
        val inp = BufferedInputStream(sock.getInputStream())
        val head = StringBuilder()
        while (head.length < 8192 && !head.endsWith("\r\n\r\n")) { val b = inp.read(); if (b < 0) return null; head.append(b.toChar()) }
        val lines = head.toString().split("\r\n")
        val first = lines[0].split(" "); if (first.size < 2) return null
        val h = lines.drop(1).filter { ':' in it }.associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
        val len = (h["content-length"]?.toIntOrNull() ?: 0).coerceIn(0, MAX_BODY)
        val body = ByteArray(len); var got = 0
        while (got < len) { val n = inp.read(body, got, len - got); if (n < 0) break; got += n }
        return Req(first[0], first[1].substringBefore('?'), h, String(body, 0, got, Charsets.UTF_8))
    }

    private fun reply(sock: Socket, code: Int, type: String, body: String) {
        val b = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\nContent-Type: $type\r\nContent-Length: ${b.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
        sock.getOutputStream().apply { write(head.toByteArray()); write(b); flush() }
    }

    private fun json(sock: Socket, code: Int, o: JSONObject) = reply(sock, code, "application/json; charset=utf-8", o.toString())

    private fun serve(sock: Socket) = sock.use { s ->
        try {
            s.soTimeout = 5000
            if (!(s.inetAddress.isSiteLocalAddress || s.inetAddress.isLoopbackAddress || s.inetAddress.isLinkLocalAddress)) return@use // home network only
            val r = read(s) ?: return@use
            lastRequest = System.currentTimeMillis()
            when {
                r.method == "GET" && r.path == "/" -> reply(s, 200, "text/html; charset=utf-8", page)
                r.method == "POST" && r.path == "/api/pin" -> pinLogin(s, r)
                r.method == "POST" && r.path.startsWith("/api/") -> {
                    val tok = r.headers["x-token"].orEmpty()
                    if (!synchronized(sessions) { tok in sessions }) return@use json(s, 401, JSONObject().put("error", "pin"))
                    lastAuthed = System.currentTimeMillis()
                    val o = try { JSONObject(r.body.ifEmpty { "{}" }) } catch (_: Exception) { JSONObject() }
                    when (r.path) {
                        "/api/key" -> { press(o.optString("key")); json(s, 200, JSONObject().put("ok", true)) }
                        "/api/text" -> { text(o.optString("text").take(500)); json(s, 200, JSONObject().put("ok", true)) }
                        "/api/state" -> json(s, 200, JSONObject().put("ok", true).put("text", current is RemoteText))
                        else -> json(s, 404, JSONObject().put("error", "not found"))
                    }
                }
                else -> reply(s, 404, "text/plain", "Not found")
            }
        } catch (_: Exception) { }
    }

    private fun pinLogin(s: Socket, r: Req) {
        val now = System.currentTimeMillis()
        if (now < lockedUntil) return json(s, 429, JSONObject().put("error", "locked").put("wait", (lockedUntil - now) / 1000 + 1))
        val given = try { JSONObject(r.body).optString("pin") } catch (_: Exception) { "" }
        if (MessageDigest.isEqual(given.toByteArray(), pin.toByteArray())) {
            fails = 0
            val tok = ByteArray(16).also { rng.nextBytes(it) }.joinToString("") { "%02x".format(it) }
            synchronized(sessions) { sessions.add(tok) }; lastAuthed = System.currentTimeMillis()
            main.post { onChange?.invoke() }
            json(s, 200, JSONObject().put("token", tok))
        } else {
            if (++fails >= 5) { fails = 0; lockedUntil = now + LOCK_MS }
            json(s, 401, JSONObject().put("error", "pin"))
        }
    }

    // ---------------------------------------------------------------- actions

    private fun press(name: String) {
        if (name == "home") { main.post { current?.let { it.startActivity(Intent(it, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)) } }; return }
        val code = when (name) {
            "up" -> KeyEvent.KEYCODE_DPAD_UP; "down" -> KeyEvent.KEYCODE_DPAD_DOWN; "left" -> KeyEvent.KEYCODE_DPAD_LEFT; "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "ok" -> KeyEvent.KEYCODE_DPAD_CENTER; "back" -> KeyEvent.KEYCODE_BACK; "menu" -> KeyEvent.KEYCODE_MENU
            "chup" -> KeyEvent.KEYCODE_CHANNEL_UP; "chdown" -> KeyEvent.KEYCODE_CHANNEL_DOWN
            else -> return
        }
        keys.execute { try { Instrumentation().sendKeyDownUpSync(code) } catch (_: Exception) { } }
    }

    /** Runs [f] on the main thread and waits for it: the catalogue is main-thread only. */
    private fun <T> onMain(f: () -> T): T {
        var out: Any? = null; var err: Throwable? = null
        val done = java.util.concurrent.CountDownLatch(1)
        main.post { try { out = f() } catch (e: Throwable) { err = e } finally { done.countDown() } }
        done.await(5, java.util.concurrent.TimeUnit.SECONDS)
        err?.let { throw it }
        @Suppress("UNCHECKED_CAST") return out as T
    }

    private fun text(s: String) { main.post { (current as? RemoteText)?.onRemoteText(s) } }
}

/** Application class: lets [RemoteHub] know which screen is in front. */
class App : Application() {
    override fun onCreate() { super.onCreate(); RemoteHub.track(this) }
}
