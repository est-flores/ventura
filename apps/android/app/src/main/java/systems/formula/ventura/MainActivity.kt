package systems.formula.ventura

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import systems.formula.ventura.feed.FeedScreen
import systems.formula.ventura.feed.FeedViewModel
import systems.formula.ventura.feed.FeedViewModelFactory
import systems.formula.ventura.ui.theme.VenturaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as VenturaApplication).container

        setContent {
            VenturaTheme {
                val feedViewModel: FeedViewModel = viewModel(
                    factory = FeedViewModelFactory(container.feedRepository)
                )
                FeedScreen(
                    feedViewModel = feedViewModel,
                    playerPool = container.videoPlayerPool,
                    preloader = container.feedPreloader,
                )
            }
        }
    }
}
