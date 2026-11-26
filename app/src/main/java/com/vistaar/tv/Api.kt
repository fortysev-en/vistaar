package com.vistaar.tv

import android.content.Context
import android.os.Build
import android.util.Base64
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** [url] is set only for free channels (negative id): their stream is the playlist URL itself, with an optional UA / referrer. */
data class Channel(val id: Long, val name: String, val order: Int, val lang: Int, val hd: Boolean, val logo: String,
                   val url: String = "", val ua: String = "", val ref: String = "", val cat: Int = 0,
                   /** Jio marks channels outside the base pack (`is_premium`); shown with a crown. */
                   val premium: Boolean = false)

/** Genre ids as used by JioTV's channel list (`channelCategoryId`); names from public knowledge, checked against sample channels. */
val GENRES = mapOf(5 to "Entertainment", 6 to "Movies", 12 to "News", 13 to "Music", 7 to "Kids", 8 to "Sports", 15 to "Devotional",
    17 to "Education", 10 to "Knowledge", 9 to "Lifestyle", 16 to "Business", 18 to "Shopping")

/** Language ids as used by JioTV's channel list. Names are from public knowledge of the service, not the APK: unknown ids fall back to "Other". */
val LANGUAGES = mapOf(1 to "Hindi", 2 to "Marathi", 3 to "Punjabi", 4 to "Urdu", 5 to "Bengali", 6 to "English", 7 to "Malayalam",
    8 to "Tamil", 9 to "Gujarati", 10 to "Odia", 11 to "Telugu", 12 to "Bhojpuri", 13 to "Kannada", 14 to "Assamese",
    15 to "Nepali", 16 to "French")
/** ISO 639 codes for the language ids above, used to prefer the channel's own audio track. */
val LANG_CODES = mapOf(1 to "hi", 2 to "mr", 3 to "pa", 4 to "ur", 5 to "bn", 6 to "en", 7 to "ml", 8 to "ta", 9 to "gu", 10 to "or", 11 to "te",
    12 to "bho", 13 to "kn", 14 to "as", 15 to "ne", 16 to "fr")
data class Program(val srno: Long, val title: String, val showtime: String, val start: Long, val end: Long, val desc: String = "")

data class Play(val url: String, val dash: Boolean, val license: String)

class ApiException(msg: String) : Exception(msg)

/** Endpoints and headers recovered from JioTV 7.1.8 (see the project README). */
object Api {
    val UA = "plaYtv/7.1.8 (Linux;Android ${Build.VERSION.RELEASE}) ExoPlayerLib/1.3.1"
    private const val APP_KEY = "NzNiMDhlYzQyNjJm"
    private const val APP_NAME = "RJIL_JioTV"
    private const val USER_GROUP = "tvYR7NSNn7rymo3F"
    private const val LOGIN = "https://jiotvapi.media.jio.com/userservice/apis/"
    private const val LISTS = "https://jiotvapi.cdn.jio.com/apis/"
    private const val PLAYBACK = "https://jiotvapi.media.jio.com/playback/apis/"
    private const val TOKENS = "https://auth.media.jio.com/tokenservice/apis/"
    private val JSON = "application/json".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    private fun call(req: Request): Pair<Int, String> =
        http.newCall(req).execute().use { it.code to (it.body?.string() ?: "") }

    /** Plain GET with no Jio headers or tokens, for third-party playlists. */
    fun text(url: String): String {
        val (code, text) = call(Request.Builder().url(url).header("User-Agent", "Vistaar/0.1").build())
        if (code !in 200..299) throw ApiException("HTTP $code")
        return text
    }

    /** Streams [url] to [out], reporting 0-100 when the size is known. Used by the updater. */
    fun download(url: String, out: java.io.File, progress: (Int) -> Unit) {
        http.newCall(Request.Builder().url(url).header("User-Agent", "Vistaar").build()).execute().use { r ->
            if (!r.isSuccessful) throw ApiException("HTTP ${r.code}")
            val body = r.body ?: throw ApiException("Empty download")
            val len = body.contentLength(); var done = 0L; var last = -1
            body.byteStream().use { i -> out.outputStream().use { o ->
                val buf = ByteArray(65536); var n: Int
                while (i.read(buf).also { n = it } >= 0) { o.write(buf, 0, n); done += n; if (len > 0) (done * 100 / len).toInt().let { if (it != last) { last = it; progress(it) } } }
            } }
        }
    }

    private fun loginHeaders(b: Request.Builder) = b
        .header("Content-Type", "application/json")
        .header("os", "android").header("devicetype", "phone")
        .header("appName", APP_NAME).header("appkey", APP_KEY)
        .header("deviceId", Store.deviceId).header("versionCode", "424")
        .header("x-platform", "androidmobile").header("osVersion", Build.VERSION.RELEASE)

    private fun num(phone: String) =
        Base64.encodeToString("+91$phone".toByteArray(), Base64.NO_WRAP)

    /** Returns null on success, otherwise a message for the user. */
    fun sendOtp(phone: String): String? {
        val body = JSONObject().put("number", num(phone)).toString().toRequestBody(JSON)
        val (code, text) = call(loginHeaders(Request.Builder().url(LOGIN + "v1/loginotp/send").post(body)).build())
        return if (code in 200..299) null else "Send OTP failed ($code): ${text.take(160)}"
    }

    fun verifyOtp(phone: String, otp: String): String? {
        val dev = JSONObject().put("name", Build.PRODUCT)
        val info = JSONObject().put("type", "android").put("platform", dev).put("androidId", Store.deviceId)
        val body = JSONObject().put("number", num(phone)).put("otp", otp)
            .put("deviceInfo", JSONObject().put("consumptionDeviceName", Build.BRAND + " " + Build.MODEL).put("info", info))
            .toString().toRequestBody(JSON)
        val (code, text) = call(loginHeaders(Request.Builder().url(LOGIN + "v2/loginotp/verify").post(body)).build())
        if (code !in 200..299) return "Verify failed ($code): ${text.take(160)}"
        val root = JSONObject(text)
        val d = root.optJSONObject("data") ?: root // response is either wrapped in {data} or flat
        val temp = d.optString("tempToken")
        if (temp.isNotEmpty()) { // device limit: keep what the app keeps, so "log out other devices" can finish this sign-in
            val u = d.optJSONObject("sessionAttributes")?.optJSONObject("user")
            pending = Pending(temp, d.optString("lbCookie"), u?.optString("unique").orEmpty(), u?.optString("subscriberId").orEmpty(), u?.optString("uid").orEmpty())
            return DEVICE_LIMIT
        }
        val user = d.optJSONObject("sessionAttributes")?.optJSONObject("user")
        if (d.optString("ssoToken").isEmpty() || user == null) return "Unexpected login response: ${text.take(160)}"
        Store.save(
            sso = d.optString("ssoToken"), auth = d.optString("authToken"), refresh = d.optString("refreshToken"),
            lb = d.optString("lbCookie"), uniqueId = user.optString("unique"), subscriberId = user.optString("subscriberId"),
            uid = user.optString("uid")
        )
        return null
    }

    const val DEVICE_LIMIT = "device_limit"
    private class Pending(val temp: String, val lb: String, val unique: String, val sub: String, val uid: String)
    @Volatile private var pending: Pending? = null

    /**
     * Device limit reached: logs the account out of every device (the JioTV app's own "log out other devices" step) using the
     * temporary token from the failed sign-in, which also returns fresh tokens. Returns null when signed in, otherwise a message.
     */
    fun expireAllUsers(): String? {
        val p = pending ?: return "That sign-in expired. Go back and request a new code."
        val body = JSONObject().put("appName", APP_NAME).put("deviceId", Store.deviceId).toString().toRequestBody(JSON)
        val (code, text) = call(Request.Builder().url(TOKENS + "v2/expireallusers").post(body)
            .header("Content-Type", "application/json").header("x-platform", "androidmobile").header("temptoken", p.temp).build())
        android.util.Log.d("Vistaar", "expireallusers -> $code")
        val root = try { JSONObject(text) } catch (e: Exception) { return "Unexpected reply from Jio ($code)." }
        val d = root.optJSONObject("data") ?: root
        val sso = d.optString("ssoToken"); val auth = d.optString("authToken"); val refresh = d.optString("refreshToken")
        if (code !in 200..299 || sso.isEmpty() || auth.isEmpty()) return root.optString("message").ifEmpty { "Could not log out the other devices ($code)." }.take(120)
        pending = null
        if (p.unique.isEmpty() || p.sub.isEmpty()) return "Other devices were logged out. Send a new code to sign in." // slot is free now, so a normal sign-in works
        Store.save(sso, auth, refresh, p.lb, p.unique, p.sub, p.uid)
        return null
    }

    /** Tells Jio this TV is signed out so it stops counting against the device limit. Best effort: short timeout, errors ignored. */
    fun logoutRemote() {
        if (Store.refresh.isEmpty()) return
        try {
            val body = JSONObject().put("appName", APP_NAME).put("deviceId", Store.deviceId).put("refreshToken", Store.refresh).toString().toRequestBody(JSON)
            val req = Request.Builder().url(TOKENS + "v1/logout").post(body)
                .header("Content-Type", "application/json").header("accesstoken", Store.auth).header("ssotoken", Store.sso).header("devicetype", "phone")
                .header("versionCode", "424").header("os", "android").header("uniqueId", Store.uniqueId).build()
            val c = http.newBuilder().callTimeout(6, TimeUnit.SECONDS).build().newCall(req).execute().use { it.code }
            android.util.Log.d("Vistaar", "logout -> $c")
        } catch (e: Exception) { android.util.Log.d("Vistaar", "logout failed: $e") }
    }

    /** Headers the app sends on playback calls and on every HLS segment request. */
    fun authHeaders(channelId: Long = 0, srno: Long = 0): Map<String, String> = mapOf(
        "uniqueId" to Store.uniqueId, "ssotoken" to Store.sso, "accesstoken" to Store.auth,
        "subscriberId" to Store.subscriberId, "deviceId" to Store.deviceId, "userId" to Store.uid,
        "os" to "android", "devicetype" to "phone", "appkey" to APP_KEY, "appName" to APP_NAME,
        "versioncode" to "424", "versionCode" to "424", "osVersion" to Build.VERSION.RELEASE,
        "x-platform" to "android", "usergroup" to USER_GROUP, "lbcookie" to Store.lb.ifEmpty { "1" },
        "channelid" to channelId.toString(), "srno" to srno.toString(), "crmid" to Store.subscriberId
    )

    private val listHeaders = mapOf(
        "usergroup" to USER_GROUP, "usertype" to "jio", "appkey" to APP_KEY, "devicetype" to "phone",
        "os" to "android", "deviceId" to "55a8a2b6299fb995", "appversioncode" to "404",
        "appversion" to "7.1.7.P.20260225.1", "lbcookie" to "1"
    )

    private fun get(url: String): String {
        val b = Request.Builder().url(url)
        listHeaders.forEach { (k, v) -> b.header(k, v) }
        val (code, text) = call(b.build())
        if (code !in 200..299) throw ApiException("HTTP $code: ${text.take(120)}")
        return text
    }

    fun channels(): List<Channel> {
        val text = get(LISTS + "v3.1/getMobileChannelList/get/?langId=6&os=android&devicetype=phone&usertype=jio&version=404")
        val arr = JSONObject(text).optJSONArray("result") ?: throw ApiException("No channel list in response")
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { !it.optBoolean("isHidden") }
            .map { Channel(it.optLong("channel_id"), it.optString("channel_name"), it.optInt("channel_order", Int.MAX_VALUE),
                it.optInt("channelLanguageId"), it.optBoolean("isHD"),
                it.optString("logoUrl").let { l -> if (l.isEmpty()) "" else "https://jiotv.catchup.cdn.jio.com/dare_images/images/$l" },
                cat = it.optInt("channelCategoryId"), premium = it.optBoolean("is_premium")) }
            .filter { it.id != 0L }.sortedBy { it.order }
    }

    /** Today's programmes by start time; empty if the EPG is unavailable (playback works without it). */
    /** Programmes of one day by start time: [offset] 0 is today, 1 tomorrow, -1 yesterday. Empty if the guide is unavailable. */
    fun epg(channelId: Long, offset: Int = 0): List<Program> = try {
        val arr = JSONObject(get(LISTS + "v1.3/getepg/get?offset=$offset&channel_id=$channelId&langId=6")).optJSONArray("epg")
        fun ms(v: Long) = if (v < 100_000_000_000L) v * 1000 else v
        (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }
            .map { Program(it.optLong("srno"), it.optString("showname"), it.optString("showtime"), ms(it.optLong("startEpoch")), ms(it.optLong("endEpoch")), it.optString("description")) }
            .sortedBy { it.start }
    } catch (e: Exception) { emptyList() }

    fun nowPlaying(channelId: Long): Program? {
        val now = System.currentTimeMillis()
        return epg(channelId).firstOrNull { now in it.start..it.end }
    }

    /** Headers of the player SDK's geturl call (com.jio.jioplayer.h.a, case 0) — differs from the segment headers above. */
    private fun sdkHeaders(channelId: Long) = mapOf(
        "appkey" to APP_KEY, "devicetype" to "phone", "os" to "android", "deviceId" to Store.deviceId,
        "versionCode" to "424", "osVersion" to Build.VERSION.RELEASE, "isott" to "false",
        "dm" to Build.MANUFACTURER + " " + Build.MODEL, "x-platform" to "android_mobile",
        "uniqueId" to Store.uniqueId, "usergroup" to USER_GROUP, "languageId" to "6",
        "userId" to Store.subscriberId, "sid" to java.util.UUID.randomUUID().toString(), "crmid" to Store.subscriberId,
        "channel_id" to channelId.toString(), "langid" to "", "camid" to "",
        "accesstoken" to Store.auth, "ssotoken" to Store.sso, "subscriberId" to Store.subscriberId,
        "lbcookie" to Store.lb.ifEmpty { "1" }
    )

    /**
     * HTTP 419 means the access token expired. The app's interceptor answers it by trading the refresh token for a new
     * one (POST tokenservice v2/refreshtoken) and retrying; this does the same. [stale] is the token the failed call used,
     * so concurrent callers refresh once. Returns true if a newer token is now stored.
     */
    @Synchronized private fun refreshAuth(stale: String): Boolean {
        if (Store.auth != stale) return true
        if (Store.refresh.isEmpty()) return false
        val body = JSONObject().put("appName", APP_NAME).put("deviceId", Store.deviceId).put("refreshToken", Store.refresh)
            .toString().toRequestBody(JSON)
        val (code, text) = call(Request.Builder().url(TOKENS + "v2/refreshtoken").post(body)
            .header("Content-Type", "application/json").header("accesstoken", Store.auth).header("devicetype", "phone")
            .header("versionCode", "424").header("os", "android").header("uniqueId", Store.uniqueId).build())
        android.util.Log.d("Vistaar", "refreshtoken -> $code")
        if (code !in 200..299) return false
        val root = try { JSONObject(text) } catch (e: Exception) { return false }
        val auth = (root.optJSONObject("data") ?: root).optString("authToken")
        if (auth.isEmpty()) return false
        Store.setAuth(auth)
        return true
    }

    /** The real app calls this after login/launch; it registers the session server-side before any playback. */
    fun beginSession() {
        fun go(): Int {
            val b = Request.Builder().url("https://tv.media.jio.com/apis/v3.0/beginsession/begin?langId=6")
            (authHeaders() + mapOf("userId" to Store.subscriberId, "isott" to "false")).forEach { (k, v) -> b.header(k, v) }
            return call(b.build()).first
        }
        val stale = Store.auth
        var code = go()
        if (code == 419 && refreshAuth(stale)) code = go()
        android.util.Log.d("Vistaar", "beginsession -> $code")
    }

    fun streamUrl(ch: Channel, p: Program?): Play {
        val srno = p?.srno ?: 0
        val form = FormBody.Builder()
            .add("stream_type", "Seek").add("channel_id", ch.id.toString())
            .add("programId", srno.toString()).add("showtime", (p?.showtime ?: "000000").replace(":", ""))
            .add("srno", "20" + srno.toString().dropLast(6)).add("begin", "").add("end", "").build()
        fun go(): Pair<Int, String> {
            val b = Request.Builder().url(PLAYBACK + "v1.1/geturl?langId=6").post(form)
            sdkHeaders(ch.id).forEach { (k, v) -> b.header(k, v) }
            return call(b.build())
        }
        val stale = Store.auth
        var (code, text) = go()
        if (code == 419 && refreshAuth(stale)) { val r = go(); code = r.first; text = r.second }
        android.util.Log.d("Vistaar", "geturl ch=${ch.id} -> $code") // never log the body: it carries the stream token
        if (code !in 200..299) throw ApiException("Playback HTTP $code: ${text.take(120)}")
        val j = JSONObject(text)
        val mpd = j.optJSONObject("mpd")
        val url = j.optString("result").ifEmpty { mpd?.optString("result") ?: "" }
        if (url.isEmpty()) throw ApiException("No stream URL (code ${j.optInt("code")}: ${j.optString("message")})")
        val dash = url.substringBefore('?').endsWith(".mpd")
        val license = mpd?.optString("key").orEmpty()
        if (dash && license.isEmpty()) throw ApiException("DASH stream without licence URL")
        return Play(url, dash, license)
    }
}

/** Session tokens persisted across launches. */
object Store {
    private lateinit var sp: android.content.SharedPreferences
    fun init(c: Context) { sp = c.getSharedPreferences("vistaar", Context.MODE_PRIVATE); Free.dir = c.cacheDir }
    private fun s(k: String) = sp.getString(k, "") ?: ""
    val deviceId: String get() = s("deviceId").ifEmpty {
        java.util.UUID.randomUUID().toString().replace("-", "").take(16).also { sp.edit().putString("deviceId", it).apply() }
    }
    val sso get() = s("sso"); val auth get() = s("auth"); val lb get() = s("lb"); val refresh get() = s("refresh")
    fun setAuth(a: String) { sp.edit().putString("auth", a).apply() }
    val uniqueId get() = s("uniqueId"); val subscriberId get() = s("subscriberId"); val uid get() = s("uid")
    val loggedIn get() = sso.isNotEmpty()
    /** "Continue with free channels": no Jio sign-in, only the free playlist. */
    var freeOnly: Boolean get() = sp.getBoolean("freeOnly", false); set(v) { sp.edit().putBoolean("freeOnly", v).apply() }
    /** Version of the disclaimer the user accepted (0 = never); see DisclaimerActivity.VERSION. */
    var disclaimerV: Int get() = sp.getInt("disclaimerV", 0); set(v) { sp.edit().putInt("disclaimerV", v).apply() }
    val hasAccess get() = loggedIn || freeOnly
    /** Chosen Jio language ids. [langsDone] with an empty set means "all languages". */
    val langs: Set<Int> get() = s("langs").split(",").mapNotNull { it.toIntOrNull() }.toSet()
    val langsDone get() = sp.getBoolean("langsDone", false)
    /** Name of the header tab the app opens on (Settings, Startup). */
    var startTab: String
        get() = s("startTab").ifEmpty { "Home" }
        set(v) { sp.edit().putString("startTab", v).apply() }
    /** Favourite categories (Jio genre ids), most wanted first: Home lists its rows in this order. */
    val genres: List<Int> get() = s("genres").split(",").mapNotNull { it.toIntOrNull() }.filter { it in GENRES }
    fun setGenres(l: Collection<Int>) { sp.edit().putString("genres", l.joinToString(",")).apply() }
    fun setLangs(l: Set<Int>) { sp.edit().putString("langs", l.joinToString(",")).putBoolean("langsDone", true).apply() }
    var playlistUrl: String get() = s("playlist").ifEmpty { Free.DEFAULT }; set(v) { sp.edit().putString("playlist", v).apply() }
    fun audioPref(id: Long) = s("aud.$id")
    fun setAudioPref(id: Long, v: String) { sp.edit().putString("aud.$id", v).apply() }
    fun clearAudioPrefs() { val e = sp.edit(); sp.all.keys.filter { it.startsWith("aud.") }.forEach { e.remove(it) }; e.apply() }
    fun save(sso: String, auth: String, refresh: String, lb: String, uniqueId: String, subscriberId: String, uid: String) {
        sp.edit().putString("sso", sso).putString("auth", auth).putString("refresh", refresh).putString("lb", lb)
            .putString("uniqueId", uniqueId).putString("subscriberId", subscriberId).putString("uid", uid).apply()
    }
    fun clear() { sp.edit().remove("sso").remove("auth").remove("refresh").apply() }
    /** Fire-and-forget access to the shared prefs for the catalogue (cache, favourites). */
    val prefs get() = sp
}
