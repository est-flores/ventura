---
name: android-video-feed
description: >
  Use when implementing or modifying the video feed player, ExoPlayer/Media3 setup,
  player pooling, video prefetch, poster-overlay first-frame handling, or anything
  touching scroll-to-play performance. Triggers: "video player", "ExoPlayer",
  "Media3", "player pool", "prefetch video", "poster overlay", "black flash",
  "feed scroll", "TTFF", "first frame", "video feed performance".
---

# Video Feed: Player Pooling & Prefetch

This is the highest-leverage skill in the Android skill set. The feed's
scroll-to-play experience **is** Ventura's product thesis — get this pattern
right and the feed feels instant; get it wrong and no other optimization matters.

## The three techniques (all required together)

1. **Player pooling** — reuse a small fleet of `ExoPlayer` instances instead of
   allocating one per feed item
2. **Prefetch** — the next N clips are decode-ready before the user scrolls to them
3. **Poster-overlay first-frame handling** — eliminates the black flash between
   scroll and playback start

None of these alone produces the "TikTok feel" — they only work combined.

---

## Player pool sizing

```kotlin
object VideoPlayerPoolConfig {
    const val PRELOAD_COUNT = 2      // clips prefetched ahead of current position
    const val POOL_SIZE = 5          // (PRELOAD_COUNT * 2) + 1 — covers both scroll directions + current
}
```

**The sizing rule:** pool size must be at least `(preloadCount * 2) + 1` — enough
players to cover preloading in both scroll directions plus the currently active item.
With `PRELOAD_COUNT = 2`, that's a minimum pool of 5.

These start as hardcoded constants. Do not make them remote-configurable until
real device telemetry (TTFF, rebuffer ratio) shows a concrete need to tune them —
premature tunability is complexity without evidence it helps.

---

## Player pool: ownership discipline

The core rule: **a player is owned by exactly one feed item at a time**, and is
fully reset before being handed to another item. Never share a player between two
simultaneously-visible items, and never leave a stale player attached to an item
that's scrolled off-screen.

```kotlin
class VideoPlayerPool(private val context: Context, size: Int = VideoPlayerPoolConfig.POOL_SIZE) {
    private val available = ArrayDeque<ExoPlayer>().apply {
        repeat(size) { add(createPlayer()) }
    }
    private val inUse = mutableMapOf<String, ExoPlayer>() // keyed by post ID

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
}
```

Provide the pool once via `AppContainer` — never create a pool per Composable,
that's the most common footgun and defeats the entire point of pooling.

---

## Load control tuning for feed clips

Short clips (~15s) need aggressive, low-latency buffering — not the defaults
tuned for long-form video:

```kotlin
private fun feedLoadControl(): LoadControl =
    DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 2_000,
            /* maxBufferMs = */ 10_000,
            /* bufferForPlaybackMs = */ 500,
            /* bufferForPlaybackAfterRebufferMs = */ 1_000
        )
        .build()
```

---

## Prefetch: DefaultPreloadManager

Use Media3's `PreloadManager` API — the version designed specifically for
dynamic, scroll-driven feeds where the "next" item depends on user interaction
(not the older sequential-playlist `PreloadMediaSource` approach, which assumes
a fixed, predictable order).

```kotlin
val preloadManager = DefaultPreloadManager.Builder(context, targetPreloadStatusControl)
    .build()

// As the feed loads posts, register them for preload ranked by scroll proximity
posts.forEachIndexed { index, post ->
    preloadManager.add(MediaItem.fromUri(post.videoUrl), rankingData = index)
}

// When the current position changes, update preload targets
preloadManager.setCurrentPlayingIndex(currentIndex)
preloadManager.invalidate()
```

`targetPreloadStatusControl` determines how much of each upcoming item to load
(init segment only vs. first few seconds) — favor loading less per item and more
items, since the point is having several clips *decode-ready*, not fully buffered.

---

## Poster-overlay first-frame handling

Kills the black flash. The poster (thumbnail from the proto `Post.thumbnail_url`,
already BlurHash-ready per the stack reference) sits on top of the video surface
until the real first frame renders, then crossfades out.

```kotlin
@Composable
fun VideoFeedItem(post: Post, player: ExoPlayer) {
    var isFirstFrameRendered by remember(post.id) { mutableStateOf(false) }

    LaunchedEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                isFirstFrameRendered = true
            }
        }
        player.addListener(listener)
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player; useController = false } },
            modifier = Modifier.fillMaxSize()
        )
        AsyncImage(
            model = post.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
                .alpha(if (isFirstFrameRendered) 0f else 1f)
                .animateContentSize()
        )
    }
}
```

**The poster alone is not enough** — it must be paired with prefetch, or it's
just a prettier stall. Both techniques together are what eliminate the visible gap.

---

## Measurement: wire in from day one

Per the stack reference's "measure everything" principle, instrument these events
using `AnalyticsListener` and tie them to the perfetto skills already installed:

```kotlin
player.addAnalyticsListener(object : AnalyticsListener {
    override fun onRenderedFirstFrame(
        eventTime: AnalyticsListener.EventTime,
        output: Any,
        renderTimeMs: Long
    ) {
        // Log TTFF: renderTimeMs - eventTime.realtimeMs
    }

    override fun onLoadError(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
        error: IOException,
        wasCanceled: Boolean
    ) {
        // Log rebuffer/error event
    }
})
```

Metrics to capture per the stack reference: **TTFF**, **rebuffer ratio**,
**scroll-to-play latency**. Send these to whatever analytics pipeline is chosen
later — for now, log locally and confirm the instrumentation points are correct.

---

## What NOT to do

- ❌ One `ExoPlayer` per feed item (allocation churn, the exact problem pooling solves)
- ❌ Creating a `VideoPlayerPool` instance inside a Composable (creates a pool per
  recomposition scope instead of one shared pool — defeats pooling entirely)
- ❌ Fully downloading the next clip during prefetch (wastes data — prefetch to
  decode-ready, not fully buffered)
- ❌ Poster-overlay without prefetch (looks nicer but still stalls — must be paired)
- ❌ Remote-configurable pool size/preload count before real telemetry justifies it

## Related official skills

- `profilers/perfetto-trace-analysis` — for measuring the TTFF and rebuffer
  metrics this skill instruments
- `profilers/perfetto-sql` — querying captured traces once instrumentation is live