ALTER TABLE research_requests
    ADD COLUMN checkpoint_token_digest varchar(64),
    ADD COLUMN checkpoint_scope jsonb,
    ADD COLUMN checkpoint_trace jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD CONSTRAINT research_requests_checkpoint_shape_check
        CHECK (
            (checkpoint_token_digest IS NULL AND checkpoint_scope IS NULL
                OR checkpoint_token_digest IS NOT NULL AND jsonb_typeof(checkpoint_scope) = 'array'
                   AND checkpoint_scope IS NOT NULL)
            AND jsonb_typeof(checkpoint_trace) = 'array'
            AND jsonb_array_length(checkpoint_trace) <= 3
        );
