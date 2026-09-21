CREATE TABLE system.logs_execution
(
    id                   UUID PRIMARY KEY,

    root_idempotency_key UUID        NOT NULL,
    parent_execution_id  UUID REFERENCES system.logs_execution (id),

    use_case             TEXT        NOT NULL,

    input                JSONB       NOT NULL,
    input_hash           BYTEA       NOT NULL,

    status               TEXT        NOT NULL,

    output               JSONB,
    error                JSONB,

    started_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at         TIMESTAMPTZ,

    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_use_case_execution_identity
    ON system.logs_execution (
                      root_idempotency_key,
                      parent_execution_id,
                      use_case,
                      input_hash
        );
