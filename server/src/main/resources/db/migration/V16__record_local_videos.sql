CREATE TABLE local_videos (
    id uuid PRIMARY KEY,
    workspace_key varchar(80) NOT NULL,
    filename varchar(255) NOT NULL,
    sha256 varchar(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    size_bytes bigint NOT NULL CHECK (size_bytes > 0),
    duration_ms bigint NOT NULL CHECK (duration_ms > 0),
    width integer NOT NULL CHECK (width > 0),
    height integer NOT NULL CHECK (height > 0),
    video_codec varchar(64) NOT NULL,
    audio_codec varchar(64),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (workspace_key, sha256)
);

CREATE INDEX local_videos_recent_idx ON local_videos (workspace_key, created_at DESC, id DESC);
