CREATE TABLE transcript_notes (
    id uuid PRIMARY KEY,
    workspace_key varchar(32) NOT NULL,
    source_id bigint NOT NULL,
    source_version varchar(64) NOT NULL,
    title varchar(160) NOT NULL,
    snapshot jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (workspace_key, source_id, source_version),
    FOREIGN KEY (source_id, source_version) REFERENCES transcript_summaries(source_id, source_version)
);

CREATE INDEX transcript_notes_recent_idx
    ON transcript_notes (workspace_key, created_at DESC, id DESC);
