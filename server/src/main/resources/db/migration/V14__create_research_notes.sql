CREATE TABLE research_notes (
    id uuid PRIMARY KEY,
    workspace_key varchar(32) NOT NULL,
    research_id uuid NOT NULL REFERENCES research_history(id),
    title varchar(160) NOT NULL,
    markdown text NOT NULL CHECK (length(markdown) <= 50000),
    citations jsonb NOT NULL CHECK (jsonb_typeof(citations) = 'array'),
    revision bigint NOT NULL DEFAULT 1 CHECK (revision >= 1),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (workspace_key, research_id)
);

CREATE INDEX research_notes_recent_idx ON research_notes (workspace_key, updated_at DESC, id DESC);
