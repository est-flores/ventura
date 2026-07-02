---
name: go-backend
description: >
  Use when writing or modifying Go backend code: handlers, repositories, services,
  postgres pool setup, middleware, or main.go wiring. Triggers: "Go handler",
  "write the query", "new service", "backend implementation", "ConnectRPC handler",
  "pgx query", "repository", "wire in main", "Go feature", "implement the endpoint",
  "add to consumer-api".
---

# Go Backend Patterns

## Three-file pattern per domain

Every domain under `internal/` follows the same structure. All three files share
`package [domain]` (e.g., `package feed`).

```
internal/feed/
├── handler.go      # ConnectRPC handler — request/response concerns only
├── repository.go   # All SQL queries and pgx row scanning
└── service.go      # Business logic — ranking, filtering, cache decisions
```

**No SQL in handlers. No business logic in repositories. No HTTP concerns in services.**

---

## handler.go

Responsibilities: extract from `req.Msg`, apply defaults, call service, wrap errors,
return response. Nothing else.

```go
type Handler struct{ service *Service }

func NewHandler(service *Service) *Handler { return &Handler{service: service} }

func (h *Handler) GetFeed(
    ctx context.Context,
    req *connect.Request[feedv1.GetFeedRequest],
) (*connect.Response[feedv1.GetFeedResponse], error) {
    limit := req.Msg.Limit
    if limit == 0 { limit = 10 }

    posts, err := h.service.GetFeed(ctx, limit)
    if err != nil {
        return nil, connect.NewError(connect.CodeInternal, err)
    }
    return connect.NewResponse(&feedv1.GetFeedResponse{Posts: posts}), nil
}
```

## repository.go

Responsibilities: SQL constants, pgx pool interactions, row scanning. Returns proto
types or domain types — never raw `*sql.Row` or database-specific types.

```go
type Repository struct{ db *pgxpool.Pool }

func NewRepository(db *pgxpool.Pool) *Repository { return &Repository{db: db} }

const getFeedQuery = `SELECT ... FROM posts p JOIN ...`

func (r *Repository) GetFeed(ctx context.Context, limit int32) ([]*feedv1.Post, error) {
    rows, err := r.db.Query(ctx, getFeedQuery, limit)
    if err != nil {
        return nil, fmt.Errorf("Repository.GetFeed query: %w", err)
    }
    defer rows.Close()  // always defer immediately after Query

    var posts []*feedv1.Post
    for rows.Next() {
        var id, videoURL string
        // ... declare all scan vars
        if err := rows.Scan(&id, &videoURL /*, ... */); err != nil {
            return nil, fmt.Errorf("Repository.GetFeed scan: %w", err)
        }
        posts = append(posts, &feedv1.Post{Id: id, VideoUrl: videoURL})
    }
    return posts, nil
}
```

## service.go

Responsibilities: business logic, orchestration, cache decisions. GPS ranking,
personalization, and Redis lookups live here — not in the repository.

```go
type Service struct{ repo *Repository }

func NewService(repo *Repository) *Service { return &Service{repo: repo} }

func (s *Service) GetFeed(ctx context.Context, limit int32) ([]*feedv1.Post, error) {
    // Future: check Redis cache, apply GPS ranking, personalization weights
    return s.repo.GetFeed(ctx, limit)
}
```

---

## postgres/pool.go pattern

```go
package postgres

func NewPool(ctx context.Context) (*pgxpool.Pool, error) {
    url := os.Getenv("DATABASE_URL")
    if url == "" {
        return nil, fmt.Errorf("DATABASE_URL not set")
    }
    return pgxpool.New(ctx, url)
}
```

---

## main.go wiring order

`main.go` is a wiring file only. No logic, no SQL, no inline config.
Always wire in this dependency injection order:

```go
// 1. Load config
if err := godotenv.Load(); err != nil {
    log.Println("no .env file, reading from environment")
}

// 2. DB pool
pool, err := postgres.NewPool(ctx)

// 3. Inject: pool → repo → service → handler
feedRepo := feed.NewRepository(pool)
feedSvc  := feed.NewService(feedRepo)

// 4. Register with ConnectRPC mux
path, h := feedv1connect.NewFeedServiceHandler(feed.NewHandler(feedSvc))
mux.Handle(path, h)

// 5. Start server (read PORT from env for Railway compatibility)
port := os.Getenv("PORT")
if port == "" { port = "8080" }
http.ListenAndServe(":"+port, h2c.NewHandler(mux, &http2.Server{}))
```

---

## ConnectRPC error codes

| Situation | Code |
|---|---|
| DB failure or internal error | `connect.CodeInternal` |
| Resource not found | `connect.CodeNotFound` |
| Bad or missing input | `connect.CodeInvalidArgument` |
| Not authenticated | `connect.CodeUnauthenticated` |
| Authenticated but not authorized | `connect.CodePermissionDenied` |

---

## Package naming rules

- Domain packages: `feed/`, `places/`, `user/` — not `handlers/`, `services/`, `repos/`
- Infrastructure: `postgres/` not `db/`, `cache/` not `redis/`
- Forbidden names: `utils/`, `helpers/`, `common/`, `base/` — always too generic
- Go compiler enforces `internal/` — nothing outside the module can import it