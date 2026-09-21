CREATE SCHEMA IF NOT EXISTS system;
CREATE SCHEMA IF NOT EXISTS public;

CREATE TABLE system.logs_application
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    timestamp  TIMESTAMPTZ  NOT NULL,
    level      VARCHAR(16)  NOT NULL,
    logger     VARCHAR(512) NOT NULL,
    line       VARCHAR(255) NOT NULL,
    thread     VARCHAR(256),
    request_id VARCHAR(128),
    user_id    VARCHAR(128),
    marker     VARCHAR(255),
    message    TEXT         NOT NULL,
    exception  TEXT
);

CREATE INDEX idx_logs_application_timestamp
    ON system.logs_application (timestamp);

CREATE INDEX idx_logs_application_level_timestamp
    ON system.logs_application (level, timestamp);

CREATE INDEX idx_logs_application_request_id
    ON system.logs_application (request_id);

CREATE INDEX idx_logs_application_user_id
    ON system.logs_application (user_id);
