CREATE TABLE source_favorites (
    workspace_key varchar(32) NOT NULL,
    source_id bigint NOT NULL REFERENCES sources(id),
    favorited boolean NOT NULL,
    PRIMARY KEY (workspace_key, source_id)
);

CREATE INDEX source_favorites_active_idx
    ON source_favorites (workspace_key, source_id) WHERE favorited;
