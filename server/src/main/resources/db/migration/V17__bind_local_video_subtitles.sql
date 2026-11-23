CREATE TABLE local_video_subtitle_tracks (
    source_id bigint PRIMARY KEY REFERENCES sources (id),
    video_id uuid NOT NULL REFERENCES local_videos (id),
    source_version varchar(64) NOT NULL CHECK (source_version ~ '^[0-9a-f]{64}$'),
    UNIQUE (video_id, source_version)
);

CREATE INDEX local_video_subtitle_tracks_video_idx ON local_video_subtitle_tracks (video_id, source_id DESC);
