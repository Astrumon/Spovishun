ALTER TABLE members ADD chat_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE members ALTER COLUMN chat_id DROP DEFAULT;
ALTER TABLE members ADD CONSTRAINT members_chat_id_user_id_unique UNIQUE (chat_id, user_id);
ALTER TABLE members ADD CONSTRAINT members_chat_id_username_unique UNIQUE (chat_id, username);
ALTER TABLE IF EXISTS members DROP CONSTRAINT IF EXISTS members_username_key;