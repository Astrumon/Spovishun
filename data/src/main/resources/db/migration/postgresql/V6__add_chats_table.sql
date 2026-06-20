CREATE TABLE chats (
    chat_id BIGINT PRIMARY KEY,
    title VARCHAR(255),
    type VARCHAR(32),
    registered_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Backfill chats from existing groups data
INSERT INTO chats (chat_id, registered_at)
SELECT DISTINCT chat_id, NOW()
FROM groups
ON CONFLICT DO NOTHING;

-- Backfill chats from existing members data
INSERT INTO chats (chat_id, registered_at)
SELECT DISTINCT chat_id, NOW()
FROM members
ON CONFLICT DO NOTHING;

-- Add foreign key constraint on groups.chat_id
ALTER TABLE groups
    ADD CONSTRAINT fk_groups_chat FOREIGN KEY (chat_id) REFERENCES chats(chat_id);
