ALTER TABLE research_requests
    ADD COLUMN resume_from uuid,
    ADD COLUMN input_fingerprint varchar(64),
    ADD CONSTRAINT research_requests_resume_parent_fk
        FOREIGN KEY (workspace_key, resume_from) REFERENCES research_requests(workspace_key, request_key),
    ADD CONSTRAINT research_requests_resume_parent_unique UNIQUE (workspace_key, resume_from);
