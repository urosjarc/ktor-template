CREATE TABLE logs
(
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

CREATE INDEX idx_logs_timestamp
    ON logs (timestamp);

CREATE INDEX idx_logs_level_timestamp
    ON logs (level, timestamp);

CREATE INDEX idx_logs_request_id
    ON logs (request_id);

CREATE INDEX idx_logs_user_id
    ON logs (user_id);
