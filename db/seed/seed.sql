INSERT INTO users (id, email, first_name, last_name)
VALUES (
  'a0000000-0000-0000-0000-000000000001',
  'traveler@ventura.app',
  'Esteban',
  'Traveler'
);

INSERT INTO places (id, name, description, location, owner_id)
VALUES (
  'b0000000-0000-0000-0000-000000000001',
  'Guatemala City',
  'The capital of Guatemala',
  ST_SetSRID(ST_MakePoint(-90.5069, 14.6349), 4326)::geography,
  'a0000000-0000-0000-0000-000000000001'
);

INSERT INTO posts (id, video_url, thumbnail_url, author_id, place_id)
VALUES (
  'c0000000-0000-0000-0000-000000000001',
  'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8',
  'https://storage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg',
  'a0000000-0000-0000-0000-000000000001',
  'b0000000-0000-0000-0000-000000000001'
);