CREATE TABLE summary_tasks (
    id uuid PRIMARY KEY,
    source_id bigint NOT NULL UNIQUE REFERENCES sources(id),
    status varchar(12) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    attempt integer NOT NULL DEFAULT 0 CHECK (attempt >= 0),
    lease_until timestamptz,
    error_code varchar(48),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CHECK ((status = 'RUNNING') = (lease_until IS NOT NULL)),
    CHECK ((status = 'FAILED') = (error_code IS NOT NULL))
);

CREATE INDEX summary_tasks_queue_idx ON summary_tasks (updated_at, id) WHERE status = 'QUEUED';
