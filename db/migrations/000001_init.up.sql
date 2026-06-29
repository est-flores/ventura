CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE users (
  id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  email      TEXT NOT NULL UNIQUE,
  first_name TEXT NOT NULL,
  last_name  TEXT NOT NULL,
  avatar_url TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE places (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name        TEXT NOT NULL,
  description TEXT,
  location    GEOGRAPHY(POINT, 4326) NOT NULL,
  owner_id    UUID NOT NULL REFERENCES users(id),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- PostGIS spatial index — this is what makes "places near me" fast
CREATE INDEX places_location_idx ON places USING GIST(location);

CREATE TABLE posts (
  id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  video_url     TEXT NOT NULL,
  thumbnail_url TEXT,
  author_id     UUID NOT NULL REFERENCES users(id),
  place_id      UUID NOT NULL REFERENCES places(id),
  view_count    BIGINT NOT NULL DEFAULT 0,
  like_count    BIGINT NOT NULL DEFAULT 0,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);