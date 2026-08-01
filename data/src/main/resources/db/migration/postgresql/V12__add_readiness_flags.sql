-- Readiness-check mode for /ping and /all (spovishun-119).
-- Enabled everywhere by default; moderators opt a chat or a single group out via $ready-off.
ALTER TABLE chats
    ADD COLUMN readiness_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE groups
    ADD COLUMN readiness_enabled BOOLEAN NOT NULL DEFAULT TRUE;
