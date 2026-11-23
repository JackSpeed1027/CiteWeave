CREATE TABLE research_history (
    id uuid PRIMARY KEY,
    workspace_key varchar(32) NOT NULL,
    question varchar(500) NOT NULL CHECK (length(btrim(question)) > 0),
    source_ids jsonb NOT NULL CHECK (jsonb_typeof(source_ids) = 'array'),
    snapshot jsonb NOT NULL CHECK (jsonb_typeof(snapshot) = 'object'),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX research_history_recent_idx
    ON research_history (workspace_key, created_at DESC, id DESC);
