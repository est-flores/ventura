package systems.formula.ventura.feed

import com.connectrpc.ResponseMessage
import com.connectrpc.impl.ProtocolClient
import ventura.feed.v1.Feed
import ventura.feed.v1.FeedServiceClient
import ventura.places.v1.Places

/**
 * Data access for the feed. Takes the shared [ProtocolClient] (wired once in
 * AppContainer) and returns generated proto types directly — the proto contract
 * is the domain model, so there is no intermediate mapping layer.
 */
class FeedRepository(client: ProtocolClient) {
    private val feedClient = FeedServiceClient(client)

    suspend fun getFeed(lat: Double, lng: Double, limit: Int = 10): List<Feed.Post> {
        val request = Feed.GetFeedRequest.newBuilder()
            .setLocation(
                Places.LatLng.newBuilder()
                    .setLatitude(lat)
                    .setLongitude(lng)
                    .build()
            )
            .setLimit(limit)
            .build()

        return when (val response = feedClient.getFeed(request)) {
            is ResponseMessage.Success -> response.message.postsList
            is ResponseMessage.Failure -> throw response.cause
        }
    }
}
