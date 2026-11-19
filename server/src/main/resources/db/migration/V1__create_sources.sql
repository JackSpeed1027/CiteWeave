CREATE TABLE sources (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title varchar(160) NOT NULL CHECK (length(trim(title)) > 0),
    kind varchar(12) NOT NULL CHECK (kind IN ('TEXT', 'TXT', 'MARKDOWN', 'PDF', 'SRT', 'VTT')),
    filename varchar(255),
    content text NOT NULL CHECK (length(content) BETWEEN 1 AND 500000),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX sources_recent_idx ON sources (created_at DESC, id DESC);
