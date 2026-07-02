---
name: feature-slice
description: >
  Use when building a new product feature end-to-end, or when asked where to start
  or what order to follow. Triggers: "new feature", "implement X", "add [feature name]",
  "how do I build", "create the [name] feature", "start building", "what order",
  "where do I start", "build the GPS feed", "add comments", "implement likes".
---

# Feature Slice: End-to-End Build Order

This is the single most important workflow in the project. Deviating from this order
creates blocked clients, premature DB complexity, or wasted implementation work.

---

## The 7-step sequence

### Step 1 — Proto contract (the design step)

Write or extend the `.proto` file first. This defines what data the feature
produces and consumes before any implementation decision is made.

- Add messages and RPCs to the relevant file in `proto/ventura/`
- Run `buf lint` to validate
- Commit the proto change before any other work begins

### Step 2 — Generate

```bash
buf generate
```

Go server interface, Swift client, and Kotlin client are now all unblocked
simultaneously. Do not proceed until this runs clean.

### Step 3 — Go stub handler

Implement the ConnectRPC handler with **hardcoded data only** — no DB, no Redis.

- Create `handler.go` + `repository.go` + `service.go` in `internal/[domain]/`
- Return one hardcoded proto response from `service.go` (real values, fake data)
- Wire into `main.go`: `pool → repo.New(pool) → svc.New(repo) → handler.New(svc) → mux`
- Test with curl before moving on — **this is the gate**

```bash
curl -X POST http://localhost:8080/ventura.[domain].v1.[Service]/[Method] \
  -H "Content-Type: application/json" \
  -d '{...}'
```

Curl must return a valid, correctly-shaped response before step 4.

### Step 4 — iOS wires the stub

- Generated Swift types are auto-available via the local `VenturaGenSwift` package
- Add the new RPC call to the relevant `FeedService` or new service class
- Create or update the SwiftUI view
- Test on **Ventura-Local** scheme against the local stub server
- **Must see real data rendered on device before step 5**

### Step 5 — Android wires the stub

- Generated Kotlin types are auto-available via Gradle `sourceSets` pointing to `gen/kotlin`
- Add the RPC call to the relevant Repository class
- Update or create the Compose screen and ViewModel
- Test on **localDebug** build variant

```bash
cd apps/android && ./gradlew installLocalDebug
```

- **Must see real data rendered on emulator before step 6**

### Step 6 — Real DB implementation

Only after **both clients are confirmed working against the stub**:

1. Create migration:
   ```bash
   migrate create -ext sql -dir db/migrations -seq [feature_name]
   ```
2. Write `.up.sql` and `.down.sql`
3. Apply locally:
   ```bash
   migrate -path db/migrations \
     -database "postgres://ventura:ventura@localhost:5432/ventura_dev?sslmode=disable" up
   ```
4. Implement the real SQL query in `repository.go`
5. Replace hardcoded stub in `service.go` with the real repository call
6. Test with curl again — data should now come from Postgres
7. Test on both mobile clients locally

### Step 7 — Staging validation

```bash
migrate -path db/migrations -database "$STAGING_DATABASE_URL" up
railway up
```

- Test iOS on **Ventura-Staging** scheme
- Test Android on **stagingDebug** build variant
- Confirm data flows end-to-end against the live Railway deployment

---

## What never to skip

**Never skip Step 1.** Writing DB schema before the proto means the contract
is designed bottom-up from the database, not top-down from client needs.
The proto defines what clients need — the DB serves the proto, not the reverse.

**Never skip the Step 3 curl test.** If the handler doesn't respond correctly
to curl, mobile bugs will be harder to isolate against real DB complexity.

**Never connect real DB before both clients are validated (Step 6 before Step 5).**
Client issues found before the DB exists are cheap to fix.
Client issues found after are expensive.

---

## Environment quick reference

| | Go server | iOS scheme | Android variant | Database |
|---|---|---|---|---|
| **Local** | `go run ./services/consumer-api/cmd/main.go` | `Ventura-Local` | `localDebug` | Docker Compose |
| **Staging** | Railway (auto-deploy on push) | `Ventura-Staging` | `stagingDebug` | Railway PostGIS |

---

## New domain checklist

When a feature introduces a new domain (e.g., `comments`):

- [ ] New proto file: `proto/ventura/comments/v1/comments.proto`
- [ ] `go_package` option matches module path
- [ ] `buf generate` runs clean
- [ ] New directory: `services/consumer-api/internal/comments/`
- [ ] Three files: `handler.go`, `repository.go`, `service.go`
- [ ] Wired in `main.go`
- [ ] Migration created and applied locally
- [ ] Both mobile clients tested before real DB connected