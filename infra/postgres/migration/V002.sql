CREATE TABLE system.logs_audit
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inserted_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    user_id     TEXT,
    request_id  TEXT,

    operation   TEXT NOT NULL,
    table_name  TEXT NOT NULL,
    primary_key JSONB,

    changes     JSONB NOT NULL
);

CREATE INDEX idx_logs_audit_inserted_at
    ON system.logs_audit (inserted_at);

CREATE INDEX idx_logs_audit_user_id
    ON system.logs_audit (user_id);

CREATE INDEX idx_logs_audit_table_primary_key
    ON system.logs_audit (table_name, primary_key);
