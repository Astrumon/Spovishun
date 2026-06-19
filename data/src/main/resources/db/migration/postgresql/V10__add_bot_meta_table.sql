CREATE TABLE IF NOT EXISTS bot_meta (
    key         VARCHAR(64) PRIMARY KEY,
    value       TEXT        NOT NULL,
    updated_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);
