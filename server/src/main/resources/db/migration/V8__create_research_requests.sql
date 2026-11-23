CREATE TABLE research_requests (
    workspace_key varchar(32) NOT NULL,
    request_key uuid NOT NULL,
    fingerprint varchar(64) NOT NULL,
    status varchar(12) NOT NULL,
    record_id uuid UNIQUE REFERENCES research_history(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_key, request_key),
    CHECK ((status = 'COMPLETED' AND record_id IS NOT NULL)
        OR (status IN ('RUNNING', 'FAILED') AND record_id IS NULL))
);
