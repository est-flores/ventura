# Ventura

GPS-driven travel discovery app. TikTok-style video feed. The consumer feed
scroll-to-play experience is the product — every decision protects it.

**Go module:** `github.com/ventura-app/ventura`
**Full architecture decisions:** See `ventura-stack-reference.md`

---

## Build commands

### Proto (run from monorepo root)
```bash
buf lint                   # validate before generating
buf generate               # regenerate Go + Swift + Kotlin simultaneously
```

### Go backend
```bash
go run ./services/consumer-api/cmd/main.go
go build ./...             # check all packages for errors
go mod tidy
```

### Migrations
```bash
# Local
migrate -path db/migrations \
  -database "postgres://ventura:ventura@localhost:5432/ventura_dev?sslmode=disable" up

# Roll back one step
migrate -path db/migrations \
  -database "postgres://ventura:ventura@localhost:5432/ventura_dev?sslmode=disable" down 1

# Staging
migrate -path db/migrations -database "$STAGING_DATABASE_URL" up
```

### iOS (from apps/ios/Ventura/)
```bash
xcodebuild build \
  -project Ventura.xcodeproj \
  -scheme Ventura-Local \
  -destination 'platform=iOS Simulator,name=iPhone 16' | xcbeautify
```

### Android (from apps/android/)
```bash
./gradlew assembleLocalDebug 2>&1 | grep -E "error:|warning:|FAILED"
./gradlew installLocalDebug
```

### Local infra
```bash
docker-compose -f infra/docker-compose.yml up -d   # start Postgres + Redis
docker-compose -f infra/docker-compose.yml down    # stop
```

---

## Key paths

| Layer | Path |
|---|---|
| Proto source | `proto/ventura/` |
| Generated Go | `gen/go/ventura/` |
| Generated Swift | `gen/swift/ventura/` |
| Generated Kotlin | `gen/kotlin/ventura/` |
| Consumer API | `services/consumer-api/` |
| Migrations | `db/migrations/` |
| iOS app | `apps/ios/Ventura/` |
| Android app | `apps/android/` |
| Infra | `infra/docker-compose.yml` |

---

## Always-on rules

**Proto first.** No DB schema, handler, or UI before the proto contract exists.
The `.proto` file is the design step — get the contract right before any implementation.

**Never edit `gen/`.** Every file under `gen/` is produced by `buf generate`.
Hand-edits are silently overwritten on the next generate.

**`main.go` is a wiring file only.** Dependency injection chain only — no business
logic, no SQL, no inline config parsing. All logic lives in `internal/`.

**Stub before real DB.** When adding a new handler, return hardcoded data first.
This unblocks iOS and Android before the query exists.

**Package names must be descriptive.** `postgres/` not `db/`, `cache/` not `redis/`,
domain packages by name (`feed/`, `places/`) not by layer (`handlers/`, `services/`).

**Hot path is sacred.** Any change to `GetFeed` that adds latency, a network hop,
or a blocking call must be explicitly justified. The feed is the product thesis.