package com.vistaar.tv

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import java.util.concurrent.Executors

/**
 * Muted, low-resolution live picture for the preview panel. Starts after the focus has rested on a channel for a moment,
 * stays invisible until the first frame, and falls back to the artwork on any error. Release it in onPause: many TVs have one decoder.
 */
class PreviewPlayer(private val a: Activity) {
    val view = AspectRatioFrameLayout(a).apply { resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; alpha = 0f }
    private val tv = TextureView(a).also { view.addView(it) }
    private val ui = Handler(Looper.getMainLooper())
    private val pool = Executors.newSingleThreadExecutor()
    private var player: ExoPlayer? = null
    private var gen = 0
    private var wait: Runnable? = null
    private var current: Channel? = null
    private var live = 0 // generation of the channel now loaded; the listener outlives single channels

    /** Shows [ch] after [DWELL_MS] unless another call comes first. */
    fun show(ch: Channel) {
        stop()
        val g = gen
        wait = Runnable {
            pool.execute {
                val r = try { Streams.resolve(ch) } catch (_: Exception) { return@execute }
                a.runOnUiThread { if (g == gen && !a.isFinishing) begin(ch, r, g) }
            }
        }.also { ui.postDelayed(it, DWELL_MS) }
    }

    private fun begin(ch: Channel, r: Streams.Resolved, g: Int) {
        val p = player ?: ExoPlayer.Builder(a)
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(2_000, 8_000, 500, 1_000).build()).build().also { np ->
                player = np; np.volume = 0f; np.setVideoTextureView(tv)
                np.trackSelectionParameters = np.trackSelectionParameters.buildUpon().setMaxVideoSize(854, 480).build()
                np.addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() { android.util.Log.d("Vistaar", "preview ok: ${current?.name}"); if (live == gen) view.animate().alpha(1f).setDuration(200).start() }
                    override fun onVideoSizeChanged(v: VideoSize) { if (v.height > 0) view.setAspectRatio(v.width * v.pixelWidthHeightRatio / v.height) }
                    override fun onPlayerError(e: PlaybackException) { android.util.Log.d("Vistaar", "preview error ${e.errorCodeName} ${(e.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode ?: ""}: ${current?.name} (${current?.id})"); stop() }
                })
            }
        current = ch; live = g; view.setAspectRatio(16f / 9f)
        p.setMediaSource(Streams.source(ch, r.play, r.cur?.srno ?: 0)); p.prepare(); p.playWhenReady = true
    }

    /** Hides the picture and halts playback but keeps the player for the next channel. */
    fun stop() {
        gen++
        wait?.let { ui.removeCallbacks(it) }; wait = null
        view.animate().cancel(); view.alpha = 0f
        player?.run { stop(); clearMediaItems() }
    }

    fun release() { stop(); player?.release(); player = null }

    private companion object { const val DWELL_MS = 800L }
}
