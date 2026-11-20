package com.vistaar.tv

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource

/** How a channel's stream is looked up and played; shared by the full player and the Live/Free TV preview so the rules stay in one place. */
object Streams {
    /** Stream URL + guide for a channel, resolved ahead of time. */
    class Resolved(val cur: Program?, val nxt: Program?, val play: Play, val at: Long)

    /** Looks up the guide and stream URL for [ch]. Blocking: call off the main thread. */
    fun resolve(ch: Channel): Resolved {
        if (ch.url.isNotEmpty()) return Resolved(null, null, Play(ch.url, ch.url.substringBefore('?').endsWith(".mpd"), ""), System.currentTimeMillis())
        val epg = Api.epg(ch.id)
        val t = System.currentTimeMillis()
        val cur = epg.firstOrNull { t in it.start..it.end }
        val nxt = cur?.let { c -> epg.firstOrNull { it.start >= c.end } }
        return Resolved(cur, nxt, Api.streamUrl(ch, cur), System.currentTimeMillis())
    }

    /** Free (third-party) streams get their own user agent and referrer and never see Jio tokens; Jio streams get the app's headers. */
    fun source(ch: Channel, play: Play, srno: Long): MediaSource {
        // The CDN hands out its auth cookie on the master playlist; variants/segments need it replayed.
        if (java.net.CookieHandler.getDefault() == null) java.net.CookieHandler.setDefault(java.net.CookieManager())
        val free = ch.url.isNotEmpty()
        val headers = if (free) (if (ch.ref.isEmpty()) emptyMap<String, String>() else mapOf("Referer" to ch.ref)) else Api.authHeaders(ch.id, srno)
        val ds = DefaultHttpDataSource.Factory()
            .setUserAgent(if (!free) Api.UA else ch.ua.ifEmpty { "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36" })
            .setAllowCrossProtocolRedirects(true).setDefaultRequestProperties(headers)
        return if (play.dash) DashMediaSource.Factory(ds).createMediaSource(
            MediaItem.Builder().setUri(play.url).setMimeType(MimeTypes.APPLICATION_MPD)
                .apply {
                    if (play.license.isNotEmpty()) setDrmConfiguration(MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                        .setLicenseUri(play.license).setMultiSession(true).setLicenseRequestHeaders(headers).build())
                }.build())
        else HlsMediaSource.Factory(ds).setPlaylistParserFactory(NoIFramePlaylistParserFactory())
            .setAllowChunklessPreparation(true) // skip fetching a media playlist just to learn the track list
            .createMediaSource(MediaItem.fromUri(play.url))
    }
}
