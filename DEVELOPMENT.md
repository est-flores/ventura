# Ventura — Development Setup

This guide gets you from a fresh clone to a video playing on a simulator/emulator,
with both local and staging environments working. Follow the sections in order —
each one depends on the previous.

---

## What you'll have when done

- Local Postgres + Redis running in Docker
- Go backend running locally, connected to local DB
- iOS app on simulator playing a video from local backend
- Android app on emulator playing the same video
- Staging environment on Railway for live testing

---

## Prerequisites

Install these before anything else. All commands below assume macOS.

### Core tools

```bash
# Homebrew (if not already installed)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Go (1.26+)
brew install go

# Buf (proto tooling)
brew install bufbuild/buf/buf

# golang-migrate (database migrations)
brew install golang-migrate

# Docker Desktop
# Download from: https://www.docker.com/products/docker-desktop
# Make sure Docker is running before the infra step

# PostgreSQL client (for seeding and inspecting the DB)
brew install libpq
echo 'export PATH="/opt/homebrew/opt/libpq/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

# xcbeautify (cleaner iOS build output)
brew install xcbeautify

# Railway CLI (for staging deploys)
brew install railway
```

### IDEs

- **Xcode** — download from the Mac App Store (required for iOS)
- **Android Studio** — download from [developer.android.com](https://developer.android.com/studio)
- **VSCode** — recommended for Go and general monorepo work, with Claude Code extension

### Accounts needed

- **Railway** — [railway.com](https://railway.com) — staging hosting
- **Buf** — [buf.build](https://buf.build) — for remote codegen plugins (free)

---

## 1. Clone and initial setup

```bash
git clone https://github.com/ventura-app/ventura.git
cd ventura
```

### Install Go dependencies

```bash
go mod tidy
```

### Log into Buf (one-time)

```bash
buf registry login
```

---

## 2. Generate code from proto

This step generates Go, Swift, and Kotlin client code from the `.proto` contract.
**Run this every time a `.proto` file changes.**

```bash
buf generate
```

You should see files appear (or be confirmed unchanged) in:
- `gen/go/ventura/`
- `gen/swift/ventura/`
- `gen/kotlin/ventura/`

> These files are committed to git, so a fresh clone already has them.
> You only need to re-run `buf generate` if you modify a `.proto` file.

---

## 3. Set up local environment variables

Copy the example env file and fill in your values:

```bash
cp .env.example .env
```

For local development, the defaults in `.env.example` already work with Docker Compose.
The staging values come from Railway — see the Staging section below.

If you don't have `.env.example`, create `.env` with:

```
DATABASE_URL=postgres://ventura:ventura@localhost:5432/ventura_dev?sslmode=disable
```

---

## 4. Start local infrastructure

This starts Postgres (with PostGIS) and Redis in Docker:

```bash
docker-compose -f infra/docker-compose.yml up -d
```

Verify both are running:

```bash
docker-compose -f infra/docker-compose.yml ps
```

Both services should show as `running`. If Docker Desktop isn't open, start it first.

---

## 5. Run database migrations

```bash
migrate \
  -path db/migrations \
  -database "postgres://ventura:ventura@localhost:5432/ventura_dev?sslmode=disable" \
  up
```

Expected output:
```
1/u init (Xms)
```

---

## 6. Seed local data

```bash
docker exec -i \
  $(docker-compose -f infra/docker-compose.yml ps -q postgres) \
  psql -U ventura -d ventura_dev < db/seed/seed.sql
```

This inserts one test user, one place, and one post with a public HLS video URL.

---

## 7. Run the Go backend

```bash
go run ./services/consumer-api/cmd/main.go
```

Expected output:
```
consumer-api listening on :8080
```

### Quick smoke test

In a second terminal, confirm the API is responding:

```bash
curl -X POST http://localhost:8080/ventura.feed.v1.FeedService/GetFeed \
  -H "Content-Type: application/json" \
  -d '{"location": {"latitude": 14.6349, "longitude": -90.5069}, "limit": 10}'
```

You should get back a JSON response with one post, including a `video_url`.

---

## 8. iOS setup

### Open the project

```bash
open apps/ios/Ventura/Ventura.xcodeproj
```

### What's already configured

- **VenturaGenSwift** local Swift Package is linked — generated Swift types are
  automatically available without any copy-paste
- **Connect-Swift** and **SwiftProtobuf** are added via Swift Package Manager
- **Two schemes** are pre-configured:
  - `Ventura-Local` → hits `http://localhost:8080`
  - `Ventura-Staging` → hits the Railway staging URL

### Run on simulator

1. Select the **Ventura-Local** scheme from the scheme dropdown in Xcode
2. Select an iPhone simulator
3. Hit **Run** (⌘R)

Make sure the Go backend (step 7) is running before launching.

### Building from terminal (for Claude Code workflow)

```bash
cd apps/ios/Ventura
xcodebuild build \
  -project Ventura.xcodeproj \
  -scheme Ventura-Local \
  -destination 'platform=iOS Simulator,name=iPhone 16' | xcbeautify
```

---

## 9. Android setup

### Open the project

Open Android Studio → Open → select `apps/android/`

### What's already configured

- Gradle `sourceSets` points directly to `gen/kotlin/` — generated Kotlin types
  are available automatically, nothing to import or copy
- **Two build variants** are pre-configured:
  - `localDebug` → hits `http://10.0.2.2:8080` (emulator → your Mac's localhost)
  - `stagingDebug` → hits the Railway staging URL

### Run on emulator

1. Open **Build Variants** panel (bottom-left in Android Studio)
2. Select `localDebug`
3. Start an emulator and hit **Run**

Make sure the Go backend (step 7) is running before launching.

### Building from terminal (for Claude Code workflow)

```bash
cd apps/android

# Check for errors
./gradlew assembleLocalDebug 2>&1 | grep -E "error:|warning:|FAILED"

# Build and install to running emulator
./gradlew installLocalDebug
```

---

## 10. Staging environment

The staging environment runs on Railway. You need access to the Railway project.

### Login

```bash
railway login
railway link  # select the ventura-staging project
```

### Run staging migrations

Get the staging database URL from Railway dashboard →
PostGIS service → Variables → `DATABASE_URL`, then:

```bash
migrate -path db/migrations -database "YOUR_RAILWAY_DATABASE_URL" up
```

### Deploy

```bash
railway up
```

### Test against staging

**iOS:** switch to the **Ventura-Staging** scheme in Xcode, run on simulator.

**Android:** switch to `stagingDebug` in Build Variants, run on emulator.

**curl:**
```bash
curl -X POST https://your-service.up.railway.app/ventura.feed.v1.FeedService/GetFeed \
  -H "Content-Type: application/json" \
  -d '{"location": {"latitude": 14.6349, "longitude": -90.5069}, "limit": 10}'
```

---

## Common issues

**`buf generate` fails with auth error**
```bash
buf registry login
```

**Migrations fail with "dirty database"**
```bash
migrate -path db/migrations \
  -database "postgres://ventura:ventura@localhost:5432/ventura_dev?sslmode=disable" \
  force [LAST_GOOD_VERSION]
```

**iOS build fails with "module not found"**
Make sure the `VenturaGenSwift` local package is linked:
Xcode → Project → Package Dependencies → confirm `gen/swift` is listed.

**Android emulator can't reach the Go server**
The emulator uses `10.0.2.2` to reach your Mac's localhost, not `localhost`.
This is already configured in `localDebug` — just confirm the Go server is running.

**`psql: command not found`**
```bash
brew install libpq
echo 'export PATH="/opt/homebrew/opt/libpq/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

---

## Reference

| Resource | Location |
|---|---|
| Architecture decisions | `ventura-stack-reference.md` |
| Proto contracts | `proto/ventura/` |
| DB migrations | `db/migrations/` |
| Seed data | `db/seed/seed.sql` |
| AI workflow (Claude Code) | `CLAUDE.md` + `.claude/skills/` |