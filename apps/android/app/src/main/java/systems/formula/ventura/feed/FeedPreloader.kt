package systems.formula.ventura.feed

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl
import ventura.feed.v1.Feed

/**
 * Scroll-driven prefetch on top of Media3's [DefaultPreloadManager]. This is the
 * dynamic-feed variant (not the sequential-playlist PreloadMediaSource approach):
 * the "next" item depends on user interaction, so we register posts ranked by
 * scroll proximity and update the current position as the feed moves.
 *
 * Favor loading a little of many items over fully buffering one — the point is
 * having several clips *decode-ready*, not fully downloaded.
 *
 * Provided once via AppContainer.
 */
@OptIn(UnstableApi::class)
class FeedPreloader(context: Context) {

    private val statusControl =
        TargetPreloadStatusControl<Int> {
            // Load only the first few seconds of each upcoming item.
            DefaultPreloadManager.Status(
                DefaultPreloadManager.Status.STAGE_LOADED_FOR_DURATION_MS,
                PRELOAD_DURATION_MS
            )
        }

    private val preloadManager =
        DefaultPreloadManager.Builder(context.applicationContext, statusControl).build()

    /** Register the loaded feed, ranked by index (scroll proximity). */
    fun setPosts(posts: List<Feed.Post>) {
        preloadManager.reset()
        posts.forEachIndexed { index, post ->
            preloadManager.add(MediaItem.fromUri(post.videoUrl), index)
        }
        preloadManager.invalidate()
    }

    /** Re-rank preload targets when the current playing position changes. */
    fun setCurrentIndex(index: Int) {
        preloadManager.setCurrentPlayingIndex(index)
        preloadManager.invalidate()
    }

    fun release() {
        preloadManager.release()
    }

    private companion object {
        const val PRELOAD_DURATION_MS = 3_000L
    }
}
