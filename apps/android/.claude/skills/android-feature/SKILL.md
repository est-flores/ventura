---
name: android-feature
description: >
  Use when adding a new Android feature, screen, ViewModel, or repository, or when
  wiring a new feature into MainActivity. Triggers: "new Android screen", "add
  feature to Android", "Android ViewModel", "Android repository", "wire into
  MainActivity", "Compose screen", "new Android feature", "Android DI",
  "AppContainer".
---

# Android Feature Pattern

Ventura stays single-module, package-by-feature. This mirrors the Go backend's
`internal/[domain]/` structure exactly — same mental model on both sides of the
stack. Do not introduce Gradle multi-module or Hilt until there are 4-5 real
features and build times justify the overhead.

## Package structure per feature

```
com.ventura.app/
├── core/
│   └── AppContainer.kt         # manual DI — single source of dependency wiring
├── feed/
│   ├── FeedRepository.kt       # ConnectRPC calls, returns generated proto types
│   ├── FeedViewModel.kt        # StateFlow<FeedState>, business logic
│   └── FeedScreen.kt           # Composable — pure rendering, no logic
├── places/                     # future feature, same three-file shape
└── user/                       # future feature, same three-file shape
```

Every feature follows the same three-file shape: **Repository → ViewModel → Screen**.
This is the direct Android equivalent of the Go `repository.go → service.go → handler.go`
split — data access, business logic, and presentation stay in separate files even
within one package.

---

## AppContainer — manual DI

No Hilt yet. A single container class wires every dependency, constructed once in
`MainActivity` or `Application`.

```kotlin
// core/AppContainer.kt
package com.ventura.app.core

import com.ventura.app.BuildConfig
import com.ventura.app.feed.FeedRepository

class AppContainer {
    private val protocolClient by lazy {
        ProtocolClient(
            httpClient = ConnectOkHttpClient(OkHttpClient()),
            config = ProtocolClientConfig(
                host = BuildConfig.API_BASE_URL,
                networkProtocol = NetworkProtocol.CONNECT,
                serializationStrategy = GoogleJavaProtoStrategy()
            )
        )
    }

    val feedRepository by lazy { FeedRepository(protocolClient) }
    // Add new repositories here as features are built —
    // this is the single place that wires the dependency graph.
}
```

Access via `Application` subclass or pass down through Compose:

```kotlin
class VenturaApplication : Application() {
    val container by lazy { AppContainer() }
}
```

---

## Repository pattern

Takes the shared `ProtocolClient`, exposes suspend functions returning generated
proto types directly — no intermediate mapping layer needed since the proto
contract already is the domain model here.

```kotlin
class FeedRepository(private val client: ProtocolClient) {
    private val feedClient = FeedServiceClient(client)

    suspend fun getFeed(lat: Double, lng: Double, limit: Int = 10): List<Post> {
        val request = GetFeedRequest.newBuilder()
            .setLocation(LatLng.newBuilder().setLatitude(lat).setLongitude(lng).build())
            .setLimit(limit)
            .build()
        return feedClient.getFeed(request).message?.postsList ?: emptyList()
    }
}
```

---

## ViewModel pattern — StateFlow + sealed UiState

Every screen ViewModel follows the same shape: sealed state class, `StateFlow`,
`viewModelScope.launch` for async work.

```kotlin
sealed class FeedState {
    object Loading : FeedState()
    data class Success(val posts: List<Post>) : FeedState()
    data class Error(val message: String) : FeedState()
}

class FeedViewModel(private val repository: FeedRepository) : ViewModel() {
    private val _state = MutableStateFlow<FeedState>(FeedState.Loading)
    val state: StateFlow<FeedState> = _state

    init { loadFeed() }

    private fun loadFeed() {
        viewModelScope.launch {
            _state.value = try {
                FeedState.Success(repository.getFeed(lat = ..., lng = ...))
            } catch (e: Exception) {
                FeedState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
```

**ViewModel factory** — since there's no Hilt, wire the repository manually:

```kotlin
class FeedViewModelFactory(private val repository: FeedRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return FeedViewModel(repository) as T
    }
}
```

---

## Screen pattern — pure rendering

Composables read `StateFlow` via `collectAsStateWithLifecycle()` and render.
No business logic, no direct repository calls from a Composable.

```kotlin
@Composable
fun FeedScreen(viewModel: FeedViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is FeedState.Loading -> LoadingIndicator()
        is FeedState.Error -> ErrorMessage(s.message)
        is FeedState.Success -> FeedContent(s.posts)
    }
}
```

---

## Wiring a new feature into MainActivity

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as VenturaApplication).container

        setContent {
            VenturaTheme {
                val feedViewModel: FeedViewModel = viewModel(
                    factory = FeedViewModelFactory(container.feedRepository)
                )
                FeedScreen(feedViewModel)
            }
        }
    }
}
```

---

## New feature checklist

- [ ] Package created: `com.ventura.app/[feature]/`
- [ ] `[Feature]Repository.kt` — takes shared `ProtocolClient`, returns proto types
- [ ] `[Feature]ViewModel.kt` — sealed `[Feature]State`, `StateFlow`, `viewModelScope`
- [ ] `[Feature]Screen.kt` — pure rendering, `collectAsStateWithLifecycle()`
- [ ] Repository added to `AppContainer`
- [ ] `[Feature]ViewModelFactory` created
- [ ] Wired into `MainActivity` or navigation graph

## When to revisit these decisions

- **Hilt**: once 4-5 features exist and manual wiring in `AppContainer` becomes unwieldy
- **Multi-module**: once build times noticeably slow iteration, or a second developer joins
- **Navigation library**: once more than 2-3 screens exist — see the `navigation-3`
  official skill already installed