package systems.formula.ventura

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.formula.ventura.data.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ventura.feed.v1.Feed

sealed class FeedState {
    object Loading : FeedState()
    data class Success(val posts: List<Feed.Post>) : FeedState()
    data class Error(val message: String) : FeedState()
}

class FeedViewModel : ViewModel() {
    private val repository = FeedRepository()

    private val _state = MutableStateFlow<FeedState>(FeedState.Loading)
    val state: StateFlow<FeedState> = _state

    init {
        loadFeed()
    }

    private fun loadFeed() {
        viewModelScope.launch {
            try {
                val posts = repository.getFeed(lat = 14.6349, lng = -90.5069)
                _state.value = FeedState.Success(posts)
            } catch (e: Exception) {
                _state.value = FeedState.Error(e.message ?: "Unknown error")
            }
        }
    }
}