package systems.formula.ventura.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ventura.feed.v1.Feed

sealed class FeedState {
    object Loading : FeedState()
    data class Success(val posts: List<Feed.Post>) : FeedState()
    data class Error(val message: String) : FeedState()
}

class FeedViewModel(private val repository: FeedRepository) : ViewModel() {
    private val _state = MutableStateFlow<FeedState>(FeedState.Loading)
    val state: StateFlow<FeedState> = _state

    init {
        loadFeed()
    }

    private fun loadFeed() {
        viewModelScope.launch {
            _state.value = try {
                FeedState.Success(repository.getFeed(lat = 14.6349, lng = -90.5069))
            } catch (e: Exception) {
                FeedState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

/** No Hilt — wire the repository manually. */
class FeedViewModelFactory(
    private val repository: FeedRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return FeedViewModel(repository) as T
    }
}
