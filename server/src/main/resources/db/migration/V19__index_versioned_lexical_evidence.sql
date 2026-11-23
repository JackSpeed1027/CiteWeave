CREATE TABLE lexical_evidence_generations (
    source_id bigint NOT NULL REFERENCES sources(id),
    source_version varchar(64) NOT NULL,
    index_version varchar(32) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (source_id, source_version, index_version)
);

CREATE TABLE lexical_evidence_chunks (
    source_id bigint NOT NULL,
    source_version varchar(64) NOT NULL,
    index_version varchar(32) NOT NULL,
    ordinal integer NOT NULL CHECK (ordinal >= 0),
    evidence_id varchar(160) NOT NULL,
    location_type varchar(16) NOT NULL CHECK (location_type IN ('transcript', 'paragraph', 'page')),
    start_ms bigint,
    end_ms bigint,
    body text NOT NULL,
    paragraph integer,
    page integer,
    terms text[] NOT NULL,
    PRIMARY KEY (source_id, source_version, index_version, ordinal),
    FOREIGN KEY (source_id, source_version, index_version)
        REFERENCES lexical_evidence_generations(source_id, source_version, index_version)
);

CREATE INDEX lexical_evidence_terms_idx ON lexical_evidence_chunks USING GIN (terms);
