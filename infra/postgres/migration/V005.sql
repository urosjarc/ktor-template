CREATE TABLE system.logs_idempotency
(
    id                   UUID PRIMARY KEY,
    key                  UUID          NOT NULL,
    scope                VARCHAR(500)  NOT NULL,
    parent_id            UUID,
    child_index          BIGINT        NOT NULL,
    use_case             VARCHAR(1000) NOT NULL,
    input                TEXT          NOT NULL,
    input_hash           VARCHAR(64)   NOT NULL,
    status               VARCHAR(32)   NOT NULL,
    output               TEXT,
    error                TEXT,
    lookup               BIGINT,
    input_serialization  BIGINT,
    execution            BIGINT,
    output_serialization BIGINT,
    persistence          BIGINT,
    total                BIGINT,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL
);
CREATE UNIQUE INDEX uq_logs_idempotency_identity
    ON system.logs_idempotency (
                                key,
                                scope,
                                COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::UUID),
                                child_index,
                                use_case
        );

CREATE INDEX idx_logs_idempotency_key_scope
    ON system.logs_idempotency (key, scope);

CREATE INDEX idx_logs_idempotency_parent
    ON system.logs_idempotency (parent_id, child_index);
