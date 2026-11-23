ALTER TABLE research_requests
    ADD COLUMN checkpoint_context jsonb,
    ADD CONSTRAINT research_requests_checkpoint_context_check
        CHECK (checkpoint_context IS NULL OR jsonb_typeof(checkpoint_context) = 'object');
