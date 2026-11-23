ALTER TABLE research_requests ADD COLUMN expires_at timestamptz;

-- 已有运行沿用原认领时间，不会因为升级而获得新的期限。
UPDATE research_requests SET expires_at = created_at + interval '60 seconds';

ALTER TABLE research_requests
    ALTER COLUMN expires_at SET NOT NULL,
    ALTER COLUMN expires_at SET DEFAULT (clock_timestamp() + interval '60 seconds'),
    DROP CONSTRAINT research_requests_check,
    ADD CONSTRAINT research_requests_status_record_check
        CHECK ((status = 'COMPLETED' AND record_id IS NOT NULL)
            OR (status IN ('RUNNING', 'FAILED', 'UNKNOWN') AND record_id IS NULL));
