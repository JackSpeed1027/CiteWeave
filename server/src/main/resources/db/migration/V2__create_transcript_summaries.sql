CREATE TABLE transcript_summaries (
    source_id bigint NOT NULL REFERENCES sources(id),
    source_version varchar(64) NOT NULL,
    result jsonb NOT NULL,
    input_evidence_ids jsonb NOT NULL,
    PRIMARY KEY (source_id, source_version)
);
