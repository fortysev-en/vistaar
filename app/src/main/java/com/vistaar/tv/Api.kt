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

data class Channel(val id: Long, val name: String, val order: Int, val lang: Int, val hd: Boolean, val logo: String)

/** Language ids as used by JioTV's channel list. Names are from public knowledge of the service, not the APK: unknown ids fall back to "Other". */
val LANGUAGES = mapOf(1 to "Hindi", 2 to "Marathi", 3 to "Punjabi", 4 to "Urdu", 5 to "Bengali", 6 to "English", 7 to "Malayalam",
    8 to "Tamil", 9 to "Gujarati", 10 to "Odia", 11 to "Telugu", 12 to "Bhojpuri", 13 to "Kannada", 14 to "Assamese",
    15 to "Nepali", 16 to "French")
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
    private val JSON = "application/json".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    private fun call(req: Request): Pair<Int, String> =
        http.newCall(req).execute().use { it.code to (it.body?.string() ?: "") }

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
        if (d.optString("tempToken").isNotEmpty()) return "Device limit reached: log out of another device in the JioTV app, then retry."
        val user = d.optJSONObject("sessionAttributes")?.optJSONObject("user")
        if (d.optString("ssoToken").isEmpty() || user == null) return "Unexpected login response: ${text.take(160)}"
        Store.save(
            sso = d.optString("ssoToken"), auth = d.optString("authToken"), refresh = d.optString("refreshToken"),
            lb = d.optString("lbCookie"), uniqueId = user.optString("unique"), subscriberId = user.optString("subscriberId"),
            uid = user.optString("uid")
        )
        return null
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
                it.optString("logoUrl").let { l -> if (l.isEmpty()) "" else "https://jiotv.catchup.cdn.jio.com/dare_images/images/$l" }) }
            .filter { it.id != 0L }.sortedBy { it.order }
    }

    /** Today's programmes by start time; empty if the EPG is unavailable (playback works without it). */
    fun epg(channelId: Long): List<Program> = try {
        val arr = JSONObject(get(LISTS + "v1.3/getepg/get?offset=0&channel_id=$channelId&langId=6")).optJSONArray("epg")
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

    /** The real app calls this after login/launch; it registers the session server-side before any playback. */
    fun beginSession() {
        val b = Request.Builder().url("https://tv.media.jio.com/apis/v3.0/beginsession/begin?langId=6")
        (authHeaders() + mapOf("userId" to Store.subscriberId, "isott" to "false")).forEach { (k, v) -> b.header(k, v) }
        val (code, text) = call(b.build())
        android.util.Log.d("Vistaar", "beginsession -> $code")
    }

    fun streamUrl(ch: Channel, p: Program?): Play {
        val srno = p?.srno ?: 0
        val form = FormBody.Builder()
            .add("stream_type", "Seek").add("channel_id", ch.id.toString())
            .add("programId", srno.toString()).add("showtime", (p?.showtime ?: "000000").replace(":", ""))
            .add("srno", "20" + srno.toString().dropLast(6)).add("begin", "").add("end", "").build()
        val b = Request.Builder().url(PLAYBACK + "v1.1/geturl?langId=6").post(form)
        sdkHeaders(ch.id).forEach { (k, v) -> b.header(k, v) }
        val (code, text) = call(b.build())
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
    fun init(c: Context) { sp = c.getSharedPreferences("vistaar", Context.MODE_PRIVATE) }
    private fun s(k: String) = sp.getString(k, "") ?: ""
    val deviceId: String get() = s("deviceId").ifEmpty {
        java.util.UUID.randomUUID().toString().replace("-", "").take(16).also { sp.edit().putString("deviceId", it).apply() }
    }
    val sso get() = s("sso"); val auth get() = s("auth"); val lb get() = s("lb")
    val uniqueId get() = s("uniqueId"); val subscriberId get() = s("subscriberId"); val uid get() = s("uid")
    val loggedIn get() = sso.isNotEmpty()
    fun save(sso: String, auth: String, refresh: String, lb: String, uniqueId: String, subscriberId: String, uid: String) {
        sp.edit().putString("sso", sso).putString("auth", auth).putString("refresh", refresh).putString("lb", lb)
            .putString("uniqueId", uniqueId).putString("subscriberId", subscriberId).putString("uid", uid).apply()
    }
    fun clear() { sp.edit().remove("sso").remove("auth").remove("refresh").apply() }
    /** Fire-and-forget access to the shared prefs for the catalogue (cache, favourites). */
    val prefs get() = sp
}
