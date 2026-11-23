ALTER TABLE local_video_subtitle_tracks
    ADD COLUMN track_source varchar(16) NOT NULL DEFAULT 'USER_UPLOAD'
        CHECK (track_source IN ('USER_UPLOAD', 'MACHINE_ASR')),
    DROP CONSTRAINT local_video_subtitle_tracks_video_id_source_version_key,
    ADD UNIQUE (video_id, source_version, track_source);

CREATE TABLE local_video_asr_tasks (
    video_id uuid PRIMARY KEY REFERENCES local_videos(id),
    status varchar(12) NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'RUNNING', 'FAILED', 'SUCCEEDED')),
    attempt integer NOT NULL DEFAULT 0 CHECK (attempt >= 0),
    lease_until timestamptz,
    error_code varchar(48),
    source_id bigint REFERENCES local_video_subtitle_tracks(source_id),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CHECK ((status = 'RUNNING') = (lease_until IS NOT NULL)),
    CHECK ((status = 'FAILED') = (error_code IS NOT NULL)),
    CHECK ((status = 'SUCCEEDED') = (source_id IS NOT NULL))
);

CREATE INDEX local_video_asr_queue_idx ON local_video_asr_tasks (updated_at, video_id) WHERE status = 'QUEUED';
