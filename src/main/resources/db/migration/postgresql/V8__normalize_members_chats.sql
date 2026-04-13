-- Step 1: Create member_chats join table (no id column — composite PK)
CREATE TABLE member_chats (
    member_id  BIGINT      NOT NULL,
    chat_id    BIGINT      NOT NULL,
    role       VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    joined_at  TIMESTAMP,
    PRIMARY KEY (member_id, chat_id)
);

-- Step 2: Populate member_chats from current members rows.
--   For each (user_id, chat_id) row, map to the *kept* member id (MIN per user_id).
INSERT INTO member_chats (member_id, chat_id, role, joined_at)
SELECT
    k.kept_id,
    m.chat_id,
    m.role,
    m.joined_at
FROM members m
JOIN (
    SELECT user_id, MIN(id) AS kept_id
    FROM members
    GROUP BY user_id
) k ON k.user_id = m.user_id;

-- Step 3: Re-point group_members.member_id from duplicate member ids to kept ones.
UPDATE group_members
SET member_id = subq.kept_id
FROM (
    SELECT m.id AS old_id, k.kept_id
    FROM members m
    JOIN (
        SELECT user_id, MIN(id) AS kept_id
        FROM members
        GROUP BY user_id
    ) k ON k.user_id = m.user_id
    WHERE m.id <> k.kept_id
) subq
WHERE group_members.member_id = subq.old_id;

-- Step 4: Delete duplicate member rows (keep only MIN(id) per user_id).
DELETE FROM members
WHERE id NOT IN (
    SELECT MIN(id)
    FROM members
    GROUP BY user_id
);

-- Step 5: Drop per-chat columns from members.
--   PostgreSQL automatically drops any constraints/indexes that cover these columns.
ALTER TABLE members
    DROP COLUMN chat_id,
    DROP COLUMN role,
    DROP COLUMN joined_at;

-- Step 6: Enforce global user uniqueness on members.
ALTER TABLE members
    ADD CONSTRAINT members_user_id_unique UNIQUE (user_id);

-- Step 7: Add FK constraints on member_chats.
ALTER TABLE member_chats
    ADD CONSTRAINT fk_member_chats_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_member_chats_chat   FOREIGN KEY (chat_id)   REFERENCES chats (chat_id) ON DELETE CASCADE;
