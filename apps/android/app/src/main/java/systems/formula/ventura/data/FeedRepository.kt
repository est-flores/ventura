package systems.formula.ventura.data
    
import com.connectrpc.ProtocolClientConfig
import com.connectrpc.ResponseMessage
import com.connectrpc.impl.ProtocolClient
import com.connectrpc.okhttp.ConnectOkHttpClient
import com.connectrpc.protocols.NetworkProtocol
import com.connectrpc.extensions.GoogleJavaProtobufStrategy
import okhttp3.OkHttpClient
import ventura.feed.v1.Feed
import ventura.feed.v1.FeedServiceClient
import ventura.places.v1.Places
import systems.formula.ventura.BuildConfig

class FeedRepository {
    private val client: FeedServiceClient

    init {
        val protocolClient = ProtocolClient(
            httpClient = ConnectOkHttpClient(OkHttpClient()),
            config = ProtocolClientConfig(
                host = BuildConfig.API_BASE_URL,
                networkProtocol = NetworkProtocol.CONNECT,
                serializationStrategy = GoogleJavaProtobufStrategy()
            )
        )
        client = FeedServiceClient(protocolClient)
    }

    suspend fun getFeed(lat: Double, lng: Double): List<Feed.Post> {
        val request = Feed.GetFeedRequest.newBuilder()
            .setLocation(
                Places.LatLng.newBuilder()
                    .setLatitude(lat)
                    .setLongitude(lng)
                    .build()
            )
            .setLimit(10)
            .build()

        val response = client.getFeed(request)
        return when (response) {
            is ResponseMessage.Success -> response.message.postsList
            is ResponseMessage.Failure -> throw response.cause
        }
    }
}