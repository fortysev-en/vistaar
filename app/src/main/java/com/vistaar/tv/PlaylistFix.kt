package com.vistaar.tv

import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParserFactory
import androidx.media3.exoplayer.upstream.ParsingLoadable
import android.net.Uri
import java.io.InputStream

/**
 * Jio's master playlists advertise I-frame (trick-play) playlists that the CDN answers with 403.
 * Media3 loads them and fails playback, so drop those lines before parsing.
 */
class NoIFramePlaylistParserFactory : HlsPlaylistParserFactory {
    private class Stripper(private val inner: ParsingLoadable.Parser<HlsPlaylist>) : ParsingLoadable.Parser<HlsPlaylist> {
        override fun parse(uri: Uri, input: InputStream): HlsPlaylist {
            val text = input.readBytes().toString(Charsets.UTF_8)
                .lineSequence().filterNot { it.startsWith("#EXT-X-I-FRAME-STREAM-INF") }.joinToString("\n")
            return inner.parse(uri, text.byteInputStream())
        }
    }

    override fun createPlaylistParser(): ParsingLoadable.Parser<HlsPlaylist> = Stripper(HlsPlaylistParser())

    override fun createPlaylistParser(
        multivariantPlaylist: HlsMultivariantPlaylist, previousMediaPlaylist: HlsMediaPlaylist?
    ): ParsingLoadable.Parser<HlsPlaylist> = Stripper(HlsPlaylistParser(multivariantPlaylist, previousMediaPlaylist))
}
