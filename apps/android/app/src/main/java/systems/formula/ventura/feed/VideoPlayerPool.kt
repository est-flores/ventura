package systems.formula.ventura.feed

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl

/**
 * Pool sizing constants. These stay hardcoded — do not make them
 * remote-configurable until real device telemetry (TTFF, rebuffer ratio) shows a
 * concrete need to tune them.
 */
object VideoPlayerPoolConfig {
    const val PRELOAD_COUNT = 2      // clips prefetched ahead of current position
    const val POOL_SIZE = 5          // (PRELOAD_COUNT * 2) + 1 — both scroll directions + current
}

/**
 * Reuses a small fleet of [ExoPlayer] instances instead of allocating one per
 * feed item. A player is owned by exactly one feed item at a time (keyed by post
 * id) and is fully reset before being handed to another item.
 *
 * Provide this once via AppContainer — never create a pool inside a Composable.
 */
@OptIn(UnstableApi::class)
class VideoPlayerPool(
    private val context: Context,
    size: Int = VideoPlayerPoolConfig.POOL_SIZE,
) {
    private val available = ArrayDeque<ExoPlayer>().apply {
        repeat(size) { add(createPlayer()) }
    }
    private val inUse = mutableMapOf<String, ExoPlayer>() // keyed by post id

    private fun createPlayer(): ExoPlayer =
        ExoPlayer.Builder(context)
            .setLoadControl(feedLoadControl())
            .build()

    fun acquire(postId: String): ExoPlayer? {
        inUse[postId]?.let { return it }
        val player = available.removeFirstOrNull() ?: return null
        inUse[postId] = player
        return player
    }

    fun release(postId: String) {
        inUse.remove(postId)?.let { player ->
            player.stop()
            player.clearMediaItems()
            available.addLast(player)
        }
    }

    /** Fully tear down every pooled player. Call from AppContainer lifecycle end. */
    fun releaseAll() {
        (available + inUse.values).forEach { it.release() }
        available.clear()
        inUse.clear()
    }

    /**
     * Short clips (~15s) need aggressive, low-latency buffering — not the
     * defaults tuned for long-form video.
     */
    private fun feedLoadControl(): LoadControl =
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 2_000,
                /* maxBufferMs = */ 10_000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1_000
            )
            .build()
}
