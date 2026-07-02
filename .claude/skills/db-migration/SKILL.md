---
name: db-migration
description: >
  Use when creating database migrations, modifying schema, adding tables or columns,
  writing PostGIS spatial queries, or implementing GPS-based feed queries. Triggers:
  "new table", "add column", "migration", "schema change", "PostGIS query",
  "nearby places", "GPS feed query", "radius query", "spatial index", "ST_DWithin",
  "geography column", "location query".
---

# Database Migration Patterns

## Creating a new migration

```bash
migrate create -ext sql -dir db/migrations -seq [description_with_underscores]

# Examples:
migrate create -ext sql -dir db/migrations -seq add_blurhash_to_posts
migrate create -ext sql -dir db/migrations -seq create_comments_table
```

This generates a pair:
```
db/migrations/000002_add_blurhash_to_posts.up.sql
db/migrations/000002_add_blurhash_to_posts.down.sql
```

**Always write both files.** Skipping `.down.sql` breaks emergency rollback.

---

## Migration rules

- **One change per migration** — atomic; don't combine unrelated schema changes
- **Always write `.down.sql`** that reverses exactly what `.up.sql` does
- **Names use underscores** — `add_column_to_table`, not `addColumnToTable`
- **Sequential numbers** — already established as `000001`, `000002`, etc.
- **Wrap related statements** in `BEGIN`/`COMMIT` when they must succeed or fail together

If a migration partially failed and left the DB in a dirty state:
```bash
migrate -path db/migrations -database "$DB_URL" force [VERSION_NUMBER]
```
Use `force` carefully — it clears the dirty flag without running the migration.

---

## CLI commands

### Local (Docker Compose)
```bash
# Apply all pending migrations
migrate -path db/migrations \
  -database "postgres://ventura:ventura@localhost:5432/ventura_dev?sslmode=disable" up

# Roll back one migration
migrate -path db/migrations \
  -database "postgres://ventura:ventura@localhost:5432/ventura_dev?sslmode=disable" down 1

# Check current version
migrate -path db/migrations \
  -database "postgres://ventura:ventura@localhost:5432/ventura_dev?sslmode=disable" version
```

### Staging (Railway)
```bash
migrate -path db/migrations -database "$STAGING_DATABASE_URL" up
```

### Seed data (local only)
```bash
docker exec -i \
  $(docker-compose -f infra/docker-compose.yml ps -q postgres) \
  psql -U ventura -d ventura_dev < db/seed/seed.sql
```

---

## PostGIS patterns

### Column type for GPS coordinates
```sql
-- Always use GEOGRAPHY (not GEOMETRY) for GPS lat/lng
-- GEOGRAPHY handles Earth's curvature for accurate distance calculations
location GEOGRAPHY(POINT, 4326) NOT NULL
```

### GiST spatial index — required on every geography column used in queries
```sql
CREATE INDEX [table]_location_idx ON [table] USING GIST(location);
```

### Inserting a GPS point
```sql
-- longitude comes FIRST in ST_MakePoint (x, y = lng, lat)
ST_SetSRID(ST_MakePoint(-90.5069, 14.6349), 4326)::geography
```

### GPS feed radius query — the core pattern for the feed feature
```sql
-- Find posts within radius_meters of user's GPS position
-- ST_DWithin uses GiST index — eliminates distant rows before ORDER BY
SELECT
    p.id,
    p.video_url,
    COALESCE(p.thumbnail_url, '') AS thumbnail_url,
    p.view_count,
    p.like_count,
    ST_Distance(
        pl.location,
        ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography
    ) AS distance_meters
FROM posts p
JOIN places pl ON pl.id = p.place_id
WHERE ST_DWithin(
    pl.location,
    ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography,
    $3  -- radius in meters (e.g., 50000 for 50km)
)
ORDER BY distance_meters ASC
LIMIT $4;
-- $1 = user longitude, $2 = user latitude, $3 = radius meters, $4 = limit
```

### Extracting lat/lng back out of a geography column (for API responses)
```sql
ST_Y(location::geometry) AS latitude,   -- Y = latitude
ST_X(location::geometry) AS longitude   -- X = longitude
```

---

## Current schema baseline

```
Extensions: postgis, uuid-ossp
Tables:     users, places, posts
Indexes:    places_location_idx (GIST on places.location)
Baseline:   000001_init
```