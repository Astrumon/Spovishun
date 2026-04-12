ALTER TABLE groups ADD COLUMN IF NOT EXISTS chat_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE groups DROP CONSTRAINT IF EXISTS groups_name_key;
CREATE UNIQUE INDEX IF NOT EXISTS groups_chat_id_name_unique ON groups (chat_id, name);