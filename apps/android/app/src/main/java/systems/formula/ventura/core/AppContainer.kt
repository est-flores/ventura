package systems.formula.ventura.core

import android.content.Context
import com.connectrpc.ProtocolClientConfig
import com.connectrpc.extensions.GoogleJavaProtobufStrategy
import com.connectrpc.impl.ProtocolClient
import com.connectrpc.okhttp.ConnectOkHttpClient
import com.connectrpc.protocols.NetworkProtocol
import okhttp3.OkHttpClient
import systems.formula.ventura.BuildConfig
import systems.formula.ventura.feed.FeedPreloader
import systems.formula.ventura.feed.FeedRepository
import systems.formula.ventura.feed.VideoPlayerPool

/**
 * Manual DI — the single source of dependency wiring, constructed once in
 * [systems.formula.ventura.VenturaApplication]. No Hilt yet; add new repositories
 * and shared services here as features are built.
 */
class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext

    private val protocolClient by lazy {
        ProtocolClient(
            httpClient = ConnectOkHttpClient(OkHttpClient()),
            config = ProtocolClientConfig(
                host = BuildConfig.API_BASE_URL,
                networkProtocol = NetworkProtocol.CONNECT,
                serializationStrategy = GoogleJavaProtobufStrategy()
            )
        )
    }

    val feedRepository by lazy { FeedRepository(protocolClient) }

    // Video feed infra — shared, provided once (never created per Composable).
    val videoPlayerPool by lazy { VideoPlayerPool(appContext) }
    val feedPreloader by lazy { FeedPreloader(appContext) }
}
