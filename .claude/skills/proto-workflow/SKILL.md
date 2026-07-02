---
name: proto-workflow
description: >
  Use when modifying .proto files, adding new RPCs or messages, running buf generate,
  changing the API contract, adding fields to existing messages, or discussing proto
  imports and versioning. Triggers: "add field to proto", "new RPC", "new endpoint",
  "update proto", "add to feed response", "buf generate", "API contract change",
  "proto message", "new service", "extend the API".
---

# Proto Workflow

## The rule: proto is the spine

Every feature starts here. The `.proto` file is the design step — it forces
the contract to be defined before any implementation. Never write SQL, handlers,
or UI until the proto is reviewed and `buf generate` has run.

## File locations

```
proto/ventura/
├── feed/v1/feed.proto        # GetFeed RPC + Post, GetFeedRequest, GetFeedResponse
├── places/v1/places.proto    # Place, LatLng messages
├── user/v1/user.proto        # User message
└── admin/v1/admin.proto      # admin-only endpoints (not yet built)
```

## When to extend vs. create a new file

**Extend existing** — adding fields to an existing message, adding an RPC to an
existing service, or adding a new message that belongs to an existing domain.

**Create new file** — new domain (e.g., `comments/v1/comments.proto`), new service
that doesn't belong to any existing domain, or explicitly breaking backwards
compatibility (use `v2/`).

## Field number rules

Field numbers are permanent wire identifiers — they cannot be reused, renumbered,
or removed without breaking backwards compatibility. When removing a field, mark it
`reserved` instead of deleting:

```proto
reserved 4; // previously: string removed_field = 4;
```

## Generate command

Run from **monorepo root**, not from inside `proto/`:

```bash
buf lint       # always validate first
buf generate   # regenerates gen/go/, gen/swift/, gen/kotlin/ simultaneously
```

Generated files are **committed to git** — never gitignored.

## Go import paths after generation

```go
// Proto message types
feedv1   "github.com/ventura-app/ventura/gen/go/ventura/feed/v1"
placesv1 "github.com/ventura-app/ventura/gen/go/ventura/places/v1"
userv1   "github.com/ventura-app/ventura/gen/go/ventura/user/v1"

// ConnectRPC server interface + client — NOTE: separate package
feedv1connect "github.com/ventura-app/ventura/gen/go/ventura/feed/v1/feedv1connect"
```

The `feedv1connect` package is intentionally separate from `feedv1` — ConnectRPC
keeps proto types lightweight and avoids name collisions this way.

## go_package convention

```proto
option go_package = "github.com/ventura-app/ventura/gen/go/ventura/[domain]/v1;[domain]v1";
```

## Versioning

- `v1` is the current contract — keep it stable
- Adding new **optional** fields to existing messages is non-breaking — safe in v1
- Removing fields, renaming RPCs, or changing field types requires `v2/`
- New proto files always start at `v1/`

## Common timestamps pattern

```proto
import "google/protobuf/timestamp.proto";
// ...
google.protobuf.Timestamp created_at = 6;
```

In Go, create with `timestamppb.New(t)` or `timestamppb.Now()`.