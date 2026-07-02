package systems.formula.ventura.feed

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import ventura.feed.v1.Feed

/**
 * Pure rendering. Reads [FeedState] from the ViewModel and renders. The shared
 * [VideoPlayerPool] and [FeedPreloader] are provided from AppContainer (never
 * created inside a Composable) and passed in.
 */
@Composable
fun FeedScreen(
    feedViewModel: FeedViewModel,
    playerPool: VideoPlayerPool,
    preloader: FeedPreloader,
) {
    val state by feedViewModel.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is FeedState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is FeedState.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: ${s.message}", color = Color.Red)
            }
        }
        is FeedState.Success -> {
            FeedContent(posts = s.posts, playerPool = playerPool, preloader = preloader)
        }
    }
}

@Composable
private fun FeedContent(
    posts: List<Feed.Post>,
    playerPool: VideoPlayerPool,
    preloader: FeedPreloader,
) {
    // Register the loaded feed for prefetch, ranked by scroll proximity.
    LaunchedEffect(posts) {
        preloader.setPosts(posts)
        preloader.setCurrentIndex(0)
    }

    val currentPost = posts.firstOrNull() ?: return
    VideoFeedItem(post = currentPost, playerPool = playerPool)
}

/**
 * A single feed item: acquires a pooled player, plays [post], and overlays the
 * thumbnail poster until the real first frame renders — then crossfades it out to
 * kill the black flash. Poster only works paired with prefetch (see [FeedPreloader]).
 */
@Composable
private fun VideoFeedItem(post: Feed.Post, playerPool: VideoPlayerPool) {
    val context = LocalContext.current
    var isFirstFrameRendered by remember(post.id) { mutableStateOf(false) }

    // Acquire a player owned by this item for the duration it is on screen.
    val player: ExoPlayer? = remember(post.id) { playerPool.acquire(post.id) }

    DisposableEffect(post.id) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                isFirstFrameRendered = true
            }
        }
        player?.apply {
            addListener(listener)
            setMediaItem(MediaItem.fromUri(post.videoUrl))
            prepare()
            playWhenReady = true
        }
        onDispose {
            player?.removeListener(listener)
            playerPool.release(post.id)
        }
    }

    val posterAlpha by animateFloatAsState(
        targetValue = if (isFirstFrameRendered) 0f else 1f,
        label = "posterAlpha",
    )

    Box(Modifier.fillMaxSize()) {
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false // TikTok-style — no playback controls
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        AsyncImage(
            model = post.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(posterAlpha)
        )
    }
}
