-- Per-group parameters get their own table (spovishun-32). `readiness_enabled` moves here from
-- `groups` so that every group parameter has one home; `icon` is the first new one.
--
-- The primary key is the foreign key: a settings row has no lifecycle of its own, and chat scoping
-- comes from `groups.chat_id` through it, so there is nothing to denormalise here.
CREATE TABLE group_settings (
    group_id          BIGINT  PRIMARY KEY REFERENCES groups (id) ON DELETE CASCADE,
    icon              VARCHAR(32),
    readiness_enabled BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO group_settings (group_id, readiness_enabled)
SELECT id, readiness_enabled
FROM groups;

ALTER TABLE groups
    DROP COLUMN readiness_enabled;
